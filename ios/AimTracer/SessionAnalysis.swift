import Foundation

struct MetricStatistics: Equatable {
    var mean: Double
    var median: Double
    var standardDeviation: Double
    var best: Double
    var worst: Double

    static let empty = MetricStatistics(
        mean: 0,
        median: 0,
        standardDeviation: 0,
        best: 0,
        worst: 0
    )
}

struct ShotStanding: Identifiable, Equatable {
    var id: UUID { shot.id }
    let shot: ShotCapture
    let ordinal: Int
    let metrics: ShotMetrics
    let holdRank: Int
    let triggerRank: Int
    let followThroughRank: Int
    let overallRank: Int
    let holdScore: Double
    let triggerScore: Double
    let followThroughScore: Double
    let techniqueIndex: Double

}

struct TechniqueBaseline: Equatable {
    let sourceSessionCount: Int
    let targetSessionCount: Int
    let holdMedian: Double
    let triggerMedian: Double
    let followThroughMedian: Double

    var isProvisional: Bool { sourceSessionCount < targetSessionCount }
}

struct TechniqueProgressPoint: Identifiable, Equatable {
    let id: UUID
    let date: Date
    let name: String
    let techniqueIndex: Double
}

struct MetricComparison: Equatable {
    let current: Double
    let reference: Double
    /// Positive values mean the current session is steadier than the reference.
    let improvementPercent: Double
}

struct SessionComparison: Equatable {
    let referenceSessionCount: Int
    let hold: MetricComparison
    let trigger: MetricComparison
    let followThrough: MetricComparison
}

struct SessionTrend: Equatable {
    let segmentSize: Int
    let holdImprovementPercent: Double
    let triggerImprovementPercent: Double
    let followThroughImprovementPercent: Double
}

struct SessionSummary: Equatable {
    let standings: [ShotStanding]
    let hold: MetricStatistics
    let trigger: MetricStatistics
    let followThrough: MetricStatistics
    let trend: SessionTrend?
    let holdScore: Double
    let triggerScore: Double
    let followThroughScore: Double
    let techniqueIndex: Double
    let baseline: TechniqueBaseline
}

enum SessionAnalysis {
    static let baselineSessionTarget = 3
    static let holdWeight = 0.30
    static let triggerWeight = 0.50
    static let followThroughWeight = 0.20
    // CALIBRATION: Higher values make the score react more strongly around
    // the personal median. Keep this identical on iOS and Android.
    static let scoreExponent = 3.0

    static func summary(
        for session: TrainingSession,
        allSessions: [TrainingSession] = []
    ) -> SessionSummary {
        let measured = session.shots.enumerated().map { index, shot in
            (shot: shot, ordinal: index + 1, metrics: MotionAnalysis.metrics(for: shot))
        }
        let holdValues = measured.map(\.metrics.holdRMS)
        let triggerValues = measured.map(\.metrics.triggerRMS)
        let followValues = measured.map(\.metrics.followThroughRMS)

        let holdRanks = ranks(for: holdValues, lowerIsBetter: true)
        let triggerRanks = ranks(for: triggerValues, lowerIsBetter: true)
        let followRanks = ranks(for: followValues, lowerIsBetter: true)
        let baseline = techniqueBaseline(for: session, in: allSessions)
        let scores = measured.map { item in
            componentScores(metrics: item.metrics, baseline: baseline)
        }
        let indices = scores.map(\.overall)
        let overallRanks = ranks(for: indices, lowerIsBetter: false)

        let standings = measured.indices.map { index in
            ShotStanding(
                shot: measured[index].shot,
                ordinal: measured[index].ordinal,
                metrics: measured[index].metrics,
                holdRank: holdRanks[index],
                triggerRank: triggerRanks[index],
                followThroughRank: followRanks[index],
                overallRank: overallRanks[index],
                holdScore: scores[index].hold,
                triggerScore: scores[index].trigger,
                followThroughScore: scores[index].follow,
                techniqueIndex: scores[index].overall
            )
        }

        return SessionSummary(
            standings: standings,
            hold: statistics(holdValues),
            trigger: statistics(triggerValues),
            followThrough: statistics(followValues),
            trend: trend(for: measured.map(\.metrics)),
            holdScore: mean(scores.map(\.hold), fallback: 50),
            triggerScore: mean(scores.map(\.trigger), fallback: 50),
            followThroughScore: mean(scores.map(\.follow), fallback: 50),
            techniqueIndex: mean(indices, fallback: 50),
            baseline: baseline
        )
    }

    static func progress(
        for session: TrainingSession,
        in allSessions: [TrainingSession]
    ) -> [TechniqueProgressPoint] {
        allSessions
            .filter {
                $0.effectiveProgram == session.effectiveProgram
                    && !$0.shots.isEmpty
            }
            .sorted { $0.startedAt < $1.startedAt }
            .map { candidate in
                let value = summary(
                    for: candidate,
                    allSessions: allSessions
                ).techniqueIndex
                return TechniqueProgressPoint(
                    id: candidate.id,
                    date: candidate.startedAt,
                    name: candidate.name,
                    techniqueIndex: value
                )
            }
    }

    static func previousComparableSessions(
        for session: TrainingSession,
        in allSessions: [TrainingSession],
        limit: Int = 5
    ) -> [TrainingSession] {
        allSessions
            .filter {
                $0.id != session.id
                    && $0.startedAt < session.startedAt
                    && $0.effectiveProgram == session.effectiveProgram
                    && $0.shots.count >= 3
            }
            .sorted { $0.startedAt > $1.startedAt }
            .prefix(limit)
            .map { $0 }
    }

    static func comparison(
        for session: TrainingSession,
        previousSessions: [TrainingSession]
    ) -> SessionComparison? {
        guard !session.shots.isEmpty, !previousSessions.isEmpty else {
            return nil
        }
        let context = previousSessions + [session]
        let current = summary(for: session, allSessions: context)
        let references = previousSessions.map {
            summary(for: $0, allSessions: context)
        }

        let holdReference = mean(references.map(\.hold.mean))
        let triggerReference = mean(references.map(\.trigger.mean))
        let followReference = mean(references.map(\.followThrough.mean))

        return SessionComparison(
            referenceSessionCount: previousSessions.count,
            hold: metricComparison(
                current: current.hold.mean,
                reference: holdReference
            ),
            trigger: metricComparison(
                current: current.trigger.mean,
                reference: triggerReference
            ),
            followThrough: metricComparison(
                current: current.followThrough.mean,
                reference: followReference
            )
        )
    }

    private static func ranks(
        for values: [Double],
        lowerIsBetter: Bool
    ) -> [Int] {
        values.map { value in
            1 + values.filter {
                lowerIsBetter ? $0 < value : $0 > value
            }.count
        }
    }

    private static func techniqueBaseline(
        for session: TrainingSession,
        in allSessions: [TrainingSession]
    ) -> TechniqueBaseline {
        var candidates = allSessions
        if !candidates.contains(where: { $0.id == session.id }) {
            candidates.append(session)
        }
        let sources = candidates
            .filter {
                $0.effectiveProgram == session.effectiveProgram
                    && $0.endedAt != nil
                    && $0.shots.count >= 3
            }
            .sorted { $0.startedAt < $1.startedAt }
            .prefix(baselineSessionTarget)
        let metrics = sources.flatMap { candidate in
            candidate.shots.map { MotionAnalysis.metrics(for: $0) }
        }
        return TechniqueBaseline(
            sourceSessionCount: sources.count,
            targetSessionCount: baselineSessionTarget,
            holdMedian: median(metrics.map(\.holdRMS)),
            triggerMedian: median(metrics.map(\.triggerRMS)),
            followThroughMedian: median(metrics.map(\.followThroughRMS))
        )
    }

    private static func componentScores(
        metrics: ShotMetrics,
        baseline: TechniqueBaseline
    ) -> (hold: Double, trigger: Double, follow: Double, overall: Double) {
        let hold = score(metrics.holdRMS, reference: baseline.holdMedian)
        let trigger = score(
            metrics.triggerRMS,
            reference: baseline.triggerMedian
        )
        let follow = score(
            metrics.followThroughRMS,
            reference: baseline.followThroughMedian
        )
        return (
            hold,
            trigger,
            follow,
            hold * holdWeight
                + trigger * triggerWeight
                + follow * followThroughWeight
        )
    }

    private static func score(_ value: Double, reference: Double) -> Double {
        guard value > 0, reference > 0 else { return 50 }
        return min(
            100,
            max(0, 100 / (1 + pow(value / reference, scoreExponent)))
        )
    }

    private static func statistics(_ values: [Double]) -> MetricStatistics {
        guard !values.isEmpty else { return .empty }
        let average = mean(values)
        let sorted = values.sorted()
        let middle = sorted.count / 2
        let median = sorted.count.isMultiple(of: 2)
            ? (sorted[middle - 1] + sorted[middle]) / 2
            : sorted[middle]
        let variance = values.reduce(0) {
            $0 + pow($1 - average, 2)
        } / Double(values.count)

        return MetricStatistics(
            mean: average,
            median: median,
            standardDeviation: sqrt(variance),
            best: sorted.first ?? 0,
            worst: sorted.last ?? 0
        )
    }

    private static func median(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        let sorted = values.sorted()
        let middle = sorted.count / 2
        return sorted.count.isMultiple(of: 2)
            ? (sorted[middle - 1] + sorted[middle]) / 2
            : sorted[middle]
    }

    private static func trend(for metrics: [ShotMetrics]) -> SessionTrend? {
        guard metrics.count >= 8 else { return nil }
        let size = min(10, metrics.count / 2)
        let first = Array(metrics.prefix(size))
        let last = Array(metrics.suffix(size))

        return SessionTrend(
            segmentSize: size,
            holdImprovementPercent: improvement(
                current: mean(last.map(\.holdRMS)),
                reference: mean(first.map(\.holdRMS))
            ),
            triggerImprovementPercent: improvement(
                current: mean(last.map(\.triggerRMS)),
                reference: mean(first.map(\.triggerRMS))
            ),
            followThroughImprovementPercent: improvement(
                current: mean(last.map(\.followThroughRMS)),
                reference: mean(first.map(\.followThroughRMS))
            )
        )
    }

    private static func metricComparison(
        current: Double,
        reference: Double
    ) -> MetricComparison {
        MetricComparison(
            current: current,
            reference: reference,
            improvementPercent: improvement(
                current: current,
                reference: reference
            )
        )
    }

    private static func improvement(
        current: Double,
        reference: Double
    ) -> Double {
        guard reference > 0 else { return 0 }
        return (reference - current) / reference * 100
    }

    private static func mean(
        _ values: [Double],
        fallback: Double = 0
    ) -> Double {
        guard !values.isEmpty else { return fallback }
        return values.reduce(0, +) / Double(values.count)
    }
}
