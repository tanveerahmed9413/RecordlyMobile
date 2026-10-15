package app.recordly.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF0B0B0D)
val Panel = Color(0xFF151519)
val PanelHi = Color(0xFF1E1E24)
val Accent = Color(0xFF8B7CFF)
val RecRed = Color(0xFFFF4D4D)
val Muted = Color(0xFF9A9AA5)

private val Scheme = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Ink,
    secondary = Accent,
    background = Ink,
    onBackground = Color(0xFFF4F4F5),
    surface = Panel,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = PanelHi,
    onSurfaceVariant = Muted,
    outline = Color(0xFF2A2A31),
)

@Composable
fun RecordlyTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme() // app hamesha dark rehti hai
    MaterialTheme(colorScheme = Scheme, content = content)
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = Muted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun NoteRow(text: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = Accent)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
