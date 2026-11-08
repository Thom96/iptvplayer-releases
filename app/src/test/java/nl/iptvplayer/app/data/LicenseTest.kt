package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** De regels voor de activatie: wanneer mag de app gebruikt worden. */
@RunWith(RobolectricTestRunner::class)
class LicenseTest {
    private val now = 1_800_000_000_000L
    private val day = License.DAY_MS

    private fun license(status: LicenseStatus, until: Long?, checked: Long = now, plan: String? = null) =
        License("7K4P-X2RM", status, plan, until, "https://gallo.example/activeren?code=7K4P-X2RM", checked)

    @Test
    fun trialWorksUntilItsEndDate() {
        assertTrue(license(LicenseStatus.TRIAL, now + day).allowsUse(now))
        assertFalse(license(LicenseStatus.TRIAL, now - 1).allowsUse(now))
    }

    @Test
    fun activeAndFreeWithoutEndDateAlwaysWork() {
        assertTrue(license(LicenseStatus.ACTIVE, null).allowsUse(now))
        assertTrue(license(LicenseStatus.FREE, null).allowsUse(now))
        assertFalse(license(LicenseStatus.ACTIVE, now - day).allowsUse(now))
    }

    @Test
    fun expiredAndBlockedNeverWork() {
        assertFalse(license(LicenseStatus.EXPIRED, null).allowsUse(now))
        assertFalse(license(LicenseStatus.BLOCKED, now + 365 * day).allowsUse(now))
    }

    @Test
    fun lastKnownStatusCountsForThreeDays() {
        assertTrue(license(LicenseStatus.ACTIVE, null, checked = now - 2 * day).isFresh(now))
        assertFalse(license(LicenseStatus.ACTIVE, null, checked = now - 4 * day).isFresh(now))
        // Klok teruggezet: niet vertrouwen.
        assertFalse(license(LicenseStatus.ACTIVE, null, checked = now + day).isFresh(now))
    }

    @Test
    fun daysLeftRoundsUp() {
        assertEquals(7, license(LicenseStatus.TRIAL, now + 6 * day + 1).daysLeft(now))
        assertEquals(0, license(LicenseStatus.TRIAL, now - day).daysLeft(now))
        assertNull(license(LicenseStatus.FREE, null).daysLeft(now))
    }

    @Test
    fun readsTheServerAnswer() {
        val l = License.fromServer(
            """{"code":"Q9D3-LM8T","status":"active","plan":"yearly","validUntil":"2027-11-08T10:00:00.000Z",""" +
                """"daysLeft":365,"activateUrl":"https://x/activeren?code=Q9D3-LM8T","serverTime":"2026-11-08T10:00:00.000Z"}""",
            now,
        )
        assertEquals("Q9D3-LM8T", l.code)
        assertEquals(LicenseStatus.ACTIVE, l.status)
        assertEquals("yearly", l.plan)
        assertEquals(1_825_668_000_000L, l.validUntil)

        val free = License.fromServer("""{"code":"A","status":"free","plan":null,"validUntil":null,"activateUrl":"u"}""", now)
        assertNull(free.validUntil)
        assertNull(free.plan)
    }

    @Test
    fun survivesSavingAndLoading() {
        val storage = Storage(RuntimeEnvironment.getApplication())
        val l = license(LicenseStatus.TRIAL, now + day, plan = "lifetime")
        storage.license = l
        assertEquals(l, storage.license)
        assertEquals(storage.deviceId, storage.deviceId)
    }
}
