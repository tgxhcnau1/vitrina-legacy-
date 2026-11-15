package stoyck.vitrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import stoyck.vitrina.persistence.data.PersistedSubredditData
import stoyck.vitrina.persistence.data.VitrinaBackupCodec

class VitrinaBackupTest {

    @Test
    fun backupRoundTrip_keepsNamesAndMinUpvotes() {
        val original = listOf(
            PersistedSubredditData(id = "2sbq3", name = "EarthPorn", minUpvoteCount = 1200),
            PersistedSubredditData(id = "direct_u_someuser", name = "u/SomeUser", minUpvoteCount = 25)
        )

        val restored = VitrinaBackupCodec.decode(VitrinaBackupCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun decode_removesDuplicatesBlankNamesAndNegativeUpvotes() {
        val json = """
            {
              "app": "vitrina+",
              "version": 1,
              "subreddits": [
                { "id": "a", "name": "EarthPorn", "minUpvoteCount": -5 },
                { "id": "b", "name": "earthporn", "minUpvoteCount": 99 },
                { "id": "", "name": "  ", "minUpvoteCount": 10 },
                { "id": "", "name": "CityPorn", "minUpvoteCount": 145 }
              ]
            }
        """.trimIndent()

        val restored = VitrinaBackupCodec.decode(json)

        assertEquals(2, restored.size)
        assertEquals("EarthPorn", restored[0].name)
        assertEquals(0L, restored[0].minUpvoteCount)
        assertEquals("CityPorn", restored[1].name)
        assertEquals(145L, restored[1].minUpvoteCount)
        assertTrue(restored[1].id.isNotBlank())
    }

    @Test
    fun decode_rejectsInvalidFile() {
        try {
            VitrinaBackupCodec.decode("this is not json")
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }
}
