package de.aimtracer.android.model

import java.util.UUID

enum class TrainingProgram(
    val title: String,
    val plannedShots: Int?
) {
    LP40("LP40", 40),
    LP60("LP60", 60),
    DRY_FIRE("Trockentraining", null),
    FREE_TRAINING("Freies Training", null)
}

enum class TriggerMode(val wireValue: Int, val title: String) {
    AUDIO(1, "Nur Mikrofon"),
    MOTION(2, "Nur Bewegung"),
    AUDIO_AND_MOTION(3, "Mikrofon + Bewegung");

    companion object {
        fun fromWire(value: Int): TriggerMode =
            entries.firstOrNull { it.wireValue == value }
                ?: AUDIO_AND_MOTION
    }
}

data class DeviceConfiguration(
    val triggerMode: TriggerMode = TriggerMode.AUDIO_AND_MOTION,
    val sampleRateHz: Int = 416,
    val liveRateHz: Int = 25,
    val preTriggerMs: Int = 500,
    val postTriggerMs: Int = 250,
    val microphoneThreshold: Int = 7_000,
    val accelerationThreshold: Int = 1_500,
    val gyroThreshold: Int = 2_000,
    val coincidenceMs: Int = 40,
    val refractoryMs: Int = 1_200,
    val gyroRangeCode: Int = 2,
    val accelerationRangeCode: Int = 2
)

data class DeviceStatus(
    val isConnected: Boolean,
    val sessionActive: Boolean,
    val armed: Boolean,
    val capturing: Boolean,
    val transmitting: Boolean,
    val calibrated: Boolean,
    val microphoneReady: Boolean,
    val lastError: Int,
    val sampleRateHz: Int,
    val bufferedSamples: Int,
    val shotCount: Int,
    val droppedTriggers: Int,
    val microphonePeak: Int,
    val transmittingShotId: Int,
    val transmittingSampleIndex: Int,
    val firmwareVersion: String
)

data class DevicePowerStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
    val externalPowerPresent: Boolean,
    val chargeSignalActive: Boolean,
    val isLow: Boolean,
    val isCritical: Boolean,
    val millivolts: Int,
    val chargeCurrentMa: Int
)

data class MotionSample(
    val index: Int,
    val gx: Short,
    val gy: Short,
    val gz: Short,
    val ax: Short,
    val ay: Short,
    val az: Short,
    val microphonePeak: Int,
    val isTrigger: Boolean
)

data class LiveMotionSample(
    val sequence: Int,
    val uptimeMs: Long,
    val gx: Short,
    val gy: Short,
    val gz: Short,
    val ax: Short,
    val ay: Short,
    val az: Short,
    val microphonePeak8: Int
)

data class ShotCapture(
    val id: String = UUID.randomUUID().toString(),
    val deviceShotId: Int,
    val receivedAt: Long = System.currentTimeMillis(),
    val triggerUptimeMs: Long,
    val sampleRateHz: Int,
    val triggerIndex: Int,
    val audioPeak: Int,
    val accelerationPeak: Int,
    val gyroPeak: Int,
    val samples: List<MotionSample>
)

data class TrainingSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val name: String,
    val program: TrainingProgram = TrainingProgram.FREE_TRAINING,
    val shots: List<ShotCapture> = emptyList()
)
