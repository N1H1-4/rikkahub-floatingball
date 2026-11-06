package me.rerere.rikkahub.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.SCREEN_CAPTURE_NOTIFICATION_CHANNEL_ID
import org.koin.android.ext.android.inject

private const val TAG = "ScreenCaptureService"

/**
 * 截屏用的前台服务。
 *
 * 它本身不碰画面，只负责两件事：让进程满足 mediaProjection 类型的前台服务要求，
 * 以及在进入前台之后把授权结果交给 [ScreenCaptureManager]。
 * Android 14 起必须在 getMediaProjection 之前先进入前台，顺序不能反。
 */
class ScreenCaptureService : Service() {
    companion object {
        private const val ACTION_START = "me.rerere.rikkahub.action.SCREEN_CAPTURE_START"
        private const val ACTION_STOP = "me.rerere.rikkahub.action.SCREEN_CAPTURE_STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        const val NOTIFICATION_ID = 2003

        fun start(context: Context, resultCode: Int, data: Intent): Boolean {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure {
                Log.e(TAG, "Unable to start screen capture service", it)
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                Log.e(TAG, "Unable to stop screen capture service", it)
            }
        }
    }

    private val captureManager: ScreenCaptureManager by inject()
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent, startId)
            else -> stopService(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    private fun startCapture(intent: Intent, startId: Int) {
        // 先进入前台，再取 MediaProjection
        if (!enterForeground()) {
            stopService(startId)
            return
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.resultData()
        if (resultData == null) {
            Log.e(TAG, "Missing screen capture result data")
            stopService(startId)
            return
        }
        captureManager.begin(resultCode, resultData)
    }

    private fun enterForeground(): Boolean = runCatching {
        val notification = NotificationCompat.Builder(this, SCREEN_CAPTURE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
        true
    }.onFailure {
        Log.e(TAG, "Failed to enter foreground", it)
    }.getOrDefault(false)

    private fun stopService(startId: Int) {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf(startId)
    }

    private fun Intent.resultData(): Intent? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }.getOrNull()
}
