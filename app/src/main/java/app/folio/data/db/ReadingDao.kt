package app.folio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    // Progress
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun progress(bookId: Long): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress")
    fun observeAllProgress(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    suspend fun allProgress(): List<ReadingProgressEntity>

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Upsert
    suspend fun upsertProgress(progress: List<ReadingProgressEntity>)

    @Query("DELETE FROM reading_progress WHERE bookId IN (:bookIds)")
    suspend fun deleteProgress(bookIds: List<Long>)

    // Sessions
    @Insert
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<ReadingSessionEntity>)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt")
    suspend fun allSessions(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeSessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions WHERE startedAt >= :from")
    suspend fun readingMsSince(from: Long): Long

    @Query("SELECT COALESCE(SUM(pagesRead), 0) FROM reading_sessions WHERE startedAt >= :from")
    suspend fun pagesSince(from: Long): Int

    // Pomodoro
    @Insert
    suspend fun insertPomodoro(session: PomodoroSessionEntity): Long

    @Insert
    suspend fun insertPomodoros(sessions: List<PomodoroSessionEntity>)

    @Query("SELECT * FROM pomodoro_sessions ORDER BY startedAt DESC")
    fun observePomodoros(): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions ORDER BY startedAt")
    suspend fun allPomodoros(): List<PomodoroSessionEntity>

    // Goals
    @Query("SELECT * FROM reading_goals")
    fun observeGoals(): Flow<List<ReadingGoalEntity>>

    @Query("SELECT * FROM reading_goals")
    suspend fun goals(): List<ReadingGoalEntity>

    @Upsert
    suspend fun upsertGoal(goal: ReadingGoalEntity)

    // Queue
    @Query("SELECT * FROM queue ORDER BY position")
    fun observeQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue ORDER BY position")
    suspend fun queue(): List<QueueItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue")
    suspend fun maxQueuePosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: QueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueAll(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue WHERE bookId = :bookId")
    suspend fun dequeue(bookId: Long)

    @Query("UPDATE queue SET position = :position WHERE bookId = :bookId")
    suspend fun setQueuePosition(bookId: Long, position: Int)

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM pomodoro_sessions")
    suspend fun deleteAllPomodoros()

    @Query("DELETE FROM reading_goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Query("DELETE FROM reading_progress")
    suspend fun deleteAllProgress()
}
