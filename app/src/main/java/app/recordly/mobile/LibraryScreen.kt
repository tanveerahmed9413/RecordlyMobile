package app.recordly.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun shareFile(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share recording"))
}

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, onOpenEditor: (File) -> Unit) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(Unit) { files = withContext(Dispatchers.IO) { Recordings.list(ctx) } }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Library", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("Apni recordings kholo, edit karo, share karo.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.height(14.dp))

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Abhi koi recording nahi hai.", color = Muted)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(files, key = { it.absolutePath }) { f ->
                RecordingCard(
                    file = f,
                    onOpen = { onOpenEditor(f) },
                    onDelete = {
                        Recordings.delete(f)
                        files = files.filterNot { it == f }
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordingCard(file: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val data by produceState<Pair<Bitmap?, VideoInfo>?>(initialValue = null, key1 = file) {
        value = withContext(Dispatchers.IO) { Recordings.thumbnail(file) to Recordings.info(file) }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .clickable(onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(180.dp).background(PanelHi)) {
            val bmp = data?.first
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            data?.second?.let {
                Text(
                    Recordings.fmt(it.durMs),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "%.1f MB".format(file.length() / 1_048_576f),
                color = Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { shareFile(ctx, file) }) {
                Icon(Icons.Filled.Share, "Share", tint = Muted)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = Muted)
            }
        }
    }
}
