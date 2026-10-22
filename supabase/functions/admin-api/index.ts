// دالة Supabase Edge: admin-api
// تتحقق من رمز Firebase ID (توقيع + issuer + audience)، تحسب الدور على الخادم، ثم تنفّذ الإجراء بمفتاح service_role.
// الأسرار (تُضبط في Supabase، لا في المستودع ولا في التطبيق):
//   ADMIN_EMAIL           بريد المالك (المدير الرئيسي)
//   FIREBASE_PROJECT_ID   معرّف مشروع Firebase (للتحقق من الرمز)
// SUPABASE_URL و SUPABASE_SERVICE_ROLE_KEY تُحقن تلقائيًا من Supabase.
// تُنشر بـ --no-verify-jwt لأن الهوية هنا رمز Firebase وليس رمز Supabase.
// deno-lint-ignore-file no-explicit-any
// @ts-nocheck
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";
import * as L from "./logic.js";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ADMIN_EMAIL = Deno.env.get("ADMIN_EMAIL") ?? "";
const FIREBASE_PROJECT_ID = Deno.env.get("FIREBASE_PROJECT_ID") ?? "";
const BUCKET = "media";

const sb = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });
const JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "content-type, x-firebase-token, apikey, authorization",
  "access-control-allow-methods": "POST, OPTIONS",
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "content-type": "application/json; charset=utf-8" } });

class HttpError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}
const bad = (m: string) => new HttpError(400, m);
const must = (r: { error: any; data?: any }, msg = "تعذّر تنفيذ العملية.") => {
  if (r.error) { console.error(msg, r.error); throw new HttpError(500, msg); }
  return r.data;
};

// ---------------------------------------------------------------- الهوية والدور
async function identify(req: Request) {
  const token = req.headers.get("x-firebase-token");
  if (!token || !FIREBASE_PROJECT_ID) return null;
  try {
    const { payload } = await jwtVerify(token, JWKS, {
      issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
      audience: FIREBASE_PROJECT_ID,
    });
    if (!payload.sub) return null;
    return {
      uid: String(payload.sub),
      email: typeof payload.email === "string" ? payload.email : "",
      emailVerified: payload.email_verified === true,
    };
  } catch (_) {
    return null;
  }
}

async function roleOf(u: { email: string; emailVerified: boolean }) {
  let staffRole: string | undefined;
  if (u.emailVerified && u.email && L.normEmail(u.email) !== L.normEmail(ADMIN_EMAIL)) {
    const r = await sb.from("staff").select("role").eq("email", L.normEmail(u.email)).maybeSingle();
    staffRole = r.data?.role;
  }
  return L.roleFor({ email: u.email, emailVerified: u.emailVerified, adminEmail: ADMIN_EMAIL, staffRole });
}

const audit = (ctx: any, action: string, target: string | null, extra: unknown = null) =>
  sb.from("audit").insert({ action, target, actor_uid: ctx.uid, actor_email: ctx.email, extra }).then(() => {}, () => {});

const publicUrl = (path: string) => `${SUPABASE_URL}/storage/v1/object/public/${BUCKET}/${path}`;

async function placeStatus(id: string): Promise<string | null> {
  if (!L.validId(id)) throw bad("معرّف المكان غير صالح.");
  const r = await sb.from("places").select("status").eq("id", id).maybeSingle();
  must(r);
  return r.data?.status ?? null;
}

// ---------------------------------------------------------------- الإجراءات
type Handler = (ctx: any, b: any) => Promise<unknown>;
const H: Record<string, Handler> = {
  async whoami(ctx) {
    return { uid: ctx.uid, email: ctx.email, emailVerified: ctx.emailVerified, role: ctx.role };
  },

  // ---- للمستخدم الموثَّق
  async load_favorites(ctx) {
    const r = await sb.from("favorites").select("place_ids").eq("uid", ctx.uid).maybeSingle();
    must(r);
    return { ids: r.data?.place_ids ?? [] };
  },
  async save_favorites(ctx, b) {
    const ids = L.sanitizeIds(b.ids);
    if (!ids) throw bad("قائمة غير صالحة.");
    must(await sb.from("favorites").upsert({ uid: ctx.uid, place_ids: ids, updated_at: new Date().toISOString() }));
    return { ok: true };
  },
  // حذف كل ما يرتبط بحساب المستخدم قبل حذف حساب Firebase من التطبيق
  async delete_my_data(ctx) {
    must(await sb.from("favorites").delete().eq("uid", ctx.uid));
    must(await sb.from("reports").delete().eq("reporter_uid", ctx.uid));
    must(await sb.from("suggestions").delete().eq("suggested_by_uid", ctx.uid));
    await audit(ctx, "account_data_deleted", `users/${ctx.uid}`);
    return { ok: true };
  },
  async submit_report(ctx, b) {
    const { errors, row } = L.sanitizeReport(b);
    if (errors.length) throw bad(errors[0]);
    const since = new Date(Date.now() - 86400000).toISOString();
    const c = await sb.from("reports").select("id", { count: "exact", head: true }).eq("reporter_uid", ctx.uid).gte("created_at", since);
    if ((c.count ?? 0) >= 20) throw new HttpError(429, "تجاوزت عدد البلاغات المسموح اليوم.");
    must(await sb.from("reports").insert({ ...row, reporter_uid: ctx.uid }));
    return { ok: true };
  },
  async submit_suggestion(ctx, b) {
    const { errors, row } = L.sanitizeSuggestion(b);
    if (errors.length) throw bad(errors[0]);
    const since = new Date(Date.now() - 86400000).toISOString();
    const c = await sb.from("suggestions").select("id", { count: "exact", head: true }).eq("suggested_by_uid", ctx.uid).gte("created_at", since);
    if ((c.count ?? 0) >= 10) throw new HttpError(429, "تجاوزت عدد الاقتراحات المسموح اليوم.");
    must(await sb.from("suggestions").insert({ ...row, suggested_by_uid: ctx.uid }));
    return { ok: true };
  },

  // ---- للطاقم
  async stats() {
    const n = async (q: any) => { const r = await q; must(r); return r.count ?? 0; };
    const head = { count: "exact", head: true } as const;
    const P = () => sb.from("places").select("id", head);
    const [places, published, drafts, review, archived, media, regions, reports, suggestions] = await Promise.all([
      n(P()), n(P().eq("status", "published")), n(P().eq("status", "draft")), n(P().eq("status", "review")), n(P().eq("status", "archived")),
      n(sb.from("media").select("id", head)), n(sb.from("regions").select("id", head)),
      n(sb.from("reports").select("id", head).eq("status", "open")), n(sb.from("suggestions").select("id", head).eq("status", "pending")),
    ]);
    return { places, published, drafts, review, archived, media, regions, openReports: reports, pendingSuggestions: suggestions };
  },
  async list_regions() {
    return { regions: must(await sb.from("regions").select("*").order("name").limit(500)) };
  },
  async list_places(_ctx, b) {
    const { offset, limit } = L.pageParams(b);
    let q = sb.from("places").select("*").order("name").range(offset, offset + limit);
    if (b.status) { if (!L.STATUSES.includes(b.status)) throw bad("حالة غير صالحة."); q = q.eq("status", b.status); }
    const rows = must(await q);
    return { places: rows.slice(0, limit), hasMore: rows.length > limit };
  },
  async list_media(_ctx, b) {
    if (!L.validId(b.placeId)) throw bad("معرّف المكان غير صالح.");
    return { media: must(await sb.from("media").select("*").eq("place_id", b.placeId).order("sort_order").limit(200)) };
  },
  async upsert_place(ctx, b) {
    const { errors, row } = L.sanitizePlace(b.place, ctx.role);
    if (errors.length) throw bad(errors.join(" "));
    const isNew = !b.id;
    const id = isNew ? L.slugId(row.name) : String(b.id);
    if (!L.validId(id)) throw bad("معرّف المكان غير صالح.");
    const existing = isNew ? null : await placeStatus(id);
    if (!isNew && existing == null) throw new HttpError(404, "المكان غير موجود.");
    if (!L.editorMayTouch(ctx.role, existing, row.status)) throw new HttpError(403, "المحرر يعمل على المسودات وقيد المراجعة فقط.");
    if (row.region_id) {
      const r = await sb.from("regions").select("name,name_en").eq("id", row.region_id).maybeSingle();
      must(r);
      if (!r.data) throw bad("المنطقة غير موجودة.");
      row.region_name = r.data.name;
      row.region_name_en = r.data.name_en ?? null;
    } else { row.region_name = null; row.region_name_en = null; }
    const now = new Date().toISOString();
    const payload: any = { ...row, id, updated_at: now };
    if (b.markVerified === true) payload.verified_at = now;
    const saved = must(await sb.from("places").upsert(payload, { onConflict: "id" }).select().single());
    await audit(ctx, isNew ? "place_create" : "place_update", `places/${id}`, { status: row.status });
    return { place: saved };
  },
  async create_upload(ctx, b) {
    const kind = b.kind === "video" ? "video" : b.kind === "image" ? "image" : null;
    if (!kind) throw bad("نوع وسيط غير معروف.");
    const problem = L.validateUpload({ kind, contentType: String(b.contentType || ""), size: Number(b.size) });
    if (problem) throw bad(problem);
    const st = await placeStatus(String(b.placeId));
    if (st == null) throw new HttpError(404, "المكان غير موجود.");
    if (!L.editorMayTouch(ctx.role, st, st)) throw new HttpError(403, "المحرر يرفع للمسودات وقيد المراجعة فقط.");
    const path = L.storagePathFor(b.placeId, kind, L.safeFileName(String(b.fileName || "file")));
    const r = await sb.storage.from(BUCKET).createSignedUploadUrl(path);
    if (r.error) { console.error(r.error); throw new HttpError(500, "تعذّر تجهيز الرفع."); }
    return { path, token: r.data.token, signedUrl: r.data.signedUrl };
  },
  async add_media(ctx, b) {
    const kind = b.kind === "video" ? "video" : "image";
    const pp = L.parseMediaPath(b.placeId, kind, b.path);
    if (!pp) throw bad("مسار الملف غير صالح.");
    const st = await placeStatus(b.placeId);
    if (st == null) throw new HttpError(404, "المكان غير موجود.");
    if (!L.editorMayTouch(ctx.role, st, st)) throw new HttpError(403, "المحرر يعدّل المسودات وقيد المراجعة فقط.");
    // لا نسجّل وسيطًا لملف غير موجود فعليًا في التخزين.
    const ls = await sb.storage.from(BUCKET).list(pp.dir, { search: pp.file, limit: 5 });
    if (ls.error || !(ls.data ?? []).some((o: any) => o.name === pp.file)) throw bad("الملف غير موجود في التخزين. أعد الرفع.");
    const cnt = await sb.from("media").select("id", { count: "exact", head: true }).eq("place_id", b.placeId);
    const meta = L.sanitizeMediaMeta(b);
    const row = {
      id: pp.file.replace(/\.[^.]+$/, ""), place_id: b.placeId, type: kind, url: publicUrl(b.path), storage_path: b.path,
      ...meta, sort_order: cnt.count ?? 0, is_cover: false,
      content_type: typeof b.contentType === "string" ? b.contentType.slice(0, 80) : null,
      size_bytes: Number.isFinite(Number(b.size)) ? Number(b.size) : null, uploaded_by: ctx.uid,
    };
    const saved = must(await sb.from("media").insert(row).select().single());
    await audit(ctx, "media_upload", `media/${row.id}`, { placeId: b.placeId });
    return { media: saved };
  },
  async update_media(ctx, b) {
    if (!L.validId(b.placeId)) throw bad("معرّف المكان غير صالح.");
    const st = await placeStatus(b.placeId);
    if (!L.editorMayTouch(ctx.role, st, st)) throw new HttpError(403, "المحرر يعدّل المسودات وقيد المراجعة فقط.");
    const { duration_sec: _d, ...meta } = L.sanitizeMediaMeta(b);
    must(await sb.from("media").update(meta).eq("id", String(b.mediaId)).eq("place_id", b.placeId));
    await audit(ctx, "media_update", `media/${b.mediaId}`);
    return { ok: true };
  },
  async set_cover(ctx, b) {
    if (!L.validId(b.placeId)) throw bad("معرّف المكان غير صالح.");
    const st = await placeStatus(b.placeId);
    if (!L.editorMayTouch(ctx.role, st, st)) throw new HttpError(403, "المحرر يعدّل المسودات وقيد المراجعة فقط.");
    const m = await sb.from("media").select("id,url,alt,type").eq("id", String(b.mediaId)).eq("place_id", b.placeId).maybeSingle();
    must(m);
    if (!m.data || m.data.type !== "image") throw bad("اختر صورة تابعة لهذا المكان.");
    must(await sb.from("media").update({ is_cover: false }).eq("place_id", b.placeId));
    must(await sb.from("media").update({ is_cover: true }).eq("id", m.data.id));
    const p = await sb.from("places").select("name").eq("id", b.placeId).single();
    must(await sb.from("places").update({ cover_url: m.data.url, cover_alt: m.data.alt || p.data?.name || null, updated_at: new Date().toISOString() }).eq("id", b.placeId));
    await audit(ctx, "set_cover", `places/${b.placeId}`, { mediaId: m.data.id });
    return { ok: true };
  },
  async reorder_media(ctx, b) {
    if (!L.validId(b.placeId) || !Array.isArray(b.order) || b.order.length > 200) throw bad("طلب غير صالح.");
    const st = await placeStatus(b.placeId);
    if (!L.editorMayTouch(ctx.role, st, st)) throw new HttpError(403, "المحرر يعدّل المسودات وقيد المراجعة فقط.");
    for (let i = 0; i < b.order.length; i++) {
      must(await sb.from("media").update({ sort_order: i }).eq("id", String(b.order[i])).eq("place_id", b.placeId));
    }
    return { ok: true };
  },

  // ---- للمدير
  async delete_media(ctx, b) {
    if (!L.validId(b.placeId)) throw bad("معرّف المكان غير صالح.");
    const r = await sb.from("media").select("*").eq("id", String(b.mediaId)).eq("place_id", b.placeId).maybeSingle();
    must(r);
    if (!r.data) throw new HttpError(404, "الوسيط غير موجود.");
    // ١) نحذف السجل أولًا (لا يبقى مرجع لملف غير موجود). ٢) ثم الملف. فشل الثاني يُسجَّل في السجل للتنظيف.
    must(await sb.from("media").delete().eq("id", r.data.id));
    const p = await sb.from("places").select("cover_url").eq("id", b.placeId).maybeSingle();
    if (p.data?.cover_url === r.data.url) {
      must(await sb.from("places").update({ cover_url: null, cover_alt: null }).eq("id", b.placeId));
    }
    const rm = await sb.storage.from(BUCKET).remove([r.data.storage_path]);
    if (rm.error) await audit(ctx, "orphan_file", r.data.storage_path, { error: String(rm.error.message ?? rm.error) });
    await audit(ctx, "media_delete", `media/${r.data.id}`, { placeId: b.placeId });
    return { ok: true, fileRemoved: !rm.error };
  },
  async delete_place(ctx, b) {
    if (!L.validId(b.id)) throw bad("معرّف المكان غير صالح.");
    const media = must(await sb.from("media").select("storage_path").eq("place_id", b.id));
    must(await sb.from("places").delete().eq("id", b.id)); // يحذف سجلات الوسائط بالتتالي
    let orphan = 0;
    if (media.length) {
      const rm = await sb.storage.from(BUCKET).remove(media.map((m: any) => m.storage_path));
      if (rm.error) { orphan = media.length; await audit(ctx, "orphan_files", `places/${b.id}`, { count: orphan }); }
    }
    await audit(ctx, "place_delete", `places/${b.id}`);
    return { ok: true, orphanFiles: orphan };
  },
  async upsert_region(ctx, b) {
    const { errors, row } = L.sanitizeRegion(b);
    if (errors.length) throw bad(errors.join(" "));
    const id = b.id ? String(b.id) : L.slugId(row.name);
    if (!L.validId(id)) throw bad("معرّف المنطقة غير صالح.");
    must(await sb.from("regions").upsert({ ...row, id, updated_at: new Date().toISOString() }, { onConflict: "id" }));
    await audit(ctx, "region_upsert", `regions/${id}`);
    return { ok: true, id };
  },
  async delete_region(ctx, b) {
    if (!L.validId(b.id)) throw bad("معرّف المنطقة غير صالح.");
    must(await sb.from("regions").delete().eq("id", b.id));
    await audit(ctx, "region_delete", `regions/${b.id}`);
    return { ok: true };
  },
  async inbox() {
    const [r, s] = await Promise.all([
      sb.from("reports").select("*").eq("status", "open").order("created_at", { ascending: false }).limit(50),
      sb.from("suggestions").select("*").eq("status", "pending").order("created_at", { ascending: false }).limit(50),
    ]);
    must(r); must(s);
    return {
      items: [
        ...r.data.map((x: any) => ({ kind: "reports", id: x.id, title: `بلاغ: ${x.reason}`, details: x.details ?? "", placeId: x.place_id, status: x.status })),
        ...s.data.map((x: any) => ({ kind: "suggestions", id: x.id, title: `اقتراح: ${x.name}`, details: [x.region_name, x.details].filter(Boolean).join(" — "), category: x.category, status: x.status })),
      ],
    };
  },
  async resolve(ctx, b) {
    if (!["reports", "suggestions"].includes(b.kind) || typeof b.id !== "string" || !/^[0-9a-f-]{36}$/i.test(b.id)) throw bad("طلب غير صالح.");
    must(await sb.from(b.kind).update({ status: b.kind === "reports" ? "resolved" : "reviewed" }).eq("id", b.id));
    await audit(ctx, `resolve_${b.kind}`, `${b.kind}/${b.id}`);
    return { ok: true };
  },
  async get_settings() {
    const r = await sb.from("settings").select("value").eq("key", "public").maybeSingle();
    must(r);
    return { settings: r.data?.value ?? { adsEnabled: true } };
  },
  async set_settings(ctx, b) {
    const value = { adsEnabled: b.adsEnabled !== false, siteMessage: String(b.siteMessage ?? "").slice(0, 300) };
    must(await sb.from("settings").upsert({ key: "public", value }));
    await audit(ctx, "settings_update", "settings/public", value);
    return { ok: true };
  },
  async audit_list() {
    return { entries: must(await sb.from("audit").select("*").order("at", { ascending: false }).limit(50)) };
  },

  // ---- للمالك فقط
  async set_role(ctx, b) {
    const email = L.normEmail(b.email);
    if (!L.validEmail(email)) throw bad("بريد غير صالح.");
    if (email === L.normEmail(ADMIN_EMAIL)) throw bad("لا يمكن تعديل صلاحية المالك.");
    if (!["admin", "editor", "none"].includes(b.role)) throw bad("دور غير صالح.");
    if (b.role === "none") must(await sb.from("staff").delete().eq("email", email));
    else must(await sb.from("staff").upsert({ email, role: b.role, added_by: ctx.email }));
    await audit(ctx, "set_role", email, { role: b.role });
    return { ok: true };
  },
  async list_staff() {
    return { staff: must(await sb.from("staff").select("*").order("created_at")) };
  },
};

// ---------------------------------------------------------------- نقطة الدخول
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json(405, { error: "الطريقة غير مسموحة." });
  try {
    if (!SUPABASE_URL || !SERVICE_KEY || !FIREBASE_PROJECT_ID) throw new HttpError(500, "الخادم غير مهيأ (FIREBASE_PROJECT_ID).");
    let body: any;
    try { body = await req.json(); } catch (_) { throw bad("جسم الطلب غير صالح."); }
    const action = String(body?.action ?? "");
    const user = await identify(req);
    const role = user ? await roleOf(user) : null;
    const ctx = { signedIn: !!user, uid: user?.uid, email: user?.email ?? "", emailVerified: !!user?.emailVerified, role };
    const auth = L.authorize(action, ctx);
    if (!auth.ok) throw new HttpError(auth.status ?? 403, auth.reason ?? "مرفوض.");
    const out = await H[action](ctx, body);
    return json(200, out);
  } catch (e) {
    if (e instanceof HttpError) return json(e.status, { error: e.message });
    console.error(e);
    return json(500, { error: "خطأ غير متوقع في الخادم." });
  }
});
