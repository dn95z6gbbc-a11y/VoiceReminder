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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }

        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            PackageManager.MATCH_ALL
        )

        val service = services
            .asSequence()
            .mapNotNull { it.serviceInfo }
            .firstOrNull { it.packageName != context.packageName }
            ?: return null

        val component = ComponentName(service.packageName, service.name)
        return SpeechRecognizer.createSpeechRecognizer(context, component)
    }
}
