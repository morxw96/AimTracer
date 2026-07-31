import SwiftUI

struct TraceCard: View {
    let shot: ShotCapture?
    let liveSamples: [LiveMotionSample]

    private var points: [TracePoint] {
        if let shot {
            MotionAnalysis.trace(for: shot)
        } else {
            MotionAnalysis.liveTrace(for: liveSamples)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(shot == nil ? "Live-Bewegung" : "Letzter Schuss")
                        .font(.headline)
                    Text(shot == nil
                         ? "Relative Winkelspur"
                         : "Blau vor, orange nach der Auslösung")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if let shot {
                    Text("#\(shot.deviceShotID)")
                        .font(.caption.monospacedDigit())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.mint.opacity(0.16), in: Capsule())
                }
            }

            MotionTraceCanvas(points: points)
                .frame(height: 300)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }
}

struct MotionTraceCanvas: View {
    let points: [TracePoint]

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            context.fill(
                Path(roundedRect: rect, cornerRadius: 14),
                with: .color(.black.opacity(0.82))
            )

            var grid = Path()
            grid.move(to: CGPoint(x: size.width / 2, y: 12))
            grid.addLine(to: CGPoint(x: size.width / 2, y: size.height - 12))
            grid.move(to: CGPoint(x: 12, y: size.height / 2))
            grid.addLine(to: CGPoint(x: size.width - 12, y: size.height / 2))
            context.stroke(grid, with: .color(.white.opacity(0.16)), lineWidth: 1)

            guard points.count > 1 else {
                context.draw(
                    Text("Warte auf Bewegungsdaten")
                        .font(.callout)
                        .foregroundStyle(.white.opacity(0.55)),
                    at: CGPoint(x: size.width / 2, y: size.height / 2)
                )
                return
            }

            let maxMagnitude = max(
                points.map { abs($0.x) }.max() ?? 0,
                points.map { abs($0.y) }.max() ?? 0,
                0.05
            )
            let scale = min(size.width, size.height) * 0.43 / maxMagnitude

            func mapped(_ point: TracePoint) -> CGPoint {
                CGPoint(
                    x: size.width / 2 + point.x * scale,
                    y: size.height / 2 - point.y * scale
                )
            }

            var pre = Path()
            var post = Path()
            var hasPre = false
            var hasPost = false

            for (index, point) in points.enumerated() {
                let position = mapped(point)
                if point.relativeTime <= 0 {
                    if hasPre { pre.addLine(to: position) } else {
                        pre.move(to: position)
                        hasPre = true
                    }
                } else {
                    if !hasPost {
                        let previous = points[max(0, index - 1)]
                        post.move(to: mapped(previous))
                        hasPost = true
                    }
                    post.addLine(to: position)
                }
            }
            context.stroke(
                pre,
                with: .color(.cyan),
                style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
            )
            context.stroke(
                post,
                with: .color(.orange),
                style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
            )

            let trigger = mapped(
                points.min(by: {
                    abs($0.relativeTime) < abs($1.relativeTime)
                }) ?? points[0]
            )
            context.fill(
                Path(ellipseIn: CGRect(
                    x: trigger.x - 5,
                    y: trigger.y - 5,
                    width: 10,
                    height: 10
                )),
                with: .color(.white)
            )
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityLabel("Relative Bewegungsbahn der Pistole")
    }
}

struct ShotMetricsView: View {
    let shot: ShotCapture

    private var metrics: ShotMetrics {
        MotionAnalysis.metrics(for: shot)
    }

    var body: some View {
        HStack(spacing: 10) {
            metric("Ruhig halten", metrics.holdRMS)
            metric("Abzug", metrics.triggerRMS)
            metric("Nachhalten", metrics.followThroughRMS)
        }
    }

    private func metric(_ title: String, _ value: Double) -> some View {
        VStack(spacing: 5) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value.formatted(.number.precision(.fractionLength(1))))
                .font(.title3.bold().monospacedDigit())
            Text("°/s RMS")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}
