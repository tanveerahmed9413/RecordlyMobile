package app.recordly.mobile

import android.graphics.Matrix

/**
 * Taps se auto-zoom timeline banata hai: paas-paas ke taps ek "moment" me cluster hote hain,
 * zoom smoothly andar jaata hai, latest tap ki taraf focus slide karta hai, phir bahar aata hai.
 */
class ZoomPlan(taps: List<Tap>, private val level: Float) {

    data class State(val scale: Float, val fx: Float, val fy: Float)

    private class Seg(val start: Long, val end: Long, val pts: List<Tap>)

    private val segs = ArrayList<Seg>()

    val momentCount: Int get() = segs.size

    init {
        val sorted = taps.sortedBy { it.tMs }
        var cur = ArrayList<Tap>()
        for (t in sorted) {
            if (cur.isNotEmpty() && t.tMs - cur.last().tMs > 1800) {
                segs.add(makeSeg(cur))
                cur = ArrayList()
            }
            cur.add(t)
        }
        if (cur.isNotEmpty()) segs.add(makeSeg(cur))
    }

    private fun makeSeg(p: List<Tap>) = Seg(maxOf(0L, p.first().tMs - 300), p.last().tMs + 1500, p)

    /** fx, fy: focus point 0..1 (y neeche ki taraf badhta hai). */
    fun at(tMs: Long, w: Int, h: Int): State {
        val seg = segs.firstOrNull { tMs in it.start..it.end } ?: return IDLE
        val inP = smooth((tMs - seg.start) / 400f)
        val outP = smooth((seg.end - tMs) / 500f)
        val k = minOf(inP, outP)
        val idx = seg.pts.indexOfLast { it.tMs <= tMs }
        val cur = seg.pts[maxOf(idx, 0)]
        val prev = if (idx > 0) seg.pts[idx - 1] else cur
        val u = if (idx < 0) 0f else smooth((tMs - cur.tMs) / 450f)
        val fx = (prev.x + (cur.x - prev.x) * u) / w.toFloat()
        val fy = (prev.y + (cur.y - prev.y) * u) / h.toFloat()
        return State(1f + (level - 1f) * k, fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f))
    }

    companion object {
        val IDLE = State(1f, 0.5f, 0.5f)

        private fun smooth(x: Float): Float {
            val c = x.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }

        /** Zoom ka center, NDC (-1..1, y upar) me, frame ke bahar na jaane de. */
        fun center(s: State): Pair<Float, Float> {
            if (s.scale <= 1.001f) return 0f to 0f
            val lim = 1f - 1f / s.scale
            val cx = (s.fx * 2f - 1f).coerceIn(-lim, lim)
            val cy = (-(s.fy * 2f - 1f)).coerceIn(-lim, lim)
            return cx to cy
        }

        fun matrix(s: State): Matrix {
            val m = Matrix()
            if (s.scale <= 1.001f) return m
            val (cx, cy) = center(s)
            m.postTranslate(-cx, -cy)
            m.postScale(s.scale, s.scale)
            return m
        }
    }
}
