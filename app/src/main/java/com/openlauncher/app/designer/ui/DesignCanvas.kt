package com.openlauncher.app.designer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.openlauncher.app.designer.model.DesignElement
import com.openlauncher.app.designer.model.DesignOps
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.model.Handle
import com.openlauncher.app.designer.render.DesignRenderer
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Zoom/pan of the editor view. zoom = 1 means "fit the whole 1024×600 canvas". */
@Stable
class ViewportState {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var keepAspect by mutableStateOf(false)

    // Layout info refreshed on every frame, used for zoom-around-point maths
    internal var containerSize = Size.Zero
    internal var fitScale = 1f
    internal var designSize = Size(1024f, 600f)

    val scale: Float get() = fitScale * zoom

    fun origin(): Offset = Offset(
        containerSize.width / 2f - designSize.width * scale / 2f + pan.x,
        containerSize.height / 2f - designSize.height * scale / 2f + pan.y
    )

    fun toDesign(screen: Offset): Offset {
        val o = origin()
        return Offset((screen.x - o.x) / scale, (screen.y - o.y) / scale)
    }

    fun toScreen(x: Float, y: Float): Offset {
        val o = origin()
        return Offset(o.x + x * scale, o.y + y * scale)
    }

    /** Zooms by [factor] keeping the design point under [focus] still. */
    fun zoomBy(factor: Float, focus: Offset) {
        val before = toDesign(focus)
        zoom = (zoom * factor).coerceIn(0.25f, 10f)
        val s = scale
        val originAfter = Offset(focus.x - before.x * s, focus.y - before.y * s)
        pan = Offset(
            originAfter.x - (containerSize.width / 2f - designSize.width * s / 2f),
            originAfter.y - (containerSize.height / 2f - designSize.height * s / 2f)
        )
    }

    fun fit() { zoom = 1f; pan = Offset.Zero }

    /** 100% = one design pixel per screen pixel (true size on a 1024×600 panel). */
    fun actualPixels() {
        if (fitScale > 0f) zoom = 1f / fitScale
        pan = Offset.Zero
    }
}

private enum class GestureMode { NONE, PAN, MOVE, RESIZE, ROTATE }

private val SelectionColor = Color(0xFF29B6F6)
private val GuideColor = Color(0xFFFF4081)
private val LockColor = Color(0xFFFFB300)

/**
 * The interactive design surface. One finger: tap selects, drag moves the element
 * (or pans the empty canvas), drag a handle to resize, drag the top knob to rotate.
 * Two fingers: pinch-scale the selected element, otherwise zoom/pan the view.
 * Mouse: click/drag like touch, wheel zooms around the cursor.
 */
@Composable
fun DesignCanvas(
    project: DesignProject,
    selectedId: String?,
    guides: Pair<List<Float>, List<Float>>,
    now: State<Long>,
    assetRevision: Int,
    renderer: DesignRenderer,
    assetDir: File?,
    viewport: ViewportState,
    onSelect: (String?) -> Unit,
    onBeginGesture: () -> Unit,
    onGestureUpdate: (DesignProject, List<Float>, List<Float>) -> Unit,
    onEndGesture: () -> Unit,
    onCanvasTouched: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 20.dp.toPx() }
    val handleDrawPx = with(density) { 6.dp.toPx() }
    val rotateKnobPx = with(density) { 34.dp.toPx() }
    val touchSlop = with(density) { 6.dp.toPx() }

    val currentProject by rememberUpdatedState(project)
    val currentSelected by rememberUpdatedState(selectedId)
    val select by rememberUpdatedState(onSelect)
    val begin by rememberUpdatedState(onBeginGesture)
    val update by rememberUpdatedState(onGestureUpdate)
    val end by rememberUpdatedState(onEndGesture)
    val touched by rememberUpdatedState(onCanvasTouched)

    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        viewport.containerSize = Size(cw, ch)
        viewport.designSize = Size(project.width.toFloat(), project.height.toFloat())
        viewport.fitScale = max(0.01f, min(cw / project.width, ch / project.height) * 0.94f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Mouse wheel zoom
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Scroll) {
                                val c = e.changes.firstOrNull() ?: continue
                                val dy = c.scrollDelta.y
                                if (dy != 0f) {
                                    viewport.zoomBy(if (dy > 0) 0.9f else 1.1f, c.position)
                                    c.consume()
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touched()
                        val p0 = currentProject
                        val scale0 = viewport.scale
                        val d0 = viewport.toDesign(down.position)
                        val sel0 = p0.element(currentSelected)?.takeIf { it.visible }

                        var mode = GestureMode.NONE
                        var target: DesignElement? = null
                        var handle: Handle? = null

                        val hitH = sel0?.let {
                            DesignOps.hitHandle(it, d0.x, d0.y, handleRadiusPx / scale0, rotateKnobPx / scale0)
                        }
                        if (hitH != null) {
                            target = sel0; handle = hitH
                            mode = if (hitH == Handle.ROTATE) GestureMode.ROTATE else GestureMode.RESIZE
                        } else {
                            // Keep dragging the selected element even if something else is drawn above it
                            target = if (sel0 != null && !sel0.locked && DesignOps.contains(sel0, d0.x, d0.y, slop = 4f / scale0)) sel0
                                     else DesignOps.hitTest(p0, d0.x, d0.y)
                            mode = if (target != null) GestureMode.MOVE else GestureMode.PAN
                        }

                        val startPan = viewport.pan
                        var started = false
                        var begun = false
                        var pinch = false
                        var pinchStart: DesignElement? = null
                        var pinchZoom = 1f

                        fun ensureBegun() {
                            if (!begun) { begin(); begun = true }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val t = target
                                if (t != null && !t.locked && currentSelected == t.id && mode != GestureMode.PAN) {
                                    // Pinch the selected element
                                    ensureBegun()
                                    if (pinchStart == null) pinchStart = currentProject.element(t.id)
                                    pinchZoom *= zoomChange
                                    pinchStart?.let { ps ->
                                        update(DesignOps.replace(currentProject, DesignOps.scale(ps, pinchZoom)), emptyList(), emptyList())
                                    }
                                } else {
                                    if (centroid != Offset.Unspecified) viewport.zoomBy(zoomChange, centroid)
                                    viewport.pan += panChange
                                }
                                pinch = true
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (pinch) continue // ignore the leftover finger after a pinch

                            val change = pressed.first()
                            val total = change.position - down.position
                            if (!started && total.getDistance() > touchSlop) started = true
                            if (!started) continue
                            val dx = total.x / scale0
                            val dy = total.y / scale0

                            when (mode) {
                                GestureMode.PAN -> viewport.pan = startPan + total
                                GestureMode.MOVE -> {
                                    val t = target ?: break
                                    if (currentSelected != t.id) select(t.id)
                                    if (!t.locked) {
                                        ensureBegun()
                                        val s = DesignOps.snapMove(currentProject, t, t.x + dx, t.y + dy, threshold = 6f / scale0.coerceAtMost(1f))
                                        update(DesignOps.replace(currentProject, t.copy(x = s.x, y = s.y)), s.guidesX, s.guidesY)
                                    }
                                }
                                GestureMode.RESIZE -> {
                                    val t = target ?: break
                                    ensureBegun()
                                    val grid = if (currentProject.snapToGrid) currentProject.gridSize else 0
                                    val r = DesignOps.resize(t, handle!!, dx, dy, keepAspect = viewport.keepAspect, grid = grid)
                                    update(DesignOps.replace(currentProject, r), emptyList(), emptyList())
                                }
                                GestureMode.ROTATE -> {
                                    val t = target ?: break
                                    ensureBegun()
                                    val cur = viewport.toDesign(change.position)
                                    val rot = DesignOps.rotationTowards(t, cur.x, cur.y)
                                    update(DesignOps.replace(currentProject, t.copy(rotation = rot)), emptyList(), emptyList())
                                }
                                GestureMode.NONE -> Unit
                            }
                            change.consume()
                        }

                        if (begun) end()
                        if (!started && !pinch) {
                            // A tap: select what is under the finger (or clear the selection)
                            select(if (mode == GestureMode.PAN) null else target?.id)
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION") assetRevision // redraw when images change
            val s = viewport.scale
            val o = viewport.origin()
            val pw = project.width.toFloat()
            val ph = project.height.toFloat()

            // Backdrop + soft frame shadow
            drawRect(Color(0xFF0B0B0B))
            drawRect(Color(0x66000000), topLeft = o + Offset(6f, 8f), size = Size(pw * s, ph * s))

            // The design itself, rendered by the same engine as PNG export / home screen
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val save = nc.save()
                nc.translate(o.x, o.y)
                nc.scale(s, s)
                renderer.draw(nc, project, assetDir, now.value) // read in draw phase: ticks only redraw
                nc.restoreToCount(save)
            }

            if (project.showGrid) drawGrid(project, o, s)

            // Canvas border
            drawRect(Color(0x55FFFFFF), topLeft = o, size = Size(pw * s, ph * s), style = Stroke(1f))

            // Smart guides
            guides.first.forEach { gx ->
                val x = o.x + gx * s
                drawLine(GuideColor, Offset(x, o.y), Offset(x, o.y + ph * s), strokeWidth = 1.5f)
            }
            guides.second.forEach { gy ->
                val y = o.y + gy * s
                drawLine(GuideColor, Offset(o.x, y), Offset(o.x + pw * s, y), strokeWidth = 1.5f)
            }

            project.element(selectedId)?.let { el ->
                drawSelection(el, viewport, handleDrawPx, rotateKnobPx / s)
            }
        }
    }
}

private fun DrawScope.drawGrid(project: DesignProject, o: Offset, s: Float) {
    var step = project.gridSize.toFloat().coerceAtLeast(1f)
    while (step * s < 8f) step *= 2f   // keep lines at least 8px apart on screen
    val color = Color(0x22FFFFFF)
    val strong = Color(0x40FFFFFF)
    var x = 0f
    var i = 0
    while (x <= project.width) {
        drawLine(if (i % 8 == 0) strong else color, Offset(o.x + x * s, o.y), Offset(o.x + x * s, o.y + project.height * s), 1f)
        x += step; i++
    }
    var y = 0f
    i = 0
    while (y <= project.height) {
        drawLine(if (i % 8 == 0) strong else color, Offset(o.x, o.y + y * s), Offset(o.x + project.width * s, o.y + y * s), 1f)
        y += step; i++
    }
    // Centre cross
    drawLine(Color(0x55FF4081), Offset(o.x + project.width / 2f * s, o.y), Offset(o.x + project.width / 2f * s, o.y + project.height * s), 1f)
    drawLine(Color(0x55FF4081), Offset(o.x, o.y + project.height / 2f * s), Offset(o.x + project.width * s, o.y + project.height / 2f * s), 1f)
}

private fun DrawScope.drawSelection(el: DesignElement, vp: ViewportState, handleSize: Float, knobDistanceDesign: Float) {
    val corners = DesignOps.corners(el).map { vp.toScreen(it.x, it.y) }
    val outline = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    val color = if (el.locked) LockColor else SelectionColor
    drawPath(outline, Color.Black.copy(alpha = 0.5f), style = Stroke(3f))
    drawPath(
        outline, color,
        style = Stroke(1.5f, pathEffect = if (el.locked) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
    )
    if (el.locked || !el.visible) return

    // Rotate knob
    val topMid = DesignOps.handlePosition(el, Handle.TOP, knobDistanceDesign).let { vp.toScreen(it.x, it.y) }
    val knob = DesignOps.handlePosition(el, Handle.ROTATE, knobDistanceDesign).let { vp.toScreen(it.x, it.y) }
    drawLine(color, topMid, knob, 1.5f)
    drawCircle(Color.White, handleSize * 1.15f, knob)
    drawCircle(color, handleSize * 1.15f, knob, style = Stroke(2f))
    drawCircle(color, handleSize * 0.45f, knob)

    val small = el.width * vp.scale < handleSize * 6 || el.height * vp.scale < handleSize * 6
    Handle.entries.filter { it != Handle.ROTATE && (!small || it.isCorner) }.forEach { h ->
        val p = DesignOps.handlePosition(el, h, knobDistanceDesign).let { vp.toScreen(it.x, it.y) }
        drawCircle(Color.White, handleSize, p)
        drawCircle(color, handleSize, p, style = Stroke(2f))
    }
}
