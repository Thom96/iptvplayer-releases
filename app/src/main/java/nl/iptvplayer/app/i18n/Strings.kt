package nl.iptvplayer.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Talen van de app. Een taal toevoegen:
 *  1. Kopieer `En.kt` naar bijv. `It.kt` (`object It : Strings()`) en vertaal alle teksten.
 *  2. Voeg hier één regel toe, met de naam van de taal in die taal zelf.
 * De compiler meldt het als er in een taal een tekst ontbreekt. De volgorde hier is de volgorde op het scherm.
 */
enum class AppLanguage(val code: String, val nativeName: String, val strings: Strings) {
    EN("en", "English", En),
    DE("de", "Deutsch", De),
    NL("nl", "Nederlands", Nl),
    ES("es", "Español", Es),
    FR("fr", "Français", Fr),
    ;

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        val DEFAULT = EN
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** Huidige taal. Compose-schermen tekenen zichzelf opnieuw als die verandert. */
object I18n {
    var language by mutableStateOf(AppLanguage.DEFAULT)
}

/** Teksten in de gekozen taal, bijv. `S.login`. */
val S: Strings get() = I18n.language.strings

/** Locale voor datums, getallen en taalnamen. */
val appLocale: Locale get() = I18n.language.locale

/**
 * Alle teksten van de interface. Elke taal is een `object` dat deze klasse invult.
 * Teksten met een getal of naam erin zijn functies.
 */
abstract class Strings {
    // Taalkeuze
    abstract val chooseLanguage: String
    abstract val chooseLanguageHint: String
    abstract val appLanguage: String
    abstract val appLanguageHint: String

    // Zijbalk
    abstract val tabSearch: String
    abstract val tabHome: String
    abstract val tabLive: String
    abstract val tabSeries: String
    abstract val tabMovies: String
    abstract val tabFavorites: String
    abstract val tabCatchup: String
    abstract val tabGuide: String
    abstract val tabSettings: String

    // Algemeen
    abstract val movie: String
    abstract val series: String
    abstract val channels: String
    abstract val play: String
    abstract val favorite: String
    abstract val inFavorites: String
    abstract val watched: String
    abstract val cancel: String
    abstract val remove: String
    abstract val removeQuestion: String
    abstract val removeHint: String
    abstract val retry: String
    abstract val later: String
    abstract val now: String
    abstract val loadingInfo: String
    abstract val noDescription: String
    abstract val continueWatching: String
    abstract val keepWatching: String
    abstract val recentlyWatched: String
    abstract fun nextUp(title: String): String
    /** "nog 20 min" midden in een zin. */
    abstract fun timeLeft(time: String): String
    abstract fun hoursMinutes(h: Int, m: Int): String
    abstract fun minutes(m: Int): String
    abstract fun seasons(n: Int): String
    abstract fun episodes(n: Int): String
    /** "1.234 films · 56 series · 789 zenders" */
    abstract fun libraryCounts(movies: String, series: String, channels: String): String

    // Inloggen en laden
    abstract val loginIntro: String
    abstract val serverPlaceholder: String
    abstract val username: String
    abstract val password: String
    abstract val loggingIn: String
    abstract val login: String
    abstract val firstLoadHint: String
    abstract val cannotConnect: String
    abstract val otherLogin: String
    abstract val connecting: String
    abstract val loading: String
    abstract val downloadingLists: String
    abstract val indexing: String

    // Foutmeldingen
    abstract val loginFailed: String
    abstract val serverNotFound: String
    abstract val serverTimeout: String
    abstract val serverUnreachable: String
    abstract val secureConnectionFailed: String
    abstract val unnamedCategory: String
    abstract val invalidServerUrl: String
    abstract val unexpectedResponse: String
    abstract val wrongCredentials: String
    abstract fun accountInactive(status: String): String
    abstract fun serverError(code: Int): String
    abstract val emptyResponse: String
    abstract fun seasonName(n: Int): String
    abstract fun episodeName(n: Int): String
    /** Kort: "S2 E4". */
    abstract fun seasonEpisode(season: Int, episode: Int): String
    /** Label op een afleveringskaart: "E4". */
    abstract fun episodeShort(n: Int): String
    abstract val unknownProgram: String
    abstract fun playbackFailed(reason: String): String

    // Home
    abstract val newSeries: String
    abstract val newMovies: String
    abstract val connected: String
    abstract val offline: String
    abstract fun subscriptionUntil(date: String): String
    abstract val noFavorites: String
    abstract val noFavoritesHint: String

    // Bladeren
    abstract val sortDefault: String
    abstract val sortNewest: String
    abstract val sortAz: String
    abstract val sortRating: String
    abstract val sortYear: String
    abstract val allChannels: String
    abstract val all: String
    abstract fun channelCount(n: String): String
    abstract fun titleCount(n: String): String
    abstract val nothingFound: String
    abstract val nothingFoundHint: String
    abstract val recentlyAdded: String
    abstract val seeAll: String
    /** Rijtitels per genre; sleutels zijn de Engelse genrenamen van de provider (zie CategoryRowsTab). */
    abstract val genres: Map<String, String>

    // Film en serie
    abstract fun director(names: String): String
    abstract fun cast(names: String): String
    abstract fun resumeAt(time: String): String
    abstract val fromStart: String
    abstract val seriesInfoFailed: String
    abstract val loadingEpisodes: String
    abstract fun resumeEpisode(season: Int, episode: Int): String
    abstract fun playEpisode(season: Int, episode: Int): String
    abstract fun resumeSeasonEpisode(season: Int, episode: Int): String
    abstract fun playSeasonEpisode(season: Int, episode: Int): String

    // Live TV
    abstract val channelUnavailable: String
    abstract val previewHint: String
    abstract val loadingGuide: String
    abstract val noGuide: String
    abstract fun laterAt(time: String, title: String): String
    abstract val zapHint: String

    // TV guide
    abstract val tomorrow: String
    abstract val guideWatchLive: String
    abstract val guideWatchArchive: String
    abstract fun guideStartsAt(time: String): String

    // Terugkijken
    abstract val today: String
    abstract val yesterday: String
    abstract val noCatchup: String
    abstract val noCatchupHint: String
    abstract fun daysBack(n: Int): String
    abstract val loadingArchive: String
    abstract val noArchive: String
    abstract fun until(time: String): String

    // Zoeken
    abstract val searchPlaceholder: String
    abstract val searchHint: String
    abstract val searching: String
    abstract fun nothingFoundFor(query: String): String

    // Updates
    abstract val newVersionAvailable: String
    abstract fun versionReady(version: String, current: String): String
    abstract fun installedVersion(current: String): String
    abstract val upToDate: String
    abstract fun checkFailed(message: String): String
    abstract fun newVersionNamed(version: String): String
    abstract val allowInstall: String
    abstract val checking: String
    abstract val updateNow: String
    abstract fun downloading(percent: Int): String
    abstract val checkForUpdates: String
    abstract fun updateServerError(code: Int): String
    abstract fun downloadFailed(code: Int): String
    abstract val emptyDownload: String

    // Instellingen
    abstract val contentLanguages: String
    abstract val contentLanguagesHint: String
    abstract val allLanguages: String
    abstract fun visible(counts: String): String
    abstract val playback: String
    abstract val playbackHint: String
    abstract val formatAuto: String
    abstract val autoNext: String
    abstract val autoNextHint: String
    abstract val autoNextOff: String
    abstract fun seconds(n: Int): String
    abstract val surroundSound: String
    abstract val surroundSoundHint: String
    abstract val surroundPassthrough: String
    abstract val surroundStereo: String
    abstract val timeshift: String
    abstract val timeshiftHint: String

    // Live pause and rewind
    abstract val backToLive: String
    abstract fun behindLive(time: String): String
    abstract val timeshiftKeys: String
    abstract val updates: String
    abstract val account: String
    abstract val server: String
    abstract val user: String
    abstract val status: String
    abstract val expiresOn: String
    abstract val unlimited: String
    abstract val connections: String
    abstract fun connectionsInUse(active: Int, max: Int): String
    abstract val library: String
    abstract val total: String
    abstract val updated: String
    abstract val refreshing: String
    abstract val refreshLists: String
    abstract val logout: String
    abstract val logoutQuestion: String
    abstract val logoutWarning: String
    abstract val settingsFooter: String

    // Speler
    abstract val back: String
    abstract val rewind10: String
    abstract val nextEpisode: String
    abstract val otherTracks: String
    abstract val playbackSpeed: String
    abstract val episodesAndMore: String
    abstract val audioAndSubtitles: String
    abstract val addToFavorites: String
    abstract val watchCredits: String
    abstract val skipIntro: String
    abstract val skipRecap: String
    abstract fun audioUnsupported(format: String): String
    abstract val pause: String
    abstract val audio: String
    abstract val subtitles: String
    abstract val defaultAudio: String
    abstract fun withSubtitles(audio: String, subtitle: String): String
    abstract val noChoice: String
    abstract val off: String
    abstract val normalSpeed: String

    // Activatie
    abstract val activateTitle: String
    abstract fun activateScan(url: String): String
    abstract val activateEnterCode: String
    abstract val activatePay: String
    abstract val deviceCode: String
    abstract fun trialDaysLeft(days: Int): String
    abstract val continueTrial: String
    abstract val checkStatus: String
    abstract val trialEnded: String
    abstract val subscriptionEnded: String
    abstract val deviceBlocked: String
    abstract val deviceBlockedHint: String
    abstract val waitingForPayment: String
    abstract val galloOffline: String
    abstract val galloOfflineHint: String
    abstract val licenseSection: String
    abstract val licenseTrial: String
    abstract val licenseActive: String
    abstract val licenseFree: String
    abstract val licenseExpired: String
    abstract val licenseBlocked: String
    abstract val validUntil: String
    abstract val noEndDate: String

    // Profiles and parental control
    abstract val whoIsWatching: String
    abstract val manageProfiles: String
    abstract val addProfile: String
    abstract val done: String
    abstract val profileDefaultName: String
    abstract val kidBadge: String
    abstract val editProfile: String
    abstract val newProfile: String
    abstract val profileNameHint: String
    abstract val profileColor: String
    abstract val kidProfile: String
    abstract val kidProfileHint: String
    abstract val lockProfile: String
    abstract val lockProfileHint: String
    abstract val allowedContent: String
    abstract val save: String
    abstract val deleteProfile: String
    abstract val deleteProfileQuestion: String
    abstract fun deleteProfileWarning(name: String): String
    abstract val enterPin: String
    abstract val newPin: String
    abstract val newPinKidHint: String
    abstract val confirmPin: String
    abstract val wrongPin: String
    abstract val pinsDontMatch: String
    abstract val forgotPin: String
    abstract val forgotPinHint: String
    abstract val wrongPassword: String
    abstract val next: String
    abstract val settingsLockedTitle: String
    abstract val settingsLockedHint: String
    abstract val unlock: String
    abstract val profilesSection: String
    abstract val profilesHint: String
    abstract val currentProfile: String
    abstract val switchProfile: String
    abstract val setPin: String
    abstract val changePin: String
    abstract val removePin: String
    abstract fun parentalTitle(name: String): String
    abstract val parentalHint: String
    abstract val visibleLabel: String
    abstract val hiddenLabel: String
    abstract val showAll: String
    abstract val hideAll: String
    abstract val noCategories: String
    // Phone login and TMDB
    abstract val phoneLoginTitle: String
    abstract val phoneLoginHint: String
    abstract val phonePageIntro: String
    abstract val phonePageSend: String
    abstract val phonePageSent: String
    abstract val phonePageExpired: String
    abstract val phonePageMissing: String
    abstract val tmdbSource: String
    abstract val tmdbNotice: String
}
