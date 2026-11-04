package me.rerere.ui.sketch

import androidx.annotation.StringRes
import me.rerere.ui.R

/**
 * 画纸接受哪种输入。
 *
 * 平板上手掌很容易压在屏上，所以默认只认笔：笔落墨，手指和手掌什么都不做。
 */
enum class SketchInputMode(@param:StringRes val label: Int) {
    /** 只有笔落墨。 */
    Stylus(R.string.sketch_dialog_input_stylus),

    /** 手指也能画。 */
    Any(R.string.sketch_dialog_input_any),
}
