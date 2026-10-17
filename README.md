# دليل الأردن — تطبيق Android

تطبيق Android أصلي (Kotlin + Jetpack Compose) بواجهة عربية RTL: دليل للمطاعم والمواقع السياحية والخدمات والمحافظات والمدن، مع بحث متعدد الفلاتر، ومفضلة، وحساب اختياري، ولوحة إدارة محمية.
الهوية: كحلي وأبيض وأحمر أردني، وشعار مستوحى من العلم، وبطاقات تصنيف بيضاء بأيقونات حمراء.

> **حالة المشروع بصدق:** الكود والقواعد والدوال والاختبارات المنطقية جاهزة. لم يُبنَ APK بعد (لا يتوفر Android SDK في بيئة التطوير التي كُتب فيها)؛ أول بناء حقيقي سيكون في GitHub Actions. لم يُربط Firebase ولم تُفعَّل الإعلانات؛ كلاهما ينتظر بياناتك.

## البنية

| المسار | المحتوى |
|---|---|
| `app/` | تطبيق Android (Compose، Firebase Auth/Firestore/Functions، Coil، Media3، Start.io) |
| `admin-web/` | لوحة إدارة ويب (تعمل من الهاتف) تُنشر على Firebase Hosting |
| `functions/` | Cloud Functions: تهيئة المالك، تعيين الأدوار، تنظيف الوسائط اليتيمة |
| `firestore.rules`, `storage.rules`, `firestore.indexes.json` | قواعد الأمان والفهارس |
| `tests/` | اختبارات القواعد على محاكي Firebase |
| `.github/workflows/` | بناء APK واختبارات تلقائية |

## ما يعمل وما ينتظر

| البند | الحالة |
|---|---|
| تصفح وبحث وفلاتر متعددة (أسعار/أعمار/ميزات) بلا تسجيل | مكتوب، بيانات تجريبية موسومة كتجريبية |
| Firebase Auth/Firestore/Storage | مكتوب؛ **ينتظر مشروع Firebase الخاص بك** |
| صلاحيات المدير | عبر custom claims من Cloud Functions فقط؛ **تنتظر نشر الدوال وتهيئتك** |
| إعلان Start.io (بانر) | SDK الأصلي مدمج؛ **معطّل حتى تضيف `STARTIO_APP_ID`** |
| اختبارات منطق الأدوار (16) ومنطق لوحة الإدارة (10) | نجحت عند التشغيل المحلي |
| اختبارات قواعد Firestore/Storage | مكتوبة؛ تُشغَّل في GitHub Actions (تحتاج محاكي Firebase) |
| اختبارات Kotlin وبناء APK | تُشغَّل في GitHub Actions؛ لم تُنفَّذ بعد |

## 1) الرفع إلى GitHub من الهاتف

1. من تطبيق GitHub أو المتصفح: أنشئ مستودعًا جديدًا (خاصًا يفضَّل) باسم `dalil-al-urdun`.
2. على الهاتف افتح github.com من المتصفح (وضع سطح المكتب) > المستودع > **Add file > Upload files** وارفع محتويات المجلد (احرص على رفع المجلد المخفي `.github`؛ إن تعذّر فأنشئ الملفين يدويًا عبر **Add file > Create new file** بالمسار `.github/workflows/android.yml` ثم `firebase-tests.yml`).
3. لا ترفع أبدًا: `google-services.json`، مفاتيح `.jks`، ملفات Service Account، `.env` (كلها مستبعدة في `.gitignore`).

## 2) بناء APK

بعد الرفع: تبويب **Actions > Android > Run workflow**. عند النجاح افتح التشغيل ونزّل الملف المرفق `dalil-al-urdun-debug-apk` (APK تجريبي). إن فشل البناء فأرسل لي نص الخطأ من سجل الخطوة الفاشلة.
- نسخة Start.io محددة `5.+` في `gradle/libs.versions.toml`؛ بعد أول بناء ناجح ثبّتها على رقم محدد.
- نسخة الإصدار الموقّعة تحتاج مفتاح توقيع تنشئه أنت وتضعه كأسرار (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)؛ لم يُجهَّز مفتاح نيابة عنك.

## 3) ربط Firebase

1. console.firebase.google.com > **Add project**.
2. **Authentication > Sign-in method**: فعّل Email/Password.
3. **Firestore Database**: أنشئ قاعدة (وضع الإنتاج).
4. **Storage**: فعّله. تحقق من خطتك الحالية ومتطلباتها في صفحة Firebase للأسعار قبل الاعتماد عليه (قد يتطلب خطة بفوترة)؛ لم أتحقق من ذلك لحسابك.
5. **Project settings > Your apps**: أضف تطبيق Android بالمعرّف `jo.guide.app` (النسخة التجريبية تستخدم المعرّف نفسه) ونزّل `google-services.json`.
6. حوّل الملف إلى base64 وضعه سرًّا في GitHub باسم `GOOGLE_SERVICES_JSON_BASE64` (Settings > Secrets and variables > Actions). الـ workflow ينشئ الملف أثناء البناء ولا يُحفظ في المستودع.
7. أضف تطبيق Web وانسخ إعداداته إلى `admin-web/firebase-config.js` (ليست أسرارًا لكنها خاصة بمشروعك).
8. انسخ `.firebaserc.example` إلى `.firebaserc` وضع معرّف مشروعك.
9. انشر القواعد والفهارس: `firebase deploy --only firestore,storage`.

بدون `google-services.json` يُبنى التطبيق ويعمل في «وضع البيانات التجريبية».

## 4) المدير الرئيسي (bilalalmomani90@gmail.com)

لا تُمنح الصلاحية بمجرد كتابة البريد. الآلية:
1. سجّل في التطبيق أو لوحة الإدارة بهذا البريد وأكّد بريدك من رسالة التحقق (شرط `email_verified`).
2. ضع البريد في `functions/.env` (انسخ من `.env.example`) — يبقى على الخادم فقط.
3. انشر الدوال: `cd functions && npm install && firebase deploy --only functions` (تتطلب خطة Blaze في Firebase).
4. من شاشة حسابك أو لوحة الويب اضغط «تهيئة المالك» (تستدعي `bootstrapOwner`): تتحقق الدالة من أن المستدعي مسجّل وبريده مؤكد ومطابق تمامًا، ثم تضبط claim `admin`.
5. سجّل الخروج ثم الدخول لتحديث الرمز؛ تظهر لوحة الإدارة.

إلى أن تُنفَّذ هذه الخطوات فعليًا تعرض الواجهة تعليمات التهيئة بدل لوحة الإدارة. تعيين المحررين (`setRole`) للمالك فقط، ولا يُمنح أي دور افتراضيًا.

## 5) Start.io

1. أنشئ تطبيقًا في لوحة Start.io وانسخ **App ID**.
2. ضعه سرًّا في GitHub باسم `STARTIO_APP_ID` (أو محليًا: `./gradlew assembleDebug -PSTARTIO_APP_ID=...`).
3. بدونه: لا يُهيَّأ SDK ولا تظهر الإعلانات.

التخطيط: المحتوى (weight 1) ثم شريط التنقل ثم صف الإعلان؛ ليس عنصرًا عائمًا. الصف يبقى 1dp قبل وصول إعلان، ويُخفى مع لوحة المفاتيح وشاشتي الحساب والإدارة، وفشل الإعلان لا يعطّل التطبيق. الإعلان الأصلي يعمل في Android فقط وليس في لوحة الويب.

## 6) لوحة الإدارة (ويب)

`firebase deploy --only hosting` ينشر `admin-web/` على رابط مشروعك. تعتمد على Firestore/Storage والدوال أعلاه، والحماية الفعلية في القواعد وليس في إخفاء الأزرار.

## 7) الاختبارات محليًا

```bash
cd functions && npm test                       # منطق الأدوار
cd admin-web/js && node --test logic.test.js   # منطق اللوحة
cd tests && npm install && npm test            # قواعد Firestore/Storage (محاكي Firebase، يحتاج Java)
./gradlew testDebugUnitTest                    # اختبارات Kotlin (يحتاج Android SDK)
```

## الفوترة والحدود

راجع حدود خطة Firebase (التخزين والنقل وقراءات Firestore) وفعّل تنبيهات الميزانية في Google Cloud. صور وفيديوهات كبيرة تستهلك النقل بسرعة؛ حدود الحجم والصيغ مفروضة في `storage.rules`. App Check غير مفعّل بعد ويحتاج مزوّدًا تضبطه أنت (Play Integrity).

## البيانات

البيانات الأولية في `SeedData.kt` **تجريبية وليست بيانات حقيقية**؛ لا توجد أرقام هواتف أو تقييمات مخترعة. أدخل الأماكن الحقيقية عبر لوحة الإدارة مع المصدر وتاريخ التحقق.
