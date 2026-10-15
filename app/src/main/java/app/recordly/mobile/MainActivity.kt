package app.recordly.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.init(applicationContext)
        setContent { RecordlyTheme { App() } }
    }
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<File?>(null) }

    BackHandler(enabled = editing != null) { editing = null }

    val current = editing
    if (current != null) {
        EditorScreen(file = current, onClose = { editing = null })
        return
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val colors = NavigationBarItemDefaults.colors(
                    indicatorColor = PanelHi,
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                )
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                    icon = { Icon(Icons.Filled.FiberManualRecord, null) }, label = { Text("Record") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                    icon = { Icon(Icons.Filled.VideoLibrary, null) }, label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") },
                )
            }
        },
    ) { inner ->
        val m = Modifier.padding(inner)
        when (tab) {
            0 -> HomeScreen(m, onOpenEditor = { editing = it })
            1 -> LibraryScreen(m, onOpenEditor = { editing = it })
            else -> SettingsScreen(m)
        }
    }
}
