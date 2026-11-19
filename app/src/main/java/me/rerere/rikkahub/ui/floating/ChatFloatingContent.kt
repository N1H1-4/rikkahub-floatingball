package me.rerere.rikkahub.ui.floating

import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.PHONE_USE_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.local.phoneUseActionOf
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.model.GenerationPhase
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.drawAppLogo
import me.rerere.rikkahub.ui.components.ui.logoMorphShape
import me.rerere.rikkahub.ui.components.ui.rememberAppLogoLayers
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdown
import me.rerere.ui.icons.ReasoningHigh
import me.rerere.ui.icons.ReasoningLow
import me.rerere.ui.icons.ReasoningMedium
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.uuid.Uuid

// 和 Material3 LoadingIndicator 的参数一致；放宽 visibilityThreshold 让低阻尼弹簧在间隔内结束
private val MorphSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 200f, visibilityThreshold = 0.1f)
// 弧线把手相对窗口右下角的内缩：卡片内边距 12/26dp + 圆角 28dp，盒子 30dp
// 卡片的实际内边距与圆角，弧线要按这个真实几何画
private val CardInsetStart = 12.dp
private val CardInsetBottom = 26.dp
private val CardCorner = 28.dp

private val ArcInsetStart = 2.dp
private val ArcInsetBottom = 14.dp

private const val MORPH_INTERVAL_MILLIS = 650L
private const val SPIN_DURATION_MILLIS = 4666

/**
 * 悬浮窗的四个阶段，决定能不能缩放、以及输入区怎么排布：
 * - [Idle]      还没开始的空对话：固定大小，自动弹键盘方便 quick reply
 * - [Sending]   刚发出、还没有内容回来：只允许调宽度
 * - [Streaming] 正在输出内容：宽度和高度都能调，可以放到超过半个屏幕
 * - [Finished]  这一轮结束：同样可调，并把模型/思考深度按钮挪到输入框那一行
 */
enum class PanelPhase { Idle, Sending, Streaming, Finished }

/** 悬浮窗展示的会话状态 */
/** 悬浮窗里展示的一条历史消息 */
data class FloatingMessage(val isUser: Boolean, val text: String)

data class FloatingChatUiState(
    val conversationId: Uuid,
    val title: String,
    val generating: Boolean,
    /** 网络重试等临时提示 */
    val processingStatus: String?,
    /** 本轮回复在做什么 */
    val phase: GenerationPhase,
    /** 本轮是否已经有回复 */
    val hasReply: Boolean,
    /** 已有的历史消息，用来在悬浮窗里显示当前会话的内容 */
    val history: List<FloatingMessage>,
    /** 本轮回复的正文（Markdown），还没写正文时是思考内容 */
    val preview: String,
    val previewIsReasoning: Boolean,
    val pendingTools: List<UIMessagePart.Tool>,
    val queuedCount: Int,
    val queuePaused: Boolean,
    /** 这一轮出现的错误 */
    val error: ChatError?,
) {
    val failed: Boolean get() = !generating && error != null

    val panelPhase: PanelPhase
        get() = when {
            generating && !hasReply -> PanelPhase.Sending
            generating -> PanelPhase.Streaming
            hasReply -> PanelPhase.Finished
            else -> PanelPhase.Idle
        }
}

@Composable
private fun BoxScope.StatusBadge(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(20.dp)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(10.dp))
    }
}

/** 展开后的面板：看进度、审批工具、停止生成和快速回复。[maxHeight] 是窗口能占的高度 */
@Composable
fun ChatFloatingPanel(
    state: FloatingChatUiState,
    maxHeight: Dp,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onOpenConversation: (draftText: String) -> Unit,
    onToolApproval: (toolCallId: String, approved: Boolean) -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onResumeQueue: () -> Unit,
    /** 截图发过来时先挂在这里的图片，发出后由调用方清掉 */
    draftImage: String? = null,
    onRemoveDraft: () -> Unit = {},
    /** 拖顶部的小横线时把窗口整体挪动 (dx, dy) */
    onMoveWindow: (Float, Float) -> Unit = { _, _ -> },
    /** 窗口高度是否已被手动指定：指定后内容要填满窗口，而不是跟着内容走 */
    heightPinned: Boolean = false,
    /** 拖角缩放：按下 / 移动（屏幕坐标）/ 松手 */
    onResizeBegin: (screenX: Float, screenY: Float, mirrored: Boolean) -> Unit =
        { _, _, _ -> },
    onResizeMove: (screenX: Float, screenY: Float, vertical: Boolean) -> Unit = { _, _, _ -> },
    onResizeEnd: () -> Unit = {},
) {
    // 输入框内容提到这一层：点全屏要把它带进聊天页，不能丢
    var replyText by rememberSaveable(state.conversationId) { mutableStateOf("") }
    var modelsOpen by remember { mutableStateOf(false) }
    var reasoningOpen by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }

    val phase = state.panelPhase

    // 空对话固定大小；其余阶段能调宽度，输出中和输出完还能调高度
    val canResizeWidth = phase != PanelPhase.Idle
    val canResizeHeight = phase == PanelPhase.Streaming || phase == PanelPhase.Finished
    var leftArcPressed by remember { mutableStateOf(false) }
    var rightArcPressed by remember { mutableStateOf(false) }
    // 卡片在屏幕上的真实位置，弧线按它画，不再用常量推（生成中/钉住高度时布局会变）
    var cardRect by remember { mutableStateOf(Rect.Zero) }

    // 窗口和屏幕一样宽，大屏上限制宽度并居中
    Box(
        modifier = if (heightPinned) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(max = maxHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                // 卡片必须跟着窗口走：之前这里限了 560dp，窗口却能继续变大，
                // 结果卡片停在原地、把手跑到卡片外面，看起来就是"拖到这么大就失灵"
                .fillMaxWidth()
                .then(if (heightPinned) Modifier.fillMaxHeight() else Modifier)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 26.dp)
                .onGloballyPositioned { cardRect = it.boundsInRoot() },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(
                // 高度被手动指定后要先把这一列撑满卡片，下面的 weight 才有确定的高度可以分
                modifier = Modifier
                    .then(if (heightPinned) Modifier.fillMaxHeight() else Modifier)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PanelDragHandle(onMove = onMoveWindow)
                PanelHeader(
                    state = state,
                    onOpenConversation = { onOpenConversation(replyText) },
                    onCollapse = onCollapse,
                    onDismiss = onDismiss,
                )
                // 多个审批、横屏或大字体时放不下，中间这段滚动，标题和输入栏始终可见。
                // 中间这块是阅读区：已有历史 + 本轮流式预览 + 待审批/排队提示。
                // 手动拉高过窗口时（heightPinned）让它吃掉剩余高度，输入栏才会贴到底部
                val hasMessages = state.history.isNotEmpty() ||
                    state.preview.isNotEmpty() ||
                    state.pendingTools.isNotEmpty() ||
                    state.queuedCount > 0
                if (hasMessages) {
                    val historyScroll = rememberScrollState()
                    // 打开时落到最新一条；生成中跟着内容走
                    LaunchedEffect(state.conversationId) { historyScroll.scrollTo(historyScroll.maxValue) }
                    LaunchedEffect(state.preview.length, state.generating) {
                        if (state.generating) historyScroll.scrollTo(historyScroll.maxValue)
                    }
                    // 阅读区高度：手动拉高过窗口时吃掉剩余高度；否则给一个有上下限的固定区间，
                    // 内容超出就在这块里滚动（不能让高度跟着内容长，否则一长就顶到屏幕边缘）
                    val readingModifier = if (heightPinned) {
                        Modifier.weight(1f)
                    } else {
                        Modifier
                            .heightIn(min = 200.dp, max = maxHeight * 0.62f)
                    }
                    Column(
                        modifier = readingModifier.verticalScroll(historyScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.history.forEach { message -> FloatingMessageBubble(message) }
                        if (state.preview.isNotEmpty()) {
                            MessagePreview(
                                text = state.preview,
                                isReasoning = state.previewIsReasoning,
                                generating = state.generating,
                                fillHeight = false,
                            )
                        }
                        state.pendingTools.forEach { tool ->
                            PendingToolCard(
                                tool = tool,
                                onApproval = { approved -> onToolApproval(tool.toolCallId, approved) },
                                onOpenConversation = { onOpenConversation(replyText) },
                            )
                        }
                        if (state.queuedCount > 0) {
                            QueueRow(count = state.queuedCount, paused = state.queuePaused, onResume = onResumeQueue)
                        }
                    }
                } else if (heightPinned) {
                    Spacer(Modifier.weight(1f))
                }
                ComposerToolbar(
                    conversationId = state.conversationId,
                    draftImage = draftImage,
                    onRemoveDraft = onRemoveDraft,
                    onApplyQuickMessage = {
                        replyText = it
                        quickOpen = false
                    },
                    quickOpen = quickOpen,
                    // 输出结束后模型/思考深度按钮挪到下面输入框那一行，这一行就能收窄
                    showModelControls = phase != PanelPhase.Finished,
                    modelsOpen = modelsOpen,
                    reasoningOpen = reasoningOpen,
                    onToggleModels = {
                        modelsOpen = !modelsOpen
                        reasoningOpen = false
                    },
                    onToggleReasoning = {
                        reasoningOpen = !reasoningOpen
                        modelsOpen = false
                    },
                )
                ReplyBar(
                    text = replyText,
                    onTextChange = { replyText = it },
                    generating = state.generating,
                    canSendEmpty = draftImage != null,
                    requestFocus = phase == PanelPhase.Idle,
                    leadingControls = {
                        QuickMessageToggle(
                            expanded = quickOpen,
                            onToggle = {
                                quickOpen = !quickOpen
                                modelsOpen = false
                                reasoningOpen = false
                            },
                        )
                    },
                    onSend = {
                        replyText = ""
                        onSend(it)
                    },
                    onStop = onStop,
                    trailingControls = if (phase == PanelPhase.Finished) {
                        {
                            ModelReasoningButtons(
                                conversationId = state.conversationId,
                                modelsOpen = modelsOpen,
                                reasoningOpen = reasoningOpen,
                                onToggleModels = {
                                    modelsOpen = !modelsOpen
                                    reasoningOpen = false
                                },
                                onToggleReasoning = {
                                    reasoningOpen = !reasoningOpen
                                    modelsOpen = false
                                },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        // 左右下角的弧线：空对话不可缩放；发出后可以调宽度；输出中和输出完还能调高度
        if (canResizeWidth) {
            // 弧线按卡片圆角的真实几何统一绘制，触摸区单独放（比弧线大，方便按中）
            ResizeArcs(leftPressed = leftArcPressed, rightPressed = rightArcPressed, cardRect = cardRect)
            ResizeHandle(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = ArcInsetStart, bottom = ArcInsetBottom),
                onBegin = { x, y -> onResizeBegin(x, y, true) },
                onMove = { x, y -> onResizeMove(x, y, canResizeHeight) },
                onEnd = onResizeEnd,
                onPressedChange = { leftArcPressed = it },
            )
            ResizeHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = ArcInsetStart, bottom = ArcInsetBottom),
                onBegin = { x, y -> onResizeBegin(x, y, false) },
                onMove = { x, y -> onResizeMove(x, y, canResizeHeight) },
                onEnd = onResizeEnd,
                onPressedChange = { rightArcPressed = it },
            )
        }
    }
}

/**
 * 角落里的缩放把手：只负责接触摸（可以比弧线大很多，方便按中），
 * 弧线由 [ResizeArcs] 按卡片圆角的真实几何统一绘制。
 */
@Composable
private fun ResizeHandle(
    modifier: Modifier = Modifier,
    onBegin: (Float, Float) -> Unit,
    onMove: (Float, Float) -> Unit,
    onEnd: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        onPressedChange(true)
                        onBegin(event.rawX, event.rawY)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        onMove(event.rawX, event.rawY)
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onPressedChange(false)
                        onEnd()
                        true
                    }

                    else -> false
                }
            },
    )
}

/** 悬浮窗里的一条历史消息：用户靠右、助手靠左，助手内容按 Markdown 渲染 */
@Composable
private fun FloatingMessageBubble(message: FloatingMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
            modifier = if (isUser) Modifier.widthIn(max = 280.dp) else Modifier.fillMaxWidth(),
        ) {
            if (isUser) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                MaterialMarkdown(
                    content = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    renderMath = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * 两段贴角弧线。
 *
 * 位置直接取卡片测量出来的真实矩形（root 坐标）和主题里的真实圆角半径，
 * 不再按 padding 常量推算 —— 生成中滚动、手动钉住高度时布局会变，推算就会飘。
 */
@Composable
private fun ResizeArcs(leftPressed: Boolean, rightPressed: Boolean, cardRect: Rect) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = MaterialTheme.shapes.extraLarge
    val leftAlpha by animateFloatAsState(if (leftPressed) 1f else 0f, label = "arc-left")
    val rightAlpha by animateFloatAsState(if (rightPressed) 1f else 0f, label = "arc-right")
    if (leftAlpha <= 0.01f && rightAlpha <= 0.01f) return
    if (cardRect == Rect.Zero) return

    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasOrigin = it.positionInRoot() },
    ) {
        val stroke = 2.5.dp.toPx()
        val cornerPx = shape.topStart.toPx(Size(cardRect.width, cardRect.height), this)
        val radius = cornerPx + 3.dp.toPx()
        // 卡片矩形换算到画布坐标
        val cardLeft = cardRect.left - canvasOrigin.x
        val cardRight = cardRect.right - canvasOrigin.x
        val cardBottom = cardRect.bottom - canvasOrigin.y

        if (leftAlpha > 0.01f) {
            val centerX = cardLeft + cornerPx
            val centerY = cardBottom - cornerPx
            drawArc(
                color = color.copy(alpha = leftAlpha * 0.75f),
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (rightAlpha > 0.01f) {
            val centerX = cardRight - cornerPx
            val centerY = cardBottom - cornerPx
            drawArc(
                color = color.copy(alpha = rightAlpha * 0.75f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

// 顶部的小横线：拖它可以把整个悬浮窗挪走
@Composable
private fun PanelDragHandle(onMove: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun PanelHeader(
    state: FloatingChatUiState,
    onOpenConversation: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.generating) {
            ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (state.failed) colors.errorContainer else colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = state.statusIcon(),
                    contentDescription = null,
                    tint = if (state.failed) colors.onErrorContainer else colors.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.failed) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenConversation) {
            Icon(HugeIcons.Fullscreen, contentDescription = stringResource(R.string.floating_chat_open_conversation))
        }
        IconButton(onClick = onCollapse) {
            Icon(HugeIcons.Remove01, contentDescription = stringResource(R.string.floating_chat_collapse))
        }
        IconButton(onClick = onDismiss) {
            Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.floating_chat_close))
        }
    }
}

// 用不带应用功能的 MaterialMarkdown：应用里的 MarkdownBlock 依赖导航、Activity 等悬浮窗里没有的东西
@Composable
private fun MessagePreview(
    text: String,
    isReasoning: Boolean,
    generating: Boolean,
    modifier: Modifier = Modifier,
    /** true 时撑满外层给的高度（面板被手动拉高），false 时用固定上限 */
    fillHeight: Boolean = false,
) {
    val scrollState = rememberScrollState()
    // 生成中跟随最新内容。Markdown 在后台解析，内容变高晚于文本变化，按可滚动距离跟随
    LaunchedEffect(generating) {
        if (generating) snapshotFlow { scrollState.maxValue }.collect { scrollState.scrollTo(it) }
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (isReasoning) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        MaterialMarkdown(
            content = text,
            style = MaterialTheme.typography.bodyMedium,
            streaming = generating,
            // 悬浮窗里没有 LocalSettings，公式渲染在这里单独打开
            renderMath = true,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(max = 200.dp),
                )
                .verticalScroll(scrollState)
                .padding(12.dp),
        )
    }
}

@Composable
private fun PendingToolCard(
    tool: UIMessagePart.Tool,
    onApproval: (approved: Boolean) -> Unit,
    onOpenConversation: () -> Unit,
) {
    val isAskUser = tool.toolName == ASK_USER_TOOL_NAME
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isAskUser) {
                    stringResource(R.string.floating_chat_ask_user)
                } else {
                    stringResource(R.string.floating_chat_tool_request, tool.toolName.substringAfterLast("__"))
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!isAskUser && tool.input.isNotBlank()) {
                Text(
                    text = tool.input.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (isAskUser) {
                    // 问答有多个问题和选项，在应用里回答
                    Button(onClick = onOpenConversation) { Text(stringResource(R.string.floating_chat_answer)) }
                } else {
                    OutlinedButton(onClick = { onApproval(false) }) { Text(stringResource(R.string.chat_message_tool_deny)) }
                    Button(onClick = { onApproval(true) }) { Text(stringResource(R.string.chat_message_tool_approve)) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(count: Int, paused: Boolean, onResume: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (paused) R.string.chat_page_queue_paused_count else R.string.chat_page_queue_pending_count,
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (paused) {
            TextButton(onClick = onResume) { Text(stringResource(R.string.chat_page_queue_resume)) }
        }
    }
}

@Composable
private fun ReplyBar(
    text: String,
    onTextChange: (String) -> Unit,
    generating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    canSendEmpty: Boolean = false,
    /** 空对话阶段自动聚焦并弹键盘，方便直接 quick reply */
    requestFocus: Boolean = false,
    /** 插在输入框左边的东西（快捷消息按钮） */
    leadingControls: (@Composable () -> Unit)? = null,
    /** 插在输入框和发送键之间的东西（输出完成后放模型/思考深度按钮） */
    trailingControls: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun send() {
        val content = text.trim()
        // 只有图片没有文字时也要能发
        if (content.isEmpty() && !canSendEmpty) return
        onSend(content)
        focusManager.clearFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingControls?.invoke()
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            // 生成中发送的消息会排队，等这一轮结束后再发
            placeholder = {
                Text(
                    stringResource(
                        if (generating) R.string.floating_chat_reply_queued_placeholder else R.string.floating_chat_reply_placeholder
                    )
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        if (generating) {
            FilledTonalIconButton(onClick = onStop) {
                Icon(HugeIcons.Stop, contentDescription = stringResource(R.string.stop))
            }
        }
        trailingControls?.invoke()
        FilledIconButton(onClick = ::send, enabled = text.isNotBlank() || canSendEmpty) {
            Icon(HugeIcons.ArrowUp02, contentDescription = stringResource(R.string.send))
        }
    }
}

/**
 * 输入框上方那一行：左边是刚截的图，中间是当前助手的快捷消息，
 * 右边是模型和思考深度。
 *
 * 模型列表和思考深度滑块都在面板内就地展开：悬浮窗是独立窗口，弹出式面板的行为更不可控。
 */
@Composable
private fun ComposerToolbar(
    conversationId: Uuid,
    draftImage: String?,
    onRemoveDraft: () -> Unit,
    onApplyQuickMessage: (String) -> Unit,
    /** 快捷消息默认收起，点了输入框左边的闪电才展开，平时不占一行 */
    quickOpen: Boolean,
    /** false 时把模型/思考深度按钮让给下面输入框那一行（输出完成阶段） */
    showModelControls: Boolean,
    modelsOpen: Boolean,
    reasoningOpen: Boolean,
    onToggleModels: () -> Unit,
    onToggleReasoning: () -> Unit,
) {
    val chatService = koinInject<ChatService>()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val conversation by chatService.getConversationFlow(conversationId).collectAsStateWithLifecycle()
    val assistant = settings.getAssistantOf(conversation)
    val model = settings.getChatModelOf(conversation)
    val quickMessages = settings.getQuickMessagesOfAssistant(assistant)
    val scope = rememberCoroutineScope()

    // 没有草稿、不需要模型按钮、快捷消息也没展开时，这一整块都不占空间
    if (draftImage == null && !showModelControls && !quickOpen && !modelsOpen && !reasoningOpen) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (draftImage != null) {
                DraftAttachment(image = draftImage, onRemove = onRemoveDraft)
            }
            if (quickOpen) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (quickMessages.isEmpty()) {
                        item {
                            ComposerChip(
                                label = stringResource(R.string.floating_chat_quick_empty),
                                selected = false,
                                onClick = {},
                            )
                        }
                    } else {
                        items(quickMessages, key = { it.id.toString() }) { quickMessage ->
                            ComposerChip(
                                label = quickMessage.title.ifBlank { quickMessage.content },
                                selected = false,
                                onClick = { onApplyQuickMessage(quickMessage.content) },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            // 模型/思考深度：输出完成阶段挪到下面输入行，这里就不画了
            if (showModelControls) {
            // 模型：显示当前模型的图标；没选模型时用大脑图标
            ComposerIconButton(
                label = stringResource(R.string.floating_chat_model),
                selected = modelsOpen,
                onClick = onToggleModels,
            ) {
                if (model != null) {
                    AutoAIIcon(
                        name = model.modelId,
                        modifier = Modifier.size(24.dp),
                        color = Color.Transparent,
                    )
                } else {
                    Icon(HugeIcons.Brain02, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            // 思考深度：图标跟着档位一起变
            ComposerIconButton(
                label = stringResource(R.string.floating_chat_reasoning),
                selected = reasoningOpen,
                onClick = onToggleReasoning,
            ) {
                Icon(assistant.reasoningLevel.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            }
        }

        if (modelsOpen) {
            val models = remember(settings.providers) {
                settings.providers.flatMap { it.models }.filter { it.type == ModelType.CHAT }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(models, key = { it.id.toString() }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onToggleModels()
                                scope.launch {
                                    chatService.updateChatAssistant(conversationId) {
                                        it.copy(chatModelId = item.id)
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AutoAIIcon(
                            name = item.modelId,
                            modifier = Modifier.size(24.dp),
                            color = Color.Transparent,
                        )
                        Text(
                            text = item.displayName.ifBlank { item.modelId },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.id == model?.id) {
                            Icon(HugeIcons.Tick01, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (reasoningOpen) {
            val levels = ReasoningLevel.entries
            var sliderValue by remember(assistant.reasoningLevel) {
                mutableFloatStateOf(
                    levels.indexOf(assistant.reasoningLevel).toFloat().coerceAtLeast(0f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    assistant.reasoningLevel.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val level = levels[sliderValue.roundToInt().coerceIn(0, levels.lastIndex)]
                        scope.launch {
                            chatService.updateChatAssistant(conversationId) {
                                it.copy(reasoningLevel = level)
                            }
                        }
                    },
                    valueRange = 0f..levels.lastIndex.toFloat(),
                    steps = (levels.size - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(assistant.reasoningLevel.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ComposerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 输入框左边的闪电：展开/收起快捷消息 */
@Composable
private fun QuickMessageToggle(expanded: Boolean, onToggle: () -> Unit) {
    ComposerIconButton(
        label = stringResource(R.string.floating_chat_quick_messages),
        selected = expanded,
        onClick = onToggle,
    ) {
        Icon(HugeIcons.Zap, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** 输出完成阶段放在输入框和发送键之间的两个按钮，逻辑和工具栏里那套一致 */
@Composable
private fun ModelReasoningButtons(
    conversationId: Uuid,
    modelsOpen: Boolean,
    reasoningOpen: Boolean,
    onToggleModels: () -> Unit,
    onToggleReasoning: () -> Unit,
) {
    val chatService = koinInject<ChatService>()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val conversation by chatService.getConversationFlow(conversationId).collectAsStateWithLifecycle()
    val assistant = settings.getAssistantOf(conversation)
    val model = settings.getChatModelOf(conversation)

    ComposerIconButton(
        label = stringResource(R.string.floating_chat_model),
        selected = modelsOpen,
        onClick = onToggleModels,
    ) {
        if (model != null) {
            AutoAIIcon(name = model.modelId, modifier = Modifier.size(24.dp), color = Color.Transparent)
        } else {
            Icon(HugeIcons.Brain02, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
    ComposerIconButton(
        label = stringResource(R.string.floating_chat_reasoning),
        selected = reasoningOpen,
        onClick = onToggleReasoning,
    ) {
        Icon(assistant.reasoningLevel.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ComposerIconButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(24.dp)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// 思考深度的图标跟着档位变，和聊天输入框里那个保持一致
private fun ReasoningLevel.icon(): ImageVector = when (this) {
    ReasoningLevel.OFF -> HugeIcons.Brain02
    ReasoningLevel.AUTO -> HugeIcons.Brain02
    ReasoningLevel.LOW -> ReasoningLow
    ReasoningLevel.MEDIUM -> ReasoningMedium
    ReasoningLevel.HIGH, ReasoningLevel.XHIGH, ReasoningLevel.MAX -> ReasoningHigh
}

private fun ReasoningLevel.labelRes(): Int = when (this) {
    ReasoningLevel.OFF -> R.string.reasoning_off
    ReasoningLevel.AUTO -> R.string.reasoning_auto
    ReasoningLevel.LOW -> R.string.reasoning_light
    ReasoningLevel.MEDIUM -> R.string.reasoning_medium
    ReasoningLevel.HIGH -> R.string.reasoning_heavy
    ReasoningLevel.XHIGH -> R.string.reasoning_xhigh
    ReasoningLevel.MAX -> R.string.reasoning_max
}

// 截图发过来时挂在输入框上方的缩略图
@Composable
private fun DraftAttachment(image: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.screenshot_remove_attachment),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun FloatingChatUiState.statusIcon(): ImageVector = when {
    error != null -> HugeIcons.Alert01
    pendingTools.isNotEmpty() -> HugeIcons.Wrench01
    hasReply -> HugeIcons.Tick01
    else -> HugeIcons.Sparkles
}

@Composable
private fun FloatingChatUiState.statusText(): String {
    if (!generating) {
        return when {
            error != null -> error.title ?: error.error.message ?: stringResource(R.string.floating_chat_status_failed)
            pendingTools.any { it.toolName == ASK_USER_TOOL_NAME } -> stringResource(R.string.floating_chat_status_awaiting_answer)
            pendingTools.isNotEmpty() -> stringResource(R.string.floating_chat_status_awaiting_approval)
            hasReply -> stringResource(R.string.floating_chat_status_done)
            else -> stringResource(R.string.floating_chat_status_idle)
        }
    }
    // 网络重试等提示优先
    processingStatus?.let { return it }
    return when (phase) {
        is GenerationPhase.RunningTool -> stringResource(R.string.notification_live_update_tool, phase.displayName())
        is GenerationPhase.Thinking -> stringResource(R.string.notification_live_update_thinking)
        is GenerationPhase.Writing -> stringResource(R.string.notification_live_update_writing)
        GenerationPhase.Waiting -> stringResource(R.string.notification_live_update_title)
    }
}

// Phone Use 一轮要调用很多次同一个工具，带上动作名才看得出进行到哪一步
private fun GenerationPhase.RunningTool.displayName(): String {
    if (toolName != PHONE_USE_TOOL_NAME) return toolName
    return phoneUseActionOf(input)?.let { "$toolName · $it" } ?: toolName
}
