package com.openlauncher.app.designer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.designer.DesignerViewModel
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.model.ElementType
import com.openlauncher.app.designer.model.LayerMove
import kotlin.math.roundToInt

fun ElementType.icon(): ImageVector = when (this) {
    ElementType.TEXT -> Icons.Default.TextFields
    ElementType.RECT -> Icons.Default.CropSquare
    ElementType.ELLIPSE -> Icons.Default.Circle
    ElementType.LINE -> Icons.Default.HorizontalRule
    ElementType.IMAGE -> Icons.Default.Image
    ElementType.ICON -> Icons.Default.Star
    ElementType.CLOCK -> Icons.Default.Schedule
    ElementType.DATE -> Icons.Default.CalendarToday
    ElementType.SHORTCUT -> Icons.Default.Apps
}

/**
 * Layer list, topmost first (like Photoshop). Tap = select, eye = show/hide,
 * lock = lock, arrows = move up/down, long-press + drag = reorder.
 */
@Composable
fun LayersPanel(project: DesignProject, selectedId: String?, vm: DesignerViewModel) {
    val rowHeightDp = 46.dp
    val rowPx = with(LocalDensity.current) { rowHeightDp.toPx() }
    // Display order is reversed: row 0 = top layer = last element
    val ordered = project.elements.asReversed()
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentProject by rememberUpdatedState(project)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("الطبقات (${project.elements.size})", color = DesignerColors.Accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("اضغط مطولاً واسحب لإعادة الترتيب", color = DesignerColors.TextMuted, fontSize = 9.sp)
        }
        if (ordered.isEmpty()) {
            Text("لا توجد عناصر بعد — أضف عنصراً من شريط الأدوات", color = DesignerColors.TextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(16.dp))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ordered.forEachIndexed { row, el ->
                val selected = el.id == selectedId
                val isDragging = row == dragIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 12f else 0f
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isDragging -> DesignerColors.Accent.copy(alpha = 0.35f)
                                selected -> DesignerColors.Accent.copy(alpha = 0.22f)
                                else -> DesignerColors.PanelAlt
                            }
                        )
                        .clickable { vm.select(el.id) }
                        .pointerInput(el.id, row) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragIndex = row; dragOffset = 0f; vm.select(el.id) },
                                onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                                onDragEnd = {
                                    val p = currentProject
                                    val fromRow = dragIndex
                                    val toRow = (fromRow + (dragOffset / rowPx).roundToInt()).coerceIn(0, p.elements.lastIndex)
                                    if (fromRow >= 0 && toRow != fromRow) {
                                        // rows are reversed z-indices
                                        val n = p.elements.size
                                        vm.reorderLayer(n - 1 - fromRow, n - 1 - toRow)
                                    }
                                    dragIndex = -1; dragOffset = 0f
                                },
                                onDragCancel = { dragIndex = -1; dragOffset = 0f }
                            )
                        }
                        .padding(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.DragIndicator, null, tint = DesignerColors.TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Icon(el.type.icon(), null, tint = if (el.visible) DesignerColors.Text else DesignerColors.TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        el.name, color = if (el.visible) DesignerColors.Text else DesignerColors.TextMuted,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            SmallIconButton(Icons.Default.KeyboardArrowUp, "أعلى", enabled = row > 0) { vm.moveLayer(el.id, LayerMove.FORWARD) }
                            SmallIconButton(Icons.Default.KeyboardArrowDown, "أسفل", enabled = row < ordered.lastIndex) { vm.moveLayer(el.id, LayerMove.BACKWARD) }
                            SmallIconButton(
                                if (el.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "إظهار",
                                tint = if (el.visible) DesignerColors.Text else DesignerColors.TextMuted
                            ) { vm.toggleVisible(el.id) }
                            SmallIconButton(
                                if (el.locked) Icons.Default.Lock else Icons.Default.LockOpen, "قفل",
                                tint = if (el.locked) DesignerColors.Accent else DesignerColors.TextMuted
                            ) { vm.toggleLocked(el.id) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
