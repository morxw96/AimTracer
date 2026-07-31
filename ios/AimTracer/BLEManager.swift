@preconcurrency import CoreBluetooth
import Foundation

enum AimTracerBLE {
    static let service = CBUUID(string: "7B8A0001-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let status = CBUUID(string: "7B8A0002-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let control = CBUUID(string: "7B8A0003-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let live = CBUUID(string: "7B8A0004-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let shot = CBUUID(string: "7B8A0005-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let config = CBUUID(string: "7B8A0006-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let power = CBUUID(string: "7B8A0007-6D5B-4F0D-9C6A-3E0D6C0A1000")
    static let batteryService = CBUUID(string: "180F")
    static let batteryLevel = CBUUID(string: "2A19")
}

enum BLEConnectionState: Equatable {
    case unavailable(String)
    case idle
    case scanning
    case connecting(String)
    case discovering
    case ready(String)

    var label: String {
        switch self {
        case .unavailable(let reason): reason
        case .idle: "Nicht verbunden"
        case .scanning: "Suche läuft"
        case .connecting(let name): "Verbinde mit \(name)"
        case .discovering: "Dienste werden geladen"
        case .ready(let name): name
        }
    }

    var isReady: Bool {
        if case .ready = self { return true }
        return false
    }
}

struct DiscoveredAimTracer: Identifiable {
    let id: UUID
    let peripheral: CBPeripheral
    var name: String
    var rssi: Int
}

@MainActor
final class BLEManager: NSObject, ObservableObject {
    @Published private(set) var connectionState: BLEConnectionState = .idle
    @Published private(set) var discoveredDevices: [DiscoveredAimTracer] = []
    @Published private(set) var status: DeviceStatus?
    @Published private(set) var configuration: DeviceConfiguration?
    @Published private(set) var powerStatus: DevicePowerStatus?
    @Published private(set) var liveSamples: [LiveMotionSample] = []
    @Published private(set) var lastShot: ShotCapture?
    @Published var message: String?

    var onShot: ((ShotCapture) -> Void)?

    private var centralManager: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?
    private var statusCharacteristic: CBCharacteristic?
    private var controlCharacteristic: CBCharacteristic?
    private var liveCharacteristic: CBCharacteristic?
    private var shotCharacteristic: CBCharacteristic?
    private var configCharacteristic: CBCharacteristic?
    private var powerCharacteristic: CBCharacteristic?
    private var batteryLevelCharacteristic: CBCharacteristic?
    private let shotAssembler = ShotAssembler()

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: .main)
    }

    func startScanning() {
        guard centralManager.state == .poweredOn else {
            message = "Bluetooth ist noch nicht verfügbar."
            return
        }
        discoveredDevices.removeAll()
        connectionState = .scanning
        centralManager.scanForPeripherals(
            withServices: [AimTracerBLE.service],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
    }

    func stopScanning() {
        centralManager.stopScan()
        if !connectionState.isReady {
            connectionState = .idle
        }
    }

    func connect(to device: DiscoveredAimTracer) {
        centralManager.stopScan()
        connectedPeripheral = device.peripheral
        connectionState = .connecting(device.name)
        centralManager.connect(device.peripheral, options: nil)
    }

    func disconnect() {
        guard let connectedPeripheral else { return }
        centralManager.cancelPeripheralConnection(connectedPeripheral)
    }

    func send(command: AimTracerCommand) {
        guard let connectedPeripheral,
              let controlCharacteristic else {
            message = "AimTracer ist nicht vollständig verbunden."
            return
        }
        connectedPeripheral.writeValue(
            Data([command.rawValue]),
            for: controlCharacteristic,
            type: .withResponse
        )
    }

    func send(configuration: DeviceConfiguration) {
        guard let connectedPeripheral,
              let configCharacteristic else {
            message = "AimTracer ist nicht vollständig verbunden."
            return
        }
        connectedPeripheral.writeValue(
            AimTracerCodec.encode(configuration),
            for: configCharacteristic,
            type: .withResponse
        )
        connectedPeripheral.readValue(for: configCharacteristic)
    }

    private func resetConnection() {
        shotAssembler.reset()
        statusCharacteristic = nil
        controlCharacteristic = nil
        liveCharacteristic = nil
        shotCharacteristic = nil
        configCharacteristic = nil
        powerCharacteristic = nil
        batteryLevelCharacteristic = nil
        connectedPeripheral = nil
        status = nil
        powerStatus = nil
        liveSamples.removeAll()
        connectionState = .idle
    }

    private func fail(_ error: Error) {
        message = error.localizedDescription
    }

    private func finishDiscoveryIfPossible() {
        guard statusCharacteristic != nil,
              controlCharacteristic != nil,
              liveCharacteristic != nil,
              shotCharacteristic != nil,
              configCharacteristic != nil,
              let peripheral = connectedPeripheral else {
            return
        }
        connectionState = .ready(peripheral.name ?? "AimTracer")
    }
}

@MainActor
extension BLEManager: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            connectionState = .idle
        case .poweredOff:
            connectionState = .unavailable("Bluetooth ist ausgeschaltet")
        case .unauthorized:
            connectionState = .unavailable("Bluetooth-Zugriff fehlt")
        case .unsupported:
            connectionState = .unavailable("Bluetooth LE wird nicht unterstützt")
        case .resetting:
            connectionState = .unavailable("Bluetooth wird neu gestartet")
        case .unknown:
            connectionState = .unavailable("Bluetooth-Status unbekannt")
        @unknown default:
            connectionState = .unavailable("Bluetooth nicht verfügbar")
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
            ?? peripheral.name
            ?? "AimTracer"
        let device = DiscoveredAimTracer(
            id: peripheral.identifier,
            peripheral: peripheral,
            name: name,
            rssi: RSSI.intValue
        )
        if let index = discoveredDevices.firstIndex(where: { $0.id == device.id }) {
            discoveredDevices[index] = device
        } else {
            discoveredDevices.append(device)
        }
        discoveredDevices.sort { $0.rssi > $1.rssi }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectedPeripheral = peripheral
        peripheral.delegate = self
        connectionState = .discovering
        peripheral.discoverServices([
            AimTracerBLE.service,
            AimTracerBLE.batteryService
        ])
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        if let error { fail(error) }
        resetConnection()
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        if let error { fail(error) }
        resetConnection()
    }
}

@MainActor
extension BLEManager: @preconcurrency CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            fail(error)
            return
        }
        guard peripheral.services?.contains(where: {
            $0.uuid == AimTracerBLE.service
        }) == true else {
            message = "AimTracer-Service wurde nicht gefunden."
            return
        }
        for service in peripheral.services ?? [] {
            switch service.uuid {
            case AimTracerBLE.service:
                peripheral.discoverCharacteristics(
                    [
                        AimTracerBLE.status,
                        AimTracerBLE.control,
                        AimTracerBLE.live,
                        AimTracerBLE.shot,
                        AimTracerBLE.config,
                        AimTracerBLE.power
                    ],
                    for: service
                )
            case AimTracerBLE.batteryService:
                peripheral.discoverCharacteristics(
                    [AimTracerBLE.batteryLevel],
                    for: service
                )
            default:
                break
            }
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            fail(error)
            return
        }
        for characteristic in service.characteristics ?? [] {
            switch characteristic.uuid {
            case AimTracerBLE.status:
                statusCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
                peripheral.readValue(for: characteristic)
            case AimTracerBLE.control:
                controlCharacteristic = characteristic
            case AimTracerBLE.live:
                liveCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case AimTracerBLE.shot:
                shotCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case AimTracerBLE.config:
                configCharacteristic = characteristic
                peripheral.readValue(for: characteristic)
            case AimTracerBLE.power:
                powerCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
                peripheral.readValue(for: characteristic)
            case AimTracerBLE.batteryLevel:
                batteryLevelCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
                peripheral.readValue(for: characteristic)
            default:
                break
            }
        }
        finishDiscoveryIfPossible()
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error {
            fail(error)
            return
        }
        guard let data = characteristic.value else { return }

        do {
            switch characteristic.uuid {
            case AimTracerBLE.status:
                status = try AimTracerCodec.status(from: data)
            case AimTracerBLE.live:
                liveSamples.append(try AimTracerCodec.live(from: data))
                if liveSamples.count > 180 {
                    liveSamples.removeFirst(liveSamples.count - 180)
                }
            case AimTracerBLE.shot:
                if let shot = try shotAssembler.ingest(data) {
                    lastShot = shot
                    onShot?(shot)
                }
            case AimTracerBLE.config:
                configuration = try AimTracerCodec.configuration(from: data)
            case AimTracerBLE.power:
                powerStatus = try AimTracerCodec.powerStatus(from: data)
            case AimTracerBLE.batteryLevel:
                guard let level = data.first else {
                    throw AimTracerProtocolError.invalidLength
                }
                let previous = powerStatus
                powerStatus = DevicePowerStatus(
                    levelPercent: min(level, 100),
                    isCharging: previous?.isCharging ?? false,
                    externalPowerPresent:
                        previous?.externalPowerPresent ?? false,
                    chargeSignalActive:
                        previous?.chargeSignalActive ?? false,
                    isLow: level <= 20,
                    isCritical: level <= 5,
                    millivolts: previous?.millivolts ?? 0,
                    chargeCurrentMa: previous?.chargeCurrentMa ?? 50
                )
            default:
                break
            }
        } catch {
            fail(error)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error { fail(error) }
    }
}
