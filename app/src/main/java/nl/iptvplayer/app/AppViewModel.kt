package nl.iptvplayer.app

import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.mutableStateMapOf
import nl.iptvplayer.app.data.CatchupEntry
import nl.iptvplayer.app.data.ItemDetails
import nl.iptvplayer.app.data.UpdateInfo
import nl.iptvplayer.app.data.Updater
import nl.iptvplayer.app.data.EpgEntry
import nl.iptvplayer.app.data.AccountInfo
import nl.iptvplayer.app.data.AuthException
import nl.iptvplayer.app.data.Credentials
import nl.iptvplayer.app.data.Episode
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.LicenseClient
import nl.iptvplayer.app.data.LicenseStatus
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.ParentalFilter
import nl.iptvplayer.app.data.Profile
import nl.iptvplayer.app.data.Progress
import nl.iptvplayer.app.data.TextMatch
import nl.iptvplayer.app.data.XtreamClient
import nl.iptvplayer.app.player.PlayItem
import nl.iptvplayer.app.player.PlaybackQueue
import nl.iptvplayer.app.player.SeriesIntro
import nl.iptvplayer.app.player.PlayerActivity
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import java.net.UnknownHostException

sealed interface AppState {
    data class Login(val error: String? = null, val busy: Boolean = false) : AppState
    /**
     * [progress]: share of the loading steps that are done (0..1); drives the rooster on the loading screen.
     * [quick]: starting from the saved lists; then no grain game but the logo with a crowing rooster.
     * [finished]: everything is in; the screen waits a moment for the rooster to finish (see [AppViewModel.loadingAnimationDone]).
     */
    data class Loading(
        val message: String,
        val progress: Float = 0f,
        val quick: Boolean = false,
        val finished: Boolean = false,
    ) : AppState
    data class Failed(val message: String) : AppState
    data object Ready : AppState
}

/** See [AppViewModel.profileScreen]: choosing who watches, or adding and changing profiles. */
enum class ProfileMode { PICK, MANAGE }

/** See [AppViewModel.gate]. */
enum class Gate { NONE, TRIAL_NOTICE, LOCKED }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

sealed interface Screen {
    val key: String

    data object Home : Screen {
        override val key = "home"
    }

    data class Movie(val item: MediaItem) : Screen {
        override val key = "movie:${item.id}"
    }

    data class Series(val item: MediaItem) : Screen {
        override val key = "series:${item.id}"
    }

    /** Full category as a grid, opened via "View all". */
    data class Browse(val kind: Kind, val categoryId: String) : Screen {
        override val key = "browse:$kind:$categoryId"
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val storage get() = app.storage

    /** Only after the language choice on first start does the app go on to login. */
    var languageChosen by mutableStateOf(app.storage.appLanguage != null)
        private set

    fun setAppLanguage(language: AppLanguage) {
        storage.appLanguage = language.code
        I18n.language = language
        languageChosen = true
    }

    // ---------- Profiles and parental control ----------

    private val profileStore get() = app.profiles
    var profiles by mutableStateOf(app.profiles.profiles)
        private set
    var activeProfile by mutableStateOf(app.profiles.active)
        private set
    var hasPin by mutableStateOf(app.profiles.hasPin)
        private set
    /** All lists without the parental filter, to choose what a kid profile may see. */
    var fullLibrary by mutableStateOf<Library?>(null)
        private set
    /** Profile screen in front of the app: at every start once there are two or more profiles. */
    var profileScreen by mutableStateOf(if (app.profiles.profiles.size > 1) ProfileMode.PICK else null)
    /** Someone chose a profile since the app started (or there was nothing to choose). */
    var profileChosen by mutableStateOf(app.profiles.profiles.size <= 1)
        private set
    /** Settings were opened with the PIN; until another profile is chosen. */
    var settingsUnlocked by mutableStateOf(false)
    val settingsLocked: Boolean get() = hasPin && !settingsUnlocked

    /** A locked profile only needs the PIN as long as there is one. */
    fun needsPin(profile: Profile) = hasPin && (profile.locked && !profile.kid)

    fun selectProfile(profile: Profile) {
        profileScreen = null
        profileChosen = true
        if (profile.id == activeProfile.id) return
        profileStore.activeId = profile.id
        storage.useProfile(profile.id)
        AppLanguage.fromCode(storage.appLanguage)?.let { I18n.language = it }
        activeProfile = profile
        settingsUnlocked = false
        nav.clear()
        nav.add(Screen.Home)
        refilter()
    }

    /** Adds or changes a profile. The first kid profile locks the grown-up profiles, so a child can't pick them. */
    fun saveProfile(profile: Profile) {
        val firstKid = profile.kid && profiles.none { it.kid }
        profileStore.save(profile)
        if (firstKid) profileStore.profiles.filter { !it.kid }.forEach { profileStore.save(it.copy(locked = true)) }
        profiles = profileStore.profiles
        if (profile.id == activeProfile.id) {
            activeProfile = profileStore.active
            refilter()
        }
    }

    fun deleteProfile(id: String) {
        if (id == activeProfile.id) {
            val screen = profileScreen
            selectProfile(profiles.first { it.id == Profile.MAIN })
            profileScreen = screen
        }
        profileStore.delete(id)
        storage.deleteProfileData(id)
        profiles = profileStore.profiles
    }

    fun newProfileId() = profileStore.newId()

    fun checkPin(pin: String) = profileStore.checkPin(pin)

    /** null removes the PIN. */
    fun setPin(pin: String?) {
        profileStore.setPin(pin)
        hasPin = profileStore.hasPin
    }

    /** "Forgot PIN": the password of the subscription proves it's the owner. */
    fun checkAccountPassword(password: String) = storage.credentials?.password == password

    /** Name on screen: the first profile is called after the account until it gets its own name. */
    fun profileName(profile: Profile): String = profile.name.ifBlank {
        if (profile.id == Profile.MAIN) storage.lastUsername.ifBlank { S.profileDefaultName } else S.profileDefaultName
    }

    private fun refilter() {
        val full = fullLibrary ?: return
        viewModelScope.launch { setLibrary(full) }
    }

    var state by mutableStateOf<AppState>(AppState.Login())
        private set
    /** All lists from the server. */
    var library by mutableStateOf<Library?>(null)
        private set
    /** The lists filtered on the chosen languages; this is what the screens show. */
    var visibleLibrary by mutableStateOf<Library?>(null)
        private set
    var account by mutableStateOf<AccountInfo?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    var client: XtreamClient? = null
        private set

    val nav = mutableStateListOf<Screen>(Screen.Home)

    /** Live TV full screen: then Home hides the navigation rail. */
    var liveFullscreen by mutableStateOf(false)

    // ---------- Details for the expanded card ----------

    /** Fetched movie details (series already have their details in the list). */
    private val movieDetails = mutableStateMapOf<String, ItemDetails>()
    private val detailsLimiter = Semaphore(3)

    fun detailsFor(item: MediaItem): ItemDetails? = when (item.kind) {
        Kind.SERIES -> ItemDetails(item.plot, item.genre, item.backdrop, item.year, null, item.rating.takeIf { it > 0 })
        Kind.MOVIE -> movieDetails[item.key]
        else -> null
    }

    /** Fetches movie details (once per movie); not needed for series. */
    suspend fun loadDetails(item: MediaItem) {
        if (item.kind != Kind.MOVIE || item.key in movieDetails) return
        val c = client ?: return
        detailsLimiter.withPermit {
            if (item.key in movieDetails) return
            runCatching { c.movieInfo(item.id) }.onSuccess { info ->
                movieDetails[item.key] = ItemDetails(
                    plot = info.plot,
                    genre = info.genre,
                    backdrop = info.backdrop,
                    year = info.releaseDate?.take(4)?.toIntOrNull() ?: item.year,
                    durationSecs = info.durationSecs,
                    rating = info.rating ?: item.rating.takeIf { it > 0 },
                )
            }
        }
    }

    // ---------- Updates ----------

    val updater = Updater(app, app.http)
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    /** Notice at startup; "Later" hides it until the next start. */
    var showUpdatePrompt by mutableStateOf(false)

    /** [manual] = via the button in Settings; then we also show "you have the latest version" and errors. */
    fun checkForUpdates(manual: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        // Don't overwrite an update that was found: then "Update" simply stays.
        if (!manual && (update is UpdateState.Available || update is UpdateState.NeedsPermission)) return
        lastUpdateCheck = System.currentTimeMillis()
        update = UpdateState.Checking
        viewModelScope.launch {
            update = try {
                val info = updater.check()
                if (info != null) {
                    if (!manual) showUpdatePrompt = true
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (manual) UpdateState.Failed(describe(e)) else UpdateState.Idle
            }
        }
    }

    fun installUpdate() {
        val info = (update as? UpdateState.Available)?.info ?: return
        if (!updater.canInstall()) {
            // One time: allow "Install unknown apps" for IPTV Player.
            update = UpdateState.NeedsPermission(info)
            updater.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch {
            update = UpdateState.Downloading(info, 0f)
            update = try {
                val apk = updater.download(info) { p -> update = UpdateState.Downloading(info, p) }
                updater.install(apk)
                UpdateState.Available(info)
            } catch (e: Exception) {
                UpdateState.Failed(describe(e))
            }
        }
    }

    /** Try again after returning from the permission screen. */
    fun retryAfterPermission() {
        val s = update as? UpdateState.NeedsPermission ?: return
        update = UpdateState.Available(s.info)
        if (updater.canInstall()) installUpdate()
    }

    // ---------- Catch-up ----------

    /** Archive guide per channel id; null in the map = not loaded yet. */
    val archive = mutableStateMapOf<Int, List<CatchupEntry>>()
    private val archiveFetchedAt = HashMap<Int, Long>()

    suspend fun loadArchive(streamId: Int) {
        val c = client ?: return
        val last = archiveFetchedAt[streamId]
        if (last != null && System.currentTimeMillis() - last < 10 * 60_000) return
        archiveFetchedAt[streamId] = System.currentTimeMillis()
        archive[streamId] = runCatching { c.archiveEpg(streamId) }.getOrElse {
            archiveFetchedAt.remove(streamId) // network error: try again next time
            emptyList()
        }
    }

    fun playCatchup(context: Context, channel: MediaItem, entry: CatchupEntry) {
        val c = client ?: return
        val day = java.text.SimpleDateFormat("EEE d MMM HH:mm", appLocale).format(java.util.Date(entry.start))
        val item = PlayItem(
            key = "CATCHUP:${channel.id}:${entry.start}", kind = Kind.CATCHUP,
            url = c.catchupUrl(channel.id, entry), title = entry.title,
            subtitle = "${channel.title} · $day", image = channel.image,
        )
        startPlayer(context, PlaybackQueue(listOf(item)))
    }

    // ---------- TV guide ----------

    /** Guide per channel id; filled by [loadEpg]. */
    val epg = mutableStateMapOf<Int, List<EpgEntry>>()
    private val epgFetchedAt = HashMap<Int, Long>()
    private val epgLimiter = Semaphore(4)

    /** Fetches the guide of a channel (at most once per 15 minutes, max. 4 at a time). */
    suspend fun loadEpg(streamId: Int) {
        val c = client ?: return
        val last = epgFetchedAt[streamId]
        if (last != null && System.currentTimeMillis() - last < 15 * 60_000) return
        epgLimiter.withPermit {
            epgFetchedAt[streamId] = System.currentTimeMillis()
            runCatching { c.shortEpg(streamId) }
                .onSuccess { epg[streamId] = it }
                // Network error: try again after a minute instead of only after a quarter of an hour.
                .onFailure { epgFetchedAt[streamId] = System.currentTimeMillis() - 14 * 60_000 }
        }
    }

    // ---------- TV guide screen ----------

    /** Guide per channel id for the TV guide screen; only the most recently viewed channels are kept. */
    val guide = mutableStateMapOf<Int, List<CatchupEntry>>()
    private val guideFetchedAt = LinkedHashMap<Int, Long>(16, 0.75f, true)
    private val guideLimiter = Semaphore(4)

    /** Fetches the guide of a channel for the guide screen (at most once per 30 minutes, max. 4 at a time). */
    suspend fun loadGuide(streamId: Int) {
        val c = client ?: return
        val last = guideFetchedAt[streamId]
        if (last != null && System.currentTimeMillis() - last < 30 * 60_000) return
        guideLimiter.withPermit {
            guideFetchedAt[streamId] = System.currentTimeMillis()
            runCatching { c.guideEpg(streamId) }
                .onSuccess { guide[streamId] = it }
                // Network error: try again after a minute.
                .onFailure { guideFetchedAt[streamId] = System.currentTimeMillis() - 29 * 60_000 }
            // Large lists: keep memory small by forgetting the channels that were viewed longest ago.
            while (guideFetchedAt.size > GUIDE_CACHE_SIZE) {
                val oldest = guideFetchedAt.keys.first()
                guideFetchedAt.remove(oldest)
                guide.remove(oldest)
            }
        }
    }

    // ---------- Activation (Gallo server) ----------

    private val licenseClient = LicenseClient(app.http)
    /** Last known status of this device; null = never had contact. */
    var license by mutableStateOf(storage.license)
        private set
    var licenseChecking by mutableStateOf(false)
        private set
    /** The last check failed (no internet or server unreachable). */
    var licenseError by mutableStateOf(false)
        private set
    /** The trial screen has already been dismissed during this start. */
    var trialNoticeSeen by mutableStateOf(false)
    private var lastLicenseCheck = 0L

    val isDemo: Boolean get() = client?.creds?.server == XtreamClient.DEMO_SERVER

    /** What must be shown in front of the app: nothing, the trial screen, or the activation screen that can't be closed. */
    val gate: Gate
        get() {
            if (isDemo) return Gate.NONE
            val now = System.currentTimeMillis()
            val l = license ?: return Gate.LOCKED
            if (!l.allowsUse(now) || !l.isFresh(now)) return Gate.LOCKED
            if (l.status == LicenseStatus.TRIAL && !trialNoticeSeen) return Gate.TRIAL_NOTICE
            return Gate.NONE
        }

    fun checkLicense() {
        if (licenseChecking) return
        licenseChecking = true
        lastLicenseCheck = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val l = licenseClient.check(storage.deviceId, BuildConfig.VERSION_NAME, android.os.Build.MODEL ?: "")
                storage.license = l
                license = l
                licenseError = false
            } catch (_: Exception) {
                licenseError = true
            } finally {
                licenseChecking = false
            }
        }
    }

    init {
        checkLicense()
        storage.credentials?.let { connect(it) }
        // At startup, wait a bit and then check in the background whether there is an update.
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_000)
            checkForUpdates(manual = false)
        }
    }

    // ---------- Login ----------

    fun login(server: String, username: String, password: String) {
        val creds = Credentials(XtreamClient.normalizeServer(server), username.trim(), password)
        state = AppState.Login(busy = true)
        viewModelScope.launch {
            try {
                val c = XtreamClient(creds, app.http, app.cacheDir)
                account = c.authenticate()
                storage.serverLiveFormat = c.liveFormat
                storage.credentials = creds
                client = c
                loadLibrary(c, forceDownload = true)
            } catch (e: Exception) {
                state = AppState.Login(error = describe(e))
            }
        }
    }

    /** Starting with saved credentials: show the cache first, then refresh in the background. */
    private fun connect(creds: Credentials) {
        viewModelScope.launch {
            try {
                val c = XtreamClient(creds, app.http, app.cacheDir)
                storage.serverLiveFormat?.let { c.liveFormat = it }
                client = c
                if (c.hasCache) {
                    loadLibrary(c, forceDownload = false)
                    runCatching { account = c.authenticate(); storage.serverLiveFormat = c.liveFormat }
                    if (c.cacheAgeMs > CACHE_MAX_AGE_MS) refreshInBackground()
                } else {
                    state = AppState.Loading(S.connecting)
                    account = c.authenticate()
                    storage.serverLiveFormat = c.liveFormat
                    loadLibrary(c, forceDownload = true)
                }
            } catch (e: AuthException) {
                state = AppState.Login(error = describe(e))
            } catch (e: Exception) {
                state = AppState.Failed(describe(e))
            }
        }
    }

    /** Completed when the rooster on the loading screen has had its moment. */
    private var roosterDone = CompletableDeferred<Unit>()

    /** The loading screen reports that the rooster is done (everything eaten and crowed). */
    fun loadingAnimationDone() {
        roosterDone.complete(Unit)
    }

    private suspend fun loadLibrary(c: XtreamClient, forceDownload: Boolean) {
        val quick = !forceDownload && c.hasCache
        roosterDone = CompletableDeferred()
        state = AppState.Loading(S.loading, quick = quick)
        setLibrary(
            c.loadLibrary(forceDownload) { msg, fraction ->
                // From background threads; progress only goes up, even when steps finish at the same time.
                synchronized(this) {
                    val shown = (state as? AppState.Loading)?.progress ?: 0f
                    state = AppState.Loading(msg, maxOf(shown, fraction), quick)
                }
            },
        )
        // Let the rooster finish its loading screen, but never keep the user waiting long.
        state = AppState.Loading(S.loading, 1f, quick, finished = true)
        withTimeoutOrNull(if (quick) QUICK_FINISH_MAX_MS else FULL_FINISH_MAX_MS) { roosterDone.await() }
        state = AppState.Ready
    }

    private suspend fun setLibrary(full: Library) {
        filterJob?.cancel() // a language filter on the old lists must not overwrite the new ones
        // Prepare the filtered library and the lists for Home in the background already.
        val profile = activeProfile
        val lib = withContext(Dispatchers.Default) { ParentalFilter.apply(full, profile) }
        val visible = withContext(Dispatchers.Default) {
            lib.filtered(storage.languages.value).also { it.newestMovies; it.newestSeries }
        }
        fullLibrary = full
        library = lib
        visibleLibrary = visible
        viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(5_000) // first let the screen build up calmly
            lib.warmUp()
        }
    }

    fun toggleLanguage(tag: String) {
        storage.toggleLanguage(tag)
        applyLanguageFilter()
    }

    /** "All languages": clear the filter in one go (not language by language, that computes everything several times). */
    fun showAllLanguages() {
        storage.setLanguages(emptySet())
        applyLanguageFilter()
    }

    private var filterJob: kotlinx.coroutines.Job? = null

    /** Recomputes the visible library; a new choice cancels a computation that is still running. */
    private fun applyLanguageFilter() {
        val lib = library ?: return
        val languages = storage.languages.value
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            visibleLibrary = withContext(Dispatchers.Default) {
                lib.filtered(languages).also { it.newestMovies; it.newestSeries }
            }
        }
    }

    // ---------- Coming back to the app ----------

    private var lastUpdateCheck = 0L

    /**
     * On a TV the app often stays in memory for days (the Home button doesn't close it). So on return:
     * refresh old lists in the background and check for updates now and then.
     */
    fun onAppResumed() {
        if (System.currentTimeMillis() - lastLicenseCheck > LICENSE_CHECK_INTERVAL_MS) checkLicense()
        val c = client
        if (state == AppState.Ready && c != null && c.cacheAgeMs > CACHE_MAX_AGE_MS) refreshInBackground()
        if (lastUpdateCheck != 0L && System.currentTimeMillis() - lastUpdateCheck > UPDATE_CHECK_INTERVAL_MS) {
            checkForUpdates(manual = false)
        }
    }

    fun retry() {
        storage.credentials?.let { connect(it) } ?: run { state = AppState.Login() }
    }

    fun refreshInBackground() {
        val c = client ?: return
        if (refreshing) return
        refreshing = true
        refreshJob = viewModelScope.launch {
            try {
                val acc = c.authenticate()
                storage.serverLiveFormat = c.liveFormat
                val lib = c.loadLibrary(forceDownload = true)
                if (client !== c) return@launch // logged out or switched account in the meantime
                account = acc
                setLibrary(lib)
            } catch (_: Exception) {
                // The old lists simply stay.
            } finally {
                refreshing = false
            }
        }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    fun logout() {
        refreshJob?.cancel()
        client?.clearCache()
        client = null
        storage.credentials = null
        library = null
        fullLibrary = null
        visibleLibrary = null
        account = null
        nav.clear()
        nav.add(Screen.Home)
        state = AppState.Login()
    }

    // ---------- Navigation ----------

    fun open(item: MediaItem, context: Context) {
        when (item.kind) {
            Kind.MOVIE -> nav.add(Screen.Movie(item))
            Kind.SERIES -> openSeries(context, item)
            Kind.LIVE -> playLive(context, listOf(item), 0)
            Kind.EPISODE, Kind.CATCHUP -> Unit
        }
    }

    fun back(): Screen? = if (nav.size > 1) nav.removeAt(nav.lastIndex) else null

    // ---------- Playback ----------

    /** Format for live streams: the choice from Settings, or with "auto" what the server recommends. */
    val liveFormat: String
        get() = storage.liveFormat.value.takeIf { it != "auto" } ?: client?.liveFormat ?: "ts"

    /** Live URL in the chosen format; [alternative] = the other format, as a fallback. */
    fun liveUrl(streamId: Int, alternative: Boolean = false): String {
        val c = client ?: return ""
        val format = if (alternative) (if (liveFormat == "ts") "m3u8" else "ts") else liveFormat
        return c.liveUrl(streamId, format)
    }

    fun playLive(context: Context, channels: List<MediaItem>, index: Int) {
        if (client == null) return
        val items = channels.map { ch ->
            PlayItem(
                key = ch.key, kind = Kind.LIVE, url = liveUrl(ch.id), title = ch.title, image = ch.image,
                fallbackUrl = liveUrl(ch.id, alternative = true),
            )
        }
        startPlayer(context, PlaybackQueue(items, index))
    }

    fun playMovie(context: Context, item: MediaItem, ext: String?, fromStart: Boolean) {
        val c = client ?: return
        val resume = if (fromStart) 0L else storage.progressFor(item.key)?.positionMs ?: 0L
        val play = PlayItem(
            key = item.key, kind = Kind.MOVIE, url = c.movieUrl(item.id, ext ?: item.ext),
            title = item.title, image = item.image, ext = ext ?: item.ext,
        )
        startPlayer(context, PlaybackQueue(listOf(play), 0, resume))
    }

    /** Plays [episodes] from [index], so the next episode starts automatically. */
    fun playEpisodes(
        context: Context,
        series: MediaItem,
        episodes: List<Episode>,
        index: Int,
        fromStart: Boolean = false,
        intro: SeriesIntro? = null,
    ) {
        val c = client ?: return
        val items = episodes.map { e ->
            PlayItem(
                key = e.key, kind = Kind.EPISODE, url = c.episodeUrl(e.id, e.ext),
                title = series.title, subtitle = "S${e.season} E${e.number} · ${TextMatch.episodeTitle(e.title)}",
                image = e.image ?: series.image, ext = e.ext, seriesId = series.id,
                tmdbId = e.seriesTmdbId, season = e.season, episode = e.number,
            )
        }
        val resume = if (fromStart) 0L else storage.progressFor(episodes[index].key)?.positionMs ?: 0L
        startPlayer(context, PlaybackQueue(items, index, resume, intro))
    }

    /** Which episode is next: a half-watched one, otherwise the first after the last one watched. */
    fun nextUpFor(seriesId: Int, episodes: List<Episode>): Pair<Episode, Progress?>? {
        if (episodes.isEmpty()) return null
        val p = storage.progress.value.firstOrNull { it.seriesId == seriesId }
        val resumeEp = p?.let { pp -> episodes.firstOrNull { it.key == pp.key } }
        if (resumeEp != null) return resumeEp to p
        val last = episodes.indexOfLast { it.key in storage.watched.value }
        return (episodes.getOrNull(last + 1) ?: episodes.first()) to null
    }

    /** Fetching episodes for [openSeries] or [resume]; while that runs, a second press doesn't count. */
    private var openingJob: kotlinx.coroutines.Job? = null

    /**
     * Open a series like Netflix: the next episode starts right away, with a panel on the left with info
     * and "Episodes and more". If fetching fails, the regular series page.
     */
    fun openSeries(context: Context, series: MediaItem) {
        val c = client ?: return
        if (openingJob?.isActive == true) return // still fetching: ignore a second press on OK
        openingJob = viewModelScope.launch {
            val info = runCatching { c.seriesInfo(series.id) }.getOrNull()
            val episodes = info?.seasons?.flatMap { it.episodes }.orEmpty()
            val next = nextUpFor(series.id, episodes)
            if (info == null || next == null) {
                nav.add(Screen.Series(series))
                return@launch
            }
            val (ep, progress) = next
            val label = if (progress != null) {
                S.resumeSeasonEpisode(ep.season, ep.number)
            } else {
                S.playSeasonEpisode(ep.season, ep.number)
            }
            val meta = listOfNotNull(
                info.releaseDate?.take(4) ?: series.year?.toString(),
                info.genre?.split(',')?.firstOrNull()?.trim(),
                if (info.seasons.size == 1) S.episodes(episodes.size) else S.seasons(info.seasons.size),
                (info.rating ?: series.rating.takeIf { it > 0 })?.let { "★ %.1f".format(it) },
            ).joinToString("  •  ")
            val intro = SeriesIntro(
                seriesKey = series.key,
                title = series.title,
                meta = meta,
                plot = info.plot ?: series.plot,
                cast = info.cast?.takeIf { it.isNotBlank() },
                playLabel = label,
            )
            playEpisodes(context, series, episodes, episodes.indexOf(ep), intro = intro)
        }
    }

    /**
     * Resumes from "continue watching". For a series we first fetch all episodes, so that
     * the next episode starts automatically afterwards; if that fails, only this episode.
     */
    fun resume(context: Context, p: Progress) {
        val c = client ?: return
        val seriesId = p.seriesId
        if (seriesId != null) {
            if (openingJob?.isActive == true) return
            openingJob = viewModelScope.launch {
                val episodes = runCatching { c.seriesInfo(seriesId) }.getOrNull()
                    ?.seasons?.flatMap { it.episodes }.orEmpty()
                val index = episodes.indexOfFirst { it.key == p.key }
                val series = library?.byKey?.get(MediaItem.keyOf(Kind.SERIES, seriesId))
                if (index >= 0 && series != null) {
                    playEpisodes(context, series, episodes, index)
                } else {
                    resumeSingle(context, c, p)
                }
            }
            return
        }
        resumeSingle(context, c, p)
    }

    private fun resumeSingle(context: Context, c: XtreamClient, p: Progress) {
        val (kindName, idText) = p.key.split(':').let { it[0] to it.getOrNull(1) }
        val id = idText?.toIntOrNull() ?: return
        val kind = Kind.valueOf(kindName)
        val url = if (kind == Kind.EPISODE) c.episodeUrl(id, p.ext) else c.movieUrl(id, p.ext)
        val play = PlayItem(p.key, kind, url, p.title, p.subtitle, p.image, p.ext, p.seriesId)
        startPlayer(context, PlaybackQueue(listOf(play), 0, p.positionMs))
    }

    private fun startPlayer(context: Context, queue: PlaybackQueue) {
        app.pendingQueue = queue
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    private fun describe(e: Exception): String = when (e) {
        is AuthException -> e.message ?: S.loginFailed
        is UnknownHostException -> S.serverNotFound
        is SocketTimeoutException -> S.serverTimeout
        is ConnectException, is NoRouteToHostException -> S.serverUnreachable
        is SSLException -> S.secureConnectionFailed
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        /** Number of channels whose guide the TV guide screen keeps in memory. */
        const val GUIDE_CACHE_SIZE = 150
        const val CACHE_MAX_AGE_MS = 12 * 60 * 60 * 1000L
        /** Check for updates at most this often when coming back to the app. */
        const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        /** How often the app (if it stays open for a long time) checks with the Gallo server again. */
        const val LICENSE_CHECK_INTERVAL_MS = 60 * 60 * 1000L
        /** Quick start: wait at most this long for the crow. */
        const val QUICK_FINISH_MAX_MS = 1_200L
        /** After downloading: wait at most this long for the rooster to eat the last grains and crow. */
        const val FULL_FINISH_MAX_MS = 2_500L
    }
}
