package me.rerere.ui.sketch

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Brush
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.CursorRectangleSelection01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Redo
import me.rerere.hugeicons.stroke.Save
import me.rerere.hugeicons.stroke.Send
import me.rerere.hugeicons.stroke.TabletPen
import me.rerere.hugeicons.stroke.TouchInteraction01
import me.rerere.hugeicons.stroke.Type
import me.rerere.hugeicons.stroke.Undo
import me.rerere.ui.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val HandleTouchRadius = 24.dp
private const val MinSelectionPx = 48f

private enum class SelectionTarget { TopLeft, TopRight, BottomLeft, BottomRight, Move, New }

/**
 * 截图编辑器：框选和画笔标注合在同一个界面里，底部是一条浮动工具栏。
 *
 * 交互分两种模式：
 * - 框选（默认）：拖动调整裁剪框，四角可拉、框内可整体移动、空白处重新拉框；
 * - 标注：画布接管触摸，可以用笔画在截图上（框选范围只是导出范围，不影响落笔位置）。
 *
 * 导出时才把「整张截图 + 标注」渲染出来，再按框选范围裁切。
 */
@Composable
fun ScreenshotEditor(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onCopy: (Bitmap) -> Unit,
    onSave: (Bitmap) -> Unit,
    onSendNewChat: (Bitmap) -> Unit,
    onSendToChat: (Bitmap) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val state = remember {
        SketchState().also {
            // 画布一旦平移缩放，屏幕上的框选范围就和导出的坐标对不上，这里锁死
            it.transformEnabled = false
            // 截图标注默认用红色，比黑色显眼
            it.usePreset(DefaultAnnotationColor)
        }
    }
    LaunchedEffect(image) { state.useBackground(image) }

    var selection by remember(bitmap) { mutableStateOf<Rect?>(null) }
    var annotating by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showRatios by remember { mutableStateOf(false) }
    var showSizeDialog by remember { mutableStateOf(false) }
    val handleRadius = with(LocalDensity.current) { HandleTouchRadius.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val scale = min(
            containerWidth / bitmap.width.toFloat(),
            containerHeight / bitmap.height.toFloat(),
        )
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val imageBounds = remember(scale, bitmap) {
            Rect(
                left = (containerWidth - drawWidth) / 2f,
                top = (containerHeight - drawHeight) / 2f,
                right = (containerWidth + drawWidth) / 2f,
                bottom = (containerHeight + drawHeight) / 2f,
            )
        }

        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // 标注层：只在标注模式下接触摸，框选模式下只负责把已画的笔迹显示出来
        SketchCanvas(
            state = state,
            modifier = Modifier.fillMaxSize(),
            interactive = annotating,
        )

        SelectionLayer(
            selection = selection,
            bounds = imageBounds,
            handleRadius = handleRadius,
            interactive = !annotating,
            onChange = { selection = it },
        )

        // 顶部胶囊：还没有选区时点哪都是"选画幅"；有选区时点数字改尺寸，点三角换比例
        val currentSelection = selection
        Surface(
            onClick = {
                if (currentSelection == null) showRatios = true else showSizeDialog = true
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (currentSelection == null) {
                        stringResource(R.string.screenshot_editor_hint)
                    } else {
                        val width = (currentSelection.width / scale).roundToInt()
                        val height = (currentSelection.height / scale).roundToInt()
                        stringResource(R.string.screenshot_editor_size, width, height)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 2.dp),
                )
                Box {
                    IconButton(onClick = { showRatios = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            HugeIcons.ArrowDown01,
                            contentDescription = stringResource(R.string.screenshot_editor_ratio),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = showRatios, onDismissRequest = { showRatios = false }) {
                        AspectRatioOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label
                                            ?: stringResource(R.string.screenshot_editor_ratio_free)
                                    )
                                },
                                onClick = {
                                    showRatios = false
                                    selection = fitRatio(imageBounds, currentSelection, option.ratio)
                                },
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.pickingColor) {
                SketchColorPicker(state = state, modifier = Modifier.widthIn(max = 420.dp))
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        CompactToggle(
                            selected = !annotating,
                            icon = HugeIcons.CursorRectangleSelection01,
                            label = stringResource(R.string.sketch_dialog_select),
                            onClick = { annotating = false },
                        )
                        CompactToggle(
                            selected = annotating && state.tool == SketchTool.Brush,
                            icon = HugeIcons.Brush,
                            label = stringResource(R.string.sketch_dialog_brush_pen),
                            onClick = {
                                annotating = true
                                // 画笔工具的 setter 是私有的，关掉橡皮就是回到画笔
                                state.useEraser(false)
                            },
                        )
                        CompactToggle(
                            selected = state.erasing,
                            icon = HugeIcons.Eraser,
                            label = stringResource(R.string.sketch_dialog_eraser),
                            onClick = {
                                annotating = true
                                state.useEraser(!state.erasing)
                            },
                        )
                        CompactToggle(
                            selected = state.tool == SketchTool.Text,
                            icon = HugeIcons.Type,
                            label = stringResource(R.string.sketch_dialog_text),
                            onClick = {
                                annotating = true
                                state.useText(state.tool != SketchTool.Text)
                            },
                        )
                        CompactToggle(
                            selected = showColors,
                            displayColor = state.color,
                            label = stringResource(R.string.sketch_dialog_custom_color),
                            onClick = { showColors = !showColors },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            SketchDefaults.BrushWidths.forEach { width ->
                                WidthDot(
                                    width = width,
                                    selected = state.width == width,
                                    onClick = {
                                        state.width = width
                                        annotating = true
                                    },
                                )
                            }
                        }
                        CompactAction(
                            icon = HugeIcons.Undo,
                            label = stringResource(R.string.sketch_dialog_undo),
                            enabled = state.canUndo,
                            onClick = state::undo,
                        )
                        CompactAction(
                            icon = HugeIcons.Redo,
                            label = stringResource(R.string.sketch_dialog_redo),
                            enabled = state.canRedo,
                            onClick = state::redo,
                        )
                        CompactAction(
                            icon = HugeIcons.Delete02,
                            label = stringResource(R.string.sketch_dialog_clear),
                            enabled = !state.isBlank,
                            onClick = state::clear,
                        )
                    }

                    if (showColors) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            SketchDefaults.BrushColors.forEach { color ->
                                ColorDot(
                                    color = color,
                                    selected = !state.usingCustomColor && state.color == color,
                                    onClick = {
                                        state.usePreset(color)
                                        annotating = true
                                        showColors = false
                                    },
                                )
                            }
                            ColorDot(
                                color = state.customColor ?: SketchDefaults.CustomColor,
                                selected = state.usingCustomColor,
                                custom = true,
                                onClick = {
                                    val custom = state.customColor
                                    if (custom == null) state.useCustom(SketchDefaults.CustomColor) else state.useCustom(custom)
                                    state.pickingColor = true
                                    annotating = true
                                    showColors = false
                                },
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        CompactAction(
                            icon = HugeIcons.Cancel01,
                            label = stringResource(R.string.screenshot_editor_cancel),
                            onClick = onCancel,
                        )
                        CompactAction(
                            icon = if (state.inputMode == SketchInputMode.Stylus) {
                                HugeIcons.TabletPen
                            } else {
                                HugeIcons.TouchInteraction01
                            },
                            label = stringResource(state.inputMode.label),
                            onClick = { state.toggleInputMode() },
                        )
                        Spacer(Modifier.width(12.dp))
                        val enabled = selection != null
                        CompactAction(
                            icon = HugeIcons.Copy01,
                            label = stringResource(R.string.screenshot_editor_copy),
                            enabled = enabled,
                            onClick = { exported(bitmap, state, selection, imageBounds, scale)?.let(onCopy) },
                        )
                        CompactAction(
                            icon = HugeIcons.Save,
                            label = stringResource(R.string.screenshot_editor_save),
                            enabled = enabled,
                            onClick = { exported(bitmap, state, selection, imageBounds, scale)?.let(onSave) },
                        )
                        CompactAction(
                            icon = HugeIcons.Send,
                            label = stringResource(R.string.screenshot_editor_send_new_chat),
                            enabled = enabled,
                            onClick = { exported(bitmap, state, selection, imageBounds, scale)?.let(onSendNewChat) },
                        )
                        CompactAction(
                            icon = HugeIcons.Message01,
                            label = stringResource(R.string.screenshot_editor_send_to_chat),
                            enabled = enabled,
                            onClick = { exported(bitmap, state, selection, imageBounds, scale)?.let(onSendToChat) },
                        )
                    }
                }
            }
        }

        // 点顶部数字：直接输入想要的宽高（按图片像素算）
        if (showSizeDialog && currentSelection != null) {
            SelectionSizeDialog(
                widthPx = (currentSelection.width / scale).roundToInt(),
                heightPx = (currentSelection.height / scale).roundToInt(),
                onDismiss = { showSizeDialog = false },
                onConfirm = { widthPx, heightPx ->
                    showSizeDialog = false
                    val width = (widthPx * scale).coerceIn(MinSelectionPx, imageBounds.width)
                    val height = (heightPx * scale).coerceIn(MinSelectionPx, imageBounds.height)
                    val left = currentSelection.left.coerceIn(imageBounds.left, imageBounds.right - width)
                    val top = currentSelection.top.coerceIn(imageBounds.top, imageBounds.bottom - height)
                    selection = Rect(left, top, left + width, top + height)
                },
            )
        }
    }
}

/** 整张截图（含标注）渲染出来后按框选范围裁切 */
private fun exported(
    source: Bitmap,
    state: SketchState,
    selection: Rect?,
    imageBounds: Rect,
    scale: Float,
): Bitmap? {
    val current = selection ?: return null
    if (scale <= 0f) return null
    val rendered = runCatching { state.toResult().bitmap }.getOrNull() ?: return null
    // 渲染出来的尺寸就是原截图尺寸，显示坐标到图片坐标用同一个比例
    val ratio = rendered.width / source.width.toFloat()
    val left = ((current.left - imageBounds.left) / scale * ratio).roundToInt().coerceIn(0, rendered.width - 1)
    val top = ((current.top - imageBounds.top) / scale * ratio).roundToInt().coerceIn(0, rendered.height - 1)
    val width = (current.width / scale * ratio).roundToInt().coerceIn(1, rendered.width - left)
    val height = (current.height / scale * ratio).roundToInt().coerceIn(1, rendered.height - top)
    return runCatching { Bitmap.createBitmap(rendered, left, top, width, height) }.getOrNull()
}

private val DefaultAnnotationColor = Color(0xFFE53935)

// label 为 null 表示"自由"，用截图本身的宽高比
private data class AspectRatioOption(val label: String?, val ratio: Float?)

private val AspectRatioOptions = listOf(
    AspectRatioOption(null, null),
    AspectRatioOption("1:1", 1f),
    AspectRatioOption("4:3", 4f / 3f),
    AspectRatioOption("3:2", 3f / 2f),
    AspectRatioOption("16:9", 16f / 9f),
    AspectRatioOption("3:4", 3f / 4f),
    AspectRatioOption("9:16", 9f / 16f),
)

/** 把当前选区（没有就按可用区域的 80% 起）调整成指定比例，并保持在可用范围内 */
private fun fitRatio(bounds: Rect, current: Rect?, ratio: Float?): Rect? {
    if (ratio == null) return current
    val center = current?.center ?: bounds.center
    var width = current?.width ?: (bounds.width * 0.8f)
    var height = width / ratio
    if (height > bounds.height) {
        height = bounds.height
        width = height * ratio
    }
    if (width > bounds.width) {
        width = bounds.width
        height = width / ratio
    }
    val left = (center.x - width / 2f).coerceIn(bounds.left, bounds.right - width)
    val top = (center.y - height / 2f).coerceIn(bounds.top, bounds.bottom - height)
    return Rect(left, top, left + width, top + height)
}

@Composable
private fun SelectionSizeDialog(
    widthPx: Int,
    heightPx: Int,
    onDismiss: () -> Unit,
    onConfirm: (width: Int, height: Int) -> Unit,
) {
    var widthText by remember { mutableStateOf(widthPx.toString()) }
    var heightText by remember { mutableStateOf(heightPx.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screenshot_editor_size_title)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.screenshot_editor_width)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.screenshot_editor_height)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val width = widthText.toIntOrNull() ?: return@TextButton
                    val height = heightText.toIntOrNull() ?: return@TextButton
                    if (width > 0 && height > 0) onConfirm(width, height)
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SelectionLayer(
    selection: Rect?,
    bounds: Rect,
    handleRadius: Float,
    interactive: Boolean,
    onChange: (Rect?) -> Unit,
) {
    var target by remember { mutableStateOf(SelectionTarget.New) }
    var newAnchor by remember { mutableStateOf(Offset.Zero) }
    var newDelta by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier.pointerInput(bounds, handleRadius) {
                        awaitEachGesture {
                            // 不等 touch slop：按下就开始，框选要跟手，也不能被画布的触摸事件带偏
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPosition = down.position
                            val current = selection
                            val mode = if (current == null) {
                                SelectionTarget.New
                            } else {
                                selectionTargetAt(startPosition, current, handleRadius)
                            }
                            target = mode
                            if (mode == SelectionTarget.New) {
                                newAnchor = startPosition
                                newDelta = Offset.Zero
                                onChange(Rect(startPosition, startPosition))
                            }
                            down.consume()

                            var moved = false
                            while (true) {
                                val change = awaitPointerEvent()
                                    .changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dragAmount = change.positionChange()
                                if (dragAmount != Offset.Zero) moved = true
                                change.consume()

                                val base = selection
                                val next = when (mode) {
                                    SelectionTarget.New -> {
                                        newDelta += dragAmount
                                        Rect(newAnchor, newAnchor + newDelta).normalized()
                                    }

                                    SelectionTarget.Move -> base?.translate(dragAmount)
                                    SelectionTarget.TopLeft -> base?.copy(
                                        left = min(base.left + dragAmount.x, base.right - MinSelectionPx),
                                        top = min(base.top + dragAmount.y, base.bottom - MinSelectionPx),
                                    )

                                    SelectionTarget.TopRight -> base?.copy(
                                        right = max(base.right + dragAmount.x, base.left + MinSelectionPx),
                                        top = min(base.top + dragAmount.y, base.bottom - MinSelectionPx),
                                    )

                                    SelectionTarget.BottomLeft -> base?.copy(
                                        left = min(base.left + dragAmount.x, base.right - MinSelectionPx),
                                        bottom = max(base.bottom + dragAmount.y, base.top + MinSelectionPx),
                                    )

                                    SelectionTarget.BottomRight -> base?.copy(
                                        right = max(base.right + dragAmount.x, base.left + MinSelectionPx),
                                        bottom = max(base.bottom + dragAmount.y, base.top + MinSelectionPx),
                                    )
                                }?.clampTo(bounds)
                                onChange(next)
                            }

                            // 误触级别的框选直接丢掉，免得留下一个几十像素的选区
                            val final = selection
                            if (mode == SelectionTarget.New && !moved) {
                                onChange(null)
                            } else if (final != null &&
                                (final.width < MinSelectionPx || final.height < MinSelectionPx)
                            ) {
                                onChange(null)
                            }
                        }
                    }
                }
            ),
    ) {
        val current = selection
        if (current == null) {
            drawRect(Color.Black.copy(alpha = 0.35f))
            return@Canvas
        }
        val scrim = Color.Black.copy(alpha = 0.5f)
        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, current.top))
        drawRect(scrim, topLeft = Offset(0f, current.bottom), size = Size(size.width, size.height - current.bottom))
        drawRect(scrim, topLeft = Offset(0f, current.top), size = Size(current.left, current.height))
        drawRect(scrim, topLeft = Offset(current.right, current.top), size = Size(size.width - current.right, current.height))
        drawRect(
            color = Color.White,
            topLeft = current.topLeft,
            size = current.size,
            style = Stroke(width = 2.dp.toPx()),
        )
        if (interactive) {
            val handle = 5.dp.toPx()
            listOf(current.topLeft, current.topRight, current.bottomLeft, current.bottomRight).forEach { corner ->
                drawCircle(Color.White, radius = handle, center = corner)
            }
        }
    }
}

@Composable
private fun CompactToggle(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(40.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

// 颜色按钮：直接显示当前颜色，点开是预设色板
@Composable
private fun CompactToggle(
    selected: Boolean,
    displayColor: Color,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(displayColor),
        )
    }
}

@Composable
private fun CompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun WidthDot(width: Dp, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    custom: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .then(
                if (custom) {
                    Modifier
                        .background(androidx.compose.ui.graphics.Brush.sweepGradient(SketchDefaults.HueColors), CircleShape)
                        .padding(3.dp)
                } else {
                    Modifier
                }
            )
            .background(color, CircleShape),
    )
}

private fun selectionTargetAt(position: Offset, selection: Rect, radius: Float): SelectionTarget = when {
    (position - selection.topLeft).getDistance() <= radius -> SelectionTarget.TopLeft
    (position - selection.topRight).getDistance() <= radius -> SelectionTarget.TopRight
    (position - selection.bottomLeft).getDistance() <= radius -> SelectionTarget.BottomLeft
    (position - selection.bottomRight).getDistance() <= radius -> SelectionTarget.BottomRight
    selection.contains(position) -> SelectionTarget.Move
    else -> SelectionTarget.New
}

private fun Rect.normalized(): Rect = Rect(
    left = min(left, right),
    top = min(top, bottom),
    right = max(left, right),
    bottom = max(top, bottom),
)

private fun Rect.clampTo(bounds: Rect): Rect = Rect(
    left = left.coerceIn(bounds.left, bounds.right),
    top = top.coerceIn(bounds.top, bounds.bottom),
    right = right.coerceIn(bounds.left, bounds.right),
    bottom = bottom.coerceIn(bounds.top, bounds.bottom),
)
