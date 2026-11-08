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
     * [progress]: deel van de laadstappen dat klaar is (0..1); stuurt de haan op het laadscherm.
     * [quick]: opstarten uit de bewaarde lijsten; dan geen korrelspel maar het logo met een kraaiende haan.
     * [finished]: alles is binnen; het scherm wacht nog even tot de haan klaar is (zie [AppViewModel.loadingAnimationDone]).
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

/** Zie [AppViewModel.gate]. */
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

    /** Volledige categorie als raster, geopend via "Alles bekijken". */
    data class Browse(val kind: Kind, val categoryId: String) : Screen {
        override val key = "browse:$kind:$categoryId"
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val storage get() = app.storage

    /** Pas na de taalkeuze bij de eerste start gaat de app verder naar inloggen. */
    var languageChosen by mutableStateOf(app.storage.appLanguage != null)
        private set

    fun setAppLanguage(language: AppLanguage) {
        storage.appLanguage = language.code
        I18n.language = language
        languageChosen = true
    }

    var state by mutableStateOf<AppState>(AppState.Login())
        private set
    /** Alle lijsten van de server. */
    var library by mutableStateOf<Library?>(null)
        private set
    /** De lijsten gefilterd op de gekozen talen; dit is wat de schermen tonen. */
    var visibleLibrary by mutableStateOf<Library?>(null)
        private set
    var account by mutableStateOf<AccountInfo?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    var client: XtreamClient? = null
        private set

    val nav = mutableStateListOf<Screen>(Screen.Home)

    /** Live TV op volledig scherm: dan verbergt Home de navigatiebalk. */
    var liveFullscreen by mutableStateOf(false)

    // ---------- Details voor de uitgeklapte kaart ----------

    /** Opgehaalde filmdetails (series hebben hun details al in de lijst). */
    private val movieDetails = mutableStateMapOf<String, ItemDetails>()
    private val detailsLimiter = Semaphore(3)

    fun detailsFor(item: MediaItem): ItemDetails? = when (item.kind) {
        Kind.SERIES -> ItemDetails(item.plot, item.genre, item.backdrop, item.year, null, item.rating.takeIf { it > 0 })
        Kind.MOVIE -> movieDetails[item.key]
        else -> null
    }

    /** Haalt filmdetails op (één keer per film); voor series is dat niet nodig. */
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
    /** Melding bij het opstarten; "Later" verbergt hem tot de volgende start. */
    var showUpdatePrompt by mutableStateOf(false)

    /** [manual] = via de knop in Instellingen; dan tonen we ook "je hebt de nieuwste versie" en fouten. */
    fun checkForUpdates(manual: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        // Een gevonden update niet overschrijven: dan blijft "Bijwerken" gewoon staan.
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
            // Eenmalig: "Installeren van onbekende apps" toestaan voor IPTV Player.
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

    /** Na terugkeer uit het toestemmingsscherm opnieuw proberen. */
    fun retryAfterPermission() {
        val s = update as? UpdateState.NeedsPermission ?: return
        update = UpdateState.Available(s.info)
        if (updater.canInstall()) installUpdate()
    }

    // ---------- Terugkijken ----------

    /** Archiefgids per zender-id; null in de map = nog niet geladen. */
    val archive = mutableStateMapOf<Int, List<CatchupEntry>>()
    private val archiveFetchedAt = HashMap<Int, Long>()

    suspend fun loadArchive(streamId: Int) {
        val c = client ?: return
        val last = archiveFetchedAt[streamId]
        if (last != null && System.currentTimeMillis() - last < 10 * 60_000) return
        archiveFetchedAt[streamId] = System.currentTimeMillis()
        archive[streamId] = runCatching { c.archiveEpg(streamId) }.getOrElse {
            archiveFetchedAt.remove(streamId) // netwerkfout: bij de volgende keer opnieuw proberen
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

    // ---------- Tv-gids ----------

    /** Gids per zender-id; wordt gevuld door [loadEpg]. */
    val epg = mutableStateMapOf<Int, List<EpgEntry>>()
    private val epgFetchedAt = HashMap<Int, Long>()
    private val epgLimiter = Semaphore(4)

    /** Haalt de gids van een zender op (hooguit eens per 15 minuten, max. 4 tegelijk). */
    suspend fun loadEpg(streamId: Int) {
        val c = client ?: return
        val last = epgFetchedAt[streamId]
        if (last != null && System.currentTimeMillis() - last < 15 * 60_000) return
        epgLimiter.withPermit {
            epgFetchedAt[streamId] = System.currentTimeMillis()
            runCatching { c.shortEpg(streamId) }
                .onSuccess { epg[streamId] = it }
                // Netwerkfout: na een minuut opnieuw proberen in plaats van pas na een kwartier.
                .onFailure { epgFetchedAt[streamId] = System.currentTimeMillis() - 14 * 60_000 }
        }
    }

    // ---------- Activatie (Gallo-server) ----------

    private val licenseClient = LicenseClient(app.http)
    /** Laatst bekende status van dit apparaat; null = nog nooit contact gehad. */
    var license by mutableStateOf(storage.license)
        private set
    var licenseChecking by mutableStateOf(false)
        private set
    /** De laatste controle mislukte (geen internet of server onbereikbaar). */
    var licenseError by mutableStateOf(false)
        private set
    /** Het proefscherm is deze start al weggeklikt. */
    var trialNoticeSeen by mutableStateOf(false)
    private var lastLicenseCheck = 0L

    val isDemo: Boolean get() = client?.creds?.server == XtreamClient.DEMO_SERVER

    /** Wat er voor de app moet staan: niets, het proefscherm, of het activatiescherm dat niet weg kan. */
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
        // Bij het opstarten even rustig wachten en dan op de achtergrond kijken of er een update is.
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_000)
            checkForUpdates(manual = false)
        }
    }

    // ---------- Inloggen ----------

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

    /** Opstarten met opgeslagen gegevens: eerst de cache tonen, daarna op de achtergrond verversen. */
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

    /** Wordt voltooid als de haan op het laadscherm zijn moment heeft gehad. */
    private var haanDone = CompletableDeferred<Unit>()

    /** Het laadscherm meldt dat de haan klaar is (alles opgegeten en gekraaid). */
    fun loadingAnimationDone() {
        haanDone.complete(Unit)
    }

    private suspend fun loadLibrary(c: XtreamClient, forceDownload: Boolean) {
        val quick = !forceDownload && c.hasCache
        haanDone = CompletableDeferred()
        state = AppState.Loading(S.loading, quick = quick)
        setLibrary(
            c.loadLibrary(forceDownload) { msg, fraction ->
                // Vanaf achtergronddraden; de voortgang loopt alleen op, ook als stappen gelijktijdig klaar zijn.
                synchronized(this) {
                    val shown = (state as? AppState.Loading)?.progress ?: 0f
                    state = AppState.Loading(msg, maxOf(shown, fraction), quick)
                }
            },
        )
        // De haan zijn laadscherm laten afmaken, maar nooit langer dan even laten wachten.
        state = AppState.Loading(S.loading, 1f, quick, finished = true)
        withTimeoutOrNull(if (quick) QUICK_FINISH_MAX_MS else FULL_FINISH_MAX_MS) { haanDone.await() }
        state = AppState.Ready
    }

    private suspend fun setLibrary(lib: Library) {
        filterJob?.cancel() // een taalfilter op de oude lijsten mag de nieuwe niet overschrijven
        // Gefilterde bibliotheek en de lijsten voor Home al op de achtergrond klaarzetten.
        val visible = withContext(Dispatchers.Default) {
            lib.filtered(storage.languages.value).also { it.newestMovies; it.newestSeries }
        }
        library = lib
        visibleLibrary = visible
        viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(5_000) // eerst het scherm rustig laten opbouwen
            lib.warmUp()
        }
    }

    fun toggleLanguage(tag: String) {
        storage.toggleLanguage(tag)
        applyLanguageFilter()
    }

    /** "Alle talen": het filter in één keer leegmaken (niet taal voor taal, dat rekent alles meerdere keren uit). */
    fun showAllLanguages() {
        storage.setLanguages(emptySet())
        applyLanguageFilter()
    }

    private var filterJob: kotlinx.coroutines.Job? = null

    /** Rekent de zichtbare bibliotheek opnieuw uit; een nieuwe keuze annuleert een berekening die nog loopt. */
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

    // ---------- Terugkomen in de app ----------

    private var lastUpdateCheck = 0L

    /**
     * De app blijft op een tv vaak dagen in het geheugen (Home-knop sluit hem niet af). Daarom bij terugkomen:
     * oude lijsten op de achtergrond verversen en af en toe op updates controleren.
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
                if (client !== c) return@launch // intussen uitgelogd of ander account
                account = acc
                setLibrary(lib)
            } catch (_: Exception) {
                // Oude lijsten blijven gewoon staan.
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
        visibleLibrary = null
        account = null
        nav.clear()
        nav.add(Screen.Home)
        state = AppState.Login()
    }

    // ---------- Navigatie ----------

    fun open(item: MediaItem, context: Context) {
        when (item.kind) {
            Kind.MOVIE -> nav.add(Screen.Movie(item))
            Kind.SERIES -> openSeries(context, item)
            Kind.LIVE -> playLive(context, listOf(item), 0)
            Kind.EPISODE, Kind.CATCHUP -> Unit
        }
    }

    fun back(): Screen? = if (nav.size > 1) nav.removeAt(nav.lastIndex) else null

    // ---------- Afspelen ----------

    /** Formaat voor live-streams: de keuze uit Instellingen, of bij "auto" wat de server aanbeveelt. */
    val liveFormat: String
        get() = storage.liveFormat.value.takeIf { it != "auto" } ?: client?.liveFormat ?: "ts"

    /** Live-URL in het gekozen formaat; [alternative] = het andere formaat, als terugval. */
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

    /** Speelt [episodes] af vanaf [index], zodat de volgende aflevering automatisch start. */
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
            )
        }
        val resume = if (fromStart) 0L else storage.progressFor(episodes[index].key)?.positionMs ?: 0L
        startPlayer(context, PlaybackQueue(items, index, resume, intro))
    }

    /** Welke aflevering is aan de beurt: een half bekeken, anders de eerste na de laatst geziene. */
    fun nextUpFor(seriesId: Int, episodes: List<Episode>): Pair<Episode, Progress?>? {
        if (episodes.isEmpty()) return null
        val p = storage.progress.value.firstOrNull { it.seriesId == seriesId }
        val resumeEp = p?.let { pp -> episodes.firstOrNull { it.key == pp.key } }
        if (resumeEp != null) return resumeEp to p
        val last = episodes.indexOfLast { it.key in storage.watched.value }
        return (episodes.getOrNull(last + 1) ?: episodes.first()) to null
    }

    /** Ophalen van afleveringen voor [openSeries] of [resume]; zolang dat loopt, telt een tweede druk niet. */
    private var openingJob: kotlinx.coroutines.Job? = null

    /**
     * Serie openen zoals Netflix: de volgende aflevering start direct, met links een paneel met info
     * en "Afleveringen en meer". Lukt het ophalen niet, dan de gewone seriepagina.
     */
    fun openSeries(context: Context, series: MediaItem) {
        val c = client ?: return
        if (openingJob?.isActive == true) return // nog bezig met ophalen: tweede druk op OK negeren
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
     * Hervat vanuit "verder kijken". Bij een serie halen we eerst alle afleveringen op, zodat
     * daarna automatisch de volgende aflevering start; lukt dat niet, dan alleen deze aflevering.
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
        const val CACHE_MAX_AGE_MS = 12 * 60 * 60 * 1000L
        /** Bij terugkomen in de app hooguit zo vaak op updates controleren. */
        const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        /** Hoe vaak de app (als hij lang open blijft) opnieuw bij de Gallo-server navraagt. */
        const val LICENSE_CHECK_INTERVAL_MS = 60 * 60 * 1000L
        /** Snelle start: hooguit zo lang op de kraai wachten. */
        const val QUICK_FINISH_MAX_MS = 1_200L
        /** Na het downloaden: hooguit zo lang wachten tot de haan de laatste korrels op heeft en kraait. */
        const val FULL_FINISH_MAX_MS = 2_500L
    }
}
