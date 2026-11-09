package nl.iptvplayer.app.player

import nl.iptvplayer.app.data.SegmentType
import nl.iptvplayer.app.data.SkipSegment
import nl.iptvplayer.app.data.parseIntroDbMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkipPlannerTest {
    private val minute = 60_000L
    private val duration = 45 * minute

    @Test
    fun readsDatabaseAnswer() {
        val json = """{"tvdb_id":1,"type":"tv","intro":[{"start_ms":60000,"end_ms":145000}],
            "recap":[{"start_ms":null,"end_ms":30000}],"credits":[{"start_ms":2600000,"end_ms":null}]}"""
        assertEquals(
            listOf(
                SkipSegment(SegmentType.RECAP, 0, 30_000),
                SkipSegment(SegmentType.INTRO, 60_000, 145_000),
                SkipSegment(SegmentType.CREDITS, 2_600_000, null),
            ),
            parseIntroDbMedia(json),
        )
        assertEquals(emptyList<SkipSegment>(), parseIntroDbMedia("not json"))
    }

    @Test
    fun knownIntroShowsOnlyDuringTheIntro() {
        val segs = listOf(SkipSegment(SegmentType.INTRO, 60_000, 145_000))
        assertNull(skipActionAt(30_000, duration, segs, null, isEpisode = true))
        assertEquals(SkipAction(SegmentType.INTRO, 145_000), skipActionAt(90_000, duration, segs, null, isEpisode = true))
        assertNull(skipActionAt(144_000, duration, segs, null, isEpisode = true))
    }

    @Test
    fun learnedIntroBeforeGuess() {
        val learned = SkipSegment(SegmentType.INTRO, 10_000, 70_000)
        assertEquals(SkipAction(SegmentType.INTRO, 70_000), skipActionAt(20_000, duration, emptyList(), learned, isEpisode = true))
        // After the learned intro: no button, not even the guess.
        assertNull(skipActionAt(100_000, duration, emptyList(), learned, isEpisode = true))
    }

    @Test
    fun guessOnlyAtTheStartOfEpisodes() {
        assertEquals(SkipAction(SegmentType.INTRO, 95_000), skipActionAt(10_000, duration, emptyList(), null, isEpisode = true))
        assertNull(skipActionAt(4 * minute, duration, emptyList(), null, isEpisode = true))
        assertNull(skipActionAt(10_000, duration, emptyList(), null, isEpisode = false))
    }

    @Test
    fun nextEpisodeAtStartOfCredits() {
        val credits = listOf(SkipSegment(SegmentType.CREDITS, duration - 2 * minute, null))
        assertEquals(2 * minute, creditsLeadMs(duration, credits))
        // Unknown: last 5%, between 30 and 90 s.
        assertEquals(90_000L, creditsLeadMs(duration, emptyList()))
        // Opening credits are ignored; the countdown always fits.
        assertEquals(90_000L, creditsLeadMs(duration, listOf(SkipSegment(SegmentType.CREDITS, 0, 60_000))))
        assertEquals(20_000L, creditsLeadMs(duration, listOf(SkipSegment(SegmentType.CREDITS, duration - 5_000, null)), minLeadMs = 20_000))
    }

    @Test
    fun learnsFromRepeatedPresses() {
        val learned = mutableListOf<Pair<Long, Long>>()
        val learner = IntroLearner { s, e -> learned += s to e }
        var t = 0L
        var pos = 15_000L
        repeat(8) { learner.onSeek(pos, pos + 10_000, t); pos += 10_000; t += 700 }
        // Overshot: one step back.
        learner.onSeek(pos, pos - 10_000, t)
        assertEquals(15_000L to 85_000L, learned.last())
    }

    @Test
    fun doesNotLearnLateOrHugeJumps() {
        val learned = mutableListOf<Pair<Long, Long>>()
        val learner = IntroLearner { s, e -> learned += s to e }
        learner.onSeek(20 * minute, 21 * minute, 0)
        learner.onSeek(30_000, 20 * minute, 10_000)
        learner.onSeek(30_000, 40_000, 20_000)
        assertEquals(emptyList<Pair<Long, Long>>(), learned)
    }
}
