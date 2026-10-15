package app.recordly.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onOpenEditor: (File) -> Unit) {
    val ctx = LocalContext.current
    val recording by RecorderState.recording.collectAsState()
    val elapsed by RecorderState.elapsedSec.collectAsState()
    val last by RecorderState.lastFile.collectAsState()

    var a11yOn by remember { mutableStateOf(isTapServiceEnabled(ctx)) }
    var overlayOn by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                a11yOn = isTapServiceEnabled(ctx)
                overlayOn = Settings.canDrawOverlays(ctx)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            RecorderState.lastFile.value = null
            val i = Intent(ctx, RecordingService::class.java)
                .setAction(RecordingService.ACTION_START)
                .putExtra(RecordingService.EXTRA_CODE, r.resultCode)
                .putExtra(RecordingService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(ctx, i)
            (ctx as? Activity)?.moveTaskToBack(true)
        }
    }

    fun launchCapture() {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { launchCapture() }

    fun onRecordClick() {
        if (recording) {
            RecordingService.stop(ctx)
            return
        }
        if (AppSettings.cam && !Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
            return
        }
        val need = buildList {
            if (AppSettings.mic) add(Manifest.permission.RECORD_AUDIO)
            if (AppSettings.cam) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) launchCapture() else permLauncher.launch(need.toTypedArray())
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Recordly",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Beautiful screen recordings, ab mobile par.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        RecordButton(recording = recording, onClick = ::onRecordClick)
        Spacer(Modifier.height(14.dp))
        Text(
            if (recording) "Recording  ${Recordings.fmt(elapsed * 1000L)}" else "Tap karke recording shuru karo",
            color = if (recording) RecRed else Muted,
            fontWeight = if (recording) FontWeight.SemiBold else FontWeight.Normal,
        )

        val done = last
        if (done != null && !recording) {
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Text("Recording ready", fontWeight = FontWeight.SemiBold)
                Text(
                    "Gallery me Movies/Recordly me bhi save ho gayi.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenEditor(File(done)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Editor me kholo") }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionCard {
            SwitchRow(Icons.Filled.Mic, "Microphone", "Apni awaaz record karo", AppSettings.mic) {
                AppSettings.mic = it; AppSettings.save()
            }
            SwitchRow(Icons.Filled.Videocam, "Webcam bubble", "Front camera ka floating bubble. Tap = bada/chhota", AppSettings.cam) {
                AppSettings.cam = it; AppSettings.save()
            }
            SwitchRow(Icons.Filled.TouchApp, "Tap ripples", "Editor me taps par ripple effect", AppSettings.ripples) {
                AppSettings.ripples = it; AppSettings.save()
            }
            SwitchRow(Icons.Filled.AutoAwesome, "Auto-zoom", "Taps ki taraf smooth zoom", AppSettings.autoZoom) {
                AppSettings.autoZoom = it; AppSettings.save()
            }
        }

        if (!a11yOn && (AppSettings.autoZoom || AppSettings.ripples)) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Tap tracking band hai", fontWeight = FontWeight.SemiBold)
                Text(
                    "Auto-zoom aur ripples ke liye Accessibility me \"Recordly tap tracker\" on karo. Bina iske recording normal chalegi, bas zoom/ripple nahi banenge.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    border = BorderStroke(1.dp, Accent),
                ) { Text("Accessibility settings kholo", color = Accent) }
            }
        }
        if (AppSettings.cam && !overlayOn) {
            Spacer(Modifier.height(12.dp))
            NoteRow("Webcam bubble ke liye \"Display over other apps\" permission chahiye. Record dabane par settings khulengi.")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val inner by animateDpAsState(if (recording) 56.dp else 108.dp, label = "inner")
    Box(
        Modifier
            .size(180.dp)
            .border(3.dp, if (recording) RecRed else Color3, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(if (recording) RoundedCornerShape(14.dp) else CircleShape)
                .background(RecRed)
        )
    }
}

private val Color3 = androidx.compose.ui.graphics.Color(0xFF3A3A44)
