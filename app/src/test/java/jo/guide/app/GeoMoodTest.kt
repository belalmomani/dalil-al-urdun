package jo.guide.app

import jo.guide.app.data.Geo
import jo.guide.app.data.Mood
import jo.guide.app.data.Place
import jo.guide.app.data.PlaceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMoodTest {
    private fun p(id: String, name: String, cat: String = "tourism", desc: String = "", lat: Double? = null, lng: Double? = null, kids: Boolean? = null, price: Int? = null) =
        Place(id = id, name = name, category = cat, description = desc, lat = lat, lng = lng, kidsFriendly = kids, priceLevel = price)

    @Test fun distanceAmmanToAqabaAbout330km() {
        val d = Geo.distanceKm(31.95, 35.93, 29.53, 35.01)
        assertTrue(d in 300.0..340.0)
    }
    @Test fun phoneValidation() {
        assertTrue(Geo.validPhone("+962 6 123 4567"))
        assertFalse(Geo.validPhone("abc"))
        assertFalse(Geo.validPhone(null))
        assertFalse(Geo.validPhone("123"))
    }
    @Test fun historyMoodMatchesCastleOnly() {
        val f = Mood.HISTORY.filter()
        assertTrue(f.matches(p("a", "قلعة عجلون")))
        assertFalse(f.matches(p("b", "مطعم هاشم", "restaurants", "مطعم شعبي")))
    }
    @Test fun foodMoodUsesCategory() {
        assertEquals(setOf("restaurants"), Mood.FOOD.filter().categories)
    }
    @Test fun familyMoodRequiresVerifiedKids() {
        val f = Mood.FAMILY.filter()
        assertFalse(f.matches(p("a", "حديقة")))
        assertTrue(f.matches(p("b", "حديقة", kids = true)))
    }
    @Test fun moodCountsAsActiveFilter() {
        assertEquals(1, Mood.PARKS.filter().activeCount)
        assertTrue(PlaceFilter().isEmpty)
    }
    @Test fun nearbySortsAndIgnoresNoCoords() {
        val list = listOf(p("far", "x", lat = 29.53, lng = 35.01), p("near", "y", lat = 31.96, lng = 35.94), p("none", "z"))
        val r = Geo.nearby(list, 31.95, 35.93, 500.0)
        assertEquals(listOf("near", "far"), r.map { it.first.id })
    }
}
