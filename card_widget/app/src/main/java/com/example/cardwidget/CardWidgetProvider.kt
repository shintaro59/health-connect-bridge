package com.example.cardwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.google.zxing.BarcodeFormat

private data class Page(
    val isShortcut: Boolean,
    val title: String,
    val value: String,
    val backgroundRes: Int,
    val format: BarcodeFormat = BarcodeFormat.CODE_128,
)

class CardWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_CARD = "com.example.cardwidget.ACTION_NEXT_CARD"
        const val ACTION_OPEN_APP = "com.example.cardwidget.ACTION_OPEN_APP"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        when (intent.action) {
            ACTION_NEXT_CARD -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val pages = buildPages(context, appWidgetId)
                val nextIndex = (WidgetPrefs.getPageIndex(context, appWidgetId) + 1) % pages.size
                WidgetPrefs.setPageIndex(context, appWidgetId, nextIndex)
                updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            }
            ACTION_OPEN_APP -> {
                val pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
                val launchIntent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    Toast.makeText(context, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetPrefs.clear(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }
}

private fun buildPages(context: Context, appWidgetId: Int): List<Page> {
    val library = WidgetPrefs.getLibrary(context, appWidgetId)
    val rakuten = WidgetPrefs.getRakuten(context, appWidgetId)
    return listOf(
        Page(isShortcut = false, title = context.getString(R.string.page_library), value = library.value, backgroundRes = R.drawable.card_bg_library, format = library.format),
        Page(isShortcut = false, title = context.getString(R.string.page_rakuten), value = rakuten.value, backgroundRes = R.drawable.card_bg_rakuten, format = rakuten.format),
        Page(isShortcut = true, title = context.getString(R.string.page_quocard), value = context.getString(R.string.package_quocard), backgroundRes = R.drawable.card_bg_quocard),
        Page(isShortcut = true, title = context.getString(R.string.page_paypay), value = context.getString(R.string.package_paypay), backgroundRes = R.drawable.card_bg_paypay),
    )
}

/** 設定完了後や更新時に、ウィジェット1個分のRemoteViewsを組み立てて反映する。常に1枚のカードだけを全面表示する。 */
fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.card_widget_stack)

    val pages = buildPages(context, appWidgetId)
    val index = WidgetPrefs.getPageIndex(context, appWidgetId).coerceIn(0, pages.size - 1)
    val page = pages[index]

    views.setInt(R.id.card_root, "setBackgroundResource", page.backgroundRes)
    views.setTextViewText(R.id.item_title, page.title)

    if (page.isShortcut) {
        views.setViewVisibility(R.id.barcode_patch, android.view.View.GONE)
        views.setViewVisibility(R.id.item_shortcut_hint, android.view.View.VISIBLE)

        val openAppIntent = Intent(context, CardWidgetProvider::class.java).apply {
            action = CardWidgetProvider.ACTION_OPEN_APP
            putExtra(CardWidgetProvider.EXTRA_TARGET_PACKAGE, page.value)
        }
        val openAppPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId * 10 + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        views.setOnClickPendingIntent(R.id.card_root, openAppPendingIntent)
    } else {
        views.setViewVisibility(R.id.barcode_patch, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.item_shortcut_hint, android.view.View.GONE)

        val bitmap = BarcodeUtil.encode(page.value, format = page.format)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.item_barcode, bitmap)
            views.setViewVisibility(R.id.item_barcode, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.item_empty_hint, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.item_barcode, android.view.View.GONE)
            views.setViewVisibility(R.id.item_empty_hint, android.view.View.VISIBLE)
        }
    }

    val nextIntent = Intent(context, CardWidgetProvider::class.java).apply {
        action = CardWidgetProvider.ACTION_NEXT_CARD
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val nextPendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId * 10 + 2,
        nextIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    views.setOnClickPendingIntent(R.id.button_next, nextPendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
