package me.rerere.rikkahub.ui.floating

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import me.rerere.rikkahub.EXTRA_TARGET_ROUTE
import me.rerere.rikkahub.ui.screenshot.EXTRA_SEND_IMMEDIATELY
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.RouteTarget
import me.rerere.rikkahub.ui.screenshot.ScreenCaptureActivity
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import com.petterp.floatingx.core.layout.FxHalfHide
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.FxListener
import com.petterp.floatingx.core.update
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.gesture.FxDrag
import com.petterp.floatingx.core.gesture.FxGesture
import com.petterp.floatingx.core.layout.FxAdsorb
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.core.storage.FxSpStorage
import com.petterp.floatingx.system.container.FxWindowContainer
import com.petterp.floatingx.system.permission.FxPermission
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.conversationIntent
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GenerationPhase
import me.rerere.rikkahub.data.model.generationPhase
import me.rerere.rikkahub.data.model.pendingToolCalls
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatFloatingTracker
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import kotlin.uuid.Uuid

private const val TAG = "ChatFloatingWindows"
private const val BUBBLE_TAG = "chat_floating_bubble"
private const val PANEL_TAG = "chat_floating_panel"
private const val LIST_TAG = "chat_floating_list"
private const val RING_TAG = "chat_floating_ring"
private const val PANEL_TOP_OFFSET_DP = 8f
private const val PANEL_MAX_WIDTH_DP = 560f
private const val PANEL_MIN_WIDTH_DP = 320f
private const val PANEL_COMPACT_WIDTH_DP = 380f
private const val PANEL_MIN_HEIGHT_DP = 170f
private const val PANEL_DEFAULT_HEIGHT_DP = 240f
private const val PANEL_RESIZE_SLOP_DP = 8f
private const val PANEL_CORNER_RADIUS_DP = 28f
private const val PREVIEW_ALPHA = 0xE6
private const val PREVIEW_FADE_MILLIS = 90L
private const val RESIZE_ITERATIONS = 3
private const val BALL_SIZE_DP = 52f
private const val BALL_RING_RADIUS_DP = 104f
private const val BALL_RING_ITEM_DP = 48f
private const val BALL_RING_PAD_DP = 8f
private const val BALL_BODY_PADDING_DP = 12f
private const val BALL_HALF_HIDE_RATIO = 0.55f

/**
 * 把 [ChatFloatingTracker] 的结果显示成系统悬浮窗：收起时是贴边的小图标，展开是固定在顶部的面板。
 * 两个窗口共用这里汇总的一份 [FloatingChatUiState]。FloatingX 只能在主线程操作，协程都跑在 AppScope 上。
 */
class ChatFloatingWindows(
    private val context: Application,
    private val appScope: AppScope,
    private val tracker: ChatFloatingTracker,
    private val settingsStore: SettingsStore,
    private val phoneUseManager: PhoneUseManager,
    lazyChatService: Lazy<ChatService>,
) {
    private val chatService by lazyChatService
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var bubble: FxControl? = null
    private var panel: FxControl? = null
    // 悬浮球旁边弹出的列表（历史会话 / 切换助手）
    private var listControl: FxControl? = null
    // 长按菜单是独立窗口：球自己的窗口尺寸不变，位置就不会被带着跑
    private var ringControl: FxControl? = null
    private val listMode = mutableStateOf<FloatingListMode?>(null)

    // 合并后的球：形象跟着显示设置走，窗口小且常驻
    private val ballVisuals = mutableStateOf(DesktopPetVisuals())
    private val ballMenuOpen = mutableStateOf(false)
    // 球贴在哪一侧：菜单是朝屏幕内侧展开的半圆
    private val ballDockedRight = mutableStateOf(true)
    private var captureInProgress = false

    private val displaySetting: StateFlow<DisplaySetting> = settingsStore.settingsFlow
        .map { it.displaySetting }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, DisplaySetting())

    // 悬浮窗显示期间一直汇总，气泡和面板切换时直接拿到当前状态，不会先空一帧
    private val uiState: StateFlow<FloatingChatUiState?> = tracker.window
        .map { it?.conversationId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else chatState(id) }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    // 悬浮窗的 Context 不是 Activity，默认的 UriHandler 打开链接时缺少 NEW_TASK 会崩溃
    private val uriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "openUri: no activity for $uri", e)
            }
        }
    }

    init {
        appScope.launch {
            tracker.window.map { it?.expanded }.distinctUntilChanged().collect(::render)
        }
        // 悬浮窗权限是从设置页跳到系统页面授予的，回来时开关值没变过、设置流不会再发射，
        // 这里在应用回到前台时自己重画一次，否则球永远装不上
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) render(tracker.window.value?.expanded)
                },
            )
        }
        appScope.launch {
            displaySetting.collect { display ->
                ballVisuals.value = DesktopPetVisuals(
                    avatar = display.desktopPetAvatar,
                    sizeScale = display.desktopPetSize,
                    opacity = display.desktopPetOpacity,
                )
                runCatching {
                    bubble?.update {
                        adsorb(adsorbFor(display))
                    }
                }.onFailure { Log.w(TAG, "update ball adsorb failed", it) }
                render(tracker.window.value?.expanded)
            }
        }
        // 回到"还没开始的空对话"阶段时，窗口恢复标准大小（那个阶段不允许缩放）
        appScope.launch {
            uiState.map { it?.panelPhase }.distinctUntilChanged().collect { phase ->
                if (phase == PanelPhase.Idle) resetPanelSize()
            }
        }
        appScope.launch {
            phoneUseManager.acting.collect(::yieldScreen)
        }
    }

    /** 空对话阶段：宽度和高度都回到默认 */
    private fun resetPanelSize() {
        val (container, lp) = panelWindow() ?: return
        val defaultWidth = panelWindowWidth()
        val widthChanged = lp.width != defaultWidth
        val heightChanged = lp.height != WindowManager.LayoutParams.WRAP_CONTENT
        if (!widthChanged && !heightChanged) return
        lp.width = defaultWidth
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        desiredWidthPx = 0
        panelHeightPx = 0
        panelHeightPinned.value = false
        runCatching {
            context.getSystemService(WindowManager::class.java).updateViewLayout(container, lp)
        }.onFailure { Log.w(TAG, "resetPanelSize failed", it) }
    }

    /**
     * Phone Use 截图或执行手势时让出屏幕：悬浮窗不能出现在截图里，也不能挡住落在它位置上的点击。
     * 不用 hide()，它带动画，每个动作都收起再弹出会一直闪；这里只让窗口透明并且不接收触摸。
     * 透明度要改窗口参数而不是 View：Android 12 起，触摸穿过不透明的悬浮窗落到别的应用上会被系统丢弃，
     * 系统看的是窗口的透明度。
     */
    private fun yieldScreen(yielding: Boolean) {
        // 展开的面板盖住屏幕上半部分还占着输入焦点，模型操作时直接收起
        if (yielding) tracker.collapse()
        listOfNotNull(bubble, panel).forEach { control ->
            val window = generateSequence(control.contentView?.parent) { it.parent }
                .filterIsInstance<FxWindowContainer>()
                .firstOrNull() ?: return@forEach
            window.windowParams.alpha = if (yielding) 0f else 1f
            window.setWindowTouchable(!yielding)
            // setWindowTouchable 在标志没变时不会提交，透明度单独提交一次
            if (window.isAttachedToWm) windowManager.updateViewLayout(window, window.windowParams)
        }
    }

    private fun chatState(id: Uuid): Flow<FloatingChatUiState> {
        // 只显示这一轮的错误。开始时间要用启动任务时记下的，等这里观察到任务再取会晚于快速失败时报的错
        val error = combine(chatService.errors, chatService.getGenerationStartedAtFlow(id)) { errors, since ->
            since?.let { errors.lastOrNull { it.conversationId == id && it.timestamp >= since } }
        }
        val conversation = combine(chatService.getConversationFlow(id), chatService.getReplyMessageIdFlow(id), ::Pair)
        return combine(
            conversation,
            chatService.getGenerationJobStateFlow(id).map { it != null },
            chatService.getProcessingStatusFlow(id),
            chatService.getInboxFlow(id),
            error,
        ) { (conversation, replyMessageId), generating, processingStatus, queue, error ->
            buildFloatingChatUiState(conversation, replyMessageId, generating, processingStatus, queue, error)
        }.flowOn(Dispatchers.Default)
    }

    private fun render(expanded: Boolean?) {
        // 面板要收起/关闭时，别把拖动预览框留在屏幕上
        if (expanded != true) hideResizePreview()
        // 后台弹不出权限申请页，没有权限就不显示
        if (!FxPermission.isGranted(context)) {
            bubble?.cancel()
            panel?.cancel()
            bubble = null
            panel = null
            return
        }
        // 合并后的球：开着"桌面宠物"开关就常驻；没有开关时只在有会话可跟时出现
        val ballVisible = !captureInProgress &&
            (displaySetting.value.enableDesktopPet || expanded != null)
        // 注意：bubble ?: ... ?: return.also { } 这种写法里 return 后面的 also 是不可达的，
        // 之前就是这么写的，导致 bubble 永远是 null、每次 render 都重新建窗口。
        val ball = bubble ?: run {
            val installed = runCatching { installBubble() }
                .onFailure { Log.e(TAG, "install floating ball failed", it) }
                .getOrNull()
                ?: return
            bubble = installed
            installed
        }

        if (expanded == true) {
            ball.hide()
            showPanelNearBall()
            applyPanelSizeIfNeeded()
        } else {
            panel?.hide()
            if (ballVisible) ball.show() else ball.hide()
        }
    }

    /** 截屏前把球藏起来，否则会被一起截进画面；截完再放回来 */
    fun onCaptureStarted() {
        captureInProgress = true
        ballMenuOpen.value = false
        bubble?.hide()
    }

    fun onCaptureFinished() {
        captureInProgress = false
        render(tracker.window.value?.expanded)
    }

    /** 展开面板时按球所在的一侧摆放，而不是永远固定在屏幕正上方 */
    private fun showPanelNearBall() {
        val control = panel ?: installPanel().also { panel = it }
        val onRight = bubble?.position?.let { it.x > safeScreenWidth() / 2f } ?: true
        runCatching {
            control.update {
                anchor(if (onRight) FxGravity.TOP_END else FxGravity.TOP_START, dy = dp(PANEL_TOP_OFFSET_DP))
                margin(left = dp(8f), right = dp(8f))
            }
        }.onFailure { Log.w(TAG, "anchor panel failed", it) }
        control.show()
    }

    private fun installList(): FxControl = FloatingX.install(LIST_TAG) {
        compose {
            WindowContentOrNull { _ ->
                val mode = listMode.value ?: return@WindowContentOrNull
                FloatingListCard(
                    mode = mode,
                    onPickConversation = { id ->
                        hideList()
                        tracker.openConversation(id)
                    },
                    onPickAssistant = { id ->
                        hideList()
                        appScope.launch { settingsStore.selectAssistant(id) }
                        tracker.openConversation(Uuid.random())
                    },
                    onDismiss = ::hideList,
                )
            }
        }
        anchor(FxGravity.CENTER_END, dy = -dp(120f))
        margin(left = dp(8f), right = dp(8f))
        adsorb(FxAdsorb.none())
        animation(FxAnimations.fade())
        systemHost(context) { permission(FxPermissionStrategy.skip()) }
    }

    /** 弹出历史会话 / 切换助手的浮层列表，球先藏起来避免被挡住 */
    private fun showList(mode: FloatingListMode) {
        ballMenuOpen.value = false
        listMode.value = mode
        bubble?.hide()
        (listControl ?: installList().also { listControl = it }).show()
    }

    private fun hideList() {
        listMode.value = null
        listControl?.hide()
        render(tracker.window.value?.expanded)
    }

    private fun installBubble(): FxControl = FloatingX.install(BUBBLE_TAG) {
        compose {
            WindowContentOrNull { state ->
                FloatingBall(
                    visuals = ballVisuals.value,
                    state = state,
                    menuOpen = false,
                    ringActions = ringActionsOf(displaySetting.value),
                    dockedRight = ballDockedRight.value,
                    onTap = ::onBallTap,
                    onLongPress = ::openBallMenu,
                    onCloseMenu = ::closeBallMenu,
                    onRingAction = ::onRingAction,
                )
            }
        }
        anchor(FxGravity.CENTER_END, dy = -dp(96f))
        margin(left = dp(8f), right = dp(8f))
        adsorb(FxAdsorb.horizontal())
        persist(FxSpStorage(context))
        animation(FxAnimations.scale())
        systemHost(context) { permission(FxPermissionStrategy.skip()) }
    }.also { control ->
        // Compose 的长按会被 FloatingX 的手势层吃掉，这里同时挂它的回调；两边都开也只开一次菜单
        runCatching {
            control.addListener(
                object : FxListener {
                    override fun onLongClick(control: FxControl, view: View) {
                        if (!ballMenuOpen.value) openBallMenu()
                    }
                },
            )
        }.onFailure { Log.w(TAG, "register long press failed", it) }
    }

    /**
     * 长按展开半圆菜单。UI 仍然画在球自己的窗口里（回到已验证可用的实现），
     * 窗口变大时球会跟着偏移，这一点后续再单独处理。
     */
    /**
     * 长按菜单。菜单画在**独立窗口**里：球自己的窗口尺寸完全不变，所以不会跟着位移。
     *
     * 之前把菜单画在球窗口里，窗口一变大，WindowManager 让内容朝一个方向长，
     * 球就跟着跑了；FloatingX 缓存的位置又不随尺寸更新，怎么补都补不回来。
     */
    private fun openBallMenu() {
        val control = bubble ?: return
        ballMenuOpen.value = true
        val ballSize = dp(BALL_SIZE_DP * displaySetting.value.desktopPetSize)
        val halfBody = (ballSize + dp(BALL_BODY_PADDING_DP)) / 2f
        val centerX = control.position.x + halfBody
        val centerY = control.position.y + halfBody
        ballDockedRight.value = centerX > safeScreenWidth() / 2f

        // 同样注意：`?: return.also { }` 里 return 后面的 also 不可达，必须写成显式块
        val ring = ringControl ?: run {
            val installed = runCatching { installRing() }
                .onFailure { Log.e(TAG, "installRing failed", it) }
                .getOrNull()
                ?: return
            ringControl = installed
            installed
        }

        // 球先藏起来，菜单窗口画在同一個位置
        control.hide()
        ring.show()

        val canvasWidth = dp(BALL_RING_RADIUS_DP) + ballSize / 2f +
            dp(BALL_RING_ITEM_DP) / 2f + dp(BALL_RING_PAD_DP)
        val canvasHeight = dp(BALL_RING_RADIUS_DP) * 2f + dp(BALL_RING_ITEM_DP)
        // 球在画布里的中心偏移（CenterEnd + 半个内边距）
        val offsetX = canvasWidth - dp(BALL_BODY_PADDING_DP) / 2f - ballSize / 2f
        val offsetY = canvasHeight / 2f
        val left = centerX - offsetX
        val top = centerY - offsetY
        runCatching { ring.moveTo(left, top, animate = false) }
            .onFailure { Log.w(TAG, "place ring menu failed", it) }
    }

    private fun closeBallMenu() {
        ballMenuOpen.value = false
        ringControl?.hide()
        // 菜单收起后把球放回来（render 会按当前状态决定显示与否）
        render(tracker.window.value?.expanded)
    }

    private fun installRing(): FxControl = FloatingX.install(RING_TAG) {
        compose {
            WindowContentOrNull { state ->
                FloatingBall(
                    visuals = ballVisuals.value,
                    state = state,
                    menuOpen = true,
                    ringActions = ringActionsOf(displaySetting.value),
                    dockedRight = ballDockedRight.value,
                    onTap = {},
                    onLongPress = {},
                    onCloseMenu = ::closeBallMenu,
                    onRingAction = ::onRingAction,
                )
            }
        }
        // TOP_START：lp.x / lp.y 就是内容的绝对坐标，moveTo 不会被锚点换算绕晕
        anchor(FxGravity.TOP_START)
        margin(left = dp(8f), right = dp(8f))
        adsorb(FxAdsorb.none())
        animation(FxAnimations.fade())
        systemHost(context) { permission(FxPermissionStrategy.skip()) }
    }

    private fun adsorbFor(display: DisplaySetting): FxAdsorb = when {
        !display.desktopPetDockToEdge -> FxAdsorb.none()
        display.desktopPetAutoHide -> FxAdsorb.horizontal(FxHalfHide(BALL_HALF_HIDE_RATIO))
        else -> FxAdsorb.horizontal()
    }

    private fun ringActionsOf(display: DisplaySetting): List<FloatingBallAction> =
        display.ballRingActions
            .mapNotNull(FloatingBallAction::fromId)
            .take(FloatingBallAction.RingLimit)

    /** 单击：执行设置里选的动作；"自动判断"时按有没有在跟的会话分流 */
    private fun onBallTap() {
        ballMenuOpen.value = false
        val action = FloatingBallAction.fromId(displaySetting.value.ballTapAction)
            ?: FloatingBallAction.Auto
        when {
            action == FloatingBallAction.Auto && tracker.window.value != null -> tracker.expand()
            action == FloatingBallAction.Auto -> launchCapture()
            else -> onRingAction(action)
        }
    }

    private fun onRingAction(action: FloatingBallAction) {
        closeBallMenu()
        when (action) {
            FloatingBallAction.Auto -> onBallTap()
            FloatingBallAction.NewChat -> tracker.openConversation(Uuid.random())
            FloatingBallAction.History -> showList(FloatingListMode.History)
            FloatingBallAction.OpenApp -> openApp(null)
            FloatingBallAction.FullScreenCapture -> launchCapture(sendImmediately = true)
            FloatingBallAction.RegionCapture -> launchCapture()
            FloatingBallAction.CloseBall -> closeBall()
            FloatingBallAction.Settings -> openApp(RouteTarget.Settings)
            FloatingBallAction.Stats -> openApp(RouteTarget.Stats)
            FloatingBallAction.SwitchAssistant -> showList(FloatingListMode.Assistants)
        }
    }

    /** 关掉开关就等于收起悬浮球（可以再从设置里打开） */
    private fun closeBall() {
        appScope.launch {
            settingsStore.update { settings ->
                settings.copy(displaySetting = settings.displaySetting.copy(enableDesktopPet = false))
            }
        }
    }

    private fun launchCapture(sendImmediately: Boolean = false) {
        onCaptureStarted()
        runCatching {
            context.startActivity(
                Intent(context, ScreenCaptureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_SEND_IMMEDIATELY, sendImmediately),
            )
        }.onFailure {
            Log.e(TAG, "launch capture failed", it)
            onCaptureFinished()
        }
    }

    private fun openApp(target: RouteTarget?) {
        runCatching {
            val intent = Intent(context, RouteActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (target != null) intent.putExtra(EXTRA_TARGET_ROUTE, target.id)
            context.startActivity(intent)
        }.onFailure { Log.w(TAG, "open app failed", it) }
    }

    private fun installPanel(): FxControl = FloatingX.install(PANEL_TAG) {
        compose {
            WindowContent { state ->
                // 预览框要跟面板同色，这里把当前主题的面板底色记下来
                val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
                LaunchedEffect(surfaceColor) { previewColor = surfaceColor.toArgb() }
                val conversationId = state.conversationId
                val draft by tracker.draftImageState.collectAsStateWithLifecycle()
                ChatFloatingPanel(
                    state = state,
                    maxHeight = rememberPanelMaxHeight(),
                    onCollapse = tracker::collapse,
                    onDismiss = tracker::dismiss,
                    onOpenConversation = { text -> openConversation(conversationId, text) },
                    onToolApproval = { toolCallId, approved ->
                        chatService.handleToolApproval(conversationId, toolCallId, approved)
                    },
                    onStop = { appScope.launch { chatService.stopGeneration(conversationId) } },
                    draftImage = draft,
                    onRemoveDraft = tracker::clearDraftImage,
                    onSend = { text ->
                        val parts = buildList {
                            draft?.let { add(UIMessagePart.Image(url = it)) }
                            if (text.isNotBlank()) add(UIMessagePart.Text(text))
                        }
                        chatService.sendMessage(conversationId, parts)
                        // 图已经进消息了，会话继续钉住，方便在面板里看回复
                        tracker.clearDraftImage()
                    },
                    onResumeQueue = { chatService.resumeInbox(conversationId) },
                    // 拖动时必须关掉动画：带动画的 moveBy 每帧都在追一个刚被顶掉的目标，看起来就像没动
                    onMoveWindow = { dx, dy -> panel?.moveBy(dx, dy, animate = false) },
                    heightPinned = panelHeightPinned.value,
                    onResizeBegin = ::onResizeBegin,
                    onResizeMove = ::onResizeMove,
                    onResizeEnd = ::onResizeEnd,
                )
            }
        }
        anchor(FxGravity.TOP_CENTER, dy = dp(PANEL_TOP_OFFSET_DP))
        gesture(FxGesture(click = false, longPress = false, drag = FxDrag.DISABLED))
        animation(FxAnimations.fade())
        systemHost(context) {
            permission(FxPermissionStrategy.skip())
            layoutParams {
                // 面板里有输入框，窗口要能获得焦点才能弹键盘；收起时窗口是 GONE，不会抢走其他应用的焦点
                it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                // 不能铺满屏宽：窗口和屏幕一样宽时只能上下移动，左右会被夹死。
                // wrap_content 又会先按对话框宽度（约 320dp）测量把面板压窄，所以给一个固定宽度。
                it.width = panelWindowWidth()
                if (panelHeightPinned.value && panelHeightPx > 0) it.height = panelHeightPx
            }
            onBackPressed {
                tracker.collapse()
                true
            }
        }
    }

    // 窗口隐藏时生命周期退回 STARTED，只在显示时订阅，流式输出不会让隐藏的窗口跟着重组
    @Composable
    private fun WindowContent(content: @Composable (FloatingChatUiState) -> Unit) {
        WindowContentOrNull { state -> state?.let { content(it) } }
    }

    /**
     * 和 [WindowContent] 一样，但允许"当前没有会话"时也渲染内容。
     *
     * 悬浮球是常驻的，没有会话时也要能画出来；之前面板和气泡共用那个只在有会话时才
     * 组合内容的版本，球就会因为内容为空而变成 0×0 的隐形窗口。
     */
    @Composable
    private fun WindowContentOrNull(content: @Composable (FloatingChatUiState?) -> Unit) {
        // 悬浮窗没有自己的 Activity 生命周期，用 STARTED 保证退到后台后仍然能更新
        val state by uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
        // 悬浮窗只在应用退到后台时显示，这时 Activity 上的常亮标志已经失效，Phone Use 操作别的应用时屏幕会按时熄灭。
        // 生成期间改由显示着的悬浮窗保持亮屏。隐藏的窗口是 GONE，它的标志不起作用；手动按电源键锁屏不受影响
        val view = LocalView.current
        val generating = state?.generating == true
        DisposableEffect(view, generating) {
            view.keepScreenOn = generating
            onDispose { view.keepScreenOn = false }
        }
        RikkahubTheme {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                content(state)
            }
        }
    }

    // 面板窗口是 wrap_content 且不受屏幕限制（FloatingX 用了 NO_LIMITS），内容多了会伸出屏幕底部，按可用高度封顶
    @Composable
    private fun rememberPanelMaxHeight(): Dp {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        return remember(configuration, density) {
            with(density) { (safeScreenHeight() - dp(PANEL_TOP_OFFSET_DP)).toDp() }
        }
    }

    // 和 FloatingX 摆放窗口用的安全区一致：R 及以上去掉屏幕级的系统栏和刘海，R 以下没有公开入口，用不含导航栏的高度
    private fun safeScreenHeight(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context.resources.displayMetrics.heightPixels
        val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val insets = WindowInsetsCompat.toWindowInsetsCompat(metrics.windowInsets)
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        return metrics.bounds.height() - insets.top - insets.bottom
    }

    private fun openConversation(conversationId: Uuid, draftText: String) {
        // 面板里的草稿（文字 + 刚截的图）要跟着进聊天页，不能丢掉
        val draftImage = tracker.draftImageState.value
        // 打开应用内的会话后，悬浮面板就没必要继续钉在上面了
        tracker.dismiss()
        // 有悬浮窗权限的应用允许从后台启动 Activity
        context.startActivity(
            conversationIntent(context, conversationId.toString(), draftText, draftImage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // 手动改过高度之后，内容要填满窗口而不是跟着内容走
    private val panelHeightPinned = mutableStateOf(false)

    private var resizeStartWidth = 0
    private var resizeStartHeight = 0
    private var panelHeightPx = 0
    private var desiredWidthPx = 0
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizePanelX = 0
    private var resizePanelY = 0
    private var resizeTargetWidth = 0
    private var resizeTargetHeight = 0
    private var resizeFromLeft = false
    private var resizeActive = false

    // 拖动时那个同色预览框（独立窗口，不可触摸，不抢手势）
    private var resizePreview: View? = null
    private var previewParams: WindowManager.LayoutParams? = null
    // 面板背景色，由 Compose 侧写进来
    private var previewColor: Int = 0xFF2B2B2B.toInt()

    /**
     * 找到面板窗口的根 View 和它的 WindowManager.LayoutParams。
     *
     * Compose 内容外面还套着一层 holder，它的 layoutParams 是普通 FrameLayout 的，
     * 只看一层父节点会拿到 null，所以这里一路往上找"参数类型是 WindowManager.LayoutParams"的那一层。
     */
    private fun panelWindow(): Pair<View, WindowManager.LayoutParams>? {
        var view: View? = panel?.contentView
        while (view != null) {
            val params = view.layoutParams
            if (params is WindowManager.LayoutParams) return view to params
            view = view.parent as? View
        }
        return null
    }

    /**
     * 缩放采用「拖动时只画预览框，松手才真正改窗口」：
     * 拖动期间窗口完全不动，把手就一直贴在手指下面，不会有抢触摸、回环抖动、
     * 或者"只有第一次生效"这类问题。
     */
    private fun onResizeBegin(screenX: Float, screenY: Float, mirrored: Boolean) {
        // 上一次如果 remove 失败，复用已分离的 View 会让预览框再也不显示，这里先清干净
        hideResizePreview()
        val (container, lp) = panelWindow() ?: return
        val location = IntArray(2).also { container.getLocationOnScreen(it) }
        resizePanelX = location[0]
        resizePanelY = location[1]
        resizeStartWidth = lp.width
        resizeStartHeight = if (lp.height > 0) lp.height else container.height
        resizeStartX = screenX
        resizeStartY = screenY
        resizeTargetWidth = resizeStartWidth
        resizeTargetHeight = resizeStartHeight
        resizeFromLeft = mirrored
        resizeActive = true
        showResizePreview(resizePanelX, resizePanelY, resizeStartWidth, resizeStartHeight)
    }

    private fun onResizeMove(screenX: Float, screenY: Float, vertical: Boolean) {
        if (!resizeActive) return
        val density = context.resources.displayMetrics.density
        val dx = (screenX - resizeStartX).toInt()
        val dy = (screenY - resizeStartY).toInt()

        val minWidth = (PANEL_MIN_WIDTH_DP * density).toInt()
        val maxWidth = safeScreenWidth()
        val width = (resizeStartWidth + if (resizeFromLeft) -dx else dx)
            .coerceIn(minWidth, maxWidth)

        // 右下角是"角缩放"：宽高一起跟着手指走，和系统小窗一致。
        // 以前要求竖直位移必须大于水平位移，稍微斜着拖就只改宽度，用起来像是"不能纵向放大"。
        var height = resizeStartHeight
        if (vertical) {
            val minHeight = (PANEL_MIN_HEIGHT_DP * density).toInt()
            val maxHeight = safeScreenHeight() - (PANEL_TOP_OFFSET_DP * density).toInt()
            height = (resizeStartHeight + dy).coerceIn(minHeight, maxHeight)
        }

        resizeTargetWidth = width
        resizeTargetHeight = height
        val x = if (resizeFromLeft) resizePanelX + resizeStartWidth - width else resizePanelX
        showResizePreview(x, resizePanelY, width, height)
    }

    private fun onResizeEnd() {
        if (!resizeActive) return
        resizeActive = false
        hideResizePreview()

        val (container, lp) = panelWindow() ?: return
        val density = context.resources.displayMetrics.density
        val compact = (PANEL_COMPACT_WIDTH_DP * density).toInt()

        // 缩到阈值以内：不真的缩，直接收成气泡
        if (resizeTargetWidth <= compact) {
            desiredWidthPx = 0
            lp.width = panelWindowWidth()
            runCatching {
                context.getSystemService(WindowManager::class.java).updateViewLayout(container, lp)
            }
            tracker.collapse()
            return
        }

        // 尺寸没变就什么都不做（松手弹回原样）
        val widthChanged = resizeTargetWidth != lp.width
        val heightChanged = resizeTargetHeight != resizeStartHeight
        if (!widthChanged && !heightChanged) return

        lp.width = resizeTargetWidth
        desiredWidthPx = resizeTargetWidth
        if (heightChanged) {
            lp.height = resizeTargetHeight
            panelHeightPx = resizeTargetHeight
            panelHeightPinned.value = true
        }
        val wm = context.getSystemService(WindowManager::class.java)
        runCatching { wm.updateViewLayout(container, lp) }
            .onFailure { Log.w(TAG, "apply panel size failed", it) }

        // 校验一次：某些情况下（窗口刚被重建、或浮窗库自己又摆了一次位置）尺寸会被改回去，
        // 这里补写一次，避免"调到极限之后就再也调不动"。
        container.post {
            val current = container.layoutParams as? WindowManager.LayoutParams ?: return@post
            if (current.width == resizeTargetWidth) return@post
            current.width = resizeTargetWidth
            if (heightChanged) current.height = resizeTargetHeight
            runCatching { wm.updateViewLayout(container, current) }
                .onFailure { Log.w(TAG, "re-apply panel size failed", it) }
        }
    }

    // ---- 拖动时的预览框 ----

    private fun showResizePreview(x: Int, y: Int, width: Int, height: Int) {
        val wm = context.getSystemService(WindowManager::class.java)
        val params = previewParams
        val view = resizePreview
        if (view == null || params == null) {
            val density = context.resources.displayMetrics.density
            val background = GradientDrawable().apply {
                cornerRadius = PANEL_CORNER_RADIUS_DP * density
                setColor(ColorUtils.setAlphaComponent(previewColor, PREVIEW_ALPHA))
                setStroke(
                    (1.5f * density).toInt(),
                    ColorUtils.setAlphaComponent(AndroidColor.WHITE, 0x33),
                )
            }
            val created = View(context).apply {
                this.background = background
                alpha = 0f
                animate().alpha(1f).setDuration(PREVIEW_FADE_MILLIS).start()
            }
            val createdParams = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
            runCatching { wm.addView(created, createdParams) }
                .onFailure { Log.w(TAG, "add resize preview failed", it) }
            resizePreview = created
            previewParams = createdParams
        } else {
            params.x = x
            params.y = y
            params.width = width
            params.height = height
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    private fun hideResizePreview() {
        val view = resizePreview ?: return
        runCatching { context.getSystemService(WindowManager::class.java).removeView(view) }
            .onFailure { Log.w(TAG, "remove resize preview failed", it) }
        resizePreview = null
        previewParams = null
    }


    /** 窗口隐藏过之后 updateViewLayout 可能没生效，重新显示时把记住的尺寸补上去 */
    private fun applyPanelSizeIfNeeded() {
        val (container, lp) = panelWindow() ?: return
        if (!container.isAttachedToWindow) return
        var changed = false
        if (desiredWidthPx > 0 && lp.width != desiredWidthPx) {
            lp.width = desiredWidthPx
            changed = true
        }
        if (panelHeightPinned.value && panelHeightPx > 0 && lp.height != panelHeightPx) {
            lp.height = panelHeightPx
            changed = true
        }
        if (!changed) return
        runCatching {
            context.getSystemService(WindowManager::class.java).updateViewLayout(container, lp)
        }.onFailure { Log.w(TAG, "applyPanelSizeIfNeeded failed", it) }
    }

    // 面板窗口宽度：内容最多 560dp，屏幕更窄时按屏幕宽度留 16dp 边距
    private fun panelWindowWidth(): Int {
        val density = context.resources.displayMetrics.density
        val screenWidth = safeScreenWidth()
        return (PANEL_MAX_WIDTH_DP * density)
            .toInt()
            .coerceAtMost((screenWidth - 16 * density).toInt())
            .coerceAtLeast((240 * density).toInt())
    }

    // 面板窗口和屏幕一样宽时只能上下移动，这里取可用宽度（去掉系统栏）
    private fun safeScreenWidth(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context.resources.displayMetrics.widthPixels
        val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val insets = WindowInsetsCompat.toWindowInsetsCompat(metrics.windowInsets)
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        return metrics.bounds.width() - insets.left - insets.right
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}

/** [replyMessageId] 是本轮正在写的助手消息，这一轮还没有输出时为空 */
internal fun buildFloatingChatUiState(
    conversation: Conversation,
    replyMessageId: Uuid?,
    generating: Boolean,
    processingStatus: String?,
    queue: InboxState,
    error: ChatError?,
): FloatingChatUiState {
    // 不看末尾也不看节点选中的分支：重新生成较早的消息时，后面的节点还在，用户也可能切回了旧回复
    val reply = replyMessageId?.let { id ->
        conversation.messageNodes.firstNotNullOfOrNull { node -> node.messages.firstOrNull { it.id == id } }
    }
    val text = reply?.parts?.filterIsInstance<UIMessagePart.Text>()?.lastOrNull()?.text?.trim().orEmpty()
    val reasoning = reply?.parts?.filterIsInstance<UIMessagePart.Reasoning>()?.lastOrNull()?.reasoning?.trim().orEmpty()
    return FloatingChatUiState(
        conversationId = conversation.id,
        title = conversation.title,
        generating = generating,
        processingStatus = processingStatus,
        phase = reply?.generationPhase() ?: GenerationPhase.Waiting,
        hasReply = reply != null,
        // 还没写正文时显示思考内容。按 Markdown 渲染，不能截断，切在代码块中间后面会整段错乱
        preview = text.ifEmpty { reasoning },
        previewIsReasoning = text.isEmpty(),
        // 和 ChatService 判断是否在等审批用同一个条件，否则快速回复会被审批挡住却看不到原因
        pendingTools = conversation.pendingToolCalls,
        queuedCount = queue.items.size,
        queuePaused = queue.paused,
        error = error,
    )
}
