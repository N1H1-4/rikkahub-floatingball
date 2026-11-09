package me.rerere.rikkahub.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.ProceduralAvatar

private val BaseSize = 64.dp

const val DESKTOP_PET_MIN_SCALE = 0.6f
const val DESKTOP_PET_MAX_SCALE = 2.0f
const val DESKTOP_PET_MIN_OPACITY = 0.25f

/** 桌宠的外观参数，任意一项变化都会让悬浮窗里的内容重组 */
data class DesktopPetVisuals(
    /** 形象，和助手头像共用同一套模型：图片 / Emoji / 程序化图案 */
    val avatar: Avatar = Avatar.Dummy,
    val sizeScale: Float = 1f,
    val opacity: Float = 1f,
)

/**
 * 桌宠本体。
 *
 * 形象直接复用助手头像那套 [Avatar]：
 * - [Avatar.Image] 用户从相册选的图（gif / webp 动图由全局 ImageLoader 解码）
 * - [Avatar.Emoji] 一个表情符号
 * - [Avatar.Dummy] 按名字生成的颜色图案
 */
@Composable
fun DesktopPetContent(visuals: DesktopPetVisuals, onClick: () -> Unit) {
    val size = BaseSize * visuals.sizeScale.coerceIn(DESKTOP_PET_MIN_SCALE, DESKTOP_PET_MAX_SCALE)
    val opacity = visuals.opacity.coerceIn(DESKTOP_PET_MIN_OPACITY, 1f)
    val colors = MaterialTheme.colorScheme

    // 不用 Surface：它会吃掉触摸事件，拖动交给 FloatingX 处理
    Box(modifier = Modifier.padding(6.dp).alpha(opacity)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.secondaryContainer)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (val avatar = visuals.avatar) {
                is Avatar.Image -> AsyncImage(
                    model = avatar.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                is Avatar.Emoji -> Text(
                    text = avatar.content,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = (size.value * 0.62f).sp,
                    ),
                    lineHeight = 0.8.em,
                    textAlign = TextAlign.Center,
                )

                Avatar.Dummy -> ProceduralAvatar(name = "pet", modifier = Modifier.fillMaxSize())
            }
        }
    }
}
