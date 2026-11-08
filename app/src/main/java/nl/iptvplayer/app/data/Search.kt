package nl.iptvplayer.app.data

import java.text.Normalizer
import java.util.Calendar

object TextMatch {
    private val marks = Regex("""\p{Mn}+""")
    private val nonAlnum = Regex("[^a-z0-9]+")

    // Separators that providers use: "|", but also "┃" (U+2503), "│" and "｜".
    private const val BAR = "[|┃│｜]"

    // Prefixes that providers put before titles: "NL - ", "EN: ", "|NL| ", "┃MULTI┃ ", "[4K] ".
    private val tagPrefix = Regex("""^\s*(\[[^\]]{1,20}]|$BAR[^|┃│｜]{1,20}$BAR|[A-Z]{2,4}\s*([-:]|$BAR))\s*""")
    private val yearSuffix = Regex("""\s*[(\[](19|20)\d{2}[)\]]\s*$""")
    private val yearAnywhere = Regex("""[(\[]((19|20)\d{2})[)\]]""")
    // Bare year at the end: "Wealthy Soul 2024", "Lonely - 2025". Only up to next year,
    // so "Blade Runner 2049" stays intact.
    private val bareYearSuffix = Regex("""\s+(-\s+)?((19|20)\d{2})\s*$""")
    private val maxYear = Calendar.getInstance().get(Calendar.YEAR) + 1

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(nonAlnum, " ")
            .trim()

    /** Title without language/quality labels and year, for exact matches when searching. */
    fun cleanTitle(name: String): String {
        var s = name
        repeat(3) { s = s.replaceFirst(tagPrefix, "") }
        return stripYear(s).ifEmpty { name }
    }

    // Stricter than tagPrefix: only clear labels ("NL - ", "NL | ", "┃NL┃", "[4K]"), so
    // titles like "CSI: Miami" or "300 - Rise of an Empire" stay intact.
    private val displayPrefix = Regex("""^\s*(\[[^\]]{1,20}]|$BAR[^|┃│｜]{1,20}$BAR|[A-Z][A-Z0-9]{1,3}([ -][A-Z0-9]{2,4})?\s*($BAR|\s[-–]))\s*""")
    private val trailingTag = Regex("""\s*\[[^\]]{1,20}]\s*$""")
    // Symbols and superscript letters at the end, like "⏺ʳᵉᶜ" (= catch-up available).
    private val trailingSymbols = Regex("""[\s\p{So}\p{Lm}]+$""")

    /** Title for display: without language labels, quality tags and year. */
    fun displayTitle(name: String): String {
        var s = name
        repeat(2) { s = s.replaceFirst(displayPrefix, "") }
        s = s.replace(trailingSymbols, "")
        repeat(2) { s = s.replace(trailingTag, "") }
        return stripYear(s).ifEmpty { name }
    }

    // "[NL] Gooische Vrouwen (2005) - S01E01 - Het Feest": remove everything up to and including "S01E01 -".
    private val episodePrefix = Regex("""^.*?\bS\d{1,2}\s?E\d{1,3}\b\s*[-–:|.]?\s*""", RegexOption.IGNORE_CASE)

    /** Clean episode title, e.g. "Het Feest" instead of "[NL] Gooische Vrouwen (2005) - S01E01 - Het Feest". */
    fun episodeTitle(raw: String): String {
        var stripped = raw.replaceFirst(episodePrefix, "").trim()
        // File name notation ("S01E01.The.Medieval.Tower"): dots are spaces. Not for "Mr.Robot" or "U.S.A.".
        if (stripped != raw.trim() && fileNameTitle.matches(stripped)) stripped = stripped.replace('.', ' ')
        return displayTitle(stripped.ifEmpty { raw })
    }

    private val fileNameTitle = Regex("""\w{2,}(\.\w{2,}){2,}""")

    private fun stripYear(s: String): String {
        val t = s.replace(yearSuffix, "").trim()
        val bare = bareYearSuffix.find(t) ?: return t
        val year = bare.groupValues[2].toInt()
        return if (year <= maxYear && bare.range.first > 0) t.substring(0, bare.range.first).trim() else t
    }

    /** Language label at the start, e.g. "NL" from "┃NL┃ Title" or "MULTI" from "┃MULTI / LINGO┃ KIDS". */
    fun languageTag(name: String): String? = languagePrefix.find(name)?.groupValues?.get(1)

    private val languagePrefix = Regex("""^\s*$BAR\s*([A-Z]{2,5})""")

    fun yearFrom(name: String): Int? {
        yearAnywhere.find(name)?.let { return it.groupValues[1].toIntOrNull() }
        val bare = bareYearSuffix.find(name.trim()) ?: return null
        return bare.groupValues[2].toInt().takeIf { it <= maxYear }
    }

    /** True if a and b differ by at most one edit (insert, delete, replace). */
    fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        val (s, l) = if (a.length <= b.length) a to b else b to a
        if (l.length - s.length > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < s.length && j < l.length) {
            if (s[i] == l[j]) {
                i++; j++
            } else {
                if (++edits > 1) return false
                if (s.length == l.length) i++
                j++
            }
        }
        return edits + (l.length - j) + (s.length - i) <= 1
    }

    /**
     * True if [text] without spaces contains [needle] ("spider man" contains "spiderman"). Without copying
     * [text], because this runs for every title on every key press. [needle] must not contain spaces.
     */
    fun containsIgnoringSpaces(text: String, needle: String): Boolean {
        if (needle.isEmpty()) return true
        for (start in text.indices) {
            if (text[start] != needle[0]) continue
            var i = start
            var j = 0
            while (i < text.length && j < needle.length) {
                val c = text[i++]
                if (c == ' ') continue
                if (c != needle[j++]) break
                if (j == needle.length) return true
            }
        }
        return false
    }
}

class SearchResults(
    val query: String,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val live: List<MediaItem>,
) {
    val isEmpty get() = movies.isEmpty() && series.isEmpty() && live.isEmpty()
}

object SearchEngine {
    private const val LIMIT = 150

    fun search(library: Library, rawQuery: String): SearchResults {
        val q = TextMatch.normalize(rawQuery)
        if (q.isEmpty()) return SearchResults(rawQuery, emptyList(), emptyList(), emptyList())
        val tokens = q.split(' ').filter { it.isNotEmpty() }
        val compact = q.replace(" ", "")
        return SearchResults(
            rawQuery,
            rank(library.movies, q, compact, tokens),
            rank(library.series, q, compact, tokens),
            rank(library.live, q, compact, tokens),
        )
    }

    private fun rank(items: List<MediaItem>, q: String, compact: String, tokens: List<String>): List<MediaItem> {
        var hits = items.mapNotNull { item -> score(item, q, compact, tokens).takeIf { it > 0 }?.let { item to it } }
        if (hits.isEmpty()) {
            // Nothing found: try with one typo per word.
            hits = items.mapNotNull { item -> fuzzyScore(item, tokens).takeIf { it > 0 }?.let { item to it } }
        }
        return hits
            .sortedWith(compareByDescending<Pair<MediaItem, Int>> { it.second }.thenByDescending { it.first.added })
            .take(LIMIT)
            .map { it.first }
    }

    private fun score(item: MediaItem, q: String, compact: String, tokens: List<String>): Int {
        val norm = item.norm
        // Quick preselection: almost all titles drop out here, without extra memory.
        if (!tokens.all { norm.contains(it) }) {
            // "spiderman" finds "Spider-Man"
            return if (q.length >= 4 && TextMatch.containsIgnoringSpaces(norm, compact)) 300 else 0
        }
        // Only for candidates: compute the cleaned title and words.
        val clean = TextMatch.normalize(TextMatch.cleanTitle(item.name))
        if (clean == q || norm == q) return 1000
        if (clean.startsWith(q)) return 800 - minOf(clean.length - q.length, 100)
        if (norm.startsWith(q)) return 700 - minOf(norm.length - q.length, 100)

        val words = words(norm)
        val allPrefix = tokens.all { t -> words.any { it.startsWith(t) } }
        if (allPrefix) {
            var s = 400 - minOf(norm.length, 100) / 2
            if (norm.contains(q)) s += 100
            return s
        }
        if (tokens.all { it.length >= 3 }) return 100
        return 0
    }

    private fun words(norm: String) = norm.split(' ').filter { it.isNotEmpty() }

    private fun fuzzyScore(item: MediaItem, tokens: List<String>): Int {
        val words = words(item.norm)
        val ok = tokens.all { t ->
            if (t.length < 4) words.any { it.startsWith(t) }
            else words.any { w ->
                TextMatch.withinOneEdit(t, w) ||
                    (w.length > t.length && TextMatch.withinOneEdit(t, w.substring(0, t.length)))
            }
        }
        return if (ok) 50 else 0
    }
}
