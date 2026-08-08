import CoreGraphics
import Foundation

struct TracePoint: Hashable {
    var x: Double
    var y: Double
    var relativeTime: Double
}

struct ShotMetrics: Equatable, Hashable {
    var holdRMS: Double
    var triggerRMS: Double
    var followThroughRMS: Double
}

struct AxisDisplayConfiguration: Equatable, Hashable {
    var invertXAxis = false
    var invertYAxis = false

    static let standard = AxisDisplayConfiguration()

    var horizontalMultiplier: Double { invertXAxis ? -1 : 1 }
    var verticalMultiplier: Double { invertYAxis ? -1 : 1 }
}

enum MotionAnalysis {
    // LSM6DS3TR-C sensitivity at ±500 dps.
    static let gyroDegreesPerSecondPerLSB = 0.0175

    /// Fixed AimTracer enclosure profile:
    /// XIAO component side down, PCB underside up, USB-C toward the shooter.
    /// Raw samples and scores stay untouched; only graph axes are transformed.
    static let mountingProfileID =
        "xiao-sense-component-side-down-usb-toward-shooter-v3"

    static func trace(
        for shot: ShotCapture,
        axes: AxisDisplayConfiguration = .standard
    ) -> [TracePoint] {
        guard !shot.samples.isEmpty, shot.sampleRateHz > 0 else { return [] }
        let dt = 1.0 / Double(shot.sampleRateHz)
        var x = 0.0
        var y = 0.0
        var points: [TracePoint] = []
        points.reserveCapacity(shot.samples.count)

        for sample in shot.samples {
            // Verified on the mounted AimTracer enclosure:
            // yaw right = +gz, pitch up = +gy, longitudinal roll = gx.
            // User inversion affects the graph only, never RMS scoring.
            x += Double(sample.gz) * gyroDegreesPerSecondPerLSB * dt
                * axes.horizontalMultiplier
            y += Double(sample.gy) * gyroDegreesPerSecondPerLSB * dt
                * axes.verticalMultiplier
            let relative = (
                Double(sample.index) - Double(shot.triggerIndex)
            ) * dt
            points.append(TracePoint(x: x, y: y, relativeTime: relative))
        }

        let trigger = points[
            min(Int(shot.triggerIndex), points.count - 1)
        ]
        return points.map {
            TracePoint(
                x: $0.x - trigger.x,
                y: $0.y - trigger.y,
                relativeTime: $0.relativeTime
            )
        }
    }

    static func liveTrace(
        for samples: [LiveMotionSample],
        axes: AxisDisplayConfiguration = .standard
    ) -> [TracePoint] {
        guard samples.count > 1 else { return [] }
        var points: [TracePoint] = [TracePoint(x: 0, y: 0, relativeTime: 0)]
        var x = 0.0
        var y = 0.0
        let start = samples[0].uptimeMs

        for index in 1..<samples.count {
            let previous = samples[index - 1]
            let current = samples[index]
            let deltaMs = current.uptimeMs &- previous.uptimeMs
            guard deltaMs < 250 else { continue }
            let dt = Double(deltaMs) / 1_000.0
            x += Double(current.gz) * gyroDegreesPerSecondPerLSB * dt
                * axes.horizontalMultiplier
            y += Double(current.gy) * gyroDegreesPerSecondPerLSB * dt
                * axes.verticalMultiplier
            points.append(
                TracePoint(
                    x: x,
                    y: y,
                    relativeTime: Double(current.uptimeMs &- start) / 1_000
                )
            )
        }
        return points
    }

    static func metrics(for shot: ShotCapture) -> ShotMetrics {
        let rate = max(Int(shot.sampleRateHz), 1)
        let trigger = min(Int(shot.triggerIndex), shot.samples.count)
        // CALIBRATION: Deliberately exclude the final 180 ms from the hold
        // window so the trigger movement is not counted twice.
        let holdEnd = max(trigger - Int(Double(rate) * 0.18), 0)
        let triggerStart = max(trigger - Int(Double(rate) * 0.15), 0)
        // The trigger score is pre-shot only. The pneumatic impulse must not
        // influence the shooter's trigger-control score.
        let triggerEnd = min(trigger + 1, shot.samples.count)
        // Skip the first 50 ms after release; this is dominated by the shot
        // impulse rather than deliberate follow-through.
        let followStart = min(
            trigger + Int(Double(rate) * 0.05),
            shot.samples.count
        )
        let followEnd = min(trigger + rate / 4, shot.samples.count)

        return ShotMetrics(
            holdRMS: rms(Array(shot.samples[0..<holdEnd])),
            triggerRMS: rms(Array(shot.samples[triggerStart..<triggerEnd])),
            followThroughRMS: rms(
                Array(shot.samples[followStart..<max(followStart, followEnd)])
            )
        )
    }

    private static func rms(_ samples: [MotionSample]) -> Double {
        guard !samples.isEmpty else { return 0 }
        let squared = samples.reduce(0.0) { partial, sample in
            let y = Double(sample.gy) * gyroDegreesPerSecondPerLSB
            let z = Double(sample.gz) * gyroDegreesPerSecondPerLSB
            // Pitch and yaw move the sight line. Roll is intentionally kept
            // out of the technique score and remains available in raw JSON.
            return partial + y * y + z * z
        }
        return sqrt(squared / Double(samples.count))
    }
}
