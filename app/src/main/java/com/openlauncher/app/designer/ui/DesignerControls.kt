package com.openlauncher.app.designer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.ui.components.ColorPickerDialog
import java.util.Locale
import kotlin.math.roundToInt

/** Fixed dark palette for the editor chrome (independent of the launcher theme). */
object DesignerColors {
    val Background = Color(0xFF121212)
    val Panel = Color(0xFF1A1A1A)
    val PanelAlt = Color(0xFF222222)
    val Field = Color(0xFF2A2A2A)
    val Border = Color(0xFF333333)
    val Text = Color(0xFFEDEDED)
    val TextMuted = Color(0xFF9A9A9A)
    val Accent = Color(0xFFD4AF37)
    val Danger = Color(0xFFE57373)
}

@Composable
fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DesignerColors.PanelAlt)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = DesignerColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun FieldBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DesignerColors.Field)
            .border(1.dp, DesignerColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

private fun fmt(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v)

/**
 * Numeric text field that commits on Done / focus loss (and live while typing
 * when the text parses). Shows the label above.
 */
@Composable
fun NumberField(label: String, value: Float, modifier: Modifier = Modifier, onValue: (Float) -> Unit) {
    var text by remember { mutableStateOf(fmt(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = fmt(value) }
    val focus = LocalFocusManager.current
    Column(modifier = modifier) {
        Text(label, color = DesignerColors.TextMuted, fontSize = 10.sp)
        FieldBox(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = text,
                onValueChange = { t ->
                    text = t.filter { it.isDigit() || it == '.' || it == '-' }.take(8)
                    text.toFloatOrNull()?.let(onValue)
                },
                singleLine = true,
                textStyle = TextStyle(color = DesignerColors.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(DesignerColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) text = fmt(value)
                    }
            )
        }
    }
}

@Composable
fun TextInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onValue: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value }
    val focus = LocalFocusManager.current
    Column(modifier = modifier) {
        if (label.isNotEmpty()) Text(label, color = DesignerColors.TextMuted, fontSize = 10.sp)
        FieldBox(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onValue(it) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                maxLines = if (singleLine) 1 else 5,
                textStyle = TextStyle(color = DesignerColors.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(DesignerColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String = { fmt(it) },
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DesignerColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(display(value), color = DesignerColors.Text, fontSize = 11.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = DesignerColors.Accent,
                activeTrackColor = DesignerColors.Accent,
                inactiveTrackColor = DesignerColors.Border
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }
    ) {
        Text(label, color = DesignerColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = DesignerColors.Accent,
                uncheckedTrackColor = DesignerColors.Field
            )
        )
    }
}

/** Horizontal list of choice chips. */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                label,
                color = if (active) Color.Black else DesignerColors.Text,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) DesignerColors.Accent else DesignerColors.Field)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Color swatch + opacity slider. Tapping the swatch opens the launcher's existing
 * HSV color picker; alpha is kept separately so translucent "glass" fills work.
 */
@Composable
fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val alpha = (color ushr 24) and 0xFF
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DesignerColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                String.format(Locale.US, "#%08X", color),
                color = DesignerColors.TextMuted, fontSize = 10.sp
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(34.dp, 24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(checkerColor)
                    .background(Color(color))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clickable { showPicker = true }
            )
        }
        Slider(
            value = alpha / 255f,
            onValueChange = { a -> onChange(((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(color or 0xFF000000.toInt()),
                inactiveTrackColor = DesignerColors.Border
            ),
            modifier = Modifier.height(24.dp)
        )
    }
    if (showPicker) {
        ColorPickerDialog(
            title = label,
            initialColor = Color(color or 0xFF000000.toInt()),
            onColorSelected = { c ->
                val rgb = c.toArgb() and 0x00FFFFFF
                // Picking a colour on a fully transparent swatch should make it visible
                val a = if (alpha == 0) 0xFF else alpha
                onChange((a shl 24) or rgb)
            },
            onDismiss = { showPicker = false }
        )
    }
}

private val checkerColor = Color(0xFF555555)

@Composable
fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = DesignerColors.Text, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .width(52.dp)
    ) {
        Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
        Text(
            label, color = if (enabled) DesignerColors.TextMuted else DesignerColors.TextMuted.copy(alpha = 0.3f),
            fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SmallIconButton(icon: ImageVector, desc: String, tint: Color = DesignerColors.Text, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(19.dp))
    }
}
