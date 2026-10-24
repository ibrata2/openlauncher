package com.openlauncher.app.designer.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure editing operations on [DesignProject] / [DesignElement].
 * Every function returns a new immutable value — the editor keeps the old one
 * for undo.
 */
object DesignOps {

    private var idCounter = 0

    fun newId(prefix: String = "el"): String {
        idCounter = (idCounter + 1) % 100_000
        val rnd = (Math.random() * 0xFFFFFF).toInt().toString(16)
        return "${prefix}_${System.currentTimeMillis().toString(36)}_${idCounter}_$rnd"
    }

    // ── Factory ────────────────────────────────────────────────────────────

    fun defaultName(type: ElementType, existing: List<DesignElement>): String {
        val base = when (type) {
            ElementType.TEXT     -> "نص"
            ElementType.RECT     -> "مستطيل"
            ElementType.ELLIPSE  -> "دائرة"
            ElementType.LINE     -> "خط"
            ElementType.IMAGE    -> "صورة"
            ElementType.ICON     -> "أيقونة"
            ElementType.CLOCK    -> "ساعة"
            ElementType.DATE     -> "تاريخ"
            ElementType.SHORTCUT -> "اختصار"
        }
        val n = existing.count { it.type == type } + 1
        return "$base $n"
    }

    /** A new element of [type] with sensible defaults, centred on ([cx], [cy]). */
    fun createElement(
        type: ElementType,
        project: DesignProject,
        cx: Float = project.width / 2f,
        cy: Float = project.height / 2f,
        accent: Int = 0xFFD4AF37.toInt()
    ): DesignElement {
        val (w, h) = when (type) {
            ElementType.TEXT     -> 260f to 60f
            ElementType.RECT     -> 220f to 140f
            ElementType.ELLIPSE  -> 140f to 140f
            ElementType.LINE     -> 300f to 4f
            ElementType.IMAGE    -> 240f to 160f
            ElementType.ICON     -> 72f to 72f
            ElementType.CLOCK    -> 320f to 120f
            ElementType.DATE     -> 320f to 44f
            ElementType.SHORTCUT -> 120f to 120f
        }
        val base = DesignElement(
            id = newId(),
            type = type,
            name = defaultName(type, project.elements),
            x = round(cx - w / 2f),
            y = round(cy - h / 2f),
            width = w,
            height = h
        )
        return when (type) {
            ElementType.TEXT -> base.copy(text = "نص جديد", fontSize = 36f, fillMode = FillMode.NONE)
            ElementType.RECT -> base.copy(fillColor = 0xFF1E1E1E.toInt(), cornerRadius = 18f,
                strokeColor = accent, strokeWidth = 2f)
            ElementType.ELLIPSE -> base.copy(fillColor = accent)
            ElementType.LINE -> base.copy(fillColor = accent)
            ElementType.IMAGE -> base.copy(fillColor = 0xFF333333.toInt(), cornerRadius = 12f)
            ElementType.ICON -> base.copy(iconName = "star", iconColor = accent, fillMode = FillMode.NONE)
            ElementType.CLOCK -> base.copy(fontSize = 96f, bold = true, fillMode = FillMode.NONE, timeFormat = "HH:mm")
            ElementType.DATE -> base.copy(fontSize = 28f, fillMode = FillMode.NONE, textColor = 0xFFBBBBBB.toInt())
            ElementType.SHORTCUT -> base.copy(
                fillColor = 0xFF1E1E1E.toInt(), cornerRadius = 24f, strokeColor = accent, strokeWidth = 2f,
                iconName = "apps", iconColor = accent, text = "تطبيق", fontSize = 18f,
                action = ElementAction.LAUNCH_APP
            )
        }
    }

    // ── List operations ───────────────────────────────────────────────────

    fun add(project: DesignProject, element: DesignElement): DesignProject =
        project.copy(elements = project.elements + element)

    fun update(project: DesignProject, id: String, transform: (DesignElement) -> DesignElement): DesignProject =
        project.copy(elements = project.elements.map { if (it.id == id) transform(it) else it })

    fun replace(project: DesignProject, element: DesignElement): DesignProject =
        update(project, element.id) { element }

    fun remove(project: DesignProject, id: String): DesignProject =
        project.copy(elements = project.elements.filterNot { it.id == id })

    /** Copies [id] directly above itself, offset so it is visibly separate. Returns the new project and the copy. */
    fun duplicate(project: DesignProject, id: String, offset: Float = 16f): Pair<DesignProject, DesignElement>? {
        val idx = project.indexOf(id)
        if (idx < 0) return null
        val src = project.elements[idx]
        val copy = src.copy(
            id = newId(),
            name = src.name + " (نسخة)",
            x = src.x + offset,
            y = src.y + offset,
            locked = false
        )
        val list = project.elements.toMutableList().apply { add(idx + 1, copy) }
        return project.copy(elements = list) to copy
    }

    fun moveLayer(project: DesignProject, id: String, move: LayerMove): DesignProject {
        val idx = project.indexOf(id)
        if (idx < 0) return project
        val target = when (move) {
            LayerMove.FORWARD  -> min(idx + 1, project.elements.lastIndex)
            LayerMove.BACKWARD -> max(idx - 1, 0)
            LayerMove.TO_FRONT -> project.elements.lastIndex
            LayerMove.TO_BACK  -> 0
        }
        return reorder(project, idx, target)
    }

    /** Moves the element at z-index [from] to z-index [to] (both in bottom→top order). */
    fun reorder(project: DesignProject, from: Int, to: Int): DesignProject {
        val list = project.elements
        if (from !in list.indices || to !in list.indices || from == to) return project
        val m = list.toMutableList()
        val item = m.removeAt(from)
        m.add(to, item)
        return project.copy(elements = m)
    }

    // ── Geometry ───────────────────────────────────────────────────────────

    fun rotate(p: Point, degrees: Float, cx: Float = 0f, cy: Float = 0f): Point {
        if (degrees == 0f) return p
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = p.x - cx
        val dy = p.y - cy
        return Point(cx + dx * c - dy * s, cy + dx * s + dy * c)
    }

    /** Converts a canvas point into the element's own (unrotated) frame. */
    fun toLocal(el: DesignElement, px: Float, py: Float): Point =
        rotate(Point(px, py), -el.rotation, el.centerX, el.centerY)

    fun contains(el: DesignElement, px: Float, py: Float, slop: Float = 0f): Boolean {
        val p = toLocal(el, px, py)
        // Thin elements (lines) get a minimum touch thickness
        val extraY = max(0f, (24f - el.height) / 2f)
        val extraX = max(0f, (24f - el.width) / 2f)
        return p.x >= el.x - slop - extraX && p.x <= el.x + el.width + slop + extraX &&
               p.y >= el.y - slop - extraY && p.y <= el.y + el.height + slop + extraY
    }

    /** Topmost visible, unlocked element under the point (locked ones are picked from the layer list). */
    fun hitTest(project: DesignProject, px: Float, py: Float, includeLocked: Boolean = false): DesignElement? =
        project.elements.asReversed().firstOrNull { el ->
            el.visible && (includeLocked || !el.locked) && contains(el, px, py)
        }

    /** Corner points in canvas space, clockwise from top-left. */
    fun corners(el: DesignElement): List<Point> = listOf(
        Point(el.x, el.y), Point(el.x + el.width, el.y),
        Point(el.x + el.width, el.y + el.height), Point(el.x, el.y + el.height)
    ).map { rotate(it, el.rotation, el.centerX, el.centerY) }

    /** Axis-aligned bounds of the (possibly rotated) element. */
    fun bounds(el: DesignElement): Box {
        if (el.rotation % 360f == 0f) return Box(el.x, el.y, el.width, el.height)
        val c = corners(el)
        val minX = c.minOf { it.x }; val maxX = c.maxOf { it.x }
        val minY = c.minOf { it.y }; val maxY = c.maxOf { it.y }
        return Box(minX, minY, maxX - minX, maxY - minY)
    }

    /** Handle positions in the element's local (unrotated) frame. */
    fun handleLocal(el: DesignElement, h: Handle, rotateKnobDistance: Float = 36f): Point = when (h) {
        Handle.TOP_LEFT     -> Point(el.x, el.y)
        Handle.TOP          -> Point(el.centerX, el.y)
        Handle.TOP_RIGHT    -> Point(el.x + el.width, el.y)
        Handle.RIGHT        -> Point(el.x + el.width, el.centerY)
        Handle.BOTTOM_RIGHT -> Point(el.x + el.width, el.y + el.height)
        Handle.BOTTOM       -> Point(el.centerX, el.y + el.height)
        Handle.BOTTOM_LEFT  -> Point(el.x, el.y + el.height)
        Handle.LEFT         -> Point(el.x, el.centerY)
        Handle.ROTATE       -> Point(el.centerX, el.y - rotateKnobDistance)
    }

    fun handlePosition(el: DesignElement, h: Handle, rotateKnobDistance: Float = 36f): Point =
        rotate(handleLocal(el, h, rotateKnobDistance), el.rotation, el.centerX, el.centerY)

    /** Which handle (if any) of [el] is within [radius] of the point. Rotate knob wins ties. */
    fun hitHandle(el: DesignElement, px: Float, py: Float, radius: Float, rotateKnobDistance: Float = 36f): Handle? {
        if (el.locked) return null
        val order = listOf(Handle.ROTATE) + Handle.entries.filter { it != Handle.ROTATE }
        // Tiny elements: edge handles overlap the corners, so only offer corners + rotate
        val small = el.width < radius * 3 || el.height < radius * 3
        return order.filter { !small || it.isCorner || it == Handle.ROTATE }.firstOrNull { h ->
            val p = handlePosition(el, h, rotateKnobDistance)
            val dx = p.x - px; val dy = p.y - py
            sqrt(dx * dx + dy * dy) <= radius
        }
    }

    // ── Move / snap ────────────────────────────────────────────────────────

    data class SnapResult(val x: Float, val y: Float, val guidesX: List<Float>, val guidesY: List<Float>)

    fun snapToGrid(v: Float, grid: Int): Float = if (grid <= 1) round(v) else round(v / grid) * grid

    /**
     * Places [el] so its top-left is at ([x],[y]) and snaps it: first to other elements'
     * edges/centres and the canvas edges/centre (within [threshold]), otherwise to the grid.
     * Returns the snapped top-left plus the guide lines to draw.
     */
    fun snapMove(project: DesignProject, el: DesignElement, x: Float, y: Float, threshold: Float = 6f): SnapResult {
        val moved = el.copy(x = x, y = y)
        val b = bounds(moved)
        var nx = x
        var ny = y
        val gx = mutableListOf<Float>()
        val gy = mutableListOf<Float>()

        var snappedX = false
        var snappedY = false
        if (project.snapToGuides) {
            val targetsX = mutableListOf(0f, project.width / 2f, project.width.toFloat())
            val targetsY = mutableListOf(0f, project.height / 2f, project.height.toFloat())
            project.elements.filter { it.id != el.id && it.visible }.forEach { o ->
                val ob = bounds(o)
                targetsX += listOf(ob.x, ob.cx, ob.right)
                targetsY += listOf(ob.y, ob.cy, ob.bottom)
            }
            val bestX = bestSnap(listOf(b.x, b.cx, b.right), targetsX, threshold)
            if (bestX != null) { nx += bestX.first; gx += bestX.second; snappedX = true }
            val bestY = bestSnap(listOf(b.y, b.cy, b.bottom), targetsY, threshold)
            if (bestY != null) { ny += bestY.first; gy += bestY.second; snappedY = true }
        }
        if (project.snapToGrid) {
            if (!snappedX) nx = snapToGrid(nx, project.gridSize)
            if (!snappedY) ny = snapToGrid(ny, project.gridSize)
        }
        return SnapResult(nx, ny, gx, gy)
    }

    /** Smallest correction (delta, target) bringing any of [edges] onto any of [targets]. */
    private fun bestSnap(edges: List<Float>, targets: List<Float>, threshold: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        for (e in edges) for (t in targets) {
            val d = t - e
            if (abs(d) <= threshold && (best == null || abs(d) < abs(best.first))) best = d to t
        }
        return best
    }

    /** Nudge by keyboard / arrow buttons. */
    fun nudge(project: DesignProject, id: String, dx: Float, dy: Float): DesignProject =
        update(project, id) { if (it.locked) it else it.copy(x = it.x + dx, y = it.y + dy) }

    // ── Resize / rotate ────────────────────────────────────────────────────

    /**
     * Resizes [start] by dragging [handle] by (worldDx, worldDy) canvas pixels.
     * Works for rotated elements: the drag is converted into the element's own frame and
     * the opposite corner/edge stays fixed on screen. [keepAspect] locks the ratio
     * (always applied for corner handles when true).
     */
    fun resize(
        start: DesignElement,
        handle: Handle,
        worldDx: Float,
        worldDy: Float,
        keepAspect: Boolean = false,
        grid: Int = 0
    ): DesignElement {
        if (handle == Handle.ROTATE) return start
        val d = rotate(Point(worldDx, worldDy), -start.rotation)
        var w = start.width
        var h = start.height

        val left = handle == Handle.LEFT || handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT
        val right = handle == Handle.RIGHT || handle == Handle.TOP_RIGHT || handle == Handle.BOTTOM_RIGHT
        val top = handle == Handle.TOP || handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
        val bottom = handle == Handle.BOTTOM || handle == Handle.BOTTOM_LEFT || handle == Handle.BOTTOM_RIGHT

        if (right) w = start.width + d.x
        if (left) w = start.width - d.x
        if (bottom) h = start.height + d.y
        if (top) h = start.height - d.y

        if (grid > 1 && start.rotation % 360f == 0f) {
            if (left || right) w = snapToGrid(w, grid)
            if (top || bottom) h = snapToGrid(h, grid)
        }

        val minW = if (start.type == ElementType.LINE) 1f else MIN_ELEMENT_SIZE
        val minH = if (start.type == ElementType.LINE) 1f else MIN_ELEMENT_SIZE
        w = max(minW, w)
        h = max(minH, h)

        if (keepAspect && start.width > 0f && start.height > 0f) {
            val ratio = start.width / start.height
            if (handle.isCorner) {
                // Follow whichever axis moved proportionally more
                if (w / start.width >= h / start.height) h = w / ratio else w = h * ratio
            } else if (left || right) {
                h = w / ratio
            } else {
                w = h * ratio
            }
            w = max(minW, w); h = max(minH, h)
        }

        // Anchor = the point opposite the dragged handle, expressed relative to the centre
        val ax = when { left -> 0.5f; right -> -0.5f; else -> 0f }
        val ay = when { top -> 0.5f; bottom -> -0.5f; else -> 0f }
        val anchorWorld = rotate(
            Point(start.centerX + ax * start.width, start.centerY + ay * start.height),
            start.rotation, start.centerX, start.centerY
        )
        // New centre so the anchor stays where it was
        val off = rotate(Point(ax * w, ay * h), start.rotation)
        val ncx = anchorWorld.x - off.x
        val ncy = anchorWorld.y - off.y
        return start.copy(x = ncx - w / 2f, y = ncy - h / 2f, width = w, height = h)
    }

    /** Uniform scale around the centre (pinch gesture). */
    fun scale(start: DesignElement, factor: Float): DesignElement {
        val f = factor.coerceIn(0.05f, 20f)
        val minSize = if (start.type == ElementType.LINE) 1f else MIN_ELEMENT_SIZE
        val w = max(minSize, start.width * f)
        val h = max(if (start.type == ElementType.LINE) start.height else minSize, if (start.type == ElementType.LINE) start.height else start.height * f)
        return start.copy(
            x = start.centerX - w / 2f, y = start.centerY - h / 2f, width = w, height = h,
            fontSize = if (start.type in TEXT_TYPES) (start.fontSize * f).coerceIn(4f, 600f) else start.fontSize
        )
    }

    private val TEXT_TYPES = setOf(ElementType.TEXT, ElementType.CLOCK, ElementType.DATE)

    /** Rotation (degrees) that points the rotate knob at the given canvas point. Snaps to 15° steps within [snapWithin]. */
    fun rotationTowards(el: DesignElement, px: Float, py: Float, snapWithin: Float = 4f): Float {
        val a = Math.toDegrees(atan2((py - el.centerY).toDouble(), (px - el.centerX).toDouble())).toFloat() + 90f
        return normalizeAngle(snapAngle(a, snapWithin))
    }

    fun snapAngle(a: Float, within: Float): Float {
        val nearest = round(a / 15f) * 15f
        return if (abs(a - nearest) <= within) nearest else a
    }

    fun normalizeAngle(a: Float): Float {
        var r = a % 360f
        if (r < 0f) r += 360f
        if (r >= 359.95f) r = 0f
        return round(r * 10f) / 10f
    }

    // ── Alignment ─────────────────────────────────────────────────────────

    fun align(project: DesignProject, id: String, alignment: Alignment): DesignProject = update(project, id) { el ->
        if (el.locked) return@update el
        val b = bounds(el)
        // Shift by the difference between the rotated bounds and the target, keeping rotation
        when (alignment) {
            Alignment.LEFT     -> el.copy(x = el.x - b.x)
            Alignment.H_CENTER -> el.copy(x = el.x + (project.width / 2f - b.cx))
            Alignment.RIGHT    -> el.copy(x = el.x + (project.width - b.right))
            Alignment.TOP      -> el.copy(y = el.y - b.y)
            Alignment.V_CENTER -> el.copy(y = el.y + (project.height / 2f - b.cy))
            Alignment.BOTTOM   -> el.copy(y = el.y + (project.height - b.bottom))
        }
    }

    /** Clamps numeric fields coming from text boxes / imported files into safe ranges. */
    fun sanitize(el: DesignElement): DesignElement = el.copy(
        width = if (el.width.isFinite()) el.width.coerceIn(1f, 20_000f) else 100f,
        height = if (el.height.isFinite()) el.height.coerceIn(1f, 20_000f) else 100f,
        x = if (el.x.isFinite()) el.x.coerceIn(-20_000f, 20_000f) else 0f,
        y = if (el.y.isFinite()) el.y.coerceIn(-20_000f, 20_000f) else 0f,
        rotation = if (el.rotation.isFinite()) normalizeAngle(el.rotation) else 0f,
        opacity = if (el.opacity.isFinite()) el.opacity.coerceIn(0f, 1f) else 1f,
        strokeWidth = el.strokeWidth.coerceIn(0f, 200f),
        cornerRadius = el.cornerRadius.coerceIn(0f, 2000f),
        fontSize = el.fontSize.coerceIn(4f, 600f),
        shadowRadius = el.shadowRadius.coerceIn(0f, 100f),
        letterSpacing = el.letterSpacing.coerceIn(-0.5f, 2f)
    )

    fun touch(project: DesignProject, now: Long = System.currentTimeMillis()): DesignProject =
        project.copy(updatedAt = now, createdAt = if (project.createdAt == 0L) now else project.createdAt)
}
