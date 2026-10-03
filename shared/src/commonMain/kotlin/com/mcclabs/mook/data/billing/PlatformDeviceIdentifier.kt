package com.mcclabs.mook.data.billing

/**
 * Gereksinim 1 (Faz 4): bu cihaz için kararlı, yarı kalıcı bir tanımlayıcı — hiçbir zaman
 * ham (donanıma özgü) haliyle DÖNMEZ, her zaman geri döndürülemez biçimde özetlenmiş (hashed)
 * olarak sağlanır (bkz. Android `actual` uygulamasının KDoc'u). Yalnızca
 * [SubscriptionRepository.deviceTrialEligibility]/[SubscriptionRepository.recordDeviceTrialConsumption]
 * için, çoklu hesap deneme suistimalini (Gereksinim 2.7 & 2.8) önlemek amacıyla kullanılır —
 * başka HİÇBİR amaç için (ör. analitik, reklam kimliği) kullanılmamalıdır.
 *
 * iOS ticari akışı henüz devreye alınmadığından iOS tarafı boş dize döner; bu, cihaz
 * kapısının (device gate) o platformda sessizce hiçbir şeyi ENGELLEMEMESİNİ sağlar
 * (`deviceTrialEligibility` boş kimlikte `false` döner — bkz. Android `actual`'ın çağıranı).
 */
expect fun platformDeviceIdentifier(): String
