import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            NavigationStack {
                LiveView()
            }
            .tabItem {
                Label("Live", systemImage: "scope")
            }

            NavigationStack {
                SessionsView()
            }
            .tabItem {
                Label("Sessions", systemImage: "clock.arrow.circlepath")
            }

            NavigationStack {
                SettingsView()
            }
            .tabItem {
                Label("Kalibrierung", systemImage: "slider.horizontal.3")
            }
        }
        .tint(.mint)
    }
}
