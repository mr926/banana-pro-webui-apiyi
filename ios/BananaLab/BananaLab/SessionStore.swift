import Foundation

struct PersistedSession: Codable, Equatable {
    var token: String
    var loggedInAt: Date
}

actor SessionStore {
    static let shared = SessionStore()

    private let keychain: KeychainStore
    private let defaults: UserDefaults
    private let sessionAccount = "auth-session"
    private let serverURLKey = "banana-lab.server-url"
    private let fallbackServerURL = "http://127.0.0.1:8787"
    private let sessionLifetime: TimeInterval = 60 * 60 * 24

    init(keychain: KeychainStore = KeychainStore(), defaults: UserDefaults = .standard) {
        self.keychain = keychain
        self.defaults = defaults
    }

    func storedServerURL() -> String {
        let value = defaults.string(forKey: serverURLKey)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? fallbackServerURL : value
    }

    func saveServerURL(_ url: String) {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        defaults.set(trimmed, forKey: serverURLKey)
    }

    func saveSession(token: String, loggedInAt: Date = .now) {
        let session = PersistedSession(token: token, loggedInAt: loggedInAt)
        guard let data = try? JSONEncoder().encode(session) else { return }
        keychain.save(data, account: sessionAccount)
    }

    func loadSession() -> PersistedSession? {
        guard let data = keychain.read(account: sessionAccount),
              let session = try? JSONDecoder().decode(PersistedSession.self, from: data) else {
            return nil
        }

        guard Date().timeIntervalSince(session.loggedInAt) <= sessionLifetime else {
            clearSession()
            return nil
        }
        return session
    }

    func clearSession() {
        keychain.delete(account: sessionAccount)
    }

    func cookieHeader() -> String? {
        guard let session = loadSession() else { return nil }
        return "banana_ui_session=\(session.token)"
    }
}
