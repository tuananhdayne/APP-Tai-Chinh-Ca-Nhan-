package com.example.apptaichinh.utils

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SoundUtils {
    fun playMicOpenSound() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                delay(200)
                toneGen.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
