package com.mcclabs.mook.domain.moderation

import com.mcclabs.mook.domain.model.ChatRoom

/**
 * Kullanıcı engelleme kuralları — saf yardımcılar (Firebase'e dokunmaz, test edilebilir).
 *
 * Engel listesi `users/{uid}.blockedUsers` dizisinde tutulur. İstemci yalnızca KENDİ
 * listesini okuyabilir (Firestore kuralları başka bir kullanıcının `users` belgesini
 * okumaya izin vermez); karşı tarafın kendisini engelleyip engellemediğini istemci
 * bilemez. O yön sunucuda (`sendMessage`, `swipe`, `getDiscoverFeed`) uygulanır ve
 * istemciye [com.mcclabs.mook.domain.model.ChatSendError.BLOCKED] olarak döner.
 */

/**
 * Firestore'dan okunan ham `blockedUsers` değerini temiz bir kümeye çevirir.
 * Alan yoksa veya boş girdiler varsa bunlar yok sayılır.
 */
fun blockedUidsFrom(raw: List<String>?): Set<String> =
    raw.orEmpty().filter { it.isNotBlank() }.toSet()

/**
 * Engellenen kişilerle olan sohbetleri listeden çıkarır. Engel kaldırılırsa sohbet
 * listede yeniden görünür; sohbet belgesi silinmez.
 */
fun List<ChatRoom>.withoutBlockedPeers(blocked: Set<String>): List<ChatRoom> =
    if (blocked.isEmpty()) this else filterNot { it.peerUid in blocked }

/**
 * Sohbet ekranında yeni mesaj yazılıp yazılamayacağı.
 *
 * @param isReadOnly Sohbetin odası kapatıldı (Gereksinim 1.7).
 * @param isBlocked İki kullanıcıdan biri diğerini engelledi.
 */
fun canComposeInChat(isReadOnly: Boolean, isBlocked: Boolean): Boolean =
    !isReadOnly && !isBlocked
