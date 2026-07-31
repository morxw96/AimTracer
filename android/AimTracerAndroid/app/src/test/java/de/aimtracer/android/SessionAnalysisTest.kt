package de.aimtracer.android

import de.aimtracer.android.analysis.SessionAnalysis
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
    fun lowerMovementRanksFirstAndComparisonImproves() {
        val current = TrainingSession(
            startedAt = 2_000,
            name = "LP40 aktuell",
            program = TrainingProgram.LP40,
            shots = (1..10).map { shot(it, it.toShort()) }
        )
        val previous = TrainingSession(
            startedAt = 1_000,
            name = "LP40 alt",
            program = TrainingProgram.LP40,
            shots = (11..20).map { shot(it, it.toShort()) }
        )

        val summary = SessionAnalysis.summary(current)
        assertEquals(1, summary.standings.first().overallRank)
        assertEquals(10, summary.standings.last().overallRank)
        assertNotNull(summary.trend)

        val comparison = SessionAnalysis.comparison(current, listOf(previous))
        assertTrue(requireNotNull(comparison).hold.improvementPercent > 0)
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
}
