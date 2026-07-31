import Foundation

@main
struct ProtocolSelfTest {
    static func main() throws {
        try decodesPowerStatus()
        try assemblesCompleteShot()
        try reportsFirstMissingSample()
        print("AimTracer iOS protocol self-test passed.")
    }

    private static func decodesPowerStatus() throws {
        let power = try AimTracerCodec.powerStatus(
            from: Data([1, 73, 0b0001_1001, 0x6C, 0x0F, 50])
        )
        guard power.levelPercent == 73,
              power.millivolts == 3_948,
              power.chargeCurrentMa == 50,
              power.isCharging,
              power.externalPowerPresent,
              power.chargeSignalActive else {
            throw SelfTestError("Akkustatus wurde nicht korrekt dekodiert.")
        }

        let unplugged = try AimTracerCodec.powerStatus(
            from: Data([1, 72, 0b0001_0001, 0x60, 0x0F, 50])
        )
        guard !unplugged.isCharging,
              !unplugged.externalPowerPresent,
              unplugged.chargeSignalActive else {
            throw SelfTestError(
                "Ladesignal ohne USB wurde nicht sicher unterdrückt."
            )
        }
    }

    private static func assemblesCompleteShot() throws {
        let packets = makeShotPackets(sampleCount: 312)
        let assembler = ShotAssembler()
        var shot: ShotCapture?
        for packet in packets {
            if let completed = try assembler.ingest(packet) {
                shot = completed
            }
        }
        guard shot?.samples.count == 312,
              shot?.triggerIndex == 208 else {
            throw SelfTestError("Vollständiger Schuss wurde nicht aufgebaut.")
        }
    }

    private static func reportsFirstMissingSample() throws {
        let packets = makeShotPackets(sampleCount: 12, omittedIndex: 7)
        let assembler = ShotAssembler()
        do {
            for packet in packets {
                _ = try assembler.ingest(packet)
            }
            throw SelfTestError("Fehlendes Sample wurde nicht erkannt.")
        } catch let error as AimTracerProtocolError {
            guard case .incompleteShot(
                expected: 12,
                received: 11,
                firstMissingIndex: 7
            ) = error else {
                throw SelfTestError(
                    "Unerwartete Diagnose für fehlendes Sample: \(error)"
                )
            }
        }
    }

    private static func makeShotPackets(
        sampleCount: UInt16,
        omittedIndex: UInt16? = nil
    ) -> [Data] {
        var metadata = [UInt8](repeating: 0, count: 20)
        metadata[0] = AimTracerPacketType.shotMeta.rawValue
        metadata[1] = AimTracerCodec.protocolVersion
        metadata.put(UInt16(42), at: 2)
        metadata.put(UInt32(123_456), at: 4)
        metadata.put(UInt16(416), at: 8)
        metadata.put(sampleCount, at: 10)
        metadata.put(min(208, sampleCount - 1), at: 12)
        metadata.put(UInt16(8_000), at: 14)
        metadata.put(UInt16(2_000), at: 16)
        metadata.put(UInt16(3_000), at: 18)

        var packets = [Data(metadata)]
        var crcPayload = Data()
        for index in 0..<sampleCount {
            var sample = [UInt8](repeating: 0, count: 20)
            sample[0] = AimTracerPacketType.shotSample.rawValue
            sample.put(UInt16(42), at: 1)
            sample.put(index, at: 3)
            sample.put(Int16(bitPattern: index &+ 1), at: 5)
            sample.put(Int16(bitPattern: index &+ 2), at: 7)
            sample.put(Int16(bitPattern: index &+ 3), at: 9)
            sample.put(Int16(bitPattern: index &+ 4), at: 11)
            sample.put(Int16(bitPattern: index &+ 5), at: 13)
            sample.put(Int16(bitPattern: index &+ 6), at: 15)
            sample.put(index &+ 100, at: 17)
            sample[19] = index == min(208, sampleCount - 1) ? 1 : 0
            crcPayload.append(contentsOf: sample[5..<19])
            if index != omittedIndex {
                packets.append(Data(sample))
            }
        }

        var end = [UInt8](repeating: 0, count: 20)
        end[0] = AimTracerPacketType.shotEnd.rawValue
        end.put(UInt16(42), at: 1)
        end.put(sampleCount, at: 3)
        end.put(CRC32.checksum(crcPayload), at: 5)
        packets.append(Data(end))
        return packets
    }
}

private struct SelfTestError: LocalizedError {
    let message: String

    init(_ message: String) {
        self.message = message
    }

    var errorDescription: String? { message }
}

private extension Array where Element == UInt8 {
    mutating func put(_ value: UInt16, at offset: Int) {
        self[offset] = UInt8(value & 0xFF)
        self[offset + 1] = UInt8((value >> 8) & 0xFF)
    }

    mutating func put(_ value: Int16, at offset: Int) {
        put(UInt16(bitPattern: value), at: offset)
    }

    mutating func put(_ value: UInt32, at offset: Int) {
        self[offset] = UInt8(value & 0xFF)
        self[offset + 1] = UInt8((value >> 8) & 0xFF)
        self[offset + 2] = UInt8((value >> 16) & 0xFF)
        self[offset + 3] = UInt8((value >> 24) & 0xFF)
    }
}
