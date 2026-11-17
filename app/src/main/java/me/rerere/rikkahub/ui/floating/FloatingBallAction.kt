package me.rerere.rikkahub.ui.floating

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.rikkahub.R
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChatAdd01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Crop
import me.rerere.hugeicons.stroke.ScissorRectangle
import me.rerere.hugeicons.stroke.Settings01
import me.rerere.hugeicons.stroke.Chart01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.UserSwitch
import me.rerere.hugeicons.stroke.Rocket01

/**
 * 悬浮球能触发的动作。保存到设置里时用 [id] 字符串，避免以后枚举改名导致旧配置失效。
 */
enum class FloatingBallAction(
    val id: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    /** 仅用于"点击球"：有在跟的会话就展开，否则选区截屏 */
    Auto("auto", R.string.floating_action_auto, HugeIcons.Rocket01),
    NewChat("new_chat", R.string.floating_action_new_chat, HugeIcons.ChatAdd01),
    History("history", R.string.floating_action_history, HugeIcons.Clock01),
    OpenApp("open_app", R.string.floating_action_open_app, HugeIcons.Rocket01),
    FullScreenCapture("full_capture", R.string.floating_action_full_capture, HugeIcons.ScissorRectangle),
    RegionCapture("region_capture", R.string.floating_action_region_capture, HugeIcons.Crop),
    CloseBall("close", R.string.floating_action_close, HugeIcons.Cancel01),
    Settings("settings", R.string.floating_action_settings, HugeIcons.Settings01),
    Stats("stats", R.string.floating_action_stats, HugeIcons.Chart01),
    SwitchAssistant("switch_assistant", R.string.floating_action_switch_assistant, HugeIcons.UserSwitch),
    ;

    companion object {
        fun fromId(id: String): FloatingBallAction? = entries.firstOrNull { it.id == id }

        /** 外围环形菜单可选的项（"自动判断"只用于点击） */
        val ringCandidates: List<FloatingBallAction>
            get() = entries.filter { it != Auto }

        const val RingLimit = 5
    }
}
