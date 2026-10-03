package com.mcclabs.mook.data

import com.mcclabs.mook.domain.moderation.BannedUserException
import com.mcclabs.mook.domain.moderation.ModerationGate
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions

/**
 * `Firebase.functions.httpsCallable(name)`'in yerini alan, TÜM Cloud Functions çağrılarının
 * geçtiği tek nokta ("interceptor") — Gereksinim 1.13.
 *
 * Bu depoda paylaşılan bir ağ istemcisi katmanı (Ktor/OkHttp interceptor'ı gibi) yoktu; her
 * repository kendi `Firebase.functions.httpsCallable(...)` çağrısını doğrudan yapıyordu.
 * Aynı [appFirestore]'un Firestore için yaptığı gibi, bu fonksiyon TEK bir global geçiş
 * noktası sağlar: [ModerationGate.isBanned] `true` iken çağrılırsa istek sunucuya hiç
 * gitmeden [BannedUserException] fırlatılır. Dönüş tipi orijinal `httpsCallable()` ile
 * birebir aynıdır (yalnızca bir vekil/geçiş çağrısıdır), bu yüzden mevcut tüm çağıran kodlar
 * (`.invoke(...)`, `.invoke(payload)`) değişmeden çalışmaya devam eder.
 *
 * `Firebase.functions.httpsCallable(...)` çağıran HER yer bunun yerine bunu kullanmalıdır.
 */
fun appHttpsCallable(name: String) = run {
    if (ModerationGate.isBanned.value) throw BannedUserException()
    Firebase.functions.httpsCallable(name)
}
