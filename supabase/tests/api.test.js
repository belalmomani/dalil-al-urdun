// اختبارات تكاملية لدالة admin-api: الكود الحقيقي (index.ts) مع تخزين/قاعدة بيانات في الذاكرة وتوثيق مزيّف.
// لا يختبر: توقيع JWT الحقيقي من Google، ولا RLS الفعلي (اختُبر على Postgres منفصلًا)، ولا شبكة Supabase.
import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';

const OWNER = 'bilalalmomani90@gmail.com';
let handler;
globalThis.Deno = {
  env: { get: (k) => ({ SUPABASE_URL: 'https://proj.supabase.co', SUPABASE_SERVICE_ROLE_KEY: 'svc', ADMIN_EMAIL: OWNER, FIREBASE_PROJECT_ID: 'proj' })[k] },
  serve: (h) => { handler = h; },
};
await import('../functions/admin-api/index.ts');
const { DB, reset } = await import('./stub-supabase.mjs');

const tok = (uid, email, verified = true, project = 'proj') => `${uid}|${email}|${verified ? 1 : 0}|${project}`;
async function call(action, body = {}, token) {
  const headers = { 'content-type': 'application/json' };
  if (token) headers['x-firebase-token'] = token;
  const res = await handler(new Request('https://x/functions/v1/admin-api', { method: 'POST', headers, body: JSON.stringify({ action, ...body }) }));
  return { status: res.status, body: await res.json() };
}
const owner = tok('u-owner', OWNER);
const alice = tok('u-alice', 'alice@example.com');
const bob = tok('u-bob', 'bob@example.com');
beforeEach(() => reset());

const place = (o = {}) => ({ name: 'Cafe Test', category: 'restaurants', status: 'draft', ...o });

test('الهوية: بلا رمز أو بمشروع آخر = 401', async () => {
  assert.equal((await call('whoami')).status, 401);
  assert.equal((await call('stats')).status, 401);
  assert.equal((await call('whoami', {}, tok('x', OWNER, true, 'other-project'))).status, 401);
  assert.equal((await call('whoami', {}, 'garbage')).status, 401);
});
test('whoami: المالك المؤكَّد فقط يحصل على owner', async () => {
  assert.equal((await call('whoami', {}, owner)).body.role, 'owner');
  assert.equal((await call('whoami', {}, tok('u2', OWNER, false))).body.role, null);
  assert.equal((await call('whoami', {}, tok('u3', 'evil@example.com'))).body.role, null);
  assert.equal((await call('whoami', { email: OWNER, role: 'owner' }, alice)).body.role, null, 'جسم الطلب لا يمنح دورًا');
});
test('المستخدم العادي مرفوض في كل إجراء إداري', async () => {
  for (const a of ['stats', 'list_places', 'upsert_place', 'create_upload', 'delete_place', 'delete_media', 'upsert_region', 'inbox', 'set_settings', 'set_role', 'audit_list', 'list_staff']) {
    const r = await call(a, { place: place(), id: 'x', email: 'a@b.co', role: 'admin' }, alice);
    assert.equal(r.status, 403, a);
  }
  assert.equal(DB.tables.places?.length ?? 0, 0);
  assert.equal(DB.tables.staff?.length ?? 0, 0);
});
test('المستخدم يمنح نفسه دورًا؟ مرفوض', async () => {
  assert.equal((await call('set_role', { email: 'alice@example.com', role: 'admin' }, alice)).status, 403);
  assert.equal((await call('whoami', {}, alice)).body.role, null);
});

test('المفضلة والبلاغات والاقتراحات لمستخدم مؤكَّد فقط', async () => {
  assert.equal((await call('save_favorites', { ids: ['a', 'b'] }, tok('u-alice', 'alice@example.com', false))).status, 403);
  assert.equal((await call('save_favorites', { ids: ['a', 'b', 'bad id'] }, alice)).status, 200);
  assert.deepEqual((await call('load_favorites', {}, alice)).body.ids, ['a', 'b']);
  assert.deepEqual((await call('load_favorites', {}, bob)).body.ids, [], 'مفضلة كل مستخدم منفصلة');
  assert.equal((await call('submit_report', { placeId: 'p', reason: 'رقم خاطئ', details: 'x' }, alice)).status, 200);
  assert.equal((await call('submit_report', { reason: '' }, alice)).status, 400);
  assert.equal((await call('submit_suggestion', { name: 'مطعم', category: 'restaurants' }, alice)).status, 200);
  for (let i = 0; i < 25; i++) await call('submit_report', { reason: 'r' }, alice);
  assert.equal((await call('submit_report', { reason: 'r' }, alice)).status, 429, 'حد يومي');
});

test('المالك: منطقة ثم مكان منشور ثم إحصائيات', async () => {
  const rg = await call('upsert_region', { name: 'Amman', type: 'city' }, owner);
  assert.equal(rg.status, 200);
  const r = await call('upsert_place', { place: place({ status: 'published', regionId: rg.body.id }), markVerified: true }, owner);
  assert.equal(r.status, 200);
  assert.equal(r.body.place.region_name, 'Amman');
  assert.ok(r.body.place.verified_at);
  assert.equal((await call('upsert_place', { place: place({ regionId: 'no-such' }) }, owner)).status, 400);
  const st = (await call('stats', {}, owner)).body;
  assert.equal(st.places, 1); assert.equal(st.published, 1); assert.equal(st.regions, 1);
  const lp = (await call('list_places', { limit: 10 }, owner)).body;
  assert.equal(lp.places.length, 1); assert.equal(lp.hasMore, false);
  assert.ok(DB.tables.audit.some((a) => a.action === 'place_create'));
});
test('التعديل لا يمسح التوثيق السابق ما لم يُطلب', async () => {
  const id = (await call('upsert_place', { place: place(), markVerified: true }, owner)).body.place.id;
  const v1 = DB.tables.places[0].verified_at;
  await call('upsert_place', { id, place: place({ name: 'Cafe Renamed' }) }, owner);
  assert.equal(DB.tables.places[0].verified_at, v1);
  assert.equal(DB.tables.places[0].name, 'Cafe Renamed');
});

test('المحرر: مسودات فقط، لا حذف، لا تعديل لمنشور', async () => {
  assert.equal((await call('set_role', { email: 'ALICE@example.com', role: 'editor' }, owner)).status, 200);
  assert.equal(DB.tables.staff[0].email, 'alice@example.com');
  assert.equal((await call('whoami', {}, alice)).body.role, 'editor');
  assert.equal((await call('whoami', {}, tok('u-alice', 'alice@example.com', false))).body.role, null, 'غير مؤكَّد');
  const d = await call('upsert_place', { place: place() }, alice);
  assert.equal(d.status, 200);
  assert.equal((await call('upsert_place', { place: place({ status: 'published' }) }, alice)).status, 400);
  const pub = (await call('upsert_place', { place: place({ name: 'Pub', status: 'published' }) }, owner)).body.place.id;
  assert.equal((await call('upsert_place', { id: pub, place: place({ name: 'Hijack', status: 'draft' }) }, alice)).status, 403);
  assert.equal(DB.tables.places.find((p) => p.id === pub).name, 'Pub');
  assert.equal((await call('delete_place', { id: d.body.place.id }, alice)).status, 403);
  assert.equal((await call('list_staff', {}, alice)).status, 403);
});
test('المالك لا يُعدَّل، والأدمن لا يعيّن أدوارًا، وإزالة الدور تعمل', async () => {
  assert.equal((await call('set_role', { email: OWNER, role: 'none' }, owner)).status, 400);
  assert.equal((await call('set_role', { email: 'x', role: 'admin' }, owner)).status, 400);
  assert.equal((await call('set_role', { email: 'bob@example.com', role: 'owner' }, owner)).status, 400);
  await call('set_role', { email: 'bob@example.com', role: 'admin' }, owner);
  assert.equal((await call('whoami', {}, bob)).body.role, 'admin');
  assert.equal((await call('set_role', { email: 'alice@example.com', role: 'admin' }, bob)).status, 403);
  assert.equal((await call('delete_region', { id: 'zz' }, bob)).status, 200);
  await call('set_role', { email: 'bob@example.com', role: 'none' }, owner);
  assert.equal((await call('whoami', {}, bob)).body.role, null);
  assert.equal((await call('stats', {}, bob)).status, 403);
});

test('الوسائط: رفع موقّع → تسجيل → غلاف → ترتيب → حذف بلا ملفات يتيمة', async () => {
  const id = (await call('upsert_place', { place: place() }, owner)).body.place.id;
  const bad = await call('create_upload', { placeId: id, kind: 'image', contentType: 'image/svg+xml', size: 10, fileName: 'a.svg' }, owner);
  assert.equal(bad.status, 400);
  assert.equal((await call('create_upload', { placeId: id, kind: 'image', contentType: 'image/jpeg', size: 99 * 1024 * 1024, fileName: 'a.jpg' }, owner)).status, 400);
  assert.equal((await call('create_upload', { placeId: 'nope', kind: 'image', contentType: 'image/jpeg', size: 10, fileName: 'a.jpg' }, owner)).status, 404);
  const up = await call('create_upload', { placeId: id, kind: 'image', contentType: 'image/jpeg', size: 1000, fileName: 'a.jpg' }, owner);
  assert.equal(up.status, 200);
  assert.ok(up.body.path.startsWith(`places/${id}/images/`) && up.body.token);
  // ملف لم يُرفع فعليًا لا يُسجَّل
  assert.equal((await call('add_media', { placeId: id, kind: 'image', path: up.body.path }, owner)).status, 400);
  DB.files.add(up.body.path); // العميل رفع الملف
  assert.equal((await call('add_media', { placeId: id, kind: 'image', path: `places/other/images/x.jpg` }, owner)).status, 400);
  assert.equal((await call('add_media', { placeId: id, kind: 'image', path: `places/${id}/images/../../x.jpg` }, owner)).status, 400);
  const m1 = await call('add_media', { placeId: id, kind: 'image', path: up.body.path, alt: 'صورة', source: 'me', license: 'own' }, owner);
  assert.equal(m1.status, 200);
  assert.ok(m1.body.media.url.startsWith('https://proj.supabase.co/storage/v1/object/public/media/places/'));
  const up2 = await call('create_upload', { placeId: id, kind: 'image', contentType: 'image/png', size: 5, fileName: 'b.png' }, owner);
  DB.files.add(up2.body.path);
  const m2 = (await call('add_media', { placeId: id, kind: 'image', path: up2.body.path }, owner)).body.media;
  assert.equal((await call('set_cover', { placeId: id, mediaId: m1.body.media.id }, owner)).status, 200);
  assert.equal(DB.tables.places[0].cover_url, m1.body.media.url);
  assert.equal((await call('set_cover', { placeId: 'other-place', mediaId: m1.body.media.id }, owner)).status, 400);
  assert.equal((await call('reorder_media', { placeId: id, order: [m2.id, m1.body.media.id] }, owner)).status, 200);
  assert.deepEqual((await call('list_media', { placeId: id }, owner)).body.media.map((x) => x.id), [m2.id, m1.body.media.id]);
  // حذف الغلاف: السجل والملف والإشارة في المكان
  const del = await call('delete_media', { placeId: id, mediaId: m1.body.media.id }, owner);
  assert.equal(del.body.fileRemoved, true);
  assert.equal(DB.files.has(m1.body.media.storage_path), false);
  assert.equal(DB.tables.places[0].cover_url, null);
  assert.equal(DB.tables.media.length, 1);
  // فشل حذف الملف: السجل يُحذف ويُسجَّل الملف اليتيم للتنظيف
  DB.failStorageRemove = true;
  const del2 = await call('delete_media', { placeId: id, mediaId: m2.id }, owner);
  assert.equal(del2.body.fileRemoved, false);
  assert.ok(DB.tables.audit.some((a) => a.action === 'orphan_file'));
  assert.equal((await call('delete_media', { placeId: id, mediaId: 'ghost' }, owner)).status, 404);
});
test('المحرر لا يرفع لمكان منشور ولا يحذف وسائط', async () => {
  await call('set_role', { email: 'alice@example.com', role: 'editor' }, owner);
  const pub = (await call('upsert_place', { place: place({ status: 'published' }) }, owner)).body.place.id;
  assert.equal((await call('create_upload', { placeId: pub, kind: 'image', contentType: 'image/png', size: 5, fileName: 'a.png' }, alice)).status, 403);
  const dr = (await call('upsert_place', { place: place({ name: 'Draft' }) }, alice)).body.place.id;
  assert.equal((await call('create_upload', { placeId: dr, kind: 'image', contentType: 'image/png', size: 5, fileName: 'a.png' }, alice)).status, 200);
  assert.equal((await call('delete_media', { placeId: dr, mediaId: 'x' }, alice)).status, 403);
});
test('حذف المكان يحذف وسائطه وملفاته', async () => {
  const id = (await call('upsert_place', { place: place() }, owner)).body.place.id;
  const up = await call('create_upload', { placeId: id, kind: 'video', contentType: 'video/mp4', size: 1000, fileName: 'v.mp4' }, owner);
  DB.files.add(up.body.path);
  await call('add_media', { placeId: id, kind: 'video', path: up.body.path, durationSec: 30 }, owner);
  assert.equal(DB.tables.media[0].duration_sec, 30);
  const r = await call('delete_place', { id }, owner);
  assert.equal(r.body.orphanFiles, 0);
  assert.equal(DB.tables.places.length, 0); assert.equal(DB.tables.media.length, 0); assert.equal(DB.files.has(up.body.path), false);
});

test('صندوق البلاغات والإعدادات وسجل التدقيق', async () => {
  await call('submit_report', { placeId: 'p', reason: 'رابط معطل' }, alice);
  await call('submit_suggestion', { name: 'مطعم جديد' }, alice);
  const ib = (await call('inbox', {}, owner)).body.items;
  assert.equal(ib.length, 2);
  const rep = ib.find((x) => x.kind === 'reports');
  assert.equal((await call('resolve', { kind: 'reports', id: rep.id }, owner)).status, 200);
  assert.equal((await call('resolve', { kind: 'places', id: rep.id }, owner)).status, 400);
  assert.equal((await call('inbox', {}, owner)).body.items.length, 1);
  assert.equal((await call('set_settings', { adsEnabled: false, siteMessage: 'x'.repeat(999) }, owner)).status, 200);
  const s = (await call('get_settings', {}, owner)).body.settings;
  assert.equal(s.adsEnabled, false); assert.equal(s.siteMessage.length, 300);
  const au = (await call('audit_list', {}, owner)).body.entries;
  assert.ok(au.length >= 2 && au.every((a) => a.actor_email));
});
test('CORS والطرق والجسم غير الصالح', async () => {
  const o = await handler(new Request('https://x', { method: 'OPTIONS' }));
  assert.equal(o.status, 204); assert.ok(o.headers.get('access-control-allow-headers').includes('x-firebase-token'));
  assert.equal((await handler(new Request('https://x', { method: 'GET' }))).status, 405);
  const r = await handler(new Request('https://x', { method: 'POST', body: 'not json' }));
  assert.equal(r.status, 400);
  assert.equal((await call('no_such_action', {}, owner)).status, 400);
});
