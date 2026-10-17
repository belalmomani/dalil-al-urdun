// اختبارات قواعد الأمان على محاكي Firebase. تعمل في GitHub Actions (انظر .github/workflows/ci.yml)
// أو محليًا: cd tests && npm install && npm test  (يتطلب Java لتشغيل المحاكي).
// ملاحظة صدق: هذه الاختبارات كُتبت لكنها لم تُشغَّل داخل بيئة التطوير التي أُنشئ فيها المشروع (لا وصول لشبكة npm/المحاكي هناك).
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, addDoc, getDocs, query, where } from 'firebase/firestore';
import { ref, uploadBytes, deleteObject, getBytes } from 'firebase/storage';
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';

let env;
const place = (o = {}) => ({ name: 'مكان', category: 'restaurants', status: 'published', ...o });

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-jordan-guide',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
    storage: { rules: readFileSync('../storage.rules', 'utf8'), host: '127.0.0.1', port: 9199 },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore(); await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'places/pub'), place());
    await setDoc(doc(db, 'places/draft'), place({ status: 'draft' }));
    await setDoc(doc(db, 'places/pub/media/m1'), { type: 'image', url: 'https://x/y.jpg', storagePath: 'places/pub/images/a.jpg' });
    await setDoc(doc(db, 'places/draft/media/m2'), { type: 'image', url: 'https://x/z.jpg', storagePath: 'places/draft/images/b.jpg' });
  });
});

const guest = () => env.unauthenticatedContext();
const user = () => env.authenticatedContext('u1', { email: 'u@x.com', email_verified: true });
const admin = () => env.authenticatedContext('a1', { email: 'a@x.com', email_verified: true, admin: true, owner: true });
const adminUnverified = () => env.authenticatedContext('a2', { email: 'a@x.com', email_verified: false, admin: true });
const editor = () => env.authenticatedContext('e1', { email: 'e@x.com', email_verified: true, editor: true });

// ---------- القراءة
test('الزائر يقرأ المنشور فقط', async () => {
  const db = guest().firestore();
  await assertSucceeds(getDoc(doc(db, 'places/pub')));
  await assertFails(getDoc(doc(db, 'places/draft')));
  await assertSucceeds(getDocs(query(collection(db, 'places'), where('status', '==', 'published'))));
  await assertFails(getDocs(collection(db, 'places'))); // استعلام بلا فلتر يكشف المسودات
});
test('وسائط المكان المنشور فقط ظاهرة للزائر', async () => {
  const db = guest().firestore();
  await assertSucceeds(getDoc(doc(db, 'places/pub/media/m1')));
  await assertFails(getDoc(doc(db, 'places/draft/media/m2')));
});
test('الطاقم يرى المسودات', async () => {
  await assertSucceeds(getDoc(doc(admin().firestore(), 'places/draft')));
  await assertSucceeds(getDoc(doc(editor().firestore(), 'places/draft')));
});

// ---------- منع الكتابة لغير المصرّح
test('الزائر والمستخدم العادي لا يكتبون الأماكن', async () => {
  for (const ctx of [guest(), user()]) {
    const db = ctx.firestore();
    await assertFails(setDoc(doc(db, 'places/new'), place()));
    await assertFails(updateDoc(doc(db, 'places/pub'), { name: 'x' }));
    await assertFails(deleteDoc(doc(db, 'places/pub')));
  }
});
test('المستخدم لا يستطيع منح نفسه صلاحية عبر وثائق users أو غيرها', async () => {
  const db = user().firestore();
  await assertFails(setDoc(doc(db, 'users/u1'), { favorites: [], role: 'admin' }));
  await assertFails(setDoc(doc(db, 'users/u1'), { favorites: [], admin: true }));
  await assertFails(setDoc(doc(db, 'roles/u1'), { admin: true }));
  await assertFails(setDoc(doc(db, 'settings/public'), { adsEnabled: false }));
});
test('مدير ببريد غير مؤكَّد لا يملك صلاحيات', async () => {
  await assertFails(setDoc(doc(adminUnverified().firestore(), 'places/new'), place()));
});
test('المدير يكتب ويحذف', async () => {
  const db = admin().firestore();
  await assertSucceeds(setDoc(doc(db, 'places/new'), place()));
  await assertSucceeds(deleteDoc(doc(db, 'places/pub')));
});
test('تحقق الحقول يرفض تصنيفًا أو حالة غير صالحة', async () => {
  const db = admin().firestore();
  await assertFails(setDoc(doc(db, 'places/bad1'), place({ category: 'hacking' })));
  await assertFails(setDoc(doc(db, 'places/bad2'), place({ status: 'live' })));
  await assertFails(setDoc(doc(db, 'places/bad3'), place({ name: '' })));
});

// ---------- المحرر
test('المحرر: مسودة ومراجعة فقط، لا نشر ولا حذف ولا تعديل منشور', async () => {
  const db = editor().firestore();
  await assertSucceeds(setDoc(doc(db, 'places/e-new'), place({ status: 'draft' })));
  await assertSucceeds(updateDoc(doc(db, 'places/draft'), { status: 'review' }));
  await assertFails(setDoc(doc(db, 'places/e-pub'), place({ status: 'published' })));
  await assertFails(updateDoc(doc(db, 'places/pub'), { name: 'x' }));
  await assertFails(updateDoc(doc(db, 'places/draft'), { status: 'published' }));
  await assertFails(deleteDoc(doc(db, 'places/draft')));
});
test('المحرر يضيف وسائط للمسودة لا للمنشور', async () => {
  const db = editor().firestore();
  const m = (p) => ({ type: 'image', url: 'https://x/a.jpg', storagePath: `places/${p}/images/a.jpg` });
  await assertSucceeds(setDoc(doc(db, 'places/draft/media/new'), m('draft')));
  await assertFails(setDoc(doc(db, 'places/pub/media/new'), m('pub')));
});
test('مسار storagePath يجب أن يخص نفس المكان', async () => {
  const db = admin().firestore();
  await assertFails(setDoc(doc(db, 'places/pub/media/bad'), { type: 'image', url: 'https://x', storagePath: 'places/other/images/a.jpg' }));
});

// ---------- المفضلة والبلاغات والتدقيق
test('المستخدم يكتب مفضلته فقط', async () => {
  const db = user().firestore();
  await assertSucceeds(setDoc(doc(db, 'users/u1'), { favorites: ['a'], updatedAt: new Date() }));
  await assertFails(setDoc(doc(db, 'users/other'), { favorites: ['a'], updatedAt: new Date() }));
  await assertFails(getDoc(doc(db, 'users/other')));
});
test('البلاغات: إنشاء بهوية صاحبها فقط، والقراءة للطاقم', async () => {
  const db = user().firestore();
  const r = { placeId: 'pub', reason: 'هاتف خاطئ', details: '', reporterUid: 'u1', status: 'open', createdAt: new Date() };
  await assertSucceeds(addDoc(collection(db, 'reports'), r));
  await assertFails(addDoc(collection(db, 'reports'), { ...r, reporterUid: 'someone' }));
  await assertFails(addDoc(collection(db, 'reports'), { ...r, status: 'resolved' }));
  await assertFails(getDocs(collection(db, 'reports')));
  await assertFails(addDoc(collection(guest().firestore(), 'reports'), r));
});
test('سجل التدقيق: لا تعديل ولا حذف، ولا إنشاء بهوية شخص آخر', async () => {
  const db = admin().firestore();
  const ref0 = await assertSucceeds(addDoc(collection(db, 'audit'), { action: 'x', actorUid: 'a1' }));
  await assertFails(updateDoc(ref0, { action: 'y' }));
  await assertFails(deleteDoc(ref0));
  await assertFails(addDoc(collection(db, 'audit'), { action: 'x', actorUid: 'someone-else' }));
  await assertFails(getDocs(collection(user().firestore(), 'audit')));
});
test('settings/public علني للقراءة ومقفل للكتابة', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'settings/public'), { adsEnabled: true }));
  await assertSucceeds(getDoc(doc(guest().firestore(), 'settings/public')));
  await assertFails(setDoc(doc(editor().firestore(), 'settings/public'), { adsEnabled: false }));
  await assertSucceeds(setDoc(doc(admin().firestore(), 'settings/public'), { adsEnabled: false }));
});

// ---------- Storage
const png = new Uint8Array([1, 2, 3]);
test('Storage: الزائر والمستخدم العادي لا يرفعون', async () => {
  for (const ctx of [guest(), user()]) {
    await assertFails(uploadBytes(ref(ctx.storage(), 'places/p1/images/a.png'), png, { contentType: 'image/png' }));
  }
});
test('Storage: الطاقم يرفع صورة صالحة ويُرفض النوع والحجم والاسم الخاطئ', async () => {
  const st = editor().storage();
  await assertSucceeds(uploadBytes(ref(st, 'places/p1/images/a.png'), png, { contentType: 'image/png' }));
  await assertFails(uploadBytes(ref(st, 'places/p1/images/a.gif'), png, { contentType: 'image/gif' }));
  await assertFails(uploadBytes(ref(st, 'places/p1/images/a.exe'), png, { contentType: 'application/octet-stream' }));
  await assertFails(uploadBytes(ref(st, 'places/p1/images/bad name!.png'), png, { contentType: 'image/png' }));
  await assertFails(uploadBytes(ref(st, 'places/p1/images/big.png'), new Uint8Array(11 * 1024 * 1024), { contentType: 'image/png' }));
  await assertFails(uploadBytes(ref(st, 'app-assets/logo.png'), png, { contentType: 'image/png' })); // للمدير فقط
  await assertFails(uploadBytes(ref(st, 'secret/x.png'), png, { contentType: 'image/png' }));
});
test('Storage: القراءة العامة للصور والحذف للطاقم فقط', async () => {
  await env.withSecurityRulesDisabled((c) => uploadBytes(ref(c.storage(), 'places/p1/images/a.png'), png, { contentType: 'image/png' }));
  await assertSucceeds(getBytes(ref(guest().storage(), 'places/p1/images/a.png')));
  await assertFails(deleteObject(ref(user().storage(), 'places/p1/images/a.png')));
  await assertSucceeds(deleteObject(ref(admin().storage(), 'places/p1/images/a.png')));
});
test('Storage: فيديو MP4 مقبول وغيره مرفوض', async () => {
  const st = admin().storage();
  await assertSucceeds(uploadBytes(ref(st, 'places/p1/videos/v.mp4'), png, { contentType: 'video/mp4' }));
  await assertFails(uploadBytes(ref(st, 'places/p1/videos/v.avi'), png, { contentType: 'video/x-msvideo' }));
});
