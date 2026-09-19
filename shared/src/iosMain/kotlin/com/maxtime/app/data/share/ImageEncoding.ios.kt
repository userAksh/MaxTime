package com.maxtime.app.data.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

// drawShareCard now paints a fully opaque canvas edge to edge (see its own doc comment) — there's
// no meaningful transparency left in this bitmap to preserve, so this stays a plain conversion.
actual fun ImageBitmap.encodeToPngBytes(): ByteArray {
    val skiaImage = Image.makeFromBitmap(asSkiaBitmap())
    return skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: error("Failed to encode share card image to PNG")
}
