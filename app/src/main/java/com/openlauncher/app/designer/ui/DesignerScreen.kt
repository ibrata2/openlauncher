package com.openlauncher.app.designer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlauncher.app.designer.DesignerViewModel
import com.openlauncher.app.designer.data.DeviceCheck
import com.openlauncher.app.designer.model.ElementAction
import com.openlauncher.app.designer.model.ElementType

/**
 * The theme editor: toolbar (top), element tools (start side), canvas (centre),
 * inspector with Properties / Layers / Canvas tabs (end side).
 * Laid out for a 1024×600 landscape head unit but scales up to tablets.
 */
@Composable
fun DesignerScreen(vm: DesignerViewModel, onExit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val project by vm.project.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val guides by vm.guides.collectAsStateWithLifecycle()
    val nowState = vm.now.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val assetRevision by vm.assetRevision.collectAsStateWithLifecycle()
    val activeHome by vm.activeDesignId.collectAsStateWithLifecycle()

    val viewport = remember { ViewportState() }
    var tab by remember { mutableIntStateOf(0) }
    var showProjects by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var deviceReport by remember { mutableStateOf<DeviceCheck.Report?>(null) }
    var imageTarget by remember { mutableStateOf(DesignerViewModel.ImageTarget.NEW_ELEMENT) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importImage(uri, imageTarget)
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importFont(uri)
    }
    val exportPkg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportPackage(uri)
    }
    val importPkg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPackage(uri)
    }

    val p = project
    if (p == null) {
        Box(Modifier.fillMaxSize().background(DesignerColors.Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesignerColors.Accent)
        }
        return
    }
    val selected = p.element(selectedId)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            Modifier
                .fillMaxSize()
                .background(DesignerColors.Background)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = if (e.isShiftPressed) 10f else 1f
                    when {
                        e.isCtrlPressed && e.key == Key.Z && e.isShiftPressed -> { vm.redo(); true }
                        e.isCtrlPressed && e.key == Key.Z -> { vm.undo(); true }
                        e.isCtrlPressed && e.key == Key.Y -> { vm.redo(); true }
                        e.isCtrlPressed && e.key == Key.S -> { vm.save(); true }
                        e.isCtrlPressed && e.key == Key.D -> { vm.duplicateSelected(); true }
                        e.key == Key.Delete -> { vm.deleteSelected(); true }
                        e.key == Key.Escape -> { vm.select(null); true }
                        e.key == Key.DirectionLeft -> { vm.nudge(-step, 0f); true }
                        e.key == Key.DirectionRight -> { vm.nudge(step, 0f); true }
                        e.key == Key.DirectionUp -> { vm.nudge(0f, -step); true }
                        e.key == Key.DirectionDown -> { vm.nudge(0f, step); true }
                        else -> false
                    }
                }
        ) {
            // ── Top bar ────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(DesignerColors.Panel).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onExit != null) SmallIconButton(Icons.Default.ArrowForward, "رجوع") { vm.saveIfDirty(); onExit() }
                Icon(Icons.Default.Brush, null, tint = DesignerColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name + if (dirty) " •" else "",
                        color = DesignerColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${p.width}×${p.height} · ${p.elements.size} عنصر · تكبير ${(viewport.zoom * 100).toInt()}%",
                        color = DesignerColors.TextMuted, fontSize = 10.sp, maxLines = 1)
                }
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = DesignerColors.Accent, strokeWidth = 2.dp)
                SmallIconButton(Icons.Default.FolderOpen, "المشاريع") { vm.refreshProjects(); showProjects = true }
                SmallIconButton(Icons.Default.Save, "حفظ", tint = if (dirty) DesignerColors.Accent else DesignerColors.Text) { vm.save() }
                SmallIconButton(Icons.Default.Undo, "تراجع", enabled = canUndo) { vm.undo() }
                SmallIconButton(Icons.Default.Redo, "إعادة", enabled = canRedo) { vm.redo() }
                SmallIconButton(Icons.Default.ZoomOutMap, "ملاءمة") { viewport.fit() }
                SmallIconButton(Icons.Default.CropFree, "100%") { viewport.actualPixels() }
                SmallIconButton(if (p.showGrid) Icons.Default.GridOn else Icons.Default.GridOff, "الشبكة") {
                    vm.updateProjectSettings { it.copy(showGrid = !it.showGrid) }
                }
                SmallIconButton(Icons.Default.PlayArrow, "معاينة") { showPreview = true }
                SmallIconButton(Icons.Default.Image, "PNG") { vm.exportPng(1f) }
                SmallIconButton(Icons.Default.Share, "مشاركة") { vm.sharePng { context.startActivity(it) } }
            }
            HorizontalDivider(color = DesignerColors.Border)

            Row(Modifier.fillMaxSize()) {
                // ── Element tools ──────────────────────────────────────
                Column(
                    Modifier.fillMaxHeight().width(62.dp).background(DesignerColors.Panel).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ToolButton(Icons.Default.TextFields, "نص") { vm.addElement(ElementType.TEXT); tab = 0 }
                    ToolButton(Icons.Default.CropSquare, "مستطيل") { vm.addElement(ElementType.RECT); tab = 0 }
                    ToolButton(Icons.Default.Circle, "دائرة") { vm.addElement(ElementType.ELLIPSE); tab = 0 }
                    ToolButton(Icons.Default.HorizontalRule, "خط") { vm.addElement(ElementType.LINE); tab = 0 }
                    ToolButton(Icons.Default.Image, "صورة") {
                        imageTarget = DesignerViewModel.ImageTarget.NEW_ELEMENT
                        pickImage.launch("image/*"); tab = 0
                    }
                    ToolButton(Icons.Default.Star, "أيقونة") { vm.addElement(ElementType.ICON); tab = 0 }
                    ToolButton(Icons.Default.Schedule, "ساعة") { vm.addElement(ElementType.CLOCK); tab = 0 }
                    ToolButton(Icons.Default.CalendarToday, "تاريخ") { vm.addElement(ElementType.DATE); tab = 0 }
                    ToolButton(Icons.Default.Apps, "اختصار") { vm.addElement(ElementType.SHORTCUT); tab = 0 }
                }
                VerticalDivider(color = DesignerColors.Border)

                // ── Canvas (always left-to-right maths) ────────────────
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    DesignCanvas(
                        project = p,
                        selectedId = selectedId,
                        guides = guides,
                        now = nowState,
                        assetRevision = assetRevision,
                        renderer = vm.renderer,
                        assetDir = vm.assetDir(),
                        viewport = viewport,
                        onSelect = { id -> vm.select(id); if (id != null) tab = 0 },
                        onBeginGesture = vm::beginGesture,
                        onGestureUpdate = { np, gx, gy -> vm.gestureUpdate(np, gx, gy) },
                        onEndGesture = vm::endGesture,
                        onCanvasTouched = { runCatching { focus.requestFocus() } },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                VerticalDivider(color = DesignerColors.Border)

                // ── Inspector ──────────────────────────────────────────
                Column(Modifier.fillMaxHeight().width(290.dp).background(DesignerColors.Panel)) {
                    Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("الخصائص", "الطبقات", "اللوحة").forEachIndexed { i, label ->
                            Text(
                                label,
                                color = if (tab == i) Color.Black else DesignerColors.Text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (tab == i) DesignerColors.Accent else DesignerColors.PanelAlt)
                                    .clickable { tab = i }
                                    .padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> PropertiesPanel(
                                el = selected,
                                vm = vm,
                                keepAspect = viewport.keepAspect,
                                onKeepAspect = { viewport.keepAspect = it },
                                onPickImage = { imageTarget = DesignerViewModel.ImageTarget.SELECTED_ELEMENT; pickImage.launch("image/*") },
                                onPickFont = { pickFont.launch("*/*") },
                                onPickIcon = { showIconPicker = true },
                                onPickApp = { vm.loadApps(); showAppPicker = true }
                            )
                            1 -> LayersPanel(p, selectedId, vm)
                            else -> CanvasPanel(
                                project = p,
                                vm = vm,
                                isActiveHome = activeHome == p.id,
                                onPickBackgroundImage = { imageTarget = DesignerViewModel.ImageTarget.BACKGROUND; pickImage.launch("image/*") },
                                onPreview = { showPreview = true },
                                onDeviceCheck = { deviceReport = runCatching { vm.deviceReport() }.getOrNull() }
                            )
                        }
                    }
                }
            }
        }

        // ── Dialogs ────────────────────────────────────────────────────
        if (showProjects) {
            ProjectsDialog(
                projects = projects,
                currentId = p.id,
                activeHomeId = activeHome,
                vm = vm,
                onImport = { importPkg.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onExport = { exportPkg.launch(p.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".oltheme") },
                onDismiss = { showProjects = false }
            )
        }
        if (showIconPicker && selected != null) {
            IconPickerDialog(
                current = selected.iconName,
                color = Color(selected.iconColor or 0xFF000000.toInt()),
                onPick = { name -> vm.updateElement(selected.id) { it.copy(iconName = name) }; showIconPicker = false },
                onDismiss = { showIconPicker = false }
            )
        }
        if (showAppPicker && selected != null) {
            AppPickerDialog(
                apps = apps,
                onPick = { app ->
                    vm.updateElement(selected.id) {
                        it.copy(
                            packageName = app.packageName,
                            action = ElementAction.LAUNCH_APP,
                            text = if (it.type == ElementType.SHORTCUT && (it.text.isBlank() || it.packageName.isEmpty())) app.label.take(18) else it.text
                        )
                    }
                    showAppPicker = false
                },
                onDismiss = { showAppPicker = false }
            )
        }
        deviceReport?.let { DeviceCheckDialog(it) { deviceReport = null } }
    }

    if (showPreview) {
        // True full-screen preview (covers the launcher sidebar too)
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            DesignPreviewOverlay(p, vm.assetDir(), vm.renderer) { showPreview = false }
        }
    }
}
