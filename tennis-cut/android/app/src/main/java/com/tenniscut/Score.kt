package com.tenniscut

/**
 * Tennis scoring state machine, same rules as pipeline/tennis/score.py.
 * A tiebreak (and a match tiebreak) counts as one game for server rotation.
 */

enum class Player {
    A, B;

    val other: Player get() = if (this == A) B else A
}

enum class FinalSet(val json: String) {
    TIEBREAK("tiebreak"), MATCH_TIEBREAK("match_tiebreak"), ADVANTAGE("advantage");

    companion object {
        fun fromJson(value: String) = entries.first { it.json == value }
    }
}

data class MatchFormat(
    val sets: Int = 3,
    val noAd: Boolean = false,
    val finalSet: FinalSet = FinalSet.MATCH_TIEBREAK,
) {
    init {
        require(sets in setOf(1, 3, 5)) { "sets must be 1, 3 or 5" }
    }
}

data class Score(
    val sets: List<List<Int>> = emptyList(), // completed sets; a match tiebreak is stored as its points
    val games: List<Int> = listOf(0, 0),
    val points: List<Int> = listOf(0, 0),
    val gameServer: Player = Player.A, // whose turn it is in the game rotation
    val winner: Player? = null,
)

object Tennis {
    private val POINT_NAMES = listOf("0", "15", "30", "40")

    fun isMatchTiebreak(s: Score, f: MatchFormat) =
        s.sets.size == f.sets - 1 && f.finalSet == FinalSet.MATCH_TIEBREAK

    fun inTiebreak(s: Score, f: MatchFormat): Boolean {
        val deciding = s.sets.size == f.sets - 1
        if (deciding && f.finalSet == FinalSet.MATCH_TIEBREAK) return true
        if (deciding && f.finalSet == FinalSet.ADVANTAGE) return false
        return s.games == listOf(6, 6)
    }

    /** Who serves the next point. In a tiebreak: 1 point, then 2 each. */
    fun server(s: Score, f: MatchFormat): Player {
        if (!inTiebreak(s, f)) return s.gameServer
        val n = s.points.sum()
        return if (n == 0 || (n - 1) / 2 % 2 == 1) s.gameServer else s.gameServer.other
    }

    fun applyPoint(s: Score, f: MatchFormat, winner: Player): Score {
        check(s.winner == null) { "match is already over" }
        val w = winner.ordinal
        val pts = bump(s.points, w)
        val tiebreak = inTiebreak(s, f)
        val gameWon = if (tiebreak) {
            val target = if (isMatchTiebreak(s, f)) 10 else 7
            pts[w] >= target && pts[w] - pts[1 - w] >= 2
        } else {
            pts[w] >= 4 && (f.noAd || pts[w] - pts[1 - w] >= 2)
        }
        if (!gameWon) return s.copy(points = pts)

        val matchTb = isMatchTiebreak(s, f)
        val games = bump(s.games, w)
        val nextServer = s.gameServer.other
        val setOver = tiebreak || (games[w] >= 6 && games[w] - games[1 - w] >= 2)
        if (!setOver) return s.copy(games = games, points = listOf(0, 0), gameServer = nextServer)

        val sets = s.sets + listOf(if (matchTb) pts else games)
        val setsWon = sets.count { it[w] > it[1 - w] }
        return Score(
            sets = sets,
            gameServer = nextServer,
            winner = if (setsWon == f.sets / 2 + 1) winner else null,
        )
    }

    fun pointLabels(s: Score, f: MatchFormat): List<String> {
        val (a, b) = s.points
        if (inTiebreak(s, f)) return listOf("$a", "$b")
        if (a >= 3 && b >= 3 && a != b) return if (a > b) listOf("AD", "40") else listOf("40", "AD")
        return listOf(POINT_NAMES[minOf(a, 3)], POINT_NAMES[minOf(b, 3)])
    }

    private fun bump(pair: List<Int>, i: Int) = pair.mapIndexed { j, v -> if (j == i) v + 1 else v }
}
