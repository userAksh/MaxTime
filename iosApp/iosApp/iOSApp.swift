import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    // Universal Link handling lives in SceneDelegate.swift, wired in via
    // AppDelegate.application(_:configurationForConnecting:options:) — covers both a cold start
    // (scene(_:willConnectTo:options:)) and an already-running app (scene(_:continue:)). The
    // SwiftUI .onContinueUserActivity modifier was tried here first and proved unreliable for the
    // cold-start case specifically — confirmed via on-device diagnostic across repeated real
    // link taps.
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
