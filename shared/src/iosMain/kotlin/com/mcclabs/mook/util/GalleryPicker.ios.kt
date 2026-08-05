package com.mcclabs.mook.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val JPEG_QUALITY = 0.9
private const val IMAGE_TYPE_IDENTIFIER = "public.image"

/**
 * Re-encodes [imageData] as JPEG in the temporary directory and returns its `file://` URL.
 *
 * Photos shot on device are often HEIC, which Coil's iOS decoder cannot read, so the bytes
 * are decoded and re-encoded as JPEG rather than copied as-is. Returns `null` if the data
 * cannot be encoded or the file cannot be written.
 */
@OptIn(BetaInteropApi::class)
private fun writeToTemporaryJpeg(imageData: NSData): String? {
    val image: UIImage = UIImage(data = imageData)
    val jpegData: NSData = UIImageJPEGRepresentation(image, JPEG_QUALITY) ?: return null
    val path = NSTemporaryDirectory() + "walkmatch_" + NSUUID().UUIDString() + ".jpg"
    val url = NSURL.fileURLWithPath(path)
    return if (jpegData.writeToURL(url, atomically = true)) url.absoluteString else null
}

/** Topmost presented controller, so the picker is not attached to a covered one. */
private fun topmostViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class GalleryPickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {

    var onImagePicked: (String) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)

        val itemProvider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider ?: return
        if (!itemProvider.hasItemConformingToTypeIdentifier(IMAGE_TYPE_IDENTIFIER)) return

        // Loading is asynchronous and completes off the main thread.
        itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE_IDENTIFIER) { data, _ ->
            val uri = data?.let(::writeToTemporaryJpeg) ?: return@loadDataRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onImagePicked(uri) }
        }
    }
}

@Composable
actual fun rememberGalleryPicker(onImagePicked: (String) -> Unit): GalleryPicker {
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    val delegate = remember { GalleryPickerDelegate() }

    // The delegate outlives each launch, so keep its callback pointing at the
    // latest composition instead of capturing a stale lambda.
    DisposableEffect(delegate) {
        delegate.onImagePicked = { currentOnImagePicked(it) }
        onDispose { delegate.onImagePicked = {} }
    }

    return remember(delegate) {
        GalleryPicker {
            val configuration = PHPickerConfiguration().apply {
                setFilter(PHPickerFilter.imagesFilter())
                setSelectionLimit(1)
            }
            val controller = PHPickerViewController(configuration = configuration)
            controller.delegate = delegate
            topmostViewController()?.presentViewController(controller, animated = true, completion = null)
        }
    }
}
