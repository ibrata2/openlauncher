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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.designer.DesignerViewModel
import com.openlauncher.app.designer.model.BackgroundType
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.model.ImageFit
import kotlin.math.roundToInt

/** Background, grid/snap and "run on the head unit" settings for the whole design. */
@Composable
fun CanvasPanel(
    project: DesignProject,
    vm: DesignerViewModel,
    isActiveHome: Boolean,
    onPickBackgroundImage: () -> Unit,
    onPreview: () -> Unit,
    onDeviceCheck: () -> Unit
) {
    val bg = project.background
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {

        PanelSection("التصميم") {
            TextInput("اسم التصميم", project.name) { vm.rename(it) }
            Text(
                "المقاس: ${project.width} × ${project.height} بكسل (شاشة Pioneer Y6 HZ25)",
                color = DesignerColors.TextMuted, fontSize = 11.sp
            )
        }

        PanelSection("الخلفية") {
            ChoiceChips(
                listOf(BackgroundType.COLOR to "لون", BackgroundType.GRADIENT to "تدرج", BackgroundType.IMAGE to "صورة"),
                bg.type
            ) { t ->
                if (t == BackgroundType.IMAGE && bg.imagePath.isEmpty()) onPickBackgroundImage()
                else vm.updateProjectSettings { it.copy(background = it.background.copy(type = t)) }
            }
            ColorRow(if (bg.type == BackgroundType.GRADIENT) "اللون الأول" else "لون الخلفية", bg.color) { c ->
                vm.updateProjectSettings("bgColor") { it.copy(background = it.background.copy(color = c)) }
            }
            if (bg.type == BackgroundType.GRADIENT) {
                ColorRow("اللون الثاني", bg.color2) { c ->
                    vm.updateProjectSettings("bgColor2") { it.copy(background = it.background.copy(color2 = c)) }
                }
                SliderRow("زاوية التدرج", bg.gradientAngle, 0f..359f, { "${it.roundToInt()}°" }) { v ->
                    vm.updateProjectSettings("bgAngle") { it.copy(background = it.background.copy(gradientAngle = v.roundToInt().toFloat())) }
                }
            }
            if (bg.type == BackgroundType.IMAGE) {
                OutlinedButton(onClick = onPickBackgroundImage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Wallpaper, null); Text("  تغيير صورة الخلفية")
                }
                ChoiceChips(
                    listOf(ImageFit.COVER to "ملء", ImageFit.CONTAIN to "احتواء", ImageFit.STRETCH to "تمديد"),
                    bg.imageFit
                ) { f -> vm.updateProjectSettings { it.copy(background = it.background.copy(imageFit = f)) } }
                SliderRow("تعتيم الصورة", bg.dim, 0f..0.9f, { "${(it * 100).roundToInt()}%" }) { v ->
                    vm.updateProjectSettings("bgDim") { it.copy(background = it.background.copy(dim = v)) }
                }
            }
        }

        PanelSection("الشبكة والالتقاط") {
            SwitchRow("إظهار الشبكة", project.showGrid) { v -> vm.updateProjectSettings { it.copy(showGrid = v) } }
            SwitchRow("الالتقاط للشبكة", project.snapToGrid) { v -> vm.updateProjectSettings { it.copy(snapToGrid = v) } }
            SwitchRow("أدلة ذكية (حواف ومراكز العناصر)", project.snapToGuides) { v -> vm.updateProjectSettings { it.copy(snapToGuides = v) } }
            ChoiceChips(listOf(4 to "4", 8 to "8", 10 to "10", 16 to "16", 24 to "24", 32 to "32"), project.gridSize) { g ->
                vm.updateProjectSettings { it.copy(gridSize = g) }
            }
        }

        PanelSection("التشغيل على شاشة السيارة") {
            Text(
                "هذا التطبيق نفسه لانشر (واجهة رئيسية)، لذلك يعمل التصميم مباشرة على الشاشة دون الحاجة لدعم ثيمات من الشركة المصنعة. " +
                    "الساعة والتاريخ يتحدثان، والاختصارات تفتح التطبيقات. للخروج من التصميم: ضغطة مطولة على أي فراغ.",
                color = DesignerColors.TextMuted, fontSize = 11.sp, lineHeight = 16.sp
            )
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Fullscreen, null); Text("  معاينة بملء الشاشة")
            }
            if (isActiveHome) {
                Text("✓ هذا التصميم مفعّل كواجهة رئيسية", color = Color(0xFF81C784), fontSize = 12.sp)
                OutlinedButton(onClick = { vm.disableHomeDesign() }, modifier = Modifier.fillMaxWidth()) {
                    Text("العودة للواجهة الكلاسيكية (الويدجتات)")
                }
            } else {
                Button(
                    onClick = { vm.applyAsHome() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignerColors.Accent, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Home, null); Text("  تفعيل كواجهة رئيسية")
                }
            }
            OutlinedButton(onClick = onDeviceCheck, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhoneAndroid, null); Text("  فحص الجهاز والشاشة")
            }
        }

        PanelSection("التصدير") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { vm.exportPng(1f) }, modifier = Modifier.weight(1f)) { Text("PNG 1024×600") }
                OutlinedButton(onClick = { vm.exportPng(2f) }, modifier = Modifier.weight(1f)) { Text("PNG ×2") }
            }
        }
    }
}
