package me.rerere.rikkahub.ui.screenshot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import me.rerere.rikkahub.BuildConfig
import java.io.File

private const val TAG = "ScreenshotExporter"

/**
 * 截图的两个"出去"动作：剪贴板和系统相册。
 *
 * 剪贴板借 FileProvider 换一个 content:// uri 再塞进 ClipData，这样别的应用粘贴时
 * 拿到的是可读的图片而不是一个它无权访问的路径。
 */
object ScreenshotExporter {

    fun copyToClipboard(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val dir = File(context.cacheDir, "clipboard").apply { mkdirs() }
        val file = File(dir, "screenshot-${System.currentTimeMillis()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val clip = ClipData.newUri(context.contentResolver, "screenshot", uri)
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        true
    }.onFailure {
        Log.e(TAG, "copyToClipboard failed", it)
    }.getOrDefault(false)

    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "rikkahub-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/RikkaHub",
                )
                // 写完之前不让相册扫到这张半成品
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.onFailure {
        Log.e(TAG, "saveToGallery failed", it)
    }.getOrDefault(false)
}
