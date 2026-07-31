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
    let comparisonIndex: Double
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
}

enum SessionAnalysis {
    static func summary(for session: TrainingSession) -> SessionSummary {
        let measured = session.shots.enumerated().map { index, shot in
            (shot: shot, ordinal: index + 1, metrics: MotionAnalysis.metrics(for: shot))
        }
        let holdValues = measured.map(\.metrics.holdRMS)
        let triggerValues = measured.map(\.metrics.triggerRMS)
        let followValues = measured.map(\.metrics.followThroughRMS)

        let holdRanks = ranks(for: holdValues, lowerIsBetter: true)
        let triggerRanks = ranks(for: triggerValues, lowerIsBetter: true)
        let followRanks = ranks(for: followValues, lowerIsBetter: true)
        let count = measured.count

        let indices = measured.indices.map { index in
            let hold = percentile(rank: holdRanks[index], count: count)
            let trigger = percentile(rank: triggerRanks[index], count: count)
            let follow = percentile(rank: followRanks[index], count: count)
            // CALIBRATION: Deliberately equal weighting until field data shows
            // that a different weighting is more useful for the shooter.
            return (hold + trigger + follow) / 3.0
        }
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
                comparisonIndex: indices[index]
            )
        }

        return SessionSummary(
            standings: standings,
            hold: statistics(holdValues),
            trigger: statistics(triggerValues),
            followThrough: statistics(followValues),
            trend: trend(for: measured.map(\.metrics))
        )
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
        let current = summary(for: session)
        let references = previousSessions.map { summary(for: $0) }

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

    private static func percentile(rank: Int, count: Int) -> Double {
        guard count > 1 else { return 100 }
        return 100.0 * Double(count - rank) / Double(count - 1)
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

    private static func mean(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        return values.reduce(0, +) / Double(values.count)
    }
}
