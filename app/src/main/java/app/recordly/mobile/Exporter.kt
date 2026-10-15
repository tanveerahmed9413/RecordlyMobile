@file:OptIn(UnstableApi::class)

package app.recordly.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

data class BgStyle(val name: String, val c1: Int, val c2: Int)

object Backgrounds {
    val all = listOf(
        BgStyle("None", 0, 0),
        BgStyle("Midnight", 0xFF0B0B14.toInt(), 0xFF26264D.toInt()),
        BgStyle("Aurora", 0xFF0F2027.toInt(), 0xFF2C7A7B.toInt()),
        BgStyle("Sunset", 0xFFFF512F.toInt(), 0xFFDD2476.toInt()),
        BgStyle("Violet", 0xFF3B2A8C.toInt(), 0xFF8B7CFF.toInt()),
        BgStyle("Mono", 0xFF0E0E0E.toInt(), 0xFF3A3A3A.toInt()),
    )
}

data class EditOptions(
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float = 1f,
    val bg: BgStyle = Backgrounds.all[1],
    val padding: Float = 0.06f,        // har side ka hissa (0..0.12)
    val autoZoom: Boolean = true,
    val zoomLevel: Float = 1.8f,
    val ripples: Boolean = true,
)

/** Background gradient (video ke liye transparent "hole" ke saath) + tap ripples. */
private class FxOverlay(
    private val w: Int,
    private val h: Int,
    private val opt: EditOptions,
    private val data: TapData?,
    private val plan: ZoomPlan?,
    private val padScale: Float,
) : BitmapOverlay() {

    private val base: Bitmap = buildBase()

    private fun srcMs(presentationTimeUs: Long): Long =
        opt.trimStartMs + (presentationTimeUs / 1000f * opt.speed).toLong()

    private fun buildBase(): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (opt.bg.c1 == 0) return bmp
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), opt.bg.c1, opt.bg.c2, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        val rw = w * padScale
        val rh = h * padScale
        val l = (w - rw) / 2f
        val t = (h - rh) / 2f
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val r = minOf(w, h) * 0.035f
        c.drawRoundRect(RectF(l, t, l + rw, t + rh), r, r, clear)
        return bmp
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val d = data
        if (!opt.ripples || d == null || d.taps.isEmpty()) return base
        val ms = srcMs(presentationTimeUs)
        val active = d.taps.filter { ms - it.tMs in 0..RIPPLE_MS }
        if (active.isEmpty()) return base

        val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val st = if (opt.autoZoom && plan != null) plan.at(ms, d.w, d.h) else ZoomPlan.IDLE
        val (cx, cy) = ZoomPlan.center(st)
        val unit = maxOf(w, h) * 0.045f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = unit * 0.12f
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (tap in active) {
            val p = (ms - tap.tMs) / RIPPLE_MS.toFloat()
            val nx = tap.x.toFloat() / d.w * 2f - 1f
            val ny = -(tap.y.toFloat() / d.h * 2f - 1f)
            val ox = st.scale * (nx - cx) * padScale
            val oy = st.scale * (ny - cy) * padScale
            val px = (ox + 1f) / 2f * w
            val py = (1f - oy) / 2f * h
            val a = ((1f - p) * 255).toInt().coerceIn(0, 255)
            dot.color = (a / 3 shl 24) or 0x8B7CFF
            ring.color = (a shl 24) or 0xFFFFFF
            val radius = unit * (0.35f + 0.9f * p)
            c.drawCircle(px, py, radius * 0.8f, dot)
            c.drawCircle(px, py, radius, ring)
        }
        return bmp
    }

    companion object {
        const val RIPPLE_MS = 650L
    }
}

object Exporter {

    /** Main thread se call karo. Transformer return hota hai taaki progress poll / cancel ho sake. */
    fun start(
        ctx: Context,
        src: File,
        info: VideoInfo,
        data: TapData?,
        opt: EditOptions,
        out: File,
        onDone: (Result<File>) -> Unit,
    ): Transformer {
        val hasTaps = data != null && data.taps.isNotEmpty()
        val plan = if (hasTaps && opt.autoZoom) ZoomPlan(data!!.taps, opt.zoomLevel) else null
        val hasBg = opt.bg.c1 != 0
        val padScale = if (hasBg) (1f - 2f * opt.padding).coerceIn(0.6f, 1f) else 1f
        val speed = opt.speed

        fun srcMs(t: Long) = opt.trimStartMs + (t / 1000f * speed).toLong()

        val effects = ArrayList<Effect>()
        val audioProcessors = ArrayList<AudioProcessor>()
        if (speed != 1f) {
            // Sabse pehle: baad ke effects ko output-timeline ke timestamps milte hain
            effects.add(SpeedChangeEffect(speed))
            audioProcessors.add(SonicAudioProcessor().apply {
                setSpeed(speed)
                setPitch(1f)
            })
        }
        if (plan != null && data != null) {
            effects.add(MatrixTransformation { t -> ZoomPlan.matrix(plan.at(srcMs(t), data.w, data.h)) })
        }
        if (padScale < 0.999f) {
            effects.add(MatrixTransformation { _ -> Matrix().apply { postScale(padScale, padScale) } })
        }
        if (hasBg || (opt.ripples && hasTaps)) {
            val overlay = FxOverlay(info.w, info.h, opt, data, plan, padScale)
            effects.add(OverlayEffect(ImmutableList.of(overlay)))
        }

        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(opt.trimStartMs)
            .setEndPositionMs(opt.trimEndMs)
            .build()
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(src))
            .setClippingConfiguration(clip)
            .build()
        val edited = EditedMediaItem.Builder(item)
            .setEffects(Effects(audioProcessors, effects))
            .build()

        val transformer = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onDone(Result.success(out))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    onDone(Result.failure(exportException))
                }
            })
            .build()
        transformer.start(edited, out.absolutePath)
        return transformer
    }
}