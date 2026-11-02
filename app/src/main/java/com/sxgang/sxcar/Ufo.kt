package com.sxgang.sxcar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.*
import kotlin.random.Random

/** Desenha um disco voador centrado em (0,0). `s` = tamanho (largura ≈ 2.2*s). */
fun DrawScope.drawUfo(s: Float, alpha: Float, t: Float, glow: Float = 1f) {
    val body = Color(0xFF9AA3B2).copy(alpha = alpha)
    val dark = Color(0xFF3C4250).copy(alpha = alpha)
    val green = SxGreen.copy(alpha = alpha)
    // luz embaixo (halo)
    drawOval(
        Brush.radialGradient(listOf(green.copy(alpha = alpha * 0.45f * glow), Color.Transparent), center = Offset(0f, s * 0.35f), radius = s * 1.6f),
        topLeft = Offset(-s * 1.6f, -s * 0.5f), size = Size(s * 3.2f, s * 2.2f),
    )
    // corpo (disco)
    drawOval(dark, topLeft = Offset(-s * 1.1f, -s * 0.18f), size = Size(s * 2.2f, s * 0.7f))
    drawOval(body, topLeft = Offset(-s * 1.1f, -s * 0.28f), size = Size(s * 2.2f, s * 0.62f))
    // cúpula
    drawArc(
        Brush.verticalGradient(listOf(green.copy(alpha = alpha * 0.9f), green.copy(alpha = alpha * 0.25f)), -s * 0.8f, -s * 0.1f),
        180f, 180f, false, topLeft = Offset(-s * 0.5f, -s * 0.8f), size = Size(s, s * 1.1f),
    )
    // luzes girando na borda
    for (i in 0 until 6) {
        val a = t * 3f + i * (PI.toFloat() * 2f / 6f)
        val x = cos(a) * s * 0.95f
        val on = (sin(a + t * 2f) * 0.5f + 0.5f)
        drawCircle(green.copy(alpha = alpha * (0.3f + 0.7f * on)), s * 0.08f, Offset(x, s * 0.06f + sin(a) * s * 0.12f))
    }
}

/** Raio laser com brilho, de a até b. */
fun DrawScope.drawBeam(a: Offset, b: Offset, alpha: Float, width: Float = 6f) {
    val c = SxGreen
    drawLine(c.copy(alpha = alpha * 0.18f), a, b, width * 6f)
    drawLine(c.copy(alpha = alpha * 0.45f), a, b, width * 2.2f)
    drawLine(Color.White.copy(alpha = alpha * 0.9f), a, b, width * 0.6f)
}

class Ufo(var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val phase: Float)

/**
 * Discos voadores semitransparentes passeando pela tela. Não captura toques:
 * recebe a posição do último toque por `touch` e os discos fogem dele.
 */
@Composable
fun UfoLayer(touch: State<Pair<Offset, Long>?>, modifier: Modifier = Modifier) {
    val ufos = remember { mutableListOf<Ufo>() }
    var frame by remember { mutableLongStateOf(0L) }
    var lastT by remember { mutableLongStateOf(0L) }
    var lastTouchStamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (lastT == 0L) lastT = now
                val dt = ((now - lastT) / 1e9f).coerceIn(0f, 0.05f)
                lastT = now
                frame = now
                val tp = touch.value
                val reactTo = if (tp != null && tp.second != lastTouchStamp) { lastTouchStamp = tp.second; tp.first } else null
                for (u in ufos) {
                    if (reactTo != null) {
                        val dx = u.x - reactTo.x; val dy = u.y - reactTo.y
                        val d = max(40f, hypot(dx, dy))
                        if (d < 520f) { // só os que estão perto do toque
                            val k = 900f * (1f - d / 520f)
                            u.vx += dx / d * k; u.vy += dy / d * k
                        }
                    }
                    // atrito suave + deriva base
                    u.vx = u.vx * (1f - 1.2f * dt) + sin(now / 1e9f * 0.6f + u.phase) * 6f * dt
                    u.vy = u.vy * (1f - 1.2f * dt) + cos(now / 1e9f * 0.45f + u.phase) * 4f * dt
                    val base = 28f
                    val sp = hypot(u.vx, u.vy)
                    if (sp < base) { val a = atan2(u.vy, u.vx).let { if (sp < 1f) u.phase else it }; u.vx = cos(a) * base; u.vy = sin(a) * base }
                    u.x += u.vx * dt; u.y += u.vy * dt
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") frame // lê o estado pra redesenhar a cada frame
        if (ufos.isEmpty()) {
            val r = Random(7)
            repeat(3) { i ->
                ufos += Ufo(
                    x = r.nextFloat() * size.width, y = size.height * (0.15f + 0.6f * r.nextFloat()),
                    vx = (if (i % 2 == 0) 1f else -1f) * (25f + r.nextFloat() * 15f), vy = (r.nextFloat() - 0.5f) * 12f,
                    size = 16f + i * 5f, phase = r.nextFloat() * 6.28f,
                )
            }
        }
        val t = frame / 1e9f
        val w = size.width; val h = size.height
        for (u in ufos) {
            // dá a volta na tela
            val m = u.size * 3f
            if (u.x < -m) u.x = w + m; if (u.x > w + m) u.x = -m
            if (u.y < -m) u.y = h + m; if (u.y > h + m) u.y = -m
            val tilt = (u.vx / 60f).coerceIn(-12f, 12f)
            translate(u.x, u.y) { rotate(tilt, Offset.Zero) { drawUfo(u.size, 0.42f, t + u.phase) } }
        }
    }
}
