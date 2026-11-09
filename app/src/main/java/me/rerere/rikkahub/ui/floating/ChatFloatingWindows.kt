package me.rerere.rikkahub.ui.floating

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petterp.floatingx.compose.compose
import com.petterp.floatingx.core.FloatingX
import com.petterp.floatingx.core.FxControl
import com.petterp.floatingx.core.animation.FxAnimations
import com.petterp.floatingx.core.gesture.FxDrag
import com.petterp.floatingx.core.gesture.FxGesture
import com.petterp.floatingx.core.layout.FxAdsorb
import com.petterp.floatingx.core.layout.FxGravity
import com.petterp.floatingx.core.storage.FxSpStorage
import com.petterp.floatingx.system.permission.FxPermission
import com.petterp.floatingx.system.permission.FxPermissionStrategy
import com.petterp.floatingx.system.systemHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatFloatingTracker
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import kotlin.uuid.Uuid

private const val TAG = "ChatFloatingWindows"
private const val BUBBLE_TAG = "chat_floating_bubble"
private const val PANEL_TAG = "chat_floating_panel"
private const val PANEL_TOP_OFFSET_DP = 8f
private const val PANEL_MAX_WIDTH_DP = 560f
private const val PANEL_MIN_WIDTH_DP = 320f
private const val PANEL_COMPACT_WIDTH_DP = 380f
private const val PANEL_MIN_HEIGHT_DP = 180f
private const val PANEL_DEFAULT_HEIGHT_DP = 240f
private const val PANEL_RESIZE_SLOP_DP = 8f

/**
 * 把 [ChatFloatingTracker] 的结果显示成系统悬浮窗：收起时是贴边的小图标，展开是固定在顶部的面板。
 * 两个窗口共用这里汇总的一份 [FloatingChatUiState]。FloatingX 只能在主线程操作，协程都跑在 AppScope 上。
 */
class ChatFloatingWindows(
    private val context: Application,
    private val appScope: AppScope,
    private val tracker: ChatFloatingTracker,
    lazyChatService: Lazy<ChatService>,
) {
    private val chatService by lazyChatService
    private var bubble: FxControl? = null
    private var panel: FxControl? = null

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
        // 后台弹不出权限申请页，没有权限就不显示
        if (expanded == null || !FxPermission.isGranted(context)) {
            bubble?.cancel()
            panel?.cancel()
            bubble = null
            panel = null
            return
        }
        val bubble = bubble ?: installBubble().also { bubble = it }
        if (expanded) {
            bubble.hide()
            (panel ?: installPanel().also { panel = it }).show()
            applyPanelSizeIfNeeded()
        } else {
            panel?.hide()
            bubble.show()
        }
    }

    private fun installBubble(): FxControl = FloatingX.install(BUBBLE_TAG) {
        compose {
            WindowContent { state -> ChatFloatingBubble(state = state, onClick = tracker::expand) }
        }
        anchor(FxGravity.CENTER_END, dy = -dp(96f))
        margin(left = dp(8f), right = dp(8f))
        adsorb(FxAdsorb.horizontal())
        persist(FxSpStorage(context))
        animation(FxAnimations.scale())
        systemHost(context) { permission(FxPermissionStrategy.skip()) }
    }

    private fun installPanel(): FxControl = FloatingX.install(PANEL_TAG) {
        compose {
            WindowContent { state ->
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
        val state by uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        RikkahubTheme {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                state?.let { content(it) }
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
    private var resizeTracking = false

    private fun panelWindow(): Pair<View, WindowManager.LayoutParams>? {
        val control = panel ?: return null
        val container = control.contentView?.parent as? View ?: return null
        val lp = container.layoutParams as? WindowManager.LayoutParams ?: return null
        return container to lp
    }

    private fun onResizeBegin(screenX: Float, screenY: Float) {
        val (container, lp) = panelWindow() ?: return
        val density = context.resources.displayMetrics.density
        resizeStartWidth = desiredWidthPx.takeIf { it > 0 }
            ?: (if (lp.width > 0) lp.width else panelWindowWidth())
        // 高度是 WRAP_CONTENT 时不知道实际值，取容器当前量出来的高度
        resizeStartHeight = if (lp.height > 0) {
            lp.height
        } else {
            container.height.takeIf { it > 0 } ?: (PANEL_DEFAULT_HEIGHT_DP * density).toInt()
        }
        // 直接用按下点做基准，拖动从第一帧就跟手
        resizeStartX = screenX
        resizeStartY = screenY
        resizeTracking = true
    }

    private fun onResizeMove(screenX: Float, screenY: Float, vertical: Boolean, mirrored: Boolean) {
        val (container, lp) = panelWindow() ?: return
        if (!resizeTracking) return
        val density = context.resources.displayMetrics.density
        val minWidth = (PANEL_MIN_WIDTH_DP * density).toInt()
        val maxWidth = safeScreenWidth()

        val dx = screenX - resizeStartX
        // 窗口是居中锚点：手指往右拖 dx，宽度要涨 2dx 右边缘才跟得上
        val signedDx = if (mirrored) -dx else dx
        val nextWidth = (resizeStartWidth + signedDx * 2).toInt().coerceIn(minWidth, maxWidth)

        // 缩到阈值以内就收成气泡：再窄下去图标和文字会挤在一起
        if (nextWidth <= (PANEL_COMPACT_WIDTH_DP * density).toInt()) {
            lp.width = panelWindowWidth()
            runCatching {
                context.getSystemService(WindowManager::class.java).updateViewLayout(container, lp)
            }
            resizeTracking = false
            tracker.collapse()
            return
        }

        var changed = false
        if (nextWidth != lp.width) {
            lp.width = nextWidth
            desiredWidthPx = nextWidth
            changed = true
        }

        // 高度只在「明显是竖着拖」时才改：
        // 横拖时 dy≈0，一旦也去写高度就会把面板从 wrap_content 钉死，触发整块重新测量，
        // 手势被打断后重新开始，宽度会被反复重算，表现就是抖动甚至突然缩成气泡。
        val generating = uiState.value?.generating == true
        if (vertical && !generating) {
            val dy = screenY - resizeStartY
            val verticalDominant = kotlin.math.abs(dy) > kotlin.math.abs(dx) &&
                kotlin.math.abs(dy) > (PANEL_RESIZE_SLOP_DP * density)
            if (verticalDominant) {
                val minHeight = (PANEL_MIN_HEIGHT_DP * density).toInt()
                val maxHeight = safeScreenHeight() - (PANEL_TOP_OFFSET_DP * density).toInt()
                val nextHeight = (resizeStartHeight + dy).toInt().coerceIn(minHeight, maxHeight)
                if (nextHeight != lp.height) {
                    lp.height = nextHeight
                    panelHeightPx = nextHeight
                    panelHeightPinned.value = true
                    changed = true
                }
            }
        }

        if (!changed) return
        runCatching {
            context.getSystemService(WindowManager::class.java).updateViewLayout(container, lp)
        }.onFailure { Log.w(TAG, "resize panel failed", it) }
    }

    private fun onResizeEnd() {
        resizeTracking = false
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
