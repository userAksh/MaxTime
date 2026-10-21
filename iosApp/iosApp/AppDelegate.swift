import UIKit
import FirebaseCore
import FirebaseAuth
import shared

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()

        // Environment derived from the bundle ID rather than a new Info.plist key — matches the
        // existing per-scheme distinction (REVERSED_APP_ID_URL_SCHEME etc.) without adding a new
        // config mechanism. One Mixpanel project across all three environments (see
        // docs/analytics/dashboards-spec.md) — same token as Android's local.properties
        // MIXPANEL_TOKEN, filtered by the `environment` property on each event instead of a
        // separate project per build. Not a secret: Mixpanel's project token is write-only
        // (ingest events, nothing else) and ships inside every client binary by design.
        let bundleId = Bundle.main.bundleIdentifier ?? ""
        let environment = bundleId.hasSuffix(".staging") ? "staging" : (bundleId.hasSuffix(".beta") ? "beta" : "production")
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        AnalyticsTracker.shared.initialize(
            platform: "ios",
            appVersion: appVersion,
            environment: environment,
            mixpanelToken: "cf1724a8595ec5e120f39c7651869629"
        )
        #if targetEnvironment(simulator)
        // The Simulator has no real APNs token; this lets phone-auth verification
        // proceed via the reCAPTCHA fallback instead of waiting on a silent push.
        Auth.auth().settings?.isAppVerificationDisabledForTesting = true
        #endif
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Auth.auth().setAPNSToken(deviceToken, type: .unknown)
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if Auth.auth().canHandleNotification(userInfo) {
            completionHandler(.noData)
            return
        }
        completionHandler(.newData)
    }

    // Points the default SwiftUI-managed window scene at SceneDelegate instead of iOS's own
    // implicit one — the one piece of setup a SwiftUI App needs to receive Universal Links via
    // UIWindowSceneDelegate, no Info.plist UIApplicationSceneManifest entry required.
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}
