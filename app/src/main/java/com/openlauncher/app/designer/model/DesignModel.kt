package com.openlauncher.app.designer.model

/*
 * Theme designer data model.
 *
 * This file (and everything else in designer/model) is pure Kotlin: no Android
 * or Compose imports, so the whole editing engine can be unit-tested on the JVM.
 * Colors are ARGB ints, coordinates are design pixels on a fixed canvas
 * (1024×600 = Pioneer Y6 HZ25 native panel by default).
 */

const val DEFAULT_CANVAS_W = 1024
const val DEFAULT_CANVAS_H = 600
const val DESIGN_FORMAT_VERSION = 1
const val MIN_ELEMENT_SIZE = 8f

enum class ElementType { TEXT, RECT, ELLIPSE, LINE, IMAGE, ICON, CLOCK, DATE, SHORTCUT }

enum class TextAlignment { START, CENTER, END }

enum class FontChoice { SYSTEM_SANS, SYSTEM_SERIF, SYSTEM_MONO, JETBRAINS_MONO, SOURCE_CODE_PRO, CUSTOM }

enum class ImageFit { COVER, CONTAIN, STRETCH }

enum class FillMode { SOLID, GRADIENT, NONE }

enum class BackgroundType { COLOR, GRADIENT, IMAGE }

/** What happens when the element is tapped while the design runs as the home screen. */
enum class ElementAction { NONE, LAUNCH_APP, OPEN_APP_LIBRARY, OPEN_SETTINGS, OPEN_DESIGNER, OPEN_CLASSIC_HOME }

data class DesignElement(
    val id: String,
    val type: ElementType,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,          // degrees, clockwise, around the element centre
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val locked: Boolean = false,

    // ── Shape / fill ───────────────────────────────────────────────
    val fillMode: FillMode = FillMode.SOLID,
    val fillColor: Int = 0xFF2A2A2A.toInt(),
    val fillColor2: Int = 0xFF000000.toInt(),
    val gradientAngle: Float = 90f,    // 0 = left→right, 90 = top→bottom
    val strokeColor: Int = 0x00000000,
    val strokeWidth: Float = 0f,
    val cornerRadius: Float = 0f,

    // ── Shadow ─────────────────────────────────────────────────────
    val shadowRadius: Float = 0f,
    val shadowColor: Int = 0x99000000.toInt(),
    val shadowDx: Float = 0f,
    val shadowDy: Float = 4f,

    // ── Text (TEXT, CLOCK, DATE, SHORTCUT label) ───────────────────
    val text: String = "",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val fontSize: Float = 32f,
    val font: FontChoice = FontChoice.SYSTEM_SANS,
    val customFontPath: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val textAlign: TextAlignment = TextAlignment.CENTER,
    val letterSpacing: Float = 0f,     // em

    // ── Clock / date ───────────────────────────────────────────────
    val timeFormat: String = "HH:mm",
    val dateFormat: String = "EEEE d MMMM",

    // ── Icon (ICON, SHORTCUT) ──────────────────────────────────────
    val iconName: String = "",
    val iconColor: Int = 0xFFFFFFFF.toInt(),
    val useAppIcon: Boolean = true,    // SHORTCUT: show the real app icon when an app is assigned

    // ── Image ──────────────────────────────────────────────────────
    val imagePath: String = "",        // file name inside the project's asset folder
    val imageFit: ImageFit = ImageFit.COVER,

    // ── Action ─────────────────────────────────────────────────────
    val action: ElementAction = ElementAction.NONE,
    val packageName: String = ""
) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

data class DesignBackground(
    val type: BackgroundType = BackgroundType.COLOR,
    val color: Int = 0xFF000000.toInt(),
    val color2: Int = 0xFF1C1C1C.toInt(),
    val gradientAngle: Float = 90f,
    val imagePath: String = "",
    val imageFit: ImageFit = ImageFit.COVER,
    val dim: Float = 0f                 // 0..1 black overlay over an image background
)

data class DesignProject(
    val id: String,
    val name: String,
    val width: Int = DEFAULT_CANVAS_W,
    val height: Int = DEFAULT_CANVAS_H,
    val background: DesignBackground = DesignBackground(),
    /** Z-order: index 0 is the bottom layer, the last element is drawn on top. */
    val elements: List<DesignElement> = emptyList(),
    val gridSize: Int = 8,
    val snapToGrid: Boolean = true,
    val snapToGuides: Boolean = true,
    val showGrid: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val formatVersion: Int = DESIGN_FORMAT_VERSION
) {
    fun element(id: String?): DesignElement? = if (id == null) null else elements.firstOrNull { it.id == id }
    fun indexOf(id: String): Int = elements.indexOfFirst { it.id == id }
}

/** Axis-aligned box in design pixels. */
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f
}

data class Point(val x: Float, val y: Float)

/** The eight resize handles plus the rotation knob. */
enum class Handle {
    TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, ROTATE;

    val isCorner: Boolean get() = this == TOP_LEFT || this == TOP_RIGHT || this == BOTTOM_RIGHT || this == BOTTOM_LEFT
}

enum class Alignment { LEFT, H_CENTER, RIGHT, TOP, V_CENTER, BOTTOM }

enum class LayerMove { FORWARD, BACKWARD, TO_FRONT, TO_BACK }
