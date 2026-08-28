package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadTaskDao
import com.example.data.local.DownloadTaskEntity
import com.example.domain.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadTaskDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DownloadTaskDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.downloadTaskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetTaskById() = runBlocking {
        val task = DownloadTaskEntity(
            id = "task-1",
            url = "https://example.com/video",
            title = "Test Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            formatId = "137",
            formatDescription = "1080p • mp4",
            startTime = "00:00:00",
            endTime = "00:01:00",
            cutMode = "fast",
            status = DownloadStatus.QUEUED
        )

        dao.insertTask(task)

        val retrieved = dao.getTaskByIdSync("task-1")
        assertNotNull(retrieved)
        assertEquals("Test Video", retrieved?.title)
        assertEquals(DownloadStatus.QUEUED, retrieved?.status)
    }

    @Test
    fun updateTaskProgressAndStatus() = runBlocking {
        val task = DownloadTaskEntity(
            id = "task-2",
            url = "https://example.com/video",
            title = "Downloading Video",
            thumbnailUrl = null,
            formatId = "22",
            formatDescription = "720p",
            startTime = null,
            endTime = null,
            cutMode = "none",
            status = DownloadStatus.QUEUED
        )

        dao.insertTask(task)

        val updated = task.copy(
            status = DownloadStatus.DOWNLOADING,
            progress = 65f,
            downloadSpeed = "5.5 MB/s",
            eta = "00:10"
        )
        dao.updateTask(updated)

        val retrieved = dao.getTaskByIdSync("task-2")
        assertEquals(DownloadStatus.DOWNLOADING, retrieved?.status)
        assertEquals(65f, retrieved?.progress)
        assertEquals("5.5 MB/s", retrieved?.downloadSpeed)
    }

    @Test
    fun deleteTaskById() = runBlocking {
        val task = DownloadTaskEntity(
            id = "task-3",
            url = "https://example.com/video",
            title = "To Delete",
            thumbnailUrl = null,
            formatId = "18",
            formatDescription = "360p",
            startTime = null,
            endTime = null,
            cutMode = "none",
            status = DownloadStatus.COMPLETED
        )

        dao.insertTask(task)
        assertNotNull(dao.getTaskByIdSync("task-3"))

        dao.deleteTaskById("task-3")
        assertNull(dao.getTaskByIdSync("task-3"))
    }

    @Test
    fun clearFinishedTasksLeavesActiveTasks() = runBlocking {
        val active = DownloadTaskEntity(
            id = "active-1",
            url = "https://example.com/1",
            title = "Active",
            thumbnailUrl = null,
            formatId = "22",
            formatDescription = "720p",
            startTime = null,
            endTime = null,
            cutMode = "none",
            status = DownloadStatus.DOWNLOADING
        )

        val completed = DownloadTaskEntity(
            id = "completed-1",
            url = "https://example.com/2",
            title = "Done",
            thumbnailUrl = null,
            formatId = "22",
            formatDescription = "720p",
            startTime = null,
            endTime = null,
            cutMode = "none",
            status = DownloadStatus.COMPLETED
        )

        dao.insertTask(active)
        dao.insertTask(completed)

        dao.clearFinishedTasks()

        val all = dao.getAllTasks().first()
        assertEquals(1, all.size)
        assertEquals("active-1", all[0].id)
    }
}
