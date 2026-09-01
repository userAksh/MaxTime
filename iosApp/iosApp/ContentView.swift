import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Matches MaxTimeColors.bg1 — Compose Multiplatform's iOS canvas doesn't reliably paint
/// behind the real status bar even with ignoresSafeArea, so without this backdrop the system
/// default (black) shows through as a mismatched strip at the top of every screen.
private let maxTimeBg1 = Color(red: 10.0 / 255, green: 12.0 / 255, blue: 7.0 / 255)

struct ContentView: View {
    var body: some View {
        ZStack {
            maxTimeBg1.ignoresSafeArea()
            ComposeView().ignoresSafeArea()
        }
    }
}
