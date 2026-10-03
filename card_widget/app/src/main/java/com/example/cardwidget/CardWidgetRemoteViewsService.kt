package com.example.cardwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class CardWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return CardStackFactory(applicationContext, appWidgetId)
    }
}

/** StackView用の4ページ分(図書館カード・楽天カード・QUOカードPay・PayPay)を順番に提供する */
private class CardStackFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Page(
        val isShortcut: Boolean,
        val title: String,
        val value: String,
        val backgroundRes: Int,
        val format: com.google.zxing.BarcodeFormat = com.google.zxing.BarcodeFormat.CODE_128,
    )

    private var pages: List<Page> = emptyList()

    override fun onCreate() {
        reloadPages()
    }

    override fun onDataSetChanged() {
        reloadPages()
    }

    private fun reloadPages() {
        val library = WidgetPrefs.getLibrary(context, appWidgetId)
        val rakuten = WidgetPrefs.getRakuten(context, appWidgetId)
        pages = listOf(
            Page(isShortcut = false, title = context.getString(R.string.page_library), value = library.value, backgroundRes = R.drawable.card_bg_library, format = library.format),
            Page(isShortcut = false, title = context.getString(R.string.page_rakuten), value = rakuten.value, backgroundRes = R.drawable.card_bg_rakuten, format = rakuten.format),
            Page(isShortcut = true, title = context.getString(R.string.page_quocard), value = context.getString(R.string.package_quocard), backgroundRes = R.drawable.card_bg_quocard),
            Page(isShortcut = true, title = context.getString(R.string.page_paypay), value = context.getString(R.string.package_paypay), backgroundRes = R.drawable.card_bg_paypay),
        )
    }

    override fun onDestroy() {}

    override fun getCount(): Int = pages.size

    override fun getViewAt(position: Int): RemoteViews {
        val page = pages[position]
        return if (page.isShortcut) {
            buildShortcutView(page)
        } else {
            buildBarcodeView(page)
        }
    }

    private fun buildBarcodeView(page: Page): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.card_widget_item_barcode)
        views.setInt(R.id.item_root, "setBackgroundResource", page.backgroundRes)
        views.setTextViewText(R.id.item_title, page.title)
        val bitmap = BarcodeUtil.encode(page.value, format = page.format)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.item_barcode, bitmap)
            views.setViewVisibility(R.id.item_barcode, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.item_empty_hint, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.item_barcode, android.view.View.GONE)
            views.setViewVisibility(R.id.item_empty_hint, android.view.View.VISIBLE)
        }
        return views
    }

    private fun buildShortcutView(page: Page): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.card_widget_item_shortcut)
        views.setInt(R.id.item_root, "setBackgroundResource", page.backgroundRes)
        views.setTextViewText(R.id.item_title, page.title)
        val fillInIntent = Intent().apply {
            putExtra(CardWidgetProvider.EXTRA_TARGET_PACKAGE, page.value)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
