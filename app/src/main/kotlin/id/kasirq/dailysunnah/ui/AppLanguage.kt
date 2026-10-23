package id.kasirq.dailysunnah.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** True when the app's selected display language is English. */
val LocalAppEnglish = staticCompositionLocalOf { false }

fun String.localized(english: Boolean, englishText: String): String =
    if (english) englishText else this
