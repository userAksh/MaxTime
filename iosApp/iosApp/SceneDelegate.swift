import UIKit
import shared

/// Explicit UIWindowSceneDelegate, wired in via AppDelegate's
/// application(_:configurationForConnecting:options:) — the officially documented, reliable way
/// to catch a Universal Link regardless of whether the app is cold-starting (willConnectTo,
/// reading connectionOptions.userActivities) or already running/backgrounded (continue
/// userActivity). The SwiftUI `.onContinueUserActivity` view modifier tried first was unreliable
/// specifically for the cold-start case — confirmed via on-device diagnostic
/// (ReferralDeepLink.lastHandledUrl stayed nil across repeated real cold-start link taps).
class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        if let userActivity = connectionOptions.userActivities.first(where: { $0.activityType == NSUserActivityTypeBrowsingWeb }),
           let url = userActivity.webpageURL {
            ReferralDeepLink.shared.handle(url: url.absoluteString)
        }
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL else { return }
        ReferralDeepLink.shared.handle(url: url.absoluteString)
    }
}
