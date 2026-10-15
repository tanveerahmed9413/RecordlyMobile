package app.recordly.mobile

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Mobile par cursor nahi hota, isliye Recordly ke "cursor auto-zoom" ki jagah
 * hum click events ke view-bounds ka center lete hain. Sirf recording ke dauran active.
 */
class TapAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !RecorderState.recording.value) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        ) return
        val node = event.source ?: return
        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.isEmpty) RecorderState.addTap(r.centerX(), r.centerY())
    }

    override fun onInterrupt() {}
}

fun isTapServiceEnabled(ctx: Context): Boolean {
    val me = ComponentName(ctx, TapAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}
