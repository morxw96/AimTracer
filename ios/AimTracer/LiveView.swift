import SwiftUI

struct LiveView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var bluetooth: BLEManager
    @EnvironmentObject private var sessions: SessionStore
    @State private var showsConnection = false
    @State private var showsSessionSetup = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                connectionCard
                sessionControls
                if let status = bluetooth.status,
                   status.capturing || status.transmitting {
                    HStack(spacing: 12) {
                        ProgressView()
                        VStack(alignment: .leading, spacing: 2) {
                            Text(
                                status.capturing
                                    ? "Schuss wird aufgezeichnet …"
                                    : "Schuss wird übertragen …"
                            )
                            .font(.headline)
                            Text(
                                status.transmitting
                                    ? "Paket \(status.transmittingSampleIndex)"
                                    : "Kurzer Nachlauf von 250 ms"
                            )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .padding()
                    .background(
                        .thinMaterial,
                        in: RoundedRectangle(cornerRadius: 18)
                    )
                }
                TraceCard(
                    shot: bluetooth.lastShot,
                    liveSamples: bluetooth.liveSamples
                )

                if let shot = bluetooth.lastShot {
                    ShotMetricsView(shot: shot)
                    if let session = sessions.activeSession {
                        LiveShotStandingCard(
                            shot: shot,
                            session: session
                        )
                    }
                }

                if let status = bluetooth.status {
                    StatusGrid(
                        status: status,
                        powerStatus: bluetooth.powerStatus
                    )
                }
            }
            .padding()
        }
        .navigationTitle("AimTracer")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showsConnection = true
                } label: {
                    Image(systemName: bluetooth.connectionState.isReady
                          ? "bolt.horizontal.circle.fill"
                          : "bolt.horizontal.circle")
                }
            }
        }
        .sheet(isPresented: $showsConnection) {
            ConnectionView()
        }
        .sheet(isPresented: $showsSessionSetup) {
            SessionSetupView { program in
                model.startSession(program: program)
                showsSessionSetup = false
            }
        }
        .alert(
            "AimTracer",
            isPresented: Binding(
                get: { bluetooth.message != nil },
                set: { if !$0 { bluetooth.message = nil } }
            )
        ) {
            Button("OK") { bluetooth.message = nil }
        } message: {
            Text(bluetooth.message ?? "")
        }
    }

    private var connectionCard: some View {
        HStack {
            Image(systemName: bluetooth.connectionState.isReady
                  ? "checkmark.circle.fill"
                  : "antenna.radiowaves.left.and.right")
                .foregroundStyle(
                    bluetooth.connectionState.isReady ? .mint : .secondary
                )
                .font(.title2)
            VStack(alignment: .leading) {
                Text(bluetooth.connectionState.label)
                    .font(.headline)
                Text(bluetooth.status?.calibrated == true
                     ? "Sensor bereit"
                     : "Gerät ruhig halten, bis die Nullung fertig ist")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let power = bluetooth.powerStatus {
                    Label(
                        powerText(for: power),
                        systemImage: batterySymbol(for: power)
                    )
                    .font(.caption)
                    .foregroundStyle(power.isCritical ? .red : .secondary)
                }
            }
            Spacer()
            Button("Verbinden") { showsConnection = true }
                .buttonStyle(.bordered)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }

    private func batterySymbol(for status: DevicePowerStatus) -> String {
        if status.isCharging {
            return "battery.100percent.bolt"
        }
        switch status.levelPercent {
        case 76...100: return "battery.100percent"
        case 51...75: return "battery.75percent"
        case 26...50: return "battery.50percent"
        case 6...25: return "battery.25percent"
        default: return "battery.0percent"
        }
    }

    private func powerText(for status: DevicePowerStatus) -> String {
        if status.isCharging {
            return "\(status.levelPercent) % • lädt"
        }
        if status.externalPowerPresent {
            return "\(status.levelPercent) % • USB angeschlossen"
        }
        return "\(status.levelPercent) %"
    }

    private var sessionControls: some View {
        HStack(spacing: 12) {
            if sessions.activeSessionID == nil {
                Button {
                    showsSessionSetup = true
                } label: {
                    Label("Session starten", systemImage: "record.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!bluetooth.connectionState.isReady)
            } else {
                Button(role: .destructive) {
                    model.stopSession()
                } label: {
                    Label("Session beenden", systemImage: "stop.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(
                    bluetooth.status?.capturing == true
                    || bluetooth.status?.transmitting == true
                )
            }
        }
    }
}

private struct SessionSetupView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var program: TrainingProgram = .lp40
    let onStart: (TrainingProgram) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Programm") {
                    Picker("Disziplin", selection: $program) {
                        ForEach(TrainingProgram.allCases) { program in
                            Text(program.title).tag(program)
                        }
                    }
                    if let count = program.plannedShotCount {
                        LabeledContent("Geplante Schüsse", value: "\(count)")
                    } else {
                        Text("Die Session hat keine feste Schusszahl.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Section {
                    Text(
                        "Für spätere Vergleiche werden nur Sessions desselben "
                            + "Programms gegenübergestellt."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Neue Session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Starten") { onStart(program) }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

private struct LiveShotStandingCard: View {
    let shot: ShotCapture
    let session: TrainingSession

    private var standing: ShotStanding? {
        SessionAnalysis.summary(for: session).standings.first {
            $0.shot.id == shot.id
        }
    }

    var body: some View {
        if let standing {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Einordnung in dieser Session")
                            .font(.headline)
                        Text(
                            "Vergleich mit \(session.shots.count) "
                                + (session.shots.count == 1
                                    ? "Schuss"
                                    : "Schüssen")
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("#\(standing.overallRank)")
                        .font(.title2.bold().monospacedDigit())
                        .foregroundStyle(.mint)
                }

                HStack {
                    rank(
                        "Ruhig halten",
                        standing.holdRank,
                        count: session.shots.count
                    )
                    rank(
                        "Abzug",
                        standing.triggerRank,
                        count: session.shots.count
                    )
                    rank(
                        "Nachhalten",
                        standing.followThroughRank,
                        count: session.shots.count
                    )
                }
                if session.shots.count < 3 {
                    Text(
                        "Die Rangfolge wird ab drei Schüssen "
                            + "aussagekräftiger."
                    )
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                }
            }
            .padding()
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
        }
    }

    private func rank(_ title: String, _ rank: Int, count: Int) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text("\(rank)/\(count)")
                .font(.headline.monospacedDigit())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct StatusGrid: View {
    let status: DeviceStatus
    let powerStatus: DevicePowerStatus?

    var body: some View {
        Grid(alignment: .leading, horizontalSpacing: 24, verticalSpacing: 10) {
            GridRow {
                statusItem("Schüsse", "\(status.shotCount)")
                statusItem("Mikrofon", "\(status.microphonePeak)")
            }
            GridRow {
                statusItem("Puffer", "\(status.bufferedSamples)")
                statusItem("Verworfen", "\(status.droppedTriggers)")
            }
            GridRow {
                statusItem("Firmware", status.firmwareVersion)
                statusItem("Rate", "\(status.sampleRateHz) Hz")
            }
            GridRow {
                statusItem(
                    "Übertragung",
                    status.transmitting
                        ? "#\(status.transmittingShotID)"
                        : "Bereit"
                )
                statusItem(
                    "TX-Sample",
                    status.transmitting
                        ? "\(status.transmittingSampleIndex)"
                        : "–"
                )
            }
            if let powerStatus {
                GridRow {
                    statusItem(
                        "Akku",
                        powerStatus.isCharging
                            ? "\(powerStatus.levelPercent) % (lädt)"
                            : powerStatus.externalPowerPresent
                                ? "\(powerStatus.levelPercent) % (USB)"
                                : "\(powerStatus.levelPercent) %"
                    )
                    statusItem(
                        "Akkuspannung",
                        String(
                            format: "%.2f V",
                            Double(powerStatus.millivolts) / 1000.0
                        )
                    )
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }

    private func statusItem(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.headline.monospacedDigit())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
