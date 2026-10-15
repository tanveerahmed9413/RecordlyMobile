package app.recordly.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : LifecycleService() {

    companion object {
        const val ACTION_START = "app.recordly.mobile.START"
        const val ACTION_STOP = "app.recordly.mobile.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 42

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var outFile: File? = null
    private var vw = 0
    private var vh = 0
    private var starting = false

    private var countdownView: TextView? = null
    private var bubble: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleBig = true

    private val ticker = object : Runnable {
        override fun run() {
            if (!RecorderState.recording.value) return
            RecorderState.elapsedSec.value =
                ((SystemClock.elapsedRealtime() - RecorderState.startElapsed) / 1000).toInt()
            handler.postDelayed(this, 500)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startFlow(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- start

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startFlow(intent: Intent) {
        if (RecorderState.recording.value || starting) return
        AppSettings.init(this)

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return
        }

        val micOk = AppSettings.mic && granted(Manifest.permission.RECORD_AUDIO)
        val camOk = AppSettings.cam && granted(Manifest.permission.CAMERA) &&
            Settings.canDrawOverlays(this)

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (micOk) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (camOk) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

        starting = true
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)

        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data)
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { if (RecorderState.recording.value) stopRecording() }
                }
            }
            p.registerCallback(cb, handler)
            projection = p
            projectionCallback = cb
        } catch (e: Exception) {
            fail("Screen capture start nahi hua: ${e.message}")
            return
        }

        val cd = AppSettings.countdown
        if (cd > 0 && Settings.canDrawOverlays(this)) {
            runCountdown(cd) { beginCapture(micOk, camOk) }
        } else {
            beginCapture(micOk, camOk)
        }
    }

    private fun runCountdown(from: Int, done: () -> Unit) {
        val wm = getSystemService(WindowManager::class.java)
        val tv = TextView(this).apply {
            textSize = 96f
            setTextColor(Color.WHITE)
            setShadowLayer(12f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(tv, lp)
            countdownView = tv
        } catch (e: Exception) {
            done()
            return
        }
        var n = from
        val step = object : Runnable {
            override fun run() {
                if (n > 0) {
                    tv.text = n.toString()
                    n--
                    handler.postDelayed(this, 1000)
                } else {
                    removeCountdown()
                    // overlay hatne ka frame nikalne ke liye thoda ruko
                    handler.postDelayed({ done() }, 250)
                }
            }
        }
        step.run()
    }

    private fun removeCountdown() {
        countdownView?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        countdownView = null
    }

    private fun even(v: Float): Int = (v.toInt() / 2) * 2

    private fun beginCapture(micOk: Boolean, camOk: Boolean) {
        val proj = projection ?: return fail("Projection missing")
        try {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val realW = bounds.width()
            val realH = bounds.height()
            val scale = minOf(1f, AppSettings.maxSide.toFloat() / maxOf(realW, realH))
            vw = even(realW * scale).coerceAtLeast(2)
            vh = even(realH * scale).coerceAtLeast(2)
            val fps = AppSettings.fps

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(Recordings.dir(this), "Recordly_$ts.mp4")
            outFile = file

            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            if (micOk) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setOutputFile(file.absolutePath)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (micOk) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000)
                rec.setAudioSamplingRate(44_100)
            }
            rec.setVideoSize(vw, vh)
            rec.setVideoFrameRate(fps)
            rec.setVideoEncodingBitRate((vw.toLong() * vh * fps / 10).toInt().coerceIn(2_000_000, 25_000_000))
            rec.prepare()
            recorder = rec

            display = proj.createVirtualDisplay(
                "RecordlyDisplay", vw, vh, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null
            )

            RecorderState.taps.clear()
            RecorderState.startElapsed = SystemClock.elapsedRealtime()
            rec.start()
            RecorderState.elapsedSec.value = 0
            RecorderState.recording.value = true
            starting = false
            handler.post(ticker)

            if (camOk) showBubble()
        } catch (e: Exception) {
            fail("Recording start nahi hui: ${e.message}")
        }
    }

    // ----------------------------------------------------------------- stop

    private fun stopRecording() {
        val wasRecording = RecorderState.recording.value
        RecorderState.recording.value = false
        handler.removeCallbacks(ticker)
        removeCountdown()
        removeBubble()

        var file = outFile
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // bahut chhoti recording: file invalid hoti hai
            file?.delete()
            file = null
        }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { display?.release() }
        display = null
        projectionCallback?.let { runCatching { projection?.unregisterCallback(it) } }
        runCatching { projection?.stop() }
        projection = null
        projectionCallback = null

        if (wasRecording && file != null && file.exists()) {
            Recordings.writeTaps(file, TapData(vw, vh, RecorderState.taps.toList()))
            Recordings.saveToMovies(this, file)
            RecorderState.lastFile.value = file.absolutePath
        }
        outFile = null
        starting = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        runCatching { recorder?.release() }
        recorder = null
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        projection = null
        outFile?.delete()
        outFile = null
        starting = false
        RecorderState.recording.value = false
        removeCountdown()
        removeBubble()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeCountdown()
        removeBubble()
        if (RecorderState.recording.value) RecorderState.recording.value = false
        super.onDestroy()
    }

    // --------------------------------------------------------- notification

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Recordly Mobile")
            .setContentText("Screen record ho rahi hai")
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .build()
    }

    // --------------------------------------------------------- webcam bubble

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        val wm = getSystemService(WindowManager::class.java)
        val preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val frame = FrameLayout(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            elevation = dp(6).toFloat()
            addView(preview, FrameLayout.LayoutParams(-1, -1))
        }
        val size = dp(150)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        // drag + tap = bada/chhota (Recordly ke expanding bubble jaisa)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) moved = true
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    runCatching { wm.updateViewLayout(frame, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    bubbleBig = !bubbleBig
                    val s = if (bubbleBig) dp(150) else dp(84)
                    lp.width = s
                    lp.height = s
                    runCatching { wm.updateViewLayout(frame, lp) }
                }
            }
            true
        }

        try {
            wm.addView(frame, lp)
            bubble = frame
            bubbleParams = lp
        } catch (e: Exception) {
            return
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val useCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
            } catch (e: Exception) {
                // camera na mile to bubble khali reh jayega
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun removeBubble() {
        bubble?.let { v ->
            runCatching { getSystemService(WindowManager::class.java).removeView(v) }
            runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        }
        bubble = null
        bubbleParams = null
    }
}
