package com.mcclabs.mook.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Handle for opening the system photo gallery.
 *
 * Obtain one with [rememberGalleryPicker] and call [launch] from a click handler.
 */
@Stable
fun interface GalleryPicker {
    fun launch()
}

/**
 * Remembers a [GalleryPicker] that opens the platform's gallery and reports the
 * chosen image back through [onImagePicked].
 *
 * [onImagePicked] receives a URI string that Coil can load directly, and is not
 * called when the user cancels. The URI is only guaranteed to be readable for
 * the lifetime of the current process, so upload it rather than persisting it.
 */
@Composable
expect fun rememberGalleryPicker(onImagePicked: (String) -> Unit): GalleryPicker
