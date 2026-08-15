package com.example.voicereminder.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.voicereminder.alarm.AlarmScheduler
import com.example.voicereminder.data.ReminderStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class VoiceCaptureActivity : ComponentActivity(), RecognitionListener {

    companion object {
        const val EXTRA_FROM_ASSISTANT = "from_assistant"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var state by mutableStateOf("Нажмите и говорите")
    private var recognized by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var saved by mutableStateOf(false)
    private var pendingReminder: PendingReminder? = null

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else state = "Нужен доступ к микрофону"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (saved) "✓" else "🎙",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            state,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        if (recognized.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                recognized,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        if (listening) {
                            CircularProgressIndicator()
                        } else if (!saved) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { ensureMicAndListen() }
                            ) {
                                Text(if (pendingReminder == null) "Говорить" else "Сказать время")
                            }
                        }
                    }
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            ensureMicAndListen()
        }, 250)
    }

    private fun ensureMicAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening(preserveRecognized: Boolean = false) {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizerFactory.createExternal(this)
        if (speechRecognizer == null) {
            state = "На телефоне не найден сервис распознавания речи"
            listening = false
            return
        }

        speechRecognizer?.setRecognitionListener(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        if (!preserveRecognized) recognized = ""
        saved = false
        state = if (pendingReminder == null) "Говорите напоминание…" else "Во сколько?"
        listening = true
        speechRecognizer?.startListening(intent)
    }

    private data class ParsedCandidate(
        val parsed: ParsedReminder,
        val phrase: String,
        val score: Float
    )

    private data class PendingCandidate(
        val pending: PendingReminder,
        val phrase: String,
        val score: Float
    )

    private fun consumeCandidates(phrases: List<String>, confidences: FloatArray?) {
        if (phrases.isEmpty()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }

        val pending = pendingReminder
        if (pending != null) {
            val choices = phrases.mapIndexedNotNull { index, phrase ->
                RussianReminderParser.completeWithTime(pending, phrase)?.let { parsed ->
                    ParsedCandidate(parsed, phrase, candidateScore(index, confidences))
                }
            }
            val best = choices.maxByOrNull { it.score }
            if (best != null) {
                recognized = best.phrase
                saveParsed(best.parsed)
            } else {
                recognized = phrases.first()
                listening = false
                state = "Не понял время. Скажи, например: «в 14:30»"
            }
            return
        }

        val parsedChoices = phrases.mapIndexedNotNull { index, phrase ->
            RussianReminderParser.parse(phrase)?.let { parsed ->
                ParsedCandidate(parsed, phrase, candidateScore(index, confidences))
            }
        }
        val bestParsed = parsedChoices.maxByOrNull { it.score }
        if (bestParsed != null) {
            recognized = bestParsed.phrase
            saveParsed(bestParsed.parsed)
            return
        }

        val pendingChoices = phrases.mapIndexedNotNull { index, phrase ->
            RussianReminderParser.parseNeedsTime(phrase)?.let { parsed ->
                PendingCandidate(parsed, phrase, candidateScore(index, confidences))
            }
        }
        val bestPending = pendingChoices.maxByOrNull { it.score }
        if (bestPending != null) {
            pendingReminder = bestPending.pending
            listening = false
            state = "Во сколько?"
            recognized = "${bestPending.pending.title} • ${formatPendingDate(bestPending.pending.targetDate)}"
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) startListening(preserveRecognized = true)
            }, 550)
            return
        }

        recognized = phrases.first()
        listening = false
        state = "Не понял когда напомнить. Попробуй: «через 5 минут», «сегодня в 17:30» или «во вторник в 12»"
    }

    private fun candidateScore(index: Int, confidences: FloatArray?): Float {
        val confidence = confidences?.getOrNull(index) ?: -1f
        return if (confidence >= 0f) {
            confidence * 1000f - index
        } else {
            1000f - index * 10f
        }
    }

    private fun saveParsed(parsed: ParsedReminder) {
        val store = ReminderStore(this)
        val reminder = store.insert(parsed.title, parsed.scheduledAt, parsed.repeatRule)
        AlarmScheduler(this).schedule(reminder)

        val formatter = DateTimeFormatter.ofPattern("EEE, d MMMM • HH:mm", Locale("ru"))
        val whenText = Instant.ofEpochMilli(parsed.scheduledAt)
            .atZone(ZoneId.systemDefault())
            .format(formatter)

        pendingReminder = null
        listening = false
        saved = true
        state = "Сохранено: $whenText"
        recognized = parsed.title

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1400)
    }

    private fun formatPendingDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("EEE, d MMMM", Locale("ru"))
        return date.format(formatter)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        state = if (pendingReminder == null) "Слушаю…" else "Слушаю время…"
    }

    override fun onBeginningOfSpeech() {
        state = if (pendingReminder == null) "Слушаю…" else "Слушаю время…"
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        state = "Распознаю…"
    }

    override fun onError(error: Int) {
        listening = false
        state = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> if (pendingReminder == null) {
                "Не расслышал. Попробуй ещё раз."
            } else {
                "Не расслышал время. Нажми «Сказать время» и повтори."
            }
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (pendingReminder == null) {
                "Не услышал речь. Попробуй ещё раз."
            } else {
                "Не услышал время. Нажми «Сказать время»."
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Распознавание речи сейчас недоступно"
            else -> "Ошибка распознавания ($error). Попробуй ещё раз."
        }
    }

    override fun onResults(results: Bundle?) {
        val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        consumeCandidates(phrases, confidences)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val phrases = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        phrases.firstOrNull()?.let { phrase ->
            recognized = if (pendingReminder == null) phrase else "Время: $phrase"
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
