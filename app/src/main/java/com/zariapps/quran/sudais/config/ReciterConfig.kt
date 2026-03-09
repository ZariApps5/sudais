package com.zariapps.quran.sudais.config

object ReciterConfig {
    const val RECITER_NAME = "Abdul Rahman Al-Sudais"
    const val RECITER_NAME_ARABIC = "عبدالرحمن السديس"
    const val AUDIO_BASE_URL = "https://server11.mp3quran.net/sds/"
    const val APP_NAME = "Quran - Al-Sudais"

    fun getAudioUrl(surahNumber: Int): String {
        return "${AUDIO_BASE_URL}${surahNumber.toString().padStart(3, '0')}.mp3"
    }
}
