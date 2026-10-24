package com.openlauncher.app.designer.render

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Icon catalogue for ICON / SHORTCUT elements. Names are what gets saved in the
 * design file; vectors come from Material Icons (already a project dependency).
 * Icons are converted to android.graphics.Path once so the same renderer can
 * draw them in the editor and into exported PNG bitmaps.
 */
object DesignIcons {

    data class IconDef(val name: String, val label: String, val vector: ImageVector)

    val all: List<IconDef> by lazy {
        listOf(
            IconDef("radio", "راديو", Icons.Default.Radio),
            IconDef("music", "موسيقى", Icons.Default.MusicNote),
            IconDef("library_music", "مكتبة", Icons.Default.LibraryMusic),
            IconDef("play", "تشغيل", Icons.Default.PlayArrow),
            IconDef("pause", "إيقاف", Icons.Default.Pause),
            IconDef("skip_next", "التالي", Icons.Default.SkipNext),
            IconDef("skip_previous", "السابق", Icons.Default.SkipPrevious),
            IconDef("volume", "الصوت", Icons.Default.VolumeUp),
            IconDef("equalizer", "معادل", Icons.Default.Equalizer),
            IconDef("headset", "سماعة", Icons.Default.Headset),
            IconDef("mic", "مايك", Icons.Default.Mic),
            IconDef("phone", "هاتف", Icons.Default.Phone),
            IconDef("message", "رسائل", Icons.Default.Message),
            IconDef("contacts", "جهات", Icons.Default.Contacts),
            IconDef("bluetooth", "بلوتوث", Icons.Default.Bluetooth),
            IconDef("wifi", "واي فاي", Icons.Default.Wifi),
            IconDef("map", "خريطة", Icons.Default.Map),
            IconDef("navigation", "ملاحة", Icons.Default.Navigation),
            IconDef("location", "موقع", Icons.Default.LocationOn),
            IconDef("car", "سيارة", Icons.Default.DirectionsCar),
            IconDef("speed", "عداد", Icons.Default.Speed),
            IconDef("gas", "وقود", Icons.Default.LocalGasStation),
            IconDef("parking", "موقف", Icons.Default.LocalParking),
            IconDef("camera", "كاميرا", Icons.Default.CameraAlt),
            IconDef("videocam", "فيديو", Icons.Default.Videocam),
            IconDef("movie", "أفلام", Icons.Default.Movie),
            IconDef("tv", "تلفاز", Icons.Default.Tv),
            IconDef("image", "صور", Icons.Default.Image),
            IconDef("folder", "ملفات", Icons.Default.Folder),
            IconDef("apps", "تطبيقات", Icons.Default.Apps),
            IconDef("home", "رئيسية", Icons.Default.Home),
            IconDef("settings", "إعدادات", Icons.Default.Settings),
            IconDef("ac", "تكييف", Icons.Default.AcUnit),
            IconDef("thermostat", "حرارة", Icons.Default.Thermostat),
            IconDef("sun", "شمس", Icons.Default.WbSunny),
            IconDef("night", "ليل", Icons.Default.NightsStay),
            IconDef("cloud", "طقس", Icons.Default.Cloud),
            IconDef("lightbulb", "إضاءة", Icons.Default.Lightbulb),
            IconDef("brightness", "سطوع", Icons.Default.Brightness6),
            IconDef("calendar", "تقويم", Icons.Default.CalendarToday),
            IconDef("clock", "ساعة", Icons.Default.Schedule),
            IconDef("timer", "مؤقت", Icons.Default.Timer),
            IconDef("battery", "بطارية", Icons.Default.BatteryFull),
            IconDef("power", "تشغيل/إطفاء", Icons.Default.PowerSettingsNew),
            IconDef("lock", "قفل", Icons.Default.Lock),
            IconDef("warning", "تنبيه", Icons.Default.Warning),
            IconDef("person", "شخص", Icons.Default.Person),
            IconDef("star", "نجمة", Icons.Default.Star),
            IconDef("favorite", "قلب", Icons.Default.Favorite),
            IconDef("globe", "إنترنت", Icons.Default.Public),
            IconDef("search", "بحث", Icons.Default.Search),
            IconDef("shopping", "تسوق", Icons.Default.ShoppingCart),
            IconDef("build", "صيانة", Icons.Default.Build)
        )
    }

    private val byName: Map<String, IconDef> by lazy { all.associateBy { it.name } }

    fun find(name: String): IconDef? = byName[name]

    /** A path in the vector's own viewport coordinates, plus that viewport size. */
    class IconPath(val path: Path, val viewportWidth: Float, val viewportHeight: Float)

    private val pathCache = HashMap<String, IconPath>()

    @Synchronized
    fun path(name: String): IconPath? {
        pathCache[name]?.let { return it }
        val def = find(name) ?: return null
        val out = Path()
        appendGroup(def.vector.root, Matrix(), out)
        return IconPath(out, def.vector.viewportWidth, def.vector.viewportHeight).also { pathCache[name] = it }
    }

    private fun appendGroup(group: VectorGroup, parent: Matrix, out: Path) {
        val m = Matrix(parent)
        // Same transform order as Compose's GroupComponent
        m.preTranslate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        m.preRotate(group.rotation)
        m.preScale(group.scaleX, group.scaleY)
        m.preTranslate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorGroup -> appendGroup(node, m, out)
                is VectorPath -> {
                    val p = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    p.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    p.transform(m)
                    out.addPath(p)
                }
                else -> Unit
            }
        }
    }
}
