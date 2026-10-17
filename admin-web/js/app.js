import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail, sendEmailVerification } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import { getFirestore, collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, query, where, orderBy, limit, startAfter, serverTimestamp, getCountFromServer, collectionGroup } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js';
import { getStorage, ref, uploadBytesResumable, getDownloadURL, deleteObject } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-storage.js';
import { getFunctions, httpsCallable } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-functions.js';
import { firebaseConfig, FUNCTIONS_REGION } from '../firebase-config.js';
import * as L from './logic.js';

const $app = document.getElementById('app');
const $who = document.getElementById('who');

// بناء DOM بدون innerHTML لمنع XSS (كل النصوص تُدرج كنص).
function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  return el;
}
const show = (...nodes) => { $app.replaceChildren(...nodes.flat().filter(Boolean)); };
const msg = (text, kind = 'err') => h('div', { class: `msg ${kind}`, role: kind === 'err' ? 'alert' : 'status' }, text);

const configured = !!firebaseConfig.apiKey && !!firebaseConfig.projectId;
if (!configured) {
  show(h('div', { class: 'card' }, h('h2', {}, 'الإعداد مطلوب'), msg('املأ ملف admin-web/firebase-config.js ببيانات تطبيق الويب من Firebase Console ثم أعد التحميل. (راجع README)', 'warn')));
  throw new Error('firebase-config.js غير مكتمل');
}

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);
const fns = getFunctions(app, FUNCTIONS_REGION);

let S = { user: null, claims: {}, role: null, tab: 'stats', flash: null };

async function refreshClaims(force = true) {
  if (!auth.currentUser) { S.claims = {}; S.role = null; return; }
  const r = await auth.currentUser.getIdTokenResult(force);
  S.claims = r.claims;
  S.role = L.staffRole(r.claims, auth.currentUser.emailVerified);
}

onAuthStateChanged(auth, async (u) => {
  S.user = u;
  await refreshClaims(true).catch(() => {});
  $who.textContent = u ? `${u.email} — ${S.role ?? 'بدون صلاحية'}` : '';
  render();
});

const audit = (action, targetPath, extra) =>
  addDoc(collection(db, 'audit'), { ...L.auditEntry(action, targetPath, auth.currentUser.uid, extra), at: serverTimestamp() }).catch(() => {});

// ---------------------------------------------------------------- شاشات الدخول
function render() {
  const st = L.authStatusMessage({ signedIn: !!S.user, emailVerified: !!S.user?.emailVerified, role: S.role });
  if (st.kind !== 'ok') return show(loginView(st));
  const tabs = [['stats', 'الإحصائيات'], ['places', 'الأماكن'], ['regions', 'المناطق'], ['inbox', 'البلاغات والاقتراحات'], ['settings', 'الإعدادات']];
  if (S.role === 'owner') tabs.push(['roles', 'الصلاحيات']);
  if (S.role === 'owner' || S.role === 'admin') tabs.push(['audit', 'سجل العمليات']);
  const body = h('div');
  show(
    h('nav', { class: 'tabs' }, tabs.map(([k, t]) => h('button', { class: S.tab === k ? 'active' : '', onclick: () => { S.tab = k; render(); } }, t)),
      h('button', { onclick: () => signOut(auth) }, 'تسجيل الخروج')),
    S.flash ? msg(S.flash.text, S.flash.kind) : null,
    body,
  );
  S.flash = null;
  ({ stats: statsView, places: placesView, regions: regionsView, inbox: inboxView, settings: settingsView, roles: rolesView, audit: auditView })[S.tab](body);
}
const flash = (text, kind = 'ok') => { S.flash = { text, kind }; render(); };

function loginView(st) {
  const email = h('input', { type: 'email', autocomplete: 'username', placeholder: 'البريد الإلكتروني' });
  const pass = h('input', { type: 'password', autocomplete: 'current-password', placeholder: 'كلمة المرور' });
  const out = h('div');
  const say = (t, k = 'err') => out.replaceChildren(msg(t, k));
  const box = h('div', { class: 'card' }, h('h2', {}, 'الدخول'));
  if (st.kind !== 'signin') box.append(msg(st.text, 'warn'));
  if (!S.user) {
    box.append(h('label', {}, 'البريد'), email, h('label', {}, 'كلمة المرور'), pass,
      h('div', { class: 'row', style: 'margin-top:12px' },
        h('button', { class: 'primary', onclick: async () => { try { await signInWithEmailAndPassword(auth, email.value.trim(), pass.value); } catch { say('البريد أو كلمة المرور غير صحيحة.'); } } }, 'دخول'),
        h('button', { onclick: async () => { try { await sendPasswordResetEmail(auth, email.value.trim()); say('إن كان البريد مسجلًا فقد أُرسل رابط الاستعادة.', 'ok'); } catch { say('تعذّر الإرسال.'); } } }, 'نسيت كلمة المرور')));
  } else {
    box.append(h('div', { class: 'row' },
      h('button', { onclick: async () => { await auth.currentUser.reload(); await refreshClaims(true); render(); } }, 'تحديث'),
      !S.user.emailVerified ? h('button', { onclick: async () => { try { await sendEmailVerification(auth.currentUser); say('أُرسلت رسالة التأكيد.', 'ok'); } catch { say('تعذّر الإرسال.'); } } }, 'إعادة إرسال التأكيد') : null,
      S.user.emailVerified && !S.role ? h('button', { class: 'primary', onclick: async () => {
        try { await httpsCallable(fns, 'bootstrapOwner')(); await refreshClaims(true); say('تمت تهيئة صلاحية المالك.', 'ok'); render(); }
        catch (e) { say(`رفض الخادم: ${e.message}`); }
      } }, 'تهيئة المالك') : null,
      h('button', { onclick: () => signOut(auth) }, 'تسجيل الخروج')));
  }
  box.append(out);
  return box;
}

// ---------------------------------------------------------------- الإحصائيات
async function statsView(body) {
  body.append(h('p', { class: 'muted' }, 'جارٍ الحساب…'));
  const c = async (q) => (await getCountFromServer(q)).data().count;
  try {
    const P = collection(db, 'places');
    const [all, pub, dr, rev, users, media, reps, sug] = await Promise.all([
      c(P), c(query(P, where('status', '==', 'published'))), c(query(P, where('status', '==', 'draft'))), c(query(P, where('status', '==', 'review'))),
      S.role === 'editor' ? Promise.resolve('—') : c(collection(db, 'users')), c(collectionGroup(db, 'media')),
      c(query(collection(db, 'reports'), where('status', '==', 'open'))), c(query(collection(db, 'suggestions'), where('status', '==', 'pending'))),
    ]);
    const s = (t, v) => h('div', { class: 'card stat' }, h('b', {}, v), t);
    body.replaceChildren(h('div', { class: 'grid' }, s('الأماكن', all), s('منشور', pub), s('مسودات', dr), s('قيد المراجعة', rev), s('المستخدمون', users), s('الوسائط', media), s('بلاغات مفتوحة', reps), s('اقتراحات', sug)));
  } catch (e) { body.replaceChildren(msg(`رفض الخادم أو فشل الاتصال: ${e.code || e.message}`)); }
}

// ---------------------------------------------------------------- الأماكن
let placeCursor = null;
async function placesView(body, reset = true) {
  if (reset) placeCursor = null;
  const q = h('input', { placeholder: 'ابحث بالاسم في النتائج المحمّلة…' });
  const st = h('select', {}, h('option', { value: '' }, 'كل الحالات'), Object.entries(L.STATUSES).map(([k, v]) => h('option', { value: k }, v)));
  const list = h('div');
  const more = h('button', {}, 'تحميل المزيد');
  let items = [];
  const draw = () => {
    const t = q.value.trim().toLowerCase();
    list.replaceChildren(...items.filter((p) => !t || (p.name || '').toLowerCase().includes(t)).map((p) =>
      h('div', { class: 'card row' }, h('div', { class: 'grow' }, h('b', {}, p.name), ' ', h('span', { class: `badge b-${p.status}` }, L.STATUSES[p.status] || p.status), h('div', { class: 'muted' }, [L.CATEGORIES[p.category], p.regionName].filter(Boolean).join(' · '))),
        h('button', { onclick: () => placeForm(body, p) }, 'تعديل'))));
  };
  const load = async () => {
    try {
      const cons = [orderBy('name'), limit(25)];
      if (st.value) cons.unshift(where('status', '==', st.value));
      if (placeCursor) cons.push(startAfter(placeCursor));
      const snap = await getDocs(query(collection(db, 'places'), ...cons));
      items = items.concat(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
      placeCursor = snap.docs.length === 25 ? snap.docs[snap.docs.length - 1] : null;
      more.hidden = !placeCursor; draw();
    } catch (e) { list.replaceChildren(msg(`تعذّر التحميل: ${e.code || e.message} (قد تحتاج إنشاء فهرس — راجع README)`)); }
  };
  st.addEventListener('change', () => { items = []; placeCursor = null; load(); });
  q.addEventListener('input', draw); more.addEventListener('click', load);
  body.replaceChildren(h('div', { class: 'row' }, h('button', { class: 'primary', onclick: () => placeForm(body, null) }, '+ إضافة مكان'), h('div', { class: 'grow' }, q), st), list, more);
  load();
}

async function placeForm(body, existing) {
  const isNew = !existing;
  const id = existing?.id || L.slugId(existing?.name || 'place');
  const p = existing || { status: 'draft', category: 'restaurants', features: [], ageGroups: [] };
  const regionsSnap = await getDocs(query(collection(db, 'regions'), orderBy('name'), limit(200))).catch(() => ({ docs: [] }));
  const regions = regionsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const F = {};
  const inp = (key, label, attrs = {}) => { F[key] = h('input', { value: p[key] ?? '', ...attrs }); return [h('label', {}, label), F[key]]; };
  const area = (key, label, val) => { F[key] = h('textarea', {}, val ?? p[key] ?? ''); return [h('label', {}, label), F[key]]; };
  const sel = (key, label, opts, val) => { F[key] = h('select', {}, opts.map(([k, v]) => h('option', { value: k, selected: String(val ?? p[key] ?? '') === String(k) }, v))); return [h('label', {}, label), F[key]]; };
  const checks = (key, label, opts) => { F[key] = opts.map(([k, v]) => h('input', { type: 'checkbox', value: k, checked: (p[key] || []).includes(k) })); return [h('label', {}, label), h('div', { class: 'checks' }, opts.map(([k, v], i) => h('label', {}, F[key][i], v)))]; };
  const out = h('div');
  const statusOpts = L.allowedStatuses(S.role).map((k) => [k, L.STATUSES[k]]);
  F.featured = h('input', { type: 'checkbox', checked: !!p.featured, style: 'width:auto;min-height:auto' });
  const kf = p.kidsFriendly === true ? 'yes' : p.kidsFriendly === false ? 'no' : '';

  const form = h('div', { class: 'card' }, h('h2', {}, isNew ? 'إضافة مكان' : `تعديل: ${p.name}`),
    inp('name', 'الاسم *'), sel('category', 'التصنيف *', Object.entries(L.CATEGORIES)),
    sel('status', 'الحالة *', statusOpts),
    sel('regionId', 'المنطقة', [['', '— غير محدد —'], ...regions.map((r) => [r.id, `${L.REGION_TYPES[r.type] || ''} ${r.name}`])]),
    area('description', 'الوصف'),
    inp('phone', 'الهاتف (اتركه فارغًا إن لم يوثَّق)', { inputmode: 'tel' }), inp('address', 'العنوان'), inp('website', 'الموقع الإلكتروني (https://…)'),
    inp('lat', 'خط العرض', { inputmode: 'decimal' }), inp('lng', 'خط الطول', { inputmode: 'decimal' }),
    sel('priceLevel', 'مستوى السعر', [['', '— غير محدد —'], ['1', 'اقتصادي'], ['2', 'متوسط'], ['3', 'مرتفع']], p.priceLevel ?? ''), inp('priceNote', 'ملاحظة عن السعر'),
    inp('openingHours', 'ساعات العمل'),
    sel('kidsFriendly', 'ملاءمة الأطفال', [['', '— غير محدد —'], ['yes', 'مناسب'], ['no', 'غير مخصص']], kf),
    checks('ageGroups', 'الفئات العمرية', Object.entries(L.AGE_GROUPS)), checks('features', 'المرافق', Object.entries(L.FEATURES)),
    area('activities', 'الأنشطة (سطر لكل نشاط)', (p.activities || []).join('\n')), area('alerts', 'تنبيهات مهمة (سطر لكل تنبيه)', (p.alerts || []).join('\n')),
    inp('sourceName', 'اسم المصدر'), inp('sourceUrl', 'رابط المصدر'),
    h('label', {}, F.featured, ' مكان مميز في الصفحة الرئيسية'),
    h('div', { class: 'muted' }, existing?.verifiedAt ? 'موثّق سابقًا.' : 'غير موثّق بعد.'),
    h('div', { class: 'row', style: 'margin-top:12px' },
      h('button', { class: 'primary', onclick: () => save(false) }, 'حفظ'),
      h('button', { onclick: () => save(true) }, 'حفظ + تسجيل أنني تحققت اليوم'),
      !isNew && L.canDelete(S.role) ? h('button', { class: 'danger', onclick: del }, 'حذف المكان') : null,
      h('button', { onclick: () => placesView(body) }, 'رجوع')),
    out);

  async function save(markVerified) {
    const val = (k) => F[k].value;
    const regionId = val('regionId');
    const r = regions.find((x) => x.id === regionId);
    const { errors, doc: d } = L.validatePlaceForm({
      name: val('name'), category: val('category'), status: val('status'), regionId, regionName: r?.name || null, description: val('description'),
      phone: val('phone'), address: val('address'), website: val('website'), lat: val('lat'), lng: val('lng'), priceLevel: val('priceLevel'), priceNote: val('priceNote'),
      openingHours: val('openingHours'), kidsFriendly: val('kidsFriendly'),
      ageGroups: F.ageGroups.filter((c) => c.checked).map((c) => c.value), features: F.features.filter((c) => c.checked).map((c) => c.value),
      activities: val('activities'), alerts: val('alerts'), sourceName: val('sourceName'), sourceUrl: val('sourceUrl'), featured: F.featured.checked,
    }, S.role);
    if (errors.length) return out.replaceChildren(...errors.map((e) => msg(e)));
    try {
      const payload = { ...d, updatedAt: serverTimestamp() };
      if (markVerified) payload.verifiedAt = serverTimestamp();
      if (isNew) payload.createdAt = serverTimestamp();
      await setDoc(doc(db, 'places', id), payload, { merge: true });
      await audit(isNew ? 'place_create' : 'place_update', `places/${id}`, { status: d.status });
      out.replaceChildren(msg('تم الحفظ. التغييرات تظهر في التطبيق إذا كانت الحالة «منشور».', 'ok'));
      if (isNew) { const snap = await getDoc(doc(db, 'places', id)); return placeForm(body, { id, ...snap.data() }); }
    } catch (e) { out.replaceChildren(msg(`رفض الخادم الحفظ: ${e.code || e.message}`)); }
  }
  async function del() {
    if (!confirm('حذف المكان ووسائطه نهائيًا؟')) return;
    try { await deleteDoc(doc(db, 'places', id)); await audit('place_delete', `places/${id}`); flash('تم الحذف (تُنظَّف الوسائط تلقائيًا من الخادم).'); S.tab = 'places'; render(); }
    catch (e) { out.replaceChildren(msg(`رفض الخادم: ${e.code || e.message}`)); }
  }
  body.replaceChildren(form, isNew ? h('div', { class: 'card muted' }, 'احفظ المكان أولًا ثم أضف الصور والفيديوهات.') : mediaPanel(id, existing));
}

// ---------------------------------------------------------------- الوسائط
function mediaPanel(placeId, place) {
  const wrap = h('div', { class: 'card' }, h('h2', {}, 'الصور والفيديوهات'));
  const list = h('div'); const uploads = h('div');
  let items = [];
  const file = h('input', { type: 'file', multiple: true, accept: 'image/jpeg,image/png,image/webp,video/mp4,video/webm' });

  async function load() {
    try {
      const snap = await getDocs(query(collection(db, 'places', placeId, 'media'), orderBy('order'), limit(100)));
      items = snap.docs.map((d) => ({ id: d.id, ...d.data() })); draw();
    } catch (e) { list.replaceChildren(msg(`تعذّر تحميل الوسائط: ${e.code || e.message}`)); }
  }
  function draw() {
    list.replaceChildren(...items.map((m, i) => {
      const title = h('input', { value: m.title || '', placeholder: 'العنوان' });
      const alt = h('input', { value: m.alt || '', placeholder: 'نص بديل (وصف للصورة)' });
      const desc = h('input', { value: m.description || '', placeholder: 'وصف' });
      return h('div', { class: 'media' },
        m.type === 'video' ? h('video', { src: m.url, preload: 'none', controls: true, muted: true }) : h('img', { src: m.url, alt: m.alt || '', loading: 'lazy' }),
        h('div', { class: 'grow', style: 'flex:1' }, title, alt, desc, h('div', { class: 'row', style: 'margin-top:6px' },
          h('button', { onclick: async () => { await updateDoc(doc(db, 'places', placeId, 'media', m.id), { title: title.value, alt: alt.value, description: desc.value }); await audit('media_update', `places/${placeId}/media/${m.id}`); flash('تم حفظ بيانات الوسيط.'); } }, 'حفظ'),
          m.type === 'image' ? h('button', { onclick: () => setCover(m) }, m.isCover ? '★ غلاف' : 'اجعلها الغلاف') : null,
          h('button', { disabled: i === 0, onclick: () => move(i, i - 1) }, '↑'), h('button', { disabled: i === items.length - 1, onclick: () => move(i, i + 1) }, '↓'),
          h('button', { class: 'danger', onclick: () => remove(m) }, 'حذف'))));
    }));
  }
  async function move(from, to) {
    const next = L.reorder(items, from, to);
    await Promise.all(next.map((m) => updateDoc(doc(db, 'places', placeId, 'media', m.id), { order: m.order })));
    items = next; draw();
  }
  async function setCover(m) {
    await Promise.all(items.map((x) => updateDoc(doc(db, 'places', placeId, 'media', x.id), { isCover: x.id === m.id })));
    await updateDoc(doc(db, 'places', placeId), { coverUrl: m.url, coverAlt: m.alt || place?.name || '', updatedAt: serverTimestamp() });
    items = items.map((x) => ({ ...x, isCover: x.id === m.id })); draw();
  }
  async function remove(m) {
    if (!confirm('حذف هذا الوسيط؟')) return;
    try {
      // ١) نحذف السجل أولًا (لا يبقى مرجع لملف غير موجود) ٢) الدالة السحابية تحذف الملف؛ ونحاول حذفه من هنا أيضًا.
      await deleteDoc(doc(db, 'places', placeId, 'media', m.id));
      if (m.storagePath) await deleteObject(ref(storage, m.storagePath)).catch(() => {});
      if (place?.coverUrl === m.url) await updateDoc(doc(db, 'places', placeId), { coverUrl: null, coverAlt: null });
      await audit('media_delete', `places/${placeId}/media/${m.id}`);
      items = items.filter((x) => x.id !== m.id); draw();
    } catch (e) { flash(`رفض الخادم الحذف: ${e.code || e.message}`, 'err'); }
  }

  function videoDuration(f) {
    return new Promise((res) => { const v = document.createElement('video'); v.preload = 'metadata'; v.onloadedmetadata = () => { res(Math.round(v.duration) || null); URL.revokeObjectURL(v.src); }; v.onerror = () => res(null); v.src = URL.createObjectURL(f); });
  }
  function startUpload(f) {
    const kind = L.kindOf(f); const row = h('div', { class: 'card' }); uploads.append(row);
    const problem = L.validateUpload(f, kind);
    if (problem) return row.append(msg(`${f.name}: ${problem}`));
    const run = async () => {
      const bar = h('progress', { max: 100, value: 0 }); const status = h('div', { class: 'muted' }, `${f.name}…`);
      row.replaceChildren(status, bar);
      const dur = kind === 'video' ? await videoDuration(f) : null;
      if (dur && dur > L.LIMITS.video.maxSeconds) return row.replaceChildren(msg(`${f.name}: المدة تتجاوز ${L.LIMITS.video.maxSeconds} ثانية.`));
      const name = L.safeFileName(f.name); const path = L.storagePathFor(placeId, kind, name);
      const task = uploadBytesResumable(ref(storage, path), f, { contentType: f.type, cacheControl: 'public,max-age=31536000' });
      task.on('state_changed', (s) => { bar.value = (s.bytesTransferred / s.totalBytes) * 100; },
        (e) => row.replaceChildren(msg(`${f.name}: فشل الرفع (${e.code}).`), h('button', { onclick: run }, 'إعادة المحاولة')),
        async () => {
          try {
            const url = await getDownloadURL(task.snapshot.ref);
            const mid = name.replace(/\.[^.]+$/, '');
            const m = { type: kind, url, storagePath: path, title: '', alt: '', description: '', order: items.length, isCover: false, durationSec: dur, source: '', license: '', uploadedBy: auth.currentUser.uid, uploadedAt: serverTimestamp(), contentType: f.type, size: f.size };
            await setDoc(doc(db, 'places', placeId, 'media', mid), m);
            await audit('media_upload', `places/${placeId}/media/${mid}`);
            row.replaceChildren(msg(`${f.name}: تم الرفع. أضف العنوان والنص البديل والمصدر/الترخيص من القائمة.`, 'ok'));
            await load();
          } catch (e) {
            // فشل كتابة السجل: نزيل الملف كي لا يبقى يتيمًا
            await deleteObject(ref(storage, path)).catch(() => {});
            row.replaceChildren(msg(`${f.name}: فشل حفظ السجل (${e.code || e.message}). حُذف الملف.`), h('button', { onclick: run }, 'إعادة المحاولة'));
          }
        });
    };
    run();
  }
  file.addEventListener('change', () => { [...file.files].forEach(startUpload); file.value = ''; });
  wrap.append(h('p', { class: 'muted' }, 'صور: JPG/PNG/WebP حتى 10MB. فيديو: MP4/WebM حتى 60MB و3 دقائق. الصور الكبيرة تُرفع كما هي؛ صغّرها قبل الرفع لتوفير بيانات المستخدمين. لا ترفع إلا ما تملكه أو لديك إذن باستخدامه.'), file, uploads, list);
  load();
  return wrap;
}

// ---------------------------------------------------------------- المناطق
async function regionsView(body) {
  const list = h('div'); const out = h('div');
  const name = h('input', { placeholder: 'اسم المنطقة' });
  const type = h('select', {}, Object.entries(L.REGION_TYPES).map(([k, v]) => h('option', { value: k }, v)));
  const parent = h('input', { placeholder: 'معرّف المنطقة الأم (اختياري)' });
  const status = h('select', {}, h('option', { value: 'published' }, 'منشور'), h('option', { value: 'draft' }, 'مسودة'));
  const canWrite = S.role === 'owner' || S.role === 'admin';
  async function load() {
    try {
      const snap = await getDocs(query(collection(db, 'regions'), orderBy('name'), limit(300)));
      list.replaceChildren(...snap.docs.map((d) => { const r = d.data(); return h('div', { class: 'card row' }, h('div', { class: 'grow' }, `${L.REGION_TYPES[r.type] || ''} ${r.name} `, h('span', { class: 'muted' }, `(${d.id}${r.parentId ? ' ⟵ ' + r.parentId : ''})`), ' ', h('span', { class: `badge b-${r.status}` }, r.status)),
        canWrite ? h('button', { class: 'danger', onclick: async () => { if (confirm('حذف المنطقة؟')) { try { await deleteDoc(doc(db, 'regions', d.id)); await audit('region_delete', `regions/${d.id}`); load(); } catch (e) { out.replaceChildren(msg(`رفض: ${e.code}`)); } } } }, 'حذف') : null); }));
    } catch (e) { list.replaceChildren(msg(`تعذّر التحميل: ${e.code || e.message}`)); }
  }
  body.replaceChildren(canWrite ? h('div', { class: 'card' }, h('h3', {}, 'إضافة منطقة'), name, type, parent, status,
    h('button', { class: 'primary', style: 'margin-top:10px', onclick: async () => {
      if (!name.value.trim()) return out.replaceChildren(msg('الاسم مطلوب.'));
      const id = L.slugId(name.value);
      try { await setDoc(doc(db, 'regions', id), { name: name.value.trim(), type: type.value, parentId: parent.value.trim() || null, status: status.value, updatedAt: serverTimestamp() }); await audit('region_create', `regions/${id}`); name.value = ''; out.replaceChildren(msg('تمت الإضافة.', 'ok')); load(); }
      catch (e) { out.replaceChildren(msg(`رفض الخادم: ${e.code || e.message}`)); }
    } }, 'إضافة'), out) : msg('إدارة المناطق للمدير فقط.', 'warn'), list);
  load();
}

// ---------------------------------------------------------------- البلاغات والاقتراحات
async function inboxView(body) {
  const list = h('div');
  const load = async () => {
    try {
      const [r, s] = await Promise.all([getDocs(query(collection(db, 'reports'), where('status', '==', 'open'), limit(50))), getDocs(query(collection(db, 'suggestions'), where('status', '==', 'pending'), limit(50)))]);
      const rows = [...r.docs.map((d) => ['reports', d, `بلاغ: ${d.data().reason}`, 'resolved']), ...s.docs.map((d) => ['suggestions', d, `اقتراح: ${d.data().name}`, 'reviewed'])];
      list.replaceChildren(...(rows.length ? rows.map(([col, d, title, next]) => h('div', { class: 'card' }, h('b', {}, title), h('div', { class: 'muted' }, d.data().details || ''), col === 'reports' ? h('div', { class: 'muted' }, `المكان: ${d.data().placeId}`) : h('div', { class: 'muted' }, `${d.data().regionName || ''} — ${L.CATEGORIES[d.data().category] || ''}`),
        h('button', { onclick: async () => { await updateDoc(doc(db, col, d.id), { status: next }); await audit(`resolve_${col}`, `${col}/${d.id}`); load(); } }, 'تمت المراجعة'))) : [h('p', { class: 'muted' }, 'لا شيء بانتظار المراجعة.')]));
    } catch (e) { list.replaceChildren(msg(`تعذّر التحميل: ${e.code || e.message}`)); }
  };
  body.replaceChildren(list); load();
}

// ---------------------------------------------------------------- الإعدادات
async function settingsView(body) {
  if (S.role === 'editor') return body.replaceChildren(msg('الإعدادات للمدير فقط.', 'warn'));
  const ref0 = doc(db, 'settings', 'public');
  const snap = await getDoc(ref0).catch(() => null);
  const cur = snap?.exists() ? snap.data() : { adsEnabled: true };
  const ads = h('input', { type: 'checkbox', checked: cur.adsEnabled !== false, style: 'width:auto;min-height:auto' });
  const note = h('input', { value: cur.siteMessage || '', placeholder: 'رسالة تظهر للمستخدمين (اختياري)' });
  const out = h('div');
  body.replaceChildren(h('div', { class: 'card' }, h('h3', {}, 'إعدادات عامة'),
    h('label', {}, ads, ' السماح بعرض الإعلانات (مفتاح إيقاف عن بُعد)'),
    msg('معرّف Start.io (App ID) لا يُخزَّن هنا ولا في قاعدة البيانات. يُمرَّر وقت بناء التطبيق عبر GitHub Secret باسم STARTIO_APP_ID. هذا المفتاح فقط يوقف عرض الإعلانات إن لزم.', 'warn'),
    h('label', {}, 'رسالة عامة'), note,
    h('button', { class: 'primary', style: 'margin-top:10px', onclick: async () => {
      try { await setDoc(ref0, { adsEnabled: ads.checked, siteMessage: note.value.slice(0, 300), updatedAt: serverTimestamp() }, { merge: true }); await audit('settings_update', 'settings/public'); out.replaceChildren(msg('تم الحفظ.', 'ok')); }
      catch (e) { out.replaceChildren(msg(`رفض الخادم: ${e.code || e.message}`)); }
    } }, 'حفظ'), out));
}

// ---------------------------------------------------------------- الصلاحيات (المالك فقط)
function rolesView(body) {
  const email = h('input', { type: 'email', placeholder: 'بريد المستخدم (يجب أن يكون قد سجّل وأكّد بريده)' });
  const role = h('select', {}, h('option', { value: 'editor' }, 'محرر (مسودات/مراجعة فقط)'), h('option', { value: 'admin' }, 'مدير'), h('option', { value: 'none' }, 'إزالة الصلاحيات'));
  const out = h('div');
  body.replaceChildren(h('div', { class: 'card' }, h('h3', {}, 'تعيين الأدوار'), msg('التنفيذ يتم في الخادم (Cloud Function) بعد التحقق أنك المالك. لا يتغير شيء من المتصفح مباشرة.', 'warn'), email, role,
    h('button', { class: 'primary', style: 'margin-top:10px', onclick: async () => {
      try { await httpsCallable(fns, 'setRole')({ email: email.value.trim(), role: role.value }); out.replaceChildren(msg('تم. على المستخدم تسجيل الخروج والدخول من جديد.', 'ok')); }
      catch (e) { out.replaceChildren(msg(`رفض الخادم: ${e.message}`)); }
    } }, 'تطبيق'), out));
}

// ---------------------------------------------------------------- سجل العمليات
async function auditView(body) {
  try {
    const snap = await getDocs(query(collection(db, 'audit'), orderBy('at', 'desc'), limit(50)));
    body.replaceChildren(...snap.docs.map((d) => { const a = d.data(); return h('div', { class: 'card' }, h('b', {}, a.action), ' ', h('span', { class: 'muted' }, `${a.targetPath || ''} — ${a.actorUid || ''} — ${a.at?.toDate?.().toLocaleString('ar-JO') || ''}`)); }));
  } catch (e) { body.replaceChildren(msg(`تعذّر التحميل: ${e.code || e.message}`)); }
}
