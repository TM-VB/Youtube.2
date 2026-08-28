package com.example.domain.util

object FileNameSanitizer {

    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|\r\n\t]""")

    fun sanitize(rawTitle: String, extension: String = "mp4"): String {
        val cleanExt = extension.trim().removePrefix(".")
        val sanitizedTitle = rawTitle
            .replace(ILLEGAL_CHARS, "_")
            .trim()
            .trim('.')

        val finalTitle = if (sanitizedTitle.isBlank()) {
            "video_${System.currentTimeMillis()}"
        } else {
            // Android filename max length is typically 255 bytes. Keep title under 120 chars safely.
            if (sanitizedTitle.length > 120) {
                sanitizedTitle.substring(0, 120).trim()
            } else {
                sanitizedTitle
            }
        }

        return if (cleanExt.isNotBlank()) "$finalTitle.$cleanExt" else finalTitle
    }
}
