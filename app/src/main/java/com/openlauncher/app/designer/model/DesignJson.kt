package com.openlauncher.app.designer.model

/**
 * Self-contained JSON codec for design projects.
 *
 * Hand-written (no Gson/reflection) so that: the file format is stable and
 * human-readable, missing fields fall back to defaults (old files keep
 * loading), unknown enum values degrade gracefully, and it runs on any JVM
 * for unit tests.
 */
object DesignJson {

    // ── Encoding ───────────────────────────────────────────────────────────

    fun encode(p: DesignProject, pretty: Boolean = true): String {
        val root = linkedMapOf<String, Any?>(
            "format" to "openlauncher-design",
            "formatVersion" to DESIGN_FORMAT_VERSION,
            "id" to p.id,
            "name" to p.name,
            "width" to p.width,
            "height" to p.height,
            "gridSize" to p.gridSize,
            "snapToGrid" to p.snapToGrid,
            "snapToGuides" to p.snapToGuides,
            "showGrid" to p.showGrid,
            "createdAt" to p.createdAt,
            "updatedAt" to p.updatedAt,
            "background" to encodeBackground(p.background),
            "elements" to p.elements.map { encodeElement(it) }
        )
        val sb = StringBuilder()
        write(sb, root, if (pretty) 0 else -1)
        return sb.toString()
    }

    private fun encodeBackground(b: DesignBackground) = linkedMapOf<String, Any?>(
        "type" to b.type.name,
        "color" to color(b.color),
        "color2" to color(b.color2),
        "gradientAngle" to b.gradientAngle,
        "imagePath" to b.imagePath,
        "imageFit" to b.imageFit.name,
        "dim" to b.dim
    )

    private fun encodeElement(e: DesignElement) = linkedMapOf<String, Any?>(
        "id" to e.id,
        "type" to e.type.name,
        "name" to e.name,
        "x" to e.x, "y" to e.y, "width" to e.width, "height" to e.height,
        "rotation" to e.rotation,
        "opacity" to e.opacity,
        "visible" to e.visible,
        "locked" to e.locked,
        "fillMode" to e.fillMode.name,
        "fillColor" to color(e.fillColor),
        "fillColor2" to color(e.fillColor2),
        "gradientAngle" to e.gradientAngle,
        "strokeColor" to color(e.strokeColor),
        "strokeWidth" to e.strokeWidth,
        "cornerRadius" to e.cornerRadius,
        "shadowRadius" to e.shadowRadius,
        "shadowColor" to color(e.shadowColor),
        "shadowDx" to e.shadowDx,
        "shadowDy" to e.shadowDy,
        "text" to e.text,
        "textColor" to color(e.textColor),
        "fontSize" to e.fontSize,
        "font" to e.font.name,
        "customFontPath" to e.customFontPath,
        "bold" to e.bold,
        "italic" to e.italic,
        "textAlign" to e.textAlign.name,
        "letterSpacing" to e.letterSpacing,
        "timeFormat" to e.timeFormat,
        "dateFormat" to e.dateFormat,
        "iconName" to e.iconName,
        "iconColor" to color(e.iconColor),
        "useAppIcon" to e.useAppIcon,
        "imagePath" to e.imagePath,
        "imageFit" to e.imageFit.name,
        "action" to e.action.name,
        "packageName" to e.packageName
    )

    fun color(argb: Int): String = "#" + String.format("%08X", argb)

    // ── Decoding ───────────────────────────────────────────────────────────

    class DesignFormatException(msg: String) : Exception(msg)

    fun decode(json: String): DesignProject {
        val root = JsonParser(json).parse() as? Map<*, *>
            ?: throw DesignFormatException("Root is not a JSON object")
        val r = Obj(root)
        val version = r.int("formatVersion", 1)
        if (version > DESIGN_FORMAT_VERSION) {
            // Newer file: still try, unknown fields are simply ignored
        }
        val d = DesignProject(id = "", name = "")
        val elements = (root["elements"] as? List<*>).orEmpty().mapNotNull { item ->
            (item as? Map<*, *>)?.let { runCatching { decodeElement(Obj(it)) }.getOrNull() }
        }
        // Guarantee unique ids (hand-edited files)
        val seen = HashSet<String>()
        val unique = elements.map { if (it.id.isEmpty() || !seen.add(it.id)) it.copy(id = DesignOps.newId()).also { c -> seen.add(c.id) } else it }
        return DesignProject(
            id = r.str("id", "").ifEmpty { DesignOps.newId("design") },
            name = r.str("name", "تصميم"),
            width = r.int("width", d.width).coerceIn(64, 8192),
            height = r.int("height", d.height).coerceIn(64, 8192),
            gridSize = r.int("gridSize", d.gridSize).coerceIn(1, 256),
            snapToGrid = r.bool("snapToGrid", d.snapToGrid),
            snapToGuides = r.bool("snapToGuides", d.snapToGuides),
            showGrid = r.bool("showGrid", d.showGrid),
            createdAt = r.long("createdAt", 0L),
            updatedAt = r.long("updatedAt", 0L),
            background = (root["background"] as? Map<*, *>)?.let { decodeBackground(Obj(it)) } ?: DesignBackground(),
            elements = unique
        )
    }

    private fun decodeBackground(o: Obj): DesignBackground {
        val d = DesignBackground()
        return DesignBackground(
            type = o.enum("type", d.type),
            color = o.color("color", d.color),
            color2 = o.color("color2", d.color2),
            gradientAngle = o.float("gradientAngle", d.gradientAngle),
            imagePath = o.str("imagePath", d.imagePath),
            imageFit = o.enum("imageFit", d.imageFit),
            dim = o.float("dim", d.dim).coerceIn(0f, 1f)
        )
    }

    private fun decodeElement(o: Obj): DesignElement {
        val type = o.enumOrNull<ElementType>("type") ?: throw DesignFormatException("Unknown element type")
        val d = DesignElement(id = "", type = type, name = "", x = 0f, y = 0f, width = 100f, height = 100f)
        return DesignOps.sanitize(
            DesignElement(
                id = o.str("id", ""),
                type = type,
                name = o.str("name", type.name),
                x = o.float("x", d.x), y = o.float("y", d.y),
                width = o.float("width", d.width), height = o.float("height", d.height),
                rotation = o.float("rotation", d.rotation),
                opacity = o.float("opacity", d.opacity),
                visible = o.bool("visible", d.visible),
                locked = o.bool("locked", d.locked),
                fillMode = o.enum("fillMode", d.fillMode),
                fillColor = o.color("fillColor", d.fillColor),
                fillColor2 = o.color("fillColor2", d.fillColor2),
                gradientAngle = o.float("gradientAngle", d.gradientAngle),
                strokeColor = o.color("strokeColor", d.strokeColor),
                strokeWidth = o.float("strokeWidth", d.strokeWidth),
                cornerRadius = o.float("cornerRadius", d.cornerRadius),
                shadowRadius = o.float("shadowRadius", d.shadowRadius),
                shadowColor = o.color("shadowColor", d.shadowColor),
                shadowDx = o.float("shadowDx", d.shadowDx),
                shadowDy = o.float("shadowDy", d.shadowDy),
                text = o.str("text", d.text),
                textColor = o.color("textColor", d.textColor),
                fontSize = o.float("fontSize", d.fontSize),
                font = o.enum("font", d.font),
                customFontPath = o.str("customFontPath", d.customFontPath),
                bold = o.bool("bold", d.bold),
                italic = o.bool("italic", d.italic),
                textAlign = o.enum("textAlign", d.textAlign),
                letterSpacing = o.float("letterSpacing", d.letterSpacing),
                timeFormat = o.str("timeFormat", d.timeFormat),
                dateFormat = o.str("dateFormat", d.dateFormat),
                iconName = o.str("iconName", d.iconName),
                iconColor = o.color("iconColor", d.iconColor),
                useAppIcon = o.bool("useAppIcon", d.useAppIcon),
                imagePath = o.str("imagePath", d.imagePath),
                imageFit = o.enum("imageFit", d.imageFit),
                action = o.enum("action", d.action),
                packageName = o.str("packageName", d.packageName)
            )
        )
    }

    /** Parses "#AARRGGBB", "#RRGGBB" or a raw number. */
    fun parseColor(v: Any?, fallback: Int): Int = when (v) {
        is Number -> v.toLong().toInt()
        is String -> {
            val s = v.trim().removePrefix("#")
            runCatching {
                when (s.length) {
                    6 -> (0xFF000000L or s.toLong(16)).toInt()
                    8 -> s.toLong(16).toInt()
                    else -> fallback
                }
            }.getOrDefault(fallback)
        }
        else -> fallback
    }

    private class Obj(val m: Map<*, *>) {
        fun str(k: String, def: String) = m[k] as? String ?: def
        fun float(k: String, def: Float): Float = (m[k] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: def
        fun int(k: String, def: Int): Int = (m[k] as? Number)?.toInt() ?: def
        fun long(k: String, def: Long): Long = (m[k] as? Number)?.toLong() ?: def
        fun bool(k: String, def: Boolean): Boolean = m[k] as? Boolean ?: def
        fun color(k: String, def: Int): Int = parseColor(m[k], def)
        inline fun <reified E : Enum<E>> enumOrNull(k: String): E? =
            (m[k] as? String)?.let { s -> enumValues<E>().firstOrNull { it.name == s } }
        inline fun <reified E : Enum<E>> enum(k: String, def: E): E = enumOrNull<E>(k) ?: def
    }

    // ── Minimal JSON writer ────────────────────────────────────────────────

    private fun write(sb: StringBuilder, v: Any?, indent: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Float -> sb.append(num(v.toDouble()))
            is Double -> sb.append(num(v))
            is Number -> sb.append(v.toLong())
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    newline(sb, indent + 1)
                    quote(sb, k.toString())
                    sb.append(if (indent >= 0) ": " else ":")
                    write(sb, value, if (indent >= 0) indent + 1 else -1)
                }
                newline(sb, indent)
                sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                v.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    newline(sb, indent + 1)
                    write(sb, item, if (indent >= 0) indent + 1 else -1)
                }
                newline(sb, indent)
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun newline(sb: StringBuilder, indent: Int) {
        if (indent < 0) return
        sb.append('\n')
        repeat(indent) { sb.append("  ") }
    }

    private fun num(d: Double): String {
        if (!d.isFinite()) return "0"
        val r = Math.round(d * 1000.0) / 1000.0
        return if (r == Math.rint(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ── Minimal JSON parser ────────────────────────────────────────────────

    class JsonParser(private val s: String) {
        private var i = 0

        fun parse(): Any? {
            skipWs()
            val v = value()
            skipWs()
            if (i != s.length) throw DesignFormatException("Trailing data at $i")
            return v
        }

        private fun value(): Any? {
            skipWs()
            if (i >= s.length) throw DesignFormatException("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw DesignFormatException("Unexpected '$c' at $i")
            }
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (peek() == '}') { i++; return m }
            while (true) {
                skipWs()
                if (peek() != '"') throw DesignFormatException("Expected key at $i")
                val k = str()
                skipWs()
                expect(':')
                m[k] = value()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw DesignFormatException("Expected , or } at $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (peek() == ']') { i++; return l }
            while (true) {
                l += value()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw DesignFormatException("Expected , or ] at $i")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw DesignFormatException("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw DesignFormatException("Bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw DesignFormatException("Bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw DesignFormatException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Number {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw DesignFormatException("Expected $word at $i")
            i += word.length
            return v
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        private fun expect(c: Char) {
            if (peek() != c) throw DesignFormatException("Expected '$c' at $i")
            i++
        }
        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
    }
}
