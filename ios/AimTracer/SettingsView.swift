import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var bluetooth: BLEManager
    @EnvironmentObject private var axisDisplay: AxisDisplayPreferences
    @State private var draft = DeviceConfiguration.default

    var body: some View {
        Form {
            Section {
                Picker("Trigger", selection: $draft.triggerMode) {
                    ForEach(TriggerMode.allCases) { mode in
                        Text(mode.title).tag(mode)
                    }
                }
                numberField(
                    "Mikrofon-Schwelle",
                    value: $draft.microphoneThreshold
                )
                numberField(
                    "Accel-Deltaschwelle",
                    value: $draft.accelerationThreshold
                )
                numberField(
                    "Gyro-Schwelle",
                    value: $draft.gyroThreshold
                )
                numberField(
                    "Koinzidenz in ms",
                    value: $draft.coincidenceMs
                )
                numberField(
                    "Sperrzeit in ms",
                    value: $draft.refractoryMs
                )
            } header: {
                Text("Erkennung")
            } footer: {
                Text(
                    L10n.text(
                        "Kalibrierpunkt: Die Startwerte müssen mit montiertem "
                            + "Gehäuse an der LP300XT anhand echter Schüsse und "
                            + "Trockentraining überprüft werden."
                    )
                )
            }

            Section("Aufnahmefenster") {
                numberField("Vorlauf in ms", value: $draft.preTriggerMs)
                numberField("Nachlauf in ms", value: $draft.postTriggerMs)
                Stepper(
                    L10n.format("Live-Rate: %d Hz", draft.liveRateHz),
                    value: $draft.liveRateHz,
                    in: 5...50,
                    step: 5
                )
                LabeledContent("IMU-Rate", value: "\(draft.sampleRateHz) Hz")
            }

            Section {
                Button("Einstellungen an Gerät senden") {
                    bluetooth.send(configuration: draft)
                }
                .disabled(!bluetooth.connectionState.isReady)

                Button("Gyro-Nullpunkt neu kalibrieren") {
                    bluetooth.send(command: .calibrate)
                }
                .disabled(!bluetooth.connectionState.isReady)

                Button("Manuellen Testtrigger setzen") {
                    bluetooth.send(command: .manualTrigger)
                }
                .disabled(!bluetooth.connectionState.isReady)
            }

            Section("Montage") {
                Toggle(
                    "X-Achse invertieren",
                    isOn: $axisDisplay.invertXAxis
                )
                Toggle(
                    "Y-Achse invertieren",
                    isOn: $axisDisplay.invertYAxis
                )
                Text(
                    L10n.text(
                        "Festes AimTracer-Profil: Platinenunterseite nach oben, "
                            + "Sensor-/Bestückungsseite nach unten und USB-C zum "
                            + "Schützen. Die Rohdaten bleiben unverändert; die App "
                            + "zeigt standardmäßig rechts als +gz und oben als +gy "
                            + "an; gx ist die Rollachse. X/Y-Invertierung verändert "
                            + "nur den Graphen, nicht die Scores."
                    )
                )
            }

            Section("Über AimTracer") {
                LabeledContent("AimTracer") {
                    Text("by Moritz Wenzel")
                        .foregroundStyle(.secondary)
                }
                LabeledContent(
                    "App-Version",
                    value: Bundle.main.infoDictionary?["CFBundleShortVersionString"]
                        as? String ?? "–"
                )
            }
        }
        .navigationTitle("Kalibrierung")
        .onAppear {
            if let configuration = bluetooth.configuration {
                draft = configuration
            }
        }
        .onReceive(bluetooth.$configuration) { configuration in
            if let configuration {
                draft = configuration
            }
        }
    }

    private func numberField(
        _ title: String,
        value: Binding<UInt16>
    ) -> some View {
        LabeledContent(L10n.text(title)) {
            TextField(L10n.text(title), value: value, format: .number)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 110)
        }
    }

    private func numberField(
        _ title: String,
        value: Binding<UInt8>
    ) -> some View {
        LabeledContent(L10n.text(title)) {
            TextField(L10n.text(title), value: value, format: .number)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 110)
        }
    }
}
