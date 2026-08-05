package com.mcclabs.mook.util

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
actual fun rememberGalleryPicker(onImagePicked: (String) -> Unit): GalleryPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)

    // The system photo picker grants read access to the returned URI without any
    // storage permission, but that grant is temporary: it can be revoked before Coil
    // renders a preview or Firebase uploads the file, surfacing as a SecurityException.
    // So we copy the bytes into app-private cache *now*, while the grant is alive, and
    // hand back a stable file:// URI that stays readable for the rest of the session.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localUri = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                if (localUri != null) currentOnImagePicked(localUri)
            }
        }
    }

    return remember(launcher) {
        GalleryPicker {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}

/**
 * Copies the picked image into the app's cache directory and returns a `file://` URI
 * string, or `null` if the copy fails. Reading happens immediately in the picker
 * callback, while the temporary read grant on [uri] is still valid.
 */
private fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    val extension = when (context.contentResolver.getType(uri)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val dir = File(context.cacheDir, "picked_images").apply { mkdirs() }
    val target = File(dir, "${UUID.randomUUID()}.$extension")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    Uri.fromFile(target).toString()
}.getOrNull()
