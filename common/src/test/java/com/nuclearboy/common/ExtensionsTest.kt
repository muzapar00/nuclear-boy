package com.nuclearboy.common

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ExtensionsTest {

    @Test
    fun `truncate short string unchanged`() {
        assertEquals("hello", "hello".truncate(10))
    }

    @Test
    fun `truncate long string with ellipsis`() {
        assertEquals("hello…", "hello world".truncate(5))
    }

    @Test
    fun `maskApiKey keeps prefix and suffix`() {
        val masked = "sk-v4-1234567890abcdef".maskApiKey()
        assertTrue(masked.startsWith("sk-v4-12"))
        assertTrue(masked.endsWith("cdef"))
        assertTrue(masked.contains("****"))
    }

    @Test
    fun `maskApiKey short key returns stars`() {
        assertEquals("****", "abc".maskApiKey())
    }

    @Test
    fun `fileExtension returns extension`() {
        assertEquals("kt", "MyFile.kt".fileExtension())
        assertEquals("docx", "report.docx".fileExtension())
    }

    @Test
    fun `fileExtension no extension returns empty`() {
        assertEquals("", "Makefile".fileExtension())
    }

    @Test
    fun `isTextFile recognizes common extensions`() {
        assertTrue("app.kt".isTextFile())
        assertTrue("main.py".isTextFile())
        assertTrue("index.js".isTextFile())
        assertTrue("styles.css".isTextFile())
        assertTrue("config.json".isTextFile())
        assertTrue("build.gradle".isTextFile())
    }

    @Test
    fun `isTextFile rejects non-text`() {
        assertFalse("image.png".isTextFile())
        assertFalse("video.mp4".isTextFile())
        assertFalse("archive.zip".isTextFile())
    }

    @Test
    fun `isDocumentFile recognizes docs`() {
        assertTrue("report.docx".isDocumentFile())
        assertTrue("data.xlsx".isDocumentFile())
        assertTrue("slides.pptx".isDocumentFile())
    }

    @Test
    fun `toFileSizeString formats correctly`() {
        assertEquals("500 B", 500L.toFileSizeString())
        assertEquals("1 KB", 1024L.toFileSizeString())
        // ~1 MB
        assertTrue((1024L * 1024L).toFileSizeString().contains("MB"))
    }

    @Test
    fun `toRelativeTimeString just now`() {
        assertEquals("刚刚", System.currentTimeMillis().toRelativeTimeString())
    }

    @Test
    fun `night mode detection`() {
        assertTrue(LocalTime.of(23, 0).isNightMode())
        assertTrue(LocalTime.of(3, 0).isNightMode())
        assertFalse(LocalTime.of(10, 0).isNightMode())
        assertFalse(LocalTime.of(15, 0).isNightMode())
    }

    @Test
    fun `greeting returns appropriate greeting`() {
        assertTrue(LocalTime.of(7, 0).toGreeting().contains("早"))
        assertTrue(LocalTime.of(14, 0).toGreeting().contains("下午"))
        assertTrue(LocalTime.of(21, 0).toGreeting().contains("晚上"))
        assertTrue(LocalTime.of(2, 0).toGreeting().contains("夜深"))
    }

    @Test
    fun `withMoodEmoji adds emoji for error context`() {
        val result = "出了点问题".withMoodEmoji()
        assertTrue(result.contains("😅"))
    }

    @Test
    fun `withMoodEmoji adds emoji for success`() {
        val result = "搞定了".withMoodEmoji()
        assertTrue(result.contains("✨"))
    }
}
