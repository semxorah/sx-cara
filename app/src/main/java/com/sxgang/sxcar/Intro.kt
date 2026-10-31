package com.sxgang.sxcar

import android.content.Context
import android.speech.tts.TextToSpeech
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

    val alienScale by animateFloatAsState(if (phase >= 1) 1f else 0.82f, tween(1400, easing = FastOutSlowInEasing), label = "s")
    val alienAlpha by animateFloatAsState(if (phase in 1..2) 1f else 0f, tween(if (phase == 3) 600 else 1100), label = "a")
    val textAlpha by animateFloatAsState(if (phase == 2) 1f else 0f, tween(if (phase == 3) 500 else 900), label = "t")
    // pulso suave do brilho (2,4 s por ciclo)
    val pulse = rememberInfiniteTransition(label = "g").animateFloat(
        0.22f, 0.5f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(SxDark), contentAlignment = Alignment.Center) {
        val headH = maxHeight * 0.78f
        val headW = headH * 0.757f
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                // halo pulsando atrás da cabeça
                Box(
                    Modifier.size(headW * 1.8f, headH * 1.4f).alpha(alienAlpha * pulse.value)
                        .background(Brush.radialGradient(listOf(Color(0xB35AF2A0), Color(0x335AF2A0), Color.Transparent)))
                )
                Image(
                    painterResource(R.drawable.alien_head), null,
                    Modifier.size(headW, headH).scale(alienScale).alpha(alienAlpha),
                )
            }
            Column(Modifier.weight(1f).alpha(textAlpha)) {
                Text(text, color = SxText, fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp, lineHeight = 46.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp, 3.dp).background(SxGreen))
                    Spacer(Modifier.width(12.dp))
                    Text("SX CAR", color = SxGreen, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp)
                }
            }
        }
    }
}
