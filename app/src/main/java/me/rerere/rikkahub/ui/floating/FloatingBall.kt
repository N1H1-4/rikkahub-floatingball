package me.rerere.rikkahub.ui.floating

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ChatAdd01
import me.rerere.hugeicons.stroke.Crop
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.drawAppLogo
import me.rerere.rikkahub.ui.components.ui.logoMorphShape
import me.rerere.rikkahub.ui.components.ui.rememberAppLogoLayers
import kotlin.math.ceil
import kotlin.math.floor

private val BallBaseSize = 52.dp
private val BallMorphSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = 200f,
    visibilityThreshold = 0.1f,
)
private const val BallMorphIntervalMillis = 1400L
private const val BallSpinDurationMillis = 4666

/**
 * 合并后的悬浮球：原来的"对话气泡"和"桌面宠物"在这里合成一个。
 *
 * - 形象：设置里选了图片/Emoji 就用它，否则用会变形的 App 兔子 logo
 * - 生成中：logo 变形 + 自转；自定义形象则加一圈呼吸光晕
 * - 角标：出错 / 待批准工具 / 有新回复
 * - 单击：由调用方决定（有对话就展开面板，否则截屏）；长按：弹菜单
 */
@Composable
fun FloatingBall(
    visuals: DesktopPetVisuals,
    state: FloatingChatUiState?,
    menuOpen: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCloseMenu: () -> Unit,
    onCapture: () -> Unit,
    onOpenChat: (() -> Unit)?,
    onOpenApp: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val size = BallBaseSize * visuals.sizeScale.coerceIn(DESKTOP_PET_MIN_SCALE, DESKTOP_PET_MAX_SCALE)
    val opacity = visuals.opacity.coerceIn(DESKTOP_PET_MIN_OPACITY, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.padding(6.dp).alpha(opacity)) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onTap, onLongClick = onLongPress),
                contentAlignment = Alignment.Center,
            ) {
                BallFace(visuals = visuals, state = state, size = size)
            }
            // 状态角标
            when {
                state?.generating == true -> {}
                state?.error != null -> BallBadge(HugeIcons.Alert01, colors.error, colors.onError)
                state?.pendingTools?.isNotEmpty() == true -> BallBadge(HugeIcons.Wrench01, colors.error, colors.onError)
                state?.hasReply == true -> BallBadge(HugeIcons.Tick01, colors.primary, colors.onPrimary)
            }
        }

        if (menuOpen) {
            BallMenu(
                onCapture = onCapture,
                onOpenChat = onOpenChat,
                onOpenApp = onOpenApp,
                onClose = onCloseMenu,
            )
        }
    }
}

@Composable
private fun BallFace(visuals: DesktopPetVisuals, state: FloatingChatUiState?, size: androidx.compose.ui.unit.Dp) {
    when (val avatar = visuals.avatar) {
        is Avatar.Image -> AsyncImage(
            model = avatar.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )

        is Avatar.Emoji -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatar.content,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = (size.value * 0.62f).sp),
                lineHeight = 0.8.em,
                textAlign = TextAlign.Center,
            )
        }

        Avatar.Dummy -> AnimatedLogo(size = size, morphing = state?.generating == true)
    }
}

/** 默认形象：会变形的 App 兔子 logo，生成中变形 + 自转 */
@Composable
private fun AnimatedLogo(size: androidx.compose.ui.unit.Dp, morphing: Boolean) {
    val logoLayers = rememberAppLogoLayers()
    val shapePosition = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    // 球收起时（窗口隐藏）没必要继续跑动画
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = morphing && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(running) {
        if (!running) {
            shapePosition.animateTo(ceil(shapePosition.value), BallMorphSpring)
            return@LaunchedEffect
        }
        launch {
            spin.animateTo(
                targetValue = spin.value + 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    tween(BallSpinDurationMillis, easing = LinearEasing),
                ),
            )
        }
        while (true) {
            val morph = launch { shapePosition.animateTo(floor(shapePosition.value) + 1f, BallMorphSpring) }
            delay(BallMorphIntervalMillis)
            morph.join()
        }
    }

    val logoShape = logoMorphShape(shapePosition.value, extraRotation = spin.value)
    Box(
        modifier = Modifier
            .size(size)
            // 不能用 elevation 阴影：轮廓是凹多边形，每帧重新三角剖分会把 RenderThread 卡住
            .dropShadow(logoShape, Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), alpha = 0.3f))
            .clip(logoShape)
            .drawAppLogo(logoLayers),
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BallBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    tint: Color,
) {
    Surface(
        shape = CircleShape,
        color = background,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .size(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        }
    }
}

/** 长按弹出的快捷菜单；点任意一项或点菜单外的球都会收起 */
@Composable
private fun BallMenu(
    onCapture: () -> Unit,
    onOpenChat: (() -> Unit)?,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            if (onOpenChat != null) {
                BallMenuItem(HugeIcons.ChatAdd01, "展开对话") {
                    onClose()
                    onOpenChat()
                }
            }
            BallMenuItem(HugeIcons.Crop, "截图提问") {
                onClose()
                onCapture()
            }
            BallMenuItem(HugeIcons.ChatAdd01, "打开 RikkaHub") {
                onClose()
                onOpenApp()
            }
        }
    }
}

@Composable
private fun BallMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(min = 132.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
