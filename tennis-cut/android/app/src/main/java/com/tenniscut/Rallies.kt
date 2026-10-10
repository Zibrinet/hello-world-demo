package com.tenniscut

import org.json.JSONArray
import org.json.JSONObject

/** A run of racket hits found in the audio. `shots` counts the hits, so it is approximate. */
data class Rally(val start: Double, val end: Double, val shots: Int) {
    val playS get() = end - start

    fun toJson(): JSONObject = JSONObject().put("start", start).put("end", end).put("shots", shots)

    companion object {
        fun fromJson(o: JSONObject) = Rally(o.getDouble("start"), o.getDouble("end"), o.getInt("shots"))
    }
}

/** What the player said about a rally when scoring after the match. */
enum class Verdict { A_WON, B_WON, NOT_A_POINT }

/** A rally with the score around it; `before`/`after` are null once an earlier rally is still unscored. */
data class ScoredRally(val idx: Int, val rally: Rally, val verdict: Verdict?, val before: Score?, val after: Score?)

fun ralliesToJson(rallies: List<Rally>) = JSONArray().apply { rallies.forEach { put(it.toJson()) } }

fun ralliesFromJson(a: JSONArray) = (0 until a.length()).map { Rally.fromJson(a.getJSONObject(it)) }

fun verdictsToJson(v: Map<Int, Verdict>) = JSONObject().apply { v.forEach { (k, x) -> put("$k", x.name) } }

fun verdictsFromJson(o: JSONObject) = o.keys().asSequence().associate { it.toInt() to Verdict.valueOf(o.getString(it)) }

/**
 * Clicker presses made during the match pre-fill the scoring: a press belongs to the last rally
 * that ended shortly before it.
 */
fun verdictsFromPresses(rallies: List<Rally>, presses: List<Marker>): Map<Int, Verdict> {
    val out = HashMap<Int, Verdict>()
    for (p in presses) {
        val idx = rallies.indices.lastOrNull { rallies[it].end <= p.t + 1.0 && p.t - rallies[it].end <= 20.0 } ?: continue
        out[idx] = if (p.player == Player.A) Verdict.A_WON else Verdict.B_WON
    }
    return out
}

/** Runs the score through the rallies in order. "Not a point" rallies are left out entirely. */
fun scoreRallies(setup: Setup, rallies: List<Rally>, verdicts: Map<Int, Verdict>): List<ScoredRally> {
    var score: Score? = Score(gameServer = setup.firstServer)
    val out = ArrayList<ScoredRally>()
    rallies.forEachIndexed { i, rally ->
        val v = verdicts[i]
        if (v == Verdict.NOT_A_POINT) return@forEachIndexed
        val before = score
        val winner = when (v) {
            Verdict.A_WON -> Player.A
            Verdict.B_WON -> Player.B
            else -> null
        }
        score = when {
            before == null || winner == null -> null
            before.winner != null -> before // points after match point are still shown, score frozen
            else -> Tennis.applyPoint(before, setup.format, winner)
        }
        out.add(ScoredRally(i, rally, v, before, score))
    }
    return out
}

data class PlayerStats(
    val pointsWon: Int,
    val servePointsWon: Int,
    val servePoints: Int,
    val returnPointsWon: Int,
    val returnPoints: Int,
    val shortRalliesWon: Int, // 4 shots or fewer
    val longRalliesWon: Int, // 5 shots or more
)

data class MatchStats(
    val points: Int,
    val a: PlayerStats,
    val b: PlayerStats,
    val shortRallies: Int,
    val longRallies: Int,
    val averageShots: Double,
    val longestRally: ScoredRally?,
    val playS: Double,
)

const val LONG_RALLY_SHOTS = 5

/** Stats over the rallies whose winner is known. */
fun matchStats(setup: Setup, scored: List<ScoredRally>): MatchStats {
    val known = scored.filter { it.before != null && it.after != null && it.verdict != null }
    fun winner(r: ScoredRally) = if (r.verdict == Verdict.A_WON) Player.A else Player.B
    fun stats(p: Player): PlayerStats {
        val serving = known.filter { Tennis.server(it.before!!, setup.format) == p }
        val returning = known - serving.toSet()
        return PlayerStats(
            pointsWon = known.count { winner(it) == p },
            servePointsWon = serving.count { winner(it) == p },
            servePoints = serving.size,
            returnPointsWon = returning.count { winner(it) == p },
            returnPoints = returning.size,
            shortRalliesWon = known.count { it.rally.shots < LONG_RALLY_SHOTS && winner(it) == p },
            longRalliesWon = known.count { it.rally.shots >= LONG_RALLY_SHOTS && winner(it) == p },
        )
    }
    return MatchStats(
        points = known.size,
        a = stats(Player.A),
        b = stats(Player.B),
        shortRallies = known.count { it.rally.shots < LONG_RALLY_SHOTS },
        longRallies = known.count { it.rally.shots >= LONG_RALLY_SHOTS },
        averageShots = scored.map { it.rally.shots }.average().takeIf { !it.isNaN() } ?: 0.0,
        longestRally = scored.maxByOrNull { it.rally.shots },
        playS = scored.sumOf { it.rally.playS },
    )
}
