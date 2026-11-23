package app.folio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import app.folio.FolioApp
import app.folio.MainActivity
import app.folio.R
import app.folio.core.model.ComfortPicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Continue reading" on the home screen: the book being read, with its cover and progress, one
 * tap from the page it was left on. Refreshed whenever Folio goes to the background, so it is
 * never more than one reading session out of date.
 */
class ContinueReadingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as FolioApp).container.appScope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_BOOK = "app.folio.action.OPEN_BOOK"
        const val EXTRA_BOOK_ID = "app.folio.extra.BOOK_ID"

        /** Redraws every placed widget with the book to resume. Cheap when none is placed. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
            if (ids.isEmpty()) return

            val container = (context.applicationContext as FolioApp).container
            val book = ComfortPicks.resume(container.library.libraryBooks.first())
            val cover = withContext(Dispatchers.IO) { book?.coverPath?.let(::loadCover) }

            val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
            if (book != null) {
                views.setTextViewText(R.id.widget_title, book.title)
                views.setProgressBar(R.id.widget_progress, 100, (book.progress * 100).toInt().coerceIn(0, 100), false)
                if (cover != null) views.setImageViewBitmap(R.id.widget_cover, cover)
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_nothing))
                views.setProgressBar(R.id.widget_progress, 100, 0, false)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (book != null) {
                    action = ACTION_OPEN_BOOK
                    putExtra(EXTRA_BOOK_ID, book.id)
                }
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(ids, views)
        }

        /** A small copy of the cover: widgets have a tight memory budget. */
        private fun loadCover(path: String): Bitmap? = runCatching {
            val file = File(path)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= 240) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
