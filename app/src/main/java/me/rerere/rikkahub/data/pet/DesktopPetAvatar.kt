package me.rerere.rikkahub.data.pet

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.File

private const val TAG = "DesktopPetAvatar"
private const val DIR = "desktop_pet"

/**
 * 用户导入的桌宠形象。
 *
 * 只保留一份：换形象时把旧文件删掉，避免相册里选了几次就攒一堆图。
 * 存进 filesDir 而不是直接记住 content:// uri，是因为相册给的临时授权过一段时间就失效了。
 */
object DesktopPetAvatar {
    fun store(context: Context, uri: Uri, mimeType: String?): Uri? {
        return runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "avatar.${extensionOf(mimeType, uri)}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open $uri")
            clear(context, except = file)
            file.toUri()
        }.onFailure {
            Log.e(TAG, "store failed for $uri", it)
        }.getOrNull()
    }

    fun clear(context: Context, except: File? = null) {
        val dir = File(context.filesDir, DIR)
        dir.listFiles()?.forEach { file ->
            if (file != except) file.delete()
        }
    }

    // Coil 靠后缀或文件头判断格式，存成无后缀会被当成二进制
    private fun extensionOf(mimeType: String?, uri: Uri): String {
        return when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            "image/svg+xml" -> "svg"
            else -> uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 } ?: "png"
        }
    }
}
