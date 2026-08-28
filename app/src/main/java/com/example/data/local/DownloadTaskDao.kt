package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun getTaskById(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskByIdSync(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED' OR status = 'CANCELLED' OR status = 'FAILED'")
    suspend fun clearFinishedTasks()
}
