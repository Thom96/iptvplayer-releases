package nl.iptvplayer.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.ProfileMode
import nl.iptvplayer.app.data.Profile
import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** "Who's watching?", the profile editor and the PIN pad, within 960×540 dp. With -Pframes=/path, screenshots are saved. */
@RunWith(RobolectricTestRunner::class)
class ProfileScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun vmWithFamily(): AppViewModel {
        I18n.language = AppLanguage.NL
        val vm = AppViewModel(rule.activity.application)
        vm.saveProfile(Profile(Profile.MAIN, "Thom", 0))
        vm.saveProfile(Profile("p1", "Sanne", 1))
        vm.setPin("1234")
        vm.saveProfile(Profile("p2", "Lotte", 2, kid = true))
        return vm
    }

    private fun snapshot(name: String) {
        rule.waitForIdle()
        val dir = frameDir ?: return
        dir.mkdirs()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "profiel-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @After
    fun resetLanguage() {
        I18n.language = AppLanguage.DEFAULT
    }

    @Test
    fun pickerShowsProfilesAndLockedOneAsksForPin() {
        val vm = vmWithFamily()
        rule.setContent { AppTheme { ProfileScreen(vm, ProfileMode.PICK) } }
        rule.onNodeWithText("Wie kijkt er?").assertIsDisplayed()
        rule.onNodeWithText("Kind").assertIsDisplayed()
        snapshot("kiezen")

        // The first kids profile locked the grown-up profiles.
        rule.onNodeWithContentDescription("Sanne").performClick()
        rule.onNodeWithText("Voer de pincode in").assertIsDisplayed()
        snapshot("pincode")
        listOf("1", "2", "3", "4").forEach { rule.onNodeWithText(it).performClick() }
        rule.waitForIdle()
        assertEquals("p1", vm.activeProfile.id)
    }

    @Test
    fun kidProfileOpensWithoutPin() {
        val vm = vmWithFamily()
        rule.setContent { AppTheme { ProfileScreen(vm, ProfileMode.PICK) } }
        rule.onNodeWithContentDescription("Lotte").performClick()
        rule.waitForIdle()
        assertEquals("p2", vm.activeProfile.id)
    }

    @Test
    fun editorShowsKidsSettings() {
        val vm = vmWithFamily()
        vm.settingsUnlocked = true
        rule.setContent { AppTheme { ProfileScreen(vm, ProfileMode.MANAGE) } }
        rule.onNodeWithText("Profielen beheren").assertIsDisplayed()
        snapshot("beheren")
        rule.onNodeWithContentDescription("Lotte").performClick()
        rule.onNodeWithText("Wat mag dit profiel zien?").assertIsDisplayed()
        snapshot("bewerken")
    }
}
