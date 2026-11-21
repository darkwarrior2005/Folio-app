package app.folio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import app.folio.FolioApp
import app.folio.MainActivity
import app.folio.R
import app.folio.core.model.ComfortPicks
import app.folio.core.model.LibraryBook
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

const val ACTION_OPEN_BOOK = "app.folio.widget.ACTION_OPEN_BOOK"
const val EXTRA_BOOK_ID = "app.folio.widget.EXTRA_BOOK_ID"

/** Shows the book being read; tapping it sends [ACTION_OPEN_BOOK] to [MainActivity]. */
class ContinueReadingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(context, manager, id) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, ContinueReadingWidget::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val container = (context.applicationContext as? FolioApp)?.container
            val resume = runBlocking {
                val books: List<LibraryBook> =
                    container?.library?.libraryBooks?.let { first(it) } ?: emptyList()
                ComfortPicks.resume(books)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
            if (resume == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_book))
                views.setViewVisibility(R.id.widget_progress, android.view.View.GONE)
            } else {
                views.setTextViewText(R.id.widget_title, resume.title)
                views.setViewVisibility(R.id.widget_progress, android.view.View.VISIBLE)
                views.setProgressBar(R.id.widget_progress, 100, (resume.progress * 100).toInt(), false)
                resume.coverPath?.let { path ->
                    BitmapFactory.decodeFile(path)?.let { views.setImageViewBitmap(R.id.widget_cover, it) }
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_BOOK
                    putExtra(EXTRA_BOOK_ID, resume.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    resume.id.toInt(),
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
