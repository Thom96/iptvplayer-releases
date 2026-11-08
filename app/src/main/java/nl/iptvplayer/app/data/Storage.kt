package nl.iptvplayer.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokale opslag: inloggegevens, favorieten en kijkvoortgang. Het wachtwoord staat versleuteld opgeslagen
 * (zie [SecretCipher]); een oud, leesbaar opgeslagen wachtwoord wordt bij het lezen omgezet.
 */
class Storage(context: Context, private val cipher: SecretCipher = KeystoreCipher()) {
    private val prefs = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    var credentials: Credentials?
        get() {
            val server = prefs.getString("server", null) ?: return null
            val user = prefs.getString("username", null) ?: return null
            val pass = prefs.getString("passwordEnc", null)?.let { enc ->
                // Niet te ontsleutelen (sleutel weg): opnieuw inloggen, server en naam staan al ingevuld.
                cipher.decrypt(enc) ?: return null
            } ?: prefs.getString("password", null)?.also(::savePassword) ?: return null
            return Credentials(server, user, pass)
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("password").remove("passwordEnc").apply()
            } else {
                prefs.edit().putString("server", value.server).putString("username", value.username).apply()
                savePassword(value.password)
            }
        }

    /** Versleuteld bewaren; lukt dat op dit toestel niet, dan zoals vroeger, zodat inloggen blijft werken. */
    private fun savePassword(password: String) {
        val enc = runCatching { cipher.encrypt(password) }.getOrNull()
        prefs.edit().apply {
            if (enc != null) putString("passwordEnc", enc).remove("password")
            else putString("password", password).remove("passwordEnc")
        }.apply()
    }

    /** Laatst gebruikte server en gebruikersnaam, om het inlogscherm voor te vullen. */
    val lastServer: String get() = prefs.getString("server", "") ?: ""
    val lastUsername: String get() = prefs.getString("username", "") ?: ""

    // ---------- Favorieten ----------

    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites

    fun toggleFavorite(key: String) {
        val next = _favorites.value.let { if (key in it) it - key else it + key }
        _favorites.value = next
        prefs.edit().putStringSet("favorites", next).apply()
    }

    // ---------- Gezien ----------

    private val _watched = MutableStateFlow(prefs.getStringSet("watched", emptySet())!!.toSet())
    val watched: StateFlow<Set<String>> = _watched

    // ---------- Verder kijken ----------

    private val _progress = MutableStateFlow(loadProgress())
    /** Nieuwste eerst. */
    val progress: StateFlow<List<Progress>> = _progress

    fun progressFor(key: String): Progress? = _progress.value.firstOrNull { it.key == key }

    /** Slaat de positie op; bijna uitgekeken telt als gezien en verdwijnt uit "Verder kijken". */
    fun saveProgress(p: Progress) {
        val finished = p.durationMs > 0 && p.positionMs >= p.durationMs * 0.93
        if (finished) {
            markWatched(p.key)
            setProgress(_progress.value.filterNot { it.key == p.key })
            return
        }
        if (p.positionMs < 30_000) return
        // Een serie heeft maar één regel in "Verder kijken": de laatst bekeken aflevering.
        val rest = _progress.value.filterNot { it.key == p.key || (p.seriesId != null && it.seriesId == p.seriesId) }
        setProgress((listOf(p) + rest).take(MAX_PROGRESS))
    }

    fun removeProgress(key: String) = setProgress(_progress.value.filterNot { it.key == key })

    /** Volgende aflevering klaarzetten in "Serie verder kijken", ook al is die nog niet gestart. */
    fun saveNextUp(p: Progress) {
        val rest = _progress.value.filterNot { it.key == p.key || (p.seriesId != null && it.seriesId == p.seriesId) }
        setProgress((listOf(p) + rest).take(MAX_PROGRESS))
    }

    /** Uitgekeken: als gezien markeren en uit "verder kijken" halen. */
    fun markFinished(key: String) {
        markWatched(key)
        setProgress(_progress.value.filterNot { it.key == key })
    }

    // ---------- Recent bekeken zenders ----------

    private val _recentLive = MutableStateFlow(
        (prefs.getString("recentLive", "") ?: "").split(',').filter { it.isNotEmpty() },
    )
    /** Laatst bekeken zenders, nieuwste eerst (max. 10). */
    val recentLive: StateFlow<List<String>> = _recentLive

    fun addRecentLive(key: String) = setRecentLive((listOf(key) + _recentLive.value.filterNot { it == key }).take(10))

    fun removeRecentLive(key: String) = setRecentLive(_recentLive.value.filterNot { it == key })

    private fun setRecentLive(list: List<String>) {
        _recentLive.value = list
        prefs.edit().putString("recentLive", list.joinToString(",")).apply()
    }

    // ---------- Streamformaat ----------

    private val _liveFormat = MutableStateFlow(prefs.getString("liveFormat", "auto") ?: "auto")
    /** "auto" (wat de server aanbeveelt), "ts" of "m3u8". */
    val liveFormat: StateFlow<String> = _liveFormat

    fun setLiveFormat(format: String) {
        _liveFormat.value = format
        prefs.edit().putString("liveFormat", format).apply()
    }

    /** Live-formaat dat de server de vorige keer aanbeval ("ts" of "m3u8"), zodat zappen direct goed start. */
    var serverLiveFormat: String?
        get() = prefs.getString("serverLiveFormat", null)
        set(value) { prefs.edit().putString("serverLiveFormat", value).apply() }

    // ---------- Taal van de app ----------

    /** Code van de gekozen app-taal (bijv. "en"); null zolang er bij de eerste start nog niet gekozen is. */
    var appLanguage: String?
        get() = prefs.getString("appLanguage", null)
        set(value) { prefs.edit().putString("appLanguage", value).apply() }

    // ---------- Talen van het aanbod ----------

    private val _languages = MutableStateFlow(prefs.getStringSet("languages", emptySet())!!.toSet())
    /** Gekozen taallabels (bijv. "NL", "MULTI"); leeg = alle talen tonen. */
    val languages: StateFlow<Set<String>> = _languages

    fun toggleLanguage(tag: String) = setLanguages(_languages.value.let { if (tag in it) it - tag else it + tag })

    /** Alle gekozen talen in één keer; leeg = alle talen tonen. */
    fun setLanguages(tags: Set<String>) {
        _languages.value = tags
        prefs.edit().putStringSet("languages", tags).apply()
    }

    private fun markWatched(key: String) {
        val next = _watched.value + key
        _watched.value = next
        prefs.edit().putStringSet("watched", next).apply()
    }

    private fun setProgress(list: List<Progress>) {
        _progress.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("key", p.key)
                put("title", p.title)
                p.subtitle?.let { put("subtitle", it) }
                p.image?.let { put("image", it) }
                p.ext?.let { put("ext", it) }
                p.seriesId?.let { put("seriesId", it) }
                put("position", p.positionMs)
                put("duration", p.durationMs)
                put("updated", p.updatedAt)
            })
        }
        prefs.edit().putString("progress", arr.toString()).apply()
    }

    private fun loadProgress(): List<Progress> {
        val arr = try {
            JSONArray(prefs.getString("progress", "[]"))
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Progress(
                key = o.optString("key"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle").ifEmpty { null },
                image = o.optString("image").ifEmpty { null },
                ext = o.optString("ext").ifEmpty { null },
                seriesId = if (o.has("seriesId")) o.optInt("seriesId") else null,
                positionMs = o.optLong("position"),
                durationMs = o.optLong("duration"),
                updatedAt = o.optLong("updated"),
            )
        }
    }

    private companion object {
        const val MAX_PROGRESS = 40
    }
}
