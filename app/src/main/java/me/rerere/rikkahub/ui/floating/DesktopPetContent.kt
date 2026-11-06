package me.rerere.rikkahub.ui.floating

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val BaseSize = 64.dp

const val DESKTOP_PET_MIN_SCALE = 0.6f
const val DESKTOP_PET_MAX_SCALE = 2.0f
const val DESKTOP_PET_MIN_OPACITY = 0.25f

/** 桌宠的外观参数，任意一项变化都会让悬浮窗里的内容重组 */
data class DesktopPetVisuals(
    /** 空表示用内置形象，否则是 file:// 路径 */
    val imagePath: String = "",
    val sizeScale: Float = 1f,
    val opacity: Float = 1f,
)

/**
 * 桌宠本体：有导入的形象就用 Coil 画（gif/webp 动图由全局 ImageLoader 解码），
 * 没有就用内置的 Canvas 表情脸。
 */
@Composable
fun DesktopPetContent(visuals: DesktopPetVisuals, onClick: () -> Unit) {
    val size = BaseSize * visuals.sizeScale.coerceIn(DESKTOP_PET_MIN_SCALE, DESKTOP_PET_MAX_SCALE)
    val opacity = visuals.opacity.coerceIn(DESKTOP_PET_MIN_OPACITY, 1f)
    val imagePath = remember(visuals.imagePath) {
        visuals.imagePath.takeIf { it.isNotBlank() }
    }

    // 不用 Surface：它会吃掉触摸事件，拖动交给 FloatingX 处理
    Box(modifier = Modifier.padding(6.dp).alpha(opacity)) {
        if (imagePath != null) {
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
            )
        } else {
            DefaultMascot(diameter = size, onClick = onClick)
        }
    }
}

/** 内置形象：一个 Canvas 画出来的表情脸，不依赖任何图片资源 */
@Composable
private fun DefaultMascot(diameter: Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(onClick = onClick),
    ) {
        // 注意不要用外层的 Dp 参数名，这里的 size 是画布尺寸
        val width = size.width
        val height = size.height
        val min = size.minDimension
        val ink = colors.onPrimaryContainer

        // 两只耳朵
        drawCircle(
            color = ink.copy(alpha = 0.85f),
            radius = min * 0.135f,
            center = Offset(width * 0.26f, height * 0.16f),
        )
        drawCircle(
            color = ink.copy(alpha = 0.85f),
            radius = min * 0.135f,
            center = Offset(width * 0.74f, height * 0.16f),
        )

        // 眼睛
        drawCircle(
            color = ink,
            radius = min * 0.072f,
            center = Offset(width * 0.34f, height * 0.46f),
        )
        drawCircle(
            color = ink,
            radius = min * 0.072f,
            center = Offset(width * 0.66f, height * 0.46f),
        )

        // 腮红
        drawCircle(
            color = colors.error.copy(alpha = 0.25f),
            radius = min * 0.075f,
            center = Offset(width * 0.2f, height * 0.62f),
        )
        drawCircle(
            color = colors.error.copy(alpha = 0.25f),
            radius = min * 0.075f,
            center = Offset(width * 0.8f, height * 0.62f),
        )

        // 嘴
        drawArc(
            color = ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(width * 0.37f, height * 0.5f),
            size = Size(width * 0.26f, height * 0.26f),
            style = Stroke(width = min * 0.035f, cap = StrokeCap.Round),
        )
    }
}
