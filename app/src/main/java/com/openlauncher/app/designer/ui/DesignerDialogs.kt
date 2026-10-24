package com.openlauncher.app.designer.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.designer.DesignerViewModel
import com.openlauncher.app.designer.data.DesignRepository
import com.openlauncher.app.designer.data.DeviceCheck
import com.openlauncher.app.designer.model.DesignTemplates
import com.openlauncher.app.designer.render.DesignIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
private fun DarkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit = { TextButton(onClick = onDismiss) { Text("إغلاق", color = DesignerColors.Accent) } },
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignerColors.Panel,
        titleContentColor = DesignerColors.Text,
        textContentColor = DesignerColors.Text,
        title = { Text(title, fontSize = 18.sp) },
        text = content,
        confirmButton = confirm,
        dismissButton = dismiss
    )
}

// ── Projects ───────────────────────────────────────────────────────────────

@Composable
fun ProjectsDialog(
    projects: List<DesignRepository.Summary>,
    currentId: String?,
    activeHomeId: String?,
    vm: DesignerViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf<DesignRepository.Summary?>(null) }
    var showTemplates by remember { mutableStateOf(false) }

    DarkDialog("مشاريع التصميم", onDismiss) {
        Column(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogAction(Icons.Default.Add, "جديد") { showTemplates = true }
                DialogAction(Icons.Default.FolderOpen, "استيراد .oltheme") { onImport() }
                DialogAction(Icons.Default.Save, "تصدير الحالي") { onExport() }
            }
            if (projects.isEmpty()) Text("لا توجد مشاريع محفوظة بعد", color = DesignerColors.TextMuted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(projects, key = { it.id }) { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (s.id == currentId) DesignerColors.Accent.copy(alpha = 0.18f) else DesignerColors.PanelAlt)
                            .clickable { vm.openProject(s.id); onDismiss() }
                            .padding(8.dp)
                    ) {
                        Thumb(s.thumb, s.updatedAt, Modifier.width(128.dp).aspectRatio(1024f / 600f))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = DesignerColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${s.elementCount} عنصر · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.updatedAt)),
                                color = DesignerColors.TextMuted, fontSize = 11.sp
                            )
                            if (s.id == activeHomeId) Text("● الواجهة الرئيسية الحالية", color = Color(0xFF81C784), fontSize = 11.sp)
                        }
                        SmallIconButton(Icons.Default.ContentCopy, "نسخ") { vm.duplicateProject(s.id) }
                        SmallIconButton(Icons.Default.Delete, "حذف", tint = DesignerColors.Danger) { confirmDelete = s }
                    }
                }
            }
        }
    }

    confirmDelete?.let { s ->
        DarkDialog(
            "حذف التصميم؟",
            onDismiss = { confirmDelete = null },
            confirm = {
                TextButton(onClick = { vm.deleteProject(s.id); confirmDelete = null }) { Text("حذف نهائي", color = DesignerColors.Danger) }
            },
            dismiss = { TextButton(onClick = { confirmDelete = null }) { Text("إلغاء", color = DesignerColors.TextMuted) } }
        ) { Text("سيتم حذف \"${s.name}\" وصوره وخطوطه. لا يمكن التراجع.") }
    }

    if (showTemplates) {
        TemplatesDialog(onPick = { key -> vm.newProject(key); showTemplates = false; onDismiss() }, onDismiss = { showTemplates = false })
    }
}

@Composable
private fun DialogAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, DesignerColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, tint = DesignerColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = DesignerColors.Text, fontSize = 12.sp)
    }
}

@Composable
private fun Thumb(file: java.io.File?, version: Long, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, file, version) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.Image, null, tint = DesignerColors.TextMuted)
    }
}

@Composable
fun TemplatesDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    DarkDialog("تصميم جديد من قالب", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesignTemplates.all.forEach { t ->
                Text(
                    t.title, color = DesignerColors.Text, fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DesignerColors.PanelAlt)
                        .clickable { onPick(t.key) }
                        .padding(14.dp)
                )
            }
        }
    }
}

// ── Icon picker ────────────────────────────────────────────────────────────

@Composable
fun IconPickerDialog(current: String, color: Color, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    DarkDialog("اختر أيقونة", onDismiss) {
        LazyVerticalGrid(GridCells.Adaptive(72.dp), modifier = Modifier.heightIn(max = 420.dp)) {
            items(DesignIcons.all, key = { it.name }) { def ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (def.name == current) DesignerColors.Accent.copy(alpha = 0.25f) else DesignerColors.PanelAlt)
                        .clickable { onPick(def.name) }
                        .padding(8.dp)
                ) {
                    Icon(def.vector, def.label, tint = color, modifier = Modifier.size(30.dp))
                    Text(def.label, color = DesignerColors.TextMuted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

// ── App picker ─────────────────────────────────────────────────────────────

@Composable
fun AppPickerDialog(apps: List<DesignerViewModel.AppEntry>, onPick: (DesignerViewModel.AppEntry) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    DarkDialog("اختر التطبيق", onDismiss) {
        Column(Modifier.heightIn(max = 460.dp)) {
            TextInput("بحث", query) { query = it }
            Spacer(Modifier.size(8.dp))
            if (apps.isEmpty()) Text("جاري تحميل التطبيقات…", color = DesignerColors.TextMuted)
            val filtered = apps.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(app.label, color = DesignerColors.Text, fontSize = 14.sp)
                        Text(app.packageName, color = DesignerColors.TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ── Device check ───────────────────────────────────────────────────────────

@Composable
fun DeviceCheckDialog(report: DeviceCheck.Report, onDismiss: () -> Unit) {
    DarkDialog("فحص الجهاز", onDismiss) {
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(report.items) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DesignerColors.PanelAlt)
                        .padding(10.dp)
                ) {
                    val (icon, tint) = when (item.status) {
                        DeviceCheck.Status.OK -> Icons.Default.CheckCircle to Color(0xFF81C784)
                        DeviceCheck.Status.WARN -> Icons.Default.Warning to Color(0xFFFFB74D)
                        DeviceCheck.Status.INFO -> Icons.Default.Info to DesignerColors.TextMuted
                    }
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(item.label, color = DesignerColors.TextMuted, fontSize = 11.sp)
                        Text(item.value, color = DesignerColors.Text, fontSize = 13.sp)
                    }
                }
            }
            if (!report.isDefaultHome) {
                item {
                    Text(
                        "لتشغيل التصميم عند تشغيل السيارة: اضغط زر الرئيسية واختر Open Launcher ثم \"دائماً\"، " +
                            "أو من إعدادات الجهاز ← التطبيقات ← التطبيقات الافتراضية ← تطبيق الشاشة الرئيسية. " +
                            "بعض الشاشات الصينية تقفل الواجهة الافتراضية من إعدادات المصنع (كلمة السر غالباً 8888 أو 1234 أو 0000 حسب الجهاز).",
                        color = Color(0xFFFFB74D), fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
