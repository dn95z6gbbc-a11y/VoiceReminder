package com.example.voicereminder.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

object SpeechRecognizerFactory {
    fun createExternal(context: Context): SpeechRecognizer? {
        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            PackageManager.MATCH_ALL
        )

        val external = services
            .asSequence()
            .filter { it.serviceInfo?.packageName != context.packageName }
            .sortedByDescending { it.priority }
            .mapNotNull { it.serviceInfo }
            .firstOrNull()

        if (external != null) {
            val component = ComponentName(external.packageName, external.name)
            return SpeechRecognizer.createSpeechRecognizer(context, component)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }

        return try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            null
        }
    }
}
