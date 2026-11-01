package com.sxgang.sxcar

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = SxGreen, background = SxDark, surface = SxCard)) { Launcher() } }
    }

    override fun onResume() {
        super.onResume()
        // esconde barra de status/navegação; aparecem com um swipe da borda
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private enum class Screen { HOME, DRAWER }

@Composable
private fun Launcher() {
    val ctx = LocalContext.current
    val resolver = remember { AppResolver(ctx) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editing by remember { mutableStateOf<Tile?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var intro by remember { mutableStateOf(!IntroState.shown) }

    LaunchedEffect(reloadKey) { apps = resolver.allApps() }
    // ao voltar de outra tela (ex.: escolha de pasta), atualiza os botões
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) reloadKey++ }
        lifecycle.lifecycle.addObserver(obs); onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    // recarrega quando um app é instalado/removido
    DisposableEffect(Unit) {
        val rec = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { reloadKey++ }
        }
        val f = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ctx.registerReceiver(rec, f)
        onDispose { ctx.unregisterReceiver(rec) }
    }

    if (intro) {
        Intro { IntroState.shown = true; intro = false }
        return
    }

    Box(Modifier.fillMaxSize().background(SxDark)) {
        when (screen) {
            Screen.HOME -> Home(
                apps = apps, resolver = resolver, showHidden = showHidden,
                onOpenDrawer = { screen = Screen.DRAWER },
                onEdit = { editing = it },
                onSettings = { showSettings = true },
                reloadKey = reloadKey,
            )
            Screen.DRAWER -> {
                BackHandler { screen = Screen.HOME }
                Drawer(apps = apps, title = "Todos os apps", onBack = { screen = Screen.HOME }) {
                    ctx.startActivity(it.launchIntent)
                }
            }
        }
    }

    editing?.let { tile ->
        TileOptions(tile, resolver, apps,
            onDismiss = { editing = null; reloadKey++ },
        )
    }

    if (showSettings) {
        SettingsDialog(
            showHidden = showHidden,
            onToggleHidden = { showHidden = it },
            onReplayIntro = { showSettings = false; intro = true },
            onDismiss = { showSettings = false; reloadKey++ },
        )
    }
}

// ---------------------------------------------------------------- HOME

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Home(
    apps: List<AppEntry>, resolver: AppResolver, showHidden: Boolean,
    onOpenDrawer: () -> Unit, onEdit: (Tile) -> Unit, onSettings: () -> Unit, reloadKey: Int,
) {
    val ctx = LocalContext.current
    val tiles = remember(reloadKey, showHidden) { DefaultTiles.filter { showHidden || !resolver.isHidden(it.id) } }

    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        // ---- painel esquerdo: relógio, status, volume, brilho
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            Clock()
            // cabeça do alien preenchendo o espaço entre o relógio e os controles
            val breathe = rememberInfiniteTransition(label = "bg").animateFloat(
                0.25f, 0.55f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                val h = minOf(maxHeight, maxWidth / 0.757f)
                val w = h * 0.757f
                Box(
                    Modifier.size(w * 1.9f, h * 1.4f).alpha(breathe.value)
                        .background(Brush.radialGradient(listOf(Color(0x995AF2A0), Color(0x225AF2A0), Color.Transparent)))
                )
                Image(painterResource(R.drawable.alien_head), null, Modifier.size(w, h))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WifiStatus()
                VolumeControl()
                BrightnessControl()
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SX CAR", color = SxGreen, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Tune, null, tint = SxMuted) }
            }
        }

        Spacer(Modifier.width(22.dp))

        // ---- grade de botões
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(tiles, key = { it.id }) { tile ->
                val target = remember(apps, reloadKey) { resolver.resolve(tile, apps) }
                val hidden = resolver.isHidden(tile.id)
                val folder = resolver.chosenFolder(tile.id)
                val subtitle = when {
                    tile.id == "apps" -> "${apps.size} instalados"
                    folder != null -> "Pasta: " + folder.substringAfterLast('/')
                    tile.builtin != null -> "SX CAR"
                    target != null -> target.label
                    tile.systemAction != null -> "Sistema"
                    else -> "Toque pra escolher"
                }
                TileCard(
                    tile = tile, subtitle = subtitle, dimmed = hidden,
                    onClick = {
                        when {
                            tile.id == "apps" -> onOpenDrawer()
                            folder != null || tile.builtin != null -> resolver.launch(tile, apps)
                            target == null && tile.systemAction == null -> onEdit(tile)
                            else -> if (!resolver.launch(tile, apps)) onEdit(tile)
                        }
                    },
                    onLongClick = { onEdit(tile) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCard(tile: Tile, subtitle: String, dimmed: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val alpha = if (dimmed) 0.35f else 1f
    Column(
        Modifier
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(22.dp))
            .background(SxCard)
            .border(1.dp, tile.color.copy(alpha = 0.25f * alpha), RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tile.color.copy(alpha = 0.16f * alpha)),
            contentAlignment = Alignment.Center,
        ) { Icon(tile.icon, null, tint = tile.color.copy(alpha = alpha), modifier = Modifier.size(28.dp)) }
        Column {
            Text(tile.label, color = SxText.copy(alpha = alpha), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = SxMuted.copy(alpha = alpha), fontSize = 12.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- painel esquerdo

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val loc = Locale("pt", "BR")
    val time = remember(now) { SimpleDateFormat("HH:mm", loc).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d 'de' MMMM", loc).format(now).replaceFirstChar { it.uppercase() } }
    Column {
        Text(time, color = SxText, fontSize = 64.sp, fontWeight = FontWeight.Light, lineHeight = 64.sp)
        Text(date, color = SxMuted, fontSize = 15.sp)
    }
}

@Composable
private fun WifiStatus() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("…") }
    var ok by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ok = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            text = when {
                !wm.isWifiEnabled -> "Wi-Fi desligado"
                ok -> {
                    @Suppress("DEPRECATION")
                    val ssid = wm.connectionInfo?.ssid?.trim('"')
                    if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") "Conectado" else ssid
                }
                else -> "Sem conexão"
            }
            delay(4000)
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(SxCard)
            .combinedClickableCompat { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) Icons.Filled.Wifi else Icons.Filled.WifiOff, null, tint = if (ok) SxGreen else SxMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = SxText, fontSize = 14.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@Composable
private fun VolumeControl() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // só relê o volume real quando o dedo NÃO está no slider
    LaunchedEffect(dragging) {
        while (!dragging) { vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat(); delay(1500) }
    }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(SxCard).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VolumeUp, null, tint = SxGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Volume", color = SxMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("${vol.toInt()}", color = SxMuted, fontSize = 12.sp)
        }
        Slider(
            value = vol.coerceIn(0f, max.toFloat()),
            onValueChange = { dragging = true; vol = it },
            onValueChangeFinished = {
                applyVolume(am, vol.toInt().coerceIn(0, max))
                dragging = false
            },
            valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = SxGreen, activeTrackColor = SxGreen, inactiveTrackColor = SxCard2),
            modifier = Modifier.height(30.dp),
        )
    }
}

/** Tenta o ajuste direto; se o painel ignorar, anda passo a passo como os botões físicos. */
private fun applyVolume(am: AudioManager, target: Int) {
    runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI) }
    var cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    var guard = 0
    while (cur != target && guard++ < 40) {
        val dir = if (target > cur) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
        val next = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (next == cur) {
            // último recurso: ajuste genérico (vai pro stream que o sistema considerar ativo)
            runCatching { am.adjustVolume(dir, AudioManager.FLAG_SHOW_UI) }
            val n2 = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (n2 == cur) break
            cur = n2
        } else cur = next
    }
}

@Composable
private fun BrightnessControl() {
    val ctx = LocalContext.current
    var b by remember {
        mutableFloatStateOf(runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(200).toFloat())
    }
    SliderRow(Icons.Filled.LightMode, "Brilho", b, 10f, 255f) { v ->
        b = v
        if (Settings.System.canWrite(ctx)) {
            runCatching {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.toInt())
            }
        } else {
            Toast.makeText(ctx, "Permita o SX CAR alterar configurações pra controlar o brilho", Toast.LENGTH_LONG).show()
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
private fun SliderRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(SxCard).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SxGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = SxMuted, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = SxGreen, activeTrackColor = SxGreen, inactiveTrackColor = SxCard2),
            modifier = Modifier.height(30.dp),
        )
    }
}

// ---------------------------------------------------------------- gaveta de apps

@Composable
private fun Drawer(apps: List<AppEntry>, title: String, onBack: () -> Unit, onPick: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { if (query.isBlank()) apps else apps.filter { it.label.norm().contains(query.norm()) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = SxText) }
            Text(title, color = SxText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Buscar", color = SxMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = SxMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.width(300.dp).height(54.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SxGreen, unfocusedBorderColor = SxCard2, focusedTextColor = SxText, unfocusedTextColor = SxText),
                shape = RoundedCornerShape(14.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.packageName }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(SxCard)
                        .combinedClickableCompat { onPick(app) }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, 52.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(app.label, color = SxText, fontSize = 13.sp, maxLines = 2, lineHeight = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry, size: androidx.compose.ui.unit.Dp) {
    val bmp = remember(app.packageName) { runCatching { app.icon?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(BitmapPainter(bmp), null, Modifier.size(size))
    else Icon(Icons.Filled.Android, null, tint = SxMuted, modifier = Modifier.size(size))
}

// ---------------------------------------------------------------- opções do botão

@Composable
private fun TileOptions(tile: Tile, resolver: AppResolver, apps: List<AppEntry>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(SxDark)) {
                Drawer(apps, "Escolher app para \"${tile.label}\"", onBack = { picking = false }) {
                    resolver.choose(tile.id, it.packageName); resolver.chooseFolder(tile.id, null); onDismiss()
                }
            }
        }
        return
    }
    val current = resolver.resolve(tile, apps)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SxCard,
        title = { Text(tile.label, color = SxText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val folder = resolver.chosenFolder(tile.id)
                Text(
                    when {
                        folder != null -> "Abre a pasta: $folder"
                        tile.builtin != null -> "Abre: explorador do SX CAR"
                        current != null -> "Abre: ${current.label}"
                        else -> "Nenhum app definido"
                    }, color = SxMuted,
                )
                OptBtn("Escolher uma pasta pra este botão", Icons.Filled.Folder) {
                    ctx.startActivity(Intent(ctx, FilesActivity::class.java).putExtra("pickFor", tile.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    onDismiss()
                }
                if (folder != null) OptBtn("Remover a pasta", Icons.Filled.FolderOff) { resolver.chooseFolder(tile.id, null); onDismiss() }
                OptBtn("Escolher qual app abre", Icons.Filled.Apps) { picking = true }
                if (resolver.chosenPackage(tile.id) != null)
                    OptBtn("Voltar ao automático", Icons.Filled.Refresh) { resolver.choose(tile.id, null); onDismiss() }
                if (tile.id != "apps") {
                    if (resolver.isHidden(tile.id)) OptBtn("Mostrar este botão", Icons.Filled.Visibility) { resolver.setHidden(tile.id, false); onDismiss() }
                    else OptBtn("Esconder este botão", Icons.Filled.VisibilityOff) { resolver.setHidden(tile.id, true); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = SxGreen) } },
    )
}

@Composable
private fun OptBtn(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SxCard2)
            .combinedClickableCompat(onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SxGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = SxText, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------- ajustes do launcher

@Composable
private fun SettingsDialog(showHidden: Boolean, onToggleHidden: (Boolean) -> Unit, onReplayIntro: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isDefault = remember { isDefaultLauncher(ctx) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SxCard,
        title = { Text("SX CAR", color = SxText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isDefault)
                    OptBtn("Definir como tela inicial padrão", Icons.Filled.Home) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                else Text("✓ Já é a tela inicial padrão", color = SxGreen)
                OptBtn(if (showHidden) "Ocultar botões escondidos" else "Mostrar botões escondidos", Icons.Filled.Visibility) { onToggleHidden(!showHidden) }
                OptBtn("Permissão de brilho (alterar configurações)", Icons.Filled.LightMode) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                OptBtn("Rever a intro", Icons.Filled.PlayArrow) { IntroState.shown = false; onReplayIntro() }
                OptBtn("Configurações do Android", Icons.Filled.Settings) {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Text("Dica: segure um botão pra escolher a pasta ou o app que ele abre, ou escondê-lo.", color = SxMuted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = SxGreen) } },
    )
}

private fun isDefaultLauncher(ctx: Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val r = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
    return r?.activityInfo?.packageName == ctx.packageName
}
