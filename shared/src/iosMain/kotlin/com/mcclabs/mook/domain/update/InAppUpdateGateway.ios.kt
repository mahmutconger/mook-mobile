package com.mcclabs.mook.domain.update

/** iOS ticari akışı henüz devreye alınmadığından no-op sağlayıcı (bkz. Gereksinim 2.15'teki eşdeğer iOS no-op'lar). */
actual fun createInAppUpdateGateway(): InAppUpdateGateway = object : InAppUpdateGateway {
    override suspend fun startImmediateUpdate(): Boolean = false
}
