// منطق نقي (بلا Deno ولا شبكة) لدالة admin-api — قابل للاختبار في Node.
// الدالة الخادمية هي المرجع الأمني؛ أي تحقق في المتصفح أو التطبيق للتجربة فقط.

export const CATEGORIES = ['tourism', 'restaurants', 'government', 'shopping', 'education', 'news', 'jobs', 'apps', 'services'];
export const STATUSES = ['draft', 'review', 'published', 'archived'];
export const REGION_TYPES = ['governorate', 'city', 'town', 'village', 'neighborhood'];
export const REGION_STATUSES = ['draft', 'published', 'archived'];
export const FEATURES = ['parking', 'kids_area', 'wheelchair', 'outdoor', 'wifi', 'prayer_room'];
export const AGE_GROUPS = ['toddlers', 'kids', 'teens', 'adults', 'seniors'];

// حدود الرفع — يجب أن تطابق إعدادات الحاوية في SQL (file_size_limit/allowed_mime_types).
export const LIMITS = {
  image: { maxBytes: 10 * 1024 * 1024, types: ['image/jpeg', 'image/png', 'image/webp'] },
  video: { maxBytes: 60 * 1024 * 1024, types: ['video/mp4', 'video/webm'], maxSeconds: 180 },
};

// ---------------------------------------------------------------- الأدوار والصلاحيات
/**
 * الدور يُحسب من رمز Firebase الموثَّق على الخادم فقط:
 *  - owner: البريد مطابق تمامًا (بلا حساسية لحالة الأحرف) لـ ADMIN_EMAIL المضبوط كسر في الخادم، والبريد مؤكَّد.
 *  - admin/editor: من جدول staff (يضيفه المالك فقط)، والبريد مؤكَّد.
 */
export function roleFor({ email, emailVerified, adminEmail, staffRole }) {
  if (emailVerified !== true) return null;
  const e = String(email || '').trim().toLowerCase();
  const a = String(adminEmail || '').trim().toLowerCase();
  if (!e) return null;
  if (a && e === a) return 'owner';
  if (staffRole === 'admin' || staffRole === 'editor') return staffRole;
  return null;
}

export const ACTIONS = {
  whoami: 'signed_in',
  load_favorites: 'verified', save_favorites: 'verified', submit_report: 'verified', submit_suggestion: 'verified',
  stats: 'staff', list_regions: 'staff', list_places: 'staff', list_media: 'staff', upsert_place: 'staff',
  create_upload: 'staff', add_media: 'staff', update_media: 'staff', set_cover: 'staff', reorder_media: 'staff',
  delete_media: 'admin', delete_place: 'admin', upsert_region: 'admin', delete_region: 'admin',
  inbox: 'admin', resolve: 'admin', get_settings: 'admin', set_settings: 'admin', audit_list: 'admin',
  set_role: 'owner', list_staff: 'owner',
};

/** ctx: { signedIn, emailVerified, role } → { ok, status, reason } */
export function authorize(action, ctx) {
  const need = ACTIONS[action];
  if (!need) return { ok: false, status: 400, reason: 'إجراء غير معروف.' };
  if (!ctx?.signedIn) return { ok: false, status: 401, reason: 'سجّل الدخول أولًا.' };
  if (need === 'signed_in') return { ok: true };
  if (!ctx.emailVerified) return { ok: false, status: 403, reason: 'أكّد بريدك الإلكتروني أولًا.' };
  if (need === 'verified') return { ok: true };
  const r = ctx.role;
  const allowed = need === 'staff' ? ['owner', 'admin', 'editor'] : need === 'admin' ? ['owner', 'admin'] : ['owner'];
  return allowed.includes(r) ? { ok: true } : { ok: false, status: 403, reason: 'لا تملك صلاحية هذا الإجراء.' };
}

export function allowedStatuses(role) {
  return role === 'owner' || role === 'admin' ? STATUSES.slice() : role === 'editor' ? ['draft', 'review'] : [];
}

// ---------------------------------------------------------------- التحقق من المدخلات
const URL_RE = /^https?:\/\/[^\s]+$/i;
const PHONE_RE = /^\+?[0-9\s\-()]{6,20}$/;
const ID_RE = /^[a-z0-9][a-z0-9-]{0,62}$/;
const FILE_RE = /^[A-Za-z0-9._-]{1,120}$/;

export const validId = (id) => typeof id === 'string' && ID_RE.test(id);

export function slugId(name, now = Date.now()) {
  const ascii = String(name || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 30).replace(/-$/, '');
  return `${ascii || 'place'}-${now.toString(36)}`;
}

const s = (v, max) => { const t = String(v ?? '').trim(); return t ? t.slice(0, max) : null; };
const num = (v) => (v === '' || v == null ? null : Number(v));
const pick = (arr, allowed) => (Array.isArray(arr) ? [...new Set(arr.filter((x) => allowed.includes(x)))] : []);
const lines = (v) => {
  const arr = Array.isArray(v) ? v : String(v || '').split('\n');
  return arr.map((x) => String(x).trim().slice(0, 120)).filter(Boolean).slice(0, 30);
};

/** يُرجع { errors, row } بأعمدة snake_case جاهزة للإدخال. الحقول الفارغة = null (لا نخترع قيمًا). */
export function sanitizePlace(f, role) {
  const errors = [];
  f = f || {};
  const name = String(f.name ?? '').trim();
  if (!name) errors.push('الاسم مطلوب.');
  if (name.length > 200) errors.push('الاسم طويل جدًا.');
  if (!CATEGORIES.includes(f.category)) errors.push('التصنيف غير صالح.');
  if (!allowedStatuses(role).includes(f.status)) errors.push('الحالة غير مسموحة لصلاحيتك.');
  const phone = s(f.phone, 40);
  if (phone && !PHONE_RE.test(phone)) errors.push('صيغة الهاتف غير صالحة.');
  const website = s(f.website, 300);
  if (website && !URL_RE.test(website)) errors.push('الموقع يجب أن يبدأ بـ http:// أو https://');
  const sourceUrl = s(f.sourceUrl, 500);
  if (sourceUrl && !URL_RE.test(sourceUrl)) errors.push('رابط المصدر غير صالح.');
  const lat = num(f.lat); const lng = num(f.lng);
  if ((lat == null) !== (lng == null)) errors.push('أدخل خط العرض والطول معًا أو اتركهما فارغين.');
  if (lat != null && !(lat >= -90 && lat <= 90)) errors.push('خط العرض خارج النطاق.');
  if (lng != null && !(lng >= -180 && lng <= 180)) errors.push('خط الطول خارج النطاق.');
  const price = num(f.priceLevel);
  if (price != null && ![1, 2, 3].includes(price)) errors.push('مستوى السعر غير صالح.');
  const kids = f.kidsFriendly === true || f.kidsFriendly === 'yes' ? true : f.kidsFriendly === false || f.kidsFriendly === 'no' ? false : null;
  const regionId = f.regionId ? String(f.regionId) : null;
  if (regionId && !validId(regionId)) errors.push('معرّف المنطقة غير صالح.');
  const row = {
    name, category: f.category, status: f.status, region_id: regionId,
    description: String(f.description ?? '').trim().slice(0, 5000),
    phone, address: s(f.address, 300), lat, lng, website,
    price_level: price, price_note: s(f.priceNote, 200), opening_hours: s(f.openingHours, 300),
    kids_friendly: kids,
    age_groups: pick(f.ageGroups, AGE_GROUPS), features: pick(f.features, FEATURES),
    activities: lines(f.activities), alerts: lines(f.alerts),
    source_name: s(f.sourceName, 200), source_url: sourceUrl, featured: f.featured === true,
  };
  return { errors, row };
}

export function sanitizeRegion(f) {
  const errors = [];
  f = f || {};
  const name = String(f.name ?? '').trim();
  if (!name) errors.push('الاسم مطلوب.');
  if (name.length > 120) errors.push('الاسم طويل جدًا.');
  if (!REGION_TYPES.includes(f.type)) errors.push('نوع المنطقة غير صالح.');
  const status = f.status || 'published';
  if (!REGION_STATUSES.includes(status)) errors.push('حالة المنطقة غير صالحة.');
  const parent = f.parentId ? String(f.parentId) : null;
  if (parent && !validId(parent)) errors.push('معرّف المنطقة الأم غير صالح.');
  return { errors, row: { name, type: f.type, parent_id: parent, status } };
}

export function validateUpload({ kind, contentType, size }) {
  const lim = LIMITS[kind];
  if (!lim) return 'نوع وسيط غير معروف.';
  if (!lim.types.includes(contentType)) return `الصيغة غير مسموحة (${contentType || 'غير معروفة'}).`;
  if (!Number.isFinite(size) || size <= 0) return 'الملف فارغ.';
  if (size > lim.maxBytes) return `الحجم يتجاوز الحد (${Math.round(lim.maxBytes / 1048576)}MB).`;
  return null;
}

export function safeFileName(original, now = Date.now(), rand = Math.random()) {
  const ext = (String(original || '').split('.').pop() || 'bin').toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 5) || 'bin';
  return `${now}-${Math.floor(rand * 1e6).toString(36)}.${ext}`;
}

export function storagePathFor(placeId, kind, fileName) {
  return `places/${placeId}/${kind === 'video' ? 'videos' : 'images'}/${fileName}`;
}

/** يتحقق أن المسار يخص هذا المكان ونوع الوسيط، وبصيغة اسم آمنة. يُرجع { dir, file } أو null. */
export function parseMediaPath(placeId, kind, path) {
  if (!validId(placeId) || typeof path !== 'string') return null;
  const folder = kind === 'video' ? 'videos' : kind === 'image' ? 'images' : null;
  if (!folder) return null;
  const prefix = `places/${placeId}/${folder}/`;
  if (!path.startsWith(prefix)) return null;
  const file = path.slice(prefix.length);
  if (!FILE_RE.test(file) || file.startsWith('.') || file.includes('..')) return null;
  return { dir: prefix.slice(0, -1), file };
}

export function sanitizeMediaMeta(m) {
  m = m || {};
  const d = num(m.durationSec);
  return {
    title: s(m.title, 200), description: s(m.description, 500), alt: s(m.alt, 300),
    source: s(m.source, 300), license: s(m.license, 200),
    duration_sec: d != null && Number.isInteger(d) && d >= 0 && d <= 3600 ? d : null,
  };
}

export function sanitizeIds(ids) {
  if (!Array.isArray(ids)) return null;
  const out = [...new Set(ids.filter((x) => typeof x === 'string' && /^[A-Za-z0-9_-]{1,80}$/.test(x)))];
  return out.slice(0, 500);
}

export function sanitizeReport(b) {
  const errors = [];
  const reason = s(b?.reason, 80);
  if (!reason) errors.push('سبب البلاغ مطلوب.');
  const placeId = b?.placeId ? String(b.placeId).slice(0, 80) : null;
  return { errors, row: { place_id: placeId, reason, details: s(b?.details, 1000) } };
}

export function sanitizeSuggestion(b) {
  const errors = [];
  const name = s(b?.name, 120);
  if (!name) errors.push('اسم المكان مطلوب.');
  const category = CATEGORIES.includes(b?.category) ? b.category : null;
  return { errors, row: { name, category, region_name: s(b?.regionName, 80), details: s(b?.details, 1000) } };
}

export function pageParams(b, max = 50) {
  let offset = Number(b?.offset); let limit = Number(b?.limit);
  if (!Number.isInteger(offset) || offset < 0) offset = 0;
  if (!Number.isInteger(limit) || limit < 1) limit = 25;
  return { offset, limit: Math.min(limit, max) };
}

/** المحرر: يعمل على المسودات/المراجعة فقط (الجديد والموجود). */
export function editorMayTouch(role, existingStatus, newStatus) {
  if (role !== 'editor') return true;
  const ok = ['draft', 'review'];
  return ok.includes(newStatus) && (existingStatus == null || ok.includes(existingStatus));
}

export function normEmail(e) { return String(e || '').trim().toLowerCase(); }
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export function validEmail(e) { return EMAIL_RE.test(normEmail(e)) && normEmail(e).length <= 200; }
