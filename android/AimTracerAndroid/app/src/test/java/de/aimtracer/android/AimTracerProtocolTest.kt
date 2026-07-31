package de.aimtracer.android

import de.aimtracer.android.model.DeviceConfiguration
import de.aimtracer.android.model.TriggerMode
import de.aimtracer.android.protocol.AimTracerCodec
import de.aimtracer.android.protocol.ProtocolException
import de.aimtracer.android.protocol.ShotAssembler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class AimTracerProtocolTest {
    @Test
    fun configurationRoundTripsTwentyBytes() {
        val configuration = DeviceConfiguration(
            triggerMode = TriggerMode.AUDIO,
            liveRateHz = 30,
            preTriggerMs = 450,
            postTriggerMs = 300,
            microphoneThreshold = 8_123,
            accelerationThreshold = 1_789,
            gyroThreshold = 2_345,
            coincidenceMs = 55,
            refractoryMs = 1_500
        )
        val encoded = AimTracerCodec.encode(configuration)
        assertEquals(20, encoded.size)
        val decoded = AimTracerCodec.configuration(encoded)
        assertEquals(configuration, decoded)
        assertArrayEquals(encoded, AimTracerCodec.encode(decoded))
    }

    @Test
    fun powerStatusDecodesBatteryAndChargingState() {
        val power = AimTracerCodec.powerStatus(
            byteArrayOf(
                1,
                73,
                0b0001_1001,
                0x6C,
                0x0F,
                50
            )
        )
        assertEquals(73, power.levelPercent)
        assertEquals(3_948, power.millivolts)
        assertEquals(50, power.chargeCurrentMa)
        assertTrue(power.isCharging)
        assertTrue(power.externalPowerPresent)
        assertTrue(power.chargeSignalActive)

        val unplugged = AimTracerCodec.powerStatus(
            byteArrayOf(
                1,
                72,
                0b0001_0001,
                0x60,
                0x0F,
                50
            )
        )
        assertFalse(unplugged.isCharging)
        assertFalse(unplugged.externalPowerPresent)
        assertTrue(unplugged.chargeSignalActive)
    }

    @Test
    fun completeShotAssemblesAndMissingSampleIsDiagnosed() {
        val complete = ShotAssembler()
        val shot = shotPackets(312).mapNotNull(complete::ingest).single()
        assertEquals(312, shot.samples.size)
        assertEquals(208, shot.triggerIndex)

        val incomplete = ShotAssembler()
        val error = assertThrows(ProtocolException::class.java) {
            shotPackets(12, omittedIndex = 7).forEach(incomplete::ingest)
        }
        assertNotNull(error.message)
        assertTrue(error.message!!.contains("11 von 12"))
        assertTrue(error.message!!.contains("Paket: 7"))
    }

    private fun shotPackets(
        sampleCount: Int,
        omittedIndex: Int? = null
    ): List<ByteArray> {
        val metadata = ByteArray(20).apply {
            this[0] = 0x20
            this[1] = 1
            putU16(2, 42)
            putU32(4, 123_456L)
            putU16(8, 416)
            putU16(10, sampleCount)
            putU16(12, minOf(208, sampleCount - 1))
            putU16(14, 8_000)
            putU16(16, 2_000)
            putU16(18, 3_000)
        }
        val packets = mutableListOf(metadata)
        val payload = mutableListOf<Byte>()
        repeat(sampleCount) { index ->
            val sample = ByteArray(20).apply {
                this[0] = 0x21
                putU16(1, 42)
                putU16(3, index)
                putU16(5, index + 1)
                putU16(7, index + 2)
                putU16(9, index + 3)
                putU16(11, index + 4)
                putU16(13, index + 5)
                putU16(15, index + 6)
                putU16(17, index + 100)
                this[19] = if (index == minOf(208, sampleCount - 1)) 1 else 0
            }
            payload += sample.copyOfRange(5, 19).toList()
            if (index != omittedIndex) packets += sample
        }
        val crc = CRC32().apply {
            update(payload.toByteArray())
        }.value
        packets += ByteArray(20).apply {
            this[0] = 0x22
            putU16(1, 42)
            putU16(3, sampleCount)
            putU32(5, crc)
        }
        return packets
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Long) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
