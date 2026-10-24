package com.openlauncher.app.designer.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.openlauncher.app.designer.model.DEFAULT_CANVAS_H
import com.openlauncher.app.designer.model.DEFAULT_CANVAS_W

/**
 * Reads what the head unit / tablet actually is, instead of assuming.
 * Shown in the designer's "device check" sheet so the user can confirm that the
 * Pioneer Y6 HZ25 panel really is 1024×600 and how the design can run on it.
 */
object DeviceCheck {

    data class Item(val label: String, val value: String, val status: Status = Status.INFO)
    enum class Status { OK, WARN, INFO }

    data class Report(
        val items: List<Item>,
        val realWidth: Int,
        val realHeight: Int,
        val matchesDesign: Boolean,
        val isDefaultHome: Boolean,
        val launchers: List<String>,
        val vendorPackages: List<String>,
        val themePackages: List<String>
    )

    private val VENDOR_PREFIXES = listOf(
        "com.szchoiceway", "com.syu", "com.fyt", "com.tw.", "com.ts.", "com.nwd", "com.microntek",
        "com.hct", "com.android.launcher3", "com.zjinnova", "com.autochips", "com.mediatek.car",
        "com.wits", "com.ecar", "com.yd", "com.carletter", "com.zlink", "com.carlink", "com.dofun", "com.topway"
    )

    @SuppressLint("QueryPermissionsNeeded")
    fun run(context: Context): Report {
        val pm = context.packageManager
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val real = Point()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.apply {
            getRealSize(real)
            getRealMetrics(metrics)
        }
        val longSide = maxOf(real.x, real.y)
        val shortSide = minOf(real.x, real.y)
        val matches = longSide == DEFAULT_CANVAS_W && shortSide == DEFAULT_CANVAS_H

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val isDefault = defaultHome == context.packageName
        @Suppress("DEPRECATION")
        val launchers = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.distinct()

        @Suppress("DEPRECATION")
        val installed = runCatching { pm.getInstalledApplications(0).map { it.packageName } }.getOrDefault(emptyList())
        val vendor = installed.filter { p -> VENDOR_PREFIXES.any { p.startsWith(it) } }.sorted()
        val themes = installed.filter { it.contains("theme", ignoreCase = true) || it.contains("skin", ignoreCase = true) }.sorted()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        val items = mutableListOf(
            Item("الشركة / الطراز", "${Build.MANUFACTURER} / ${Build.MODEL}"),
            Item("المنتج / اللوحة", "${Build.PRODUCT} / ${Build.BOARD} / ${Build.HARDWARE}"),
            Item("أندرويد", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                if (Build.VERSION.SDK_INT >= 21) Status.OK else Status.WARN),
            Item("الدقة الفعلية للشاشة", "${real.x} × ${real.y} بكسل",
                if (matches) Status.OK else Status.WARN),
            Item("مطابقة لـ 1024×600", if (matches) "نعم — التصميم يظهر بكسل مقابل بكسل" else "لا — سيتم تحجيم التصميم مع الحفاظ على النسبة",
                if (matches) Status.OK else Status.WARN),
            Item("الكثافة", "${metrics.densityDpi} dpi (×${"%.2f".format(java.util.Locale.US, metrics.density)})"),
            Item("الذاكرة", "${mem.totalMem / (1024 * 1024)} MB",
                if (mem.totalMem >= 1024L * 1024 * 1024) Status.OK else Status.WARN),
            Item("الواجهة الافتراضية الحالية", defaultHome ?: "غير محددة (يسأل النظام في كل مرة)",
                if (isDefault) Status.OK else Status.WARN),
            Item("واجهات Launcher المثبتة", launchers.joinToString("، ").ifEmpty { "—" }),
            Item("حزم الشركة المصنعة المكتشفة", vendor.joinToString("، ").ifEmpty { "لم يُعثر على حزم معروفة" }),
            Item("حزم ثيمات/سكنات في النظام", themes.joinToString("، ").ifEmpty { "لا يوجد — النظام لا يُظهر مدير ثيمات" }),
            Item("طريقة التشغيل المعتمدة",
                "يعمل التصميم كواجهة رئيسية (HOME) عبر هذا التطبيق نفسه، ولا يحتاج دعم ثيمات من الشركة المصنعة.",
                Status.OK)
        )
        return Report(items, real.x, real.y, matches, isDefault, launchers, vendor, themes)
    }
}
