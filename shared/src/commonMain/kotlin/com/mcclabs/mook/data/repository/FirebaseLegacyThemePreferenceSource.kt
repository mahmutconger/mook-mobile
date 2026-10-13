package com.mcclabs.mook.data.repository

import com.mcclabs.mook.data.appFirestore
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

/** Eski hesap ayarı yalnızca yerel tercih henüz belirlenmemişse bir kez taşınır. */
class FirebaseLegacyThemePreferenceSource : LegacyThemePreferenceSource {
    override suspend fun read(): Boolean? {
        val uid = Firebase.auth.currentUser?.uid ?: return null
        val document = appFirestore.collection("users").document(uid).get()
        return runCatching { document.get<Boolean>("isDarkMode") }.getOrNull()
    }
}
