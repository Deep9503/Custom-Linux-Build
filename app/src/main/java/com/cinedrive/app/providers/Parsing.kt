package com.cinedrive.app.providers

/** Pure helpers (no Android types) so they can be unit tested on the JVM. */
object Parsing {

    /** "3117.52" (seconds) or "52:57" / "1:02:03" -> seconds. */
    fun durationSeconds(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (':' in s) {
            val parts = s.split(':').map { it.toDoubleOrNull() ?: return null }
            return parts.fold(0.0) { acc, p -> acc * 60 + p }
        }
        return s.toDoubleOrNull()
    }

    /** Keeps letters, digits and spaces so user text can't change the search query's meaning. */
    fun safeQuery(q: String): String =
        q.replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Strips HTML tags/entities from archive descriptions. */
    fun plainText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html
            .replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun year(raw: String?): Int? = raw?.let { Regex("(1[89]|20)\\d{2}").find(it)?.value?.toIntOrNull() }

    /** Groups an item's .mp4 files into one entry per video, preferring the best encode. */
    fun pickVideos(files: List<Pair<String, String>>): List<String> {
        // files: (name, format)
        val mp4 = files.filter { it.first.endsWith(".mp4", ignoreCase = true) }
        val groups = mp4.groupBy { baseName(it.first) }
        return groups.values.map { group ->
            group.maxByOrNull { rank(it.second, it.first) }!!.first
        }.sortedBy { it.lowercase() }
    }

    fun baseName(name: String): String = name
        .removeSuffix(".mp4").removeSuffix(".MP4")
        .removeSuffix("_512kb").removeSuffix(".ia")
        .lowercase()

    private fun rank(format: String, name: String): Int = when {
        format.equals("h.264", true) -> 4
        format.startsWith("h.264", true) -> 3
        format.equals("MPEG4", true) -> 2
        name.contains("_512kb") -> 1
        else -> 0
    }
}
