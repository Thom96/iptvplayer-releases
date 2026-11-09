package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The web page the TV serves for signing in from a phone. */
@RunWith(RobolectricTestRunner::class)
class PhoneLoginTest {
    private val now = 1_800_000_000_000L
    private val received = mutableListOf<Triple<String, String, String>>()
    private val server = PhoneLoginServer { s, u, p -> received += Triple(s, u, p) }

    /** [PhoneLoginServer.newCode] needs a socket; [PhoneLoginServer.handle] does not, so the code is set directly. */
    private fun code(): String {
        val f = PhoneLoginServer::class.java.getDeclaredField("code").apply { isAccessible = true }
        val e = PhoneLoginServer::class.java.getDeclaredField("expiresAt").apply { isAccessible = true }
        f.set(server, "abc123xyz789")
        e.setLong(server, now + PhoneLoginServer.CODE_VALID_MS)
        return "abc123xyz789"
    }

    @Test
    fun formNeedsTheCode() {
        val c = code()
        assertTrue(server.handle("GET", "/?c=$c", "", now).status.startsWith("200"))
        assertTrue(server.handle("GET", "/?c=wrong", "", now).status.startsWith("403"))
        assertTrue(server.handle("GET", "/", "", now).status.startsWith("403"))
    }

    @Test
    fun sendingPassesTheDetailsOn() {
        val c = code()
        val r = server.handle("POST", "/send", "c=$c&server=http%3A%2F%2Fprov.tv%3A8080&username=jan&password=p%26ss+w", now)
        assertTrue(r.status.startsWith("200"))
        assertEquals(listOf(Triple("http://prov.tv:8080", "jan", "p&ss w")), received)
    }

    @Test
    fun wrongOrExpiredCodeSendsNothing() {
        val c = code()
        assertTrue(server.handle("POST", "/send", "c=nope&server=a&username=b", now).status.startsWith("403"))
        val late = now + PhoneLoginServer.CODE_VALID_MS + 1
        assertTrue(server.handle("POST", "/send", "c=$c&server=a&username=b", late).status.startsWith("403"))
        assertTrue(received.isEmpty())
    }

    @Test
    fun missingFieldsShowTheFormAgain() {
        val c = code()
        val r = server.handle("POST", "/send", "c=$c&server=&username=jan", now)
        assertTrue(r.html.contains("value=\"jan\""))
        assertTrue(received.isEmpty())
    }

    @Test
    fun valuesAreEscaped() {
        assertEquals("&lt;b&gt; &quot;x&quot; &amp;", PhoneLoginServer.esc("<b> \"x\" &"))
        assertFalse(PhoneLoginServer.esc("'").contains("'"))
    }

    @Test
    fun noCodeWithoutStart() {
        assertNull(PhoneLoginServer { _, _, _ -> }.newCode(now))
    }
}
