package com.openlauncher.app.designer.model

/** Built-in starting points, all laid out for a 1024×600 head unit panel. */
object DesignTemplates {

    data class Template(val key: String, val title: String, val build: () -> DesignProject)

    val all: List<Template> by lazy {
        listOf(
            Template("blank", "فارغ 1024×600") { blank() },
            Template("classic", "داكن كلاسيكي") { classicDark() },
            Template("gold", "ذهبي فاخر") { gold() },
            Template("neon", "نيون أزرق") { neon() }
        )
    }

    fun byKey(key: String): DesignProject = (all.firstOrNull { it.key == key } ?: all.first()).build()

    private fun base(name: String, bg: DesignBackground) = DesignOps.touch(
        DesignProject(id = DesignOps.newId("design"), name = name, background = bg)
    )

    private fun el(
        type: ElementType, name: String, x: Float, y: Float, w: Float, h: Float,
        block: DesignElement.() -> DesignElement = { this }
    ) = DesignElement(id = DesignOps.newId(), type = type, name = name, x = x, y = y, width = w, height = h).block()

    fun blank(): DesignProject = base("تصميم جديد", DesignBackground(color = 0xFF000000.toInt()))

    private fun shortcutRow(
        accent: Int, tile: Int, text: Int, y: Float, radius: Float, stroke: Float
    ): List<DesignElement> {
        val items = listOf(
            Triple("الراديو", "radio", "Radio"),
            Triple("الموسيقى", "music", "Music"),
            Triple("الهاتف", "phone", "Phone"),
            Triple("الخرائط", "map", "Maps"),
            Triple("الكاميرا", "camera", "Camera"),
            Triple("التطبيقات", "apps", "Apps")
        )
        val w = 140f; val gap = 24f
        val total = items.size * w + (items.size - 1) * gap
        val startX = (DEFAULT_CANVAS_W - total) / 2f
        return items.mapIndexed { i, (label, icon, en) ->
            el(ElementType.SHORTCUT, "اختصار $en", startX + i * (w + gap), y, w, 130f) {
                copy(
                    fillColor = tile, cornerRadius = radius, strokeColor = accent, strokeWidth = stroke,
                    iconName = icon, iconColor = accent, text = label, textColor = text, fontSize = 18f,
                    action = if (icon == "apps") ElementAction.OPEN_APP_LIBRARY else ElementAction.LAUNCH_APP
                )
            }
        }
    }

    fun classicDark(): DesignProject {
        val accent = 0xFF4FC3F7.toInt()
        return base(
            "داكن كلاسيكي",
            DesignBackground(type = BackgroundType.GRADIENT, color = 0xFF0B0F14.toInt(), color2 = 0xFF1B2430.toInt(), gradientAngle = 90f)
        ).copy(
            elements = listOf(
                el(ElementType.CLOCK, "الساعة", 40f, 40f, 420f, 150f) {
                    copy(fontSize = 120f, bold = true, textAlign = TextAlignment.START, fillMode = FillMode.NONE)
                },
                el(ElementType.DATE, "التاريخ", 46f, 190f, 420f, 44f) {
                    copy(fontSize = 28f, textColor = 0xFF9FB3C8.toInt(), textAlign = TextAlignment.START, fillMode = FillMode.NONE)
                },
                el(ElementType.RECT, "بطاقة جانبية", 560f, 40f, 424f, 200f) {
                    copy(fillColor = 0x33FFFFFF, cornerRadius = 24f, strokeColor = 0x44FFFFFF, strokeWidth = 1f)
                },
                el(ElementType.TEXT, "اسم السيارة", 580f, 70f, 384f, 50f) {
                    copy(text = "PIONEER Y6", fontSize = 34f, bold = true, textColor = accent, fillMode = FillMode.NONE)
                },
                el(ElementType.TEXT, "شعار فرعي", 580f, 130f, 384f, 80f) {
                    copy(text = "رحلة سعيدة", fontSize = 30f, textColor = 0xFFE0E0E0.toInt(), fillMode = FillMode.NONE)
                },
                el(ElementType.LINE, "فاصل", 40f, 290f, 944f, 2f) { copy(fillColor = 0x33FFFFFF) }
            ) + shortcutRow(accent, 0xFF16202B.toInt(), 0xFFFFFFFF.toInt(), 360f, 28f, 1.5f)
        )
    }

    fun gold(): DesignProject {
        val gold = 0xFFD4AF37.toInt()
        return base(
            "ذهبي فاخر",
            DesignBackground(type = BackgroundType.GRADIENT, color = 0xFF000000.toInt(), color2 = 0xFF1A1408.toInt(), gradientAngle = 135f)
        ).copy(
            elements = listOf(
                el(ElementType.ELLIPSE, "هالة", 352f, -140f, 320f, 320f) {
                    copy(fillMode = FillMode.GRADIENT, fillColor = 0x55D4AF37, fillColor2 = 0x00000000, gradientAngle = 90f)
                },
                el(ElementType.CLOCK, "الساعة", 262f, 40f, 500f, 150f) {
                    copy(fontSize = 128f, textColor = gold, font = FontChoice.SYSTEM_SERIF, fillMode = FillMode.NONE,
                        shadowRadius = 12f, shadowColor = 0x88D4AF37.toInt(), shadowDy = 0f)
                },
                el(ElementType.DATE, "التاريخ", 262f, 192f, 500f, 40f) {
                    copy(fontSize = 26f, textColor = 0xFFBFA76A.toInt(), fillMode = FillMode.NONE)
                },
                el(ElementType.LINE, "خط ذهبي", 312f, 262f, 400f, 2f) {
                    copy(fillMode = FillMode.GRADIENT, fillColor = 0x00D4AF37, fillColor2 = gold, gradientAngle = 0f)
                }
            ) + shortcutRow(gold, 0xFF0D0D0D.toInt(), 0xFFF5E6B8.toInt(), 330f, 70f, 2f)
        )
    }

    fun neon(): DesignProject {
        val cyan = 0xFF00E5FF.toInt()
        val pink = 0xFFFF2BD6.toInt()
        return base("نيون أزرق", DesignBackground(color = 0xFF05010F.toInt())).copy(
            elements = listOf(
                el(ElementType.RECT, "إطار", 24f, 24f, 976f, 552f) {
                    copy(fillMode = FillMode.NONE, strokeColor = cyan, strokeWidth = 3f, cornerRadius = 32f,
                        shadowRadius = 16f, shadowColor = 0xAA00E5FF.toInt(), shadowDy = 0f)
                },
                el(ElementType.CLOCK, "الساعة", 60f, 60f, 460f, 150f) {
                    copy(fontSize = 120f, bold = true, textColor = cyan, textAlign = TextAlignment.START, fillMode = FillMode.NONE,
                        shadowRadius = 18f, shadowColor = 0xCC00E5FF.toInt(), shadowDy = 0f, font = FontChoice.SYSTEM_MONO)
                },
                el(ElementType.DATE, "التاريخ", 66f, 205f, 460f, 40f) {
                    copy(fontSize = 26f, textColor = pink, textAlign = TextAlignment.START, fillMode = FillMode.NONE)
                },
                el(ElementType.ICON, "أيقونة سيارة", 800f, 70f, 150f, 150f) {
                    copy(iconName = "car", iconColor = pink, fillMode = FillMode.NONE,
                        shadowRadius = 14f, shadowColor = 0xCCFF2BD6.toInt(), shadowDy = 0f)
                }
            ) + shortcutRow(cyan, 0x2200E5FF, 0xFFFFFFFF.toInt(), 360f, 20f, 2f)
        )
    }
}
