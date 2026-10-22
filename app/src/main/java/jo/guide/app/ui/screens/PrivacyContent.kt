package jo.guide.app.ui.screens

/** نص سياسة الخصوصية (يُولَّد من نفس المصدر الذي تُولَّد منه صفحة admin-web/privacy.html). */
object PrivacyContent {
    const val CONTACT_EMAIL = "bilalalmomani90@gmail.com"
    const val UPDATED_AR = "22 أكتوبر 2026"
    const val UPDATED_EN = "October 22, 2026"
    class Section(val titleAr: String, val titleEn: String, val ar: List<String>, val en: List<String>)
    val sections: List<Section> = listOf(
        Section("المعلومات التي نتعامل معها", "Information we handle",
            listOf("الحساب (اختياري): إن أنشأت حساباً نخزّن بريدك الإلكتروني عبر خدمة Firebase Authentication التابعة لـ Google. كلمة المرور تُعالَج لدى Firebase ولا نراها ولا نخزّنها.", "المفضلة: تُحفظ على جهازك. إن سجّلت الدخول تُزامَن قائمة معرّفات الأماكن المفضلة مع حسابك على خوادمنا (Supabase).", "البلاغات والاقتراحات: ما تكتبه فيها (سبب البلاغ، تفاصيل، اسم مكان مقترح…) مع معرّف حسابك، ويراها فريق الإدارة فقط.", "الإعدادات: لغة التطبيق وخيار الإشعارات تُحفظ على جهازك فقط.", "التخزين المؤقت: نحفظ على جهازك نسخة من بيانات الأماكن لتعمل الصفحات دون اتصال."),
            listOf("Account (optional): if you create an account we store your email through Google's Firebase Authentication. Your password is handled by Firebase; we never see or store it.", "Favorites: saved on your device. If you sign in, the list of favorite place IDs is synced to your account on our servers (Supabase).", "Reports and suggestions: what you write in them (report reason, details, suggested place name…) together with your account ID; only the admin team can see them.", "Settings: the app language and notification choice are stored on your device only.", "Cache: we keep a copy of place data on your device so pages work offline.")),
        Section("ما لا نجمعه", "What we do not collect",
            listOf("لا نجمع موقعك الجغرافي، ولا جهات الاتصال، ولا الصور أو الملفات من جهازك، ولا الميكروفون أو الكاميرا.", "لا نبيع بياناتك الشخصية."),
            listOf("We do not collect your location, contacts, photos or files from your device, microphone or camera.", "We do not sell your personal data.")),
        Section("الإعلانات", "Advertising",
            listOf("يعرض التطبيق إعلانات بانر عبر شبكة Start.io. قد تجمع الشبكة معرّف الإعلانات في جهازك ومعلومات الجهاز وعنوان IP لعرض إعلانات وقياس أدائها، وفق سياسة خصوصية Start.io المنشورة على موقعها.", "لا نعرض الإعلانات في شاشات الحساب ولوحة الإدارة، ويمكنك إعادة ضبط أو تعطيل معرّف الإعلانات من إعدادات أندرويد (الخصوصية ← الإعلانات)."),
            listOf("The app shows banner ads through the Start.io network. The network may collect your device advertising ID, device information and IP address to serve and measure ads, under the Start.io privacy policy published on its website.", "Ads are not shown on the account and admin screens. You can reset or disable your advertising ID in Android settings (Privacy → Ads).")),
        Section("الإشعارات", "Notifications",
            listOf("الإشعارات اختيارية ومغلقة افتراضيًا. عند تفعيلها يتحقق التطبيق دوريًا من وجود أماكن جديدة ويعرض إشعارًا محليًا على جهازك. يمكنك إيقافها في أي وقت من شاشة «حسابي» أو من إعدادات أندرويد."),
            listOf("Notifications are optional and off by default. When enabled, the app periodically checks for new places and shows a local notification on your device. You can turn them off at any time from the Account screen or Android settings.")),
        Section("الأطراف التي نعتمد عليها", "Third parties we rely on",
            listOf("Google Firebase Authentication: تسجيل الدخول.", "Supabase: تخزين بيانات الأماكن والمفضلة والبلاغات والاقتراحات والوسائط.", "Start.io: الإعلانات.", "لكلٍّ منها سياسة خصوصية خاصة به."),
            listOf("Google Firebase Authentication: sign-in.", "Supabase: storing places, favorites, reports, suggestions and media.", "Start.io: advertising.", "Each has its own privacy policy.")),
        Section("حقوقك", "Your rights",
            listOf("يمكنك استخدام التطبيق كزائر دون حساب. يمكنك حذف حسابك وبياناتك المرتبطة به (المفضلة والبلاغات والاقتراحات) مباشرة من التطبيق: «حسابي ← حذف حسابي وبياناتي». وللاستفسار عن بياناتك راسلنا على البريد أدناه."),
            listOf("You can use the app as a guest without an account. You can delete your account and its linked data (favorites, reports, suggestions) directly in the app: Account → Delete my account and data. To ask about your data, email us at the address below.")),
        Section("الأطفال", "Children",
            listOf("التطبيق دليل عام ولا يستهدف الأطفال دون 13 عامًا، ولا نجمع عمدًا بيانات منهم."),
            listOf("The app is a general guide and is not directed to children under 13; we do not knowingly collect data from them.")),
        Section("التغييرات والتواصل", "Changes and contact",
            listOf("قد نحدّث هذه السياسة، وسيظهر تاريخ آخر تحديث أعلاها. للتواصل: bilalalmomani90@gmail.com"),
            listOf("We may update this policy; the last-updated date appears at the top. Contact: bilalalmomani90@gmail.com")),
    )
}
