package com.mcclabs.mook.util

import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorageMetadata

/**
 * Wraps a platform URI string (an Android `content://` URI or an iOS `file://` URL)
 * as a Firebase Storage [File], or returns `null` if the URI cannot be parsed.
 */
expect fun storageFileFromUri(uri: String): File?

/**
 * Metadata for a photo upload, or `null` to let the platform SDK decide.
 *
 * The Storage rules reject anything whose content type is not an image, and each
 * platform knows the true type by a different route — so neither a hardcoded value
 * nor blanket SDK inference is correct for both.
 */
expect fun photoUploadMetadata(): FirebaseStorageMetadata?
