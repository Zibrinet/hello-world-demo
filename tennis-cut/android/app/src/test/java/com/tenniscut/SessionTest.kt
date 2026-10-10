package com.tenniscut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTest {
    private val setup = Setup("2026-10-12_1630", "Me", "Friend", MatchFormat(), Player.A)
    private val rallies = listOf(Rally(10.0, 20.0, 3), Rally(40.0, 52.0, 7), Rally(80.0, 85.0, 1), Rally(100.0, 110.0, 5))

    private fun won(t: Double, p: Player) = Marker(t, Marker.POINT_WON, p, "clicker")

    @Test fun undoRemovesLatestPressInTimeOrder() {
        val presses = resolveMarkers(listOf(won(30.0, Player.A), won(10.0, Player.B),
            Marker(31.0, Marker.UNDO, null, "clicker"), won(32.0, Player.B)))
        assertEquals(listOf(10.0, 32.0), presses.map { it.t })
    }

    @Test fun clickerPressesBelongToTheRallyJustBefore() {
        val v = verdictsFromPresses(rallies, listOf(won(23.0, Player.B), won(55.0, Player.A), won(200.0, Player.A)))
        assertEquals(mapOf(0 to Verdict.B_WON, 1 to Verdict.A_WON), v)
    }

    @Test fun scoreRunsThroughRalliesAndSkipsNonPoints() {
        val scored = scoreRallies(setup, rallies, mapOf(0 to Verdict.A_WON, 1 to Verdict.A_WON, 2 to Verdict.NOT_A_POINT, 3 to Verdict.B_WON))
        assertEquals(listOf(0, 1, 3), scored.map { it.idx })
        assertEquals(listOf("30", "15"), Tennis.pointLabels(scored.last().after!!, setup.format))
    }

    @Test fun anUnscoredRallyStopsTheScoreAfterIt() {
        val scored = scoreRallies(setup, rallies, mapOf(0 to Verdict.A_WON, 2 to Verdict.A_WON))
        assertEquals(listOf(1, 0), scored[0].after!!.points)
        assertNull(scored[1].after)
        assertNull(scored[2].before)
    }

    @Test fun statsSplitServeReturnAndRallyLength() {
        val scored = scoreRallies(setup, rallies, mapOf(0 to Verdict.A_WON, 1 to Verdict.B_WON, 2 to Verdict.B_WON, 3 to Verdict.A_WON))
        val s = matchStats(setup, scored)
        assertEquals(4, s.points)
        assertEquals(2, s.a.pointsWon)
        assertEquals(4, s.a.servePoints) // A serves the whole first game
        assertEquals(2, s.b.returnPointsWon)
        assertEquals(1, s.a.shortRalliesWon)
        assertEquals(1, s.a.longRalliesWon)
        assertEquals(7, s.longestRally!!.rally.shots)
    }

    @Test fun jsonRoundTrips() {
        assertEquals(setup, Setup.fromJson(setup.toJson()))
        val m = Marker(1.5, Marker.UNDO, null, "screen")
        assertEquals(m, Marker.fromJson(m.toJson()))
        assertEquals(rallies, ralliesFromJson(ralliesToJson(rallies)))
        val v = mapOf(0 to Verdict.A_WON, 3 to Verdict.NOT_A_POINT)
        assertEquals(v, verdictsFromJson(verdictsToJson(v)))
    }
}
