package com.mcclabs.mook.data.billing

import com.mcclabs.mook.domain.billing.PendingActionQueue

/** Android `SharedPreferences` ile, iOS `NSUserDefaults` ile — ikisi de senkron diske yazar. */
expect fun createPendingActionQueue(): PendingActionQueue
