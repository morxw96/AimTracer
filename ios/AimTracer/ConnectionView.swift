import SwiftUI

struct ConnectionView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var bluetooth: BLEManager

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if bluetooth.discoveredDevices.isEmpty {
                        HStack {
                            ProgressView()
                            Text("Suche nach AimTracer …")
                                .foregroundStyle(.secondary)
                        }
                    }
                    ForEach(bluetooth.discoveredDevices) { device in
                        Button {
                            bluetooth.connect(to: device)
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(device.name)
                                        .foregroundStyle(.primary)
                                    Text(device.id.uuidString)
                                        .font(.caption2.monospaced())
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("\(device.rssi) dBm")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                } header: {
                    Text("Geräte")
                } footer: {
                    Text("Es werden nur Geräte mit dem AimTracer-Service angezeigt.")
                }

                if bluetooth.connectionState.isReady {
                    Section {
                        Button("Verbindung trennen", role: .destructive) {
                            bluetooth.disconnect()
                        }
                    }
                }
            }
            .navigationTitle("Bluetooth")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fertig") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        bluetooth.startScanning()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .onAppear {
                if !bluetooth.connectionState.isReady {
                    bluetooth.startScanning()
                }
            }
            .onChange(of: bluetooth.connectionState) { _, state in
                if state.isReady { dismiss() }
            }
        }
    }
}
