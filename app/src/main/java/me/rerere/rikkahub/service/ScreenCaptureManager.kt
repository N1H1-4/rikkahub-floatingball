package me.rerere.rikkahub.service

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ScreenCaptureManager"

/** 一次截屏的生命周期，界面直接订阅这个流 */
sealed interface CaptureState {
    data object Idle : CaptureState
    data object Preparing : CaptureState
    data class Ready(val bitmap: Bitmap) : CaptureState
    data class Failed(val reason: String) : CaptureState
}

/**
 * 持有 MediaProjection 与抓帧用的 ImageReader，整个进程共用一份。
 *
 * MediaProjection 必须在 [ScreenCaptureService] 进入前台之后才能取到，
 * 所以这里不负责启动服务，只负责在拿到授权结果后建立虚拟屏幕并抓一帧。
 */
class ScreenCaptureManager(private val context: Application) {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // 界面已经关掉时，晚到的帧不该再塞进状态里
    private var released = true

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection stopped by system")
            stopCapture()
            // 用户从系统状态栏结束共享时，界面要能知道自己没拿到图
            if (_state.value !is CaptureState.Ready) {
                _state.value = CaptureState.Failed("系统已停止截屏授权")
            }
        }
    }

    /** 用系统授权返回的结果建立虚拟屏幕并抓取一帧 */
    fun begin(resultCode: Int, data: Intent) {
        stopCapture()
        released = false
        _state.value = CaptureState.Preparing

        val manager = context.getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            fail("设备不支持屏幕捕获")
            return
        }
        val projection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (projection == null) {
            fail("截屏授权无效")
            return
        }
        this.projection = projection

        val worker = HandlerThread("screen-capture").also { it.start() }
        this.worker = worker
        val handler = Handler(worker.looper)
        this.handler = handler

        // Android 14 起要求 createVirtualDisplay 之前先注册回调
        runCatching { projection.registerCallback(projectionCallback, handler) }
            .onFailure { Log.e(TAG, "registerCallback failed", it) }

        val (width, height) = realScreenSize()
        if (width <= 0 || height <= 0) {
            fail("无法获取屏幕尺寸")
            return
        }
        val density = context.resources.displayMetrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ r -> onImageAvailable(r) }, handler)

        val display = runCatching {
            projection.createVirtualDisplay(
                "rikkahub-capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
        }.getOrNull()

        if (display == null) {
            fail("无法建立屏幕镜像")
            return
        }
        virtualDisplay = display
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        val bitmap = try {
            image.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "convert image failed", e)
            null
        } finally {
            runCatching { image.close() }
        }

        if (bitmap == null) {
            fail("读取画面失败")
            return
        }
        // 只要一帧，抓到之后就把监听摘掉，避免后面的帧反复覆盖
        reader.setOnImageAvailableListener(null, null)
        if (released) {
            bitmap.recycle()
        } else {
            _state.value = CaptureState.Ready(bitmap)
        }
    }

    /** 释放投影与缓冲，界面关闭时必须调用 */
    fun release() {
        released = true
        stopCapture()
        if (_state.value is CaptureState.Ready) {
            _state.value = CaptureState.Idle
        }
    }

    private fun stopCapture() {
        imageReader?.setOnImageAvailableListener(null, null)
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null

        val projection = this.projection
        this.projection = null
        if (projection != null) {
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
        }

        worker?.quitSafely()
        worker = null
        handler = null
    }

    private fun fail(reason: String) {
        Log.e(TAG, reason)
        if (!released) {
            _state.value = CaptureState.Failed(reason)
        }
    }

    // 虚拟屏幕要和真实屏幕一样大，否则抓到的画面会被缩放并且坐标对不上
    private fun realScreenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java)
                .maximumWindowMetrics
                .bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    // ImageReader 的行宽会按对齐补齐，多出来的部分要裁掉
    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }
}
