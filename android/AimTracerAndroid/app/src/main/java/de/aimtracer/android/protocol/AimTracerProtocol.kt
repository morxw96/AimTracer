package de.aimtracer.android.protocol

import de.aimtracer.android.model.DeviceConfiguration
import de.aimtracer.android.model.DevicePowerStatus
import de.aimtracer.android.model.DeviceStatus
import de.aimtracer.android.model.LiveMotionSample
import de.aimtracer.android.model.MotionSample
import de.aimtracer.android.model.ShotCapture
import de.aimtracer.android.model.TriggerMode
import java.util.zip.CRC32

enum class AimTracerCommand(val wireValue: Byte) {
    START_SESSION(0x01.toByte()),
    STOP_SESSION(0x02.toByte()),
    ARM(0x03.toByte()),
    DISARM(0x04.toByte()),
    MANUAL_TRIGGER(0x05.toByte()),
    CALIBRATE(0x06.toByte())
}

class ProtocolException(message: String) : Exception(message)

object AimTracerCodec {
    const val PROTOCOL_VERSION = 1
    const val PACKET_LENGTH = 20

    fun status(data: ByteArray): DeviceStatus {
        requirePacket(data, 0x01)
        if (data.u8(1) != PROTOCOL_VERSION) {
            throw ProtocolException("Unterschiedliche Protokollversionen.")
        }
        val flags = data.u8(2)
        return DeviceStatus(
            isConnected = flags and (1 shl 0) != 0,
            sessionActive = flags and (1 shl 1) != 0,
            armed = flags and (1 shl 2) != 0,
            capturing = flags and (1 shl 3) != 0,
            transmitting = flags and (1 shl 4) != 0,
            calibrated = flags and (1 shl 5) != 0,
            microphoneReady = flags and (1 shl 6) != 0,
            lastError = data.u8(3),
            sampleRateHz = data.u16(4),
            bufferedSamples = data.u16(6),
            shotCount = data.u16(8),
            droppedTriggers = data.u16(10),
            microphonePeak = data.u16(12),
            transmittingShotId = data.u16(14),
            transmittingSampleIndex = data.u16(16),
            firmwareVersion = "${data.u8(18)}.${data.u8(19)}"
        )
    }

    fun live(data: ByteArray): LiveMotionSample {
        requirePacket(data, 0x10)
        return LiveMotionSample(
            sequence = data.u16(1),
            uptimeMs = data.u32(3),
            gx = data.i16(7),
            gy = data.i16(9),
            gz = data.i16(11),
            ax = data.i16(13),
            ay = data.i16(15),
            az = data.i16(17),
            microphonePeak8 = data.u8(19)
        )
    }

    fun powerStatus(data: ByteArray): DevicePowerStatus {
        if (data.size != 6) {
            throw ProtocolException("BLE-Akkupaket hat nicht 6 Byte.")
        }
        if (data.u8(0) != PROTOCOL_VERSION) {
            throw ProtocolException("Unterschiedliche Protokollversionen.")
        }
        val flags = data.u8(2)
        val externalPowerPresent = flags and (1 shl 3) != 0
        return DevicePowerStatus(
            levelPercent = data.u8(1).coerceIn(0, 100),
            isCharging =
                flags and (1 shl 0) != 0 && externalPowerPresent,
            externalPowerPresent = externalPowerPresent,
            chargeSignalActive = flags and (1 shl 4) != 0,
            isLow = flags and (1 shl 1) != 0,
            isCritical = flags and (1 shl 2) != 0,
            millivolts = data.u16(3),
            chargeCurrentMa = data.u8(5)
        )
    }

    fun configuration(data: ByteArray): DeviceConfiguration {
        if (data.size != PACKET_LENGTH || data.u8(0) != PROTOCOL_VERSION) {
            throw ProtocolException("Ungültiges Konfigurationspaket.")
        }
        return DeviceConfiguration(
            triggerMode = TriggerMode.fromWire(data.u8(1)),
            sampleRateHz = data.u16(2),
            liveRateHz = data.u8(4),
            preTriggerMs = data.u16(6),
            postTriggerMs = data.u16(8),
            microphoneThreshold = data.u16(10),
            accelerationThreshold = data.u16(12),
            gyroThreshold = data.u16(14),
            coincidenceMs = data.u8(16),
            refractoryMs = data.u8(17) * 10,
            gyroRangeCode = data.u8(18),
            accelerationRangeCode = data.u8(19)
        )
    }

    fun encode(configuration: DeviceConfiguration): ByteArray =
        ByteArray(PACKET_LENGTH).apply {
            this[0] = PROTOCOL_VERSION.toByte()
            this[1] = configuration.triggerMode.wireValue.toByte()
            putU16(2, configuration.sampleRateHz)
            this[4] = configuration.liveRateHz.toByte()
            putU16(6, configuration.preTriggerMs)
            putU16(8, configuration.postTriggerMs)
            putU16(10, configuration.microphoneThreshold)
            putU16(12, configuration.accelerationThreshold)
            putU16(14, configuration.gyroThreshold)
            this[16] = configuration.coincidenceMs.toByte()
            this[17] = (configuration.refractoryMs / 10)
                .coerceIn(0, 255)
                .toByte()
            this[18] = configuration.gyroRangeCode.toByte()
            this[19] = configuration.accelerationRangeCode.toByte()
        }

    private fun requirePacket(data: ByteArray, type: Int) {
        if (data.size != PACKET_LENGTH) {
            throw ProtocolException("BLE-Paket hat nicht 20 Byte.")
        }
        if (data.u8(0) != type) {
            throw ProtocolException("Unerwarteter BLE-Pakettyp.")
        }
    }
}

class ShotAssembler {
    private data class Metadata(
        val shotId: Int,
        val uptimeMs: Long,
        val sampleRateHz: Int,
        val sampleCount: Int,
        val triggerIndex: Int,
        val audioPeak: Int,
        val accelerationPeak: Int,
        val gyroPeak: Int
    )

    private data class Builder(
        val metadata: Metadata,
        val samples: MutableMap<Int, MotionSample> = mutableMapOf(),
        val crcBytes: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    private val builders = mutableMapOf<Int, Builder>()

    fun ingest(data: ByteArray): ShotCapture? {
        if (data.size != AimTracerCodec.PACKET_LENGTH) {
            throw ProtocolException("Unvollständiges Schusspaket.")
        }
        return when (data.u8(0)) {
            0x20 -> {
                if (data.u8(1) != AimTracerCodec.PROTOCOL_VERSION) {
                    throw ProtocolException("Unterschiedliche Protokollversionen.")
                }
                val metadata = Metadata(
                    shotId = data.u16(2),
                    uptimeMs = data.u32(4),
                    sampleRateHz = data.u16(8),
                    sampleCount = data.u16(10),
                    triggerIndex = data.u16(12),
                    audioPeak = data.u16(14),
                    accelerationPeak = data.u16(16),
                    gyroPeak = data.u16(18)
                )
                builders[metadata.shotId] = Builder(metadata)
                null
            }

            0x21 -> {
                val shotId = data.u16(1)
                val index = data.u16(3)
                val builder = builders[shotId]
                    ?: throw ProtocolException("Schuss-Metadaten fehlen.")
                builder.samples[index] = MotionSample(
                    index = index,
                    gx = data.i16(5),
                    gy = data.i16(7),
                    gz = data.i16(9),
                    ax = data.i16(11),
                    ay = data.i16(13),
                    az = data.i16(15),
                    microphonePeak = data.u16(17),
                    isTrigger = data.u8(19) and 0x01 != 0
                )
                builder.crcBytes[index] = data.copyOfRange(5, 19)
                null
            }

            0x22 -> finish(data)
            else -> throw ProtocolException("Ungültiger Schusspakettyp.")
        }
    }

    private fun finish(data: ByteArray): ShotCapture {
        val shotId = data.u16(1)
        val announcedCount = data.u16(3)
        val expectedCrc = data.u32(5)
        val builder = builders.remove(shotId)
            ?: throw ProtocolException("Schuss-Metadaten fehlen.")
        if (
            announcedCount != builder.metadata.sampleCount ||
            builder.samples.size != announcedCount
        ) {
            val firstMissing = (0 until builder.metadata.sampleCount)
                .firstOrNull {
                    builder.samples[it] == null || builder.crcBytes[it] == null
                }
            val detail = firstMissing?.let {
                " Erstes fehlendes Paket: $it."
            }.orEmpty()
            throw ProtocolException(
                "Schussübertragung war unvollständig " +
                    "(${builder.samples.size} von " +
                    "${builder.metadata.sampleCount} Samples).$detail"
            )
        }
        val indices = builder.samples.keys.sorted()
        if (indices.withIndex().any { it.index != it.value }) {
            throw ProtocolException("Sample-Reihenfolge ist unvollständig.")
        }
        val crc = CRC32()
        indices.forEach { index ->
            crc.update(
                builder.crcBytes[index]
                    ?: throw ProtocolException("CRC-Daten fehlen.")
            )
        }
        if (crc.value != expectedCrc) {
            throw ProtocolException("Prüfsumme stimmt nicht.")
        }
        return ShotCapture(
            deviceShotId = shotId,
            triggerUptimeMs = builder.metadata.uptimeMs,
            sampleRateHz = builder.metadata.sampleRateHz,
            triggerIndex = builder.metadata.triggerIndex,
            audioPeak = builder.metadata.audioPeak,
            accelerationPeak = builder.metadata.accelerationPeak,
            gyroPeak = builder.metadata.gyroPeak,
            samples = indices.map { builder.samples.getValue(it) }
        )
    }
}

internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

internal fun ByteArray.u16(offset: Int): Int =
    u8(offset) or (u8(offset + 1) shl 8)

internal fun ByteArray.i16(offset: Int): Short = u16(offset).toShort()

internal fun ByteArray.u32(offset: Int): Long =
    (u8(offset).toLong()) or
        (u8(offset + 1).toLong() shl 8) or
        (u8(offset + 2).toLong() shl 16) or
        (u8(offset + 3).toLong() shl 24)

internal fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}
