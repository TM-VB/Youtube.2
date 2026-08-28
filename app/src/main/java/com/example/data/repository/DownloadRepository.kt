package com.example.data.repository

import com.example.data.local.DownloadTaskDao
import com.example.data.local.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val dao: DownloadTaskDao) {

    val allTasks: Flow<List<DownloadTaskEntity>> = dao.getAllTasks()

    fun getTaskById(id: String): Flow<DownloadTaskEntity?> = dao.getTaskById(id)

    suspend fun getTaskByIdSync(id: String): DownloadTaskEntity? = dao.getTaskByIdSync(id)

    suspend fun insertTask(task: DownloadTaskEntity) = dao.insertTask(task)

    suspend fun updateTask(task: DownloadTaskEntity) = dao.updateTask(task)

    suspend fun deleteTask(id: String) = dao.deleteTaskById(id)

    suspend fun clearFinishedTasks() = dao.clearFinishedTasks()
}
