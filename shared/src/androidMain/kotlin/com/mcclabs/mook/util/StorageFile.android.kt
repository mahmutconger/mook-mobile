package com.mcclabs.mook.util

import android.net.Uri
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorageMetadata

actual fun storageFileFromUri(uri: String): File? =
    runCatching { File(Uri.parse(uri)) }.getOrNull()

/**
 * Returns `null` so the Firebase SDK infers the type itself.
 *
 * A picked photo may be JPEG, PNG or WEBP, and the true type is only known through a
 * ContentResolver, which needs a Context this layer has no access to. The SDK resolves
 * the URI with its own Context, so its inference is the accurate answer here.
 */
actual fun photoUploadMetadata(): FirebaseStorageMetadata? = null
