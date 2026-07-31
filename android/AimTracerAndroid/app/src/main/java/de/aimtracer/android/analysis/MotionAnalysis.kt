package de.aimtracer.android.analysis

import de.aimtracer.android.model.LiveMotionSample
import de.aimtracer.android.model.MotionSample
import de.aimtracer.android.model.ShotCapture
import de.aimtracer.android.model.TrainingSession
import kotlin.math.pow
import kotlin.math.sqrt

data class TracePoint(
    val x: Double,
    val y: Double,
    val relativeTime: Double
)

data class ShotMetrics(
    val holdRms: Double,
    val triggerRms: Double,
    val followThroughRms: Double
)

data class MetricStatistics(
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val best: Double,
    val worst: Double
) {
    companion object {
        val EMPTY = MetricStatistics(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class ShotStanding(
    val shot: ShotCapture,
    val ordinal: Int,
    val metrics: ShotMetrics,
    val holdRank: Int,
    val triggerRank: Int,
    val followThroughRank: Int,
    val overallRank: Int,
    val comparisonIndex: Double
)

data class MetricComparison(
    val current: Double,
    val reference: Double,
    val improvementPercent: Double
)

data class SessionComparison(
    val referenceSessionCount: Int,
    val hold: MetricComparison,
    val trigger: MetricComparison,
    val followThrough: MetricComparison
)

data class SessionTrend(
    val segmentSize: Int,
    val holdImprovementPercent: Double,
    val triggerImprovementPercent: Double,
    val followThroughImprovementPercent: Double
)

data class SessionSummary(
    val standings: List<ShotStanding>,
    val hold: MetricStatistics,
    val trigger: MetricStatistics,
    val followThrough: MetricStatistics,
    val trend: SessionTrend?
)

object MotionAnalysis {
    const val GYRO_DPS_PER_LSB = 0.0175

    fun trace(shot: ShotCapture): List<TracePoint> {
        if (shot.samples.isEmpty() || shot.sampleRateHz <= 0) return emptyList()
        val dt = 1.0 / shot.sampleRateHz
        var x = 0.0
        var y = 0.0
        val points = shot.samples.map { sample ->
            // CALIBRATION: Board flat, USB-C points to the rear of the pistol.
            x += sample.gz * GYRO_DPS_PER_LSB * dt
            y += sample.gy * GYRO_DPS_PER_LSB * dt
            TracePoint(
                x,
                y,
                (sample.index - shot.triggerIndex) * dt
            )
        }
        val trigger = points[shot.triggerIndex.coerceIn(points.indices)]
        return points.map {
            it.copy(x = it.x - trigger.x, y = it.y - trigger.y)
        }
    }

    fun liveTrace(samples: List<LiveMotionSample>): List<TracePoint> {
        if (samples.size < 2) return emptyList()
        val points = mutableListOf(TracePoint(0.0, 0.0, 0.0))
        var x = 0.0
        var y = 0.0
        val start = samples.first().uptimeMs
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val deltaMs = (current.uptimeMs - previous.uptimeMs)
            if (deltaMs !in 0 until 250) continue
            val dt = deltaMs / 1_000.0
            x += current.gz * GYRO_DPS_PER_LSB * dt
            y += current.gy * GYRO_DPS_PER_LSB * dt
            points += TracePoint(x, y, (current.uptimeMs - start) / 1_000.0)
        }
        return points
    }

    fun metrics(shot: ShotCapture): ShotMetrics {
        val rate = shot.sampleRateHz.coerceAtLeast(1)
        val trigger = shot.triggerIndex.coerceIn(0, shot.samples.size)
        val holdEnd = (trigger - rate / 6).coerceAtLeast(0)
        val triggerStart = (trigger - (rate * 0.15).toInt()).coerceAtLeast(0)
        val triggerEnd = (trigger + (rate * 0.08).toInt())
            .coerceAtMost(shot.samples.size)
        val followEnd = (trigger + rate / 4).coerceAtMost(shot.samples.size)
        return ShotMetrics(
            holdRms = rms(shot.samples.subList(0, holdEnd)),
            triggerRms = rms(
                shot.samples.subList(triggerStart, triggerEnd)
            ),
            followThroughRms = rms(
                shot.samples.subList(trigger, followEnd)
            )
        )
    }

    private fun rms(samples: List<MotionSample>): Double {
        if (samples.isEmpty()) return 0.0
        return sqrt(
            samples.sumOf {
                val x = it.gx * GYRO_DPS_PER_LSB
                val y = it.gy * GYRO_DPS_PER_LSB
                val z = it.gz * GYRO_DPS_PER_LSB
                x * x + y * y + z * z
            } / samples.size
        )
    }
}

object SessionAnalysis {
    fun summary(session: TrainingSession): SessionSummary {
        val measured = session.shots.mapIndexed { index, shot ->
            Triple(shot, index + 1, MotionAnalysis.metrics(shot))
        }
        val holdValues = measured.map { it.third.holdRms }
        val triggerValues = measured.map { it.third.triggerRms }
        val followValues = measured.map { it.third.followThroughRms }
        val holdRanks = ranks(holdValues, true)
        val triggerRanks = ranks(triggerValues, true)
        val followRanks = ranks(followValues, true)
        val count = measured.size
        val indices = measured.indices.map { index ->
            // CALIBRATION: Keep equal weights until paired Meyton data exists.
            (
                percentile(holdRanks[index], count) +
                    percentile(triggerRanks[index], count) +
                    percentile(followRanks[index], count)
                ) / 3.0
        }
        val overallRanks = ranks(indices, false)
        val standings = measured.indices.map { index ->
            ShotStanding(
                shot = measured[index].first,
                ordinal = measured[index].second,
                metrics = measured[index].third,
                holdRank = holdRanks[index],
                triggerRank = triggerRanks[index],
                followThroughRank = followRanks[index],
                overallRank = overallRanks[index],
                comparisonIndex = indices[index]
            )
        }
        return SessionSummary(
            standings = standings,
            hold = statistics(holdValues),
            trigger = statistics(triggerValues),
            followThrough = statistics(followValues),
            trend = trend(measured.map { it.third })
        )
    }

    fun previousComparable(
        session: TrainingSession,
        allSessions: List<TrainingSession>,
        limit: Int = 5
    ): List<TrainingSession> =
        allSessions
            .filter {
                it.id != session.id &&
                    it.startedAt < session.startedAt &&
                    it.program == session.program &&
                    it.shots.size >= 3
            }
            .sortedByDescending { it.startedAt }
            .take(limit)

    fun comparison(
        session: TrainingSession,
        previous: List<TrainingSession>
    ): SessionComparison? {
        if (session.shots.isEmpty() || previous.isEmpty()) return null
        val current = summary(session)
        val references = previous.map(::summary)
        return SessionComparison(
            referenceSessionCount = previous.size,
            hold = compare(current.hold.mean, references.map { it.hold.mean }.mean()),
            trigger = compare(
                current.trigger.mean,
                references.map { it.trigger.mean }.mean()
            ),
            followThrough = compare(
                current.followThrough.mean,
                references.map { it.followThrough.mean }.mean()
            )
        )
    }

    private fun ranks(values: List<Double>, lowerIsBetter: Boolean): List<Int> =
        values.map { value ->
            1 + values.count {
                if (lowerIsBetter) it < value else it > value
            }
        }

    private fun percentile(rank: Int, count: Int): Double =
        if (count <= 1) 100.0
        else 100.0 * (count - rank) / (count - 1)

    private fun statistics(values: List<Double>): MetricStatistics {
        if (values.isEmpty()) return MetricStatistics.EMPTY
        val average = values.mean()
        val sorted = values.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
        val variance = values.sumOf { (it - average).pow(2) } / values.size
        return MetricStatistics(
            mean = average,
            median = median,
            standardDeviation = sqrt(variance),
            best = sorted.first(),
            worst = sorted.last()
        )
    }

    private fun trend(metrics: List<ShotMetrics>): SessionTrend? {
        if (metrics.size < 8) return null
        val size = minOf(10, metrics.size / 2)
        val first = metrics.take(size)
        val last = metrics.takeLast(size)
        return SessionTrend(
            segmentSize = size,
            holdImprovementPercent = improvement(
                last.map { it.holdRms }.mean(),
                first.map { it.holdRms }.mean()
            ),
            triggerImprovementPercent = improvement(
                last.map { it.triggerRms }.mean(),
                first.map { it.triggerRms }.mean()
            ),
            followThroughImprovementPercent = improvement(
                last.map { it.followThroughRms }.mean(),
                first.map { it.followThroughRms }.mean()
            )
        )
    }

    private fun compare(current: Double, reference: Double) =
        MetricComparison(current, reference, improvement(current, reference))

    private fun improvement(current: Double, reference: Double): Double =
        if (reference <= 0) 0.0
        else (reference - current) / reference * 100

    private fun List<Double>.mean(): Double =
        if (isEmpty()) 0.0 else sum() / size
}
