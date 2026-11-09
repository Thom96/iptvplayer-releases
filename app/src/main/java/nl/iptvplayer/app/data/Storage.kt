package nl.iptvplayer.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local storage: login details, favourites and watch progress (the latter per profile, see [useProfile]). The password is stored encrypted
 * (see [SecretCipher]); an old password stored in plain text is converted when it is read.
 */
class Storage(private val context: Context, private val cipher: SecretCipher = KeystoreCipher()) {
    private val prefs = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    /**
     * Storage of the active profile: favourites, watched, continue watching, recent channels and languages.
     * The first profile uses the same file as before profiles existed, so an update keeps everything.
     */
    private var profilePrefs = prefs

    /** Switches to the data of another profile; the flows below then show that profile's lists. */
    fun useProfile(id: String) {
        profilePrefs = if (id == Profile.MAIN) prefs else context.getSharedPreferences("iptv_profile_$id", Context.MODE_PRIVATE)
        _favorites.value = profilePrefs.getStringSet("favorites", emptySet())!!.toSet()
        _watched.value = profilePrefs.getStringSet("watched", emptySet())!!.toSet()
        _progress.value = loadProgress()
        _recentLive.value = loadRecentLive()
        _languages.value = profilePrefs.getStringSet("languages", emptySet())!!.toSet()
    }

    /** Removes everything a deleted profile had stored. */
    fun deleteProfileData(id: String) {
        if (id == Profile.MAIN) return
        context.getSharedPreferences("iptv_profile_$id", Context.MODE_PRIVATE).edit().clear().apply()
    }

    var credentials: Credentials?
        get() {
            val server = prefs.getString("server", null) ?: return null
            val user = prefs.getString("username", null) ?: return null
            val pass = prefs.getString("passwordEnc", null)?.let { enc ->
                // Can't be decrypted (key gone): log in again, server and name are already filled in.
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

    /** Store encrypted; if that doesn't work on this device, store as before, so login keeps working. */
    private fun savePassword(password: String) {
        val enc = runCatching { cipher.encrypt(password) }.getOrNull()
        prefs.edit().apply {
            if (enc != null) putString("passwordEnc", enc).remove("password")
            else putString("password", password).remove("passwordEnc")
        }.apply()
    }

    /** Last used server and username, to prefill the login screen. */
    val lastServer: String get() = prefs.getString("server", "") ?: ""
    val lastUsername: String get() = prefs.getString("username", "") ?: ""

    // ---------- Favourites ----------

    private val _favorites = MutableStateFlow(profilePrefs.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites

    fun toggleFavorite(key: String) {
        val next = _favorites.value.let { if (key in it) it - key else it + key }
        _favorites.value = next
        profilePrefs.edit().putStringSet("favorites", next).apply()
    }

    // ---------- Watched ----------

    private val _watched = MutableStateFlow(profilePrefs.getStringSet("watched", emptySet())!!.toSet())
    val watched: StateFlow<Set<String>> = _watched

    // ---------- Continue watching ----------

    private val _progress = MutableStateFlow(loadProgress())
    /** Newest first. */
    val progress: StateFlow<List<Progress>> = _progress

    fun progressFor(key: String): Progress? = _progress.value.firstOrNull { it.key == key }

    /** Saves the position; almost finished counts as watched and drops out of "Continue watching". */
    fun saveProgress(p: Progress) {
        val finished = p.durationMs > 0 && p.positionMs >= p.durationMs * 0.93
        if (finished) {
            markWatched(p.key)
            setProgress(_progress.value.filterNot { it.key == p.key })
            return
        }
        if (p.positionMs < 30_000) return
        // A series has only one entry in "Continue watching": the last watched episode.
        val rest = _progress.value.filterNot { it.key == p.key || (p.seriesId != null && it.seriesId == p.seriesId) }
        setProgress((listOf(p) + rest).take(MAX_PROGRESS))
    }

    fun removeProgress(key: String) = setProgress(_progress.value.filterNot { it.key == key })

    /** Queue the next episode in "Continue watching" for series, even though it hasn't started yet. */
    fun saveNextUp(p: Progress) {
        val rest = _progress.value.filterNot { it.key == p.key || (p.seriesId != null && it.seriesId == p.seriesId) }
        setProgress((listOf(p) + rest).take(MAX_PROGRESS))
    }

    /** Finished: mark as watched and remove from "continue watching". */
    fun markFinished(key: String) {
        markWatched(key)
        setProgress(_progress.value.filterNot { it.key == key })
    }

    // ---------- Recently watched channels ----------

    private val _recentLive = MutableStateFlow(loadRecentLive())
    /** Last watched channels, newest first (max. 10). */
    val recentLive: StateFlow<List<String>> = _recentLive

    fun addRecentLive(key: String) = setRecentLive((listOf(key) + _recentLive.value.filterNot { it == key }).take(10))

    fun removeRecentLive(key: String) = setRecentLive(_recentLive.value.filterNot { it == key })

    private fun loadRecentLive() = (profilePrefs.getString("recentLive", "") ?: "").split(',').filter { it.isNotEmpty() }

    private fun setRecentLive(list: List<String>) {
        _recentLive.value = list
        profilePrefs.edit().putString("recentLive", list.joinToString(",")).apply()
    }

    // ---------- Stream format ----------

    private val _liveFormat = MutableStateFlow(prefs.getString("liveFormat", "auto") ?: "auto")
    /** "auto" (what the server recommends), "ts" or "m3u8". */
    val liveFormat: StateFlow<String> = _liveFormat

    fun setLiveFormat(format: String) {
        _liveFormat.value = format
        prefs.edit().putString("liveFormat", format).apply()
    }

    /** Live format the server recommended last time ("ts" or "m3u8"), so zapping starts right away. */
    var serverLiveFormat: String?
        get() = prefs.getString("serverLiveFormat", null)
        set(value) { prefs.edit().putString("serverLiveFormat", value).apply() }

    // ---------- App language ----------

    /** Code of the chosen app language (e.g. "en"); null as long as nothing was chosen on first start. */
    var appLanguage: String?
        // A new profile starts in the language of the device's first profile.
        get() = profilePrefs.getString("appLanguage", null) ?: prefs.getString("appLanguage", null)
        set(value) { profilePrefs.edit().putString("appLanguage", value).apply() }

    // ---------- Activation ----------

    /** This device's own ID at the Gallo server; created once and always the same after that. */
    val deviceId: String
        get() = prefs.getString("deviceId", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }

    /** Last known status from the server, for when it can't be reached for a while. */
    var license: License?
        get() = License.fromJson(prefs.getString("license", null))
        set(value) { prefs.edit().putString("license", value?.toJson()).apply() }

    // ---------- Languages of the catalogue ----------

    private val _languages = MutableStateFlow(profilePrefs.getStringSet("languages", emptySet())!!.toSet())
    /** Chosen language labels (e.g. "NL", "MULTI"); empty = show all languages. */
    val languages: StateFlow<Set<String>> = _languages

    fun toggleLanguage(tag: String) = setLanguages(_languages.value.let { if (tag in it) it - tag else it + tag })

    /** All chosen languages at once; empty = show all languages. */
    fun setLanguages(tags: Set<String>) {
        _languages.value = tags
        profilePrefs.edit().putStringSet("languages", tags).apply()
    }

    private fun markWatched(key: String) {
        val next = _watched.value + key
        _watched.value = next
        profilePrefs.edit().putStringSet("watched", next).apply()
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
        profilePrefs.edit().putString("progress", arr.toString()).apply()
    }

    private fun loadProgress(): List<Progress> {
        val arr = try {
            JSONArray(profilePrefs.getString("progress", "[]"))
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
