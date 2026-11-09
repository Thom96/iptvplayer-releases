package nl.iptvplayer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A person who watches on this TV. Each profile has its own continue watching, favourites, history and languages
 * (see [Storage.useProfile]); login, device code and licence belong to the whole device.
 *
 * [kid]: only sees what the parental filter lets through ([ParentalFilter]).
 * [locked]: opening this profile asks for the PIN (so a child can't simply pick a parent's profile).
 * [hidden]/[shown]: category keys ("LIVE:12") the parent hid or explicitly allowed in a kid profile.
 */
data class Profile(
    val id: String,
    val name: String,
    val color: Int,
    val kid: Boolean = false,
    val locked: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val shown: Set<String> = emptySet(),
) {
    companion object {
        /** The first profile: it keeps using the storage from before profiles existed, so nothing is lost on update. */
        const val MAIN = "main"
        const val MAX = 5
        /** Avatar colours (index into [nl.iptvplayer.app.ui.Palette.avatars]). */
        const val COLORS = 6
    }
}

/** Profiles and the PIN, stored separately from [Storage] so the existing data stays exactly where it was. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("iptv_profiles", Context.MODE_PRIVATE)

    /** Always at least one profile: [Profile.MAIN]. Its empty name is shown as the account name or "Profile 1". */
    var profiles: List<Profile> = load()
        private set

    var activeId: String
        get() = prefs.getString("active", null)?.takeIf { id -> profiles.any { it.id == id } } ?: Profile.MAIN
        set(value) { prefs.edit().putString("active", value).apply() }

    val active: Profile get() = profiles.first { it.id == activeId }

    fun get(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** Adds or replaces a profile (matched on id). */
    fun save(profile: Profile) {
        val list = profiles.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else if (list.size < Profile.MAX) list += profile else return
        store(list)
    }

    /** The first profile can't be removed: it holds the data from before profiles existed. */
    fun delete(id: String) {
        if (id == Profile.MAIN) return
        store(profiles.filterNot { it.id == id })
        if (activeId == id) activeId = Profile.MAIN
    }

    fun newId(): String = "p" + System.currentTimeMillis().toString(36)

    // ---------- PIN ----------

    val hasPin: Boolean get() = prefs.getString("pinHash", null) != null

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pinSalt", null) ?: return false
        return hash(salt, pin) == prefs.getString("pinHash", null)
    }

    /** null removes the PIN. */
    fun setPin(pin: String?) {
        if (pin == null) {
            prefs.edit().remove("pinHash").remove("pinSalt").apply()
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    // ---------- Storage ----------

    private fun store(list: List<Profile>) {
        profiles = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("color", p.color)
                put("kid", p.kid)
                put("locked", p.locked)
                put("hidden", JSONArray(p.hidden.toList()))
                put("shown", JSONArray(p.shown.toList()))
            })
        }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun load(): List<Profile> {
        val arr = runCatching { JSONArray(prefs.getString("profiles", "[]")) }.getOrElse { JSONArray() }
        fun JSONArray?.strings(): Set<String> = if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Profile(
                id = o.optString("id").ifEmpty { return@mapNotNull null },
                name = o.optString("name"),
                color = o.optInt("color"),
                kid = o.optBoolean("kid"),
                locked = o.optBoolean("locked"),
                hidden = o.optJSONArray("hidden").strings(),
                shown = o.optJSONArray("shown").strings(),
            )
        }
        // Before profiles existed (or on a fresh install): one main profile with everything that was already there.
        return if (list.none { it.id == Profile.MAIN }) listOf(Profile(Profile.MAIN, "", 0)) + list else list
    }
}

/**
 * What a kid profile may see. Categories that look like adult content are hidden unless the parent allowed them;
 * the parent can also hide any other category. Applies to every list, so also to search, favourites and zapping.
 */
object ParentalFilter {
    private val adult = Regex(
        """(^|[^a-z0-9])(adults?|xxx|18\s?\+|\+\s?18|porno?|erotic[a]?|erotik|erotiek|playboy|hustler|brazzers|for adults only)($|[^a-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    fun looksAdult(name: String): Boolean = adult.containsMatchIn(name)

    fun categoryKey(kind: Kind, categoryId: String) = "${kind.name}:$categoryId"

    fun isHidden(profile: Profile, kind: Kind, category: Category): Boolean {
        val key = categoryKey(kind, category.id)
        return key in profile.hidden || (looksAdult(category.name) && key !in profile.shown)
    }

    /** The library as [profile] may see it; unchanged for a grown-up profile. */
    fun apply(lib: Library, profile: Profile): Library {
        if (!profile.kid) return lib
        fun filterKind(kind: Kind, cats: List<Category>, items: List<MediaItem>): Pair<List<Category>, List<MediaItem>> {
            val hiddenIds = cats.filter { isHidden(profile, kind, it) }.map { it.id }.toHashSet()
            val keptItems = items.filter { it.categoryId !in hiddenIds && !looksAdult(it.name) }
            return cats.filterNot { it.id in hiddenIds } to keptItems
        }
        val (lc, l) = filterKind(Kind.LIVE, lib.liveCategories, lib.live)
        val (mc, m) = filterKind(Kind.MOVIE, lib.movieCategories, lib.movies)
        val (sc, s) = filterKind(Kind.SERIES, lib.seriesCategories, lib.series)
        return Library(lc, mc, sc, l, m, s, lib.updatedAt)
    }
}
