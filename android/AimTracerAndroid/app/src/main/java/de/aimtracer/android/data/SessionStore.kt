package de.aimtracer.android.data

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.aimtracer.android.model.MotionSample
import de.aimtracer.android.model.ShotCapture
import de.aimtracer.android.model.TrainingProgram
import de.aimtracer.android.model.TrainingSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class SessionStore(context: Context) {
    private val file = AtomicFile(context.filesDir.resolve("sessions.json"))

    var sessions by mutableStateOf<List<TrainingSession>>(emptyList())
        private set

    var activeSessionId by mutableStateOf<String?>(null)
        private set

    val activeSession: TrainingSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    init {
        load()
    }

    fun start(program: TrainingProgram) {
        if (activeSessionId != null) return
        val now = System.currentTimeMillis()
        val session = TrainingSession(
            startedAt = now,
            name = "${program.title} ${
                DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT
                ).format(Date(now))
            }",
            program = program
        )
        sessions = listOf(session) + sessions
        activeSessionId = session.id
        save()
    }

    fun stop() {
        val id = activeSessionId ?: return
        sessions = sessions.map {
            if (it.id == id) it.copy(endedAt = System.currentTimeMillis())
            else it
        }
        activeSessionId = null
        save()
    }

    fun record(shot: ShotCapture) {
        if (activeSessionId == null) start(TrainingProgram.FREE_TRAINING)
        val id = activeSessionId ?: return
        sessions = sessions.map {
            if (it.id == id) it.copy(shots = it.shots + shot) else it
        }
        save()
    }

    fun deleteSession(id: String) {
        sessions = sessions.filterNot { it.id == id }
        if (activeSessionId == id) activeSessionId = null
        save()
    }

    fun deleteShot(sessionId: String, shotId: String) {
        sessions = sessions.map { session ->
            if (session.id == sessionId) {
                session.copy(shots = session.shots.filterNot { it.id == shotId })
            } else {
                session
            }
        }
        save()
    }

    private fun load() {
        if (!file.baseFile.exists()) return
        runCatching {
            file.openRead().bufferedReader().use { it.readText() }
        }.mapCatching { text ->
            val array = JSONArray(text)
            List(array.length()) { index ->
                sessionFromJson(array.getJSONObject(index))
            }
        }.onSuccess { loaded ->
            val now = System.currentTimeMillis()
            sessions = loaded.map {
                if (it.endedAt == null) it.copy(endedAt = now) else it
            }
        }
    }

    private fun save() {
        val bytes = JSONArray().apply {
            sessions.forEach { put(sessionToJson(it)) }
        }.toString().toByteArray(Charsets.UTF_8)

        val stream = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            stream.write(bytes)
            stream.flush()
            file.finishWrite(stream)
        } catch (_: Exception) {
            file.failWrite(stream)
        }
    }

    private fun sessionToJson(session: TrainingSession) = JSONObject().apply {
        put("id", session.id)
        put("startedAt", session.startedAt)
        put("endedAt", session.endedAt ?: JSONObject.NULL)
        put("name", session.name)
        put("program", session.program.name)
        put("shots", JSONArray().apply {
            session.shots.forEach { put(shotToJson(it)) }
        })
    }

    private fun shotToJson(shot: ShotCapture) = JSONObject().apply {
        put("id", shot.id)
        put("deviceShotId", shot.deviceShotId)
        put("receivedAt", shot.receivedAt)
        put("triggerUptimeMs", shot.triggerUptimeMs)
        put("sampleRateHz", shot.sampleRateHz)
        put("triggerIndex", shot.triggerIndex)
        put("audioPeak", shot.audioPeak)
        put("accelerationPeak", shot.accelerationPeak)
        put("gyroPeak", shot.gyroPeak)
        put("samples", JSONArray().apply {
            shot.samples.forEach { sample ->
                put(JSONArray().apply {
                    put(sample.index)
                    put(sample.gx.toInt())
                    put(sample.gy.toInt())
                    put(sample.gz.toInt())
                    put(sample.ax.toInt())
                    put(sample.ay.toInt())
                    put(sample.az.toInt())
                    put(sample.microphonePeak)
                    put(sample.isTrigger)
                })
            }
        })
    }

    private fun sessionFromJson(json: JSONObject): TrainingSession {
        val shotsJson = json.optJSONArray("shots") ?: JSONArray()
        return TrainingSession(
            id = json.getString("id"),
            startedAt = json.getLong("startedAt"),
            endedAt = if (json.isNull("endedAt")) null else json.getLong("endedAt"),
            name = json.getString("name"),
            program = runCatching {
                TrainingProgram.valueOf(json.optString("program"))
            }.getOrDefault(TrainingProgram.FREE_TRAINING),
            shots = List(shotsJson.length()) {
                shotFromJson(shotsJson.getJSONObject(it))
            }
        )
    }

    private fun shotFromJson(json: JSONObject): ShotCapture {
        val samplesJson = json.getJSONArray("samples")
        return ShotCapture(
            id = json.getString("id"),
            deviceShotId = json.getInt("deviceShotId"),
            receivedAt = json.getLong("receivedAt"),
            triggerUptimeMs = json.getLong("triggerUptimeMs"),
            sampleRateHz = json.getInt("sampleRateHz"),
            triggerIndex = json.getInt("triggerIndex"),
            audioPeak = json.getInt("audioPeak"),
            accelerationPeak = json.getInt("accelerationPeak"),
            gyroPeak = json.getInt("gyroPeak"),
            samples = List(samplesJson.length()) { index ->
                val sample = samplesJson.getJSONArray(index)
                MotionSample(
                    index = sample.getInt(0),
                    gx = sample.getInt(1).toShort(),
                    gy = sample.getInt(2).toShort(),
                    gz = sample.getInt(3).toShort(),
                    ax = sample.getInt(4).toShort(),
                    ay = sample.getInt(5).toShort(),
                    az = sample.getInt(6).toShort(),
                    microphonePeak = sample.getInt(7),
                    isTrigger = sample.getBoolean(8)
                )
            }
        )
    }
}
