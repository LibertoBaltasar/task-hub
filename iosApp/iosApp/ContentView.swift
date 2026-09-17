import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose maneja su propio teclado
            .onOpenURL { url in
                // Callback del flujo de Google Sign-In (ver
                // Platform.ios.kt launchGoogleSignIn): solo procesamos URLs
                // con el scheme reverse-DNS del client ID iOS; cualquier
                // otro esquema se ignora.
                guard url.scheme == GoogleOAuthConfigKt.GOOGLE_IOS_REVERSED_CLIENT_ID else { return }
                let idToken = extractIdToken(from: url)
                // GoogleSignInResultHolder es un `object` de Kotlin:
                // Kotlin/Native lo expone a Swift como una clase con
                // singleton `.shared`, y `setResult(token: String?)` se
                // mapea a `setResult(token:)` (convención estándar de
                // interop Kotlin/Native ↔ Objective-C/Swift; a validar en
                // el primer build real en Xcode).
                GoogleSignInResultHolder.shared.setResult(token: idToken ?? "")
            }
    }
}

/// Extrae `id_token` del fragmento (`#...`) de la URL de callback OAuth de Google.
private func extractIdToken(from url: URL) -> String? {
    guard let fragment = url.fragment else { return nil }
    for pair in fragment.split(separator: "&") {
        let parts = pair.split(separator: "=", maxSplits: 1)
        if parts.count == 2, parts[0] == "id_token" {
            return String(parts[1]).removingPercentEncoding
        }
    }
    return nil
}
