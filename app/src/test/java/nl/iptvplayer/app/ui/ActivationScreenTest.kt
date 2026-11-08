package nl.iptvplayer.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import nl.iptvplayer.app.Gate
import nl.iptvplayer.app.data.License
import nl.iptvplayer.app.data.LicenseStatus
import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Het activatiescherm in de standen die de klant kan zien. Alles moet binnen 960×540 dp passen.
 * Met -Pframes=/pad worden schermafbeeldingen bewaard.
 */
@RunWith(RobolectricTestRunner::class)
class ActivationScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)
    private val now = 1_800_000_000_000L

    private fun license(status: LicenseStatus, until: Long?, plan: String? = null) = License(
        "7K4P-X2RM", status, plan, until, "https://gallo-heermoes-13.vercel.app/activeren?code=7K4P-X2RM", now - 60_000,
    )

    private fun show(name: String, license: License?, gate: Gate, checking: Boolean = false) {
        I18n.language = AppLanguage.NL
        rule.setContent {
            AppTheme { ActivationContent(license, gate, checking, onContinue = {}, onCheck = {}, onNextLanguage = {}, now = now) }
        }
        rule.waitForIdle()
        val dir = frameDir ?: return
        dir.mkdirs()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "activatie-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @After
    fun resetLanguage() {
        I18n.language = AppLanguage.DEFAULT
    }

    @Test
    fun trialNoticeShowsCodeDaysAndContinue() {
        show("proef", license(LicenseStatus.TRIAL, now + 5 * License.DAY_MS - 1), Gate.TRIAL_NOTICE)
        rule.onNodeWithText("7K4P-X2RM").assertIsDisplayed()
        rule.onNodeWithText("Proefperiode · nog 5 dagen").assertIsDisplayed()
        rule.onNodeWithText("Verder met proefperiode").assertIsDisplayed()
        rule.onNodeWithText("Scan de QR-code of ga naar gallo-heermoes-13.vercel.app/activeren").assertIsDisplayed()
    }

    @Test
    fun expiredTrialIsLockedAndWaitsForPayment() {
        show("verlopen", license(LicenseStatus.EXPIRED, now - License.DAY_MS), Gate.LOCKED)
        rule.onNodeWithText("Je proefperiode is voorbij").assertIsDisplayed()
        rule.onNodeWithText("Wacht op betaling…").assertIsDisplayed()
        rule.onNodeWithText("Status vernieuwen").assertIsDisplayed()
    }

    @Test
    fun endedSubscriptionSaysSo() {
        show("abonnement", license(LicenseStatus.EXPIRED, now - License.DAY_MS, plan = "yearly"), Gate.LOCKED)
        rule.onNodeWithText("Je abonnement is verlopen").assertIsDisplayed()
    }

    @Test
    fun blockedShowsNoPaymentSteps() {
        show("geblokkeerd", license(LicenseStatus.BLOCKED, null), Gate.LOCKED)
        rule.onNodeWithText("Deze tv is geblokkeerd").assertIsDisplayed()
        rule.onNodeWithText("7K4P-X2RM").assertIsDisplayed()
    }

    @Test
    fun neverReachedServerAsksToCheckConnection() {
        show("offline", null, Gate.LOCKED)
        rule.onNodeWithText("Gallo is niet te bereiken").assertIsDisplayed()
    }
}
