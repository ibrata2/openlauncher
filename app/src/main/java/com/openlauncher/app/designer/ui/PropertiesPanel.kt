package com.openlauncher.app.designer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.designer.DesignerViewModel
import com.openlauncher.app.designer.model.Alignment as Align
import com.openlauncher.app.designer.model.DesignElement
import com.openlauncher.app.designer.model.ElementAction
import com.openlauncher.app.designer.model.ElementType
import com.openlauncher.app.designer.model.FillMode
import com.openlauncher.app.designer.model.FontChoice
import com.openlauncher.app.designer.model.ImageFit
import com.openlauncher.app.designer.model.LayerMove
import com.openlauncher.app.designer.model.TextAlignment
import com.openlauncher.app.designer.render.DesignIcons
import kotlin.math.roundToInt

/** Every editable property of the selected element. */
@Composable
fun PropertiesPanel(
    el: DesignElement?,
    vm: DesignerViewModel,
    keepAspect: Boolean,
    onKeepAspect: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onPickFont: () -> Unit,
    onPickIcon: () -> Unit,
    onPickApp: () -> Unit
) {
    if (el == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("لا يوجد عنصر محدد", color = DesignerColors.Text, fontSize = 15.sp)
            Text(
                "• اضغط على عنصر في اللوحة لتحديده\n• اسحبه لتحريكه، واسحب المقابض البيضاء لتغيير الحجم\n" +
                    "• المقبض العلوي للتدوير\n• إصبعان على عنصر محدد = تكبير/تصغير\n• إصبعان على الفراغ أو عجلة الماوس = تقريب اللوحة",
                color = DesignerColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        return
    }
    val id = el.id
    fun upd(key: String?, t: (DesignElement) -> DesignElement) = vm.updateElement(id, key, t)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {

        // ── Quick actions ──────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallIconButton(Icons.Default.ContentCopy, "تكرار") { vm.duplicateSelected() }
            SmallIconButton(Icons.Default.FlipToFront, "للأمام") { vm.moveLayer(id, LayerMove.FORWARD) }
            SmallIconButton(Icons.Default.FlipToBack, "للخلف") { vm.moveLayer(id, LayerMove.BACKWARD) }
            SmallIconButton(if (el.locked) Icons.Default.Lock else Icons.Default.LockOpen, "قفل") { vm.toggleLocked(id) }
            SmallIconButton(if (el.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "إظهار") { vm.toggleVisible(id) }
            SmallIconButton(Icons.Default.Delete, "حذف", tint = DesignerColors.Danger) { vm.deleteSelected() }
        }

        PanelSection("الاسم") {
            TextInput("", el.name) { vm.renameElement(id, it) }
        }

        // ── Geometry ───────────────────────────────────────────────────
        PanelSection("الموضع والحجم") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("X", el.x, Modifier.weight(1f)) { v -> upd("x") { it.copy(x = v) } }
                NumberField("Y", el.y, Modifier.weight(1f)) { v -> upd("y") { it.copy(y = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("العرض", el.width, Modifier.weight(1f)) { v ->
                    upd("w") { if (keepAspect && it.width > 0) it.copy(width = v, height = v * it.height / it.width) else it.copy(width = v) }
                }
                NumberField("الارتفاع", el.height, Modifier.weight(1f)) { v ->
                    upd("h") { if (keepAspect && it.height > 0) it.copy(height = v, width = v * it.width / it.height) else it.copy(height = v) }
                }
            }
            SwitchRow("الحفاظ على النسبة", keepAspect, onKeepAspect)
            SliderRow("التدوير", el.rotation, 0f..359f, { "${it.roundToInt()}°" }) { v -> upd("rot") { it.copy(rotation = v.roundToInt().toFloat()) } }
            SliderRow("الشفافية", el.opacity, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> upd("opacity") { it.copy(opacity = v) } }
            Text("محاذاة على اللوحة", color = DesignerColors.TextMuted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallIconButton(Icons.Default.AlignHorizontalLeft, "يسار") { vm.align(Align.LEFT) }
                SmallIconButton(Icons.Default.AlignHorizontalCenter, "وسط أفقي") { vm.align(Align.H_CENTER) }
                SmallIconButton(Icons.Default.AlignHorizontalRight, "يمين") { vm.align(Align.RIGHT) }
                SmallIconButton(Icons.Default.AlignVerticalTop, "أعلى") { vm.align(Align.TOP) }
                SmallIconButton(Icons.Default.AlignVerticalCenter, "وسط عمودي") { vm.align(Align.V_CENTER) }
                SmallIconButton(Icons.Default.AlignVerticalBottom, "أسفل") { vm.align(Align.BOTTOM) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SmallIconButton(Icons.Default.KeyboardArrowLeft, "تحريك يسار") { vm.nudge(-1f, 0f) }
                SmallIconButton(Icons.Default.KeyboardArrowUp, "تحريك أعلى") { vm.nudge(0f, -1f) }
                SmallIconButton(Icons.Default.KeyboardArrowDown, "تحريك أسفل") { vm.nudge(0f, 1f) }
                SmallIconButton(Icons.Default.KeyboardArrowRight, "تحريك يمين") { vm.nudge(1f, 0f) }
            }
        }

        // ── Content per type ───────────────────────────────────────────
        when (el.type) {
            ElementType.TEXT -> PanelSection("النص") {
                TextInput("المحتوى", el.text, singleLine = false) { v -> upd("text") { it.copy(text = v) } }
            }
            ElementType.CLOCK -> PanelSection("الساعة") {
                ChoiceChips(
                    listOf("HH:mm" to "24 ساعة", "hh:mm" to "12 ساعة", "hh:mm a" to "12 + ص/م", "HH:mm:ss" to "بالثواني"),
                    el.timeFormat
                ) { f -> upd(null) { it.copy(timeFormat = f) } }
                TextInput("صيغة مخصصة", el.timeFormat) { v -> upd("tf") { it.copy(timeFormat = v) } }
            }
            ElementType.DATE -> PanelSection("التاريخ") {
                ChoiceChips(
                    listOf("EEEE d MMMM" to "الجمعة 24 أكتوبر", "d/M/yyyy" to "24/10/2026", "EEE, d MMM" to "مختصر", "EEEE" to "اليوم فقط"),
                    el.dateFormat
                ) { f -> upd(null) { it.copy(dateFormat = f) } }
                TextInput("صيغة مخصصة", el.dateFormat) { v -> upd("df") { it.copy(dateFormat = v) } }
            }
            ElementType.IMAGE -> PanelSection("الصورة") {
                OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Image, null); Text("  اختيار صورة من الجهاز")
                }
                ChoiceChips(
                    listOf(ImageFit.COVER to "ملء (قص)", ImageFit.CONTAIN to "احتواء", ImageFit.STRETCH to "تمديد"),
                    el.imageFit
                ) { f -> upd(null) { it.copy(imageFit = f) } }
            }
            ElementType.ICON -> PanelSection("الأيقونة") {
                IconChooserRow(el.iconName, onPickIcon)
                ColorRow("لون الأيقونة", el.iconColor) { c -> upd("iconColor") { it.copy(iconColor = c) } }
            }
            ElementType.SHORTCUT -> PanelSection("اختصار التطبيق") {
                TextInput("النص تحت الأيقونة", el.text) { v -> upd("text") { it.copy(text = v) } }
                ChoiceChips(
                    listOf(
                        ElementAction.LAUNCH_APP to "فتح تطبيق",
                        ElementAction.OPEN_APP_LIBRARY to "قائمة التطبيقات",
                        ElementAction.OPEN_SETTINGS to "الإعدادات",
                        ElementAction.OPEN_DESIGNER to "المحرر",
                        ElementAction.OPEN_CLASSIC_HOME to "الواجهة الكلاسيكية",
                        ElementAction.NONE to "بدون"
                    ),
                    el.action
                ) { a -> upd(null) { it.copy(action = a) } }
                if (el.action == ElementAction.LAUNCH_APP) {
                    OutlinedButton(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Apps, null)
                        Text("  " + (el.packageName.ifEmpty { "اختيار التطبيق" }), maxLines = 1)
                    }
                    SwitchRow("استخدام أيقونة التطبيق الأصلية", el.useAppIcon) { v -> upd(null) { it.copy(useAppIcon = v) } }
                }
                IconChooserRow(el.iconName, onPickIcon)
                ColorRow("لون الأيقونة", el.iconColor) { c -> upd("iconColor") { it.copy(iconColor = c) } }
            }
            else -> Unit
        }

        // Tap action for non-shortcut elements (run mode)
        if (el.type != ElementType.SHORTCUT) {
            PanelSection("عند الضغط (في وضع التشغيل)") {
                ChoiceChips(
                    listOf(
                        ElementAction.NONE to "لا شيء",
                        ElementAction.LAUNCH_APP to "فتح تطبيق",
                        ElementAction.OPEN_APP_LIBRARY to "التطبيقات",
                        ElementAction.OPEN_SETTINGS to "الإعدادات",
                        ElementAction.OPEN_DESIGNER to "المحرر"
                    ),
                    el.action
                ) { a -> upd(null) { it.copy(action = a) } }
                if (el.action == ElementAction.LAUNCH_APP) {
                    OutlinedButton(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
                        Text(el.packageName.ifEmpty { "اختيار التطبيق" }, maxLines = 1)
                    }
                }
            }
        }

        // ── Typography ─────────────────────────────────────────────────
        if (el.type in setOf(ElementType.TEXT, ElementType.CLOCK, ElementType.DATE, ElementType.SHORTCUT)) {
            PanelSection("الخط") {
                ColorRow("لون النص", el.textColor) { c -> upd("textColor") { it.copy(textColor = c) } }
                SliderRow("الحجم", el.fontSize, 6f..300f, { "${it.roundToInt()} px" }) { v -> upd("fontSize") { it.copy(fontSize = v.roundToInt().toFloat()) } }
                ChoiceChips(
                    listOf(
                        FontChoice.SYSTEM_SANS to "النظام",
                        FontChoice.SYSTEM_SERIF to "Serif",
                        FontChoice.SYSTEM_MONO to "Mono",
                        FontChoice.JETBRAINS_MONO to "JetBrains",
                        FontChoice.SOURCE_CODE_PRO to "Source Code",
                        FontChoice.CUSTOM to "خط مستورد"
                    ),
                    el.font
                ) { f -> if (f == FontChoice.CUSTOM && el.customFontPath.isEmpty()) onPickFont() else upd(null) { it.copy(font = f) } }
                OutlinedButton(onClick = onPickFont, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FontDownload, null); Text("  استيراد خط TTF / OTF")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChips(
                        listOf(TextAlignment.START to "بداية", TextAlignment.CENTER to "وسط", TextAlignment.END to "نهاية"),
                        el.textAlign
                    ) { a -> upd(null) { it.copy(textAlign = a) } }
                }
                SwitchRow("عريض", el.bold) { v -> upd(null) { it.copy(bold = v) } }
                SwitchRow("مائل", el.italic) { v -> upd(null) { it.copy(italic = v) } }
                SliderRow("تباعد الحروف", el.letterSpacing, -0.2f..1f, { String.format(java.util.Locale.US, "%.2f", it) }) { v ->
                    upd("ls") { it.copy(letterSpacing = (v * 100).roundToInt() / 100f) }
                }
            }
        }

        // ── Fill & stroke ──────────────────────────────────────────────
        PanelSection(if (el.type == ElementType.LINE) "لون الخط" else "التعبئة والإطار") {
            val fillOptions = if (el.type == ElementType.LINE)
                listOf(FillMode.SOLID to "لون", FillMode.GRADIENT to "تدرج")
            else listOf(FillMode.NONE to "بدون", FillMode.SOLID to "لون", FillMode.GRADIENT to "تدرج")
            ChoiceChips(fillOptions, el.fillMode) { m -> upd(null) { it.copy(fillMode = m) } }
            if (el.fillMode != FillMode.NONE || el.type == ElementType.LINE) {
                ColorRow(if (el.fillMode == FillMode.GRADIENT) "اللون الأول" else "لون التعبئة", el.fillColor) { c -> upd("fill") { it.copy(fillColor = c) } }
            }
            if (el.fillMode == FillMode.GRADIENT) {
                ColorRow("اللون الثاني", el.fillColor2) { c -> upd("fill2") { it.copy(fillColor2 = c) } }
                SliderRow("زاوية التدرج", el.gradientAngle, 0f..359f, { "${it.roundToInt()}°" }) { v -> upd("gangle") { it.copy(gradientAngle = v.roundToInt().toFloat()) } }
            }
            if (el.type != ElementType.LINE) {
                SliderRow("سماكة الإطار", el.strokeWidth, 0f..30f, { "${it.roundToInt()} px" }) { v -> upd("sw") { it.copy(strokeWidth = (v * 2).roundToInt() / 2f) } }
                if (el.strokeWidth > 0f) ColorRow("لون الإطار", el.strokeColor) { c -> upd("stroke") { it.copy(strokeColor = c) } }
                if (el.type != ElementType.ELLIPSE) {
                    SliderRow("استدارة الزوايا", el.cornerRadius, 0f..300f, { "${it.roundToInt()} px" }) { v -> upd("radius") { it.copy(cornerRadius = v.roundToInt().toFloat()) } }
                }
            }
        }

        PanelSection("الظل والتوهج") {
            SliderRow("قوة الظل", el.shadowRadius, 0f..60f, { "${it.roundToInt()}" }) { v -> upd("shadow") { it.copy(shadowRadius = v.roundToInt().toFloat()) } }
            if (el.shadowRadius > 0f) {
                ColorRow("لون الظل", el.shadowColor) { c -> upd("shadowColor") { it.copy(shadowColor = c) } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("إزاحة X", el.shadowDx, Modifier.weight(1f)) { v -> upd("sdx") { it.copy(shadowDx = v) } }
                    NumberField("إزاحة Y", el.shadowDy, Modifier.weight(1f)) { v -> upd("sdy") { it.copy(shadowDy = v) } }
                }
            }
        }
    }
}

@Composable
private fun IconChooserRow(iconName: String, onPick: () -> Unit) {
    val def = DesignIcons.find(iconName)
    OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
        if (def != null) Icon(def.vector, null)
        Text("  " + (def?.label ?: "اختيار أيقونة"))
    }
}
