package com.zariapps.quran.sudais.config

object ReciterConfig {
    const val RECITER_NAME = "Abdul Rahman Al-Sudais"
    const val RECITER_NAME_ARABIC = "عبدالرحمن السديس"
    const val AUDIO_BASE_URL = "https://server11.mp3quran.net/sds/"
    const val APP_NAME = "Quran - Al-Sudais"

    // Play Store URL — update once the app is published
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.zariapps.quran.sudais"

    const val BIOGRAPHY_ENGLISH = """Sheikh Abdul Rahman ibn Abdul Aziz Al-Sudais was born on February 10, 1960, in Riyadh, Saudi Arabia. He memorized the entire Quran at the age of 12 and went on to earn a Bachelor's degree in Islamic Sharia from King Saud University, a Master's degree from Imam Muhammad ibn Saud Islamic University, and a Doctorate in Islamic Sharia from Umm Al-Qura University in Makkah.

In 1984, at the age of 24, he was appointed as Imam and Khateeb of Masjid Al-Haram (the Grand Mosque) in Makkah — the holiest mosque in Islam — a position he continues to hold. He is widely regarded as one of the most beautiful and moving Quran reciters in the world, and his voice has touched the hearts of hundreds of millions of Muslims globally.

Sheikh Al-Sudais also serves as the President of the General Presidency for the Affairs of the Two Holy Mosques, overseeing Masjid Al-Haram in Makkah and Masjid An-Nabawi in Madinah."""

    const val BIOGRAPHY_ARABIC = """الشيخ عبد الرحمن بن عبد العزيز السُّدَيس، وُلد في العاشر من فبراير عام 1960م بمدينة الرياض في المملكة العربية السعودية. حفظ القرآن الكريم كاملاً في سن الثانية عشرة، وحصل على درجة البكالوريوس في الشريعة الإسلامية من جامعة الملك سعود، ثم الماجستير من جامعة الإمام محمد بن سعود الإسلامية، والدكتوراه في الشريعة الإسلامية من جامعة أم القرى بمكة المكرمة.

في عام 1984م، وهو في الرابعة والعشرين من عمره، عُيِّن إماماً وخطيباً للمسجد الحرام بمكة المكرمة — أقدس مساجد الإسلام — ولا يزال يشغل هذا المنصب حتى اليوم. يُعدّ من أعذب أصوات القرآن الكريم وأكثرها تأثيراً في القلوب، وقد أحيت تلاواته الإيمانَ في نفوس مئات الملايين من المسلمين حول العالم.

كما يتولى الشيخ السديس رئاسة الرئاسة العامة لشؤون المسجد الحرام والمسجد النبوي، مشرفاً على خدمة قاصدَي أشرف البقاع."""

    fun getAudioUrl(surahNumber: Int): String {
        return "${AUDIO_BASE_URL}${surahNumber.toString().padStart(3, '0')}.mp3"
    }
}
