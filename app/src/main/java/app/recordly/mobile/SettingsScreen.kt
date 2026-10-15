package app.recordly.mobile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = Ink,
                ),
            )
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

        SectionCard {
            Text("Resolution", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                listOf("720p" to 1280, "1080p" to 1920, "1440p" to 2560),
                AppSettings.maxSide,
            ) { AppSettings.maxSide = it; AppSettings.save() }

            Spacer(Modifier.height(16.dp))
            Text("Frame rate", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf("30 fps" to 30, "60 fps" to 60), AppSettings.fps) {
                AppSettings.fps = it; AppSettings.save()
            }

            Spacer(Modifier.height(16.dp))
            Text("Countdown", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf("Off" to 0, "3 sec" to 3, "5 sec" to 5), AppSettings.countdown) {
                AppSettings.countdown = it; AppSettings.save()
            }
            NoteRow("Countdown dikhane ke liye \"Display over other apps\" permission chahiye.")
        }

        SectionCard {
            Text("Tap tracking", fontWeight = FontWeight.SemiBold)
            Text(
                if (isTapServiceEnabled(ctx)) "Chalu hai" else "Band hai",
                color = if (isTapServiceEnabled(ctx)) Accent else Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Accessibility settings")
            }
        }

        SectionCard {
            Text("About", fontWeight = FontWeight.SemiBold)
            Text(
                "Recordly Mobile ek unofficial mobile take hai, desktop app Recordly (recordly.dev) se inspired. " +
                    "Desktop wale cursor effects ki jagah yaha tap-based auto-zoom aur ripples hain.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
