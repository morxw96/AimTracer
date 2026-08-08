package de.aimtracer.android

import de.aimtracer.android.analysis.SessionAnalysis
import de.aimtracer.android.analysis.MotionAnalysis
import de.aimtracer.android.analysis.AxisDisplayConfiguration
import de.aimtracer.android.model.MotionSample
import de.aimtracer.android.model.ShotCapture
import de.aimtracer.android.model.TrainingProgram
import de.aimtracer.android.model.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalysisTest {
    @Test
    fun lp20AndFixedMountingProfileAreApplied() {
        assertEquals(20, TrainingProgram.LP20.plannedShots)

        val traceShot = ShotCapture(
            deviceShotId = 1,
            triggerUptimeMs = 1_000,
            sampleRateHz = 100,
            triggerIndex = 0,
            audioPeak = 100,
            accelerationPeak = 200,
            gyroPeak = 300,
            samples = listOf(
                sample(0, gx = 500, gy = 0, gz = 0),
                sample(1, gx = 500, gy = 100, gz = 200)
            )
        )
        val trace = MotionAnalysis.trace(traceShot)

        assertTrue(trace.last().x > 0)
        assertTrue(trace.last().y > 0)
        val inverted = MotionAnalysis.trace(
            traceShot,
            AxisDisplayConfiguration(
                invertXAxis = true,
                invertYAxis = true
            )
        )
        assertTrue(inverted.last().x < 0)
        assertTrue(inverted.last().y < 0)
        assertEquals(
            "xiao-sense-component-side-down-usb-toward-shooter-v3",
            MotionAnalysis.MOUNTING_PROFILE_ID
        )
    }

    @Test
    fun barrelRollIsExcludedWhilePitchAffectsMetrics() {
        val roll = MotionAnalysis.metrics(axisShot(gx = 2_000, gy = 0, gz = 0))
        val pitch = MotionAnalysis.metrics(axisShot(gx = 0, gy = 2_000, gz = 0))

        assertEquals(0.0, roll.holdRms, 0.0001)
        assertEquals(0.0, roll.triggerRms, 0.0001)
        assertEquals(0.0, roll.followThroughRms, 0.0001)
        assertTrue(pitch.holdRms > 0)
        assertTrue(pitch.triggerRms > 0)
        assertTrue(pitch.followThroughRms > 0)
    }

    @Test
    fun lowerMovementRanksFirstAndComparisonImproves() {
        val current = TrainingSession(
            startedAt = 2_000,
            name = "LP40 aktuell",
            program = TrainingProgram.LP40,
            shots = (1..10).map { shot(it, it.toShort()) }
        )
        val previous = TrainingSession(
            startedAt = 1_000,
            endedAt = 1_500,
            name = "LP40 alt",
            program = TrainingProgram.LP40,
            shots = (11..20).map { shot(it, it.toShort()) }
        )

        val summary = SessionAnalysis.summary(current, listOf(previous, current))
        assertEquals(1, summary.standings.first().overallRank)
        assertEquals(10, summary.standings.last().overallRank)
        assertNotNull(summary.trend)

        val comparison = SessionAnalysis.comparison(current, listOf(previous))
        assertTrue(requireNotNull(comparison).hold.improvementPercent > 0)
    }

    @Test
    fun personalTechniqueIndexStartsAtFiftyAndComparesAcrossSessions() {
        val firstShotSession = TrainingSession(
            startedAt = 1_000,
            name = "LP20 start",
            program = TrainingProgram.LP20,
            shots = listOf(shot(1, 10))
        )
        assertEquals(
            50.0,
            SessionAnalysis.summary(firstShotSession).techniqueIndex,
            0.0001
        )

        val baselineSessions = (1..3).map { sessionIndex ->
            TrainingSession(
                startedAt = sessionIndex * 1_000L,
                endedAt = sessionIndex * 1_000L + 500,
                name = "LP20 baseline $sessionIndex",
                program = TrainingProgram.LP20,
                shots = (1..3).map {
                    shot(sessionIndex * 10 + it, 10)
                }
            )
        }
        val moreMovement = TrainingSession(
            startedAt = 4_000,
            name = "LP20 later",
            program = TrainingProgram.LP20,
            shots = (41..43).map { shot(it, 20) }
        )
        val all = baselineSessions + moreMovement
        val summary = SessionAnalysis.summary(moreMovement, all)

        assertTrue(summary.techniqueIndex < 50)
        assertEquals(3, summary.baseline.sourceSessionCount)
        assertTrue(!summary.baseline.isProvisional)
        assertEquals(4, SessionAnalysis.progress(moreMovement, all).size)
    }

    private fun shot(id: Int, level: Short): ShotCapture =
        ShotCapture(
            deviceShotId = id,
            triggerUptimeMs = id * 1_000L,
            sampleRateHz = 416,
            triggerIndex = 208,
            audioPeak = 100,
            accelerationPeak = 200,
            gyroPeak = level.toInt(),
            samples = List(312) { index ->
                MotionSample(
                    index = index,
                    gx = level,
                    gy = level,
                    gz = level,
                    ax = 0,
                    ay = 0,
                    az = 0,
                    microphonePeak = 100,
                    isTrigger = index == 208
                )
            }
        )

    private fun axisShot(gx: Int, gy: Int, gz: Int) = ShotCapture(
        deviceShotId = 999,
        triggerUptimeMs = 1_000,
        sampleRateHz = 100,
        triggerIndex = 50,
        audioPeak = 100,
        accelerationPeak = 200,
        gyroPeak = maxOf(kotlin.math.abs(gx), kotlin.math.abs(gy), kotlin.math.abs(gz)),
        samples = List(80) { index -> sample(index, gx, gy, gz) }
    )

    private fun sample(index: Int, gx: Int, gy: Int, gz: Int) = MotionSample(
        index = index,
        gx = gx.toShort(),
        gy = gy.toShort(),
        gz = gz.toShort(),
        ax = 0,
        ay = 0,
        az = 0,
        microphonePeak = 0,
        isTrigger = index == 0
    )
}
