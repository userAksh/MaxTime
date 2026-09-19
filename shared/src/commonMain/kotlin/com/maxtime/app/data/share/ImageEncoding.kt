package com.maxtime.app.data.share

import androidx.compose.ui.graphics.ImageBitmap

/**
 * `ImageBitmap` has no single cross-platform "give me the encoded bytes" call — Android wraps a
 * platform `android.graphics.Bitmap`, while iOS (and every other Skiko-backed target) wraps a
 * `org.jetbrains.skia.Bitmap`. Needed to turn a rendered share-card ([ShareCardTemplate], captured
 * via `GraphicsLayer.toImageBitmap()`) into bytes a share intent / UIActivityViewController can
 * actually hand off to another app.
 */
expect fun ImageBitmap.encodeToPngBytes(): ByteArray
