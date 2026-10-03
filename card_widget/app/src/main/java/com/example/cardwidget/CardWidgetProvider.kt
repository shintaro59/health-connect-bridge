package com.example.cardwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.Toast

class CardWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ITEM_CLICK = "com.example.cardwidget.ACTION_ITEM_CLICK"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ITEM_CLICK) {
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

/** 設定完了後や更新時に、ウィジェット1個分のRemoteViewsを組み立てて反映する */
fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.card_widget_stack)

    // StackViewに表示するデータを提供するRemoteViewsServiceを指す。
    // data Uriをウィジェットごとに変えないと、複数ウィジェットでアダプタが共有されてしまう。
    val serviceIntent = Intent(context, CardWidgetRemoteViewsService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse("cardwidget://widget/$appWidgetId")
    }
    views.setRemoteAdapter(R.id.stack_view, serviceIntent)
    views.setEmptyView(R.id.stack_view, R.id.empty_view)

    // QUOカードPay/PayPayのページをタップした時に、どのアプリを開くかをitem側のfillInIntentで渡す。
    // テンプレート自体はCardWidgetProvider宛のブロードキャストにしておき、受け取った側で振り分ける。
    val clickIntentTemplate = Intent(context, CardWidgetProvider::class.java).apply {
        action = CardWidgetProvider.ACTION_ITEM_CLICK
    }
    val clickPendingIntentTemplate = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        clickIntentTemplate,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    views.setPendingIntentTemplate(R.id.stack_view, clickPendingIntentTemplate)

    appWidgetManager.updateAppWidget(appWidgetId, views)
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.stack_view)
}
