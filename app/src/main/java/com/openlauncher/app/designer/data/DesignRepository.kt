package com.openlauncher.app.designer.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.openlauncher.app.designer.model.DesignJson
import com.openlauncher.app.designer.model.DesignOps
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.render.DesignRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * File-based storage for designs:
 *
 *   filesDir/designs/<id>/design.json   – the project
 *   filesDir/designs/<id>/assets/…      – images and fonts it uses
 *   filesDir/designs/<id>/thumb.png     – small preview for the project list
 *
 * Also owns the "active design" (the one running as the home screen) and the
 * .oltheme package format (a zip of the folder above) for backup/sharing.
 * All functions do blocking I/O — call them from Dispatchers.IO.
 */
class DesignRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "designs").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("designer", Context.MODE_PRIVATE)
    private val thumbRenderer by lazy { DesignRenderer(appContext) }

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null)?.takeIf { it.isNotEmpty() })
    /** Design currently shown as the home screen, or null for the classic widget home. */
    val activeId: StateFlow<String?> = _activeId

    private val _revision = MutableStateFlow(0L)
    /** Bumped on every save/delete so the live home screen reloads. */
    val revision: StateFlow<Long> = _revision

    data class Summary(val id: String, val name: String, val updatedAt: Long, val thumb: File?, val elementCount: Int)

    // ── Paths ──────────────────────────────────────────────────────────────

    private fun safeId(id: String): String {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { "Bad design id" }
        return id
    }

    fun projectDir(id: String): File = File(root, safeId(id))
    fun assetDir(id: String): File = File(projectDir(id), "assets")
    private fun jsonFile(id: String) = File(projectDir(id), "design.json")
    private fun thumbFile(id: String) = File(projectDir(id), "thumb.png")

    // ── CRUD ───────────────────────────────────────────────────────────────

    fun list(): List<Summary> = (root.listFiles() ?: emptyArray())
        .filter { it.isDirectory && File(it, "design.json").isFile }
        .mapNotNull { dir ->
            runCatching {
                val p = DesignJson.decode(File(dir, "design.json").readText())
                Summary(dir.name, p.name, p.updatedAt, File(dir, "thumb.png").takeIf { it.isFile }, p.elements.size)
            }.getOrNull()
        }
        .sortedByDescending { it.updatedAt }

    fun load(id: String): DesignProject? = runCatching {
        val f = jsonFile(id)
        if (!f.isFile) return null
        // The folder name is the source of truth for the id
        DesignJson.decode(f.readText()).copy(id = id)
    }.getOrNull()

    fun save(project: DesignProject): DesignProject {
        val p = DesignOps.touch(project)
        val dir = projectDir(p.id).apply { mkdirs() }
        File(dir, "assets").mkdirs()
        val tmp = File(dir, "design.json.tmp")
        tmp.writeText(DesignJson.encode(p))
        val target = jsonFile(p.id)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                target.writeText(tmp.readText()); tmp.delete()
            }
        }
        writeThumb(p)
        _revision.value = _revision.value + 1
        return p
    }

    /** Called after an asset file changes so cached bitmaps/fonts are re-read. */
    fun invalidateAssets() = synchronized(thumbRenderer) { thumbRenderer.clearCaches() }

    private fun writeThumb(p: DesignProject) = runCatching {
        val bmp = synchronized(thumbRenderer) {
            thumbRenderer.renderBitmap(p, assetDir(p.id), scale = 256f / p.width)
        }
        thumbFile(p.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    fun delete(id: String) {
        projectDir(id).deleteRecursively()
        if (_activeId.value == id) setActive(null)
        _revision.value = _revision.value + 1
    }

    fun duplicate(id: String): DesignProject? {
        val src = load(id) ?: return null
        val copy = src.copy(id = DesignOps.newId("design"), name = src.name + " (نسخة)", createdAt = 0L)
        assetDir(id).takeIf { it.isDirectory }?.copyRecursively(assetDir(copy.id), overwrite = true)
        return save(copy)
    }

    fun setActive(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id ?: "").apply()
        _activeId.value = id
    }

    // ── Assets ─────────────────────────────────────────────────────────────

    enum class AssetKind { IMAGE, FONT }

    /**
     * Copies a picked file into the project's asset folder and returns the stored
     * file name, or null if it is not a usable image/font or is too large.
     */
    fun importAsset(projectId: String, uri: Uri, kind: AssetKind): String? {
        val dir = assetDir(projectId).apply { mkdirs() }
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val ext = when (kind) {
            AssetKind.IMAGE -> when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            AssetKind.FONT -> {
                val lower = uri.lastPathSegment.orEmpty().lowercase(Locale.ROOT)
                if (lower.endsWith(".otf") || mime.contains("otf")) "otf" else "ttf"
            }
        }
        val name = "${if (kind == AssetKind.IMAGE) "img" else "font"}_${System.currentTimeMillis().toString(36)}.$ext"
        val out = File(dir, name)
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> copyLimited(input, out, MAX_ASSET_BYTES) } ?: false
        }.getOrDefault(false)
        if (!copied) { out.delete(); return null }
        val valid = when (kind) {
            AssetKind.IMAGE -> BitmapFactory.Options().apply { inJustDecodeBounds = true }
                .also { BitmapFactory.decodeFile(out.absolutePath, it) }.outWidth > 0
            AssetKind.FONT -> runCatching { android.graphics.Typeface.createFromFile(out) }.isSuccess && out.length() > 1000
        }
        if (!valid) { out.delete(); return null }
        return name
    }

    private fun copyLimited(input: InputStream, out: File, limit: Long): Boolean {
        var total = 0L
        out.outputStream().use { os ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) return false
                os.write(buf, 0, n)
            }
        }
        return total > 0
    }

    // ── .oltheme package (zip) ─────────────────────────────────────────────

    fun exportPackage(id: String, out: OutputStream) {
        val p = load(id) ?: throw IllegalStateException("Design not found")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("design.json"))
            zip.write(DesignJson.encode(p).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            assetDir(id).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("assets/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Imports a package as a *new* design (never overwrites an existing one). */
    fun importPackage(input: InputStream): DesignProject {
        val newId = DesignOps.newId("design")
        val staging = File(root, "import_$newId").apply { deleteRecursively(); mkdirs() }
        try {
            var json: String? = null
            var total = 0L
            var entries = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (++entries > 500) throw IllegalArgumentException("Too many files in package")
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    val bytes = readLimited(zip, MAX_ASSET_BYTES)
                    total += bytes.size
                    if (total > MAX_PACKAGE_BYTES) throw IllegalArgumentException("Package too large")
                    when {
                        name == "design.json" -> json = bytes.toString(Charsets.UTF_8)
                        name.startsWith("assets/") -> {
                            val file = name.removePrefix("assets/")
                            // Zip-slip protection: plain file names only
                            if (file.isEmpty() || file.contains('/') || file.startsWith(".")) continue
                            File(staging, file).writeBytes(bytes)
                        }
                    }
                }
            }
            val decoded = DesignJson.decode(json ?: throw IllegalArgumentException("design.json missing"))
            val project = decoded.copy(id = newId, createdAt = 0L)
            val assets = assetDir(newId)
            assets.parentFile?.mkdirs()
            if (!staging.renameTo(assets)) {
                staging.copyRecursively(assets, overwrite = true)
            }
            return save(project)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw IllegalArgumentException("File too large in package")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ── PNG export ─────────────────────────────────────────────────────────

    data class PngResult(val uri: Uri, val displayPath: String)

    /** Renders at exact design resolution (1024×600 by default) and saves to the gallery. */
    fun exportPng(project: DesignProject, scale: Float = 1f): PngResult {
        // Separate renderer from the editor's: export runs on a background thread
        val bmp = synchronized(thumbRenderer) { thumbRenderer.renderBitmap(project, assetDir(project.id), scale) }
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = project.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(40).ifEmpty { "design" }
            val fileName = "${safeName}_${bmp.width}x${bmp.height}_$stamp.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenLauncher")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create gallery entry")
                resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: throw IllegalStateException("Could not open gallery entry")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return PngResult(uri, "Pictures/OpenLauncher/$fileName")
            } else {
                // No storage permission needed for the app-specific external folder
                val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: appContext.filesDir, "OpenLauncher")
                dir.mkdirs()
                val f = File(dir, fileName)
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(appContext, appContext.packageName + ".designfiles", f)
                return PngResult(uri, f.absolutePath)
            }
        } finally {
            bmp.recycle()
        }
    }

    /** A temporary PNG in the cache folder, shareable through the FileProvider. */
    fun pngForSharing(project: DesignProject): Uri {
        val bmp = synchronized(thumbRenderer) { thumbRenderer.renderBitmap(project, assetDir(project.id), 1f) }
        val dir = File(appContext.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "design_${project.width}x${project.height}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return FileProvider.getUriForFile(appContext, appContext.packageName + ".designfiles", f)
    }

    companion object {
        private const val KEY_ACTIVE = "active_design_id"
        private const val MAX_ASSET_BYTES = 25L * 1024 * 1024
        private const val MAX_PACKAGE_BYTES = 80L * 1024 * 1024

        @Volatile private var instance: DesignRepository? = null

        fun get(context: Context): DesignRepository =
            instance ?: synchronized(this) { instance ?: DesignRepository(context).also { instance = it } }
    }
}
