package com.maxtime.app.data.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.gitlive.firebase.storage.Data
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.posix.memcpy

actual class ImagePickerContext

@Composable
actual fun rememberImagePickerContext(): ImagePickerContext = remember { ImagePickerContext() }

@OptIn(ExperimentalForeignApi::class)
actual suspend fun pickProfilePhoto(context: ImagePickerContext): PickedImage? =
    suspendCancellableCoroutine { cont ->
        val root = topMostViewController()
        if (root == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter()
        config.selectionLimit = 1

        val picker = PHPickerViewController(configuration = config)
        val delegate = PickerDelegate(cont)
        picker.delegate = delegate
        // Keep the delegate alive for the continuation's lifetime — without a strong reference
        // held somewhere, ARC is free to deallocate it before PHPicker's callback fires.
        retainedPickerDelegate = delegate
        cont.invokeOnCancellation { retainedPickerDelegate = null }

        root.presentViewController(picker, animated = true, completion = null)
    }

@OptIn(ExperimentalForeignApi::class)
private var retainedPickerDelegate: NSObject? = null

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(
    private val cont: CancellableContinuation<PickedImage?>
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            if (cont.isActive) cont.resume(null)
            retainedPickerDelegate = null
            return
        }
        provider.loadDataRepresentationForTypeIdentifier(
            typeIdentifier = "public.image",
            completionHandler = { data: NSData?, _: NSError? ->
                if (cont.isActive) {
                    cont.resume(data?.let { PickedImage(Data(it), it.toByteArray(), "image/jpeg") })
                }
                retainedPickerDelegate = null
            }
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length.convert())
        }
    }
    return bytes
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }

@OptIn(ExperimentalForeignApi::class)
private fun topMostViewController(): UIViewController? {
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull() ?: return null
    val keyWindow = windowScene.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?: windowScene.windows.filterIsInstance<UIWindow>().firstOrNull()
    var top = keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
