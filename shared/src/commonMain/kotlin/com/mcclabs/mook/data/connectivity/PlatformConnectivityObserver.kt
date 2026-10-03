package com.mcclabs.mook.data.connectivity

import com.mcclabs.mook.domain.connectivity.ConnectivityObserver

/**
 * Android, `ConnectivityManager.NetworkCallback` ile gerçek zamanlı ağ değişikliklerini dinler.
 * iOS, `NWPathMonitor` (Network.framework) kullanır.
 */
expect fun createConnectivityObserver(): ConnectivityObserver
