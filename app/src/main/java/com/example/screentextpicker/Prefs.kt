package com.example.screentextpicker

import android.content.Context

object Prefs {
    private const val NAME = "settings"
    private const val KEY_INCLUDE_OFFSCREEN = "include_offscreen"
    private const val KEY_SEPARATOR = "separator"

    fun includeOffscreen(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INCLUDE_OFFSCREEN, false)

    fun setIncludeOffscreen(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_INCLUDE_OFFSCREEN, value).apply()
    }

    /** 複数選択したときの区切り。Separator.ordinal を保存する。 */
    fun separator(context: Context): Separator {
        val i = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_SEPARATOR, 0)
        return Separator.entries.getOrElse(i) { Separator.NEWLINE }
    }

    fun setSeparator(context: Context, value: Separator) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SEPARATOR, value.ordinal).apply()
    }
}

enum class Separator(val label: String, val value: String) {
    NEWLINE("改行", "\n"),
    BLANK_LINE("空行", "\n\n"),
    SPACE("空白", " "),
    NONE("なし", ""),
}
