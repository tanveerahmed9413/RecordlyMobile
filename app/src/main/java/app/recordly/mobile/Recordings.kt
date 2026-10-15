package app.recordly.mobile

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VideoInfo(val durMs: Long, val w: Int, val h: Int)

object Recordings {
    fun dir(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Recordly").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun tapsFile(video: File) =
        File(video.parentFile, video.nameWithoutExtension + ".taps.json")

    fun writeTaps(video: File, d: TapData) {
        val arr = JSONArray()
        d.taps.forEach { arr.put(JSONObject().put("t", it.tMs).put("x", it.x).put("y", it.y)) }
        val obj = JSONObject().put("w", d.w).put("h", d.h).put("taps", arr)
        tapsFile(video).writeText(obj.toString())
    }

    fun readTaps(video: File): TapData? = try {
        val f = tapsFile(video)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val a = o.getJSONArray("taps")
            val list = (0 until a.length()).map {
                val t = a.getJSONObject(it)
                Tap(t.getLong("t"), t.getInt("x"), t.getInt("y"))
            }
            TapData(o.getInt("w"), o.getInt("h"), list)
        }
    } catch (e: Exception) {
        null
    }

    fun delete(video: File) {
        video.delete()
        tapsFile(video).delete()
    }

    fun info(file: File): VideoInfo {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            fun key(k: Int) = r.extractMetadata(k)?.toLongOrNull() ?: 0L
            VideoInfo(
                key(MediaMetadataRetriever.METADATA_KEY_DURATION),
                key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt().coerceAtLeast(2),
                key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt().coerceAtLeast(2),
            )
        } catch (e: Exception) {
            VideoInfo(0, 1080, 1920)
        } finally {
            r.release()
        }
    }

    fun thumbnail(file: File): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    /** Video ko gallery (Movies/Recordly) me copy karta hai. */
    fun saveToMovies(ctx: Context, src: File, displayName: String = src.nameWithoutExtension): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Recordly")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
