package com.sxgang.sxcar

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

/**
 * Explorador de arquivos: armazenamento interno + pendrives/cartões.
 * Abra com putExtra("usb", true) para ir direto pro pendrive.
 */
class FilesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val usbOnly = intent.getBooleanExtra("usb", false)
        val filter = intent.getStringExtra("filter")
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = SxGreen)) { Explorer(usbOnly, filter) { finish() } } }
    }
    override fun onResume() {
        super.onResume()
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private data class Root(val name: String, val dir: File, val removable: Boolean)

private fun roots(ctx: android.content.Context): List<Root> {
    val sm = ctx.getSystemService(StorageManager::class.java)
    val out = mutableListOf<Root>()
    for (v in sm.storageVolumes) {
        val dir: File? = if (Build.VERSION.SDK_INT >= 30) v.directory else null
        if (dir != null && dir.exists()) {
            val name = if (v.isPrimary) "Armazenamento interno" else (v.getDescription(ctx) ?: "Pendrive")
            out += Root(name, dir, v.isRemovable)
        }
    }
    // alguns aparelhos montam o pendrive em /mnt/media_rw ou /storage/XXXX-XXXX sem listar
    for (base in listOf("/storage", "/mnt/media_rw", "/mnt/usb", "/mnt/usb_storage")) {
        File(base).listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead() && out.none { it.dir.canonicalPath == f.canonicalPath })
                out += Root("USB ${f.name}", f, true)
        }
    }
    if (out.none { !it.removable }) out += Root("Armazenamento interno", Environment.getExternalStorageDirectory(), false)
    return out
}

private fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

@Composable
private fun Explorer(usbOnly: Boolean, filter: String?, onExit: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(hasAllFiles()) }
    val allRoots = remember(granted) { roots(ctx) }
    val rootList = remember(allRoots, usbOnly) { if (usbOnly) allRoots.filter { it.removable }.ifEmpty { allRoots } else allRoots }
    var stack by remember { mutableStateOf<List<File>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(rootList) { if (rootList.size == 1 && stack.isEmpty()) stack = listOf(rootList[0].dir) }

    // ao voltar da tela de permissão, reconfere
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { granted = hasAllFiles(); refresh++ } }
        lifecycle.lifecycle.addObserver(obs); onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    BackHandler { if (stack.size > 1 || (stack.size == 1 && rootList.size > 1)) stack = stack.dropLast(1) else onExit() }

    Column(Modifier.fillMaxSize().background(SxDark).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (stack.isEmpty() || (stack.size == 1 && rootList.size == 1)) onExit() else stack = stack.dropLast(1) }) {
                Icon(Icons.Filled.ArrowBack, null, tint = SxText)
            }
            Text(
                if (stack.isEmpty()) (when { usbOnly -> "USB"; filter == "video" -> "Vídeos"; filter == "audio" -> "Músicas"; else -> "Arquivos" }) else stack.last().name.ifBlank { "/" },
                color = SxText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (!granted) TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }) { Text("Permitir acesso aos arquivos", color = SxGreen) }
            IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, null, tint = SxMuted) }
        }
        Spacer(Modifier.height(8.dp))

        if (stack.isEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rootList) { r ->
                    FileRow(if (r.removable) Icons.Filled.Usb else Icons.Filled.Storage, r.name, r.dir.path, true) { stack = listOf(r.dir) }
                }
                if (rootList.none { it.removable }) item {
                    Text("Nenhum pendrive encontrado. Conecte um e toque em atualizar.", color = SxMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        } else {
            val dir = stack.last()
            val entries = remember(dir, refresh, granted) {
                (dir.listFiles()?.toList() ?: emptyList())
                    .filter { !it.name.startsWith(".") }
                    .filter { f ->
                        when (filter) {
                            "video" -> f.isDirectory || f.isVideo()
                            "audio" -> f.isDirectory || f.isAudio()
                            else -> true
                        }
                    }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
            if (entries.isEmpty()) Text(
                if (!granted) "Sem permissão pra ler esta pasta. Toque em \"Permitir acesso aos arquivos\"." else "Pasta vazia",
                color = SxMuted, modifier = Modifier.padding(12.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.path }) { f ->
                    val icon = when {
                        f.isDirectory -> Icons.Filled.Folder
                        f.isAudio() -> Icons.Filled.MusicNote
                        f.isVideo() -> Icons.Filled.Movie
                        f.isImage() -> Icons.Filled.Image
                        f.extension.lowercase() == "apk" -> Icons.Filled.Android
                        else -> Icons.Filled.InsertDriveFile
                    }
                    FileRow(icon, f.name, if (f.isDirectory) "" else f.length().human(), f.isDirectory) {
                        if (f.isDirectory) stack = stack + f else openFile(ctx, f)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, dir: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SxCard).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (dir) SxGreen else SxMuted, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SxText, fontSize = 16.sp, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, color = SxMuted, fontSize = 12.sp, maxLines = 1)
        }
        if (dir) Icon(Icons.Filled.ChevronRight, null, tint = SxMuted)
    }
}

private fun File.isAudio() = extension.lowercase() in setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "wma")
private fun File.isVideo() = extension.lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts")
private fun File.isImage() = extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun Long.human(): String = when {
    this >= 1L shl 30 -> "%.1f GB".format(this / (1L shl 30).toDouble())
    this >= 1L shl 20 -> "%.1f MB".format(this / (1L shl 20).toDouble())
    this >= 1L shl 10 -> "%.0f KB".format(this / 1024.0)
    else -> "$this B"
}

private fun openFile(ctx: android.content.Context, f: File) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    val uri = runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f) }.getOrElse { Uri.fromFile(f) }
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, "Nenhum app abre este arquivo", Toast.LENGTH_SHORT).show() }
}
