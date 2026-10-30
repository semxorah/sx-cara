package com.sxgang.sxcar

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Um app instalado, já resolvido. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val launchIntent: Intent,
    val icon: Drawable?,
)

/** Um botão grande da tela inicial. */
data class Tile(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
    /** Pacotes conhecidos, em ordem de preferência. */
    val packages: List<String> = emptyList(),
    /** Palavras procuradas no NOME do app (minúsculas, sem acento). */
    val keywords: List<String> = emptyList(),
    /** Ação de sistema (ex.: abrir configurações de Wi-Fi) usada se nenhum app for achado. */
    val systemAction: String? = null,
    /** Tela embutida do próprio SX CAR (tem prioridade sobre tudo). */
    val builtin: ((Context) -> Unit)? = null,
)

private fun open(ctx: Context, cls: Class<*>, vararg extras: Pair<String, Any>) {
    val i = Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    extras.forEach { (k, v) -> when (v) { is String -> i.putExtra(k, v); is Boolean -> i.putExtra(k, v) } }
    ctx.startActivity(i)
}

val SxGreen = Color(0xFF5AF2A0)
val SxPurple = Color(0xFFB07CFF)
val SxGold = Color(0xFFF2B94A)
val SxBlue = Color(0xFF5AC8F2)
val SxRed = Color(0xFFFF6B6B)
val SxDark = Color(0xFF08080C)
val SxCard = Color(0xFF15151C)
val SxCard2 = Color(0xFF1E1E28)
val SxText = Color(0xFFF2F2F5)
val SxMuted = Color(0xFF8A8A99)

/**
 * Tiles padrão. Os pacotes são os mais comuns em multimídias Unisoc/TS/FYT;
 * se nenhum existir, o app é achado pelo nome; se ainda assim não achar,
 * o botão abre a lista pra você escolher (e lembra a escolha).
 */
val DefaultTiles = listOf(
    Tile(
        id = "radio", label = "Rádio", icon = Icons.Filled.Radio, color = SxGreen,
        packages = listOf(
            "com.ts.radio", "com.syu.radio", "com.android.radio", "com.car.radio",
            "com.unisoc.radio", "com.ks.radio", "com.zhonghong.radio", "com.allgo.radio",
            "com.fyt.radio", "com.hct.radio", "com.wwc2.radio", "com.ecar.radio",
        ),
        keywords = listOf("radio", "fm"),
    ),
    Tile(
        id = "usb", label = "USB", icon = Icons.Filled.Usb, color = SxGold,
        builtin = { open(it, FilesActivity::class.java, "usb" to true) },
    ),
    Tile(
        id = "files", label = "Arquivos", icon = Icons.Filled.Folder, color = SxGold,
        builtin = { open(it, FilesActivity::class.java) },
    ),
    Tile(
        id = "video", label = "Vídeos", icon = Icons.Filled.PlayCircle, color = SxRed,
        builtin = { open(it, WebActivity::class.java, "url" to "https://m.youtube.com") },
    ),
    Tile(
        id = "music", label = "Músicas", icon = Icons.Filled.MusicNote, color = SxPurple,
        builtin = { open(it, WebActivity::class.java, "url" to "https://music.youtube.com") },
    ),
    Tile(
        id = "gps", label = "GPS", icon = Icons.Filled.Navigation, color = SxBlue,
        packages = listOf("com.waze", "com.google.android.apps.maps", "ru.yandex.yandexnavi", "com.here.app.maps"),
        keywords = listOf("waze", "maps", "mapa", "navega", "gps"),
    ),
    Tile(
        id = "bt", label = "Bluetooth", icon = Icons.Filled.Bluetooth, color = SxBlue,
        packages = listOf(
            "com.ts.bt", "com.syu.bt", "com.android.bluetooth.phone", "com.car.bt",
            "com.unisoc.bt", "com.ks.bt", "com.zhonghong.bt", "com.fyt.bt", "com.wwc2.bt",
        ),
        keywords = listOf("bluetooth", "bt phone", "telefone", "phone"),
        systemAction = Settings.ACTION_BLUETOOTH_SETTINGS,
    ),
    Tile(
        id = "camera", label = "Câmera traseira", icon = Icons.Filled.Videocam, color = SxGreen,
        packages = listOf(
            "com.ts.cameraview", "com.ts.camera", "com.syu.camera", "com.car.camera",
            "com.unisoc.camera", "com.ks.camera", "com.zhonghong.camera", "com.fyt.camera",
        ),
        keywords = listOf("camera", "reverse", "rear", "ré"),
    ),
    Tile(
        id = "camera2", label = "Câmera auxiliar", icon = Icons.Filled.CameraAlt, color = SxGreen,
        packages = listOf("com.ts.frontcamera", "com.ts.dvr", "com.syu.dvr", "com.car.dvr", "com.unisoc.dvr"),
        keywords = listOf("dvr", "front", "aux", "360", "dashcam"),
    ),
    Tile(
        id = "settings", label = "Configurações", icon = Icons.Filled.Settings, color = SxMuted,
        packages = listOf("com.android.settings"),
        systemAction = Settings.ACTION_SETTINGS,
    ),
    Tile(
        id = "apps", label = "Todos os apps", icon = Icons.Filled.Apps, color = SxText,
    ),
)

/** Remove acentos e deixa minúsculo, pra comparar nomes. */
fun String.norm(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase()

class AppResolver(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val prefs = ctx.getSharedPreferences("sxcar", Context.MODE_PRIVATE)

    /** Todos os apps que aparecem numa gaveta normal, em ordem alfabética. */
    fun allApps(): List<AppEntry> {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                val pkg = it.activityInfo.packageName
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, it.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                AppEntry(it.loadLabel(pm).toString(), pkg, launch, it.loadIcon(pm))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.norm() }
    }

    fun chosenPackage(tileId: String): String? = prefs.getString("tile_$tileId", null)
    fun choose(tileId: String, pkg: String?) {
        prefs.edit().apply { if (pkg == null) remove("tile_$tileId") else putString("tile_$tileId", pkg) }.apply()
    }
    fun isHidden(tileId: String) = prefs.getBoolean("hide_$tileId", false)
    fun setHidden(tileId: String, hidden: Boolean) = prefs.edit().putBoolean("hide_$tileId", hidden).apply()

    /** Decide o que o botão abre. */
    fun resolve(tile: Tile, apps: List<AppEntry>): AppEntry? {
        chosenPackage(tile.id)?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
        for (pkg in tile.packages) apps.firstOrNull { it.packageName == pkg }?.let { return it }
        for (kw in tile.keywords) {
            val k = kw.norm()
            // palavras muito curtas só batem como palavra inteira, pra "re" não achar "Reader"
            apps.firstOrNull { a ->
                val n = a.label.norm()
                if (k.length <= 2) n.split(" ", "-", "_").any { it == k } else n.contains(k)
            }?.let { return it }
        }
        return null
    }

    fun launch(tile: Tile, apps: List<AppEntry>): Boolean {
        tile.builtin?.let { it(ctx); return true }
        resolve(tile, apps)?.let { ctx.startActivity(it.launchIntent); return true }
        tile.systemAction?.let {
            runCatching { ctx.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onSuccess { return true }
        }
        return false
    }
}
