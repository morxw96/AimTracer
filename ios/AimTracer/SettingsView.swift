import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var bluetooth: BLEManager
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
                    "Kalibrierpunkt: Die Startwerte müssen mit montiertem "
                    + "Gehäuse an der LP300XT anhand echter Schüsse und "
                    + "Trockentraining überprüft werden."
                )
            }

            Section("Aufnahmefenster") {
                numberField("Vorlauf in ms", value: $draft.preTriggerMs)
                numberField("Nachlauf in ms", value: $draft.postTriggerMs)
                Stepper(
                    "Live-Rate: \(draft.liveRateHz) Hz",
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
                Text(
                    "Die aktuelle Visualisierung nimmt an, dass das Board "
                    + "flach montiert ist und USB-C nach hinten zeigt. Bei "
                    + "anderer Lage die Achsenzuordnung in MotionAnalysis.swift "
                    + "anpassen."
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
        LabeledContent(title) {
            TextField(title, value: value, format: .number)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 110)
        }
    }

    private func numberField(
        _ title: String,
        value: Binding<UInt8>
    ) -> some View {
        LabeledContent(title) {
            TextField(title, value: value, format: .number)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 110)
        }
    }
}
