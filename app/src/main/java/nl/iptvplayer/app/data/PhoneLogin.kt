package nl.iptvplayer.app.data

import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Sign in from a phone on the same Wi-Fi: while the login screen is open, the TV serves a small web page on the local
 * network. The QR code on the screen holds its address plus a one-time code; without that code nothing is accepted.
 * The details never leave the home network.
 *
 * [onCredentials] is called on a background thread.
 */
class PhoneLoginServer(private val onCredentials: (server: String, username: String, password: String) -> Unit) {
    private var socket: ServerSocket? = null
    private var baseUrl: String? = null

    @Volatile private var code: String = ""
    @Volatile private var expiresAt = 0L

    /** Starts listening. Returns false if this TV has no local network address (then no QR code is shown). */
    fun start(): Boolean {
        if (socket != null) return true
        val address = localAddress() ?: return false
        val s = try {
            ServerSocket(PREFERRED_PORT)
        } catch (e: IOException) {
            runCatching { ServerSocket(0) }.getOrNull() ?: return false
        }
        socket = s
        baseUrl = "http://${address}:${s.localPort}"
        thread(name = "phone-login", isDaemon = true) { acceptLoop(s) }
        return true
    }

    /** A fresh one-time code, valid for [CODE_VALID_MS]. Returns the address for the QR code. */
    fun newCode(now: Long = System.currentTimeMillis()): String? {
        val base = baseUrl ?: return null
        code = randomCode()
        expiresAt = now + CODE_VALID_MS
        return "$base/?c=$code"
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        baseUrl = null
        code = ""
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val client = try {
                s.accept()
            } catch (e: IOException) {
                break
            }
            thread(name = "phone-login-request", isDaemon = true) { client.use(::serve) }
        }
    }

    private fun serve(client: Socket) {
        client.soTimeout = 10_000
        val request = runCatching { readRequest(BufferedInputStream(client.getInputStream())) }.getOrNull() ?: return
        val response = handle(request.method, request.path, request.body)
        val body = response.html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 ${response.status}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            client.getOutputStream().apply {
                write(head.toByteArray(Charsets.ISO_8859_1))
                write(body)
                flush()
            }
        }
    }

    class Response(val status: String, val html: String)

    /** The whole web app: the form, and what happens when it is sent. Separate from the socket so it can be tested. */
    fun handle(method: String, path: String, body: String, now: Long = System.currentTimeMillis()): Response {
        val route = path.substringBefore('?')
        val query = parseForm(path.substringAfter('?', ""))
        return when {
            method == "GET" && route == "/" ->
                if (isValid(query["c"], now)) Response("200 OK", formPage(query["c"]!!, null))
                else Response("403 Forbidden", messagePage(S.phonePageExpired))
            method == "POST" && route == "/send" -> {
                val form = parseForm(body)
                val c = form["c"]
                val server = form["server"].orEmpty().trim()
                val username = form["username"].orEmpty().trim()
                val password = form["password"].orEmpty()
                when {
                    !isValid(c, now) -> Response("403 Forbidden", messagePage(S.phonePageExpired))
                    server.isEmpty() || username.isEmpty() -> Response("200 OK", formPage(c!!, S.phonePageMissing, server, username))
                    else -> {
                        // The code stays valid, so a typo can be corrected from the phone while the TV shows the error.
                        onCredentials(server, username, password)
                        Response("200 OK", messagePage(S.phonePageSent))
                    }
                }
            }
            else -> Response("404 Not Found", messagePage(S.phonePageExpired))
        }
    }

    private fun isValid(given: String?, now: Long): Boolean {
        val expected = code
        if (given == null || expected.isEmpty() || now > expiresAt) return false
        return MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())
    }

    private fun formPage(c: String, error: String?, server: String = "", username: String = ""): String = page(
        """
        <p>${esc(S.phonePageIntro)}</p>
        ${error?.let { "<p class=\"err\">${esc(it)}</p>" }.orEmpty()}
        <form method="post" action="/send">
          <input type="hidden" name="c" value="${esc(c)}">
          <label>${esc(S.serverPlaceholder)}<input name="server" value="${esc(server)}" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" required></label>
          <label>${esc(S.username)}<input name="username" value="${esc(username)}" autocomplete="username" autocapitalize="off" autocorrect="off" spellcheck="false" required></label>
          <label>${esc(S.password)}<input name="password" type="password" autocomplete="current-password"></label>
          <button type="submit">${esc(S.phonePageSend)}</button>
        </form>
        """,
    )

    private fun messagePage(message: String) = page("<p class=\"msg\">${esc(message)}</p>")

    /** In the colours of the app: petrol, silver-white text, champagne button. */
    private fun page(content: String) = """
        <!doctype html><html lang="${I18n.language.code}"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>GalloTV</title>
        <style>
          body{margin:0;background:#0B1A22;color:#EDF3F2;font:16px/1.5 system-ui,sans-serif;padding:32px 20px}
          main{max-width:420px;margin:0 auto}
          h1{font:400 36px Georgia,serif;margin:0 0 4px}h1 b{color:#E8CC94;font-weight:400}
          h2{font-size:17px;font-weight:500;color:#CFDCDD;margin:0 0 18px}
          p{color:#CFDCDD}.err{color:#E8907A}.msg{font-size:18px;color:#EDF3F2}
          form{display:grid;gap:14px}label{display:grid;gap:6px;font-size:14px;color:#A9BDC2}
          input{font:inherit;color:#EDF3F2;background:#14262F;border:1px solid rgba(237,243,242,.15);border-radius:10px;padding:12px}
          input:focus{outline:3px solid #E8CC94;border-color:transparent}
          button{font:600 16px system-ui,sans-serif;background:#E8CC94;color:#07131A;border:0;border-radius:24px;padding:14px;margin-top:6px}
        </style></head>
        <body><main><h1>Gallo<b>TV</b></h1><h2>${esc(S.phoneLoginTitle)}</h2>$content</main></body></html>
    """.trimIndent()

    private class Request(val method: String, val path: String, val body: String)

    private fun readRequest(input: InputStream): Request? {
        val head = StringBuilder()
        // Headers end with an empty line; a request without one within 8 KB is ignored.
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0 || head.length > 8_192) return null
            head.append(b.toChar())
        }
        val lines = head.lines()
        val parts = lines.first().split(' ')
        if (parts.size < 2) return null
        val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        if (length !in 0..MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return Request(parts[0], parts[1], String(body, Charsets.UTF_8))
    }

    companion object {
        const val PREFERRED_PORT = 8765
        const val CODE_VALID_MS = 10 * 60 * 1000L
        private const val MAX_BODY = 4_096
        private val random = SecureRandom()
        private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

        private fun randomCode(): String = (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        fun parseForm(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            decode(k) to decode(v)
        }

        private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

        /** IPv4 address of this TV on the home network (Wi-Fi or Ethernet), or null. */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
