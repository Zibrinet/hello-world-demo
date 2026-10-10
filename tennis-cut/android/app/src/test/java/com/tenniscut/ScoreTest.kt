package com.tenniscut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreTest {
    private val bo3 = MatchFormat(sets = 3, finalSet = FinalSet.TIEBREAK)
    private val sixAll = ("AAAA" + "BBBB").repeat(6)

    private fun play(s: Score, f: MatchFormat, winners: String) =
        winners.fold(s) { acc, c -> Tennis.applyPoint(acc, f, Player.valueOf("$c")) }

    @Test fun gameLabels() {
        val s = play(Score(), bo3, "AAB")
        assertEquals(listOf("30", "15"), Tennis.pointLabels(s, bo3))
    }

    @Test fun deuceAndAdvantage() {
        var s = play(Score(), bo3, "AAABBBB")
        assertEquals(listOf("40", "AD"), Tennis.pointLabels(s, bo3))
        s = play(s, bo3, "AAA")
        assertEquals(listOf(1, 0), s.games)
    }

    @Test fun noAdDecidingPoint() {
        val f = bo3.copy(noAd = true)
        assertEquals(listOf(0, 1), play(Score(), f, "AAABBBB").games)
    }

    @Test fun tiebreakServingAndSet() {
        var s = play(Score(), bo3, sixAll)
        val servers = StringBuilder()
        repeat(7) {
            servers.append(Tennis.server(s, bo3).name)
            s = Tennis.applyPoint(s, bo3, Player.A)
        }
        assertEquals("ABBAABB", servers.toString())
        assertEquals(listOf(listOf(7, 6)), s.sets)
        assertEquals(Player.B, Tennis.server(s, bo3))
    }

    @Test fun matchTiebreakDecider() {
        val f = MatchFormat(sets = 3, finalSet = FinalSet.MATCH_TIEBREAK)
        var s = play(Score(), f, "AAAA".repeat(6) + "BBBB".repeat(6))
        s = play(s, f, "AB".repeat(9) + "BAAA")
        assertEquals(listOf(listOf(6, 0), listOf(0, 6), listOf(12, 10)), s.sets)
        assertEquals(Player.A, s.winner)
    }

    @Test fun advantageFinalSet() {
        val f = MatchFormat(sets = 3, finalSet = FinalSet.ADVANTAGE)
        var s = play(Score(), f, "AAAA".repeat(6) + "BBBB".repeat(6) + sixAll)
        assertEquals(listOf(6, 6), s.games)
        s = play(s, f, "AAAA")
        assertNull(s.winner)
        s = play(s, f, "AAAA")
        assertEquals(Player.A, s.winner)
    }
}
