package nl.iptvplayer.app.i18n

/** Español. */
object Es : Strings() {
    override val chooseLanguage = "Elige tu idioma"
    override val chooseLanguageHint = "Puedes cambiarlo más tarde en Ajustes."
    override val appLanguage = "Idioma de la app"
    override val appLanguageHint = "El idioma de los menús, botones y mensajes."

    override val tabSearch = "Buscar"
    override val tabHome = "Inicio"
    override val tabLive = "TV en directo"
    override val tabSeries = "Series"
    override val tabMovies = "Películas"
    override val tabFavorites = "Favoritos"
    override val tabCatchup = "Volver a ver"
    override val tabSettings = "Ajustes"

    override val movie = "Película"
    override val series = "Serie"
    override val channels = "Canales"
    override val play = "Reproducir"
    override val favorite = "Favorito"
    override val inFavorites = "En favoritos"
    override val watched = "Visto"
    override val cancel = "Cancelar"
    override val remove = "Eliminar"
    override val removeQuestion = "¿Eliminar de esta lista?"
    override val removeHint = "Mantén pulsado OK para eliminar"
    override val retry = "Reintentar"
    override val later = "Más tarde"
    override val now = "AHORA"
    override val loadingInfo = "Cargando información…"
    override val noDescription = "No hay descripción disponible."
    override val continueWatching = "Seguir viendo"
    override val keepWatching = "Seguir viendo"
    override val recentlyWatched = "Vistos recientemente"
    override fun nextUp(title: String) = "A continuación: $title"
    override fun timeLeft(time: String) = "quedan $time"
    override fun hoursMinutes(h: Int, m: Int) = "$h h $m min"
    override fun minutes(m: Int) = "$m min"
    override fun seasons(n: Int) = if (n == 1) "1 temporada" else "$n temporadas"
    override fun episodes(n: Int) = if (n == 1) "1 episodio" else "$n episodios"
    override fun libraryCounts(movies: String, series: String, channels: String) =
        "$movies películas · $series series · $channels canales"

    override val loginIntro = "Inicia sesión con los datos de tu proveedor de IPTV (Xtream Codes). " +
        "Pulsa OK en un campo para abrir el teclado."
    override val serverPlaceholder = "URL del servidor (p. ej. provider.tv:8080)"
    override val username = "Usuario"
    override val password = "Contraseña"
    override val loggingIn = "Iniciando sesión…"
    override val login = "Iniciar sesión"
    override val firstLoadHint = "La primera vez puede tardar un poco: se descarga la lista completa de películas y series."
    override val cannotConnect = "No se puede conectar"
    override val otherLogin = "Otra cuenta"
    override val connecting = "Conectando con el servidor…"
    override val loading = "Cargando…"
    override val downloadingLists = "Descargando listas…"
    override val indexing = "Indexando películas y series…"

    override val loginFailed = "No se pudo iniciar sesión."
    override val serverNotFound = "Servidor no encontrado. Comprueba la URL y tu conexión a internet."
    override val serverTimeout = "El servidor no responde. Inténtalo de nuevo más tarde."
    override val serverUnreachable = "No se puede conectar con el servidor. Comprueba la URL y tu conexión a internet."
    override val secureConnectionFailed = "Ha fallado la conexión segura con el servidor. Prueba http:// en lugar de https://."
    override val unnamedCategory = "Otros"
    override val invalidServerUrl = "URL del servidor no válida"
    override val unexpectedResponse = "Respuesta inesperada del servidor. ¿Es correcta la URL?"
    override val wrongCredentials = "Usuario o contraseña incorrectos."
    override fun accountInactive(status: String) = "La cuenta no está activa (estado: $status)."
    override fun serverError(code: Int) = "El servidor devolvió el error $code"
    override val emptyResponse = "Respuesta vacía del servidor"
    override fun seasonName(n: Int) = "Temporada $n"
    override fun episodeName(n: Int) = "Episodio $n"
    override fun seasonEpisode(season: Int, episode: Int) = "T$season E$episode"
    override fun episodeShort(n: Int) = "E$n"
    override val unknownProgram = "Programa desconocido"
    override fun playbackFailed(reason: String) = "Error de reproducción: $reason"

    override val newSeries = "Series nuevas"
    override val newMovies = "Películas nuevas"
    override val connected = "Conectado"
    override val offline = "Sin conexión – listas guardadas"
    override fun subscriptionUntil(date: String) = "suscripción hasta el $date"
    override val noFavorites = "Aún no hay favoritos"
    override val noFavoritesHint = "Mantén pulsado OK en una película, serie o canal para guardarlo aquí."

    override val sortDefault = "Predeterminado"
    override val sortNewest = "Más recientes"
    override val sortAz = "A-Z"
    override val sortRating = "Valoración"
    override val sortYear = "Año"
    override val allChannels = "Todos los canales"
    override val all = "Todo"
    override fun channelCount(n: String) = "$n canales"
    override fun titleCount(n: String) = "$n títulos"
    override val nothingFound = "No se ha encontrado nada"
    override val nothingFoundHint = "No hay títulos en los idiomas elegidos. Cambia el filtro de idioma en Ajustes."
    override val recentlyAdded = "Añadidos recientemente"
    override val seeAll = "Ver todo"
    override val genres = mapOf(
        "comedy" to "Comedia",
        "drama" to "Drama",
        "crime" to "Crimen",
        "action & adventure" to "Acción y aventura",
        "sci-fi & fantasy" to "Ciencia ficción y fantasía",
        "mystery" to "Misterio",
        "documentary" to "Documentales",
        "reality" to "Reality",
        "animation" to "Animación",
        "kids" to "Para niños",
        "family" to "Familia",
        "war & politics" to "Bélico y política",
        "romance" to "Romance",
        "thriller" to "Suspense",
        "western" to "Western",
        "history" to "Historia",
        "talk" to "Programas de entrevistas",
    )

    override fun director(names: String) = "Dirección: $names"
    override fun cast(names: String) = "Reparto: $names"
    override fun resumeAt(time: String) = "Seguir viendo ($time)"
    override val fromStart = "Desde el principio"
    override val seriesInfoFailed = "No se pudo cargar la información de la serie."
    override val loadingEpisodes = "Cargando episodios…"
    override fun resumeEpisode(season: Int, episode: Int) = "Seguir: T$season E$episode"
    override fun playEpisode(season: Int, episode: Int) = "Reproducir: T$season E$episode"
    override fun resumeSeasonEpisode(season: Int, episode: Int) = "Seguir viendo: temporada $season, episodio $episode"
    override fun playSeasonEpisode(season: Int, episode: Int) = "Reproducir temporada $season, episodio $episode"

    override val channelUnavailable = "Este canal no está disponible ahora"
    override val previewHint = "Quédate un momento en un canal para ver una vista previa"
    override val loadingGuide = "Cargando guía…"
    override val noGuide = "No hay información de guía para este canal."
    override fun laterAt(time: String, title: String) = "Después $time: $title"
    override val zapHint = "▲▼ cambiar canal  ·  Atrás = lista de canales"

    override val today = "Hoy"
    override val yesterday = "Ayer"
    override val noCatchup = "No hay canales para volver a ver"
    override val noCatchupHint = "Tu proveedor no ofrece (en los idiomas elegidos) canales cuyos programas puedas volver a ver."
    override fun daysBack(n: Int) = if (n == 1) "1 día atrás" else "$n días atrás"
    override val loadingArchive = "Cargando archivo…"
    override val noArchive = "No hay programas en el archivo de este canal."
    override fun until(time: String) = "hasta las $time"

    override val searchPlaceholder = "Buscar películas, series y canales…"
    override val searchHint = "Busca por título, también con parte de una palabra (\"break bad\") o sin acentos. " +
        "También se encuentran pequeñas erratas. También puedes usar el botón del micrófono del teclado."
    override val searching = "Buscando…"
    override fun nothingFoundFor(query: String) = "No se ha encontrado nada para \"$query\"."

    override val newVersionAvailable = "Nueva versión disponible"
    override fun versionReady(version: String, current: String) = "La versión $version está lista. Tu versión actual es $current."
    override fun installedVersion(current: String) =
        "Versión instalada: $current. La app comprueba automáticamente si hay una versión nueva cada vez que se inicia."
    override val upToDate = "Tienes la versión más reciente."
    override fun checkFailed(message: String) = "Error al comprobar: $message"
    override fun newVersionNamed(version: String) = "Nueva versión disponible: $version"
    override val allowInstall = "Permite una vez que Gallo instale apps (activa el interruptor y vuelve atrás). Después, la actualización continuará automáticamente."
    override val checking = "Comprobando…"
    override val updateNow = "Actualizar ahora"
    override fun downloading(percent: Int) = "Descargando… $percent %"
    override val checkForUpdates = "Buscar actualizaciones"
    override fun updateServerError(code: Int) = "El servidor de actualizaciones devolvió el error $code"
    override fun downloadFailed(code: Int) = "Error en la descarga (error $code)"
    override val emptyDownload = "Descarga vacía"

    override val contentLanguages = "Idiomas del contenido"
    override val contentLanguagesHint = "Elige qué idiomas quieres ver en Inicio, Películas, Series, TV en directo y en la búsqueda. " +
        "Nada elegido = mostrar todo."
    override val allLanguages = "Todos los idiomas"
    override fun visible(counts: String) = "Visible: $counts"
    override val playback = "Reproducción"
    override val playbackHint = "Formato de stream para la TV en directo. TS arranca más rápido en la mayoría de servidores IPTV; HLS a veces es más estable. " +
        "Si uno no funciona, la app prueba automáticamente el otro."
    override val formatAuto = "Automático"
    override val updates = "Actualizaciones"
    override val account = "Cuenta"
    override val server = "Servidor"
    override val user = "Usuario"
    override val status = "Estado"
    override val expiresOn = "Caduca el"
    override val unlimited = "Ilimitado"
    override val connections = "Conexiones"
    override fun connectionsInUse(active: Int, max: Int) = "$active de $max en uso"
    override val library = "Biblioteca"
    override val total = "Total"
    override val updated = "Actualizado"
    override val refreshing = "Actualizando…"
    override val refreshLists = "Actualizar listas"
    override val logout = "Cerrar sesión"
    override val logoutQuestion = "¿Cerrar sesión?"
    override val logoutWarning = "Después tendrás que volver a introducir el servidor, el usuario y la contraseña."
    override val settingsFooter = "Las listas se actualizan automáticamente cada 12 horas. Mientras ves algo: izquierda/derecha retrocede 10 s o avanza 30 s; " +
        "en la TV en directo, arriba/abajo cambia entre los canales de la categoría."

    override val back = "Atrás"
    override val rewind10 = "Retroceder 10 segundos"
    override val nextEpisode = "Siguiente episodio"
    override val otherTracks = "Otros…"
    override val playbackSpeed = "Velocidad de reproducción"
    override val episodesAndMore = "Episodios y más"
    override val audioAndSubtitles = "Audio y subtítulos"
    override val addToFavorites = "Añadir a favoritos"
    override val watchCredits = "Ver créditos"
    override val pause = "Pausa"
    override val audio = "Audio"
    override val subtitles = "Subtítulos"
    override val defaultAudio = "Audio predeterminado"
    override fun withSubtitles(audio: String, subtitle: String) = "$audio con subtítulos ($subtitle)"
    override val noChoice = "No hay opciones disponibles."
    override val off = "Desactivados"
    override val normalSpeed = "Normal"
}
