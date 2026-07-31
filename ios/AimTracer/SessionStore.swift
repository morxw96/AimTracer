import Foundation

@MainActor
final class SessionStore: ObservableObject {
    @Published private(set) var sessions: [TrainingSession] = []
    @Published private(set) var activeSessionID: UUID?

    private let fileURL: URL

    init(fileURL: URL? = nil) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let base = FileManager.default.urls(
                for: .applicationSupportDirectory,
                in: .userDomainMask
            ).first!
            let directory = base.appendingPathComponent(
                "AimTracer",
                isDirectory: true
            )
            try? FileManager.default.createDirectory(
                at: directory,
                withIntermediateDirectories: true
            )
            self.fileURL = directory.appendingPathComponent("sessions.json")
        }
        load()
    }

    var activeSession: TrainingSession? {
        guard let activeSessionID else { return nil }
        return sessions.first(where: { $0.id == activeSessionID })
    }

    func startSession(program: TrainingProgram = .freeTraining) {
        if activeSessionID != nil { return }
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        let session = TrainingSession(
            name: "\(program.title) \(formatter.string(from: Date()))",
            program: program
        )
        sessions.insert(session, at: 0)
        activeSessionID = session.id
        save()
    }

    func stopSession() {
        guard let activeSessionID,
              let index = sessions.firstIndex(where: { $0.id == activeSessionID })
        else { return }
        sessions[index].endedAt = Date()
        self.activeSessionID = nil
        save()
    }

    func record(_ shot: ShotCapture) {
        if activeSessionID == nil {
            startSession(program: .freeTraining)
        }
        guard let activeSessionID,
              let index = sessions.firstIndex(where: { $0.id == activeSessionID })
        else { return }
        sessions[index].shots.append(shot)
        save()
    }

    func deleteSession(id: UUID) {
        sessions.removeAll(where: { $0.id == id })
        if activeSessionID == id {
            activeSessionID = nil
        }
        save()
    }

    func deleteShot(sessionID: UUID, shotID: UUID) {
        guard let sessionIndex = sessions.firstIndex(
            where: { $0.id == sessionID }
        ) else { return }
        sessions[sessionIndex].shots.removeAll { $0.id == shotID }
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        if let loaded = try? decoder.decode([TrainingSession].self, from: data) {
            sessions = loaded
            // A prior app termination always closes a still-open local session.
            for index in sessions.indices where sessions[index].endedAt == nil {
                sessions[index].endedAt = sessions[index].startedAt
            }
        }
    }

    private func save() {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(sessions) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}
