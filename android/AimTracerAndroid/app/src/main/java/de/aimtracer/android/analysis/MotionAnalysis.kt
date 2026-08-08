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

data class AxisDisplayConfiguration(
    val invertXAxis: Boolean = false,
    val invertYAxis: Boolean = false
) {
    val horizontalMultiplier: Double get() = if (invertXAxis) -1.0 else 1.0
    val verticalMultiplier: Double get() = if (invertYAxis) -1.0 else 1.0
}

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
    val holdScore: Double,
    val triggerScore: Double,
    val followThroughScore: Double,
    val techniqueIndex: Double
)

data class TechniqueBaseline(
    val sourceSessionCount: Int,
    val targetSessionCount: Int,
    val holdMedian: Double,
    val triggerMedian: Double,
    val followThroughMedian: Double
) {
    val isProvisional: Boolean get() = sourceSessionCount < targetSessionCount
}

data class TechniqueProgressPoint(
    val id: String,
    val date: Long,
    val name: String,
    val techniqueIndex: Double
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
    val trend: SessionTrend?,
    val holdScore: Double,
    val triggerScore: Double,
    val followThroughScore: Double,
    val techniqueIndex: Double,
    val baseline: TechniqueBaseline
)

object MotionAnalysis {
    const val GYRO_DPS_PER_LSB = 0.0175
    const val MOUNTING_PROFILE_ID =
        "xiao-sense-component-side-down-usb-toward-shooter-v3"

    fun trace(
        shot: ShotCapture,
        axes: AxisDisplayConfiguration = AxisDisplayConfiguration()
    ): List<TracePoint> {
        if (shot.samples.isEmpty() || shot.sampleRateHz <= 0) return emptyList()
        val dt = 1.0 / shot.sampleRateHz
        var x = 0.0
        var y = 0.0
        val points = shot.samples.map { sample ->
            // Verified on the mounted AimTracer enclosure:
            // yaw right = +gz, pitch up = +gy, longitudinal roll = gx.
            // User inversion affects the graph only, never RMS scoring.
            x += sample.gz * GYRO_DPS_PER_LSB * dt * axes.horizontalMultiplier
            y += sample.gy * GYRO_DPS_PER_LSB * dt * axes.verticalMultiplier
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

    fun liveTrace(
        samples: List<LiveMotionSample>,
        axes: AxisDisplayConfiguration = AxisDisplayConfiguration()
    ): List<TracePoint> {
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
            x += current.gz * GYRO_DPS_PER_LSB * dt * axes.horizontalMultiplier
            y += current.gy * GYRO_DPS_PER_LSB * dt * axes.verticalMultiplier
            points += TracePoint(x, y, (current.uptimeMs - start) / 1_000.0)
        }
        return points
    }

    fun metrics(shot: ShotCapture): ShotMetrics {
        val rate = shot.sampleRateHz.coerceAtLeast(1)
        val trigger = shot.triggerIndex.coerceIn(0, shot.samples.size)
        // CALIBRATION: Keep the hold and trigger windows separate.
        val holdEnd = (trigger - (rate * 0.18).toInt()).coerceAtLeast(0)
        val triggerStart = (trigger - (rate * 0.15).toInt()).coerceAtLeast(0)
        val triggerEnd = (trigger + 1).coerceAtMost(shot.samples.size)
        // The first 50 ms contain the pneumatic impulse, not follow-through.
        val followStart = (trigger + (rate * 0.05).toInt())
            .coerceAtMost(shot.samples.size)
        val followEnd = (trigger + rate / 4).coerceAtMost(shot.samples.size)
        return ShotMetrics(
            holdRms = rms(shot.samples.subList(0, holdEnd)),
            triggerRms = rms(
                shot.samples.subList(triggerStart, triggerEnd)
            ),
            followThroughRms = rms(
                shot.samples.subList(followStart, maxOf(followStart, followEnd))
            )
        )
    }

    private fun rms(samples: List<MotionSample>): Double {
        if (samples.isEmpty()) return 0.0
        return sqrt(
            samples.sumOf {
                val y = it.gy * GYRO_DPS_PER_LSB
                val z = it.gz * GYRO_DPS_PER_LSB
                // Pitch/yaw move the sight line; roll remains in raw JSON.
                y * y + z * z
            } / samples.size
        )
    }
}

object SessionAnalysis {
    const val BASELINE_SESSION_TARGET = 3
    const val HOLD_WEIGHT = 0.30
    const val TRIGGER_WEIGHT = 0.50
    const val FOLLOW_THROUGH_WEIGHT = 0.20
    const val SCORE_EXPONENT = 3.0

    fun summary(
        session: TrainingSession,
        allSessions: List<TrainingSession> = emptyList()
    ): SessionSummary {
        val measured = session.shots.mapIndexed { index, shot ->
            Triple(shot, index + 1, MotionAnalysis.metrics(shot))
        }
        val holdValues = measured.map { it.third.holdRms }
        val triggerValues = measured.map { it.third.triggerRms }
        val followValues = measured.map { it.third.followThroughRms }
        val holdRanks = ranks(holdValues, true)
        val triggerRanks = ranks(triggerValues, true)
        val followRanks = ranks(followValues, true)
        val baseline = techniqueBaseline(session, allSessions)
        val scores = measured.map { componentScores(it.third, baseline) }
        val indices = scores.map { it.overall }
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
                holdScore = scores[index].hold,
                triggerScore = scores[index].trigger,
                followThroughScore = scores[index].follow,
                techniqueIndex = scores[index].overall
            )
        }
        return SessionSummary(
            standings = standings,
            hold = statistics(holdValues),
            trigger = statistics(triggerValues),
            followThrough = statistics(followValues),
            trend = trend(measured.map { it.third }),
            holdScore = scores.map { it.hold }.meanOr(50.0),
            triggerScore = scores.map { it.trigger }.meanOr(50.0),
            followThroughScore = scores.map { it.follow }.meanOr(50.0),
            techniqueIndex = indices.meanOr(50.0),
            baseline = baseline
        )
    }

    fun progress(
        session: TrainingSession,
        allSessions: List<TrainingSession>
    ): List<TechniqueProgressPoint> =
        allSessions
            .filter { it.program == session.program && it.shots.isNotEmpty() }
            .sortedBy { it.startedAt }
            .map { candidate ->
                TechniqueProgressPoint(
                    id = candidate.id,
                    date = candidate.startedAt,
                    name = candidate.name,
                    techniqueIndex = summary(candidate, allSessions).techniqueIndex
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
        val context = previous + session
        val current = summary(session, context)
        val references = previous.map { summary(it, context) }
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

    private data class ComponentScores(
        val hold: Double,
        val trigger: Double,
        val follow: Double,
        val overall: Double
    )

    private fun techniqueBaseline(
        session: TrainingSession,
        allSessions: List<TrainingSession>
    ): TechniqueBaseline {
        val candidates = if (allSessions.any { it.id == session.id }) {
            allSessions
        } else {
            allSessions + session
        }
        val sources = candidates
            .filter {
                it.program == session.program &&
                    it.endedAt != null &&
                    it.shots.size >= 3
            }
            .sortedBy { it.startedAt }
            .take(BASELINE_SESSION_TARGET)
        val metrics = sources.flatMap { source ->
            source.shots.map(MotionAnalysis::metrics)
        }
        return TechniqueBaseline(
            sourceSessionCount = sources.size,
            targetSessionCount = BASELINE_SESSION_TARGET,
            holdMedian = metrics.map { it.holdRms }.median(),
            triggerMedian = metrics.map { it.triggerRms }.median(),
            followThroughMedian = metrics.map { it.followThroughRms }.median()
        )
    }

    private fun componentScores(
        metrics: ShotMetrics,
        baseline: TechniqueBaseline
    ): ComponentScores {
        val hold = score(metrics.holdRms, baseline.holdMedian)
        val trigger = score(metrics.triggerRms, baseline.triggerMedian)
        val follow = score(metrics.followThroughRms, baseline.followThroughMedian)
        return ComponentScores(
            hold,
            trigger,
            follow,
            hold * HOLD_WEIGHT +
                trigger * TRIGGER_WEIGHT +
                follow * FOLLOW_THROUGH_WEIGHT
        )
    }

    private fun score(value: Double, reference: Double): Double {
        if (value <= 0 || reference <= 0) return 50.0
        return (100.0 / (1.0 + (value / reference).pow(SCORE_EXPONENT)))
            .coerceIn(0.0, 100.0)
    }

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

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
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

    private fun List<Double>.meanOr(fallback: Double): Double =
        if (isEmpty()) fallback else sum() / size
}
