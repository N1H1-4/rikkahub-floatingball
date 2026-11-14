package me.rerere.rikkahub.ui.floating

import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChatAdd01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Crop
import me.rerere.hugeicons.stroke.Fullscreen
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
    val label: String,
    val icon: ImageVector,
) {
    /** 仅用于"点击球"：有在跟的会话就展开，否则选区截屏 */
    Auto("auto", "自动判断", HugeIcons.Rocket01),
    NewChat("new_chat", "悬浮窗新对话", HugeIcons.ChatAdd01),
    History("history", "历史会话", HugeIcons.Clock01),
    OpenApp("open_app", "打开 RikkaHub", HugeIcons.Rocket01),
    FullScreenCapture("full_capture", "截全屏并提问", HugeIcons.Fullscreen),
    RegionCapture("region_capture", "选区截图", HugeIcons.Crop),
    CloseBall("close", "关闭悬浮球", HugeIcons.Cancel01),
    Settings("settings", "打开设置", HugeIcons.Settings01),
    Stats("stats", "统计页面", HugeIcons.Chart01),
    SwitchAssistant("switch_assistant", "切换助手", HugeIcons.UserSwitch),
    ;

    companion object {
        fun fromId(id: String): FloatingBallAction? = entries.firstOrNull { it.id == id }

        /** 外围环形菜单可选的项（"自动判断"只用于点击） */
        val ringCandidates: List<FloatingBallAction>
            get() = entries.filter { it != Auto }

        const val RingLimit = 5
    }
}
