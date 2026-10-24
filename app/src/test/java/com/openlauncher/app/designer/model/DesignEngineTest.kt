package com.openlauncher.app.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DesignEngineTest {

    private val eps = 0.01f

    private fun project() = DesignProject(id = "p", name = "test", snapToGrid = false, snapToGuides = false)

    private fun rect(id: String, x: Float, y: Float, w: Float, h: Float, rot: Float = 0f) =
        DesignElement(id = id, type = ElementType.RECT, name = id, x = x, y = y, width = w, height = h, rotation = rot)

    // ── Factory & layers ──────────────────────────────────────────────────

    @Test fun createElementIsCentredAndNamed() {
        val p = project()
        for (t in ElementType.entries) {
            val e = DesignOps.createElement(t, p)
            assertEquals(512f, e.centerX, 1f)
            assertEquals(300f, e.centerY, 1f)
            assertTrue(e.name.isNotBlank())
            assertTrue(e.width >= 1f && e.height >= 1f)
        }
    }

    @Test fun idsAreUnique() {
        val ids = (1..2000).map { DesignOps.newId() }.toSet()
        assertEquals(2000, ids.size)
    }

    @Test fun addDuplicateRemove() {
        var p = project()
        p = DesignOps.add(p, rect("a", 0f, 0f, 10f, 10f))
        p = DesignOps.add(p, rect("b", 0f, 0f, 10f, 10f))
        val (p2, copy) = DesignOps.duplicate(p, "a")!!
        assertEquals(listOf("a", copy.id, "b"), p2.elements.map { it.id })
        assertEquals(16f, copy.x, eps)
        val p3 = DesignOps.remove(p2, "a")
        assertEquals(listOf(copy.id, "b"), p3.elements.map { it.id })
        assertNull(DesignOps.duplicate(p3, "missing"))
    }

    @Test fun layerOrdering() {
        var p = project()
        listOf("a", "b", "c", "d").forEach { p = DesignOps.add(p, rect(it, 0f, 0f, 10f, 10f)) }
        assertEquals("bacd", DesignOps.moveLayer(p, "a", LayerMove.FORWARD).elements.joinToString("") { it.id })
        assertEquals("bcda", DesignOps.moveLayer(p, "a", LayerMove.TO_FRONT).elements.joinToString("") { it.id })
        assertEquals("dabc", DesignOps.moveLayer(p, "d", LayerMove.TO_BACK).elements.joinToString("") { it.id })
        assertEquals("abdc", DesignOps.moveLayer(p, "d", LayerMove.BACKWARD).elements.joinToString("") { it.id })
        // Out of range / no-op cases leave the list alone
        assertEquals("abcd", DesignOps.moveLayer(p, "a", LayerMove.BACKWARD).elements.joinToString("") { it.id })
        assertEquals("abcd", DesignOps.reorder(p, 0, 9).elements.joinToString("") { it.id })
        assertEquals("cabd", DesignOps.reorder(p, 2, 0).elements.joinToString("") { it.id })
    }

    // ── Hit testing ───────────────────────────────────────────────────────

    @Test fun hitTestPicksTopmostVisibleUnlocked() {
        var p = project()
        p = DesignOps.add(p, rect("bottom", 0f, 0f, 100f, 100f))
        p = DesignOps.add(p, rect("top", 50f, 50f, 100f, 100f))
        assertEquals("top", DesignOps.hitTest(p, 60f, 60f)?.id)
        assertEquals("bottom", DesignOps.hitTest(p, 10f, 10f)?.id)
        assertNull(DesignOps.hitTest(p, 500f, 500f))
        val hidden = DesignOps.update(p, "top") { it.copy(visible = false) }
        assertEquals("bottom", DesignOps.hitTest(hidden, 60f, 60f)?.id)
        val locked = DesignOps.update(p, "top") { it.copy(locked = true) }
        assertEquals("bottom", DesignOps.hitTest(locked, 60f, 60f)?.id)
        assertEquals("top", DesignOps.hitTest(locked, 60f, 60f, includeLocked = true)?.id)
    }

    @Test fun hitTestRespectsRotation() {
        // 200×20 bar rotated 90° around its centre (100,10) becomes a vertical bar x∈[90,110], y∈[-90,110]
        val p = DesignOps.add(project(), rect("bar", 0f, 0f, 200f, 20f, rot = 90f))
        assertNotNull(DesignOps.hitTest(p, 100f, -80f))
        assertNull(DesignOps.hitTest(p, 180f, 10f))
    }

    @Test fun thinLinesHaveTouchSlop() {
        val line = DesignElement(id = "l", type = ElementType.LINE, name = "l", x = 0f, y = 100f, width = 300f, height = 2f)
        assertTrue(DesignOps.contains(line, 150f, 109f))
        assertFalse(DesignOps.contains(line, 150f, 130f))
    }

    @Test fun boundsOfRotatedElement() {
        val b = DesignOps.bounds(rect("r", 0f, 0f, 200f, 100f, rot = 90f))
        assertEquals(50f, b.x, eps); assertEquals(-50f, b.y, eps)
        assertEquals(100f, b.w, eps); assertEquals(200f, b.h, eps)
    }

    @Test fun handleHit() {
        val e = rect("r", 100f, 100f, 200f, 100f)
        assertEquals(Handle.BOTTOM_RIGHT, DesignOps.hitHandle(e, 302f, 198f, 12f))
        assertEquals(Handle.TOP, DesignOps.hitHandle(e, 200f, 100f, 12f))
        assertEquals(Handle.ROTATE, DesignOps.hitHandle(e, 200f, 64f, 12f))
        assertNull(DesignOps.hitHandle(e, 200f, 150f, 12f))
        assertNull(DesignOps.hitHandle(e.copy(locked = true), 302f, 198f, 12f))
    }

    // ── Resize ────────────────────────────────────────────────────────────

    @Test fun resizeCornersAndEdgesUnrotated() {
        val e = rect("r", 100f, 100f, 200f, 100f)
        val br = DesignOps.resize(e, Handle.BOTTOM_RIGHT, 50f, 20f)
        assertEquals(Box(100f, 100f, 250f, 120f), Box(br.x, br.y, br.width, br.height))
        val tl = DesignOps.resize(e, Handle.TOP_LEFT, 50f, 20f)
        assertEquals(150f, tl.x, eps); assertEquals(120f, tl.y, eps)
        assertEquals(150f, tl.width, eps); assertEquals(80f, tl.height, eps)
        val l = DesignOps.resize(e, Handle.LEFT, -30f, 999f)   // vertical motion ignored on a side handle
        assertEquals(70f, l.x, eps); assertEquals(230f, l.width, eps)
        assertEquals(100f, l.y, eps); assertEquals(100f, l.height, eps)
    }

    @Test fun resizeNeverGoesBelowMinimum() {
        val e = rect("r", 100f, 100f, 200f, 100f)
        val r = DesignOps.resize(e, Handle.BOTTOM_RIGHT, -1000f, -1000f)
        assertEquals(MIN_ELEMENT_SIZE, r.width, eps)
        assertEquals(MIN_ELEMENT_SIZE, r.height, eps)
        assertEquals(100f, r.x, eps)   // anchor (top-left) unchanged
    }

    @Test fun resizeKeepsAspect() {
        val e = rect("r", 0f, 0f, 200f, 100f)
        val r = DesignOps.resize(e, Handle.BOTTOM_RIGHT, 100f, 0f, keepAspect = true)
        assertEquals(300f, r.width, eps); assertEquals(150f, r.height, eps)
        val side = DesignOps.resize(e, Handle.RIGHT, 100f, 0f, keepAspect = true)
        assertEquals(150f, side.height, eps)
    }

    @Test fun resizeRotatedKeepsOppositeCornerFixed() {
        val e = rect("r", 100f, 100f, 200f, 100f, rot = 30f)
        val anchorBefore = DesignOps.handlePosition(e, Handle.TOP_LEFT)
        // Drag the bottom-right corner along the element's own x-axis by 50 local px
        val dir = DesignOps.rotate(Point(50f, 0f), 30f)
        val r = DesignOps.resize(e, Handle.BOTTOM_RIGHT, dir.x, dir.y)
        assertEquals(250f, r.width, 0.05f)
        assertEquals(100f, r.height, 0.05f)
        val anchorAfter = DesignOps.handlePosition(r, Handle.TOP_LEFT)
        assertEquals(anchorBefore.x, anchorAfter.x, 0.05f)
        assertEquals(anchorBefore.y, anchorAfter.y, 0.05f)
        assertEquals(30f, r.rotation, eps)
    }

    @Test fun scaleAroundCentre() {
        val e = DesignElement(id = "t", type = ElementType.TEXT, name = "t", x = 0f, y = 0f, width = 100f, height = 50f, fontSize = 20f)
        val s = DesignOps.scale(e, 2f)
        assertEquals(50f, s.centerX, eps); assertEquals(25f, s.centerY, eps)
        assertEquals(200f, s.width, eps); assertEquals(40f, s.fontSize, eps)
    }

    // ── Rotation ──────────────────────────────────────────────────────────

    @Test fun rotationTowardsPointAndSnapping() {
        val e = rect("r", 0f, 0f, 100f, 100f)          // centre (50,50)
        assertEquals(0f, DesignOps.rotationTowards(e, 50f, -100f), eps)   // straight up
        assertEquals(90f, DesignOps.rotationTowards(e, 200f, 50f), eps)   // right
        assertEquals(180f, DesignOps.rotationTowards(e, 50f, 300f), eps)  // down
        assertEquals(270f, DesignOps.rotationTowards(e, -100f, 52f), eps) // ~left snaps to 270
        assertEquals(0f, DesignOps.normalizeAngle(360f), eps)
        assertEquals(350f, DesignOps.normalizeAngle(-10f), eps)
    }

    // ── Snapping ──────────────────────────────────────────────────────────

    @Test fun snapToGrid() {
        val p = project().copy(snapToGrid = true, gridSize = 10)
        val e = rect("r", 0f, 0f, 50f, 50f)
        val s = DesignOps.snapMove(p, e, 23f, 47f)
        assertEquals(20f, s.x, eps); assertEquals(50f, s.y, eps)
    }

    @Test fun snapToCanvasCentreAndOtherElements() {
        var p = project().copy(snapToGuides = true)
        val e = rect("r", 0f, 0f, 100f, 100f)
        // centre x of 100px element at x=460 → 510, close to canvas centre 512
        val s = DesignOps.snapMove(p, e, 460f, 300f)
        assertEquals(462f, s.x, eps)
        assertTrue(s.guidesX.contains(512f))
        // Snap left edge onto another element's right edge
        p = DesignOps.add(p, rect("o", 700f, 0f, 50f, 50f))
        val s2 = DesignOps.snapMove(p, e, 753f, 400f)
        assertEquals(750f, s2.x, eps)
        assertTrue(s2.guidesX.contains(750f))
    }

    @Test fun alignToCanvas() {
        var p = DesignOps.add(project(), rect("r", 10f, 10f, 100f, 50f))
        p = DesignOps.align(p, "r", Alignment.RIGHT)
        assertEquals(924f, p.element("r")!!.x, eps)
        p = DesignOps.align(p, "r", Alignment.V_CENTER)
        assertEquals(275f, p.element("r")!!.y, eps)
        p = DesignOps.align(p, "r", Alignment.H_CENTER)
        assertEquals(462f, p.element("r")!!.x, eps)
        // Locked elements do not move
        val locked = DesignOps.update(p, "r") { it.copy(locked = true) }
        assertEquals(462f, DesignOps.align(locked, "r", Alignment.LEFT).element("r")!!.x, eps)
    }

    @Test fun sanitizeClampsBadValues() {
        val bad = rect("r", Float.NaN, 1e9f, -5f, Float.POSITIVE_INFINITY).copy(opacity = 3f, rotation = 725f)
        val s = DesignOps.sanitize(bad)
        assertEquals(0f, s.x, eps)
        assertEquals(20_000f, s.y, eps)
        assertEquals(1f, s.width, eps)
        assertEquals(100f, s.height, eps)
        assertEquals(1f, s.opacity, eps)
        assertEquals(5f, s.rotation, eps)
    }

    // ── History ───────────────────────────────────────────────────────────

    @Test fun undoRedo() {
        val h = History<Int>(limit = 3)
        var present = 0
        for (v in 1..5) { h.record(present); present = v }
        assertEquals(3, h.undoDepth)              // bounded
        present = h.undo(present)!!; assertEquals(4, present)
        present = h.undo(present)!!; assertEquals(3, present)
        present = h.redo(present)!!; assertEquals(4, present)
        h.record(present); present = 99           // new edit clears redo
        assertFalse(h.canRedo)
        assertNull(h.redo(present))
    }

    // ── JSON ──────────────────────────────────────────────────────────────

    @Test fun jsonRoundTripOfEveryTemplate() {
        for (t in DesignTemplates.all) {
            val p = t.build()
            val decoded = DesignJson.decode(DesignJson.encode(p))
            assertEquals("template ${t.key}", p, decoded)
            val compact = DesignJson.decode(DesignJson.encode(p, pretty = false))
            assertEquals(p, compact)
        }
    }

    @Test fun jsonRoundTripOfAllFieldsAndTrickyText() {
        val e = DesignElement(
            id = "x1", type = ElementType.SHORTCUT, name = "اسم \"مقتبس\" \\ \n سطر",
            x = 1.5f, y = -2.25f, width = 300f, height = 120.125f, rotation = 33.3f, opacity = 0.42f,
            visible = false, locked = true, fillMode = FillMode.GRADIENT, fillColor = 0x80FF0000.toInt(),
            fillColor2 = 0x00000000, gradientAngle = 45f, strokeColor = 0xFF00FF00.toInt(), strokeWidth = 3f,
            cornerRadius = 12f, shadowRadius = 8f, shadowColor = 0x40000000, shadowDx = 1f, shadowDy = 2f,
            text = "مرحبا 👋 Hello\t\u0001", textColor = 0xFF123456.toInt(), fontSize = 44f, font = FontChoice.CUSTOM,
            customFontPath = "font.ttf", bold = true, italic = true, textAlign = TextAlignment.END, letterSpacing = 0.1f,
            timeFormat = "hh:mm a", dateFormat = "yyyy/MM/dd", iconName = "phone", iconColor = 0xFFABCDEF.toInt(),
            useAppIcon = false, imagePath = "img.png", imageFit = ImageFit.CONTAIN, action = ElementAction.LAUNCH_APP,
            packageName = "com.example.app"
        )
        val p = project().copy(
            elements = listOf(e),
            background = DesignBackground(BackgroundType.IMAGE, 0xFF010203.toInt(), 0xFF040506.toInt(), 10f, "bg.jpg", ImageFit.STRETCH, 0.3f),
            gridSize = 12, showGrid = true, createdAt = 1700000000000L, updatedAt = 1700000000123L
        )
        assertEquals(p, DesignJson.decode(DesignJson.encode(p)))
    }

    @Test fun jsonToleratesMissingAndUnknownFields() {
        val json = """
            {"name":"قديم","elements":[
               {"type":"TEXT","text":"hi","futureField":{"a":[1,2,3]}},
               {"type":"HOLOGRAM"},
               {"id":"dup","type":"RECT","fillColor":"#FF0000","textAlign":"WEIRD"},
               {"id":"dup","type":"ELLIPSE"}
            ],"background":{"type":"GRADIENT","color":"#112233"}}
        """.trimIndent()
        val p = DesignJson.decode(json)
        assertEquals("قديم", p.name)
        assertEquals(1024, p.width)
        assertEquals(3, p.elements.size)                       // unknown type skipped
        assertEquals(0xFFFF0000.toInt(), p.elements[1].fillColor)
        assertEquals(TextAlignment.CENTER, p.elements[1].textAlign)
        assertEquals(3, p.elements.map { it.id }.toSet().size) // duplicate ids repaired
        assertTrue(p.id.isNotEmpty())
        assertEquals(0xFF112233.toInt(), p.background.color)
    }

    @Test fun jsonRejectsGarbage() {
        for (bad in listOf("", "[]", "{", "{\"a\":}", "nope", "{\"a\":1} x")) {
            var threw = false
            try { DesignJson.decode(bad) } catch (e: Exception) { threw = true }
            assertTrue("should reject: $bad", threw)
        }
    }

    @Test fun colorParsing() {
        assertEquals(0xFF112233.toInt(), DesignJson.parseColor("#112233", 0))
        assertEquals(0x80112233.toInt(), DesignJson.parseColor("#80112233", 0))
        assertEquals(7, DesignJson.parseColor("#zz", 7))
        assertEquals("#80112233", DesignJson.color(0x80112233.toInt()))
        assertTrue(abs(DesignJson.parseColor(255, 0) - 255) == 0)
    }
}
