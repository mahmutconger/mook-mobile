package com.mcclabs.mook

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * Initializes the Android-only RevenueCat SDK before Compose or authentication start.
 * The Firebase uid is associated later by [SubscriptionRepository] after sign-in.
 */
class MookApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!Purchases.isConfigured) {
            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_PUBLIC_SDK_KEY).build(),
            )
        }

        // RevenueCat customer identity must always be the Firebase uid. This listener
        // also covers account switches and logout, rather than relying on any one UI.
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Purchases.sharedInstance.logOut()
            } else {
                Purchases.sharedInstance.logIn(uid)
            }
        }
    }
}
