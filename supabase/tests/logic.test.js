import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from '../functions/admin-api/logic.js';

const OWNER = 'bilalalmomani90@gmail.com';

test('المالك: بريد مطابق ومؤكَّد فقط', () => {
  assert.equal(L.roleFor({ email: OWNER, emailVerified: true, adminEmail: OWNER }), 'owner');
  assert.equal(L.roleFor({ email: 'BilalAlmomani90@Gmail.com', emailVerified: true, adminEmail: OWNER }), 'owner');
  assert.equal(L.roleFor({ email: OWNER, emailVerified: false, adminEmail: OWNER }), null, 'غير مؤكَّد');
  assert.equal(L.roleFor({ email: OWNER, emailVerified: 'true', adminEmail: OWNER }), null, 'يجب true منطقية');
  assert.equal(L.roleFor({ email: 'bilalalmomani90+x@gmail.com', emailVerified: true, adminEmail: OWNER }), null);
  assert.equal(L.roleFor({ email: 'x' + OWNER, emailVerified: true, adminEmail: OWNER }), null);
  assert.equal(L.roleFor({ email: OWNER, emailVerified: true, adminEmail: '' }), null, 'بلا ADMIN_EMAIL لا مالك');
  assert.equal(L.roleFor({ email: '', emailVerified: true, adminEmail: '' }), null);
});

test('المحرر والمدير من جدول staff فقط وبعد تأكيد البريد', () => {
  assert.equal(L.roleFor({ email: 'a@b.com', emailVerified: true, adminEmail: OWNER, staffRole: 'editor' }), 'editor');
  assert.equal(L.roleFor({ email: 'a@b.com', emailVerified: true, adminEmail: OWNER, staffRole: 'admin' }), 'admin');
  assert.equal(L.roleFor({ email: 'a@b.com', emailVerified: false, adminEmail: OWNER, staffRole: 'admin' }), null);
  assert.equal(L.roleFor({ email: 'a@b.com', emailVerified: true, adminEmail: OWNER, staffRole: 'owner' }), null, 'لا يُمنح owner من الجدول');
  assert.equal(L.roleFor({ email: 'a@b.com', emailVerified: true, adminEmail: OWNER, staffRole: undefined }), null);
});

test('authorize: المستخدم العادي لا يصل لأي إجراء إداري', () => {
  const user = { signedIn: true, emailVerified: true, role: null };
  for (const [action, need] of Object.entries(L.ACTIONS)) {
    const r = L.authorize(action, user);
    if (need === 'signed_in' || need === 'verified') assert.equal(r.ok, true, action);
    else assert.equal(r.ok, false, `${action} يجب أن يُرفض للمستخدم العادي`);
  }
});
test('authorize: الزائر بلا هوية يُرفض 401، وغير المؤكَّد 403', () => {
  assert.equal(L.authorize('stats', { signedIn: false }).status, 401);
  assert.equal(L.authorize('whoami', { signedIn: false }).status, 401);
  assert.equal(L.authorize('whoami', { signedIn: true, emailVerified: false }).ok, true);
  assert.equal(L.authorize('submit_report', { signedIn: true, emailVerified: false }).status, 403);
  assert.equal(L.authorize('stats', { signedIn: true, emailVerified: false, role: 'owner' }).status, 403);
});
test('authorize: تدرّج الأدوار', () => {
  const ctx = (role) => ({ signedIn: true, emailVerified: true, role });
  assert.equal(L.authorize('upsert_place', ctx('editor')).ok, true);
  assert.equal(L.authorize('delete_place', ctx('editor')).ok, false);
  assert.equal(L.authorize('delete_media', ctx('editor')).ok, false);
  assert.equal(L.authorize('set_role', ctx('admin')).ok, false, 'الأدمن لا يعيّن الأدوار');
  assert.equal(L.authorize('set_role', ctx('owner')).ok, true);
  assert.equal(L.authorize('set_settings', ctx('admin')).ok, true);
  assert.equal(L.authorize('inbox', ctx('editor')).ok, false);
  assert.equal(L.authorize('غير_موجود', ctx('owner')).status, 400);
});

const base = { name: 'مطعم تجريبي', category: 'restaurants', status: 'draft' };
test('المحرر لا ينشر', () => {
  assert.ok(L.sanitizePlace({ ...base, status: 'published' }, 'editor').errors.length > 0);
  assert.equal(L.sanitizePlace({ ...base, status: 'published' }, 'admin').errors.length, 0);
  assert.ok(L.sanitizePlace(base, null).errors.length > 0);
  assert.equal(L.editorMayTouch('editor', 'published', 'draft'), false, 'لا يعدّل منشورًا');
  assert.equal(L.editorMayTouch('editor', null, 'draft'), true);
  assert.equal(L.editorMayTouch('editor', 'draft', 'published'), false);
  assert.equal(L.editorMayTouch('admin', 'published', 'archived'), true);
});
test('الحقول الفارغة null ولا تُخترع قيم', () => {
  const { row, errors } = L.sanitizePlace(base, 'admin');
  assert.equal(errors.length, 0);
  for (const k of ['phone', 'address', 'lat', 'lng', 'website', 'price_level', 'kids_friendly', 'source_url', 'region_id']) assert.equal(row[k], null, k);
  assert.deepEqual(row.features, []);
  assert.equal(row.featured, false);
});
test('تحقق الهاتف والرابط والإحداثيات والسعر والمنطقة', () => {
  const bad = (o) => L.sanitizePlace({ ...base, ...o }, 'admin').errors.length > 0;
  assert.ok(bad({ phone: 'abc' })); assert.ok(bad({ website: 'ftp://x' })); assert.ok(bad({ sourceUrl: 'javascript:alert(1)' }));
  assert.ok(bad({ lat: 10 })); assert.ok(bad({ lat: 100, lng: 10 })); assert.ok(bad({ priceLevel: 4 }));
  assert.ok(bad({ regionId: 'A b' })); assert.ok(bad({ category: 'x' }));
  assert.ok(!bad({ phone: '+962 6 123 4567', website: 'https://example.com', lat: 31.9, lng: 35.9, priceLevel: '2', regionId: 'amman' }));
});
test('القوائم تُصفّى وتُقصّ', () => {
  const { row } = L.sanitizePlace({ ...base, features: ['wifi', 'x', 'wifi'], ageGroups: ['kids', 'bad'], activities: 'أ\n\n ب \n' + 'x\n'.repeat(40) }, 'admin');
  assert.deepEqual(row.features, ['wifi']); assert.deepEqual(row.age_groups, ['kids']);
  assert.equal(row.activities.length, 30); assert.equal(row.activities[0], 'أ');
});
test('الدور لا يأتي من جسم الطلب', () => {
  const { row } = L.sanitizePlace({ ...base, role: 'owner', status: 'draft', admin: true }, 'editor');
  assert.equal('role' in row, false); assert.equal('admin' in row, false);
});

test('الرفع: صيغة وحجم', () => {
  assert.equal(L.validateUpload({ kind: 'image', contentType: 'image/jpeg', size: 1000 }), null);
  assert.ok(L.validateUpload({ kind: 'image', contentType: 'image/svg+xml', size: 1000 }));
  assert.ok(L.validateUpload({ kind: 'image', contentType: 'text/html', size: 10 }));
  assert.ok(L.validateUpload({ kind: 'image', contentType: 'image/png', size: 11 * 1024 * 1024 }));
  assert.equal(L.validateUpload({ kind: 'video', contentType: 'video/mp4', size: 59 * 1024 * 1024 }), null);
  assert.ok(L.validateUpload({ kind: 'video', contentType: 'video/mp4', size: 61 * 1024 * 1024 }));
  assert.ok(L.validateUpload({ kind: 'video', contentType: 'video/x-msvideo', size: 10 }));
  assert.ok(L.validateUpload({ kind: 'image', contentType: 'image/png', size: 0 }));
  assert.ok(L.validateUpload({ kind: 'audio', contentType: 'audio/mp3', size: 10 }));
  assert.ok(L.validateUpload({ kind: 'image', contentType: 'image/png', size: NaN }));
});
test('مسار التخزين: يخص المكان ونوعه ولا يخرج منه', () => {
  const name = L.safeFileName('Photo Ünï.JPG', 1700000000000, 0.5);
  assert.match(name, /^[A-Za-z0-9._-]+$/);
  const p = L.storagePathFor('petra-1', 'image', name);
  assert.deepEqual(L.parseMediaPath('petra-1', 'image', p), { dir: 'places/petra-1/images', file: name });
  assert.equal(L.parseMediaPath('other', 'image', p), null, 'مكان مختلف');
  assert.equal(L.parseMediaPath('petra-1', 'video', p), null, 'نوع مختلف');
  assert.equal(L.parseMediaPath('petra-1', 'image', 'places/petra-1/images/../../x/a.jpg'), null);
  assert.equal(L.parseMediaPath('petra-1', 'image', 'places/petra-1/images/a/b.jpg'), null);
  assert.equal(L.parseMediaPath('petra-1', 'image', 'places/petra-1/images/.hidden'), null);
  assert.equal(L.parseMediaPath('petra-1', 'image', 'app-assets/a.jpg'), null);
  assert.equal(L.parseMediaPath('../x', 'image', 'places/../x/images/a.jpg'), null);
  assert.equal(L.parseMediaPath('petra-1', 'file', p), null);
});
test('بيانات الوسيط وتقييد المدة', () => {
  assert.equal(L.sanitizeMediaMeta({ durationSec: 99999 }).duration_sec, null);
  assert.equal(L.sanitizeMediaMeta({ durationSec: 42 }).duration_sec, 42);
  assert.equal(L.sanitizeMediaMeta({ title: '  ' }).title, null);
});
test('المفضلة والبلاغات والاقتراحات', () => {
  assert.deepEqual(L.sanitizeIds(['a', 'a', 'b c', 'sample-petra']), ['a', 'sample-petra']);
  assert.equal(L.sanitizeIds('x'), null);
  assert.equal(L.sanitizeIds(Array.from({ length: 900 }, (_, i) => `id${i}`)).length, 500);
  assert.ok(L.sanitizeReport({ reason: ' ' }).errors.length);
  assert.ok(L.sanitizeSuggestion({ name: '' }).errors.length);
  assert.equal(L.sanitizeSuggestion({ name: 'x', category: 'hack' }).row.category, null);
});
test('المناطق والبريد والصفحات', () => {
  assert.ok(L.sanitizeRegion({ name: 'x', type: 'planet' }).errors.length);
  assert.equal(L.sanitizeRegion({ name: 'عمّان', type: 'city' }).errors.length, 0);
  assert.equal(L.validEmail('a@b.co'), true); assert.equal(L.validEmail('a b@c'), false);
  assert.deepEqual(L.pageParams({ offset: -5, limit: 9999 }), { offset: 0, limit: 50 });
  assert.deepEqual(L.pageParams({ offset: 30, limit: 10 }), { offset: 30, limit: 10 });
  assert.match(L.slugId('Cafe Amman!!', 1), /^cafe-amman-1$/);
  assert.match(L.slugId('مقهى', 5), /^place-5$/);
  assert.ok(L.validId(L.slugId('x y z')));
});
