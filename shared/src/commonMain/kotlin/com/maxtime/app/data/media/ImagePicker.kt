package com.maxtime.app.data.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import dev.gitlive.firebase.storage.Data

/** A picked image, both wrapped for Firebase Storage's `putData` ([data] — GitLive's own
 *  multiplatform type, ByteArray on Android / NSData on iOS) and as a plain [bytes] array so the
 *  UI can decode + show an instant local preview without waiting on the upload or re-fetching
 *  anything over the network. */
class PickedImage(val data: Data, val bytes: ByteArray, val contentType: String)

/** Platform handle needed to launch the gallery picker — mirrors [com.maxtime.app.data.location.LocationContext]'s
 *  shape/spirit exactly (an Activity + a permission/launcher bridge on Android, nothing on iOS). */
expect class ImagePickerContext

@Composable
expect fun rememberImagePickerContext(): ImagePickerContext

/** Opens the platform's native photo-gallery picker (Android Photo Picker / PHPickerViewController)
 *  restricted to images, and suspends for the whole round trip. Returns null if the user backed
 *  out without picking anything — never an exception for that case. */
expect suspend fun pickProfilePhoto(context: ImagePickerContext): PickedImage?

/** Decodes raw image bytes for an immediate local preview (BitmapFactory on Android, Skia on
 *  iOS — Compose's ImageBitmap is backed by a different native type per platform, so this can't
 *  be a shared implementation). Null on anything unparseable rather than throwing — a bad decode
 *  should fall back to the picker's placeholder state, not crash the screen. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
