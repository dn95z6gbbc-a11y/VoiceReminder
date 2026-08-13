package com.example.voicereminder.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class VoiceCaptureActivity : ComponentActivity(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var state by mutableStateOf("Нажмите и говорите")
    private var recognized by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var saved by mutableStateOf(false)

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
                                Text("Говорить")
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

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            state = "На телефоне не найден сервис распознавания речи"
            listening = false
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }

        speechRecognizer?.setRecognitionListener(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        recognized = ""
        saved = false
        state = "Говорите напоминание…"
        listening = true
        speechRecognizer?.startListening(intent)
    }

    private fun consume(text: String) {
        recognized = text
        val parsed = RussianReminderParser.parse(text)
        if (parsed == null) {
            listening = false
            state = "Не понял дату или время. Например: «завтра в 12 позвонить врачу»"
            return
        }

        val store = ReminderStore(this)
        val reminder = store.insert(parsed.title, parsed.scheduledAt)
        AlarmScheduler(this).schedule(reminder)

        val formatter = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
        val whenText = Instant.ofEpochMilli(parsed.scheduledAt)
            .atZone(ZoneId.systemDefault())
            .format(formatter)

        listening = false
        saved = true
        state = "Сохранено: $whenText"
        recognized = parsed.title

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1200)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        state = "Слушаю…"
    }

    override fun onBeginningOfSpeech() {
        state = "Слушаю…"
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        state = "Распознаю…"
    }

    override fun onError(error: Int) {
        listening = false
        state = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Не расслышал. Попробуйте ещё раз."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не услышал речь. Попробуйте ещё раз."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Распознавание речи сейчас недоступно"
            else -> "Ошибка распознавания ($error). Попробуйте ещё раз."
        }
    }

    override fun onResults(results: Bundle?) {
        val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val best = phrases.firstOrNull()
        if (best == null) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
        } else {
            consume(best)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val phrases = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        phrases.firstOrNull()?.let { recognized = it }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
