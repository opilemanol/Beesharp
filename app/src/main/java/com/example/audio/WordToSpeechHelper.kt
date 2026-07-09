package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class WordToSpeechHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ngLocale = Locale("en", "NG")
            var result = tts?.setLanguage(ngLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("WordToSpeechHelper", "Nigerian English not supported, falling back to US English")
                result = tts?.setLanguage(Locale.US)
            }
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                Log.d("WordToSpeechHelper", "TTS initialized successfully with locale: ${tts?.language}")
            } else {
                Log.e("WordToSpeechHelper", "TTS language not supported or missing data")
            }
        } else {
            Log.e("WordToSpeechHelper", "TTS Initialization failed: status $status")
        }
    }

    fun speak(word: String) {
        if (isInitialized) {
            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "SpellingBeeTTS")
            Log.d("WordToSpeechHelper", "Speaking word: $word")
        } else {
            Log.w("WordToSpeechHelper", "TTS is not initialized yet. Skipping speech.")
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
