package org.taskhub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import org.taskhub.platform.ImagePickerResultHolder
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android helper para elegir una foto de avatar desde galería o cámara.
 *
 * Mirrors [GoogleSignInHelper]: [register] debe ejecutarse en
 * `MainActivity.onCreate` antes de que la UI llame a [pickFromGallery] o
 * [takePhoto]. Ambos caminos decodifican, re-escalan (máx. [MAX_DIMENSION]px)
 * y comprimen a JPEG la imagen elegida antes de entregarla vía
 * [ImagePickerResultHolder], para que el resto de la app nunca maneje bitmaps
 * ni el original de varios MP de la cámara.
 */
object ImagePickerHelper {

    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 80

    private var galleryLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var cameraLauncher: ActivityResultLauncher<Uri>? = null
    private var pendingCameraUri: Uri? = null
    private var appContext: Context? = null

    fun register(activity: ComponentActivity) {
        appContext = activity.applicationContext

        galleryLauncher = activity.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri -> handlePickedUri(uri) }

        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            handlePickedUri(if (success) uri else null)
        }
    }

    fun pickFromGallery() {
        galleryLauncher?.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun takePhoto() {
        val context = appContext ?: return
        val file = File(context.cacheDir, "avatar_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher?.launch(uri)
    }

    private fun handlePickedUri(uri: Uri?) {
        val context = appContext
        if (uri == null || context == null) {
            ImagePickerResultHolder.setResult(null)
            return
        }
        val bytes = try {
            decodeAndCompress(context, uri)
        } catch (_: Exception) {
            null
        }
        ImagePickerResultHolder.setResult(bytes)
    }

    /** Decodifica el [uri] elegido, lo re-escala a como mucho [MAX_DIMENSION]px por lado y lo comprime a JPEG. */
    private fun decodeAndCompress(context: Context, uri: Uri): ByteArray? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return output.toByteArray()
    }
}
