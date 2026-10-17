import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from './logic.js';

const base = { name: 'مطعم تجريبي', category: 'restaurants', status: 'draft' };

test('staffRole: لا دور بدون تأكيد البريد أو claims', () => {
  assert.equal(L.staffRole({ admin: true }, false), null);
  assert.equal(L.staffRole({}, true), null);
  assert.equal(L.staffRole(null, true), null);
  assert.equal(L.staffRole({ admin: 'true' }, true), null); // يجب أن تكون true منطقية
  assert.equal(L.staffRole({ admin: true, owner: true }, true), 'owner');
  assert.equal(L.staffRole({ admin: true }, true), 'admin');
  assert.equal(L.staffRole({ editor: true }, true), 'editor');
});
test('المحرر لا ينشر ولا يحذف', () => {
  assert.deepEqual(L.allowedStatuses('editor'), ['draft', 'review']);
  assert.equal(L.canDelete('editor'), false);
  assert.equal(L.canDelete('admin'), true);
  assert.deepEqual(L.allowedStatuses(null), []);
  assert.ok(L.validatePlaceForm({ ...base, status: 'published' }, 'editor').errors.length > 0);
  assert.equal(L.validatePlaceForm({ ...base, status: 'published' }, 'admin').errors.length, 0);
});
test('الحقول الفارغة تصبح null ولا تُخترع قيم', () => {
  const { doc, errors } = L.validatePlaceForm(base, 'admin');
  assert.equal(errors.length, 0);
  assert.equal(doc.phone, null); assert.equal(doc.lat, null); assert.equal(doc.priceLevel, null); assert.equal(doc.kidsFriendly, null);
  assert.deepEqual(doc.features, []);
});
test('تحقق الهاتف والموقع والإحداثيات والسعر', () => {
  assert.ok(L.validatePlaceForm({ ...base, phone: 'abc' }, 'admin').errors.length);
  assert.equal(L.validatePlaceForm({ ...base, phone: '+962 6 123 4567' }, 'admin').errors.length, 0);
  assert.ok(L.validatePlaceForm({ ...base, website: 'javascript:alert(1)' }, 'admin').errors.length);
  assert.ok(L.validatePlaceForm({ ...base, lat: 31.9 }, 'admin').errors.length); // خط الطول مفقود
  assert.ok(L.validatePlaceForm({ ...base, lat: 100, lng: 35 }, 'admin').errors.length);
  assert.ok(L.validatePlaceForm({ ...base, priceLevel: 5 }, 'admin').errors.length);
  assert.ok(L.validatePlaceForm({ ...base, category: 'nope' }, 'admin').errors.length);
  assert.ok(L.validatePlaceForm({ ...base, name: '  ' }, 'admin').errors.length);
});
test('القوائم تُنقّى من قيم غير معروفة', () => {
  const { doc } = L.validatePlaceForm({ ...base, features: ['wifi', 'hacker'], ageGroups: ['kids', 'x'], activities: 'a\n\n b ' }, 'admin');
  assert.deepEqual(doc.features, ['wifi']); assert.deepEqual(doc.ageGroups, ['kids']); assert.deepEqual(doc.activities, ['a', 'b']);
});
test('حدود الرفع تطابق storage.rules', () => {
  assert.equal(L.validateUpload({ type: 'image/png', size: 1000 }, 'image'), null);
  assert.ok(L.validateUpload({ type: 'image/gif', size: 1000 }, 'image'));
  assert.ok(L.validateUpload({ type: 'image/png', size: 11 * 1048576 }, 'image'));
  assert.equal(L.validateUpload({ type: 'video/mp4', size: 50 * 1048576 }, 'video'), null);
  assert.ok(L.validateUpload({ type: 'video/mp4', size: 61 * 1048576 }, 'video'));
  assert.ok(L.validateUpload({ type: 'application/pdf', size: 10 }, 'image'));
  assert.ok(L.validateUpload({ type: 'image/png', size: 0 }, 'image'));
});
test('اسم الملف الآمن يطابق regex القواعد', () => {
  const n = L.safeFileName('../../صورة غريبة!!.JPG', 1700000000000, 0.5);
  assert.match(n, /^[A-Za-z0-9._-]{1,120}$/);
  assert.ok(n.endsWith('.jpg'));
  assert.match(L.safeFileName('x'), /^[A-Za-z0-9._-]{1,120}$/);
});
test('مسار التخزين', () => {
  assert.equal(L.storagePathFor('p1', 'image', 'a.jpg'), 'places/p1/images/a.jpg');
  assert.equal(L.storagePathFor('p1', 'video', 'a.mp4'), 'places/p1/videos/a.mp4');
});
test('إعادة الترتيب', () => {
  const r = L.reorder([{ id: 'a' }, { id: 'b' }, { id: 'c' }], 0, 2);
  assert.deepEqual(r.map((x) => x.id), ['b', 'c', 'a']);
  assert.deepEqual(r.map((x) => x.order), [0, 1, 2]);
  assert.deepEqual(L.reorder([{ id: 'a' }], 0, 5).map((x) => x.id), ['a']);
});
test('رسائل الحالة', () => {
  assert.equal(L.authStatusMessage({ signedIn: false }).kind, 'signin');
  assert.equal(L.authStatusMessage({ signedIn: true, emailVerified: false }).kind, 'verify');
  assert.equal(L.authStatusMessage({ signedIn: true, emailVerified: true, role: null }).kind, 'norole');
  assert.equal(L.authStatusMessage({ signedIn: true, emailVerified: true, role: 'admin' }).kind, 'ok');
});
