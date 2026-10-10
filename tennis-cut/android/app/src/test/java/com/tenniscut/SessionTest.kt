package com.tenniscut

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTest {
    private val setup = Setup("2026-10-12_1630", "Me", "Friend", MatchFormat(), Player.A)

    private fun won(t: Double, p: Player) = Marker(t, Marker.POINT_WON, p, "clicker")

    @Test fun undoRemovesLatestPressInTimeOrder() {
        val presses = resolveMarkers(listOf(won(30.0, Player.A), won(10.0, Player.B),
            Marker(31.0, Marker.UNDO, null, "clicker"), won(32.0, Player.B)))
        assertEquals(listOf(10.0, 32.0), presses.map { it.t })
    }

    @Test fun windowsUsePreRollAndNeverOverlap() {
        val points = pointsFrom(setup, listOf(won(30.0, Player.A), won(45.0, Player.B), won(100.0, Player.A)), 200.0)
        assertEquals(listOf(10.0 to 31.0, 31.0 to 46.0, 80.0 to 101.0), points.map { it.start to it.end })
        assertEquals(listOf("15", "0"), Tennis.pointLabels(points[0].after, setup.format))
        assertEquals(listOf("0", "0"), Tennis.pointLabels(points[0].before, setup.format))
    }

    @Test fun setupAndMarkersRoundTripThroughJson() {
        assertEquals(setup, Setup.fromJson(setup.toJson()))
        val m = Marker(1.5, Marker.UNDO, null, "screen")
        assertEquals(m, Marker.fromJson(m.toJson()))
    }
}
