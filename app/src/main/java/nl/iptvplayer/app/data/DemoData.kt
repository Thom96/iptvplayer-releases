package nl.iptvplayer.app.data

/**
 * Fake library for demo mode (server "demo"): to test the look without a real IPTV server.
 * Images come from picsum.photos, videos are public test streams.
 */
internal object DemoData {
    const val MOVIE_URL = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    const val EPISODE_URL = "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8"
    const val LIVE_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    private val movieTitles = listOf(
        "NL - The Matrix (1999)", "NL - The Matrix Reloaded (2003)", "EN - Inception (2010)", "EN - Interstellar (2014)",
        "NL - Amélie (2001)", "EN - Spider-Man: No Way Home (2021)", "EN - Dune (2021)", "EN - Dune: Part Two (2024)",
        "NL - Oppenheimer (2023)", "EN - Barbie (2023)", "NL - Soof (2013)", "NL - Alles is Liefde (2007)",
        "EN - The Dark Knight (2008)", "EN - Pulp Fiction (1994)", "EN - Parasite (2019)", "NL - Zwartboek (2006)",
        "EN - Top Gun: Maverick (2022)", "EN - Gladiator (2000)", "EN - Avatar (2009)", "NL - De Club van Sinterklaas (2004)",
        "EN - The Godfather (1972)", "EN - Jurassic Park (1993)", "EN - Toy Story (1995)", "EN - Frozen (2013)",
    )
    private val seriesTitles = listOf(
        "Breaking Bad", "NL - Undercover", "The Office", "NL - Flikken Maastricht", "Stranger Things",
        "Game of Thrones", "NL - Penoza", "The Crown", "Friends", "Dark", "Succession", "The Last of Us",
    )
    private val channels = listOf(
        "NL | NPO 1", "NL | NPO 2", "NL | NPO 3", "NL | RTL 4", "NL | RTL 5", "NL | SBS 6", "NL | Veronica",
        "NL | ESPN 1", "NL | Ziggo Sport", "BE | VTM", "BE | Eén", "UK | BBC One", "UK | BBC Two", "DE | ARD",
    )

    val movieCategories = listOf(Category("1", "NL | Films"), Category("2", "EN | Films"), Category("3", "Kids")) +
        extraMovieCategories()
    val seriesCategories = listOf(Category("11", "NL | Series"), Category("12", "EN | Series")) + extraSeriesCategories()

    /**
     * Extra categories with 30 titles each, so the demo has as many rows as a real subscription:
     * needed to simulate scrolling through Movies and Series (see ScrollSimulationTest).
     */
    private fun extraMovieCategories() =
        listOf("NL | Netflix", "NL | Disney+", "NL | Prime Video", "EN | Actie", "EN | Komedie", "EN | 4K").mapIndexed { i, n -> Category("${4 + i}", n) }
    private fun extraSeriesCategories() =
        listOf("NL | Netflix", "NL | HBO Max", "EN | Disney+", "EN | Apple TV+", "EN | Documentaires").mapIndexed { i, n -> Category("${13 + i}", n) }
    private val demoGenres = listOf("Drama", "Comedy", "Crime, Drama", "Action & Adventure", "Sci-Fi & Fantasy", "Mystery, Drama", "Documentary")
    private const val PER_EXTRA_CATEGORY = 30
    val liveCategories = listOf(Category("21", "Nederland"), Category("22", "Sport"), Category("23", "Buitenland"))

    fun library(): Library {
        val now = System.currentTimeMillis() / 1000
        val movies = movieTitles.mapIndexed { i, name ->
            val cat = when {
                name.contains("Sinterklaas") || name.contains("Toy Story") || name.contains("Frozen") -> "3"
                name.startsWith("NL") -> "1"
                else -> "2"
            }
            MediaItem(
                Kind.MOVIE, 100 + i, name, poster(100 + i), cat, now - i * 86_400L,
                rating = 5.5 + (i * 7 % 40) / 10.0, ext = "mp4", year = TextMatch.yearFrom(name),
            )
        }
        val extraMovies = movieCategories.drop(3).flatMapIndexed { c, cat ->
            (0 until PER_EXTRA_CATEGORY).map { j ->
                val id = 1000 + c * 100 + j
                val name = movieTitles[(c * 7 + j) % movieTitles.size]
                MediaItem(
                    Kind.MOVIE, id, name, poster(id), cat.id, now - (c * 50 + j) * 3_600L - 30 * 86_400L,
                    rating = 5.0 + (id * 7 % 45) / 10.0, ext = "mp4", year = TextMatch.yearFrom(name),
                )
            }
        }
        fun seriesItem(id: Int, name: String, cat: String, added: Long, i: Int) = MediaItem(
            Kind.SERIES, id, name, poster(id), cat, added, rating = 7.0 + (i % 5) / 2.0, ext = null, year = 2008 + i % 17,
            plot = "Demoserie: hier staat de beschrijving die je IPTV-provider bij de serie meelevert.",
            genre = demoGenres[i % demoGenres.size], backdrop = backdrop(id),
        )
        val series = seriesTitles.mapIndexed { i, name ->
            seriesItem(200 + i, name, if (name.startsWith("NL")) "11" else "12", now - i * 43_200L, i)
        } + seriesCategories.drop(2).flatMapIndexed { c, cat ->
            (0 until PER_EXTRA_CATEGORY).map { j ->
                seriesItem(2000 + c * 100 + j, seriesTitles[(c * 5 + j) % seriesTitles.size], cat.id, now - (c * 50 + j) * 3_600L - 10 * 86_400L, c + j)
            }
        }
        val live = channels.mapIndexed { i, name ->
            val cat = when {
                name.contains("ESPN") || name.contains("Sport") -> "22"
                name.startsWith("NL") -> "21"
                else -> "23"
            }
            MediaItem(Kind.LIVE, 300 + i, name, null, cat, 0, 0.0, null, null, num = i + 1, archiveDays = if (i < 6) 7 else 0)
        }
        return Library(liveCategories, movieCategories, seriesCategories, live, movies + extraMovies, series, System.currentTimeMillis())
    }

    fun movieInfo(id: Int) = MovieInfo(
        plot = "Dit is een demofilm. In de echte app staat hier de beschrijving die je IPTV-provider meelevert, " +
            "met daaronder de regisseur en de cast.",
        genre = "Actie, Sciencefiction",
        cast = "Keanu Reeves, Carrie-Anne Moss, Laurence Fishburne",
        director = "Lana Wachowski, Lilly Wachowski",
        releaseDate = "1999-03-31",
        durationSecs = 8160,
        rating = 8.7,
        backdrop = backdrop(id),
        poster = poster(id),
        ext = "mp4",
    )

    fun seriesInfo(id: Int): SeriesInfo {
        val seasons = (1..3).map { s ->
            Season(s, "Seizoen $s", (1..8).map { e ->
                Episode(
                    id = id * 1000 + s * 100 + e, season = s, number = e,
                    title = "Aflevering $e", ext = "mp4",
                    image = "https://picsum.photos/seed/ep$id$s$e/480/270",
                    plot = "Beschrijving van aflevering $e uit seizoen $s.", durationSecs = 2700,
                )
            })
        }
        return SeriesInfo(
            plot = "Dit is een demoserie met drie seizoenen van elk acht afleveringen.",
            genre = "Drama", cast = "Bryan Cranston, Aaron Paul", releaseDate = "2008-01-20",
            rating = 9.1, backdrop = backdrop(id), poster = poster(id), seasons = seasons,
        )
    }

    fun epg(id: Int): List<EpgEntry> {
        val shows = listOf("Journaal", "Studio Sport", "Weerbericht", "Documentaire", "Talkshow", "Film van de week")
        val slot = 30 * 60_000L
        val first = System.currentTimeMillis() / slot * slot
        return (0 until 4).map { i ->
            EpgEntry(
                title = shows[(id + i) % shows.size],
                description = "Beschrijving van het programma in de demomodus.",
                start = first + i * slot,
                end = first + (i + 1) * slot,
            )
        }
    }

    fun archive(id: Int): List<CatchupEntry> {
        val shows = listOf("Journaal", "Studio Sport", "Weerbericht", "Documentaire", "Talkshow", "Film van de week")
        val hour = 60 * 60_000L
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
        val first = (System.currentTimeMillis() / hour - 24 * 3) * hour
        return (0 until 24 * 3 + 3).map { i ->
            val start = first + i * hour
            CatchupEntry(
                title = shows[(id + i) % shows.size],
                description = "Programma uit het archief (demo).",
                start = start,
                end = start + hour,
                serverStart = fmt.format(java.util.Date(start)),
                hasArchive = start + hour < System.currentTimeMillis(),
            )
        }
    }

    /** Guide for the TV guide screen: programmes of varying length from yesterday until the day after tomorrow. */
    fun guide(id: Int, now: Long): List<CatchupEntry> {
        val shows = listOf("Journaal", "Studio Sport", "Weerbericht", "Documentaire", "Talkshow", "Film van de week", "Quiz", "Kookprogramma")
        val lengths = listOf(15, 30, 45, 60, 90, 25, 50, 120)
        val minute = 60_000L
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
        var start = (now - XtreamClient.GUIDE_PAST_MS) / (30 * minute) * (30 * minute) + (id % 4) * 5 * minute
        var i = id
        val out = ArrayList<CatchupEntry>()
        while (start < now + XtreamClient.GUIDE_FUTURE_MS) {
            val end = start + lengths[(i * 7) % lengths.size] * minute
            out += CatchupEntry(
                title = shows[i % shows.size],
                description = "Beschrijving van het programma in de demomodus.",
                start = start, end = end,
                serverStart = fmt.format(java.util.Date(start)),
                hasArchive = id % 3 == 0 && end < now,
            )
            start = end
            i++
        }
        return out
    }

    private fun poster(id: Int) = "https://picsum.photos/seed/p$id/300/450"
    private fun backdrop(id: Int) = "https://picsum.photos/seed/b$id/1280/720"
}
