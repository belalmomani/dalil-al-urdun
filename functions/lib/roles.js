'use strict';
/**
 * منطق نقي (بلا Firebase) لقرارات الصلاحيات في الخادم — قابل للاختبار بدون أي خدمة.
 * القاعدة الذهبية: لا يُمنح شيء بسبب نص مُرسل من العميل. المدخلات الموثوقة فقط:
 *  - request.auth.token (يوقّعه Firebase)،
 *  - سجل المستخدم من Admin SDK،
 *  - ADMIN_EMAIL من إعدادات الخادم.
 */

const normalizeEmail = (e) => (typeof e === 'string' ? e.trim().toLowerCase() : '');

/** هل يحق لهذا الحساب أن يصبح المالك؟ */
function decideBootstrap({ authToken, userRecord, adminEmail }) {
  const configured = normalizeEmail(adminEmail);
  if (!configured) return { ok: false, code: 'failed-precondition', reason: 'ADMIN_EMAIL غير مضبوط في إعدادات الخادم.' };
  if (!authToken || !userRecord) return { ok: false, code: 'unauthenticated', reason: 'يجب تسجيل الدخول.' };
  // ١) التوكن نفسه يقول إن البريد مؤكَّد
  if (authToken.email_verified !== true) return { ok: false, code: 'permission-denied', reason: 'البريد غير مؤكَّد في التوكن.' };
  // ٢) نتحقق ثانية من سجل المستخدم في الخادم (لا نكتفي بالتوكن)
  if (userRecord.emailVerified !== true) return { ok: false, code: 'permission-denied', reason: 'البريد غير مؤكَّد.' };
  if (userRecord.disabled === true) return { ok: false, code: 'permission-denied', reason: 'الحساب معطّل.' };
  // ٣) تطابق تام للبريد في السجل والتوكن مع البريد المعتمد
  const a = normalizeEmail(userRecord.email);
  const b = normalizeEmail(authToken.email);
  if (!a || a !== b || a !== configured) return { ok: false, code: 'permission-denied', reason: 'هذا الحساب ليس بريد المالك المعتمد.' };
  return { ok: true, claims: { admin: true, owner: true } };
}

const ASSIGNABLE = new Set(['admin', 'editor', 'none']);

/** هل يحق للمستدعي تغيير دور الهدف؟ المالك فقط. */
function decideSetRole({ callerToken, targetRecord, role, adminEmail }) {
  if (!callerToken) return { ok: false, code: 'unauthenticated', reason: 'يجب تسجيل الدخول.' };
  if (callerToken.email_verified !== true || callerToken.owner !== true || callerToken.admin !== true) {
    return { ok: false, code: 'permission-denied', reason: 'المالك فقط يستطيع تعيين الأدوار.' };
  }
  if (!ASSIGNABLE.has(role)) return { ok: false, code: 'invalid-argument', reason: 'دور غير معروف.' };
  if (!targetRecord) return { ok: false, code: 'not-found', reason: 'لا يوجد مستخدم بهذا البريد.' };
  if (targetRecord.emailVerified !== true) return { ok: false, code: 'failed-precondition', reason: 'بريد المستخدم المستهدف غير مؤكَّد.' };
  if (normalizeEmail(targetRecord.email) === normalizeEmail(adminEmail)) {
    return { ok: false, code: 'permission-denied', reason: 'لا يمكن تغيير صلاحيات المالك.' };
  }
  const claims = role === 'admin' ? { admin: true } : role === 'editor' ? { editor: true } : {};
  return { ok: true, claims };
}

/** يبني claims الجديدة: يزيل الأدوار القديمة (admin/editor/owner) ويحتفظ بغيرها. */
function mergeClaims(existing, next) {
  const kept = { ...(existing || {}) };
  delete kept.admin; delete kept.editor; delete kept.owner;
  return { ...kept, ...next };
}

/** مسارات Storage المسموح للدالة بحذفها لمكان معيّن (يمنع حذف مسارات أخرى بالخطأ). */
function storagePathAllowed(placeId, path) {
  return typeof path === 'string' && path.startsWith(`places/${placeId}/images/`) || (typeof path === 'string' && path.startsWith(`places/${placeId}/videos/`));
}

module.exports = { normalizeEmail, decideBootstrap, decideSetRole, mergeClaims, storagePathAllowed };
