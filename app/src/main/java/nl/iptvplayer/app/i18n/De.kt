package nl.iptvplayer.app.i18n

/** Deutsch. */
object De : Strings() {
    override val chooseLanguage = "Sprache wählen"
    override val chooseLanguageHint = "Du kannst das später in den Einstellungen ändern."
    override val appLanguage = "App-Sprache"
    override val appLanguageHint = "Die Sprache von Menüs, Schaltflächen und Meldungen."

    override val tabSearch = "Suche"
    override val tabHome = "Start"
    override val tabLive = "Live-TV"
    override val tabSeries = "Serien"
    override val tabMovies = "Filme"
    override val tabFavorites = "Favoriten"
    override val tabCatchup = "Replay"
    override val tabSettings = "Einstellungen"

    override val movie = "Film"
    override val series = "Serie"
    override val channels = "Sender"
    override val play = "Abspielen"
    override val favorite = "Favorit"
    override val inFavorites = "In Favoriten"
    override val watched = "Gesehen"
    override val cancel = "Abbrechen"
    override val remove = "Entfernen"
    override val removeQuestion = "Aus dieser Liste entfernen?"
    override val removeHint = "OK gedrückt halten zum Entfernen"
    override val retry = "Erneut versuchen"
    override val later = "Später"
    override val now = "JETZT"
    override val loadingInfo = "Informationen werden geladen…"
    override val noDescription = "Keine Beschreibung verfügbar."
    override val continueWatching = "Weiterschauen"
    override val keepWatching = "Weiterschauen"
    override val recentlyWatched = "Zuletzt gesehen"
    override fun nextUp(title: String) = "Als Nächstes: $title"
    override fun timeLeft(time: String) = "noch $time"
    override fun hoursMinutes(h: Int, m: Int) = "$h Std. $m Min."
    override fun minutes(m: Int) = "$m Min."
    override fun seasons(n: Int) = if (n == 1) "1 Staffel" else "$n Staffeln"
    override fun episodes(n: Int) = if (n == 1) "1 Folge" else "$n Folgen"
    override fun libraryCounts(movies: String, series: String, channels: String) =
        "$movies Filme · $series Serien · $channels Sender"

    override val loginIntro = "Melde dich mit den Daten deines IPTV-Anbieters an (Xtream Codes). " +
        "Drücke OK auf einem Feld, um die Tastatur zu öffnen."
    override val serverPlaceholder = "Server-URL (z. B. provider.tv:8080)"
    override val username = "Benutzername"
    override val password = "Passwort"
    override val loggingIn = "Anmeldung läuft…"
    override val login = "Anmelden"
    override val firstLoadHint = "Beim ersten Mal kann das etwas dauern: Die komplette Film- und Serienliste wird geladen."
    override val cannotConnect = "Keine Verbindung"
    override val otherLogin = "Andere Anmeldung"
    override val connecting = "Verbindung zum Server…"
    override val loading = "Wird geladen…"
    override val downloadingLists = "Listen werden heruntergeladen…"
    override val indexing = "Filme und Serien werden indexiert…"

    override val loginFailed = "Anmeldung fehlgeschlagen."
    override val serverNotFound = "Server nicht gefunden. Prüfe die URL und deine Internetverbindung."
    override val serverTimeout = "Der Server antwortet nicht. Versuche es später erneut."
    override val invalidServerUrl = "Ungültige Server-URL"
    override val unexpectedResponse = "Unerwartete Antwort vom Server. Stimmt die URL?"
    override val wrongCredentials = "Benutzername oder Passwort falsch."
    override fun accountInactive(status: String) = "Konto ist nicht aktiv (Status: $status)."
    override fun serverError(code: Int) = "Server meldet Fehler $code"
    override val emptyResponse = "Leere Antwort vom Server"
    override fun seasonName(n: Int) = "Staffel $n"
    override fun episodeName(n: Int) = "Folge $n"
    override fun seasonEpisode(season: Int, episode: Int) = "S$season F$episode"
    override fun episodeShort(n: Int) = "F$n"
    override val unknownProgram = "Unbekannte Sendung"
    override fun playbackFailed(reason: String) = "Wiedergabe fehlgeschlagen: $reason"

    override val newSeries = "Neue Serien"
    override val newMovies = "Neue Filme"
    override val connected = "Verbunden"
    override val offline = "Offline – gespeicherte Listen"
    override fun subscriptionUntil(date: String) = "Abo bis $date"
    override val noFavorites = "Noch keine Favoriten"
    override val noFavoritesHint = "Halte OK auf einem Film, einer Serie oder einem Sender gedrückt, um ihn hier zu speichern."

    override val sortDefault = "Standard"
    override val sortNewest = "Neueste"
    override val sortAz = "A-Z"
    override val sortRating = "Bewertung"
    override val sortYear = "Jahr"
    override val allChannels = "Alle Sender"
    override val all = "Alle"
    override fun channelCount(n: String) = "$n Sender"
    override fun titleCount(n: String) = "$n Titel"
    override val nothingFound = "Nichts gefunden"
    override val nothingFoundHint = "In den gewählten Sprachen gibt es keine Titel. Passe den Sprachfilter in den Einstellungen an."
    override val recentlyAdded = "Neu hinzugefügt"
    override val seeAll = "Alle ansehen"
    override val genres = mapOf(
        "comedy" to "Komödie",
        "drama" to "Drama",
        "crime" to "Krimi",
        "action & adventure" to "Action & Abenteuer",
        "sci-fi & fantasy" to "Sci-Fi & Fantasy",
        "mystery" to "Mystery",
        "documentary" to "Dokumentationen",
        "reality" to "Reality",
        "animation" to "Animation",
        "kids" to "Für Kinder",
        "family" to "Familie",
        "war & politics" to "Krieg & Politik",
        "romance" to "Romantik",
        "thriller" to "Thriller",
        "western" to "Western",
        "history" to "Geschichte",
        "talk" to "Talkshows",
    )

    override fun director(names: String) = "Regie: $names"
    override fun cast(names: String) = "Mit: $names"
    override fun resumeAt(time: String) = "Weiterschauen ($time)"
    override val fromStart = "Von vorne"
    override val seriesInfoFailed = "Die Serieninformationen konnten nicht geladen werden."
    override val loadingEpisodes = "Folgen werden geladen…"
    override fun resumeEpisode(season: Int, episode: Int) = "Weiter: S$season F$episode"
    override fun playEpisode(season: Int, episode: Int) = "Abspielen: S$season F$episode"
    override fun resumeSeasonEpisode(season: Int, episode: Int) = "Weiterschauen: Staffel $season, Folge $episode"
    override fun playSeasonEpisode(season: Int, episode: Int) = "Staffel $season, Folge $episode abspielen"

    override val channelUnavailable = "Dieser Sender ist gerade nicht verfügbar"
    override val previewHint = "Bleib kurz auf einem Sender für eine Vorschau"
    override val loadingGuide = "Programm wird geladen…"
    override val noGuide = "Keine Programminformationen für diesen Sender."
    override fun laterAt(time: String, title: String) = "Danach $time: $title"
    override val zapHint = "▲▼ zappen  ·  Zurück = Senderliste"

    override val today = "Heute"
    override val yesterday = "Gestern"
    override val noCatchup = "Keine Sender mit Replay"
    override val noCatchupHint = "Dein Anbieter bietet (in den gewählten Sprachen) keine Sender an, deren Sendungen du nachträglich ansehen kannst."
    override fun daysBack(n: Int) = if (n == 1) "1 Tag zurück" else "$n Tage zurück"
    override val loadingArchive = "Archiv wird geladen…"
    override val noArchive = "Keine Sendungen im Archiv dieses Senders."
    override fun until(time: String) = "bis $time"

    override val searchPlaceholder = "Filme, Serien und Sender suchen…"
    override val searchHint = "Suche nach Titel, auch mit einem Teil eines Wortes (\"break bad\") oder ohne Akzente. " +
        "Kleine Tippfehler werden auch gefunden. Du kannst auch die Mikrofontaste der Tastatur verwenden."
    override val searching = "Suche läuft…"
    override fun nothingFoundFor(query: String) = "Nichts gefunden für \"$query\"."

    override val newVersionAvailable = "Neue Version verfügbar"
    override fun versionReady(version: String, current: String) = "Version $version ist bereit. Deine aktuelle Version ist $current."
    override fun installedVersion(current: String) =
        "Installierte Version: $current. Die App prüft bei jedem Start automatisch, ob es eine neue Version gibt."
    override val upToDate = "Du hast die neueste Version."
    override fun checkFailed(message: String) = "Prüfung fehlgeschlagen: $message"
    override fun newVersionNamed(version: String) = "Neue Version verfügbar: $version"
    override val allowInstall = "Erlaube Gallo einmalig, Apps zu installieren (Schalter einschalten und zurückgehen). Danach geht die Aktualisierung automatisch weiter."
    override val checking = "Wird geprüft…"
    override val updateNow = "Jetzt aktualisieren"
    override fun downloading(percent: Int) = "Wird heruntergeladen… $percent %"
    override val checkForUpdates = "Nach Updates suchen"
    override fun updateServerError(code: Int) = "Update-Server meldet Fehler $code"
    override fun downloadFailed(code: Int) = "Download fehlgeschlagen (Fehler $code)"
    override val emptyDownload = "Leerer Download"

    override val contentLanguages = "Sprachen des Angebots"
    override val contentLanguagesHint = "Wähle, welche Sprachen du in Start, Filme, Serien, Live-TV und bei der Suche sehen möchtest. " +
        "Nichts gewählt = alles anzeigen."
    override val allLanguages = "Alle Sprachen"
    override fun visible(counts: String) = "Sichtbar: $counts"
    override val playback = "Wiedergabe"
    override val playbackHint = "Streamformat für Live-TV. TS startet bei den meisten IPTV-Servern schneller; HLS ist manchmal stabiler. " +
        "Funktioniert eines nicht, probiert die App automatisch das andere."
    override val formatAuto = "Automatisch"
    override val updates = "Updates"
    override val account = "Konto"
    override val server = "Server"
    override val user = "Benutzer"
    override val status = "Status"
    override val expiresOn = "Läuft ab am"
    override val unlimited = "Unbegrenzt"
    override val connections = "Verbindungen"
    override fun connectionsInUse(active: Int, max: Int) = "$active von $max in Gebrauch"
    override val library = "Mediathek"
    override val total = "Gesamt"
    override val updated = "Aktualisiert"
    override val refreshing = "Wird aktualisiert…"
    override val refreshLists = "Listen aktualisieren"
    override val logout = "Abmelden"
    override val logoutQuestion = "Abmelden?"
    override val logoutWarning = "Danach musst du Server, Benutzername und Passwort erneut eingeben."
    override val settingsFooter = "Listen werden automatisch alle 12 Stunden aktualisiert. Beim Schauen: links/rechts spult 10 s zurück oder 30 s vor; " +
        "bei Live-TV zappst du mit hoch/runter durch die Sender der Kategorie."

    override val back = "Zurück"
    override val rewind10 = "10 Sekunden zurück"
    override val nextEpisode = "Nächste Folge"
    override val otherTracks = "Andere…"
    override val playbackSpeed = "Wiedergabegeschwindigkeit"
    override val episodesAndMore = "Folgen und mehr"
    override val audioAndSubtitles = "Audio und Untertitel"
    override val addToFavorites = "Zu Favoriten hinzufügen"
    override val watchCredits = "Abspann ansehen"
    override val pause = "Pause"
    override val audio = "Audio"
    override val subtitles = "Untertitel"
    override val defaultAudio = "Standard-Audio"
    override fun withSubtitles(audio: String, subtitle: String) = "$audio mit Untertiteln ($subtitle)"
    override val noChoice = "Keine Auswahl verfügbar."
    override val off = "Aus"
    override val normalSpeed = "Normal"
}
