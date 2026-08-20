package com.charactym.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

private const val SIZE = 256

private val GuideColor = Color(0xFFC8D3E1)
private val BorderColor = Color(0xFFD9DEE6)

private data class Cell(val x: Int, val y: Int)

/**
 * 256×256 黑白画布组件：
 * - 单指 = 书写（画笔/橡皮由外层状态决定），点按也可落一个点；
 * - 双指 = 缩放/拖动，缩放范围 [铺满画布, 16 倍]；
 * - 辅助线：横纵各 3 根（分成 4×4=16 格）+ 两条对角线，可开关；
 * - resetKey 变化时视图复位（铺满居中）。
 */
@Composable
fun GlyphCanvas(
    image: ImageBitmap,
    revision: State<Int>,
    guidesVisible: Boolean,
    resetKey: Int,
    onStrokeStart: () -> Unit,
    onStrokeSegment: (x1: Int, y1: Int, x2: Int, y2: Int) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitScale by remember { mutableFloatStateOf(1f) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var initialized by remember { mutableStateOf(false) }

    fun resetView() {
        val w = sizePx.width
        val h = sizePx.height
        if (w <= 0 || h <= 0) return
        fitScale = min(w, h) / SIZE.toFloat()
        scale = fitScale
        offset = Offset((w - SIZE * fitScale) / 2f, (h - SIZE * fitScale) / 2f)
    }

    LaunchedEffect(resetKey) {
        if (initialized) resetView()
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                sizePx = size
                if (!initialized) {
                    initialized = true
                    resetView()
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = MODE_DRAW
                    var strokeActive = false
                    var moved = false
                    var lastCell: Cell? = null

                    // 双指变换的基准值
                    var baseCentroid = Offset.Zero
                    var baseSpan = 1f
                    var baseScale = scale
                    var baseOffset = offset

                    fun cellOf(p: Offset): Cell = Cell(
                        ((p.x - offset.x) / scale).toInt().coerceIn(0, SIZE - 1),
                        ((p.y - offset.y) / scale).toInt().coerceIn(0, SIZE - 1),
                    )

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            // 双指：缩放 + 拖动
                            if (mode != MODE_TRANSFORM) {
                                if (strokeActive) {
                                    onStrokeEnd()
                                    strokeActive = false
                                }
                                mode = MODE_TRANSFORM
                                baseCentroid = centroidOf(pressed)
                                baseSpan = spanOf(pressed).coerceAtLeast(1f)
                                baseScale = scale
                                baseOffset = offset
                            } else {
                                val c = centroidOf(pressed)
                                val s = spanOf(pressed).coerceAtLeast(1f)
                                val newScale = (baseScale * s / baseSpan).coerceIn(fitScale, fitScale * 16f)
                                // 保持基准中心点下的内容不动
                                val content = (baseCentroid - baseOffset) / baseScale
                                scale = newScale
                                offset = c - Offset(content.x * newScale, content.y * newScale)
                            }
                        } else if (mode == MODE_DRAW) {
                            // 单指：书写
                            val cell = cellOf(pressed.first().position)
                            if (!strokeActive) {
                                onStrokeStart()
                                strokeActive = true
                            }
                            val last = lastCell ?: cell
                            if (last != cell) {
                                moved = true
                                onStrokeSegment(last.x, last.y, cell.x, cell.y)
                            }
                            lastCell = cell
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }

                    // 手势结束：点按（没有移动）也要落一个点
                    if (strokeActive) {
                        if (!moved && lastCell != null && mode == MODE_DRAW) {
                            val c = lastCell
                            onStrokeSegment(c.x, c.y, c.x, c.y)
                        }
                        onStrokeEnd()
                    }
                }
            },
    ) {
        // 读取版本号：内容变化时触发重绘
        revision.value

        drawRect(Color.White)

        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawImage(
                image = image,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(SIZE, SIZE),
            )
            if (guidesVisible) {
                // 线宽约 1.5 个屏幕像素（内容坐标系下除以缩放倍数）
                val thin = 1.5f / scale
                // 横纵各 3 根辅助线：把画布分成 4×4 = 16 个小格
                for (i in 1..3) {
                    val p = i * (SIZE / 4f)
                    drawLine(GuideColor, Offset(p, 0f), Offset(p, SIZE.toFloat()), strokeWidth = thin)
                    drawLine(GuideColor, Offset(0f, p), Offset(SIZE.toFloat(), p), strokeWidth = thin)
                }
                // 两条对角线
                drawLine(GuideColor, Offset(0f, 0f), Offset(SIZE.toFloat(), SIZE.toFloat()), strokeWidth = thin)
                drawLine(GuideColor, Offset(0f, SIZE.toFloat()), Offset(SIZE.toFloat(), 0f), strokeWidth = thin)
            }
        }

        drawRect(BorderColor, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun centroidOf(pressed: List<androidx.compose.ui.input.pointer.PointerInputChange>): Offset {
    val sum = pressed.fold(Offset.Zero) { acc, c -> acc + c.position }
    return sum / pressed.size.toFloat()
}

private fun spanOf(pressed: List<androidx.compose.ui.input.pointer.PointerInputChange>): Float =
    (pressed.first().position - pressed.last().position).getDistance()

private const val MODE_DRAW = 0
private const val MODE_TRANSFORM = 1
