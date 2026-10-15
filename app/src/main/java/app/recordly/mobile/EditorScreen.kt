@file:OptIn(UnstableApi::class)

package app.recordly.mobile

import android.content.Intent
import android.net.Uri
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(file: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(file) { Recordings.info(file) }
    val tapData = remember(file) { Recordings.readTaps(file) }
    val hasTaps = tapData != null && tapData.taps.isNotEmpty()

    var opt by remember {
        mutableStateOf(
            EditOptions(
                trimStartMs = 0,
                trimEndMs = info.durMs,
                autoZoom = AppSettings.autoZoom && hasTaps,
                ripples = AppSettings.ripples && hasTaps,
            )
        )
    }
    val moments = remember(tapData, opt.zoomLevel) {
        if (hasTaps) ZoomPlan(tapData!!.taps, opt.zoomLevel).momentCount else 0
    }

    // ---- player (raw preview)
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var pos by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            playing = player.isPlaying
            if (player.isPlaying && pos >= opt.trimEndMs) player.pause()
            delay(100)
        }
    }
    LaunchedEffect(opt.speed) { player.setPlaybackSpeed(opt.speed) }

    // ---- export
    var progress by remember { mutableIntStateOf(-1) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val exporting = progress >= 0
    LaunchedEffect(exporting) {
        while (exporting) {
            val t = transformer
            if (t != null) {
                val h = ProgressHolder()
                if (t.getProgress(h) != Transformer.PROGRESS_STATE_NOT_STARTED) progress = h.progress
            }
            delay(300)
        }
    }

    fun doExport() {
        player.pause()
        savedUri = null
        error = null
        progress = 0
        val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        try {
            transformer = Exporter.start(ctx, file, info, tapData, opt, out) { result ->
                result.onSuccess { f ->
                    savedUri = Recordings.saveToMovies(ctx, f, "Recordly_Edit_${System.currentTimeMillis()}")
                    f.delete()
                }
                result.onFailure { error = it.message ?: "Export fail ho gaya" }
                progress = -1
                transformer = null
            }
        } catch (e: Exception) {
            error = e.message
            progress = -1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("Editor", fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { if (exporting) { transformer?.cancel(); progress = -1 } else doExport() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4F4F5), contentColor = Ink),
            ) { Text(if (exporting) "Cancel" else "Export") }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .aspectRatio(info.w.toFloat() / info.h.toFloat(), matchHeightConstraintsFirst = true)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
            ) {
                AndroidView(
                    factory = { c -> SurfaceView(c).also { player.setVideoSurfaceView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "Preview raw recording dikhata hai. Zoom, background aur ripples export ke baad dikhenge.",
                color = Muted, style = MaterialTheme.typography.bodySmall,
            )

            if (exporting) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                )
                Text("Export ho raha hai… $progress%", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            savedUri?.let { uri ->
                SectionCard {
                    Text("Export ho gaya", fontWeight = FontWeight.SemiBold)
                    Text("Movies/Recordly me save hai.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(i, "Share video"))
                    }) { Text("Share") }
                }
            }
            error?.let { NoteRow("Export error: $it") }

            // transport
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else {
                        if (pos < opt.trimStartMs || pos >= opt.trimEndMs) player.seekTo(opt.trimStartMs)
                        player.play()
                    }
                }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause") }
                Text(
                    "${Recordings.fmt(pos)} / ${Recordings.fmt(info.durMs)}",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            }

            // timeline: taps markers + playhead + trim
            SectionCard {
                Text("Timeline", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val dur = info.durMs.coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))) {
                    drawRect(Color(0xFF24242B))
                    val sx = size.width * opt.trimStartMs / dur
                    val ex = size.width * opt.trimEndMs / dur
                    drawRect(Color(0x338B7CFF), Offset(sx, 0f), androidx.compose.ui.geometry.Size(ex - sx, size.height))
                    tapData?.taps?.forEach {
                        val x = size.width * it.tMs / dur
                        drawLine(Accent, Offset(x, size.height * 0.2f), Offset(x, size.height * 0.8f), 3f)
                    }
                    val px = size.width * pos / dur
                    drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 4f)
                }
                Slider(
                    value = pos.toFloat().coerceIn(0f, dur.toFloat()),
                    onValueChange = { player.seekTo(it.toLong()); pos = it.toLong() },
                    valueRange = 0f..dur.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                )
                Text("Trim: ${Recordings.fmt(opt.trimStartMs)} – ${Recordings.fmt(opt.trimEndMs)}",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                RangeSlider(
                    value = opt.trimStartMs.toFloat()..opt.trimEndMs.toFloat(),
                    onValueChange = { r ->
                        val a = r.start.toLong()
                        val b = r.endInclusive.toLong()
                        if (b - a >= 1000) opt = opt.copy(trimStartMs = a, trimEndMs = b)
                    },
                    valueRange = 0f..dur.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
            }

            // auto-zoom
            SectionCard {
                ToggleLine("Auto-zoom", if (hasTaps) "$moments zoom moments mile" else "Taps capture nahi hue", opt.autoZoom, hasTaps) {
                    opt = opt.copy(autoZoom = it)
                }
                if (opt.autoZoom) {
                    Text("Zoom level  ${"%.1f".format(opt.zoomLevel)}x", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Slider(
                        value = opt.zoomLevel, valueRange = 1.3f..2.6f,
                        onValueChange = { opt = opt.copy(zoomLevel = it) },
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    )
                }
                ToggleLine("Tap ripples", "Har tap par ring effect", opt.ripples, hasTaps) {
                    opt = opt.copy(ripples = it)
                }
                if (!hasTaps) NoteRow("Recording se pehle Accessibility me \"Recordly tap tracker\" on karo, tab taps save honge.")
            }

            // background
            SectionCard {
                Text("Background", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Backgrounds.all.forEach { bg ->
                        val sel = bg == opt.bg
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (bg.c1 == 0) Brush.linearGradient(listOf(Color(0xFF2A2A31), Color(0xFF2A2A31)))
                                    else Brush.linearGradient(listOf(Color(bg.c1), Color(bg.c2)))
                                )
                                .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color(0xFF3A3A44), CircleShape)
                                .clickable { opt = opt.copy(bg = bg) },
                            contentAlignment = Alignment.Center,
                        ) { if (bg.c1 == 0) Text("–", color = Muted) }
                    }
                }
                if (opt.bg.c1 != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Padding", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Slider(
                        value = opt.padding, valueRange = 0.02f..0.12f,
                        onValueChange = { opt = opt.copy(padding = it) },
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    )
                }
            }

            // speed
            SectionCard {
                Text("Speed", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    listOf("0.5x" to 0.5f, "1x" to 1f, "1.5x" to 1.5f, "2x" to 2f),
                    opt.speed,
                ) { opt = opt.copy(speed = it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
        )
    }
}
