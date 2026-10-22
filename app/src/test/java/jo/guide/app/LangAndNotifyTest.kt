package jo.guide.app

import jo.guide.app.i18n.Lang
import jo.guide.app.notify.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LangAndNotifyTest {
    @Test fun systemFollowsDeviceArabic() { assertEquals("ar", Lang.resolve("system", "ar")) }
    @Test fun systemFallsBackToEnglish() { assertEquals("en", Lang.resolve("system", "fr")); assertEquals("en", Lang.resolve("system", "en")) }
    @Test fun manualChoiceWins() { assertEquals("en", Lang.resolve("en", "ar")); assertEquals("ar", Lang.resolve("ar", "en")) }
    @Test fun firstRunNoNotification() { assertNull(Notifications.newCount(null, setOf("a", "b"))) }
    @Test fun countsOnlyNewIds() {
        assertEquals(1, Notifications.newCount(setOf("a", "b"), setOf("a", "b", "c")))
        assertEquals(0, Notifications.newCount(setOf("a", "b"), setOf("a")))
    }
}
