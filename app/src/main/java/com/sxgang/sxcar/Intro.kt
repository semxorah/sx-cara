package com.sxgang.sxcar

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

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

/** Toca o áudio da saudação embutido no app (não depende de voz instalada na multimídia). */
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

private const val INTRO_MS = 7000L

@Composable
fun Intro(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember { greeting() }

    // fases: 0 = escuro, 1 = alien aparece, 2 = texto + voz, 3 = saída
    var phase by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    LaunchedEffect(Unit) {
        delay(300); phase = 1
        delay(1400); phase = 2
        player = playGreeting(ctx)
        delay(INTRO_MS - 300 - 1400 - 700); phase = 3
        delay(700)
        onDone()
    }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }

    val alienScale by animateFloatAsState(if (phase >= 1) 1f else 0.85f, tween(1400, easing = FastOutSlowInEasing), label = "s")
    val alienAlpha by animateFloatAsState(if (phase in 1..2) 1f else 0f, tween(if (phase == 3) 600 else 1100), label = "a")
    val textAlpha by animateFloatAsState(if (phase == 2) 1f else 0f, tween(if (phase == 3) 500 else 900), label = "t")
    // pulso suave do brilho (2,4 s por ciclo)
    val pulse = rememberInfiniteTransition(label = "g").animateFloat(
        0.22f, 0.5f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(SxDark), contentAlignment = Alignment.Center) {
        val headH = maxHeight * 0.62f
        val headW = headH * 0.757f
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                // halo pulsando atrás da cabeça
                Box(
                    Modifier.size(headW * 1.9f, headH * 1.35f).alpha(alienAlpha * pulse.value)
                        .background(Brush.radialGradient(listOf(Color(0xB35AF2A0), Color(0x335AF2A0), Color.Transparent)))
                )
                Image(
                    painterResource(R.drawable.alien_head), null,
                    Modifier.size(headW, headH).scale(alienScale).alpha(alienAlpha),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text, color = SxText, fontSize = 38.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp,
                textAlign = TextAlign.Center, modifier = Modifier.alpha(textAlpha),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "SX CAR", color = SxGreen, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp,
                modifier = Modifier.alpha(textAlpha),
            )
        }
    }
}
