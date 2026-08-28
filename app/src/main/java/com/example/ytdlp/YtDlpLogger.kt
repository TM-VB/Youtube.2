package com.example.ytdlp

import android.util.Log
import java.net.URI

/**
 * Dedicated secure logger for yt-dlp operations.
 * Explicitly guards against logging sensitive information (tokens, passwords, cookies, authorization headers).
 */
object YtDlpLogger {

    private const val TAG = "YtDlpLogger"

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val isError: Boolean = false
    )

    private val logHistory = mutableListOf<LogEntry>()
    private const val MAX_HISTORY = 100

    @Synchronized
    fun getRecentLogs(): List<LogEntry> = logHistory.toList()

    fun logAnalyzeStarted(url: String, processId: String? = null) {
        val sanitized = sanitizeUrl(url)
        val procInfo = if (processId != null) " [process=$processId]" else ""
        val msg = "Analyze started for URL: $sanitized$procInfo"
        record(msg, false)
        Log.i(TAG, msg)
    }

    fun logAnalyzeCompleted(url: String, formatCount: Int, durationMs: Long, extractor: String? = null) {
        val sanitized = sanitizeUrl(url)
        val extInfo = if (extractor != null) " [extractor=$extractor]" else ""
        val msg = "Analyze completed in ${durationMs}ms: found $formatCount formats for $sanitized$extInfo"
        record(msg, false)
        Log.i(TAG, msg)
    }

    fun logAnalyzeCancelled(url: String, durationMs: Long, processId: String? = null) {
        val sanitized = sanitizeUrl(url)
        val procInfo = if (processId != null) " [process=$processId]" else ""
        val msg = "Analyze cancelled after ${durationMs}ms for $sanitized$procInfo"
        record(msg, false)
        Log.w(TAG, msg)
    }

    fun logAnalyzeError(url: String, throwable: Throwable, durationMs: Long) {
        val sanitized = sanitizeUrl(url)
        val errorDetail = throwable.message?.take(200) ?: throwable.javaClass.simpleName
        val msg = "Analyze failed after ${durationMs}ms for $sanitized: $errorDetail"
        record(msg, true)
        Log.e(TAG, msg, throwable)
    }

    fun logFormatSelected(formatId: String, isManual: Boolean) {
        val type = if (isManual) "Manual" else "Preset/List"
        val msg = "Format selected ($type): $formatId"
        record(msg, false)
        Log.d(TAG, msg)
    }

    @Synchronized
    private fun record(msg: String, isError: Boolean) {
        if (logHistory.size >= MAX_HISTORY) {
            logHistory.removeAt(0)
        }
        logHistory.add(LogEntry(tag = TAG, message = msg, isError = isError))
    }

    /**
     * Strips credentials, auth tokens, and session cookies from URLs before logging.
     */
    fun sanitizeUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl.trim())
            val host = uri.host ?: return "invalid-url"
            val path = uri.path ?: ""
            val query = uri.query

            val safeQuery = if (query != null) {
                query.split("&").joinToString("&") { param ->
                    val parts = param.split("=", limit = 2)
                    val key = parts[0]
                    if (isSensitiveParam(key)) {
                        "$key=***REDACTED***"
                    } else {
                        param
                    }
                }
            } else null

            val scheme = uri.scheme ?: "https"
            if (safeQuery != null) {
                "$scheme://$host$path?$safeQuery"
            } else {
                "$scheme://$host$path"
            }
        } catch (_: Exception) {
            rawUrl.take(60) + "..."
        }
    }

    private fun isSensitiveParam(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("key") ||
            lower.contains("token") ||
            lower.contains("auth") ||
            lower.contains("pass") ||
            lower.contains("sig") ||
            lower.contains("secret") ||
            lower.contains("cookie")
    }
}
