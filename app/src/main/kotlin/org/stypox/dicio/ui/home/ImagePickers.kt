package org.stypox.dicio.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.stypox.dicio.skills.image.loadImage
import java.io.File

/** The three ways an image reaches Hey re: the camera, the photo picker and the keyboard. */
class ImagePickers(
    val takePhoto: () -> Unit,
    val choosePhoto: () -> Unit,
    val pasteImage: (Uri) -> Unit,
)

@Composable
fun rememberImagePickers(onImage: (Bitmap) -> Unit): ImagePickers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun show(uri: Uri) {
        scope.launch { withContext(Dispatchers.IO) { loadImage(context, uri) }?.let(onImage) }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::show)
    }
    val photoUri = remember {
        val file = File(context.cacheDir, "camera/photo.jpg").also { it.parentFile?.mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) show(photoUri)
    }
    return ImagePickers(
        takePhoto = { take.launch(photoUri) },
        choosePhoto = { choose.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        // the keyboard's grant on a pasted image lasts only as long as this call, so it's read now
        pasteImage = { uri -> loadImage(context, uri)?.let(onImage) },
    )
}
