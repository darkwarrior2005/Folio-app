package app.folio.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

private const val WIDTH = 1080
private const val HEIGHT = 1350
private const val MAX_QUOTE_CHARS = 700

/** Renders a quote as a shareable image: serif text on a cream card, with the book title. */
object QuoteCard {

    fun render(quote: String, bookTitle: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F6EFE1"))

        val margin = 96
        val quoteText = quote.trim().let {
            if (it.length > MAX_QUOTE_CHARS) it.take(MAX_QUOTE_CHARS).trimEnd() + "…" else it
        }

        val quotePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#2B2520")
            textSize = 54f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val quoteLayout = StaticLayout.Builder.obtain(
            "“$quoteText”",
            0,
            "“$quoteText”".length,
            quotePaint,
            WIDTH - margin * 2,
        ).setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL).setLineSpacing(10f, 1.1f).build()

        val quoteTop = (HEIGHT - quoteLayout.height) / 2
        canvas.save()
        canvas.translate(margin.toFloat(), quoteTop.toFloat())
        quoteLayout.draw(canvas)
        canvas.restore()

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#6B6258")
            textSize = 32f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("— $bookTitle", WIDTH / 2f, HEIGHT - margin.toFloat(), titlePaint)

        return bitmap
    }

    fun share(context: Context, quote: String, bookTitle: String) {
        val bitmap = render(quote, bookTitle)
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, "quote.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
