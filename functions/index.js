'use strict';
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentDeleted } = require('firebase-functions/v2/firestore');
const { defineString } = require('firebase-functions/params');
const logger = require('firebase-functions/logger');
const admin = require('firebase-admin');
const { decideBootstrap, decideSetRole, mergeClaims, storagePathAllowed } = require('./lib/roles');

admin.initializeApp();

// البريد المعتمد للمالك: يُضبط على الخادم فقط (ملف functions/.env غير مرفوع إلى GitHub)، وليس داخل التطبيق.
// مثال محتوى functions/.env:  ADMIN_EMAIL=owner@example.com
const ADMIN_EMAIL = defineString('ADMIN_EMAIL', { description: 'بريد المالك المعتمد (المدير الرئيسي)' });

const fail = (d) => { throw new HttpsError(d.code, d.reason); };

/**
 * bootstrapOwner — تهيئة المالك الأول.
 * لا تأخذ أي مدخلات من العميل. تعتمد على هوية المستدعي الموثّقة فقط.
 */
exports.bootstrapOwner = onCall({ region: 'us-central1', maxInstances: 3 }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'يجب تسجيل الدخول.');
  const userRecord = await admin.auth().getUser(request.auth.uid);
  const d = decideBootstrap({ authToken: request.auth.token, userRecord, adminEmail: ADMIN_EMAIL.value() });
  if (!d.ok) { logger.warn('bootstrapOwner denied', { uid: request.auth.uid, reason: d.reason }); fail(d); }
  await admin.auth().setCustomUserClaims(userRecord.uid, mergeClaims(userRecord.customClaims, d.claims));
  await admin.firestore().collection('audit').add({
    action: 'bootstrap_owner', actorUid: userRecord.uid, targetUid: userRecord.uid,
    at: admin.firestore.FieldValue.serverTimestamp(), by: 'function',
  });
  logger.info('Owner claims set', { uid: userRecord.uid });
  return { ok: true, note: 'سجّل الخروج ثم الدخول (أو حدّث الحالة) لتفعيل الصلاحية.' };
});

/** setRole — المالك فقط: { email, role: 'admin' | 'editor' | 'none' } */
exports.setRole = onCall({ region: 'us-central1', maxInstances: 3 }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'يجب تسجيل الدخول.');
  const email = String(request.data?.email || '').trim();
  const role = String(request.data?.role || '');
  let target = null;
  try { target = email ? await admin.auth().getUserByEmail(email) : null; } catch (_) { target = null; }
  const d = decideSetRole({ callerToken: request.auth.token, targetRecord: target, role, adminEmail: ADMIN_EMAIL.value() });
  if (!d.ok) fail(d);
  await admin.auth().setCustomUserClaims(target.uid, mergeClaims(target.customClaims, d.claims));
  await admin.auth().revokeRefreshTokens(target.uid); // يُجبر على تحديث الـ token بالصلاحيات الجديدة
  await admin.firestore().collection('audit').add({
    action: 'set_role', role, actorUid: request.auth.uid, targetUid: target.uid,
    at: admin.firestore.FieldValue.serverTimestamp(), by: 'function',
  });
  return { ok: true };
});

/** عند حذف سجل وسيط: احذف الملف من Storage كي لا تبقى ملفات يتيمة. */
exports.onMediaDeleted = onDocumentDeleted({ document: 'places/{placeId}/media/{mediaId}', region: 'us-central1' }, async (event) => {
  const data = event.data?.data();
  const path = data?.storagePath;
  if (!storagePathAllowed(event.params.placeId, path)) return;
  try { await admin.storage().bucket().file(path).delete({ ignoreNotFound: true }); }
  catch (e) { logger.error('storage delete failed', { path, e: String(e) }); }
  for (const t of ['thumbPath']) {
    const tp = data?.[t];
    if (storagePathAllowed(event.params.placeId, tp)) {
      try { await admin.storage().bucket().file(tp).delete({ ignoreNotFound: true }); } catch (_) { /* تجاهل */ }
    }
  }
});

/** عند حذف مكان: احذف وسائطه (السجلات + الملفات). */
exports.onPlaceDeleted = onDocumentDeleted({ document: 'places/{placeId}', region: 'us-central1' }, async (event) => {
  const { placeId } = event.params;
  await admin.firestore().recursiveDelete(admin.firestore().collection('places').doc(placeId).collection('media'));
  await admin.storage().bucket().deleteFiles({ prefix: `places/${placeId}/` });
});
