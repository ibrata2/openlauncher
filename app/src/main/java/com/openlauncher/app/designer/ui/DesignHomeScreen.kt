package com.openlauncher.app.designer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openlauncher.app.designer.model.DesignElement
import com.openlauncher.app.designer.model.DesignOps
import com.openlauncher.app.designer.model.DesignProject
import com.openlauncher.app.designer.model.ElementAction
import com.openlauncher.app.designer.render.DesignRenderer
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.min

/**
 * Runs a design as a real, interactive screen: scaled to fit (pixel-exact on a
 * 1024×600 panel), clock/date live, tappable elements perform their action.
 * Long-press anywhere opens a small menu so the user can never get stuck.
 */
@Composable
fun DesignRunScreen(
    project: DesignProject,
    assetDir: File?,
    onAction: (DesignElement) -> Unit,
    menuItems: List<Pair<Pair<ImageVector, String>, () -> Unit>>,
    modifier: Modifier = Modifier,
    renderer: DesignRenderer? = null
) {
    val context = LocalContext.current
    val r = renderer ?: remember { DesignRenderer(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pressedId by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val currentProject by rememberUpdatedState(project)
    val act by rememberUpdatedState(onAction)

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = System.currentTimeMillis()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val scale = min(cw / project.width, ch / project.height)
        val ox = (cw - project.width * scale) / 2f
        val oy = (ch - project.height * scale) / 2f

        fun hit(pos: Offset): DesignElement? {
            val x = (pos.x - ox) / scale
            val y = (pos.y - oy) / scale
            return currentProject.elements.asReversed().firstOrNull {
                it.visible && it.action != ElementAction.NONE && DesignOps.contains(it, x, y)
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(scale, ox, oy) {
                    detectTapGestures(
                        onPress = { pos ->
                            pressedId = hit(pos)?.id
                            tryAwaitRelease()
                            pressedId = null
                        },
                        onTap = { pos -> hit(pos)?.let(act) },
                        onLongPress = { showMenu = true }
                    )
                }
        ) {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val save = nc.save()
                nc.translate(ox, oy)
                nc.scale(scale, scale)
                // Pressed feedback: dim the touched element slightly
                val shown = pressedId?.let { id ->
                    DesignOps.update(project, id) { it.copy(opacity = it.opacity * 0.6f) }
                } ?: project
                r.draw(nc, shown, assetDir, now)
                nc.restoreToCount(save)
            }
        }
    }

    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DesignerColors.Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(project.name, color = DesignerColors.Accent, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                menuItems.forEach { (meta, action) ->
                    val (icon, label) = meta
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showMenu = false; action() }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(icon, null, tint = DesignerColors.Text, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = DesignerColors.Text, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** Convenience overlay used by the editor's full-screen preview (adds a close button). */
@Composable
fun DesignPreviewOverlay(project: DesignProject, assetDir: File?, renderer: DesignRenderer, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        DesignRunScreen(
            project = project,
            assetDir = assetDir,
            renderer = renderer,
            onAction = { /* preview: actions are not executed */ },
            menuItems = listOf((Icons.Default.Close to "إغلاق المعاينة") to onClose)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose)
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Close, "إغلاق", tint = Color.White)
        }
    }
}
