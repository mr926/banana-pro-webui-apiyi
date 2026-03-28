import Foundation
import UserNotifications

@MainActor
final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationCoordinator()

    private let center = UNUserNotificationCenter.current()

    func configure() {
        center.delegate = self
        let category = UNNotificationCategory(
            identifier: "BANANALAB_GENERATION_RESULT",
            actions: [],
            intentIdentifiers: [],
            options: .customDismissAction
        )
        center.setNotificationCategories([category])
    }

    func requestAuthorizationIfNeeded() async {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            guard granted else { return }
            try? await center.setBadgeCount(0)
        } catch {
            return
        }
    }

    func postGenerationNotification(success: Bool, title: String, message: String) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = message
        content.sound = .default
        content.categoryIdentifier = "BANANALAB_GENERATION_RESULT"
        content.userInfo = ["kind": success ? "success" : "failure"]

        let request = UNNotificationRequest(
            identifier: "banana-lab-\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        do {
            try await center.add(request)
        } catch {
            // If notification scheduling fails, keep the in-app banner as the fallback.
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
