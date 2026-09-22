package com.mcclabs.mook.data.sso

import com.mcclabs.mook.domain.sso.AuthStateStore

/** Platformun işletim sistemi destekli CSPRNG'sinden [size] bayt okur. */
internal expect fun platformSecureRandomBytes(size: Int): ByteArray

/**
 * Uygulamanın özel, yedeklenmeyen alanında duran state deposunu oluşturur.
 *
 * Şifreleme bilinçli olarak eklenmedi: değer zaten istemciye URL ile gönderiliyor,
 * 10 dakikada geçersizleşiyor ve tek kullanımlık. Uygulama sanal alanını okuyabilen bir
 * saldırgan için şifreleme anlamlı bir engel olmazdı. `EncryptedSharedPreferences` ise
 * androidx tarafından kullanımdan kaldırıldı.
 */
internal expect fun createAuthStateStore(): AuthStateStore
