package app.folio.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.folio.core.model.InkPacking
import app.folio.core.model.InkStroke
import app.folio.core.model.InkTool
import kotlinx.serialization.Serializable

/** A drawing card pinned to a position in an EPUB or text book. */
@Entity(
    tableName = "drawn_notes",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("updatedAt")],
)
@Serializable
data class DrawnNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val location: String,
    val positionLabel: String,
    val progress: Float,
    /** Card height divided by width. */
    val aspect: Float,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One pen or highlighter stroke. Page ink has [page] set; drawn-note ink has [drawnNoteId] set.
 * [points] are packed whole-page (or whole-card) normalized coordinates, see `InkPacking`.
 */
@Entity(
    tableName = "ink_strokes",
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DrawnNoteEntity::class, ["id"], ["drawnNoteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["bookId", "page"]), Index("drawnNoteId")],
)
@Serializable
class InkStrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val page: Int? = null,
    val drawnNoteId: Long? = null,
    val tool: InkTool,
    val argb: Int,
    val widthNorm: Float,
    val points: ByteArray,
    val createdAt: Long,
) {
    fun withId(newId: Long) = InkStrokeEntity(newId, bookId, page, drawnNoteId, tool, argb, widthNorm, points, createdAt)
}

data class DrawnNoteWithBook(@Embedded val note: DrawnNoteEntity, val bookTitle: String, val bookFormat: String)

fun InkStrokeEntity.toInkStroke(): InkStroke =
    InkStroke(id = id, tool = tool, argb = argb, widthNorm = widthNorm, points = InkPacking.unpack(points))
