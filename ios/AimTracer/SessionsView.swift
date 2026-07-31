import Charts
import SwiftUI

struct SessionsView: View {
    @EnvironmentObject private var store: SessionStore

    var body: some View {
        Group {
            if store.sessions.isEmpty {
                ContentUnavailableView(
                    "Noch keine Sessions",
                    systemImage: "target",
                    description: Text("Starte im Live-Tab dein erstes Training.")
                )
            } else {
                List {
                    ForEach(store.sessions) { session in
                        NavigationLink {
                            SessionDetailView(sessionID: session.id)
                        } label: {
                            SessionRow(session: session)
                        }
                        .swipeActions {
                            Button("Löschen", role: .destructive) {
                                store.deleteSession(id: session.id)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Sessions")
    }
}

private struct SessionRow: View {
    let session: TrainingSession

    private var summary: SessionSummary {
        SessionAnalysis.summary(for: session)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(session.name)
                    .font(.headline)
                Spacer()
                progressBadge
            }
            HStack {
                Label(
                    "\(session.shots.count)",
                    systemImage: "scope"
                )
                Text(session.startedAt, style: .date)
                Text(session.startedAt, style: .time)
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            if !session.shots.isEmpty {
                HStack(spacing: 14) {
                    compactMetric("Halten", summary.hold.mean)
                    compactMetric("Abzug", summary.trigger.mean)
                    compactMetric("Nachhalten", summary.followThrough.mean)
                }
            }
        }
        .padding(.vertical, 3)
    }

    @ViewBuilder
    private var progressBadge: some View {
        if let planned = session.effectiveProgram.plannedShotCount {
            Text("\(session.shots.count)/\(planned)")
                .font(.caption.bold().monospacedDigit())
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.mint.opacity(0.15), in: Capsule())
        } else {
            Text(session.effectiveProgram.title)
                .font(.caption.bold())
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.secondary.opacity(0.12), in: Capsule())
        }
    }

    private func compactMetric(_ title: String, _ value: Double) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(value.formatted(.number.precision(.fractionLength(1))))
                .font(.caption.bold().monospacedDigit())
        }
    }
}

private enum ShotSortOrder: String, CaseIterable, Identifiable {
    case chronological = "Reihenfolge"
    case ranking = "Ranking"

    var id: String { rawValue }
}

private struct ExportLinks {
    let csv: URL
    let pdf: URL
}

private struct SessionDetailView: View {
    @EnvironmentObject private var store: SessionStore
    let sessionID: UUID
    @State private var sortOrder: ShotSortOrder = .chronological
    @State private var exportLinks: ExportLinks?
    @State private var exportError: String?

    private var session: TrainingSession? {
        store.sessions.first { $0.id == sessionID }
    }

    var body: some View {
        Group {
            if let session {
                sessionList(session)
            } else {
                ContentUnavailableView(
                    "Session nicht gefunden",
                    systemImage: "exclamationmark.triangle"
                )
            }
        }
        .navigationTitle(session?.name ?? "Session")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                exportMenu
            }
        }
        .task(id: session?.shots.count) {
            prepareExports()
        }
        .alert(
            "Export nicht möglich",
            isPresented: Binding(
                get: { exportError != nil },
                set: { if !$0 { exportError = nil } }
            )
        ) {
            Button("OK") { exportError = nil }
        } message: {
            Text(exportError ?? "")
        }
    }

    private func sessionList(_ session: TrainingSession) -> some View {
        let summary = SessionAnalysis.summary(for: session)
        let previous = SessionAnalysis.previousComparableSessions(
            for: session,
            in: store.sessions
        )
        let comparison = SessionAnalysis.comparison(
            for: session,
            previousSessions: previous
        )
        let standings = sortedStandings(summary.standings)

        return List {
            Section {
                SessionOverview(session: session)
            }

            if !session.shots.isEmpty {
                Section("Mittelwerte") {
                    SessionMetricSummary(summary: summary)
                }

                Section("Verlauf") {
                    SessionTrendChart(standings: summary.standings)
                        .frame(height: 230)
                        .listRowInsets(
                            EdgeInsets(top: 12, leading: 8, bottom: 12, trailing: 8)
                        )
                    if let trend = summary.trend {
                        TrendSummary(trend: trend)
                    }
                }

                Section("Vergleich mit früheren Sessions") {
                    if let comparison {
                        Text(
                            "Mittelwert der letzten "
                                + "\(comparison.referenceSessionCount) "
                                + "vergleichbaren "
                                + "\(session.effectiveProgram.title)-Sessions"
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        ComparisonRow(
                            title: "Ruhig halten",
                            comparison: comparison.hold
                        )
                        ComparisonRow(
                            title: "Abzugsverhalten",
                            comparison: comparison.trigger
                        )
                        ComparisonRow(
                            title: "Nachhalten",
                            comparison: comparison.followThrough
                        )
                    } else {
                        Text(
                            "Sobald eine weitere Session desselben Programms "
                                + "vorliegt, erscheint hier der Langzeitvergleich."
                        )
                        .font(.callout)
                        .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Picker("Sortierung", selection: $sortOrder) {
                        ForEach(ShotSortOrder.allCases) {
                            Text($0.rawValue).tag($0)
                        }
                    }
                    .pickerStyle(.segmented)

                    ForEach(standings) { standing in
                        NavigationLink {
                            ShotDetailView(
                                shot: standing.shot,
                                standing: standing,
                                shotCount: session.shots.count
                            )
                        } label: {
                            ShotStandingRow(
                                standing: standing,
                                shotCount: session.shots.count
                            )
                        }
                        .swipeActions {
                            Button("Löschen", role: .destructive) {
                                store.deleteShot(
                                    sessionID: session.id,
                                    shotID: standing.shot.id
                                )
                            }
                        }
                    }
                } header: {
                    Text("Schussranking")
                } footer: {
                    Text(
                        "Der Vergleichsindex kombiniert die drei relativen "
                            + "Platzierungen gleichgewichtet. Er ist keine "
                            + "Ringzahl und nur innerhalb dieser Session gültig. "
                            + "Fehlauslösungen lassen sich nach links wischen "
                            + "und aus der Session löschen."
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var exportMenu: some View {
        Menu {
            if let exportLinks {
                ShareLink(
                    item: exportLinks.csv,
                    preview: SharePreview(
                        "AimTracer Excel-Tabelle",
                        image: Image(systemName: "tablecells")
                    )
                ) {
                    Label("Für Excel exportieren", systemImage: "tablecells")
                }
                ShareLink(
                    item: exportLinks.pdf,
                    preview: SharePreview(
                        "AimTracer PDF-Bericht",
                        image: Image(systemName: "doc.richtext")
                    )
                ) {
                    Label("PDF-Bericht exportieren", systemImage: "doc.richtext")
                }
            } else {
                Label("Export wird vorbereitet", systemImage: "clock")
            }
        } label: {
            Image(systemName: "square.and.arrow.up")
        }
        .disabled(session?.shots.isEmpty != false || exportLinks == nil)
    }

    private func sortedStandings(
        _ standings: [ShotStanding]
    ) -> [ShotStanding] {
        switch sortOrder {
        case .chronological:
            standings.sorted { $0.ordinal < $1.ordinal }
        case .ranking:
            standings.sorted {
                if $0.overallRank == $1.overallRank {
                    return $0.ordinal < $1.ordinal
                }
                return $0.overallRank < $1.overallRank
            }
        }
    }

    private func prepareExports() {
        exportLinks = nil
        guard let session, !session.shots.isEmpty else { return }
        let previous = SessionAnalysis.previousComparableSessions(
            for: session,
            in: store.sessions
        )
        do {
            exportLinks = ExportLinks(
                csv: try SessionExporter.makeExcelCSV(
                    session: session,
                    previousSessions: previous
                ),
                pdf: try SessionExporter.makePDF(
                    session: session,
                    previousSessions: previous
                )
            )
        } catch {
            exportError = error.localizedDescription
        }
    }
}

private struct SessionOverview: View {
    let session: TrainingSession

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(session.effectiveProgram.title)
                        .font(.title2.bold())
                    Text(
                        session.startedAt.formatted(
                            date: .abbreviated,
                            time: .shortened
                        )
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("\(session.shots.count)")
                        .font(.largeTitle.bold().monospacedDigit())
                    Text(
                        session.effectiveProgram.plannedShotCount
                            .map { "von \($0) Schüssen" }
                            ?? "Schüsse"
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }
            if let planned = session.effectiveProgram.plannedShotCount {
                ProgressView(
                    value: min(Double(session.shots.count), Double(planned)),
                    total: Double(planned)
                )
                .tint(.mint)
            }
        }
        .padding(.vertical, 5)
    }
}

private struct SessionMetricSummary: View {
    let summary: SessionSummary

    var body: some View {
        Grid(horizontalSpacing: 9) {
            GridRow {
                metric("Ruhig halten", summary.hold)
                metric("Abzug", summary.trigger)
                metric("Nachhalten", summary.followThrough)
            }
        }
        .padding(.vertical, 6)
    }

    private func metric(
        _ title: String,
        _ statistics: MetricStatistics
    ) -> some View {
        VStack(spacing: 5) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(
                statistics.mean.formatted(
                    .number.precision(.fractionLength(2))
                )
            )
            .font(.title3.bold().monospacedDigit())
            Text(
                "Best "
                    + statistics.best.formatted(
                        .number.precision(.fractionLength(2))
                    )
            )
            .font(.caption2.monospacedDigit())
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct SessionTrendChart: View {
    let standings: [ShotStanding]

    var body: some View {
        Chart {
            ForEach(standings) { standing in
                LineMark(
                    x: .value("Schuss", standing.ordinal),
                    y: .value("°/s RMS", standing.metrics.holdRMS),
                    series: .value("Kennwert", "Halten")
                )
                .foregroundStyle(by: .value("Kennwert", "Halten"))

                LineMark(
                    x: .value("Schuss", standing.ordinal),
                    y: .value("°/s RMS", standing.metrics.triggerRMS),
                    series: .value("Kennwert", "Abzug")
                )
                .foregroundStyle(by: .value("Kennwert", "Abzug"))

                LineMark(
                    x: .value(
                        "Schuss",
                        standing.ordinal
                    ),
                    y: .value(
                        "°/s RMS",
                        standing.metrics.followThroughRMS
                    ),
                    series: .value("Kennwert", "Nachhalten")
                )
                .foregroundStyle(by: .value("Kennwert", "Nachhalten"))
            }
        }
        .chartForegroundStyleScale([
            "Halten": Color.mint,
            "Abzug": Color.orange,
            "Nachhalten": Color.blue
        ])
        .chartXAxisLabel("Schuss")
        .chartYAxisLabel("°/s RMS")
        .chartLegend(position: .bottom, spacing: 12)
    }
}

private struct TrendSummary: View {
    let trend: SessionTrend

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(
                "Letzte \(trend.segmentSize) gegen erste "
                    + "\(trend.segmentSize) Schüsse"
            )
            .font(.caption)
            .foregroundStyle(.secondary)
            HStack {
                trendValue("Halten", trend.holdImprovementPercent)
                trendValue("Abzug", trend.triggerImprovementPercent)
                trendValue("Nachhalten", trend.followThroughImprovementPercent)
            }
        }
    }

    private func trendValue(_ title: String, _ value: Double) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(signedPercent(value))
                .font(.headline.monospacedDigit())
                .foregroundStyle(value >= 0 ? .mint : .red)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ComparisonRow: View {
    let title: String
    let comparison: MetricComparison

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(
                    comparison.current.formatted(
                        .number.precision(.fractionLength(2))
                    )
                        + " vs. "
                        + comparison.reference.formatted(
                            .number.precision(.fractionLength(2))
                        )
                        + " °/s"
                )
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }
            Spacer()
            Text(signedPercent(comparison.improvementPercent))
                .font(.headline.monospacedDigit())
                .foregroundStyle(
                    comparison.improvementPercent >= 0 ? .mint : .red
                )
        }
    }
}

private struct ShotStandingRow: View {
    let standing: ShotStanding
    let shotCount: Int

    var body: some View {
        HStack(spacing: 12) {
            rankBadge
            VStack(alignment: .leading, spacing: 4) {
                Text("Schuss \(standing.ordinal)")
                    .font(.headline)
                HStack(spacing: 10) {
                    value("H", standing.metrics.holdRMS)
                    value("A", standing.metrics.triggerRMS)
                    value("N", standing.metrics.followThroughRMS)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(
                    standing.comparisonIndex.formatted(
                        .number.precision(.fractionLength(0))
                    )
                )
                .font(.headline.monospacedDigit())
                Text("Vergleich")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 3)
    }

    private var rankBadge: some View {
        ZStack {
            Circle()
                .fill(rankColor.opacity(0.16))
                .frame(width: 42, height: 42)
            Text("#\(standing.overallRank)")
                .font(.caption.bold().monospacedDigit())
                .foregroundStyle(rankColor)
        }
        .accessibilityLabel(
            "Rang \(standing.overallRank) von \(shotCount)"
        )
    }

    private var rankColor: Color {
        switch standing.overallRank {
        case 1: .mint
        case 2, 3: .orange
        default: .secondary
        }
    }

    private func value(_ title: String, _ value: Double) -> some View {
        Text(
            "\(title) "
                + value.formatted(.number.precision(.fractionLength(1)))
        )
        .font(.caption.monospacedDigit())
        .foregroundStyle(.secondary)
    }
}

private struct ShotDetailView: View {
    let shot: ShotCapture
    let standing: ShotStanding?
    let shotCount: Int

    init(
        shot: ShotCapture,
        standing: ShotStanding? = nil,
        shotCount: Int = 0
    ) {
        self.shot = shot
        self.standing = standing
        self.shotCount = shotCount
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let standing {
                    ShotRankingDetail(
                        standing: standing,
                        shotCount: shotCount
                    )
                }
                TraceCard(shot: shot, liveSamples: [])
                ShotMetricsView(shot: shot)
                Grid(alignment: .leading, horizontalSpacing: 24) {
                    GridRow {
                        detail("Audio-Peak", "\(shot.audioPeak)")
                        detail("Accel-Peak", "\(shot.accelerationPeak)")
                    }
                    GridRow {
                        detail("Gyro-Peak", "\(shot.gyroPeak)")
                        detail("Samples", "\(shot.samples.count)")
                    }
                }
                .padding()
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
            }
            .padding()
        }
        .navigationTitle(
            standing.map { "Schuss \($0.ordinal)" }
                ?? "Schuss #\(shot.deviceShotID)"
        )
        .navigationBarTitleDisplayMode(.inline)
    }

    private func detail(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.headline.monospacedDigit())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ShotRankingDetail: View {
    let standing: ShotStanding
    let shotCount: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Session-Ranking")
                    .font(.headline)
                Spacer()
                Text("#\(standing.overallRank) von \(shotCount)")
                    .font(.title3.bold().monospacedDigit())
                    .foregroundStyle(.mint)
            }
            HStack {
                rank("Ruhig halten", standing.holdRank)
                rank("Abzug", standing.triggerRank)
                rank("Nachhalten", standing.followThroughRank)
            }
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }

    private func rank(_ title: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text("\(value)/\(shotCount)")
                .font(.headline.monospacedDigit())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private func signedPercent(_ value: Double) -> String {
    let prefix = value > 0 ? "+" : ""
    return prefix
        + value.formatted(.number.precision(.fractionLength(1)))
        + " %"
}
