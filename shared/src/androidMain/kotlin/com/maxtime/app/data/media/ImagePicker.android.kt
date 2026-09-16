package com.maxtime.app.data.media

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.gitlive.firebase.storage.Data
import kotlinx.coroutines.CompletableDeferred

actual class ImagePickerContext(
    val contentResolver: ContentResolver,
    val launch: suspend () -> Uri?
)

@Composable
actual fun rememberImagePickerContext(): ImagePickerContext {
    val contentResolver = LocalContext.current.contentResolver
    var pending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        pending?.complete(uri)
        pending = null
    }
    return remember(contentResolver, launcher) {
        ImagePickerContext(
            contentResolver = contentResolver,
            launch = {
                val deferred = CompletableDeferred<Uri?>()
                pending = deferred
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                deferred.await()
            }
        )
    }
}

actual suspend fun pickProfilePhoto(context: ImagePickerContext): PickedImage? {
    val uri = context.launch() ?: return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    return PickedImage(Data(bytes), bytes, mimeType)
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
