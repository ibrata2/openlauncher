package com.openlauncher.app.designer

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlauncher.app.designer.data.DesignRepository
import com.openlauncher.app.designer.data.DeviceCheck
import com.openlauncher.app.designer.model.Alignment
import com.openlauncher.app.designer.model.BackgroundType
import com.openlauncher.app.designer.model.DesignElement
import com.openlauncher.app.designer.model.DesignOps
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.model.DesignTemplates
import com.openlauncher.app.designer.model.ElementType
import com.openlauncher.app.designer.model.FontChoice
import com.openlauncher.app.designer.model.History
import com.openlauncher.app.designer.model.LayerMove
import com.openlauncher.app.designer.render.DesignRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor state + every editing command. The UI only reads these flows and calls
 * these functions; all geometry lives in [DesignOps] (unit-tested).
 */
class DesignerViewModel(application: Application) : AndroidViewModel(application) {

    val repo = DesignRepository.get(application)
    /** Renderer for the on-screen canvas (UI thread only). */
    val renderer = DesignRenderer(application)

    private val history = History<DesignProject>(limit = 150)

    private val _project = MutableStateFlow<DesignProject?>(null)
    val project: StateFlow<DesignProject?> = _project

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId

    private val _guides = MutableStateFlow<Pair<List<Float>, List<Float>>>(emptyList<Float>() to emptyList())
    val guides: StateFlow<Pair<List<Float>, List<Float>>> = _guides

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    private val _projects = MutableStateFlow<List<DesignRepository.Summary>>(emptyList())
    val projects: StateFlow<List<DesignRepository.Summary>> = _projects

    data class AppEntry(val packageName: String, val label: String)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** Ticks every second so clock/date elements stay live in the editor. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    /** Bumped when asset files change so the canvas redraws with fresh bitmaps. */
    private val _assetRevision = MutableStateFlow(0)
    val assetRevision: StateFlow<Int> = _assetRevision

    val activeDesignId: StateFlow<String?> = repo.activeId

    private val prefs = application.getSharedPreferences("designer", 0)
    private var autosaveJob: Job? = null
    private var gestureStart: DesignProject? = null
    private var lastEditKey: String? = null
    private var lastEditTime = 0L

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L - System.currentTimeMillis() % 1000L)
                _now.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch {
            val lastId = prefs.getString("last_open_id", null)
            val loaded = withContext(Dispatchers.IO) {
                lastId?.let { runCatching { repo.load(it) }.getOrNull() }
                    ?: repo.list().firstOrNull()?.let { repo.load(it.id) }
            }
            if (loaded != null) setProject(loaded) else newProject("classic")
            refreshProjects()
        }
    }

    // ── Project lifecycle ──────────────────────────────────────────────────

    private fun setProject(p: DesignProject) {
        history.clear()
        _project.value = p
        _selectedId.value = null
        _guides.value = emptyList<Float>() to emptyList()
        _dirty.value = false
        lastEditKey = null
        refreshHistoryFlags()
        prefs.edit().putString("last_open_id", p.id).apply()
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _projects.value = withContext(Dispatchers.IO) { runCatching { repo.list() }.getOrDefault(emptyList()) }
        }
    }

    fun newProject(templateKey: String) {
        viewModelScope.launch {
            saveNowInternal()
            val p = withContext(Dispatchers.IO) { repo.save(DesignTemplates.byKey(templateKey)) }
            setProject(p)
            refreshProjects()
            toast("تم إنشاء تصميم جديد: ${p.name}")
        }
    }

    fun openProject(id: String) {
        viewModelScope.launch {
            saveNowInternal()
            val p = withContext(Dispatchers.IO) { repo.load(id) }
            if (p == null) toast("تعذر فتح التصميم") else setProject(p)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            if (_project.value?.id == id) {
                val next = withContext(Dispatchers.IO) { repo.list().firstOrNull()?.let { repo.load(it.id) } }
                if (next != null) setProject(next) else {
                    val p = withContext(Dispatchers.IO) { repo.save(DesignTemplates.blank()) }
                    setProject(p)
                }
            }
            refreshProjects()
            toast("تم حذف التصميم")
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            saveNowInternal()
            val copy = withContext(Dispatchers.IO) { repo.duplicate(id) }
            refreshProjects()
            if (copy != null) { setProject(copy); toast("تم إنشاء نسخة") }
        }
    }

    fun rename(name: String) {
        val clean = name.trim().take(60).ifEmpty { return }
        commit("rename") { it.copy(name = clean) }
    }

    /** Saves now (button). */
    fun save() {
        viewModelScope.launch {
            if (saveNowInternal(force = true)) toast("تم الحفظ")
        }
    }

    /** Called from onStop so nothing is lost when leaving the app. */
    fun saveIfDirty() {
        viewModelScope.launch { saveNowInternal() }
    }

    private suspend fun saveNowInternal(force: Boolean = false): Boolean {
        autosaveJob?.cancel()
        val p = _project.value ?: return false
        if (!force && !_dirty.value) return true
        if (!force && gestureStart != null) { scheduleAutosave(); return true } // finger still down
        return try {
            val saved = withContext(Dispatchers.IO) { repo.save(p) }
            // Keep the in-memory copy unless the user kept editing meanwhile
            if (_project.value?.id == saved.id && _project.value == p) _project.value = saved
            _dirty.value = false
            refreshProjects()
            true
        } catch (e: Exception) {
            toast("فشل الحفظ: ${e.message}")
            false
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(4000)
            saveNowInternal()
        }
    }

    // ── Editing core ───────────────────────────────────────────────────────

    /**
     * Applies [transform] to the project with undo support. Rapid edits sharing
     * the same [key] (typing in a field, dragging a slider) become one undo step.
     */
    fun commit(key: String? = null, transform: (DesignProject) -> DesignProject) {
        val before = _project.value ?: return
        val after = transform(before)
        if (after == before) return
        val now = System.currentTimeMillis()
        val coalesce = key != null && key == lastEditKey && now - lastEditTime < 1500
        if (!coalesce) history.record(before)
        lastEditKey = key
        lastEditTime = now
        _project.value = after
        markChanged()
    }

    private fun markChanged() {
        _dirty.value = true
        refreshHistoryFlags()
        scheduleAutosave()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    fun updateElement(id: String, key: String? = null, transform: (DesignElement) -> DesignElement) =
        commit(key?.let { "$id:$it" }) { p -> DesignOps.update(p, id) { DesignOps.sanitize(transform(it)) } }

    fun updateSelected(key: String? = null, transform: (DesignElement) -> DesignElement) {
        val id = _selectedId.value ?: return
        updateElement(id, key, transform)
    }

    fun select(id: String?) {
        _selectedId.value = id
        lastEditKey = null
    }

    fun undo() {
        val cur = _project.value ?: return
        val prev = history.undo(cur) ?: return
        _project.value = prev
        if (prev.element(_selectedId.value) == null) _selectedId.value = null
        lastEditKey = null
        markChanged()
    }

    fun redo() {
        val cur = _project.value ?: return
        val next = history.redo(cur) ?: return
        _project.value = next
        if (next.element(_selectedId.value) == null) _selectedId.value = null
        lastEditKey = null
        markChanged()
    }

    // ── Gestures (live preview without flooding the undo stack) ────────────

    fun beginGesture() {
        gestureStart = _project.value
        lastEditKey = null
    }

    fun gestureUpdate(p: DesignProject, guidesX: List<Float> = emptyList(), guidesY: List<Float> = emptyList()) {
        _project.value = p
        _guides.value = guidesX to guidesY
    }

    fun endGesture() {
        val start = gestureStart
        gestureStart = null
        _guides.value = emptyList<Float>() to emptyList()
        val cur = _project.value ?: return
        if (start != null && start != cur) {
            history.record(start)
            markChanged()
        }
    }

    // ── Element commands ───────────────────────────────────────────────────

    fun addElement(type: ElementType, cx: Float? = null, cy: Float? = null) {
        val p = _project.value ?: return
        val el = DesignOps.createElement(type, p, cx ?: p.width / 2f, cy ?: p.height / 2f)
        commit { DesignOps.add(it, el) }
        _selectedId.value = el.id
    }

    fun deleteSelected() {
        val id = _selectedId.value ?: return
        commit { DesignOps.remove(it, id) }
        _selectedId.value = null
    }

    fun deleteElement(id: String) {
        commit { DesignOps.remove(it, id) }
        if (_selectedId.value == id) _selectedId.value = null
    }

    fun duplicateSelected() {
        val id = _selectedId.value ?: return
        var newId: String? = null
        commit { p -> DesignOps.duplicate(p, id)?.let { (np, copy) -> newId = copy.id; np } ?: p }
        newId?.let { _selectedId.value = it }
    }

    fun moveLayer(id: String, move: LayerMove) = commit { DesignOps.moveLayer(it, id, move) }

    fun reorderLayer(from: Int, to: Int) = commit { DesignOps.reorder(it, from, to) }

    fun align(alignment: Alignment) {
        val id = _selectedId.value ?: return
        commit { DesignOps.align(it, id, alignment) }
    }

    fun nudge(dx: Float, dy: Float) {
        val id = _selectedId.value ?: return
        commit("nudge:$id") { DesignOps.nudge(it, id, dx, dy) }
    }

    fun toggleVisible(id: String) = updateElement(id) { it.copy(visible = !it.visible) }
    fun toggleLocked(id: String) = updateElement(id) { it.copy(locked = !it.locked) }
    fun renameElement(id: String, name: String) = updateElement(id, "name") { it.copy(name = name.take(60)) }

    fun updateProjectSettings(key: String? = null, transform: (DesignProject) -> DesignProject) =
        commit(key?.let { "project:$it" }, transform)

    // ── Assets ─────────────────────────────────────────────────────────────

    enum class ImageTarget { SELECTED_ELEMENT, NEW_ELEMENT, BACKGROUND }

    fun importImage(uri: Uri, target: ImageTarget) {
        val p = _project.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val name = withContext(Dispatchers.IO) {
                runCatching { repo.importAsset(p.id, uri, DesignRepository.AssetKind.IMAGE) }.getOrNull()
            }
            _busy.value = false
            if (name == null) { toast("الملف ليس صورة صالحة أو حجمه كبير جداً"); return@launch }
            _assetRevision.value++
            when (target) {
                ImageTarget.BACKGROUND -> commit { it.copy(background = it.background.copy(type = BackgroundType.IMAGE, imagePath = name)) }
                ImageTarget.SELECTED_ELEMENT -> {
                    val sel = _project.value?.element(_selectedId.value)
                    if (sel != null && sel.type == ElementType.IMAGE) updateElement(sel.id) { it.copy(imagePath = name) }
                    else addImageElement(name)
                }
                ImageTarget.NEW_ELEMENT -> addImageElement(name)
            }
        }
    }

    private fun addImageElement(name: String) {
        val p = _project.value ?: return
        val el = DesignOps.createElement(ElementType.IMAGE, p).copy(imagePath = name)
        commit { DesignOps.add(it, el) }
        _selectedId.value = el.id
    }

    fun importFont(uri: Uri) {
        val p = _project.value ?: return
        val id = _selectedId.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val name = withContext(Dispatchers.IO) {
                runCatching { repo.importAsset(p.id, uri, DesignRepository.AssetKind.FONT) }.getOrNull()
            }
            _busy.value = false
            if (name == null) { toast("ملف الخط غير صالح (TTF / OTF)"); return@launch }
            updateElement(id) { it.copy(font = FontChoice.CUSTOM, customFontPath = name) }
            toast("تم تطبيق الخط")
        }
    }

    fun assetDir(): java.io.File? = _project.value?.let { repo.assetDir(it.id) }

    // ── Export / import ────────────────────────────────────────────────────

    fun exportPng(scale: Float = 1f) {
        val p = _project.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) { runCatching { repo.exportPng(p, scale) } }
            _busy.value = false
            result.onSuccess { toast("تم حفظ الصورة PNG (${(p.width * scale).toInt()}×${(p.height * scale).toInt()}) في ${it.displayPath}") }
                .onFailure { toast("فشل تصدير PNG: ${it.message}") }
        }
    }

    /** Returns a share intent for the PNG through [onReady]. */
    fun sharePng(onReady: (Intent) -> Unit) {
        val p = _project.value ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { runCatching { repo.pngForSharing(p) }.getOrNull() }
            if (uri == null) { toast("تعذر تجهيز الصورة"); return@launch }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onReady(Intent.createChooser(send, "مشاركة التصميم"))
        }
    }

    fun exportPackage(uri: Uri) {
        val p = _project.value ?: return
        viewModelScope.launch {
            _busy.value = true
            saveNowInternal(force = true)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { repo.exportPackage(p.id, it) }
                        ?: error("no stream")
                }.isSuccess
            }
            _busy.value = false
            toast(if (ok) "تم تصدير ملف التصميم (.oltheme)" else "فشل تصدير الملف")
        }
    }

    fun importPackage(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            saveNowInternal()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { repo.importPackage(it) }
                        ?: error("no stream")
                }
            }
            _busy.value = false
            result.onSuccess { setProject(it); refreshProjects(); toast("تم استيراد: ${it.name}") }
                .onFailure { toast("ملف غير صالح: ${it.message}") }
        }
    }

    // ── Run on the head unit ───────────────────────────────────────────────

    fun applyAsHome() {
        val p = _project.value ?: return
        viewModelScope.launch {
            saveNowInternal(force = true)
            repo.setActive(p.id)
            toast("تم تفعيل التصميم كواجهة رئيسية")
        }
    }

    fun disableHomeDesign() {
        repo.setActive(null)
        toast("تمت العودة للواجهة الكلاسيكية")
    }

    fun deviceReport(): DeviceCheck.Report = DeviceCheck.run(getApplication())

    // ── Apps for shortcut elements ─────────────────────────────────────────

    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = getApplication<Application>().packageManager
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                        .distinctBy { it.packageName }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
        }
    }

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    override fun onCleared() {
        renderer.clearCaches()
        super.onCleared()
    }
}
