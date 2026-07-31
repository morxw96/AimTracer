import SwiftUI

@main
struct AimTracerApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .environmentObject(model.bluetooth)
                .environmentObject(model.sessions)
        }
    }
}
