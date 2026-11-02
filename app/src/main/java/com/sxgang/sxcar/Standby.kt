package com.sxgang.sxcar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

private fun ease(x: Float) = (x.coerceIn(0f, 1f)).let { it * it * (3 - 2 * it) }
private fun seg(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))

/**
 * Descanso de tela: fundo preto, estrelas, relógio grande, cabeça do alien
 * flutuando bem devagar com o brilho respirando e um disco passando de vez em quando.
 * Qualquer toque é tratado por quem chama (via `touch`).
 */
@Composable
fun Screensaver() {
    val head: ImageBitmap = ImageBitmap.imageResource(R.drawable.alien_head)
    var frame by remember { mutableLongStateOf(0L) }
    var start by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { now -> if (start == 0L) start = now; frame = now } }
    val stars = remember { val r = Random(11); List(70) { Triple(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat()) } }
    val t = (frame - start) / 1e9f
    val fadeIn = seg(t, 0f, 1.5f)

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(1000) } }
    val loc = Locale("pt", "BR")
    val time = remember(now) { SimpleDateFormat("HH:mm", loc).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d 'de' MMMM", loc).format(now).replaceFirstChar { it.uppercase() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize().alpha(fadeIn)) {
            val w = size.width; val h = size.height
            for ((sx, sy, sz) in stars) {
                val tw = 0.5f + 0.5f * sin(t * (0.8f + sz) + sx * 20f)
                drawCircle(Color.White.copy(alpha = 0.15f + 0.4f * tw), sz * 1.4f, Offset(sx * w, sy * h))
            }
            // cabeça flutuando em órbita lenta (Lissajous), tamanho moderado
            val hh = h * 0.46f; val hw = hh * head.width / head.height
            val cx = w * 0.5f + sin(t * 0.11f) * w * 0.22f
            val cy = h * 0.50f + sin(t * 0.17f + 1.3f) * h * 0.12f
            val glow = 0.18f + 0.14f * sin(t * 1.3f)
            drawOval(
                Brush.radialGradient(listOf(SxGreen.copy(alpha = glow), Color.Transparent), Offset(cx, cy), hh * 0.95f),
                topLeft = Offset(cx - hh, cy - hh), size = Size(hh * 2, hh * 2),
            )
            drawImage(head, dstOffset = IntOffset((cx - hw / 2).toInt(), (cy - hh / 2).toInt()), dstSize = IntSize(hw.toInt(), hh.toInt()), alpha = 0.9f)
            // um disco cruza a cada ~14 s
            val cyc = (t % 14f) / 14f
            if (cyc < 0.35f) {
                val p = cyc / 0.35f
                val dir = if (((t / 14f).toInt()) % 2 == 0) 1f else -1f
                val x = if (dir > 0) -60f + p * (w + 120f) else w + 60f - p * (w + 120f)
                val y = h * (0.12f + 0.08f * sin(p * 6f))
                translate(x, y) { rotate(dir * 6f) { drawUfo(min(w, h) * 0.03f, 0.75f, t) } }
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(start = 36.dp, top = 64.dp).alpha(fadeIn)) {
            Text(time, color = SxText, fontSize = 84.sp, fontWeight = FontWeight.Thin, lineHeight = 84.sp)
            Text(date, color = SxMuted, fontSize = 18.sp)
        }
        Text(
            "Toque para voltar", color = SxMuted.copy(alpha = 0.6f), fontSize = 13.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).alpha(fadeIn),
        )
    }
}

private const val SHORT_SEC = 7f
private const val SHORT_VOICE_AT = 2.4f

/**
 * Intro curta (7 s) usada ao acordar: feixe rápido, cabeça materializa,
 * saudação com voz, fade.
 */
@Composable
fun ShortIntro(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember { greeting() }
    val head: ImageBitmap = ImageBitmap.imageResource(R.drawable.alien_head)
    var frame by remember { mutableLongStateOf(0L) }
    var start by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var spoke by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> if (start == 0L) start = now; frame = now }
            val t = (frame - start) / 1e9f
            if (!spoke && t >= SHORT_VOICE_AT) { spoke = true; player = playGreeting(ctx) }
            if (t >= SHORT_SEC) { onDone(); break }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }

    val stars = remember { val r = Random(5); List(60) { Triple(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat()) } }
    val t = (frame - start) / 1e9f
    val fadeOut = 1f - seg(t, 6.2f, 7f)

    Box(Modifier.fillMaxSize().background(SxDark)) {
        Canvas(Modifier.fillMaxSize().alpha(fadeOut)) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h * 0.40f
            val starA = seg(t, 0f, 0.8f)
            for ((sx, sy, sz) in stars) {
                val tw = 0.5f + 0.5f * sin(t * (1.5f + sz) + sx * 20f)
                drawCircle(Color.White.copy(alpha = starA * (0.25f + 0.55f * tw)), sz * 1.6f, Offset(sx * w, sy * h))
            }
            // dois discos rápidos cruzando
            val u = min(w, h) * 0.04f
            listOf(Triple(0.2f, 1.6f, 0.22f), Triple(0.6f, 2.1f, 0.74f)).forEachIndexed { i, (a0, a1, yy) ->
                val p = (t - a0) / (a1 - a0)
                if (p in 0f..1f) {
                    val left = i == 0
                    val x = if (left) -u * 3 + p * (w + u * 6) else w + u * 3 - p * (w + u * 6)
                    translate(x, h * yy + sin(p * 8f) * h * 0.02f) { rotate(if (left) 8f else -8f) { drawUfo(u, 0.95f, t) } }
                }
            }
            // feixe + cabeça
            val beamA = seg(t, 0.8f, 1.4f) * (1f - seg(t, 4.8f, 5.6f))
            if (beamA > 0f) {
                val top = Offset(cx, 0f); val bw = w * 0.16f
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(top.x - u * 0.6f, top.y); lineTo(top.x + u * 0.6f, top.y)
                        lineTo(cx + bw, cy + h * 0.42f); lineTo(cx - bw, cy + h * 0.42f); close()
                    },
                    Brush.verticalGradient(listOf(SxGreen.copy(alpha = 0.5f * beamA), SxGreen.copy(alpha = 0.04f * beamA)), 0f, cy + h * 0.42f),
                )
            }
            val headA = seg(t, 1.3f, 2.4f)
            if (headA > 0f) {
                val hh = h * 0.42f; val hw = hh * head.width / head.height
                val sc = 0.88f + 0.12f * headA
                val glow = 0.25f + 0.25f * sin(t * 2.6f)
                drawOval(
                    Brush.radialGradient(listOf(SxGreen.copy(alpha = headA * glow), Color.Transparent), Offset(cx, cy), hh * 0.9f),
                    topLeft = Offset(cx - hh, cy - hh), size = Size(hh * 2, hh * 2),
                )
                val dw = (hw * sc).toInt(); val dh = (hh * sc).toInt()
                val dst = IntOffset((cx - dw / 2f).toInt(), (cy - dh / 2f).toInt())
                drawImage(head, dstOffset = dst, dstSize = IntSize(dw, dh), alpha = headA)
                if (headA < 1f) {
                    val ly = dst.y + dh * (1f - headA)
                    drawBeam(Offset(dst.x.toFloat(), ly), Offset((dst.x + dw).toFloat(), ly), 1f - headA, 3f)
                }
            }
        }
        val textA = seg(t, SHORT_VOICE_AT, SHORT_VOICE_AT + 0.7f) * fadeOut
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).alpha(textA),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, color = SxText, fontSize = 72.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp, lineHeight = 76.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("SX CAR", color = SxGreen, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp)
        }
    }
}
