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

enum MotionAnalysis {
    // LSM6DS3TR-C sensitivity at ±500 dps.
    static let gyroDegreesPerSecondPerLSB = 0.0175

    static func trace(for shot: ShotCapture) -> [TracePoint] {
        guard !shot.samples.isEmpty, shot.sampleRateHz > 0 else { return [] }
        let dt = 1.0 / Double(shot.sampleRateHz)
        var x = 0.0
        var y = 0.0
        var points: [TracePoint] = []
        points.reserveCapacity(shot.samples.count)

        for sample in shot.samples {
            // CALIBRATION: Axis mapping depends on the enclosure orientation.
            // This assumes the board is flat, USB-C to the rear of the pistol.
            x += Double(sample.gz) * gyroDegreesPerSecondPerLSB * dt
            y += Double(sample.gy) * gyroDegreesPerSecondPerLSB * dt
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

    static func liveTrace(for samples: [LiveMotionSample]) -> [TracePoint] {
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
            y += Double(current.gy) * gyroDegreesPerSecondPerLSB * dt
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
        let holdEnd = max(trigger - rate / 6, 0)
        let triggerStart = max(trigger - Int(Double(rate) * 0.15), 0)
        let triggerEnd = min(
            trigger + Int(Double(rate) * 0.08),
            shot.samples.count
        )
        let followEnd = min(trigger + rate / 4, shot.samples.count)

        return ShotMetrics(
            holdRMS: rms(Array(shot.samples[0..<holdEnd])),
            triggerRMS: rms(Array(shot.samples[triggerStart..<triggerEnd])),
            followThroughRMS: rms(Array(shot.samples[trigger..<followEnd]))
        )
    }

    private static func rms(_ samples: [MotionSample]) -> Double {
        guard !samples.isEmpty else { return 0 }
        let squared = samples.reduce(0.0) { partial, sample in
            let x = Double(sample.gx) * gyroDegreesPerSecondPerLSB
            let y = Double(sample.gy) * gyroDegreesPerSecondPerLSB
            let z = Double(sample.gz) * gyroDegreesPerSecondPerLSB
            return partial + x * x + y * y + z * z
        }
        return sqrt(squared / Double(samples.count))
    }
}
