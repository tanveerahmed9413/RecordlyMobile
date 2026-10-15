package app.recordly.mobile

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/** Ek tap/click: recording start se kitne ms baad, aur screen par kis pixel pe. */
data class Tap(val tMs: Long, val x: Int, val y: Int)

/** Sidecar data jo video ke saath save hota hai (w/h = video ki size). */
data class TapData(val w: Int, val h: Int, val taps: List<Tap>)

/** Service, accessibility service aur UI ke beech shared state. */
object RecorderState {
    val recording = MutableStateFlow(false)
    val elapsedSec = MutableStateFlow(0)
    val lastFile = MutableStateFlow<String?>(null)
    val taps = CopyOnWriteArrayList<Tap>()

    @Volatile
    var startElapsed = 0L

    fun addTap(x: Int, y: Int) {
        taps.add(Tap(SystemClock.elapsedRealtime() - startElapsed, x, y))
    }
}
