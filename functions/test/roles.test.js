'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { decideBootstrap, decideSetRole, mergeClaims, storagePathAllowed } = require('../lib/roles');

const OWNER = 'bilalalmomani90@gmail.com';
const tok = (o) => ({ email: OWNER, email_verified: true, ...o });
const rec = (o) => ({ uid: 'u1', email: OWNER, emailVerified: true, disabled: false, ...o });

test('المالك المؤكَّد ببريد مطابق يحصل على admin+owner', () => {
  const d = decideBootstrap({ authToken: tok(), userRecord: rec(), adminEmail: OWNER });
  assert.equal(d.ok, true);
  assert.deepEqual(d.claims, { admin: true, owner: true });
});
test('المطابقة غير حساسة لحالة الأحرف والمسافات', () => {
  assert.equal(decideBootstrap({ authToken: tok({ email: 'BilalAlmomani90@Gmail.com' }), userRecord: rec({ email: 'BilalAlmomani90@Gmail.com' }), adminEmail: ` ${OWNER} ` }).ok, true);
});
test('بريد مختلف مرفوض', () => {
  const d = decideBootstrap({ authToken: tok({ email: 'x@y.com' }), userRecord: rec({ email: 'x@y.com' }), adminEmail: OWNER });
  assert.equal(d.ok, false); assert.equal(d.code, 'permission-denied');
});
test('بريد مشابه (إضافة نقطة أو لاحقة) مرفوض', () => {
  for (const e of ['bilal.almomani90@gmail.com', 'bilalalmomani90@gmail.com.evil.com', 'xbilalalmomani90@gmail.com', 'bilalalmomani90+a@gmail.com']) {
    assert.equal(decideBootstrap({ authToken: tok({ email: e }), userRecord: rec({ email: e }), adminEmail: OWNER }).ok, false, e);
  }
});
test('بريد غير مؤكَّد مرفوض حتى لو مطابق', () => {
  assert.equal(decideBootstrap({ authToken: tok({ email_verified: false }), userRecord: rec(), adminEmail: OWNER }).ok, false);
  assert.equal(decideBootstrap({ authToken: tok(), userRecord: rec({ emailVerified: false }), adminEmail: OWNER }).ok, false);
});
test('اختلاف بريد التوكن عن سجل المستخدم مرفوض', () => {
  assert.equal(decideBootstrap({ authToken: tok({ email: 'a@b.com' }), userRecord: rec(), adminEmail: OWNER }).ok, false);
});
test('حساب معطّل مرفوض', () => {
  assert.equal(decideBootstrap({ authToken: tok(), userRecord: rec({ disabled: true }), adminEmail: OWNER }).ok, false);
});
test('ADMIN_EMAIL غير مضبوط → لا أحد يصبح مديرًا', () => {
  for (const v of ['', undefined, null, '   ']) assert.equal(decideBootstrap({ authToken: tok(), userRecord: rec(), adminEmail: v }).ok, false);
});
test('بدون هوية مرفوض', () => {
  assert.equal(decideBootstrap({ authToken: null, userRecord: null, adminEmail: OWNER }).ok, false);
});

const ownerTok = { email_verified: true, owner: true, admin: true };
const target = { uid: 't', email: 'ed@x.com', emailVerified: true };
test('المالك يعيّن محررًا', () => {
  const d = decideSetRole({ callerToken: ownerTok, targetRecord: target, role: 'editor', adminEmail: OWNER });
  assert.deepEqual(d, { ok: true, claims: { editor: true } });
});
test('مدير غير مالك لا يعيّن أدوارًا', () => {
  assert.equal(decideSetRole({ callerToken: { email_verified: true, admin: true }, targetRecord: target, role: 'editor', adminEmail: OWNER }).ok, false);
});
test('مستخدم عادي أو محرر لا يعيّن أدوارًا', () => {
  assert.equal(decideSetRole({ callerToken: { email_verified: true }, targetRecord: target, role: 'admin', adminEmail: OWNER }).ok, false);
  assert.equal(decideSetRole({ callerToken: { email_verified: true, editor: true }, targetRecord: target, role: 'admin', adminEmail: OWNER }).ok, false);
});
test('لا يمكن تعديل صلاحيات المالك', () => {
  assert.equal(decideSetRole({ callerToken: ownerTok, targetRecord: { ...target, email: OWNER }, role: 'none', adminEmail: OWNER }).ok, false);
});
test('هدف غير مؤكَّد البريد أو دور غير معروف مرفوض', () => {
  assert.equal(decideSetRole({ callerToken: ownerTok, targetRecord: { ...target, emailVerified: false }, role: 'editor', adminEmail: OWNER }).ok, false);
  assert.equal(decideSetRole({ callerToken: ownerTok, targetRecord: target, role: 'superuser', adminEmail: OWNER }).ok, false);
});
test('mergeClaims يزيل الأدوار القديمة ويحتفظ بغيرها', () => {
  assert.deepEqual(mergeClaims({ admin: true, editor: true, owner: true, foo: 1 }, { editor: true }), { foo: 1, editor: true });
});
test('storagePathAllowed يقيّد الحذف بمسارات المكان نفسه', () => {
  assert.equal(storagePathAllowed('p1', 'places/p1/images/a.jpg'), true);
  assert.equal(storagePathAllowed('p1', 'places/p1/videos/a.mp4'), true);
  assert.equal(storagePathAllowed('p1', 'places/p2/images/a.jpg'), false);
  assert.equal(storagePathAllowed('p1', 'app-assets/logo.png'), false);
  assert.equal(storagePathAllowed('p1', undefined), false);
});
