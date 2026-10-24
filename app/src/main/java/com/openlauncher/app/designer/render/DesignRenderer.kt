package com.openlauncher.app.designer.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.R
import com.openlauncher.app.designer.model.BackgroundType
import com.openlauncher.app.designer.model.DesignBackground
import com.openlauncher.app.designer.model.DesignElement
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.model.ElementType
import com.openlauncher.app.designer.model.FillMode
import com.openlauncher.app.designer.model.FontChoice
import com.openlauncher.app.designer.model.ImageFit
import com.openlauncher.app.designer.model.TextAlignment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The single renderer for designs. The editor canvas, the live home screen and
 * PNG export all call [draw], so what you see while editing is exactly what is
 * exported and what runs on the head unit.
 *
 * Coordinates passed to [draw] are design pixels; callers scale the Canvas.
 */
class DesignRenderer(context: Context) {

    private val appContext = context.applicationContext

    // ── Caches ─────────────────────────────────────────────────────────────

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<String>()
    private val typefaceCache = HashMap<String, Typeface>()
    private val appIconCache = HashMap<String, Bitmap?>()
    private val layoutCache = LruCache<String, StaticLayout>(96)

    private fun maxCacheBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 6).coerceAtMost(96L * 1024 * 1024).toInt()
    }

    fun clearCaches() {
        bitmapCache.evictAll()
        synchronized(missing) { missing.clear() }
        typefaceCache.clear()
        appIconCache.clear()
        layoutCache.evictAll()
    }

    /** Forget one asset (after it is replaced on disk). */
    fun invalidate(file: File) {
        bitmapCache.remove(file.absolutePath)
        synchronized(missing) { missing.remove(file.absolutePath) }
    }

    // ── Paint objects (reused; renderer is used from one thread at a time) ─

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val matrix = Matrix()

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Draws [project] into [canvas] (design-pixel space).
     * [assetDir] holds the project's images/fonts; [now] drives clock/date elements.
     */
    fun draw(canvas: Canvas, project: DesignProject, assetDir: File?, now: Long = System.currentTimeMillis()) {
        val w = project.width.toFloat()
        val h = project.height.toFloat()
        val save = canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        drawBackground(canvas, project.background, w, h, assetDir)
        for (el in project.elements) {
            if (!el.visible || el.opacity <= 0.001f) continue
            runCatching { drawElement(canvas, el, assetDir, now) }
        }
        canvas.restoreToCount(save)
    }

    /** Renders to a bitmap at [scale] × the design size (1f = exact 1024×600 PNG). */
    fun renderBitmap(project: DesignProject, assetDir: File?, scale: Float = 1f, now: Long = System.currentTimeMillis()): Bitmap {
        val bw = max(1, (project.width * scale).toInt())
        val bh = max(1, (project.height * scale).toInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(bw / project.width.toFloat(), bh / project.height.toFloat())
        draw(c, project, assetDir, now)
        return bmp
    }

    // ── Background ─────────────────────────────────────────────────────────

    private fun drawBackground(canvas: Canvas, bg: DesignBackground, w: Float, h: Float, assetDir: File?) {
        fill.reset(); fill.isAntiAlias = true
        rect.set(0f, 0f, w, h)
        when (bg.type) {
            BackgroundType.COLOR -> {
                fill.color = bg.color
                canvas.drawRect(rect, fill)
            }
            BackgroundType.GRADIENT -> {
                fill.shader = gradient(rect, bg.gradientAngle, bg.color, bg.color2)
                canvas.drawRect(rect, fill)
                fill.shader = null
            }
            BackgroundType.IMAGE -> {
                fill.color = bg.color
                canvas.drawRect(rect, fill)
                val bmp = bitmap(assetDir, bg.imagePath)
                if (bmp != null) drawBitmapFit(canvas, bmp, rect, bg.imageFit)
                if (bg.dim > 0f) {
                    fill.color = android.graphics.Color.argb((bg.dim.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)
                    canvas.drawRect(rect, fill)
                }
            }
        }
    }

    // ── Elements ───────────────────────────────────────────────────────────

    private fun drawElement(canvas: Canvas, el: DesignElement, assetDir: File?, now: Long) {
        val save = canvas.save()
        try {
            drawElementInner(canvas, el, assetDir, now)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun drawElementInner(canvas: Canvas, el: DesignElement, assetDir: File?, now: Long) {
        if (el.rotation != 0f) canvas.rotate(el.rotation, el.centerX, el.centerY)
        if (el.opacity < 0.999f) {
            val pad = el.shadowRadius * 2 + abs(el.shadowDx) + abs(el.shadowDy) + el.strokeWidth + 4f
            rect.set(el.x - pad, el.y - pad, el.x + el.width + pad, el.y + el.height + pad)
            canvas.saveLayerAlpha(rect, (el.opacity * 255).toInt().coerceIn(0, 255))
        }
        rect.set(el.x, el.y, el.x + el.width, el.y + el.height)
        when (el.type) {
            ElementType.RECT -> drawShape(canvas, el, oval = false)
            ElementType.ELLIPSE -> drawShape(canvas, el, oval = true)
            ElementType.LINE -> drawLine(canvas, el)
            ElementType.IMAGE -> drawImage(canvas, el, assetDir)
            ElementType.ICON -> {
                drawBox(canvas, el)
                drawIcon(canvas, el.iconName, el.iconColor, RectF(el.x, el.y, el.x + el.width, el.y + el.height), el)
            }
            ElementType.TEXT -> {
                drawBox(canvas, el)
                drawText(canvas, el, el.text, RectF(el.x, el.y, el.x + el.width, el.y + el.height), assetDir, singleLine = false)
            }
            ElementType.CLOCK -> {
                drawBox(canvas, el)
                drawText(canvas, el, formatDate(el.timeFormat, "HH:mm", now), RectF(el.x, el.y, el.x + el.width, el.y + el.height), assetDir, singleLine = true)
            }
            ElementType.DATE -> {
                drawBox(canvas, el)
                drawText(canvas, el, formatDate(el.dateFormat, "d/M/yyyy", now), RectF(el.x, el.y, el.x + el.width, el.y + el.height), assetDir, singleLine = true)
            }
            ElementType.SHORTCUT -> drawShortcut(canvas, el, assetDir)
        }
    }

    private fun preparePaintFill(el: DesignElement, box: RectF) {
        fill.reset()
        fill.isAntiAlias = true
        fill.style = Paint.Style.FILL
        if (el.fillMode == FillMode.GRADIENT) {
            fill.shader = gradient(box, el.gradientAngle, el.fillColor, el.fillColor2)
        } else {
            fill.color = el.fillColor
        }
        if (el.shadowRadius > 0f) fill.setShadowLayer(el.shadowRadius, el.shadowDx, el.shadowDy, el.shadowColor)
    }

    private fun prepareStroke(el: DesignElement) {
        stroke.reset()
        stroke.isAntiAlias = true
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = el.strokeWidth
        stroke.color = el.strokeColor
        if (el.shadowRadius > 0f && el.fillMode == FillMode.NONE) {
            stroke.setShadowLayer(el.shadowRadius, el.shadowDx, el.shadowDy, el.shadowColor)
        }
    }

    private fun drawShape(canvas: Canvas, el: DesignElement, oval: Boolean) {
        val box = RectF(rect)
        if (el.fillMode != FillMode.NONE) {
            preparePaintFill(el, box)
            if (oval) canvas.drawOval(box, fill) else canvas.drawRoundRect(box, el.cornerRadius, el.cornerRadius, fill)
        }
        if (el.strokeWidth > 0f && android.graphics.Color.alpha(el.strokeColor) > 0) {
            prepareStroke(el)
            val inset = el.strokeWidth / 2f
            val sb = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset)
            val r = max(0f, el.cornerRadius - inset)
            if (oval) canvas.drawOval(sb, stroke) else canvas.drawRoundRect(sb, r, r, stroke)
        }
    }

    /** Optional background box behind text/icon elements. */
    private fun drawBox(canvas: Canvas, el: DesignElement) {
        val hasStroke = el.strokeWidth > 0f && android.graphics.Color.alpha(el.strokeColor) > 0
        if (el.fillMode == FillMode.NONE && !hasStroke) return
        val box = RectF(el.x, el.y, el.x + el.width, el.y + el.height)
        if (el.fillMode != FillMode.NONE) {
            preparePaintFill(el, box)
            fill.clearShadowLayer() // the shadow belongs to the glyphs, not the box
            canvas.drawRoundRect(box, el.cornerRadius, el.cornerRadius, fill)
        }
        if (hasStroke) {
            prepareStroke(el)
            stroke.clearShadowLayer()
            val inset = el.strokeWidth / 2f
            val r = max(0f, el.cornerRadius - inset)
            canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), r, r, stroke)
        }
    }

    private fun drawLine(canvas: Canvas, el: DesignElement) {
        val box = RectF(rect)
        preparePaintFill(el.copy(fillMode = if (el.fillMode == FillMode.NONE) FillMode.SOLID else el.fillMode), box)
        val r = box.height() / 2f
        canvas.drawRoundRect(box, r, r, fill)
    }

    private fun drawImage(canvas: Canvas, el: DesignElement, assetDir: File?) {
        val box = RectF(rect)
        val bmp = bitmap(assetDir, el.imagePath)
        val save = canvas.save()
        if (el.cornerRadius > 0f) {
            path.reset()
            path.addRoundRect(box, el.cornerRadius, el.cornerRadius, Path.Direction.CW)
            canvas.clipPath(path)
        }
        if (bmp != null) {
            drawBitmapFit(canvas, bmp, box, el.imageFit)
        } else {
            // Placeholder so an empty/missing image is still visible and selectable
            fill.reset(); fill.isAntiAlias = true; fill.color = 0xFF2E2E2E.toInt()
            canvas.drawRect(box, fill)
            val s = min(box.width(), box.height()) * 0.4f
            drawIcon(canvas, "image", 0xFF777777.toInt(), RectF(box.centerX() - s / 2, box.centerY() - s / 2, box.centerX() + s / 2, box.centerY() + s / 2), null)
        }
        canvas.restoreToCount(save)
        if (el.strokeWidth > 0f && android.graphics.Color.alpha(el.strokeColor) > 0) {
            prepareStroke(el)
            val inset = el.strokeWidth / 2f
            val r = max(0f, el.cornerRadius - inset)
            canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), r, r, stroke)
        }
    }

    private fun drawBitmapFit(canvas: Canvas, bmp: Bitmap, box: RectF, fit: ImageFit) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val dst = RectF(box)
        val src = Rect(0, 0, bmp.width, bmp.height)
        when (fit) {
            ImageFit.STRETCH -> Unit
            ImageFit.CONTAIN -> {
                val s = min(box.width() / bw, box.height() / bh)
                val w = bw * s; val h = bh * s
                dst.set(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
            }
            ImageFit.COVER -> {
                val s = max(box.width() / bw, box.height() / bh)
                val sw = box.width() / s; val sh = box.height() / s
                val l = ((bw - sw) / 2f).toInt(); val t = ((bh - sh) / 2f).toInt()
                src.set(l, t, (l + sw).toInt().coerceAtMost(bmp.width), (t + sh).toInt().coerceAtMost(bmp.height))
            }
        }
        canvas.drawBitmap(bmp, src, dst, bmpPaint)
    }

    private fun drawIcon(canvas: Canvas, name: String, color: Int, box: RectF, el: DesignElement?) {
        val icon = DesignIcons.path(name) ?: DesignIcons.path("star") ?: return
        val s = min(box.width() / icon.viewportWidth, box.height() / icon.viewportHeight)
        val dx = box.centerX() - icon.viewportWidth * s / 2f
        val dy = box.centerY() - icon.viewportHeight * s / 2f
        matrix.reset()
        matrix.setScale(s, s)
        matrix.postTranslate(dx, dy)
        path.reset()
        icon.path.transform(matrix, path)
        fill.reset(); fill.isAntiAlias = true; fill.style = Paint.Style.FILL; fill.color = color
        if (el != null && el.shadowRadius > 0f) fill.setShadowLayer(el.shadowRadius, el.shadowDx, el.shadowDy, el.shadowColor)
        canvas.drawPath(path, fill)
    }

    // ── Text ───────────────────────────────────────────────────────────────

    private fun formatDate(pattern: String, fallback: String, now: Long): String =
        runCatching { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
            .getOrElse { SimpleDateFormat(fallback, Locale.getDefault()).format(Date(now)) }

    fun typeface(choice: FontChoice, bold: Boolean, italic: Boolean, customPath: String, assetDir: File?): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val key = "${choice.name}|$style|${if (choice == FontChoice.CUSTOM) File(assetDir, customPath).absolutePath else ""}"
        typefaceCache[key]?.let { return it }
        val base: Typeface = when (choice) {
            FontChoice.SYSTEM_SANS -> Typeface.SANS_SERIF
            FontChoice.SYSTEM_SERIF -> Typeface.SERIF
            FontChoice.SYSTEM_MONO -> Typeface.MONOSPACE
            FontChoice.JETBRAINS_MONO -> runCatching {
                ResourcesCompat.getFont(appContext, if (bold) R.font.jetbrains_mono_bold else R.font.jetbrains_mono_regular)
            }.getOrNull() ?: Typeface.MONOSPACE
            FontChoice.SOURCE_CODE_PRO -> runCatching {
                ResourcesCompat.getFont(appContext, if (bold) R.font.source_code_pro_bold else R.font.source_code_pro_regular)
            }.getOrNull() ?: Typeface.MONOSPACE
            FontChoice.CUSTOM -> {
                val f = if (assetDir != null && customPath.isNotEmpty()) File(assetDir, customPath) else null
                if (f != null && f.isFile) runCatching { Typeface.createFromFile(f) }.getOrNull() ?: Typeface.DEFAULT
                else Typeface.DEFAULT
            }
        }
        val tf = Typeface.create(base, style)
        typefaceCache[key] = tf
        return tf
    }

    private fun drawText(canvas: Canvas, el: DesignElement, text: String, box: RectF, assetDir: File?, singleLine: Boolean) {
        if (text.isEmpty() || box.width() < 1f) return
        val layout = layoutFor(el, text, box.width().toInt().coerceAtLeast(1), assetDir, singleLine)
        val top = box.top + (box.height() - layout.height) / 2f
        val save = canvas.save()
        canvas.translate(box.left, top)
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun layoutFor(el: DesignElement, text: String, width: Int, assetDir: File?, singleLine: Boolean, sizeOverride: Float? = null): StaticLayout {
        val size = sizeOverride ?: el.fontSize
        val key = listOf(
            text, width, size, el.font, el.customFontPath, el.bold, el.italic, el.textAlign,
            el.textColor, el.letterSpacing, el.shadowRadius, el.shadowColor, el.shadowDx, el.shadowDy, singleLine, assetDir?.path
        ).joinToString("\u0001")
        layoutCache.get(key)?.let { return it }

        val p = TextPaint(textPaint)
        p.reset()
        p.isAntiAlias = true
        p.isSubpixelText = true
        p.textSize = size
        p.color = el.textColor
        p.typeface = typeface(el.font, el.bold, el.italic, el.customFontPath, assetDir)
        p.letterSpacing = el.letterSpacing
        if (el.shadowRadius > 0f) p.setShadowLayer(el.shadowRadius, el.shadowDx, el.shadowDy, el.shadowColor)

        val align = when (el.textAlign) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val content: CharSequence = if (singleLine) TextUtils.ellipsize(text, p, width.toFloat(), TextUtils.TruncateAt.END) else text
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(content, 0, content.length, p, width)
                .setAlignment(align)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(content, p, width, align, 1f, 0f, false)
        }
        layoutCache.put(key, layout)
        return layout
    }

    // ── Shortcut tile ──────────────────────────────────────────────────────

    private fun drawShortcut(canvas: Canvas, el: DesignElement, assetDir: File?) {
        val box = RectF(rect)
        if (el.fillMode != FillMode.NONE) {
            preparePaintFill(el, box)
            canvas.drawRoundRect(box, el.cornerRadius, el.cornerRadius, fill)
        }
        if (el.strokeWidth > 0f && android.graphics.Color.alpha(el.strokeColor) > 0) {
            prepareStroke(el)
            stroke.clearShadowLayer()
            val inset = el.strokeWidth / 2f
            val r = max(0f, el.cornerRadius - inset)
            canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), r, r, stroke)
        }
        val hasLabel = el.text.isNotBlank()
        val labelH = if (hasLabel) el.fontSize * 1.6f else 0f
        val pad = min(box.width(), box.height()) * 0.14f
        val iconArea = RectF(box.left + pad, box.top + pad, box.right - pad, box.bottom - pad - labelH)
        if (iconArea.height() < 4f) iconArea.set(box.left + pad, box.top + pad, box.right - pad, box.bottom - pad)
        val iconSize = min(iconArea.width(), iconArea.height())
        val iconBox = RectF(
            iconArea.centerX() - iconSize / 2f, iconArea.centerY() - iconSize / 2f,
            iconArea.centerX() + iconSize / 2f, iconArea.centerY() + iconSize / 2f
        )
        val appIcon = if (el.useAppIcon && el.packageName.isNotEmpty()) appIcon(el.packageName) else null
        if (appIcon != null) {
            canvas.drawBitmap(appIcon, null, iconBox, bmpPaint)
        } else {
            drawIcon(canvas, el.iconName.ifEmpty { "apps" }, el.iconColor, iconBox, null)
        }
        if (hasLabel) {
            val labelBox = RectF(box.left + 6f, box.bottom - pad * 0.6f - labelH, box.right - 6f, box.bottom - pad * 0.6f)
            drawText(canvas, el.copy(textAlign = TextAlignment.CENTER), el.text, labelBox, assetDir, singleLine = true)
        }
    }

    fun appIcon(pkg: String): Bitmap? {
        if (appIconCache.containsKey(pkg)) return appIconCache[pkg]
        val bmp = runCatching {
            appContext.packageManager.getApplicationIcon(pkg).toBitmap(192, 192)
        }.getOrNull()
        appIconCache[pkg] = bmp
        return bmp
    }

    // ── Bitmaps ────────────────────────────────────────────────────────────

    fun bitmap(assetDir: File?, name: String): Bitmap? {
        if (assetDir == null || name.isEmpty()) return null
        val f = File(assetDir, name)
        val key = f.absolutePath
        bitmapCache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        if (!f.isFile) { synchronized(missing) { missing.add(key) }; return null }
        val bmp = decodeSampled(f, 2048)
        if (bmp == null) synchronized(missing) { missing.add(key) } else bitmapCache.put(key, bmp)
        return bmp
    }

    private fun decodeSampled(f: File, maxDim: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun gradient(box: RectF, angleDeg: Float, c1: Int, c2: Int): Shader {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val half = abs(box.width() * dx) / 2f + abs(box.height() * dy) / 2f
        return LinearGradient(
            box.centerX() - dx * half, box.centerY() - dy * half,
            box.centerX() + dx * half, box.centerY() + dy * half,
            c1, c2, Shader.TileMode.CLAMP
        )
    }
}
