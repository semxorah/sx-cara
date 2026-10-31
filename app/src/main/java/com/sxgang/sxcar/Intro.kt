package com.sxgang.sxcar

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** Controla se a intro já foi mostrada nesta ligada (processo). */
object IntroState { var shown = false }

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Bom dia, mestre"
        in 12..17 -> "Boa tarde, mestre"
        else -> "Boa noite, mestre"
    }
}

/** Fala o texto em pt-BR se a multimídia tiver um motor de voz. */
class Voice(ctx: Context) {
    private var ready = false
    private var pending: String? = null
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts?.language = Locale("pt", "BR") }
                ready = true
                pending?.let { say(it) }
            }
        }
    }

    fun say(text: String) {
        if (!ready) { pending = text; return }
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sxcar") }
    }

    fun shutdown() = runCatching { tts?.shutdown() }
}

private const val INTRO_MS = 7000L

@Composable
fun Intro(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val voice = remember { Voice(ctx) }
    val text = remember { greeting() }

    // fases: 0 = escuro, 1 = alien aparece, 2 = texto, 3 = saída
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(300); phase = 1
        delay(1500); phase = 2
        voice.say(text)
        delay(INTRO_MS - 300 - 1500 - 700); phase = 3
        delay(700)
        onDone()
    }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    val alienScale by animateFloatAsState(if (phase >= 1) 1f else 0.6f, tween(1200, easing = FastOutSlowInEasing), label = "s")
    val alienAlpha by animateFloatAsState(if (phase in 1..2) 1f else 0f, tween(if (phase == 3) 600 else 1000), label = "a")
    val textAlpha by animateFloatAsState(if (phase == 2) 1f else 0f, tween(if (phase == 3) 500 else 900), label = "t")
    val glow = rememberInfiniteTransition(label = "g").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
    )
    val scan = rememberInfiniteTransition(label = "sc").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "scan",
    )

    Box(Modifier.fillMaxSize().background(SxDark), contentAlignment = Alignment.Center) {
        // grade / varredura de fundo
        Canvas(Modifier.fillMaxSize().alpha(alienAlpha * 0.5f)) {
            val step = 48f
            var x = 0f
            while (x < size.width) { drawLine(Color(0x1A5AF2A0), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0x1A5AF2A0), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val sy = size.height * scan.value
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x335AF2A0), Color.Transparent), sy - 60f, sy + 60f),
                topLeft = Offset(0f, sy - 60f), size = androidx.compose.ui.geometry.Size(size.width, 120f),
            )
        }
        // halo
        Box(
            Modifier.size(340.dp).alpha(alienAlpha * glow.value)
                .background(Brush.radialGradient(listOf(Color(0x665AF2A0), Color.Transparent)))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.alien), null,
                Modifier.size(210.dp).scale(alienScale).alpha(alienAlpha),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text, color = SxText, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp,
                modifier = Modifier.alpha(textAlpha),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "SX CAR", color = SxGreen, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                modifier = Modifier.alpha(textAlpha),
            )
        }
    }
}
