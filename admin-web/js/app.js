import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail, sendEmailVerification } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import { config } from '../config.js';
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

if (!config.firebase.apiKey || !config.firebase.projectId || !config.supabaseUrl.startsWith('https://')) {
  show(h('div', { class: 'card' }, h('h2', {}, 'الإعداد مطلوب'), msg('لم تُضبط إعدادات Firebase/Supabase لهذه اللوحة. شغّل workflow «نشر لوحة الإدارة» بعد إضافة الأسرار (راجع README).', 'warn')));
  throw new Error('config.js غير مكتمل');
}

const auth = getAuth(initializeApp(config.firebase));
let S = { user: null, role: null, tab: 'stats', flash: null, regions: [] };
let sbClient = null;

async function api(action, body = {}) {
  const token = await auth.currentUser.getIdToken();
  let res;
  try {
    res = await fetch(`${config.supabaseUrl.replace(/\/$/, '')}/functions/v1/admin-api`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-firebase-token': token, apikey: config.supabaseAnonKey },
      body: JSON.stringify({ ...body, action }),
    });
  } catch { throw new Error('تعذّر الاتصال بالخادم.'); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `رفض الخادم الطلب (${res.status}).`);
  return data;
}

async function loadRole() {
  S.role = null;
  if (!auth.currentUser || !auth.currentUser.emailVerified) return;
  try { S.role = L.normRole((await api('whoami')).role); } catch { S.role = null; }
}

onAuthStateChanged(auth, async (u) => {
  S.user = u;
  await loadRole();
  $who.textContent = u ? `${u.email} — ${S.role ?? 'بدون صلاحية'}` : '';
  render();
});

const run = async (fn, okText) => {
  try { await fn(); if (okText) S.flash = { text: okText, kind: 'ok' }; }
  catch (e) { S.flash = { text: e.message || 'حدث خطأ.', kind: 'err' }; }
  render();
};
const flash = () => { const f = S.flash; S.flash = null; return f ? msg(f.text, f.kind) : null; };
const field = (label, input) => h('label', { class: 'field' }, h('span', {}, label), input);
const sel = (name, map, cur, blank) => h('select', { name }, blank ? h('option', { value: '' }, blank) : null, Object.entries(map).map(([k, v]) => h('option', { value: k, selected: k === cur }, v)));
const checks = (name, map, cur = []) => h('div', { class: 'checks' }, Object.entries(map).map(([k, v]) => h('label', {}, h('input', { type: 'checkbox', name, value: k, checked: cur.includes(k) }), ' ', v)));

// ---------------------------------------------------------------- الدخول
function render() {
  if (!S.user) return renderSignIn();
  const st = L.authStatusMessage({ signedIn: true, emailVerified: S.user.emailVerified, role: S.role });
  if (st.kind !== 'ok') return renderBlocked(st);
  renderShell();
}

function renderSignIn() {
  const email = h('input', { type: 'email', name: 'email', required: true, autocomplete: 'username' });
  const pass = h('input', { type: 'password', name: 'pass', required: true, autocomplete: 'current-password' });
  show(h('form', { class: 'card', onsubmit: (e) => { e.preventDefault(); run(() => signInWithEmailAndPassword(auth, email.value.trim(), pass.value)); } },
    h('h2', {}, 'تسجيل الدخول'), flash(), field('البريد الإلكتروني', email), field('كلمة المرور', pass),
    h('button', { class: 'btn', type: 'submit' }, 'دخول'),
    h('button', { class: 'link', type: 'button', onclick: () => run(() => sendPasswordResetEmail(auth, email.value.trim()), 'إن كان البريد مسجلًا فستصله رسالة.') }, 'نسيت كلمة المرور؟')));
}

function renderBlocked(st) {
  show(h('div', { class: 'card' }, h('h2', {}, 'لا يمكن المتابعة'), flash(), msg(st.text, 'warn'),
    st.kind === 'verify' ? h('button', { class: 'btn', onclick: () => run(() => sendEmailVerification(auth.currentUser), 'أُرسلت رسالة التأكيد.') }, 'إرسال رسالة التأكيد') : null,
    h('button', { class: 'btn', onclick: () => run(async () => { await auth.currentUser.reload(); await auth.currentUser.getIdToken(true); await loadRole(); $who.textContent = `${auth.currentUser.email} — ${S.role ?? 'بدون صلاحية'}`; }) }, 'تحديث'),
    h('button', { class: 'link', onclick: () => signOut(auth) }, 'تسجيل الخروج')));
}

// ---------------------------------------------------------------- الهيكل
const TABS = [['stats', 'نظرة عامة'], ['places', 'الأماكن'], ['regions', 'المناطق'], ['inbox', 'البلاغات'], ['settings', 'الإعدادات'], ['staff', 'الفريق'], ['audit', 'السجل']];
const isAdmin = () => L.canDelete(S.role);
const tabsFor = () => TABS.filter(([k]) => isAdmin() || ['stats', 'places'].includes(k)).filter(([k]) => k !== 'staff' || S.role === 'owner');

async function renderShell() {
  const nav = h('nav', { class: 'tabs' }, tabsFor().map(([k, t]) => h('button', { class: k === S.tab ? 'on' : '', onclick: () => { S.tab = k; S.edit = null; render(); } }, t)),
    h('button', { class: 'link', onclick: () => signOut(auth) }, 'خروج'));
  const body = h('div', {}, h('p', { class: 'muted' }, 'جارٍ التحميل…'));
  show(nav, flash(), body);
  try {
    if (!S.regions.length) S.regions = (await api('list_regions')).regions;
    const view = await VIEWS[S.tab]?.();
    body.replaceChildren(...[].concat(view || []));
  } catch (e) { body.replaceChildren(msg(e.message)); }
}

const VIEWS = {
  async stats() {
    const s = await api('stats');
    const items = [['الأماكن', s.places], ['منشور', s.published], ['مسودات', s.drafts], ['قيد المراجعة', s.review], ['مؤرشف', s.archived], ['الوسائط', s.media], ['المناطق', s.regions], ['بلاغات مفتوحة', s.openReports], ['اقتراحات', s.pendingSuggestions]];
    return h('div', { class: 'grid' }, items.map(([k, v]) => h('div', { class: 'stat' }, h('b', {}, v), h('span', {}, k))));
  },

  async places() {
    if (S.edit) return placeEditor(S.edit === 'new' ? null : S.edit);
    const { places, hasMore } = await api('list_places', { offset: S.offset || 0, limit: 30 });
    const cat = (c) => L.CATEGORIES[c] || c;
    return [
      h('button', { class: 'btn', onclick: () => { S.edit = 'new'; render(); } }, '+ مكان جديد'),
      places.length ? h('ul', { class: 'list' }, places.map((p) => h('li', {}, h('div', {}, h('b', {}, p.name), h('small', {}, ` ${cat(p.category)} · ${L.STATUSES[p.status] || p.status}`)),
        h('button', { class: 'btn sm', onclick: () => { S.edit = p; render(); } }, 'تعديل')))) : h('p', { class: 'muted' }, 'لا أماكن بعد.'),
      h('div', { class: 'row' },
        S.offset ? h('button', { class: 'btn sm', onclick: () => { S.offset = Math.max(0, S.offset - 30); render(); } }, 'السابق') : null,
        hasMore ? h('button', { class: 'btn sm', onclick: () => { S.offset = (S.offset || 0) + 30; render(); } }, 'التالي') : null),
    ];
  },

  async regions() {
    const rows = S.regions;
    const f = h('form', { class: 'card', onsubmit: (e) => { e.preventDefault(); const d = Object.fromEntries(new FormData(e.target)); run(async () => { await api('upsert_region', { name: d.name, type: d.type, parentId: d.parentId || null, status: 'published' }); S.regions = []; }, 'حُفظت المنطقة.'); } },
      h('h3', {}, 'إضافة منطقة'), field('الاسم', h('input', { name: 'name', required: true })), field('النوع', sel('type', L.REGION_TYPES, 'governorate')),
      field('تتبع لـ', h('select', { name: 'parentId' }, h('option', { value: '' }, '— بدون —'), rows.map((r) => h('option', { value: r.id }, r.name)))), h('button', { class: 'btn' }, 'حفظ'));
    return [f, h('ul', { class: 'list' }, rows.map((r) => h('li', {}, h('span', {}, `${r.name} (${L.REGION_TYPES[r.type] || r.type})`),
      h('button', { class: 'btn sm danger', onclick: () => confirm(`حذف «${r.name}»؟`) && run(async () => { await api('delete_region', { id: r.id }); S.regions = []; }, 'حُذفت.') }, 'حذف'))))];
  },

  async inbox() {
    const { items } = await api('inbox');
    return items.length ? h('ul', { class: 'list' }, items.map((i) => h('li', {}, h('div', {}, h('b', {}, i.title), h('small', {}, ` ${i.details}`)),
      h('button', { class: 'btn sm', onclick: () => run(() => api('resolve', { kind: i.kind, id: i.id }), 'تم.') }, 'تمّت المعالجة')))) : h('p', { class: 'muted' }, 'لا شيء بانتظار المراجعة.');
  },

  async settings() {
    const { settings } = await api('get_settings');
    const ads = h('input', { type: 'checkbox', checked: settings.adsEnabled !== false });
    const note = h('input', { value: settings.siteMessage || '', maxlength: 300 });
    return h('form', { class: 'card', onsubmit: (e) => { e.preventDefault(); run(() => api('set_settings', { adsEnabled: ads.checked, siteMessage: note.value }), 'حُفظت الإعدادات.'); } },
      h('label', {}, ads, ' تفعيل الإعلانات (مفتاح إيقاف عن بُعد)'), field('رسالة عامة', note), h('button', { class: 'btn' }, 'حفظ'));
  },

  async staff() {
    const { staff } = await api('list_staff');
    const email = h('input', { type: 'email', required: true });
    const role = sel('role', { admin: 'مدير', editor: 'محرر' }, 'editor');
    return [
      h('form', { class: 'card', onsubmit: (e) => { e.preventDefault(); run(() => api('set_role', { email: email.value, role: role.value }), 'حُدّثت الصلاحية.'); } },
        h('h3', {}, 'منح صلاحية'), field('البريد', email), field('الدور', role), h('button', { class: 'btn' }, 'حفظ')),
      h('ul', { class: 'list' }, staff.map((s) => h('li', {}, h('span', {}, `${s.email} — ${s.role}`), h('button', { class: 'btn sm danger', onclick: () => run(() => api('set_role', { email: s.email, role: 'none' }), 'أُزيلت.') }, 'إزالة')))),
    ];
  },

  async audit() {
    const { entries } = await api('audit_list');
    return h('ul', { class: 'list' }, entries.map((a) => h('li', {}, h('span', {}, `${new Date(a.at).toLocaleString('ar-JO')} — ${a.action} — ${a.actor_email || ''} — ${a.target || ''}`))));
  },
};

// ---------------------------------------------------------------- محرر المكان
function placeEditor(p) {
  const v = (k) => p?.[k] ?? '';
  const camel = { regionId: p?.region_id, priceLevel: p?.price_level, priceNote: p?.price_note, openingHours: p?.opening_hours, kidsFriendly: p?.kids_friendly, ageGroups: p?.age_groups, sourceName: p?.source_name, sourceUrl: p?.source_url };
  const form = h('form', { class: 'card', onsubmit: (e) => {
    e.preventDefault();
    const d = Object.fromEntries(new FormData(e.target));
    d.features = [...e.target.querySelectorAll('input[name=features]:checked')].map((x) => x.value);
    d.ageGroups = [...e.target.querySelectorAll('input[name=ageGroups]:checked')].map((x) => x.value);
    d.featured = e.target.elements.featured.checked;
    const { errors, doc } = L.validatePlaceForm(d, S.role);
    if (errors.length) { S.flash = { text: errors.join(' '), kind: 'err' }; return render(); }
    run(async () => {
      const r = await api('upsert_place', { id: p?.id, place: doc, markVerified: e.target.elements.markVerified.checked });
      S.edit = r.place;
    }, 'حُفظ المكان.');
  } },
    h('h3', {}, p ? 'تعديل مكان' : 'مكان جديد'), flash(),
    field('الاسم', h('input', { name: 'name', value: v('name'), required: true })),
    field('التصنيف', sel('category', L.CATEGORIES, p?.category || 'services')),
    field('الحالة', sel('status', Object.fromEntries(Object.entries(L.STATUSES).filter(([k]) => L.allowedStatuses(S.role).includes(k))), p?.status || 'draft')),
    field('المنطقة', h('select', { name: 'regionId' }, h('option', { value: '' }, '— غير محددة —'), S.regions.map((r) => h('option', { value: r.id, selected: r.id === p?.region_id }, r.name)))),
    field('الوصف', h('textarea', { name: 'description', rows: 4 }, v('description'))),
    field('الهاتف (فقط إن كان موثّقًا)', h('input', { name: 'phone', value: v('phone'), inputmode: 'tel' })),
    field('العنوان', h('input', { name: 'address', value: v('address') })),
    h('div', { class: 'row' }, field('خط العرض', h('input', { name: 'lat', value: v('lat'), inputmode: 'decimal' })), field('خط الطول', h('input', { name: 'lng', value: v('lng'), inputmode: 'decimal' }))),
    field('الموقع الإلكتروني', h('input', { name: 'website', value: v('website'), type: 'url' })),
    field('مستوى السعر', sel('priceLevel', { 1: 'اقتصادي', 2: 'متوسط', 3: 'مرتفع' }, String(camel.priceLevel ?? ''), '— غير محدد —')),
    field('ملاحظة السعر', h('input', { name: 'priceNote', value: v('price_note') })),
    field('ساعات العمل', h('input', { name: 'openingHours', value: v('opening_hours') })),
    field('مناسب للأطفال', sel('kidsFriendly', { yes: 'نعم', no: 'لا' }, camel.kidsFriendly === true ? 'yes' : camel.kidsFriendly === false ? 'no' : '', '— غير محدد —')),
    field('الفئات العمرية', checks('ageGroups', L.AGE_GROUPS, camel.ageGroups || [])),
    field('المزايا', checks('features', L.FEATURES, p?.features || [])),
    field('الأنشطة (سطر لكل نشاط)', h('textarea', { name: 'activities', rows: 3 }, (p?.activities || []).join('\n'))),
    field('تنبيهات (سطر لكل تنبيه)', h('textarea', { name: 'alerts', rows: 2 }, (p?.alerts || []).join('\n'))),
    field('اسم المصدر', h('input', { name: 'sourceName', value: v('source_name') })),
    field('رابط المصدر', h('input', { name: 'sourceUrl', value: v('source_url'), type: 'url' })),
    h('label', {}, h('input', { type: 'checkbox', name: 'featured', checked: p?.featured }), ' مميّز'),
    h('label', {}, h('input', { type: 'checkbox', name: 'markVerified' }), ' تم التحقق من المعلومات الآن'),
    h('div', { class: 'row' }, h('button', { class: 'btn', type: 'submit' }, 'حفظ'), h('button', { class: 'btn sm', type: 'button', onclick: () => { S.edit = null; render(); } }, 'رجوع'),
      p && isAdmin() ? h('button', { class: 'btn sm danger', type: 'button', onclick: () => confirm('حذف المكان ووسائطه نهائيًا؟') && run(async () => { await api('delete_place', { id: p.id }); S.edit = null; }, 'حُذف المكان.') }, 'حذف') : null));
  return p ? [form, mediaPanel(p)] : [form];
}

// ---------------------------------------------------------------- الوسائط
function mediaPanel(p) {
  const box = h('div', { class: 'card' }, h('h3', {}, 'الصور والفيديو'), h('p', { class: 'muted' }, 'جارٍ التحميل…'));
  const reload = async () => {
    const { media } = await api('list_media', { placeId: p.id });
    const list = media.map((m, i) => h('li', {},
      m.type === 'video' ? h('video', { src: m.url, controls: true, preload: 'metadata', width: 120 }) : h('img', { src: m.url, alt: m.alt || '', width: 80, loading: 'lazy' }),
      h('small', {}, m.is_cover ? ' (الغلاف) ' : ' ', m.title || ''),
      h('span', { class: 'row' },
        m.type === 'image' && !m.is_cover ? h('button', { class: 'btn sm', onclick: () => run(() => api('set_cover', { placeId: p.id, mediaId: m.id }), 'عُيّن الغلاف.') }, 'غلاف') : null,
        i > 0 ? h('button', { class: 'btn sm', onclick: () => run(() => api('reorder_media', { placeId: p.id, order: L.reorder(media, i, i - 1).map((x) => x.id) })) }, '↑') : null,
        isAdmin() ? h('button', { class: 'btn sm danger', onclick: () => confirm('حذف الوسيط؟') && run(() => api('delete_media', { placeId: p.id, mediaId: m.id }), 'حُذف.') }, 'حذف') : null)));
    const file = h('input', { type: 'file', accept: 'image/jpeg,image/png,image/webp,video/mp4,video/webm' });
    const alt = h('input', { placeholder: 'وصف الصورة (للقارئات الصوتية)' });
    const src = h('input', { placeholder: 'مصدر الصورة/الترخيص (مطلوب لغير ملكك)' });
    const status = h('p', { class: 'muted' });
    const up = h('button', { class: 'btn', onclick: async () => {
      const f = file.files[0]; if (!f) return;
      const kind = L.kindOf(f); const bad = L.validateUpload(f, kind);
      if (bad) { status.textContent = bad; return; }
      up.disabled = true; status.textContent = 'جارٍ الرفع…';
      try {
        const u = await api('create_upload', { placeId: p.id, kind, contentType: f.type, size: f.size, fileName: f.name });
        if (!sbClient) { const { createClient } = await import('https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.45.4/+esm'); sbClient = createClient(config.supabaseUrl, config.supabaseAnonKey, { auth: { persistSession: false } }); }
        const r = await sbClient.storage.from('media').uploadToSignedUrl(u.path, u.token, f, { contentType: f.type });
        if (r.error) throw new Error('فشل رفع الملف.');
        await api('add_media', { placeId: p.id, kind, path: u.path, contentType: f.type, size: f.size, alt: alt.value, source: src.value, license: src.value, title: f.name.replace(/\.[^.]+$/, '') });
        S.flash = { text: 'رُفع الملف.', kind: 'ok' }; render();
      } catch (e) { status.textContent = e.message; up.disabled = false; }
    } }, 'رفع');
    box.replaceChildren(h('h3', {}, 'الصور والفيديو'), media.length ? h('ul', { class: 'list' }, list) : h('p', { class: 'muted' }, 'لا وسائط بعد.'),
      h('p', { class: 'muted' }, 'ارفع فقط صورًا تملكها أو مرخّصة. الحد: صورة 10MB، فيديو 60MB.'), file, alt, src, up, status);
  };
  reload().catch((e) => box.replaceChildren(msg(e.message)));
  return box;
}
