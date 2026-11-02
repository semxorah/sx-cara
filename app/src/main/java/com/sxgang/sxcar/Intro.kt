package com.sxgang.sxcar

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

/** Controla se a intro já foi mostrada nesta ligada (processo). */
object IntroState { var shown = false }

private fun period(): Int {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> 0; in 12..17 -> 1; else -> 2 }
}

fun greeting(): String = when (period()) {
    0 -> "Bom dia, mestre"
    1 -> "Boa tarde, mestre"
    else -> "Boa noite, mestre"
}

private fun greetingAudio(): Int = when (period()) {
    0 -> R.raw.bom_dia
    1 -> R.raw.boa_tarde
    else -> R.raw.boa_noite
}

private fun playGreeting(ctx: Context): MediaPlayer? = runCatching {
    MediaPlayer.create(ctx, greetingAudio())?.apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        setOnCompletionListener { it.release() }
        start()
    }
}.getOrNull()

private const val INTRO_SEC = 15f
private const val VOICE_AT = 10.6f

// utilitários de animação por tempo
private fun ease(x: Float) = (x.coerceIn(0f, 1f)).let { it * it * (3 - 2 * it) }
private fun seg(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))
private fun pulse01(t: Float, a: Float, b: Float): Float { // sobe e desce entre a e b
    val p = ((t - a) / (b - a)).coerceIn(0f, 1f); return sin(p * PI.toFloat())
}

/**
 * Intro cinematográfica (15 s):
 *  0–1 s   estrelas acendem
 *  1–7 s   discos voadores cruzam a tela, raios varrem
 *  7–9.5 s discos convergem e um feixe desce do topo
 *  9.5 s   a cabeça do alien materializa no feixe
 *  10.6 s  saudação (texto + voz)
 *  14.2 s  fade out
 */
@Composable
fun Intro(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember { greeting() }
    val head: ImageBitmap = ImageBitmap.imageResource(R.drawable.alien_head)

    var frame by remember { mutableLongStateOf(0L) }
    var start by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var spoke by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                frame = now
            }
            val t = (frame - start) / 1e9f
            if (!spoke && t >= VOICE_AT) { spoke = true; player = playGreeting(ctx) }
            if (t >= INTRO_SEC) { onDone(); break }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }

    val stars = remember { val r = Random(42); List(90) { Triple(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat()) } }
    val t = (frame - start) / 1e9f
    val fadeOut = 1f - seg(t, 14.2f, 15f)

    Box(Modifier.fillMaxSize().background(SxDark)) {
        Canvas(Modifier.fillMaxSize().alpha(fadeOut)) {
            val w = size.width; val h = size.height
            val starA = seg(t, 0f, 1.2f)

            // --- estrelas
            for ((sx, sy, sz) in stars) {
                val tw = 0.5f + 0.5f * sin(t * (1.5f + sz) + sx * 20f)
                drawCircle(Color.White.copy(alpha = starA * (0.25f + 0.55f * tw)), sz * 1.6f, Offset(sx * w, sy * h))
            }

            // --- raios varrendo (3 passagens)
            fun beamPass(a0: Float, a1: Float, fromLeft: Boolean, yA: Float, yB: Float) {
                val p = (t - a0) / (a1 - a0)
                if (p in 0f..1f) {
                    val x = if (fromLeft) -w * 0.2f + p * w * 1.4f else w * 1.2f - p * w * 1.4f
                    val al = pulse01(t, a0, a1)
                    drawBeam(Offset(x, h * yA), Offset(x + (if (fromLeft) w * 0.18f else -w * 0.18f), h * yB), al, 5f)
                }
            }
            beamPass(2.2f, 3.0f, true, 0.1f, 0.9f)
            beamPass(3.6f, 4.3f, false, 0.0f, 1.0f)
            beamPass(5.0f, 5.7f, true, 0.2f, 0.8f)
            beamPass(6.2f, 6.9f, false, 0.05f, 0.95f)

            // --- discos cruzando (1–7 s)
            fun fly(a0: Float, a1: Float, y0: Float, y1: Float, s: Float, fromLeft: Boolean) {
                val p = (t - a0) / (a1 - a0)
                if (p in 0f..1f) {
                    val e = p
                    val x = if (fromLeft) -s * 3 + e * (w + s * 6) else w + s * 3 - e * (w + s * 6)
                    val y = h * (y0 + (y1 - y0) * e) + sin(p * 9f) * h * 0.03f
                    translate(x, y) { rotate(if (fromLeft) 8f else -8f) { drawUfo(s, 0.95f, t) } }
                }
            }
            val u = min(w, h) * 0.045f
            fly(1.0f, 3.4f, 0.22f, 0.30f, u * 1.1f, true)
            fly(1.8f, 3.9f, 0.70f, 0.62f, u * 0.8f, false)
            fly(3.2f, 5.2f, 0.45f, 0.40f, u * 1.4f, true)
            fly(4.4f, 6.3f, 0.15f, 0.25f, u * 0.9f, false)
            fly(5.3f, 7.2f, 0.80f, 0.70f, u * 1.2f, true)

            // --- convergência (7–9.5 s): três discos param em formação sobre o centro
            val cx = w / 2f; val cy = h * 0.40f
            val conv = seg(t, 7.0f, 8.8f)
            val leave = seg(t, 13.2f, 14.6f)
            if (t >= 7.0f) {
                val slots = listOf(Offset(-w * 0.26f, -h * 0.30f), Offset(0f, -h * 0.40f), Offset(w * 0.26f, -h * 0.30f))
                val starts = listOf(Offset(-w * 0.7f, h * 0.3f), Offset(0f, h * 0.7f), Offset(w * 0.7f, h * 0.2f))
                slots.forEachIndexed { i, sl ->
                    val st = starts[i]
                    val px = st.x + (sl.x - st.x) * conv - leave * (sl.x.sign.takeIf { it != 0f } ?: 1f) * w * 0.9f
                    val py = st.y + (sl.y - st.y) * conv - leave * h * 0.6f + sin(t * 2f + i) * 6f
                    translate(cx + px, cy + py) { drawUfo(u * 1.1f, 0.95f, t + i, glow = 1f + conv) }
                }
            }

            // --- feixe de materialização (8.6 s em diante)
            val beamA = seg(t, 8.6f, 9.4f) * (1f - seg(t, 12.4f, 13.4f))
            if (beamA > 0f) {
                val top = Offset(cx, cy - h * 0.40f + u)
                val bw = w * 0.16f
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(top.x - u * 0.6f, top.y); lineTo(top.x + u * 0.6f, top.y)
                        lineTo(cx + bw, cy + h * 0.42f); lineTo(cx - bw, cy + h * 0.42f); close()
                    },
                    Brush.verticalGradient(listOf(SxGreen.copy(alpha = 0.55f * beamA), SxGreen.copy(alpha = 0.05f * beamA)), top.y, cy + h * 0.42f),
                )
                // partículas subindo no feixe
                val r = Random(3)
                repeat(26) { i ->
                    val fx = r.nextFloat(); val spd = 0.25f + r.nextFloat() * 0.5f
                    val py = cy + h * 0.42f - ((t * spd + i * 0.137f) % 1f) * h * 0.8f
                    val frac = (py - top.y) / (h * 0.82f)
                    val px = cx + (fx - 0.5f) * 2f * bw * frac
                    drawCircle(SxGreen.copy(alpha = beamA * 0.7f), 2.2f, Offset(px, py))
                }
            }

            // --- cabeça do alien materializando (9.5 s)
            val headA = seg(t, 9.5f, 11.0f)
            if (headA > 0f) {
                val hh = h * 0.42f
                val hw = hh * head.width / head.height
                val sc = 0.86f + 0.14f * headA
                // halo respirando
                val glow = 0.25f + 0.25f * sin(t * 2.6f)
                drawOval(
                    Brush.radialGradient(listOf(SxGreen.copy(alpha = headA * glow), Color.Transparent), Offset(cx, cy), hh * 0.9f),
                    topLeft = Offset(cx - hh, cy - hh), size = Size(hh * 2, hh * 2),
                )
                // "scanline" de materialização: desenha a cabeça por fatias enquanto headA < 1
                val dw = (hw * sc).toInt(); val dh = (hh * sc).toInt()
                val dst = IntOffset((cx - dw / 2f).toInt(), (cy - dh / 2f).toInt())
                drawImage(head, dstOffset = dst, dstSize = IntSize(dw, dh), alpha = headA)
                if (headA < 1f) {
                    val ly = dst.y + dh * (1f - headA)
                    drawBeam(Offset(dst.x.toFloat(), ly), Offset((dst.x + dw).toFloat(), ly), 1f - headA, 3f)
                }
            }
        }

        // --- saudação
        val textA = seg(t, VOICE_AT, VOICE_AT + 0.8f) * fadeOut
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
