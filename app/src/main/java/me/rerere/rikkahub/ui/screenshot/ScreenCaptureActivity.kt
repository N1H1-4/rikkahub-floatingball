package me.rerere.rikkahub.ui.screenshot

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.service.CaptureState
import me.rerere.rikkahub.service.ChatFloatingTracker
import me.rerere.rikkahub.service.ScreenCaptureManager
import me.rerere.rikkahub.service.ScreenCaptureService
import me.rerere.rikkahub.ui.floating.DesktopPetWindow
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.ui.sketch.ScreenshotEditor
import org.koin.android.ext.android.inject
import java.io.ByteArrayOutputStream
import kotlin.uuid.Uuid

/**
 * 截屏授权与编辑。
 *
 * 这个 Activity 在拿到画面之前窗口是全透明的（不能画任何东西，否则会被截进画面里），
 * 拿到冻结帧之后才把画面铺满屏幕，交给 [ScreenshotEditor] 做框选、标注和输出。
 */
class ScreenCaptureActivity : ComponentActivity() {
    private val captureManager: ScreenCaptureManager by inject()
    private val desktopPet: DesktopPetWindow by inject()
    private val filesManager: FilesManager by inject()
    private val floatingTracker: ChatFloatingTracker by inject()

    private val requestCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                // 等系统授权弹窗的消失动画走完，否则会把弹窗一起截进来
                lifecycleScope.launch {
                    delay(CAPTURE_SETTLE_MILLIS)
                    // 这段延迟里界面可能已经被关掉，别再起一个没人收尾的前台服务
                    if (isFinishing || isDestroyed) return@launch
                    ScreenCaptureService.start(this@ScreenCaptureActivity, result.resultCode, data)
                }
            } else {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 抓帧时这个窗口必须是全透明，才不会污染画面
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        // 铺到系统栏下面，冻结的画面才和用户刚才看到的屏幕严丝合缝
        WindowCompat.setDecorFitsSystemWindows(window, false)

        desktopPet.onCaptureStarted()
        if (savedInstanceState == null) {
            launchCapture()
        }

        setContent {
            val state by captureManager.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            // 点了"发送到已有对话"但还没选会话的图
            var pendingPick by remember { mutableStateOf<Bitmap?>(null) }

            RikkahubTheme {
                val current = state
                when (current) {
                    is CaptureState.Ready -> ScreenshotEditor(
                        bitmap = current.bitmap,
                        onCancel = { finish() },
                        onCopy = { bitmap ->
                            val ok = ScreenshotExporter.copyToClipboard(this, bitmap)
                            toast(if (ok) R.string.screenshot_copied else R.string.screenshot_copy_failed)
                            finish()
                        },
                        onSave = { bitmap ->
                            val ok = ScreenshotExporter.saveToGallery(this, bitmap)
                            toast(if (ok) R.string.screenshot_saved else R.string.screenshot_save_failed)
                            finish()
                        },
                        onSendNewChat = { bitmap ->
                            scope.launch { sendToConversation(bitmap, Uuid.random()) }
                        },
                        onSendToChat = { bitmap -> pendingPick = bitmap },
                    )

                    is CaptureState.Failed -> CaptureFailed(
                        reason = current.reason,
                        onClose = { finish() },
                    )
                    // Preparing / Idle 一帧都不能画，否则会出现在截图里
                    else -> Unit
                }

                pendingPick?.let { bitmap ->
                    ConversationPickerSheet(
                        onDismiss = { pendingPick = null },
                        onPick = { conversation ->
                            pendingPick = null
                            scope.launch { sendToConversation(bitmap, conversation.id) }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        captureManager.release()
        ScreenCaptureService.stop(this)
        desktopPet.onCaptureFinished()
        super.onDestroy()
    }

    /**
     * 把图存成聊天附件，然后让悬浮面板打开这个会话并把图挂在输入框上。
     *
     * 不直接发出去：截图之后通常还要配一句话，让用户自己按发送。
     */
    private suspend fun sendToConversation(bitmap: Bitmap, conversationId: Uuid) {
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.screenshot_need_overlay_permission)
            return
        }
        val bytes = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }
        val uri = withContext(Dispatchers.IO) {
            filesManager.createChatFilesByByteArrays(
                byteArrays = listOf(bytes),
                displayName = "screenshot.png",
                mimeType = "image/png",
            ).firstOrNull()
        }
        if (uri == null) {
            toast(R.string.screenshot_save_failed)
            return
        }
        floatingTracker.openDraft(conversationId, uri.toString())
        finish()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    private fun launchCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            finish()
            return
        }
        val intent = runCatching { manager.createScreenCaptureIntent() }.getOrNull()
        if (intent == null) {
            finish()
            return
        }
        requestCapture.launch(intent)
    }

    private companion object {
        // 授权弹窗退出后再抓帧，太短会把弹窗截进去
        const val CAPTURE_SETTLE_MILLIS = 300L
    }
}

@Composable
private fun CaptureFailed(reason: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_capture_failed),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Button(onClick = onClose) {
                Text(stringResource(R.string.screen_capture_close))
            }
        }
    }
}
