import Foundation

enum AimTracerPacketType: UInt8 {
    case status = 0x01
    case live = 0x10
    case shotMeta = 0x20
    case shotSample = 0x21
    case shotEnd = 0x22
}

enum AimTracerCommand: UInt8 {
    case startSession = 0x01
    case stopSession = 0x02
    case arm = 0x03
    case disarm = 0x04
    case manualTrigger = 0x05
    case calibrate = 0x06
}

enum AimTracerProtocolError: LocalizedError {
    case invalidLength
    case invalidVersion
    case invalidPacket
    case incompleteShot(
        expected: Int,
        received: Int,
        firstMissingIndex: Int?
    )
    case checksumMismatch

    var errorDescription: String? {
        switch self {
        case .invalidLength:
            return L10n.text("BLE-Paket hat eine unerwartete Länge.")
        case .invalidVersion:
            return L10n.text("Firmware und App verwenden verschiedene Protokollversionen.")
        case .invalidPacket:
            return L10n.text("BLE-Paket ist ungültig.")
        case .incompleteShot(
            let expected,
            let received,
            let firstMissingIndex
        ):
            let missing = firstMissingIndex.map {
                L10n.format(" Erstes fehlendes Paket: %d.", $0)
            } ?? ""
            return L10n.format(
                "Schussübertragung war unvollständig (%d von %d Samples).",
                received,
                expected
            ) + missing
        case .checksumMismatch:
            return L10n.text("Prüfsumme der Schussdaten stimmt nicht.")
        }
    }
}

enum AimTracerCodec {
    static let protocolVersion: UInt8 = 1
    static let packetLength = 20

    static func status(from data: Data) throws -> DeviceStatus {
        try requirePacket(data, type: .status)
        guard data[1] == protocolVersion else {
            throw AimTracerProtocolError.invalidVersion
        }
        let flags = data[2]
        return DeviceStatus(
            isConnected: flags & (1 << 0) != 0,
            sessionActive: flags & (1 << 1) != 0,
            armed: flags & (1 << 2) != 0,
            capturing: flags & (1 << 3) != 0,
            transmitting: flags & (1 << 4) != 0,
            calibrated: flags & (1 << 5) != 0,
            microphoneReady: flags & (1 << 6) != 0,
            lastError: data[3],
            sampleRateHz: data.u16(at: 4),
            bufferedSamples: data.u16(at: 6),
            shotCount: data.u16(at: 8),
            droppedTriggers: data.u16(at: 10),
            microphonePeak: data.u16(at: 12),
            transmittingShotID: data.u16(at: 14),
            transmittingSampleIndex: data.u16(at: 16),
            firmwareVersion: "\(data[18]).\(data[19])"
        )
    }

    static func powerStatus(from data: Data) throws -> DevicePowerStatus {
        guard data.count == 6 else {
            throw AimTracerProtocolError.invalidLength
        }
        guard data[0] == protocolVersion else {
            throw AimTracerProtocolError.invalidVersion
        }
        let flags = data[2]
        let externalPowerPresent = flags & (1 << 3) != 0
        return DevicePowerStatus(
            levelPercent: min(data[1], 100),
            isCharging:
                flags & (1 << 0) != 0 && externalPowerPresent,
            externalPowerPresent: externalPowerPresent,
            chargeSignalActive: flags & (1 << 4) != 0,
            isLow: flags & (1 << 1) != 0,
            isCritical: flags & (1 << 2) != 0,
            millivolts: data.u16(at: 3),
            chargeCurrentMa: data[5]
        )
    }

    static func live(from data: Data) throws -> LiveMotionSample {
        try requirePacket(data, type: .live)
        return LiveMotionSample(
            sequence: data.u16(at: 1),
            uptimeMs: data.u32(at: 3),
            gx: data.i16(at: 7),
            gy: data.i16(at: 9),
            gz: data.i16(at: 11),
            ax: data.i16(at: 13),
            ay: data.i16(at: 15),
            az: data.i16(at: 17),
            microphonePeak8: data[19]
        )
    }

    static func configuration(from data: Data) throws -> DeviceConfiguration {
        guard data.count == packetLength else {
            throw AimTracerProtocolError.invalidLength
        }
        guard data[0] == protocolVersion,
              let trigger = TriggerMode(rawValue: data[1]) else {
            throw AimTracerProtocolError.invalidVersion
        }
        return DeviceConfiguration(
            triggerMode: trigger,
            sampleRateHz: data.u16(at: 2),
            liveRateHz: data[4],
            preTriggerMs: data.u16(at: 6),
            postTriggerMs: data.u16(at: 8),
            microphoneThreshold: data.u16(at: 10),
            accelerationThreshold: data.u16(at: 12),
            gyroThreshold: data.u16(at: 14),
            coincidenceMs: data[16],
            refractoryMs: UInt16(data[17]) * 10,
            gyroRangeCode: data[18],
            accelerationRangeCode: data[19]
        )
    }

    static func encode(_ configuration: DeviceConfiguration) -> Data {
        var bytes = [UInt8](repeating: 0, count: packetLength)
        bytes[0] = protocolVersion
        bytes[1] = configuration.triggerMode.rawValue
        bytes.put(configuration.sampleRateHz, at: 2)
        bytes[4] = configuration.liveRateHz
        bytes.put(configuration.preTriggerMs, at: 6)
        bytes.put(configuration.postTriggerMs, at: 8)
        bytes.put(configuration.microphoneThreshold, at: 10)
        bytes.put(configuration.accelerationThreshold, at: 12)
        bytes.put(configuration.gyroThreshold, at: 14)
        bytes[16] = configuration.coincidenceMs
        bytes[17] = UInt8(clamping: configuration.refractoryMs / 10)
        bytes[18] = configuration.gyroRangeCode
        bytes[19] = configuration.accelerationRangeCode
        return Data(bytes)
    }

    private static func requirePacket(
        _ data: Data,
        type: AimTracerPacketType
    ) throws {
        guard data.count == packetLength else {
            throw AimTracerProtocolError.invalidLength
        }
        guard data[0] == type.rawValue else {
            throw AimTracerProtocolError.invalidPacket
        }
    }
}

final class ShotAssembler {
    private struct Metadata {
        var shotID: UInt16
        var uptimeMs: UInt32
        var sampleRateHz: UInt16
        var sampleCount: UInt16
        var triggerIndex: UInt16
        var audioPeak: UInt16
        var accelerationPeak: UInt16
        var gyroPeak: UInt16
    }

    private final class Builder {
        let metadata: Metadata
        var samples: [UInt16: MotionSample] = [:]
        var crcBytes: [UInt16: Data] = [:]

        init(metadata: Metadata) {
            self.metadata = metadata
        }
    }

    private var builders: [UInt16: Builder] = [:]

    func ingest(_ data: Data) throws -> ShotCapture? {
        guard data.count == AimTracerCodec.packetLength,
              let packetType = AimTracerPacketType(rawValue: data[0]) else {
            throw AimTracerProtocolError.invalidLength
        }

        switch packetType {
        case .shotMeta:
            guard data[1] == AimTracerCodec.protocolVersion else {
                throw AimTracerProtocolError.invalidVersion
            }
            let metadata = Metadata(
                shotID: data.u16(at: 2),
                uptimeMs: data.u32(at: 4),
                sampleRateHz: data.u16(at: 8),
                sampleCount: data.u16(at: 10),
                triggerIndex: data.u16(at: 12),
                audioPeak: data.u16(at: 14),
                accelerationPeak: data.u16(at: 16),
                gyroPeak: data.u16(at: 18)
            )
            builders[metadata.shotID] = Builder(metadata: metadata)
            return nil

        case .shotSample:
            let shotID = data.u16(at: 1)
            let index = data.u16(at: 3)
            guard let builder = builders[shotID] else {
                throw AimTracerProtocolError.invalidPacket
            }
            guard index < builder.metadata.sampleCount else {
                throw AimTracerProtocolError.invalidPacket
            }
            builder.samples[index] = MotionSample(
                index: index,
                gx: data.i16(at: 5),
                gy: data.i16(at: 7),
                gz: data.i16(at: 9),
                ax: data.i16(at: 11),
                ay: data.i16(at: 13),
                az: data.i16(at: 15),
                microphonePeak: data.u16(at: 17),
                isTrigger: data[19] & 0x01 != 0
            )
            builder.crcBytes[index] = data.subdata(in: 5..<19)
            return nil

        case .shotEnd:
            let shotID = data.u16(at: 1)
            let announcedCount = data.u16(at: 3)
            let expectedCRC = data.u32(at: 5)
            guard let builder = builders.removeValue(forKey: shotID) else {
                throw AimTracerProtocolError.invalidPacket
            }
            let expected = Int(builder.metadata.sampleCount)
            let received = builder.samples.count
            guard announcedCount == builder.metadata.sampleCount,
                  received == expected else {
                throw AimTracerProtocolError.incompleteShot(
                    expected: expected,
                    received: received,
                    firstMissingIndex: firstMissingIndex(
                        in: builder,
                        expected: expected
                    )
                )
            }

            let indices = builder.samples.keys.sorted()
            guard indices.enumerated().allSatisfy({
                $0.offset == Int($0.element)
            }) else {
                throw AimTracerProtocolError.incompleteShot(
                    expected: expected,
                    received: received,
                    firstMissingIndex: firstMissingIndex(
                        in: builder,
                        expected: expected
                    )
                )
            }
            var payload = Data()
            for index in indices {
                guard let bytes = builder.crcBytes[index] else {
                    throw AimTracerProtocolError.incompleteShot(
                        expected: expected,
                        received: received,
                        firstMissingIndex: Int(index)
                    )
                }
                payload.append(bytes)
            }
            guard CRC32.checksum(payload) == expectedCRC else {
                throw AimTracerProtocolError.checksumMismatch
            }

            return ShotCapture(
                deviceShotID: shotID,
                triggerUptimeMs: builder.metadata.uptimeMs,
                sampleRateHz: builder.metadata.sampleRateHz,
                triggerIndex: builder.metadata.triggerIndex,
                audioPeak: builder.metadata.audioPeak,
                accelerationPeak: builder.metadata.accelerationPeak,
                gyroPeak: builder.metadata.gyroPeak,
                samples: indices.compactMap { builder.samples[$0] }
            )

        default:
            throw AimTracerProtocolError.invalidPacket
        }
    }

    func reset() {
        builders.removeAll(keepingCapacity: true)
    }

    private func firstMissingIndex(
        in builder: Builder,
        expected: Int
    ) -> Int? {
        (0..<expected).first {
            builder.samples[UInt16($0)] == nil ||
                builder.crcBytes[UInt16($0)] == nil
        }
    }
}

enum CRC32 {
    static func checksum(_ data: Data) -> UInt32 {
        var crc = UInt32.max
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                crc = (crc >> 1) ^ (crc & 1 == 1 ? 0xEDB8_8320 : 0)
            }
        }
        return crc ^ UInt32.max
    }
}

private extension Data {
    func u16(at offset: Int) -> UInt16 {
        UInt16(self[offset]) | (UInt16(self[offset + 1]) << 8)
    }

    func i16(at offset: Int) -> Int16 {
        Int16(bitPattern: u16(at: offset))
    }

    func u32(at offset: Int) -> UInt32 {
        UInt32(self[offset])
            | (UInt32(self[offset + 1]) << 8)
            | (UInt32(self[offset + 2]) << 16)
            | (UInt32(self[offset + 3]) << 24)
    }
}

private extension Array where Element == UInt8 {
    mutating func put(_ value: UInt16, at offset: Int) {
        self[offset] = UInt8(value & 0xFF)
        self[offset + 1] = UInt8((value >> 8) & 0xFF)
    }
}
