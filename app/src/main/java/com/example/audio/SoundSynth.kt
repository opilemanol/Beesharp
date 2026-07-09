package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

object SoundSynth {
    suspend fun playSuccess() = withContext(Dispatchers.Default) {
        try {
            val sampleRate = 44100
            val durationSeconds = 0.5
            val numSamples = (sampleRate * durationSeconds).toInt()
            val sample = DoubleArray(numSamples)
            val buffer = ShortArray(numSamples)

            // Dynamic ascending chime notes: C5 (523.25 Hz), E5 (659.25 Hz), G5 (783.99 Hz), C6 (1046.50 Hz)
            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
            val noteDuration = numSamples / notes.size

            for (i in 0 until numSamples) {
                val noteIndex = i / noteDuration
                val freq = notes[noteIndex.coerceIn(0, notes.size - 1)]
                // Decay progress
                val noteProgress = (i % noteDuration).toDouble() / noteDuration
                val envelope = 1.0 - noteProgress
                sample[i] = sin(2 * Math.PI * i * freq / sampleRate) * envelope
                buffer[i] = (sample[i] * 32767).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            kotlinx.coroutines.delay(600)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun playFailure() = withContext(Dispatchers.Default) {
        try {
            val sampleRate = 44100
            val durationSeconds = 0.6
            val numSamples = (sampleRate * durationSeconds).toInt()
            val sample = DoubleArray(numSamples)
            val buffer = ShortArray(numSamples)

            // Descending sad sliding note
            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val currentFreq = 260.0 - progress * 130.0 // deslides down
                val envelope = 1.0 - progress
                sample[i] = sin(2 * Math.PI * i * currentFreq / sampleRate) * envelope
                buffer[i] = (sample[i] * 32767).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            kotlinx.coroutines.delay(700)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
