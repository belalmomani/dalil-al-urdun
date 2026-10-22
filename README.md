# دليل الأردن — تطبيق Android

تطبيق Android أصلي (Kotlin + Jetpack Compose) بواجهة عربية RTL: دليل للمطاعم والمواقع السياحية والخدمات والمحافظات والمدن، مع بحث متعدد الفلاتر، ومفضلة، وحساب اختياري، ولوحة إدارة محمية.
الهوية: كحلي وأبيض وأحمر أردني، وشعار مستوحى من العلم.

> **حالة المشروع بصدق:** اختُبر منطق الخادم (دالة admin-api) ومخطط قاعدة البيانات وسياسات RLS ومنطق لوحة الإدارة محليًا. **لم يُجمَّع كود Kotlin بعد** (لا Android SDK في بيئة الكتابة)؛ أول تجميع حقيقي في GitHub Actions وقد يظهر فيه أخطاء تُصلح بسرعة عند إرسال السجل. لم تُجرَّب الدالة على Supabase حقيقي ولا التحقق من رمز Firebase الحقيقي بعد.

## البنية

| المسار | المحتوى |
|---|---|
| `app/` | تطبيق Android (Compose، Firebase Auth للدخول فقط، Supabase REST، Coil، Media3، Start.io) |
| `supabase/migrations/` | مخطط قاعدة البيانات + RLS (القراءة العامة للمنشور فقط) + حاوية الوسائط `media` |
| `supabase/functions/admin-api/` | دالة Edge: تتحقق من رمز Firebase وتحدد الدور **في الخادم** وتنفّذ كل عمليات الكتابة |
| `admin-web/` | لوحة إدارة ويب تعمل من الهاتف، تُنشر على GitHub Pages |
| `legacy-firebase/` | أرشيف النسخة القديمة (Firestore/Storage) — غير مستخدم |
| `.github/workflows/` | بناء APK، اختبارات المنطق، نشر Supabase، نشر اللوحة |

## كيف يعمل

- **الدخول**: Firebase Authentication (بريد/كلمة مرور). التصفح والبحث بلا تسجيل.
- **البيانات والوسائط**: Supabase. القراءة العامة عبر REST بمفتاح `anon` (يرى المنشور فقط بحكم RLS). لا كتابة مباشرة من العملاء إطلاقًا.
- **الصلاحيات**: المالك = البريد المطابق لسر `ADMIN_EMAIL` في الخادم **وبريده مؤكَّد**. المديرون والمحررون يضيفهم المالك فقط في جدول `staff`. المحرر يعمل على المسودات/قيد المراجعة ولا يحذف. لا شيء يعتمد على نص البريد في الواجهة.
- **رفع الوسائط**: روابط رفع موقّعة قصيرة العمر تصدرها الدالة بعد فحص الصلاحية والنوع والحجم (صورة ≤10MB، فيديو ≤60MB).

## الإعداد خطوة بخطوة (كلها من الهاتف)

### 1) Supabase
1. أنشئ مشروعًا على supabase.com.
2. من Project Settings → API انسخ: `Project URL` و`anon public key`. (لا تنسخ `service_role` إلى أي مكان خارج Supabase.)
3. من Account → Access Tokens أنشئ Token. وخذ `Project ref` (جزء الرابط قبل `.supabase.co`) وكلمة مرور قاعدة البيانات.

### 2) أسرار GitHub (Settings → Secrets and variables → Actions)
| السر | المصدر |
|---|---|
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | الخطوة 1 |
| `SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF`, `SUPABASE_DB_PASSWORD` | الخطوة 1 (للنشر فقط) |
| `GOOGLE_SERVICES_JSON_BASE64` | ملف `google-services.json` من Firebase مشفّرًا base64 |
| `STARTIO_APP_ID` | اختياري؛ بدونه الإعلانات معطلة |
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` | اختياري للتوقيع (alias: `dalil`) |

### 3) Firebase (للدخول فقط)
فعّل Authentication → Sign-in method → Email/Password. أضف نطاق GitHub Pages (`<اسم-حسابك>.github.io`) إلى Authentication → Settings → Authorized domains.

### 4) نشر الخادم
Actions → «نشر Supabase» → Run workflow. ينشئ الجداول والحاوية، ويضبط `ADMIN_EMAIL` (افتراضيًا `bilalalmomani90@gmail.com`) و`FIREBASE_PROJECT_ID`، وينشر الدالة بـ `--no-verify-jwt`.
بديل يدوي: الصق ملف `supabase/migrations/*.sql` في SQL Editor.

### 5) لوحة الإدارة
Settings → Pages → Source: GitHub Actions، ثم Actions → «نشر لوحة الإدارة» → Run. افتح الرابط، سجّل الدخول بحساب المالك (بريده **مؤكَّد**) فتظهر لك الأقسام كاملة. ضع رابط اللوحة في متغير البناء `ADMIN_PANEL_URL` إن أردت زرًّا في التطبيق.

### 6) بناء APK
Actions → «بناء APK» → Run. حمّل الـ artifact. بدون `SUPABASE_URL`/`SUPABASE_ANON_KEY` يعمل التطبيق ببيانات تجريبية موسومة.

### 7) إدخال المحتوى
من اللوحة: أضف المناطق ثم الأماكن (مسودة ← نشر) مع المصدر وتاريخ التحقق، وارفع الصور. لا أرقام هواتف ولا تقييمات تُخترع؛ الحقول الفارغة تبقى فارغة. ارفع فقط صورًا تملكها أو مرخّصة.

## الاختبارات محليًا
```
node --test admin-web/js/logic.test.js
cd supabase && node --test tests/logic.test.js
cd supabase && node --experimental-strip-types --import ./tests/register.mjs --test tests/api.test.js
./gradlew testDebugUnitTest      # يحتاج Android SDK
```

## حدود معروفة
- لم يُجرَّب التحقق من توقيع رمز Firebase الحقيقي ولا الدالة على Supabase حقيقي.
- مشروع Firebase المشترك مع تطبيقات أخرى يشارك مستخدميه؛ يُفضّل مشروع مخصص.
- لا يوجد App Check بعد. راقب حدود خطة Supabase (التخزين/النقل).
- البيانات الأولية في `SeedData.kt` تجريبية وليست حقيقية.

## تحديث 22 أكتوبر 2026: الإنجليزية، الإشعارات، الخصوصية
1. شغّل في Supabase SQL Editor بالترتيب: `supabase/migrations/20261022000000_english.sql` ثم `supabase/seed/20261022_english.sql` (آمنان للتكرار).
2. أعد نشر دالة admin-api (workflow: supabase-deploy) لتقبل الحقول الإنجليزية.
3. أعد نشر Pages (workflow: pages): تصبح سياسة الخصوصية على `<رابط-الصفحة>/privacy.html`.
4. أعد بناء التطبيق. اللغة: «حسابي ← الإعدادات». الإشعارات اختيارية (أماكن جديدة) وتعمل محليًا بلا خادم إشعارات.
