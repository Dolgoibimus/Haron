package com.vamp.haron.common.util

import com.vamp.haron.domain.model.FileEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class IconResTest {

    private fun entry(name: String, ext: String, isDir: Boolean = false) = FileEntry(
        name = name,
        path = "/test/$name",
        isDirectory = isDir,
        size = 100L,
        lastModified = 0L,
        extension = ext,
        isHidden = false,
        childCount = 0
    )

    @Test
    fun `directory returns folder`() {
        assertEquals("folder", entry("docs", "", isDir = true).iconRes())
    }

    // --- Images ---

    @Test
    fun `jpg returns image`() = assertEquals("image", entry("photo.jpg", "jpg").iconRes())

    @Test
    fun `jpeg returns image`() = assertEquals("image", entry("photo.jpeg", "jpeg").iconRes())

    @Test
    fun `png returns image`() = assertEquals("image", entry("icon.png", "png").iconRes())

    @Test
    fun `gif returns image`() = assertEquals("image", entry("anim.gif", "gif").iconRes())

    @Test
    fun `bmp returns image`() = assertEquals("image", entry("raw.bmp", "bmp").iconRes())

    @Test
    fun `webp returns image`() = assertEquals("image", entry("img.webp", "webp").iconRes())

    @Test
    fun `svg returns image`() = assertEquals("image", entry("logo.svg", "svg").iconRes())

    // --- Video ---

    @Test
    fun `mp4 returns video`() = assertEquals("video", entry("clip.mp4", "mp4").iconRes())

    @Test
    fun `mkv returns video`() = assertEquals("video", entry("movie.mkv", "mkv").iconRes())

    @Test
    fun `avi returns video`() = assertEquals("video", entry("old.avi", "avi").iconRes())

    @Test
    fun `mov returns video`() = assertEquals("video", entry("rec.mov", "mov").iconRes())

    @Test
    fun `webm returns video`() = assertEquals("video", entry("web.webm", "webm").iconRes())

    @Test
    fun `3gp returns video`() = assertEquals("video", entry("cam.3gp", "3gp").iconRes())

    @Test
    fun `ts returns video`() = assertEquals("video", entry("stream.ts", "ts").iconRes())

    // --- Audio ---

    @Test
    fun `mp3 returns audio`() = assertEquals("audio", entry("song.mp3", "mp3").iconRes())

    @Test
    fun `wav returns audio`() = assertEquals("audio", entry("raw.wav", "wav").iconRes())

    @Test
    fun `flac returns audio`() = assertEquals("audio", entry("hi.flac", "flac").iconRes())

    @Test
    fun `ogg returns audio`() = assertEquals("audio", entry("vo.ogg", "ogg").iconRes())

    @Test
    fun `m4a returns audio`() = assertEquals("audio", entry("rec.m4a", "m4a").iconRes())

    @Test
    fun `opus returns audio`() = assertEquals("audio", entry("voice.opus", "opus").iconRes())

    // --- PDF ---

    @Test
    fun `pdf returns pdf`() = assertEquals("pdf", entry("doc.pdf", "pdf").iconRes())

    // --- Documents ---

    @Test
    fun `doc returns document`() = assertEquals("document", entry("file.doc", "doc").iconRes())

    @Test
    fun `docx returns document`() = assertEquals("document", entry("file.docx", "docx").iconRes())

    @Test
    fun `odt returns document`() = assertEquals("document", entry("file.odt", "odt").iconRes())

    @Test
    fun `rtf returns document`() = assertEquals("document", entry("file.rtf", "rtf").iconRes())

    @Test
    fun `fb2 returns document`() = assertEquals("document", entry("book.fb2", "fb2").iconRes())

    // --- Spreadsheet ---

    @Test
    fun `xls returns spreadsheet`() = assertEquals("spreadsheet", entry("data.xls", "xls").iconRes())

    @Test
    fun `xlsx returns spreadsheet`() = assertEquals("spreadsheet", entry("data.xlsx", "xlsx").iconRes())

    @Test
    fun `csv returns spreadsheet`() = assertEquals("spreadsheet", entry("data.csv", "csv").iconRes())

    // --- Presentation ---

    @Test
    fun `pptx returns presentation`() = assertEquals("presentation", entry("slides.pptx", "pptx").iconRes())

    // --- Archive ---

    @Test
    fun `zip returns archive`() = assertEquals("archive", entry("files.zip", "zip").iconRes())

    @Test
    fun `rar returns archive`() = assertEquals("archive", entry("files.rar", "rar").iconRes())

    @Test
    fun `7z returns archive`() = assertEquals("archive", entry("files.7z", "7z").iconRes())

    @Test
    fun `tar returns archive`() = assertEquals("archive", entry("files.tar", "tar").iconRes())

    @Test
    fun `gz returns archive`() = assertEquals("archive", entry("files.gz", "gz").iconRes())

    // --- APK ---

    @Test
    fun `apk returns apk`() = assertEquals("apk", entry("app.apk", "apk").iconRes())

    // --- Text ---

    @Test
    fun `txt returns text`() = assertEquals("text", entry("note.txt", "txt").iconRes())

    @Test
    fun `md returns text`() = assertEquals("text", entry("readme.md", "md").iconRes())

    @Test
    fun `json returns text`() = assertEquals("text", entry("config.json", "json").iconRes())

    @Test
    fun `xml returns text`() = assertEquals("text", entry("layout.xml", "xml").iconRes())

    @Test
    fun `log returns text`() = assertEquals("text", entry("app.log", "log").iconRes())

    @Test
    fun `yaml returns text`() = assertEquals("text", entry("ci.yaml", "yaml").iconRes())

    @Test
    fun `sql returns text`() = assertEquals("text", entry("db.sql", "sql").iconRes())

    // --- Code ---

    @Test
    fun `kt returns code`() = assertEquals("code", entry("Main.kt", "kt").iconRes())

    @Test
    fun `java returns code`() = assertEquals("code", entry("App.java", "java").iconRes())

    @Test
    fun `py returns code`() = assertEquals("code", entry("script.py", "py").iconRes())

    @Test
    fun `js returns code`() = assertEquals("code", entry("app.js", "js").iconRes())

    @Test
    fun `html returns code`() = assertEquals("code", entry("index.html", "html").iconRes())

    @Test
    fun `css returns code`() = assertEquals("code", entry("style.css", "css").iconRes())

    @Test
    fun `sh returns code`() = assertEquals("code", entry("run.sh", "sh").iconRes())

    @Test
    fun `dart returns code`() = assertEquals("code", entry("main.dart", "dart").iconRes())

    // --- Unknown ---

    @Test
    fun `unknown extension returns file`() = assertEquals("file", entry("data.xyz", "xyz").iconRes())

    @Test
    fun `empty extension returns file`() = assertEquals("file", entry("Makefile", "").iconRes())
}
