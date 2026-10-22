package jo.guide.app.ui

import jo.guide.app.data.Category
import jo.guide.app.data.Place
import jo.guide.app.i18n.tr

/** نص مشاركة المكان: الاسم والتصنيف والعنوان ورابط الخريطة (إن وُجدت إحداثيات موثّقة) والموقع. لا نضيف أي معلومة غير موجودة. */
fun sharePlaceText(p: Place): String {
    val cat = Category.fromId(p.category)?.label
    val lines = buildList {
        add(p.displayName)
        val sub = listOfNotNull(cat, p.displayRegion).joinToString(" · ")
        if (sub.isNotBlank()) add(sub)
        p.address?.let { add(it) }
        if (p.lat != null && p.lng != null) add("https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lng}")
        p.website?.let { add(it) }
        add("")
        add(tr("— عبر تطبيق دليل الأردن", "— via the Jordan Guide app"))
    }
    return lines.joinToString("\n")
}
