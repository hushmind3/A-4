package com.pickle.client.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.pickle.client.data.model.Lang

val LocalLang = staticCompositionLocalOf { Lang.KO }

fun tr(lang: Lang, ko: String, en: String): String = if (lang == Lang.KO) ko else en
