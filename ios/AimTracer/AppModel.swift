import Foundation

final class AxisDisplayPreferences: ObservableObject {
    static let invertXAxisKey = "aimtracer.display.invertXAxis"
    static let invertYAxisKey = "aimtracer.display.invertYAxis"

    @Published var invertXAxis: Bool {
        didSet {
            UserDefaults.standard.set(invertXAxis, forKey: Self.invertXAxisKey)
        }
    }

    @Published var invertYAxis: Bool {
        didSet {
            UserDefaults.standard.set(invertYAxis, forKey: Self.invertYAxisKey)
        }
    }

    init(defaults: UserDefaults = .standard) {
        invertXAxis = defaults.bool(forKey: Self.invertXAxisKey)
        invertYAxis = defaults.bool(forKey: Self.invertYAxisKey)
    }

    var configuration: AxisDisplayConfiguration {
        AxisDisplayConfiguration(
            invertXAxis: invertXAxis,
            invertYAxis: invertYAxis
        )
    }

    static var storedConfiguration: AxisDisplayConfiguration {
        AxisDisplayConfiguration(
            invertXAxis: UserDefaults.standard.bool(forKey: invertXAxisKey),
            invertYAxis: UserDefaults.standard.bool(forKey: invertYAxisKey)
        )
    }
}

@MainActor
final class AppModel: ObservableObject {
    let bluetooth: BLEManager
    let sessions: SessionStore
    let axisDisplay = AxisDisplayPreferences()

    init() {
        let bluetooth = BLEManager()
        let sessions = SessionStore()
        self.bluetooth = bluetooth
        self.sessions = sessions
        bluetooth.onShot = { [weak sessions] shot in
            sessions?.record(shot)
        }
    }

    func startSession(program: TrainingProgram) {
        sessions.startSession(program: program)
        bluetooth.send(command: .startSession)
    }

    func stopSession(meytonScore: Double?) {
        bluetooth.send(command: .stopSession)
        sessions.stopSession(meytonScore: meytonScore)
    }
}
