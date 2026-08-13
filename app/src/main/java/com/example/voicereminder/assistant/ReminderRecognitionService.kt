package com.example.voicereminder.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.example.voicereminder.voice.SpeechRecognizerFactory

/**
 * Android expects a RecognitionService to be paired with a VoiceInteractionService.
 * This implementation forwards recognition requests to another speech-recognition
 * service installed on the phone, while avoiding a loop back into VoiceReminder.
 */
class ReminderRecognitionService : RecognitionService() {

    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        destroyDelegate()

        val recognizer = SpeechRecognizerFactory.createExternal(this)
        if (recognizer == null) {
            listener.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        delegate = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listener.readyForSpeech(params ?: Bundle.EMPTY)
            }

            override fun onBeginningOfSpeech() = listener.beginningOfSpeech()
            override fun onRmsChanged(rmsdB: Float) = listener.rmsChanged(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) {
                if (buffer != null) listener.bufferReceived(buffer)
            }

            override fun onEndOfSpeech() = listener.endOfSpeech()

            override fun onError(error: Int) {
                listener.error(error)
                destroyDelegate()
            }

            override fun onResults(results: Bundle?) {
                listener.results(results ?: Bundle.EMPTY)
                destroyDelegate()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                listener.partialResults(partialResults ?: Bundle.EMPTY)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                listener.event(eventType, params ?: Bundle.EMPTY)
            }
        })
        recognizer.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback) {
        delegate?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        delegate?.cancel()
        destroyDelegate()
    }

    override fun onDestroy() {
        destroyDelegate()
        super.onDestroy()
    }

    private fun destroyDelegate() {
        delegate?.destroy()
        delegate = null
    }
}
