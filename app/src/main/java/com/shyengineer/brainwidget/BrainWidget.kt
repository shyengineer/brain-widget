package com.shyengineer.brainwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrainWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.shyengineer.brainwidget.REFRESH"

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BrainWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, build(ctx, id))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun build(ctx: Context, id: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget)

            val cache = Cache.of(ctx)
            val status = when {
                Sync.running -> "동기화 중…"
                cache.syncedAt == 0L -> "앱에서 설정 후 동기화"
                else -> SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(cache.syncedAt)) + " 동기화"
            }
            rv.setTextViewText(R.id.widget_status, status)

            // 목록 어댑터
            val svc = Intent(ctx, WidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.widget_list, svc)
            rv.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 제목·목록 누르면 앱 열기
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_title, open)
            rv.setPendingIntentTemplate(R.id.widget_list, open)

            // 새로고침 버튼
            val refresh = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, BrainWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_refresh, refresh)
            return rv
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Sync.schedulePeriodic(context)
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            Sync.scheduleNow(context)
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, BrainWidget::class.java))
            for (id in ids) {
                val rv = RemoteViews(context.packageName, R.layout.widget)
                rv.setTextViewText(R.id.widget_status, "동기화 중…")
                mgr.partiallyUpdateAppWidget(id, rv)
            }
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
        private var rows: List<Row> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}
        override fun onDataSetChanged() {
            rows = Rows.widgetRows(Cache.of(ctx))
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_header)
            val rv: RemoteViews
            if (r.kind == Row.Kind.HEADER) {
                rv = RemoteViews(ctx.packageName, R.layout.widget_header)
                rv.setTextViewText(R.id.row_title, r.title)
            } else {
                rv = RemoteViews(ctx.packageName, R.layout.widget_item)
                rv.setTextViewText(R.id.row_badge, r.badge)
                val title = SpannableString(r.title)
                if (r.tone == Row.Tone.NOW) title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)
                rv.setTextViewText(R.id.row_title, title)
                rv.setTextViewText(R.id.row_sub, r.sub)
                rv.setViewVisibility(R.id.row_sub, if (r.sub.isEmpty()) View.GONE else View.VISIBLE)
                val (titleColor, badgeColor) = when (r.tone) {
                    Row.Tone.DIM -> Color.parseColor("#9E9E9E") to Color.parseColor("#BDBDBD")
                    Row.Tone.NOW -> Color.parseColor("#1B1B1F") to Color.parseColor("#1565C0")
                    Row.Tone.WARN -> Color.parseColor("#C62828") to Color.parseColor("#C62828")
                    Row.Tone.NORMAL -> Color.parseColor("#1B1B1F") to Color.parseColor("#616161")
                }
                rv.setTextColor(R.id.row_title, titleColor)
                rv.setTextColor(R.id.row_badge, badgeColor)
            }
            rv.setOnClickFillInIntent(R.id.row_root, Intent())
            return rv
        }
    }
}
