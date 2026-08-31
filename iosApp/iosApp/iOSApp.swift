import SwiftUI
import FirebaseCore
import FirebaseMessaging
import UserNotifications
import Shared

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        
        // Push Notification Setup
        UNUserNotificationCenter.current().delegate = self
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(options: authOptions) { granted, error in
            if let error = error {
                print("Notification authorization error: \(error)")
            }
        }
        
        application.registerForRemoteNotifications()
        Messaging.messaging().delegate = self
        
        return true
    }
    
    // Pass APNs token to Firebase
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }
    
    // Receive FCM token
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken = fcmToken else { return }

        // FirebaseMessaging is linked here, not into the shared framework, so iOS
        // cannot read its own token from Kotlin — it has to be handed in. The shared
        // PushTokenRepository writes it to users/{uid}.fcmTokens, which is the array
        // the sendMessage Cloud Function pushes notifications to. Without this call
        // the token exists only in this process and no notification is ever delivered.
        PushTokenBridgeKt.onPushTokenReceived(token: fcmToken)
    }
    
    // Show notification while app is in foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
