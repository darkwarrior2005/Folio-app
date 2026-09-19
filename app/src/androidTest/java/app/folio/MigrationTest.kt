package app.folio

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.folio.data.db.FolioDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FolioDatabase::class.java)

    @Test
    fun version1LibrarySurvivesMusicMigration() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO books (id, uri, fileName, fileSize, fileHash, mimeType, format, storage,
                   pageCount, coverHidden, displayTitle, sortTitle, status, statusManual, favorite, progress,
                   dateAdded, missing, passwordProtected, indexState, customOrder)
                   VALUES (1, 'content://x/book.pdf', 'book.pdf', 10, 'h', 'application/pdf', 'PDF', 'LINKED',
                   10, 0, 'Kept Book', 'kept book', 'READING', 0, 0, 0.5,
                   0, 0, 0, 'PENDING', 0)""",
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT displayTitle FROM books WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Kept Book", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM tracks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version2LibrarySurvivesInkMigration() {
        helper.createDatabase(DB_INK, 2).use { db ->
            db.execSQL(
                """INSERT INTO books (id, uri, fileName, fileSize, fileHash, mimeType, format, storage,
                   pageCount, coverHidden, displayTitle, sortTitle, status, statusManual, favorite, progress,
                   dateAdded, missing, passwordProtected, indexState, customOrder)
                   VALUES (1, 'content://x/book.pdf', 'book.pdf', 10, 'h', 'application/pdf', 'PDF', 'LINKED',
                   10, 0, 'Kept Book', 'kept book', 'READING', 0, 0, 0.5,
                   0, 0, 0, 'PENDING', 0)""",
            )
            db.execSQL(
                """INSERT INTO highlights (id, bookId, location, positionLabel, progress, text, color, createdAt, updatedAt)
                   VALUES (1, 1, '{}', '3', 0.3, 'kept passage', 'YELLOW', 0, 0)""",
            )
        }
        helper.runMigrationsAndValidate(DB_INK, 3, true).use { db ->
            db.query("SELECT displayTitle FROM books WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Kept Book", cursor.getString(0))
            }
            db.query("SELECT text FROM highlights WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("kept passage", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM ink_strokes").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM drawn_notes").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
        const val DB_INK = "migration-test-ink"
    }
}
