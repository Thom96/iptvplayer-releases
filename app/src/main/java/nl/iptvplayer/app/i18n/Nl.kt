package nl.iptvplayer.app.i18n

/** Nederlands. */
object Nl : Strings() {
    override val chooseLanguage = "Kies je taal"
    override val chooseLanguageHint = "Je kunt dit later wijzigen bij Instellingen."
    override val appLanguage = "Taal van de app"
    override val appLanguageHint = "De taal van menu's, knoppen en meldingen."

    override val tabSearch = "Zoeken"
    override val tabHome = "Home"
    override val tabLive = "Live TV"
    override val tabSeries = "Series"
    override val tabMovies = "Films"
    override val tabFavorites = "Favorieten"
    override val tabCatchup = "Terugkijken"
    override val tabSettings = "Instellingen"

    override val movie = "Film"
    override val series = "Serie"
    override val channels = "Zenders"
    override val play = "Afspelen"
    override val favorite = "Favoriet"
    override val inFavorites = "In favorieten"
    override val watched = "Gezien"
    override val cancel = "Annuleren"
    override val remove = "Verwijderen"
    override val removeQuestion = "Verwijderen uit deze lijst?"
    override val removeHint = "Houd OK ingedrukt om te verwijderen"
    override val retry = "Opnieuw proberen"
    override val later = "Later"
    override val now = "NU"
    override val loadingInfo = "Informatie laden…"
    override val noDescription = "Geen beschrijving beschikbaar."
    override val continueWatching = "Verder kijken"
    override val keepWatching = "Kijk verder"
    override val recentlyWatched = "Recent bekeken"
    override fun nextUp(title: String) = "Volgende: $title"
    override fun timeLeft(time: String) = "nog $time"
    override fun hoursMinutes(h: Int, m: Int) = "${h}u ${m}m"
    override fun minutes(m: Int) = "$m min"
    override fun seasons(n: Int) = if (n == 1) "1 seizoen" else "$n seizoenen"
    override fun episodes(n: Int) = if (n == 1) "1 aflevering" else "$n afleveringen"
    override fun libraryCounts(movies: String, series: String, channels: String) =
        "$movies films · $series series · $channels zenders"

    override val loginIntro = "Log in met de gegevens van je IPTV-provider (Xtream Codes). " +
        "Druk op OK op een veld om het toetsenbord te openen."
    override val serverPlaceholder = "Server-URL (bijv. provider.tv:8080)"
    override val username = "Gebruikersnaam"
    override val password = "Wachtwoord"
    override val loggingIn = "Bezig met inloggen…"
    override val login = "Inloggen"
    override val firstLoadHint = "Bij de eerste keer kan dit even duren: de volledige film- en serielijst wordt opgehaald."
    override val cannotConnect = "Kan niet verbinden"
    override val otherLogin = "Andere login"
    override val connecting = "Verbinden met server…"
    override val loading = "Laden…"
    override val downloadingLists = "Lijsten downloaden…"
    override val indexing = "Films en series indexeren…"

    override val loginFailed = "Inloggen mislukt."
    override val serverNotFound = "Server niet gevonden. Controleer de URL en je internetverbinding."
    override val serverTimeout = "De server reageert niet. Probeer het later opnieuw."
    override val invalidServerUrl = "Ongeldige server-URL"
    override val unexpectedResponse = "Onverwacht antwoord van de server. Klopt de URL?"
    override val wrongCredentials = "Gebruikersnaam of wachtwoord onjuist."
    override fun accountInactive(status: String) = "Account is niet actief (status: $status)."
    override fun serverError(code: Int) = "Server gaf fout $code"
    override val emptyResponse = "Leeg antwoord van de server"
    override fun seasonName(n: Int) = "Seizoen $n"
    override fun episodeName(n: Int) = "Aflevering $n"
    override fun seasonEpisode(season: Int, episode: Int) = "S$season E$episode"
    override fun episodeShort(n: Int) = "E$n"
    override val unknownProgram = "Onbekend programma"
    override fun playbackFailed(reason: String) = "Afspelen mislukt: $reason"

    override val newSeries = "Nieuwe series"
    override val newMovies = "Nieuwe films"
    override val connected = "Verbonden"
    override val offline = "Offline – opgeslagen lijsten"
    override fun subscriptionUntil(date: String) = "abonnement tot $date"
    override val noFavorites = "Nog geen favorieten"
    override val noFavoritesHint = "Houd OK ingedrukt op een film, serie of zender om hem hier te bewaren."

    override val sortDefault = "Standaard"
    override val sortNewest = "Nieuwst"
    override val sortAz = "A-Z"
    override val sortRating = "Beoordeling"
    override val sortYear = "Jaar"
    override val allChannels = "Alle zenders"
    override val all = "Alles"
    override fun channelCount(n: String) = "$n zenders"
    override fun titleCount(n: String) = "$n titels"
    override val nothingFound = "Niets gevonden"
    override val nothingFoundHint = "Er zijn geen titels in de gekozen talen. Pas het taalfilter aan bij Instellingen."
    override val recentlyAdded = "Nieuw toegevoegd"
    override val seeAll = "Alles bekijken"
    override val genres = mapOf(
        "comedy" to "Komedie",
        "drama" to "Drama",
        "crime" to "Misdaad",
        "action & adventure" to "Actie & avontuur",
        "sci-fi & fantasy" to "Sci-fi & fantasy",
        "mystery" to "Mysterie",
        "documentary" to "Documentaires",
        "reality" to "Reality",
        "animation" to "Animatie",
        "kids" to "Voor kinderen",
        "family" to "Familie",
        "war & politics" to "Oorlog & politiek",
        "romance" to "Romantiek",
        "thriller" to "Thrillers",
        "western" to "Westerns",
        "history" to "Geschiedenis",
        "talk" to "Talkshows",
    )

    override fun director(names: String) = "Regie: $names"
    override fun cast(names: String) = "Met: $names"
    override fun resumeAt(time: String) = "Verder kijken ($time)"
    override val fromStart = "Vanaf begin"
    override val seriesInfoFailed = "Kon de serie-informatie niet laden."
    override val loadingEpisodes = "Afleveringen laden…"
    override fun resumeEpisode(season: Int, episode: Int) = "Verder: S$season E$episode"
    override fun playEpisode(season: Int, episode: Int) = "Afspelen: S$season E$episode"
    override fun resumeSeasonEpisode(season: Int, episode: Int) = "Verder kijken: seizoen $season, aflevering $episode"
    override fun playSeasonEpisode(season: Int, episode: Int) = "Seizoen $season: Aflevering $episode afspelen"

    override val channelUnavailable = "Deze zender is nu niet beschikbaar"
    override val previewHint = "Blijf even op een zender staan voor een voorbeeld"
    override val loadingGuide = "Gids laden…"
    override val noGuide = "Geen gidsinformatie voor deze zender."
    override fun laterAt(time: String, title: String) = "Straks $time: $title"
    override val zapHint = "▲▼ zappen  ·  Terug = zenderlijst"

    override val today = "Vandaag"
    override val yesterday = "Gisteren"
    override val noCatchup = "Geen zenders met terugkijken"
    override val noCatchupHint = "Je provider biedt (in de gekozen talen) geen zenders aan waarvan je programma's kunt terugkijken."
    override fun daysBack(n: Int) = if (n == 1) "1 dag terug" else "$n dagen terug"
    override val loadingArchive = "Archief laden…"
    override val noArchive = "Geen programma's in het archief van deze zender."
    override fun until(time: String) = "tot $time"

    override val searchPlaceholder = "Zoek films, series en zenders…"
    override val searchHint = "Zoek op titel, ook met een deel van een woord (\"break bad\") of zonder accenten. " +
        "Kleine typfouten worden ook gevonden. Je kunt ook de microfoonknop van het toetsenbord gebruiken."
    override val searching = "Zoeken…"
    override fun nothingFoundFor(query: String) = "Niets gevonden voor \"$query\"."

    override val newVersionAvailable = "Nieuwe versie beschikbaar"
    override fun versionReady(version: String, current: String) = "Versie $version staat klaar. Je huidige versie is $current."
    override fun installedVersion(current: String) =
        "Geïnstalleerde versie: $current. De app kijkt bij elke start automatisch of er een nieuwe versie is."
    override val upToDate = "Je hebt de nieuwste versie."
    override fun checkFailed(message: String) = "Controleren mislukt: $message"
    override fun newVersionNamed(version: String) = "Nieuwe versie beschikbaar: $version"
    override val allowInstall = "Sta eenmalig toe dat Gallo apps mag installeren (zet de schakelaar aan en ga terug). Daarna gaat het bijwerken vanzelf verder."
    override val checking = "Controleren…"
    override val updateNow = "Nu bijwerken"
    override fun downloading(percent: Int) = "Downloaden… $percent%"
    override val checkForUpdates = "Controleren op updates"
    override fun updateServerError(code: Int) = "Updateserver gaf fout $code"
    override fun downloadFailed(code: Int) = "Download mislukt (fout $code)"
    override val emptyDownload = "Lege download"

    override val contentLanguages = "Talen van het aanbod"
    override val contentLanguagesHint = "Kies welke talen je wilt zien in Home, Films, Series, Live TV en bij het zoeken. " +
        "Niets gekozen = alles tonen."
    override val allLanguages = "Alle talen"
    override fun visible(counts: String) = "Zichtbaar: $counts"
    override val playback = "Afspelen"
    override val playbackHint = "Streamformaat voor live-tv. TS start bij de meeste IPTV-servers sneller; HLS is soms stabieler. " +
        "Werkt het ene niet, dan probeert de app automatisch het andere."
    override val formatAuto = "Automatisch"
    override val updates = "Updates"
    override val account = "Account"
    override val server = "Server"
    override val user = "Gebruiker"
    override val status = "Status"
    override val expiresOn = "Verloopt op"
    override val unlimited = "Onbeperkt"
    override val connections = "Verbindingen"
    override fun connectionsInUse(active: Int, max: Int) = "$active van $max in gebruik"
    override val library = "Bibliotheek"
    override val total = "Totaal"
    override val updated = "Bijgewerkt"
    override val refreshing = "Bezig met verversen…"
    override val refreshLists = "Lijsten vernieuwen"
    override val logout = "Uitloggen"
    override val logoutQuestion = "Uitloggen?"
    override val logoutWarning = "Daarna moet je server, gebruikersnaam en wachtwoord opnieuw invullen."
    override val settingsFooter = "Lijsten worden automatisch elke 12 uur ververst. Tijdens het kijken: links/rechts spoelt 10 s terug of 30 s vooruit, " +
        "bij live-tv zap je met omhoog/omlaag door de zenders van de categorie."

    override val back = "Terug"
    override val rewind10 = "10 seconden terug"
    override val nextEpisode = "Volgende aflevering"
    override val otherTracks = "Andere…"
    override val playbackSpeed = "Afspeelsnelheid"
    override val episodesAndMore = "Afleveringen en meer"
    override val audioAndSubtitles = "Audio en ondertiteling"
    override val addToFavorites = "Toevoegen aan favorieten"
    override val watchCredits = "Aftiteling kijken"
    override val pause = "Pauzeren"
    override val audio = "Audio"
    override val subtitles = "Ondertiteling"
    override val defaultAudio = "Standaard audio"
    override fun withSubtitles(audio: String, subtitle: String) = "$audio met ondertiteling ($subtitle)"
    override val noChoice = "Geen keuze beschikbaar."
    override val off = "Uit"
    override val normalSpeed = "Normaal"
}
