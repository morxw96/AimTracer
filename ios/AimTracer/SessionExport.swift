import Foundation
import UIKit

enum SessionExportError: LocalizedError {
    case noShots

    var errorDescription: String? {
        switch self {
        case .noShots:
            L10n.text("Die Session enthält noch keine Schüsse.")
        }
    }
}

enum SessionExporter {
    static func makeExcelCSV(
        session: TrainingSession,
        previousSessions: [TrainingSession]
    ) throws -> URL {
        guard !session.shots.isEmpty else { throw SessionExportError.noShots }
        let summary = SessionAnalysis.summary(for: session)
        let comparison = SessionAnalysis.comparison(
            for: session,
            previousSessions: previousSessions
        )
        var rows: [[String]] = [
            ["AimTracer Trainingsprotokoll"],
            ["Session", session.name],
            ["Programm", session.effectiveProgram.title],
            ["Beginn", exportDateFormatter.string(from: session.startedAt)],
            ["Ende", session.endedAt.map(exportDateFormatter.string) ?? ""],
            ["Schüsse", "\(session.shots.count)"],
            [
                "Geplant",
                session.effectiveProgram.plannedShotCount.map(String.init) ?? ""
            ],
            [
                "Meyton-Gesamtergebnis",
                session.meytonScore.map(score) ?? ""
            ],
            [],
            [
                "Kennwert",
                "Mittelwert (°/s RMS)",
                "Median",
                "Standardabweichung",
                "Bestwert",
                "Schlechtester Wert"
            ],
            statisticsRow("Ruhig halten", summary.hold),
            statisticsRow("Abzugsverhalten", summary.trigger),
            statisticsRow("Nachhalten", summary.followThrough)
        ]

        if let comparison {
            rows += [
                [],
                [
                    "Vergleich",
                    "Aktuelle Session",
                    "Vorherige Sessions",
                    "Veränderung"
                ],
                comparisonRow("Ruhig halten", comparison.hold),
                comparisonRow("Abzugsverhalten", comparison.trigger),
                comparisonRow("Nachhalten", comparison.followThrough)
            ]
        }

        rows += [
            [],
            [
                "Schuss",
                "Geräte-ID",
                "Zeit",
                "Gesamtrang",
                "Vergleichsindex",
                "Ruhig halten (°/s RMS)",
                "Rang Halten",
                "Abzugsverhalten (°/s RMS)",
                "Rang Abzug",
                "Nachhalten (°/s RMS)",
                "Rang Nachhalten",
                "Audio-Peak",
                "Accel-Peak",
                "Gyro-Peak"
            ]
        ]

        rows += summary.standings.map { standing in
            [
                "\(standing.ordinal)",
                "\(standing.shot.deviceShotID)",
                timeFormatter.string(from: standing.shot.receivedAt),
                "\(standing.overallRank)",
                number(standing.comparisonIndex, digits: 1),
                number(standing.metrics.holdRMS),
                "\(standing.holdRank)",
                number(standing.metrics.triggerRMS),
                "\(standing.triggerRank)",
                number(standing.metrics.followThroughRMS),
                "\(standing.followThroughRank)",
                "\(standing.shot.audioPeak)",
                "\(standing.shot.accelerationPeak)",
                "\(standing.shot.gyroPeak)"
            ]
        }

        rows += [
            [],
            [
                "Hinweis",
                "Niedrigere RMS-Werte bedeuten weniger Winkelbewegung. "
                    + "Rang und Vergleichsindex beziehen sich nur auf die "
                    + "Schüsse dieser Session und sind keine Ringzahl."
            ]
        ]

        let contents = rows.map {
            $0.map { csvField(L10n.text($0)) }.joined(separator: ";")
        }.joined(separator: "\r\n")
        let data = Data(("\u{FEFF}" + contents).utf8)
        let url = try exportURL(
            session: session,
            suffix: "Excel",
            extension: "csv"
        )
        try data.write(to: url, options: .atomic)
        return url
    }

    static func makePDF(
        session: TrainingSession,
        previousSessions: [TrainingSession]
    ) throws -> URL {
        guard !session.shots.isEmpty else { throw SessionExportError.noShots }
        let url = try exportURL(
            session: session,
            suffix: L10n.text("Bericht"),
            extension: "pdf"
        )
        let report = PDFSessionReport(
            session: session,
            previousSessions: previousSessions
        )
        try report.write(to: url)
        return url
    }

    static func makeRawJSON(session: TrainingSession) throws -> URL {
        guard !session.shots.isEmpty else { throw SessionExportError.noShots }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [
            .prettyPrinted,
            .sortedKeys,
            .withoutEscapingSlashes
        ]
        let data = try encoder.encode(RawSessionExport(session: session))
        let url = try exportURL(
            session: session,
            suffix: L10n.text("Rohdaten"),
            extension: "json"
        )
        try data.write(to: url, options: .atomic)
        return url
    }

    private static func statisticsRow(
        _ title: String,
        _ statistics: MetricStatistics
    ) -> [String] {
        [
            title,
            number(statistics.mean),
            number(statistics.median),
            number(statistics.standardDeviation),
            number(statistics.best),
            number(statistics.worst)
        ]
    }

    private static func comparisonRow(
        _ title: String,
        _ comparison: MetricComparison
    ) -> [String] {
        [
            title,
            number(comparison.current),
            number(comparison.reference),
            percent(comparison.improvementPercent)
        ]
    }

    private static func csvField(_ value: String) -> String {
        guard value.contains(";")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")
        else { return value }
        return "\"\(value.replacingOccurrences(of: "\"", with: "\"\""))\""
    }

    private static func number(_ value: Double, digits: Int = 3) -> String {
        String(
            format: "%.\(digits)f",
            locale: Locale.current,
            value
        )
    }

    private static func percent(_ value: Double) -> String {
        let prefix = value > 0 ? "+" : ""
        return prefix + number(value, digits: 1) + " %"
    }

    private static func score(_ value: Double) -> String {
        number(value, digits: value.rounded() == value ? 0 : 1)
    }

    private static func exportURL(
        session: TrainingSession,
        suffix: String,
        extension fileExtension: String
    ) throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("AimTracer-Exporte", isDirectory: true)
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
        let safeName = session.name
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "-")
        return directory.appendingPathComponent(
            "\(safeName)-\(suffix).\(fileExtension)"
        )
    }

    private static let exportDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateStyle = .short
        formatter.timeStyle = .medium
        return formatter
    }()

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()
}

private struct RawSessionExport: Encodable {
    let schemaVersion = 1
    let format = "aimtracer-raw-session"
    let exportedAt = Date()
    let mounting = RawMountingMetadata()
    let session: RawTrainingSession

    init(session: TrainingSession) {
        self.session = RawTrainingSession(session: session)
    }
}

private struct RawMountingMetadata: Encodable {
    let id = MotionAnalysis.mountingProfileID
    let boardOrientation =
        "PCB underside up; component and IMU side down; USB-C toward shooter"
    let rawSampleAxes = "Unmodified LSM6DS3TR-C sensor coordinates"
    let analysisAxes = [
        "roll": "gy",
        "horizontalRight": "-gz",
        "verticalUp": "gx"
    ]
}

private struct RawTrainingSession: Encodable {
    let id: UUID
    let startedAt: Date
    let endedAt: Date?
    let name: String
    let program: String
    let plannedShotCount: Int?
    let meytonScore: Double?
    let shots: [RawShotCapture]

    init(session: TrainingSession) {
        id = session.id
        startedAt = session.startedAt
        endedAt = session.endedAt
        name = session.name
        program = session.effectiveProgram.rawValue
        plannedShotCount = session.effectiveProgram.plannedShotCount
        meytonScore = session.meytonScore
        shots = session.shots.map(RawShotCapture.init)
    }
}

private struct RawShotCapture: Encodable {
    let id: UUID
    let deviceShotId: UInt16
    let receivedAt: Date
    let triggerUptimeMs: UInt32
    let sampleRateHz: UInt16
    let triggerIndex: UInt16
    let audioPeak: UInt16
    let accelerationPeak: UInt16
    let gyroPeak: UInt16
    let samples: [RawMotionSample]

    init(shot: ShotCapture) {
        id = shot.id
        deviceShotId = shot.deviceShotID
        receivedAt = shot.receivedAt
        triggerUptimeMs = shot.triggerUptimeMs
        sampleRateHz = shot.sampleRateHz
        triggerIndex = shot.triggerIndex
        audioPeak = shot.audioPeak
        accelerationPeak = shot.accelerationPeak
        gyroPeak = shot.gyroPeak
        samples = shot.samples.map {
            RawMotionSample(
                sample: $0,
                triggerIndex: shot.triggerIndex,
                sampleRateHz: shot.sampleRateHz
            )
        }
    }
}

private struct RawMotionSample: Encodable {
    let index: UInt16
    let relativeTimeMs: Double
    let gx: Int16
    let gy: Int16
    let gz: Int16
    let ax: Int16
    let ay: Int16
    let az: Int16
    let microphonePeak: UInt16
    let isTrigger: Bool

    init(
        sample: MotionSample,
        triggerIndex: UInt16,
        sampleRateHz: UInt16
    ) {
        index = sample.index
        relativeTimeMs = sampleRateHz == 0 ? 0 :
            (Double(sample.index) - Double(triggerIndex))
                * 1_000.0 / Double(sampleRateHz)
        gx = sample.gx
        gy = sample.gy
        gz = sample.gz
        ax = sample.ax
        ay = sample.ay
        az = sample.az
        microphonePeak = sample.microphonePeak
        isTrigger = sample.isTrigger
    }
}

private struct PDFSessionReport {
    let session: TrainingSession
    let previousSessions: [TrainingSession]

    private let page = CGRect(x: 0, y: 0, width: 595.2, height: 841.8)
    private let margin: CGFloat = 36
    private let accent = UIColor(
        red: 0.05,
        green: 0.55,
        blue: 0.47,
        alpha: 1
    )
    private let muted = UIColor(
        red: 0.35,
        green: 0.39,
        blue: 0.43,
        alpha: 1
    )

    func write(to url: URL) throws {
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [
            kCGPDFContextTitle as String: "AimTracer - \(session.name)",
            kCGPDFContextCreator as String: "AimTracer"
        ]
        let renderer = UIGraphicsPDFRenderer(bounds: page, format: format)
        try renderer.writePDF(to: url) { rendererContext in
            drawOverview(in: rendererContext)
            drawShotTable(in: rendererContext)
        }
    }

    private func drawOverview(in rendererContext: UIGraphicsPDFRendererContext) {
        rendererContext.beginPage()
        let summary = SessionAnalysis.summary(for: session)
        let comparison = SessionAnalysis.comparison(
            for: session,
            previousSessions: previousSessions
        )

        drawText(
            "AimTracer",
            in: CGRect(x: margin, y: 34, width: 180, height: 30),
            font: .boldSystemFont(ofSize: 25),
            color: accent
        )
        drawText(
            "TRAININGSPROTOKOLL",
            in: CGRect(x: margin, y: 65, width: 240, height: 16),
            font: .boldSystemFont(ofSize: 9),
            color: muted
        )
        drawText(
            session.name,
            in: CGRect(
                x: margin,
                y: 96,
                width: page.width - margin * 2,
                height: 30
            ),
            font: .boldSystemFont(ofSize: 19),
            color: .label
        )

        let planned = session.effectiveProgram.plannedShotCount
            .map { " / \($0)" } ?? ""
        drawText(
            L10n.format(
                "%@  •  %d%@ Schüsse  •  %@",
                session.effectiveProgram.title,
                session.shots.count,
                planned,
                Self.dateFormatter.string(from: session.startedAt)
            ),
            in: CGRect(
                x: margin,
                y: 128,
                width: page.width - margin * 2,
                height: 20
            ),
            font: .systemFont(ofSize: 10),
            color: muted
        )

        let cardY: CGFloat = 172
        let gap: CGFloat = 10
        let cardWidth = (page.width - margin * 2 - gap * 2) / 3
        drawMetricCard(
            title: "RUHIG HALTEN",
            statistics: summary.hold,
            x: margin,
            y: cardY,
            width: cardWidth
        )
        drawMetricCard(
            title: "ABZUGSVERHALTEN",
            statistics: summary.trigger,
            x: margin + cardWidth + gap,
            y: cardY,
            width: cardWidth
        )
        drawMetricCard(
            title: "NACHHALTEN",
            statistics: summary.followThrough,
            x: margin + (cardWidth + gap) * 2,
            y: cardY,
            width: cardWidth
        )

        drawTrendChart(
            standings: summary.standings,
            in: CGRect(
                x: margin,
                y: 286,
                width: page.width - margin * 2,
                height: 205
            )
        )

        var comparisonY: CGFloat = 526
        drawText(
            "Vergleich",
            in: CGRect(
                x: margin,
                y: comparisonY,
                width: 180,
                height: 22
            ),
            font: .boldSystemFont(ofSize: 15),
            color: .label
        )
        comparisonY += 27

        if let comparison {
            drawText(
                L10n.format(
                    "Gegen den Mittelwert der letzten %d vergleichbaren %@-Sessions",
                    comparison.referenceSessionCount,
                    session.effectiveProgram.title
                ),
                in: CGRect(
                    x: margin,
                    y: comparisonY,
                    width: page.width - margin * 2,
                    height: 18
                ),
                font: .systemFont(ofSize: 9),
                color: muted
            )
            comparisonY += 25
            drawComparison(
                title: "Ruhig halten",
                comparison: comparison.hold,
                y: comparisonY
            )
            drawComparison(
                title: "Abzugsverhalten",
                comparison: comparison.trigger,
                y: comparisonY + 27
            )
            drawComparison(
                title: "Nachhalten",
                comparison: comparison.followThrough,
                y: comparisonY + 54
            )
        } else {
            drawText(
                "Noch keine frühere Session desselben Programms vorhanden.",
                in: CGRect(
                    x: margin,
                    y: comparisonY,
                    width: page.width - margin * 2,
                    height: 18
                ),
                font: .systemFont(ofSize: 10),
                color: muted
            )
        }

        let meytonResult = session.meytonScore.map {
            L10n.format(
                "Meyton-Gesamtergebnis: %@ Ringe",
                Self.score($0)
            )
        } ?? L10n.text("Meyton-Gesamtergebnis: nicht eingetragen")
        drawText(
            meytonResult,
            in: CGRect(
                x: margin,
                y: 690,
                width: page.width - margin * 2,
                height: 24
            ),
            font: .boldSystemFont(ofSize: 11),
            color: .label
        )
        drawText(
            L10n.text(
                "AimTracer misst relative Winkelbewegung; RMS, Rang und "
                    + "Vergleichsindex sind keine Ringzahl."
            ),
            in: CGRect(
                x: margin,
                y: 738,
                width: page.width - margin * 2,
                height: 42
            ),
            font: .systemFont(ofSize: 8),
            color: muted
        )
        drawFooter(pageNumber: 1)
    }

    private func drawMetricCard(
        title: String,
        statistics: MetricStatistics,
        x: CGFloat,
        y: CGFloat,
        width: CGFloat
    ) {
        let rect = CGRect(x: x, y: y, width: width, height: 84)
        let path = UIBezierPath(roundedRect: rect, cornerRadius: 8)
        UIColor.systemGray6.setFill()
        path.fill()
        drawText(
            title,
            in: CGRect(x: x + 10, y: y + 10, width: width - 20, height: 14),
            font: .boldSystemFont(ofSize: 8),
            color: muted
        )
        drawText(
            Self.number(statistics.mean, digits: 2),
            in: CGRect(x: x + 10, y: y + 29, width: width - 20, height: 27),
            font: .boldSystemFont(ofSize: 20),
            color: .label
        )
        drawText(
            L10n.format(
                "°/s RMS  •  Best %@",
                Self.number(statistics.best, digits: 2)
            ),
            in: CGRect(x: x + 10, y: y + 59, width: width - 20, height: 13),
            font: .systemFont(ofSize: 7.5),
            color: muted
        )
    }

    private func drawTrendChart(
        standings: [ShotStanding],
        in rect: CGRect
    ) {
        drawText(
            "Verlauf je Schuss",
            in: CGRect(x: rect.minX, y: rect.minY, width: 200, height: 22),
            font: .boldSystemFont(ofSize: 15),
            color: .label
        )
        let plot = CGRect(
            x: rect.minX + 34,
            y: rect.minY + 35,
            width: rect.width - 42,
            height: rect.height - 60
        )
        let context = UIGraphicsGetCurrentContext()!
        context.setStrokeColor(UIColor.systemGray4.cgColor)
        context.setLineWidth(0.5)
        for index in 0...4 {
            let y = plot.minY + plot.height * CGFloat(index) / 4
            context.move(to: CGPoint(x: plot.minX, y: y))
            context.addLine(to: CGPoint(x: plot.maxX, y: y))
        }
        context.strokePath()

        guard standings.count > 1 else {
            drawText(
                "Mindestens zwei Schüsse für den Verlauf erforderlich.",
                in: plot,
                font: .systemFont(ofSize: 9),
                color: muted
            )
            return
        }
        let allValues = standings.flatMap {
            [
                $0.metrics.holdRMS,
                $0.metrics.triggerRMS,
                $0.metrics.followThroughRMS
            ]
        }
        let maximum = max(allValues.max() ?? 1, 0.1)
        drawLine(
            values: standings.map(\.metrics.holdRMS),
            maximum: maximum,
            color: accent,
            in: plot
        )
        drawLine(
            values: standings.map(\.metrics.triggerRMS),
            maximum: maximum,
            color: .systemOrange,
            in: plot
        )
        drawLine(
            values: standings.map(\.metrics.followThroughRMS),
            maximum: maximum,
            color: .systemBlue,
            in: plot
        )
        drawLegend("Halten", color: accent, x: plot.minX, y: plot.maxY + 9)
        drawLegend(
            "Abzug",
            color: .systemOrange,
            x: plot.minX + 90,
            y: plot.maxY + 9
        )
        drawLegend(
            "Nachhalten",
            color: .systemBlue,
            x: plot.minX + 175,
            y: plot.maxY + 9
        )
    }

    private func drawLine(
        values: [Double],
        maximum: Double,
        color: UIColor,
        in rect: CGRect
    ) {
        guard values.count > 1 else { return }
        let path = UIBezierPath()
        for (index, value) in values.enumerated() {
            let x = rect.minX
                + rect.width * CGFloat(index) / CGFloat(values.count - 1)
            let y = rect.maxY
                - rect.height * CGFloat(value / maximum)
            let point = CGPoint(x: x, y: y)
            index == 0 ? path.move(to: point) : path.addLine(to: point)
        }
        color.setStroke()
        path.lineWidth = 1.6
        path.stroke()
    }

    private func drawLegend(
        _ title: String,
        color: UIColor,
        x: CGFloat,
        y: CGFloat
    ) {
        let context = UIGraphicsGetCurrentContext()!
        context.setFillColor(color.cgColor)
        context.fillEllipse(in: CGRect(x: x, y: y + 2, width: 7, height: 7))
        drawText(
            title,
            in: CGRect(x: x + 11, y: y, width: 80, height: 13),
            font: .systemFont(ofSize: 7.5),
            color: muted
        )
    }

    private func drawComparison(
        title: String,
        comparison: MetricComparison,
        y: CGFloat
    ) {
        let improvement = comparison.improvementPercent
        let color: UIColor = improvement >= 0 ? accent : .systemRed
        drawText(
            title,
            in: CGRect(x: margin, y: y, width: 150, height: 18),
            font: .systemFont(ofSize: 10),
            color: .label
        )
        drawText(
            "\(Self.number(comparison.current, digits: 2)) vs. "
                + "\(Self.number(comparison.reference, digits: 2)) °/s",
            in: CGRect(x: margin + 158, y: y, width: 180, height: 18),
            font: .monospacedDigitSystemFont(ofSize: 9, weight: .regular),
            color: muted
        )
        let prefix = improvement > 0 ? "+" : ""
        drawText(
            "\(prefix)\(Self.number(improvement, digits: 1)) %",
            in: CGRect(
                x: page.width - margin - 80,
                y: y,
                width: 80,
                height: 18
            ),
            font: .boldSystemFont(ofSize: 10),
            color: color,
            alignment: .right
        )
    }

    private func drawShotTable(
        in rendererContext: UIGraphicsPDFRendererContext
    ) {
        let standings = SessionAnalysis.summary(for: session).standings
        let columns: [(String, CGFloat)] = [
            ("Nr.", 25),
            ("Zeit", 58),
            ("Rang", 40),
            ("Index", 55),
            ("Halten", 68),
            ("Abzug", 68),
            ("Nachhalten", 68),
            ("Audio", 60)
        ]
        let rowHeight: CGFloat = 17
        let rowsPerPage = 40
        var rowIndex = 0
        var pageNumber = 2

        repeat {
            rendererContext.beginPage()
            drawText(
                "Schussübersicht",
                in: CGRect(
                    x: margin,
                    y: 34,
                    width: page.width - margin * 2,
                    height: 26
                ),
                font: .boldSystemFont(ofSize: 18),
                color: .label
            )
            drawText(
                session.name,
                in: CGRect(
                    x: margin,
                    y: 61,
                    width: page.width - margin * 2,
                    height: 17
                ),
                font: .systemFont(ofSize: 9),
                color: muted
            )
            var y: CGFloat = 96
            drawTableRow(
                columns.map(\.0),
                columns: columns,
                y: y,
                height: 21,
                header: true
            )
            y += 21

            // Exactly 40 rows occupy y=117...797, leaving 16 pt before
            // the footer. This keeps a complete LP40 table on one A4 page.
            let pageEnd = min(rowIndex + rowsPerPage, standings.count)
            while rowIndex < pageEnd {
                let standing = standings[rowIndex]
                drawTableRow(
                    [
                        "\(standing.ordinal)",
                        Self.timeFormatter.string(
                            from: standing.shot.receivedAt
                        ),
                        "\(standing.overallRank)",
                        Self.number(standing.comparisonIndex, digits: 1),
                        Self.number(standing.metrics.holdRMS, digits: 2),
                        Self.number(standing.metrics.triggerRMS, digits: 2),
                        Self.number(
                            standing.metrics.followThroughRMS,
                            digits: 2
                        ),
                        "\(standing.shot.audioPeak)"
                    ],
                    columns: columns,
                    y: y,
                    height: rowHeight,
                    shaded: rowIndex.isMultiple(of: 2)
                )
                rowIndex += 1
                y += rowHeight
            }
            drawFooter(pageNumber: pageNumber)
            pageNumber += 1
        } while rowIndex < standings.count
    }

    private func drawTableRow(
        _ values: [String],
        columns: [(String, CGFloat)],
        y: CGFloat,
        height: CGFloat,
        header: Bool = false,
        shaded: Bool = false
    ) {
        let totalWidth = columns.reduce(CGFloat(0)) { $0 + $1.1 }
        if header || shaded {
            (header ? accent : UIColor.systemGray6).setFill()
            UIRectFill(
                CGRect(x: margin, y: y, width: totalWidth, height: height)
            )
        }
        var x = margin
        for (index, column) in columns.enumerated() {
            let alignment: NSTextAlignment = index <= 1 ? .left : .right
            drawText(
                values[index],
                in: CGRect(
                    x: x + 4,
                    y: y + 3,
                    width: column.1 - 8,
                    height: height - 4
                ),
                font: header
                    ? .boldSystemFont(ofSize: 7.5)
                    : .monospacedDigitSystemFont(
                        ofSize: 7.5,
                        weight: .regular
                    ),
                color: header ? .white : .label,
                alignment: alignment
            )
            x += column.1
        }
    }

    private func drawFooter(pageNumber: Int) {
        drawText(
            "AimTracer  •  \(Self.dateFormatter.string(from: Date()))",
            in: CGRect(
                x: margin,
                y: page.height - 29,
                width: 300,
                height: 12
            ),
            font: .systemFont(ofSize: 7),
            color: muted
        )
        drawText(
            L10n.format("Seite %d", pageNumber),
            in: CGRect(
                x: page.width - margin - 80,
                y: page.height - 29,
                width: 80,
                height: 12
            ),
            font: .systemFont(ofSize: 7),
            color: muted,
            alignment: .right
        )
    }

    private func drawText(
        _ text: String,
        in rect: CGRect,
        font: UIFont,
        color: UIColor,
        alignment: NSTextAlignment = .left
    ) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byTruncatingTail
        (L10n.text(text) as NSString).draw(
            in: rect,
            withAttributes: [
                .font: font,
                .foregroundColor: color,
                .paragraphStyle: paragraph
            ]
        )
    }

    private static func number(_ value: Double, digits: Int) -> String {
        String(
            format: "%.\(digits)f",
            locale: Locale.current,
            value
        )
    }

    private static func score(_ value: Double) -> String {
        number(value, digits: value.rounded() == value ? 0 : 1)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter
    }()

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()
}
