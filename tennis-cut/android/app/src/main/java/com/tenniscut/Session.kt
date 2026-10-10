package com.tenniscut

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One match = one folder: match.mp4, setup.json, markers.json (format in tennis-cut/README.md,
 * so the laptop pipeline can read the same folder).
 */

data class Setup(
    val sessionId: String,
    val nameA: String,
    val nameB: String,
    val format: MatchFormat,
    val firstServer: Player,
) {
    fun name(p: Player) = if (p == Player.A) nameA else nameB

    fun toJson(): JSONObject = JSONObject()
        .put("schema", 1)
        .put("session_id", sessionId)
        .put("video", VIDEO_FILE)
        .put("players", JSONArray()
            .put(JSONObject().put("id", "A").put("name", nameA))
            .put(JSONObject().put("id", "B").put("name", nameB)))
        .put("format", JSONObject()
            .put("sets", format.sets)
            .put("no_ad", format.noAd)
            .put("final_set", format.finalSet.json))
        .put("first_server", firstServer.name)

    companion object {
        fun fromJson(o: JSONObject): Setup {
            val players = o.getJSONArray("players")
            val fmt = o.getJSONObject("format")
            return Setup(
                sessionId = o.getString("session_id"),
                nameA = players.getJSONObject(0).getString("name"),
                nameB = players.getJSONObject(1).getString("name"),
                format = MatchFormat(
                    sets = fmt.getInt("sets"),
                    noAd = fmt.getBoolean("no_ad"),
                    finalSet = FinalSet.fromJson(fmt.getString("final_set")),
                ),
                firstServer = Player.valueOf(o.getString("first_server")),
            )
        }
    }
}

/** A clicker or on-screen press. `t` is seconds since the recording started. */
data class Marker(val t: Double, val type: String, val player: Player?, val source: String) {
    fun toJson(): JSONObject = JSONObject().put("t", t).put("type", type).put("source", source).also {
        if (player != null) it.put("player", player.name)
    }

    companion object {
        const val POINT_WON = "point_won"
        const val UNDO = "undo"

        fun fromJson(o: JSONObject) = Marker(
            t = o.getDouble("t"),
            type = o.getString("type"),
            player = if (o.has("player")) Player.valueOf(o.getString("player")) else null,
            source = o.optString("source", "clicker"),
        )
    }
}

/** Apply undos in time order; returns the presses that still count. */
fun resolveMarkers(markers: List<Marker>): List<Marker> {
    val presses = ArrayList<Marker>()
    for (m in markers.sortedBy { it.t }) {
        when (m.type) {
            Marker.POINT_WON -> presses.add(m)
            Marker.UNDO -> if (presses.isNotEmpty()) presses.removeAt(presses.size - 1)
        }
    }
    return presses
}

const val VIDEO_FILE = "match.mp4"

class SessionDir(val dir: File) {
    val video get() = File(dir, VIDEO_FILE)
    private val setupFile get() = File(dir, "setup.json")
    private val markersFile get() = File(dir, "markers.json")
    private val highlightsFile get() = File(dir, "highlights_uri.txt")
    private val topRalliesFile get() = File(dir, "top_rallies_uri.txt")
    private val ralliesFile get() = File(dir, "rallies.json")
    private val verdictsFile get() = File(dir, "scoring.json")

    /** Rallies found in the audio, or null if the recording has not been analysed yet. */
    fun readRallies(): List<Rally>? =
        if (ralliesFile.exists()) ralliesFromJson(JSONArray(ralliesFile.readText())) else null

    fun writeRallies(rallies: List<Rally>) = writeAtomically(ralliesFile, ralliesToJson(rallies).toString())

    /** Who won each rally (by rally index): clicker presses first, then anything scored after the match. */
    fun readVerdicts(rallies: List<Rally>): Map<Int, Verdict> {
        val scored = if (verdictsFile.exists()) verdictsFromJson(JSONObject(verdictsFile.readText())) else emptyMap()
        return verdictsFromPresses(rallies, resolveMarkers(readMarkers())) + scored
    }

    fun writeVerdicts(verdicts: Map<Int, Verdict>) = writeAtomically(verdictsFile, verdictsToJson(verdicts).toString())

    var topRalliesUri: Uri?
        get() = if (topRalliesFile.exists()) Uri.parse(topRalliesFile.readText()) else null
        set(value) {
            if (value == null) topRalliesFile.delete() else topRalliesFile.writeText(value.toString())
        }

    /** Where the finished highlight video was saved in the gallery, if it has been made. */
    var highlightsUri: Uri?
        get() = if (highlightsFile.exists()) Uri.parse(highlightsFile.readText()) else null
        set(value) {
            if (value == null) highlightsFile.delete() else highlightsFile.writeText(value.toString())
        }

    fun readSetup() = Setup.fromJson(JSONObject(setupFile.readText()))
    fun writeSetup(setup: Setup) = writeAtomically(setupFile, setup.toJson().toString(2))

    fun readMarkers(): List<Marker> {
        if (!markersFile.exists()) return emptyList()
        val arr = JSONArray(markersFile.readText())
        return (0 until arr.length()).map { Marker.fromJson(arr.getJSONObject(it)) }
    }

    fun writeMarkers(markers: List<Marker>) =
        writeAtomically(markersFile, JSONArray().apply { markers.forEach { put(it.toJson()) } }.toString(2))

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        check(tmp.renameTo(target)) { "could not save ${target.name}" }
    }
}
