package app.folio.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/**
 * A highlight turned into a picture to share: the passage set in a warm serif on a cream card,
 * with the book's title underneath. Made on the phone, shared through the system share sheet.
 */
object QuoteCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 110f
    private val PAPER = 0xFFFBF4E8.toInt()
    private val INK = 0xFF3A2E27.toInt()
    private val SOFT = 0xFF9C8676.toInt()
    private val ACCENT = 0xFFD9B99B.toInt()

    fun render(quote: String, title: String, author: String?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)

        // A soft inner frame, like the edge of a page.
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = ACCENT
        }
        canvas.drawRoundRect(RectF(48f, 48f, WIDTH - 48f, HEIGHT - 48f), 36f, 36f, frame)

        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 220f
            color = ACCENT
        }
        canvas.drawText("“", MARGIN - 10f, 300f, mark)

        val text = quote.trim().replace(Regex("\\s+"), " ").let { if (it.length > 700) it.take(697).trimEnd() + "…" else it }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            color = INK
            textSize = when {
                text.length < 120 -> 64f
                text.length < 300 -> 52f
                else -> 42f
            }
        }
        val width = (WIDTH - MARGIN * 2).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, body, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.3f)
            .build()
        val top = ((HEIGHT - layout.height) / 2f).coerceAtLeast(320f)
        canvas.save()
        canvas.translate(MARGIN, top)
        layout.draw(canvas)
        canvas.restore()

        val credit = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            color = SOFT
            textSize = 38f
        }
        val line = listOfNotNull(title.takeIf { it.isNotBlank() }, author?.takeIf { it.isNotBlank() }).joinToString(" · ")
        val creditText = android.text.TextUtils.ellipsize("— $line", credit, width.toFloat(), android.text.TextUtils.TruncateAt.END)
        canvas.drawText(creditText, 0, creditText.length, MARGIN, HEIGHT - 140f, credit)
        return bitmap
    }

    /** Renders the card and opens the share sheet with it. */
    fun share(context: Context, quote: String, title: String, author: String?) {
        val bitmap = render(quote, title, author)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "quote.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
