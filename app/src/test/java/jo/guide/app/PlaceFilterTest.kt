package jo.guide.app

import jo.guide.app.data.Place
import jo.guide.app.data.PlaceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceFilterTest {

    private val petra = Place(
        id = "petra", name = "البتراء", category = "tourism", regionId = "maan", regionName = "معان",
        priceLevel = 3, ageGroups = listOf("kids", "adults"), features = listOf("parking"),
        kidsFriendly = true,
    )
    private val cafe = Place(
        id = "cafe", name = "مقهى الجبل", category = "restaurants", regionId = "amman", regionName = "عمّان",
        priceLevel = 1, ageGroups = listOf("adults"), features = listOf("wifi", "outdoor"),
    )
    private val unknown = Place(id = "u", name = "مكان غير موثق", category = "restaurants", regionId = "amman")
    private val all = listOf(petra, cafe, unknown)

    @Test fun emptyFilterKeepsEverything() {
        assertEquals(3, PlaceFilter().apply(all).size)
    }

    @Test fun multiplePriceLevelsAreOr() {
        val r = PlaceFilter(priceLevels = setOf(1, 3)).apply(all)
        assertEquals(setOf("petra", "cafe"), r.map { it.id }.toSet())
    }

    @Test fun multipleAgeGroupsAreOr() {
        val r = PlaceFilter(ageGroups = setOf("kids", "seniors")).apply(all)
        assertEquals(listOf("petra"), r.map { it.id })
    }

    @Test fun featuresAreAnd() {
        assertEquals(listOf("cafe"), PlaceFilter(features = setOf("wifi", "outdoor")).apply(all).map { it.id })
        assertTrue(PlaceFilter(features = setOf("wifi", "parking")).apply(all).isEmpty())
    }

    @Test fun unknownDataIsExcludedWhenFilteringByIt() {
        assertTrue(PlaceFilter(priceLevels = setOf(1, 2, 3)).apply(all).none { it.id == "u" })
        assertTrue(PlaceFilter(kidsFriendlyOnly = true).apply(all).none { it.id == "u" })
    }

    @Test fun arabicNormalizationMatchesVariants() {
        assertEquals(listOf("cafe"), PlaceFilter(query = "مقهي الجبل").apply(all).map { it.id })
        assertEquals(listOf("cafe"), PlaceFilter(query = "عمان").apply(all).map { it.id })
        assertEquals(listOf("petra"), PlaceFilter(query = "البترآء".replace('آ', 'ا')).apply(all).map { it.id })
    }

    @Test fun groupsCombineWithAnd() {
        val r = PlaceFilter(categories = setOf("restaurants"), priceLevels = setOf(1)).apply(all)
        assertEquals(listOf("cafe"), r.map { it.id })
    }
}
