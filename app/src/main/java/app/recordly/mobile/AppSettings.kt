package app.recordly.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppSettings {
    private var sp: SharedPreferences? = null

    var maxSide by mutableIntStateOf(1920)   // 1280 = 720p, 1920 = 1080p, 2560 = 1440p
    var fps by mutableIntStateOf(30)
    var mic by mutableStateOf(true)
    var cam by mutableStateOf(false)
    var countdown by mutableIntStateOf(3)
    var autoZoom by mutableStateOf(true)
    var ripples by mutableStateOf(true)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sp = p
        maxSide = p.getInt("maxSide", 1920)
        fps = p.getInt("fps", 30)
        mic = p.getBoolean("mic", true)
        cam = p.getBoolean("cam", false)
        countdown = p.getInt("countdown", 3)
        autoZoom = p.getBoolean("autoZoom", true)
        ripples = p.getBoolean("ripples", true)
    }

    fun save() {
        sp?.edit()
            ?.putInt("maxSide", maxSide)
            ?.putInt("fps", fps)
            ?.putBoolean("mic", mic)
            ?.putBoolean("cam", cam)
            ?.putInt("countdown", countdown)
            ?.putBoolean("autoZoom", autoZoom)
            ?.putBoolean("ripples", ripples)
            ?.apply()
    }
}
