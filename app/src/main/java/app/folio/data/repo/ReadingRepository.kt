package app.folio.data.repo

import app.folio.core.model.SessionSample
import app.folio.data.db.FolioDatabase
import app.folio.data.db.GoalType
import app.folio.data.db.PomodoroPhase
import app.folio.data.db.PomodoroSessionEntity
import app.folio.data.db.ReadingGoalEntity
import app.folio.data.db.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reading sessions, goals and completed Pomodoro sessions: the raw data behind statistics. */
class ReadingRepository(
    db: FolioDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.reading()

    val sessions: Flow<List<ReadingSessionEntity>> = dao.observeSessions()
    val samples: Flow<List<SessionSample>> = sessions.map { list ->
        list.map { SessionSample(it.bookId, it.startedAt, it.durationMs, it.pagesRead) }
    }
    val pomodoros: Flow<List<PomodoroSessionEntity>> = dao.observePomodoros()
    val goals: Flow<List<ReadingGoalEntity>> = dao.observeGoals()

    fun sessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>> = dao.observeSessionsForBook(bookId)

    /** Reading sessions shorter than this are noise (opening a book to check something). */
    suspend fun recordSession(
        bookId: Long,
        startedAt: Long,
        endedAt: Long,
        pagesRead: Int,
        startProgress: Float,
        endProgress: Float,
        pomodoroSessionId: Long? = null,
    ): Long? {
        val duration = endedAt - startedAt
        if (duration < MIN_SESSION_MS) return null
        return dao.insertSession(
            ReadingSessionEntity(
                bookId = bookId,
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = duration,
                pagesRead = pagesRead.coerceAtLeast(0),
                startProgress = startProgress,
                endProgress = endProgress,
                pomodoroSessionId = pomodoroSessionId,
            ),
        )
    }

    suspend fun recordPomodoro(
        phase: PomodoroPhase,
        startedAt: Long,
        plannedMs: Long,
        actualMs: Long,
        completed: Boolean,
        bookId: Long?,
    ): Long = dao.insertPomodoro(
        PomodoroSessionEntity(
            phase = phase,
            startedAt = startedAt,
            endedAt = now(),
            plannedMs = plannedMs,
            actualMs = actualMs,
            completed = completed,
            bookId = bookId,
        ),
    )

    suspend fun setGoal(type: GoalType, target: Int, enabled: Boolean) =
        dao.upsertGoal(ReadingGoalEntity(type, target, enabled))

    suspend fun goalsOnce(): List<ReadingGoalEntity> = dao.goals()

    suspend fun sessionsOnce(): List<ReadingSessionEntity> = dao.allSessions()

    suspend fun pomodorosOnce(): List<PomodoroSessionEntity> = dao.allPomodoros()

    suspend fun readingMsSince(from: Long): Long = dao.readingMsSince(from)

    suspend fun pagesSince(from: Long): Int = dao.pagesSince(from)

    companion object {
        const val MIN_SESSION_MS = 10_000L
    }
}
