package com.cinedrive.app

import com.cinedrive.app.providers.Parsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {
    @Test fun durationFormats() {
        assertEquals(3117.52, Parsing.durationSeconds("3117.52")!!, 0.001)
        assertEquals(3177.0, Parsing.durationSeconds("52:57")!!, 0.001)
        assertEquals(3723.0, Parsing.durationSeconds("1:02:03")!!, 0.001)
        assertNull(Parsing.durationSeconds("abc"))
        assertNull(Parsing.durationSeconds(null))
    }

    @Test fun safeQueryStripsOperators() {
        assertEquals("night of the living dead", Parsing.safeQuery("night (of) the: living* dead"))
        assertEquals("", Parsing.safeQuery("()[]:*"))
    }

    @Test fun plainTextStripsHtml() {
        assertEquals("A film\nby someone & co", Parsing.plainText("A film<br/>by <b>someone</b> &amp; co"))
    }

    @Test fun yearFromDates() {
        assertEquals(1938, Parsing.year("1938-05-01"))
        assertNull(Parsing.year("unknown"))
    }

    @Test fun pickVideosPrefersBestEncodePerVideo() {
        val files = listOf(
            "movie.mp4" to "h.264", "movie_512kb.mp4" to "512Kb MPEG4", "movie.ogv" to "Ogg Video",
        )
        assertEquals(listOf("movie.mp4"), Parsing.pickVideos(files))
        val series = listOf("ep2.mp4" to "h.264", "ep1.mp4" to "h.264", "ep1_512kb.mp4" to "512Kb MPEG4")
        assertEquals(listOf("ep1.mp4", "ep2.mp4"), Parsing.pickVideos(series))
    }
}
