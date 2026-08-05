package com.mcclabs.mook.util

import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorageMetadata
import dev.gitlive.firebase.storage.storageMetadata
import platform.Foundation.NSURL

actual fun storageFileFromUri(uri: String): File? =
    NSURL.URLWithString(uri)?.let { File(it) }

/**
 * Declares JPEG, which is always true here: the gallery picker re-encodes every chosen
 * photo to JPEG before handing over a file URL. Stating it beats relying on the SDK
 * guessing from the file extension.
 */
actual fun photoUploadMetadata(): FirebaseStorageMetadata? =
    storageMetadata { contentType = "image/jpeg" }
