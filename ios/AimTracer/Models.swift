import Foundation

enum TrainingProgram: String, CaseIterable, Codable, Identifiable {
    case lp20
    case lp40
    case lp60
    case dryFire
    case freeTraining

    var id: String { rawValue }

    var title: String {
        switch self {
        case .lp20: "LP20"
        case .lp40: "LP40"
        case .lp60: "LP60"
        case .dryFire: L10n.text("Trockentraining")
        case .freeTraining: L10n.text("Freies Training")
        }
    }

    var plannedShotCount: Int? {
        switch self {
        case .lp20: 20
        case .lp40: 40
        case .lp60: 60
        case .dryFire, .freeTraining: nil
        }
    }
}

enum TriggerMode: UInt8, CaseIterable, Codable, Identifiable {
    case audio = 1
    case motion = 2
    case audioAndMotion = 3

    var id: UInt8 { rawValue }

    var title: String {
        switch self {
        case .audio: L10n.text("Nur Mikrofon")
        case .motion: L10n.text("Nur Bewegung")
        case .audioAndMotion: L10n.text("Mikrofon + Bewegung")
        }
    }
}

struct DeviceConfiguration: Codable, Equatable {
    static let `default` = DeviceConfiguration(
        triggerMode: .audioAndMotion,
        sampleRateHz: 416,
        liveRateHz: 25,
        preTriggerMs: 500,
        postTriggerMs: 250,
        microphoneThreshold: 7_000,
        accelerationThreshold: 1_500,
        gyroThreshold: 2_000,
        coincidenceMs: 40,
        refractoryMs: 1_200,
        gyroRangeCode: 2,
        accelerationRangeCode: 2
    )

    var triggerMode: TriggerMode
    var sampleRateHz: UInt16
    var liveRateHz: UInt8
    var preTriggerMs: UInt16
    var postTriggerMs: UInt16
    var microphoneThreshold: UInt16
    var accelerationThreshold: UInt16
    var gyroThreshold: UInt16
    var coincidenceMs: UInt8
    var refractoryMs: UInt16
    var gyroRangeCode: UInt8
    var accelerationRangeCode: UInt8
}

struct DeviceStatus: Equatable {
    var isConnected: Bool
    var sessionActive: Bool
    var armed: Bool
    var capturing: Bool
    var transmitting: Bool
    var calibrated: Bool
    var microphoneReady: Bool
    var lastError: UInt8
    var sampleRateHz: UInt16
    var bufferedSamples: UInt16
    var shotCount: UInt16
    var droppedTriggers: UInt16
    var microphonePeak: UInt16
    var transmittingShotID: UInt16
    var transmittingSampleIndex: UInt16
    var firmwareVersion: String
}

struct DevicePowerStatus: Equatable {
    var levelPercent: UInt8
    var isCharging: Bool
    var externalPowerPresent: Bool
    var chargeSignalActive: Bool
    var isLow: Bool
    var isCritical: Bool
    var millivolts: UInt16
    var chargeCurrentMa: UInt8
}

struct MotionSample: Codable, Hashable, Identifiable {
    var index: UInt16
    var gx: Int16
    var gy: Int16
    var gz: Int16
    var ax: Int16
    var ay: Int16
    var az: Int16
    var microphonePeak: UInt16
    var isTrigger: Bool

    var id: UInt16 { index }
}

struct LiveMotionSample: Hashable, Identifiable {
    var sequence: UInt16
    var uptimeMs: UInt32
    var gx: Int16
    var gy: Int16
    var gz: Int16
    var ax: Int16
    var ay: Int16
    var az: Int16
    var microphonePeak8: UInt8

    var id: UInt16 { sequence }
}

struct ShotCapture: Codable, Hashable, Identifiable {
    var id: UUID
    var deviceShotID: UInt16
    var receivedAt: Date
    var triggerUptimeMs: UInt32
    var sampleRateHz: UInt16
    var triggerIndex: UInt16
    var audioPeak: UInt16
    var accelerationPeak: UInt16
    var gyroPeak: UInt16
    var samples: [MotionSample]

    init(
        id: UUID = UUID(),
        deviceShotID: UInt16,
        receivedAt: Date = Date(),
        triggerUptimeMs: UInt32,
        sampleRateHz: UInt16,
        triggerIndex: UInt16,
        audioPeak: UInt16,
        accelerationPeak: UInt16,
        gyroPeak: UInt16,
        samples: [MotionSample]
    ) {
        self.id = id
        self.deviceShotID = deviceShotID
        self.receivedAt = receivedAt
        self.triggerUptimeMs = triggerUptimeMs
        self.sampleRateHz = sampleRateHz
        self.triggerIndex = triggerIndex
        self.audioPeak = audioPeak
        self.accelerationPeak = accelerationPeak
        self.gyroPeak = gyroPeak
        self.samples = samples
    }
}

struct TrainingSession: Codable, Hashable, Identifiable {
    var id: UUID
    var startedAt: Date
    var endedAt: Date?
    var name: String
    // Optional keeps sessions.json from AimTracer 0.1 backward compatible.
    var program: TrainingProgram?
    // Optional keeps sessions written before Meyton pairing backward compatible.
    var meytonScore: Double?
    var shots: [ShotCapture]

    init(
        id: UUID = UUID(),
        startedAt: Date = Date(),
        endedAt: Date? = nil,
        name: String,
        program: TrainingProgram? = nil,
        meytonScore: Double? = nil,
        shots: [ShotCapture] = []
    ) {
        self.id = id
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.name = name
        self.program = program
        self.meytonScore = meytonScore
        self.shots = shots
    }

    var effectiveProgram: TrainingProgram {
        program ?? .freeTraining
    }
}
