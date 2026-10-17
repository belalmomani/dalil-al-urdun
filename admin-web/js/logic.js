// منطق نقي (بلا DOM ولا Firebase) للوحة الإدارة — قابل للاختبار في Node.

export const CATEGORIES = {
  tourism: 'سياحة', restaurants: 'مطاعم', government: 'حكومي', shopping: 'تسوّق', education: 'تعليم',
  news: 'أخبار', jobs: 'وظائف', apps: 'تطبيقات', services: 'خدمات',
};
export const STATUSES = { draft: 'مسودة', review: 'قيد المراجعة', published: 'منشور', archived: 'مؤرشف' };
export const REGION_TYPES = { governorate: 'محافظة', city: 'مدينة', town: 'بلدة', village: 'قرية', neighborhood: 'حي' };
export const FEATURES = { parking: 'مواقف سيارات', kids_area: 'ركن أطفال', wheelchair: 'وصول لذوي الإعاقة', outdoor: 'جلسات خارجية', wifi: 'إنترنت', prayer_room: 'مصلّى' };
export const AGE_GROUPS = { toddlers: 'رُضّع وصغار (0–3)', kids: 'أطفال (4–12)', teens: 'مراهقون (13–17)', adults: 'بالغون', seniors: 'كبار السن' };

// حدود الرفع — يجب أن تطابق storage.rules (الخادم هو المرجع؛ هذه للتجربة السريعة فقط).
export const LIMITS = {
  image: { maxBytes: 10 * 1024 * 1024, types: ['image/jpeg', 'image/png', 'image/webp'] },
  video: { maxBytes: 60 * 1024 * 1024, types: ['video/mp4', 'video/webm'], maxSeconds: 180 },
};

/** الدور الظاهر في الواجهة (للتجربة فقط؛ الحماية الفعلية في القواعد). */
export function staffRole(claims, emailVerified) {
  if (!emailVerified || !claims) return null;
  if (claims.admin === true) return claims.owner === true ? 'owner' : 'admin';
  if (claims.editor === true) return 'editor';
  return null;
}

/** المحرر لا ينشر ولا يؤرشف؛ المدير يفعل كل شيء. */
export function allowedStatuses(role) {
  return role === 'owner' || role === 'admin' ? Object.keys(STATUSES) : role === 'editor' ? ['draft', 'review'] : [];
}
export function canDelete(role) { return role === 'owner' || role === 'admin'; }

const URL_RE = /^https?:\/\/[^\s]+$/i;
const PHONE_RE = /^\+?[0-9\s\-()]{6,20}$/;

/** يتحقق من نموذج المكان ويُرجع { errors, doc }. لا نخترع قيمًا: الحقول الفارغة تصبح null. */
export function validatePlaceForm(f, role) {
  const errors = [];
  const name = (f.name || '').trim();
  if (!name) errors.push('الاسم مطلوب.');
  if (name.length > 200) errors.push('الاسم طويل جدًا.');
  if (!(f.category in CATEGORIES)) errors.push('التصنيف غير صالح.');
  if (!allowedStatuses(role).includes(f.status)) errors.push('الحالة غير مسموحة لصلاحيتك.');
  const phone = (f.phone || '').trim();
  if (phone && !PHONE_RE.test(phone)) errors.push('صيغة الهاتف غير صالحة.');
  const website = (f.website || '').trim();
  if (website && !URL_RE.test(website)) errors.push('الموقع يجب أن يبدأ بـ http:// أو https://');
  const sourceUrl = (f.sourceUrl || '').trim();
  if (sourceUrl && !URL_RE.test(sourceUrl)) errors.push('رابط المصدر غير صالح.');
  let lat = f.lat === '' || f.lat == null ? null : Number(f.lat);
  let lng = f.lng === '' || f.lng == null ? null : Number(f.lng);
  if ((lat == null) !== (lng == null)) errors.push('أدخل خط العرض والطول معًا أو اتركهما فارغين.');
  if (lat != null && !(lat >= -90 && lat <= 90)) errors.push('خط العرض خارج النطاق.');
  if (lng != null && !(lng >= -180 && lng <= 180)) errors.push('خط الطول خارج النطاق.');
  let priceLevel = f.priceLevel === '' || f.priceLevel == null ? null : Number(f.priceLevel);
  if (priceLevel != null && ![1, 2, 3].includes(priceLevel)) errors.push('مستوى السعر غير صالح.');
  const pick = (arr, allowed) => (Array.isArray(arr) ? arr.filter((x) => x in allowed) : []);
  const lines = (s) => String(s || '').split('\n').map((x) => x.trim()).filter(Boolean).slice(0, 30);
  const kids = f.kidsFriendly === 'yes' ? true : f.kidsFriendly === 'no' ? false : null;
  const doc = {
    name, category: f.category, status: f.status,
    regionId: f.regionId || null, regionName: f.regionName || null,
    description: (f.description || '').trim(),
    phone: phone || null, address: (f.address || '').trim() || null,
    lat, lng, website: website || null,
    priceLevel, priceNote: (f.priceNote || '').trim() || null,
    openingHours: (f.openingHours || '').trim() || null,
    kidsFriendly: kids,
    ageGroups: pick(f.ageGroups, AGE_GROUPS), features: pick(f.features, FEATURES),
    activities: lines(f.activities), alerts: lines(f.alerts),
    sourceName: (f.sourceName || '').trim() || null, sourceUrl: sourceUrl || null,
    featured: !!f.featured,
  };
  return { errors, doc };
}

export function validateUpload(file, kind) {
  const lim = LIMITS[kind];
  if (!lim) return 'نوع وسيط غير معروف.';
  if (!lim.types.includes(file.type)) return `الصيغة غير مسموحة (${file.type || 'غير معروفة'}).`;
  if (file.size > lim.maxBytes) return `الحجم يتجاوز الحد (${Math.round(lim.maxBytes / 1048576)}MB).`;
  if (file.size <= 0) return 'الملف فارغ.';
  return null;
}

export function kindOf(file) { return file.type?.startsWith('video/') ? 'video' : 'image'; }

/** اسم ملف آمن يطابق قاعدة Storage: [A-Za-z0-9._-]{1,120} */
export function safeFileName(original, now = Date.now(), rand = Math.random()) {
  const ext = (String(original).split('.').pop() || 'bin').toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 5) || 'bin';
  return `${now}-${Math.floor(rand * 1e6).toString(36)}.${ext}`;
}

export function storagePathFor(placeId, kind, fileName) { return `places/${placeId}/${kind === 'video' ? 'videos' : 'images'}/${fileName}`; }

/** تبديل موضع عنصر في القائمة وإرجاع ترتيب جديد 0..n-1 */
export function reorder(items, from, to) {
  if (from < 0 || to < 0 || from >= items.length || to >= items.length) return items.map((m, i) => ({ ...m, order: i }));
  const a = items.slice();
  const [x] = a.splice(from, 1);
  a.splice(to, 0, x);
  return a.map((m, i) => ({ ...m, order: i }));
}

export function slugId(name, now = Date.now()) {
  const ascii = String(name).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 30);
  return `${ascii || 'place'}-${now.toString(36)}`;
}

export function auditEntry(action, targetPath, actorUid, extra = {}) {
  return { action, targetPath, actorUid, ...extra };
}

export function authStatusMessage({ signedIn, emailVerified, role }) {
  if (!signedIn) return { kind: 'signin', text: 'سجّل الدخول بحساب الإدارة.' };
  if (!emailVerified) return { kind: 'verify', text: 'بريدك غير مؤكَّد. افتح رسالة التأكيد ثم اضغط «تحديث».' };
  if (!role) return { kind: 'norole', text: 'هذا الحساب لا يملك صلاحية إدارية بعد. إن كنت المالك فاضغط «تهيئة المالك» (يتحقق الخادم من بريدك).' };
  return { kind: 'ok', text: '' };
}
