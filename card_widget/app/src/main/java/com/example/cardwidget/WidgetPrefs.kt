package com.example.cardwidget

import android.content.Context
import com.google.zxing.BarcodeFormat

data class CardData(val value: String, val format: BarcodeFormat)

/**
 * カード番号・バーコード形式はウィジェットIDごとにこの端末内のSharedPreferencesだけに保存する。
 * どこにも送信しない。
 */
object WidgetPrefs {
    private const val PREFS_NAME = "card_widget_prefs"
    private fun keyLibraryValue(appWidgetId: Int) = "library_value_$appWidgetId"
    private fun keyLibraryFormat(appWidgetId: Int) = "library_format_$appWidgetId"
    private fun keyRakutenValue(appWidgetId: Int) = "rakuten_value_$appWidgetId"
    private fun keyRakutenFormat(appWidgetId: Int) = "rakuten_format_$appWidgetId"

    fun saveLibrary(context: Context, appWidgetId: Int, value: String, format: BarcodeFormat) {
        prefs(context).edit()
            .putString(keyLibraryValue(appWidgetId), value)
            .putString(keyLibraryFormat(appWidgetId), format.name)
            .apply()
    }

    fun saveRakuten(context: Context, appWidgetId: Int, value: String, format: BarcodeFormat) {
        prefs(context).edit()
            .putString(keyRakutenValue(appWidgetId), value)
            .putString(keyRakutenFormat(appWidgetId), format.name)
            .apply()
    }

    fun getLibrary(context: Context, appWidgetId: Int): CardData = CardData(
        value = prefs(context).getString(keyLibraryValue(appWidgetId), "") ?: "",
        format = readFormat(context, keyLibraryFormat(appWidgetId)),
    )

    fun getRakuten(context: Context, appWidgetId: Int): CardData = CardData(
        value = prefs(context).getString(keyRakutenValue(appWidgetId), "") ?: "",
        format = readFormat(context, keyRakutenFormat(appWidgetId)),
    )

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(keyLibraryValue(appWidgetId))
            .remove(keyLibraryFormat(appWidgetId))
            .remove(keyRakutenValue(appWidgetId))
            .remove(keyRakutenFormat(appWidgetId))
            .apply()
    }

    private fun readFormat(context: Context, key: String): BarcodeFormat {
        val name = prefs(context).getString(key, null) ?: return BarcodeFormat.CODE_128
        return try {
            BarcodeFormat.valueOf(name)
        } catch (e: IllegalArgumentException) {
            BarcodeFormat.CODE_128
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
