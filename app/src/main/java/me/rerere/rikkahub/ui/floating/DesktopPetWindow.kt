package me.rerere.rikkahub.ui.floating

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.layout.FxAdsorb
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.core.layout.FxHalfHide
import com.petterp.floatingx.core.storage.FxSpStorage
import com.petterp.floatingx.core.update
import com.petterp.floatingx.system.permission.FxPermission
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.screenshot.ScreenCaptureActivity
import me.rerere.rikkahub.ui.theme.RikkahubTheme

private const val TAG = "DesktopPetWindow"
private const val PET_TAG = "desktop_pet"

/** 贴边半隐时藏掉的比例，剩下的部分仍然可点可拖 */
private const val HALF_HIDE_RATIO = 0.55f

/**
 * 常驻桌面的角色窗口。
 *
 * 和 [ChatFloatingWindows] 里那个“后台生成时冒出来”的气泡不同，这个只看开关，
 * 一旦打开就一直挂着；单击触发选区截屏。
 *
 * 贴边吸附与贴边半隐都用 FloatingX 自带的 FxAdsorb / FxHalfHide，
 * 设置变化时通过 FxControl.update {} 就地换配置，不重建窗口，位置不会丢。
 */
class DesktopPetWindow(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    // 内容在悬浮窗自己的组合里，用 Compose 的 State 才能让改大小/透明度立刻生效
    private val visuals = mutableStateOf(DesktopPetVisuals())

    private var control: FxControl? = null
    private var enabled = false
    private var captureInProgress = false
    private var dockToEdge = true
    private var autoHide = false
    private var adsorbKey: String? = null

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { it.displaySetting }
                .distinctUntilChanged()
                .collect(::apply)
        }
        // 授权悬浮窗是从设置页跳到系统页面做的，回来时开关没变过，得自己重画一次
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) render()
                }
            )
        }
    }

    /** 截屏开始前先把角色藏起来，否则会被一起截进画面 */
    fun onCaptureStarted() {
        captureInProgress = true
        control?.hide()
    }

    /** 截屏界面关闭后恢复显示 */
    fun onCaptureFinished() {
        captureInProgress = false
        render()
    }

    private fun apply(display: DisplaySetting) {
        enabled = display.enableDesktopPet
        dockToEdge = display.desktopPetDockToEdge
        autoHide = display.desktopPetAutoHide
        visuals.value = DesktopPetVisuals(
            avatar = display.desktopPetAvatar,
            sizeScale = display.desktopPetSize,
            opacity = display.desktopPetOpacity,
        )

        val key = "$dockToEdge/$autoHide"
        if (key != adsorbKey) {
            adsorbKey = key
            // 窗口还没建出来时不用管，install() 会读取最新值
            control?.let { current ->
                runCatching { current.update { adsorb(adsorbFor()) } }
                    .onFailure { Log.w(TAG, "Failed to update adsorb", it) }
            }
        }
        render()
    }

    private fun render() {
        // 没有悬浮窗权限时系统会直接拒绝，后台也弹不出授权页
        if (!enabled || captureInProgress || !FxPermission.isGranted(context)) {
            control?.cancel()
            control = null
            return
        }
        (control ?: install().also { control = it }).show()
    }

    private fun adsorbFor(): FxAdsorb = when {
        !dockToEdge -> FxAdsorb.none()
        autoHide -> FxAdsorb.horizontal(FxHalfHide(HALF_HIDE_RATIO))
        else -> FxAdsorb.horizontal()
    }

    private fun install(): FxControl = FloatingX.install(PET_TAG) {
        compose {
            RikkahubTheme {
                DesktopPetContent(visuals = visuals.value, onClick = ::launchCapture)
            }
        }
        // 和聊天悬浮气泡（CENTER_END / dy = -96dp）错开，避免两个窗口叠在一起
        anchor(FxGravity.CENTER_END, dy = dp(180f))
        margin(left = dp(8f), right = dp(8f))
        adsorb(adsorbFor())
        persist(FxSpStorage(context))
        animation(FxAnimations.scale())
        // 权限在 render() 里判断过了，这里不再走库的申请流程
        systemHost(context) { permission(FxPermissionStrategy.skip()) }
    }

    private fun launchCapture() {
        Log.i(TAG, "Desktop pet tapped, requesting screen capture")
        onCaptureStarted()
        runCatching {
            // 有悬浮窗权限的应用允许从后台启动 Activity
            context.startActivity(
                Intent(context, ScreenCaptureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Log.e(TAG, "Unable to launch screen capture", it)
            onCaptureFinished()
        }
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}
