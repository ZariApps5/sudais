package com.zariapps.quran.sudais.config

object ReciterConfig {
    const val RECITER_NAME = "Abdul Rahman Al-Sudais"
    const val RECITER_NAME_ARABIC = "عبدالرحمن السديس"
    const val APP_NAME = "Quran - Al-Sudais"

    // Play Store URL — update once the app is published
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.zariapps.quran.sudais"

    const val BIOGRAPHY_ENGLISH = """Sheikh Abdul Rahman ibn Abdul Aziz Al-Sudais was born on February 10, 1960, in Riyadh, Saudi Arabia. He memorized the entire Quran at the age of 12 and went on to earn a Bachelor's degree in Islamic Sharia from King Saud University, a Master's degree from Imam Muhammad ibn Saud Islamic University, and a Doctorate in Islamic Sharia from Umm Al-Qura University in Makkah.

In 1984, at the age of 24, he was appointed as Imam and Khateeb of Masjid Al-Haram (the Grand Mosque) in Makkah — the holiest mosque in Islam — a position he continues to hold. He is widely regarded as one of the most beautiful and moving Quran reciters in the world, and his voice has touched the hearts of hundreds of millions of Muslims globally.

Sheikh Al-Sudais also serves as the President of the General Presidency for the Affairs of the Two Holy Mosques, overseeing Masjid Al-Haram in Makkah and Masjid An-Nabawi in Madinah."""

    const val BIOGRAPHY_ARABIC = """الشيخ عبد الرحمن بن عبد العزيز السُّدَيس، وُلد في العاشر من فبراير عام 1960م بمدينة الرياض في المملكة العربية السعودية. حفظ القرآن الكريم كاملاً في سن الثانية عشرة، وحصل على درجة البكالوريوس في الشريعة الإسلامية من جامعة الملك سعود، ثم الماجستير من جامعة الإمام محمد بن سعود الإسلامية، والدكتوراه في الشريعة الإسلامية من جامعة أم القرى بمكة المكرمة.

في عام 1984م، وهو في الرابعة والعشرين من عمره، عُيِّن إماماً وخطيباً للمسجد الحرام بمكة المكرمة — أقدس مساجد الإسلام — ولا يزال يشغل هذا المنصب حتى اليوم. يُعدّ من أعذب أصوات القرآن الكريم وأكثرها تأثيراً في القلوب، وقد أحيت تلاواته الإيمانَ في نفوس مئات الملايين من المسلمين حول العالم.

كما يتولى الشيخ السديس رئاسة الرئاسة العامة لشؤون المسجد الحرام والمسجد النبوي، مشرفاً على خدمة قاصدَي أشرف البقاع."""

    // Exact filenames for each surah as bundled in assets/audio/
    private val audioFiles = mapOf(
        1 to "001-al-fatihah.lite.mp3",
        2 to "002-1.mp3",
        3 to "003-al-imran.mp3",
        4 to "004-an-nisa.mp3",
        5 to "005-al-maidah.mp3",
        6 to "006-al-anam.mp3",
        7 to "007-al-araf.mp3",
        8 to "008-al-anfal.mp3",
        9 to "009-at-taubah.mp3",
        10 to "010-yunus.mp3",
        11 to "011-hud.mp3",
        12 to "012-yusuf.mp3",
        13 to "013-ar-rad.mp3",
        14 to "014-ibrahim.mp3",
        15 to "015-al-hijr.mp3",
        16 to "016-an-nahl.mp3",
        17 to "017-al-isra.mp3",
        18 to "018-al-kahf.lite.mp3",
        19 to "019-maryam.lite.mp3",
        20 to "020-ta-ha.lite.mp3",
        21 to "021-al-anbiya.lite.mp3",
        22 to "022-al-hajj.lite.mp3",
        23 to "023-al-muminun.lite.mp3",
        24 to "024-an-nur.lite.mp3",
        25 to "025-al-furqan.lite.mp3",
        26 to "026-ash-shuara.lite.mp3",
        27 to "027-an-naml.lite.mp3",
        28 to "028-al-qasas.lite.mp3",
        29 to "029-al-ankabut.lite.mp3",
        30 to "030-ar-rum.lite.mp3",
        31 to "031-luqman.lite.mp3",
        32 to "032-as-sajdah.lite.mp3",
        33 to "033-al-ahzab.lite.mp3",
        34 to "034-saba.lite.mp3",
        35 to "035-fatir.lite.mp3",
        36 to "036-ya-sin.lite.mp3",
        37 to "037-as-saffat.lite.mp3",
        38 to "038-sad.lite.mp3",
        39 to "039-az-zumar.lite.mp3",
        40 to "040-ghafir.lite.mp3",
        41 to "041-fussilat.lite.mp3",
        42 to "042-ash-shura.lite.mp3",
        43 to "043-az-zukhruf.lite.mp3",
        44 to "044-ad-dukhan.mp3",
        45 to "045-al-jathiyah.mp3",
        46 to "046-al-ahqaf.mp3",
        47 to "047-muhammad.mp3",
        48 to "048-al-fath.mp3",
        49 to "049-al-hujurat.mp3",
        50 to "050-qaf.mp3",
        51 to "051-adh-dhariyat.mp3",
        52 to "052-at-tur.mp3",
        53 to "053-an-najm.mp3",
        54 to "054-al-qamar.mp3",
        55 to "055-ar-rahman.mp3",
        56 to "056-al-waqiah.mp3",
        57 to "057-al-hadid.mp3",
        58 to "058-al-mujadilah.mp3",
        59 to "059-al-hashr.mp3",
        60 to "060-al-mumtahanah.mp3",
        61 to "061-as-saff.mp3",
        62 to "062-al-jumuah.mp3",
        63 to "063-al-munafiqun.mp3",
        64 to "064-at-taghabun.mp3",
        65 to "065-at-talaq.mp3",
        66 to "066-at-tahrim.mp3",
        67 to "067-al-mulk.mp3",
        68 to "068-al-qalam.mp3",
        69 to "069-al-haqqah.mp3",
        70 to "070-al-maarij.mp3",
        71 to "071-nuh.mp3",
        72 to "072-al-jinn.mp3",
        73 to "073-al-muzammil.mp3",
        74 to "074-al-muddaththir.mp3",
        75 to "075-al-qiyamah.mp3",
        76 to "076-al-insan.mp3",
        77 to "077-al-mursalat.mp3",
        78 to "078-an-naba.mp3",
        79 to "079-an-naziat.mp3",
        80 to "080-abasa.mp3",
        81 to "081-at-takwir.mp3",
        82 to "082-al-infitar.mp3",
        83 to "083-al-mutaffifin.mp3",
        84 to "084-al-inshiqaq.mp3",
        85 to "085-al-buruj.mp3",
        86 to "086-at-tariq.mp3",
        87 to "087-al-ala.mp3",
        88 to "088-al-ghashiyah.mp3",
        89 to "089-al-fajr.mp3",
        90 to "090-al-balad.mp3",
        91 to "091-ash-shams.mp3",
        92 to "092-al-lail.mp3",
        93 to "093-ad-duha.mp3",
        94 to "094-ash-sharh.mp3",
        95 to "095-at-tin.mp3",
        96 to "096-al-alaq.mp3",
        97 to "097-al-qadr.mp3",
        98 to "098-al-baiyyinah.mp3",
        99 to "099-az-zalzalah.mp3",
        100 to "100-al-adiyat.mp3",
        101 to "101-al-qariah.mp3",
        102 to "102-at-takathur.mp3",
        103 to "103-al-asr.mp3",
        104 to "104-al-humazah.mp3",
        105 to "105-al-fil.mp3",
        106 to "106-quraish.mp3",
        107 to "107-al-maun.mp3",
        108 to "108-al-kauthar.mp3",
        109 to "109-al-kafirun.mp3",
        110 to "110-an-nasr.mp3",
        111 to "111-al-masad.mp3",
        112 to "112-al-ikhlas.mp3",
        113 to "113-al-falaq.mp3",
        114 to "114-an-nas.mp3"
    )

    fun getAudioAssetUri(surahNumber: Int): String {
        val filename = audioFiles[surahNumber] ?: error("No audio file for surah $surahNumber")
        return "file:///android_asset/audio/$filename"
    }
}
