import Foundation

@MainActor
final class AppModel: ObservableObject {
    let bluetooth: BLEManager
    let sessions: SessionStore

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
