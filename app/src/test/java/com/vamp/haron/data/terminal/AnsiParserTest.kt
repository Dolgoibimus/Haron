package com.vamp.haron.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnsiParserTest {

    private lateinit var parser: AnsiParser

    @Before
    fun setUp() {
        parser = AnsiParser()
    }

    // ── 1. Plain text without ANSI codes ──

    @Test
    fun `plain text without ANSI codes produces single span`() {
        val result = parser.parseLine("Hello, world!")
        assertEquals(1, result.spans.size)
        assertEquals("Hello, world!", result.spans[0].text)
        assertFalse(result.spans[0].bold)
        assertFalse(result.spans[0].italic)
        assertFalse(result.spans[0].underline)
        assertNull(result.spans[0].fg)
        assertNull(result.spans[0].bg)
    }

    // ── 2. Empty string ──

    @Test
    fun `empty string produces single empty span`() {
        val result = parser.parseLine("")
        assertEquals(1, result.spans.size)
        assertEquals("", result.spans[0].text)
    }

    // ── 3. Bold text ──

    @Test
    fun `bold on and reset produces two spans with correct bold flags`() {
        val result = parser.parseLine("\u001B[1mHello\u001B[0m world")
        assertEquals(2, result.spans.size)
        assertEquals("Hello", result.spans[0].text)
        assertTrue(result.spans[0].bold)
        assertEquals(" world", result.spans[1].text)
        assertFalse(result.spans[1].bold)
    }

    @Test
    fun `bold off with code 22`() {
        val result = parser.parseLine("\u001B[1mbold\u001B[22mnot bold")
        assertEquals(2, result.spans.size)
        assertTrue(result.spans[0].bold)
        assertFalse(result.spans[1].bold)
    }

    // ── 4. Multiple SGR in one sequence ──

    @Test
    fun `multiple SGR codes in one sequence sets bold italic underline`() {
        val result = parser.parseLine("\u001B[1;3;4mtext\u001B[0m")
        assertEquals(1, result.spans.size)
        assertEquals("text", result.spans[0].text)
        assertTrue(result.spans[0].bold)
        assertTrue(result.spans[0].italic)
        assertTrue(result.spans[0].underline)
    }

    // ── 5. Foreground color ──

    @Test
    fun `foreground color code sets non-null fg`() {
        val result = parser.parseLine("\u001B[31mred\u001B[0m")
        assertEquals(1, result.spans.size)
        assertEquals("red", result.spans[0].text)
        assertNotNull(result.spans[0].fg)
    }

    @Test
    fun `foreground default code 39 resets fg to null`() {
        val result = parser.parseLine("\u001B[31mred\u001B[39mdefault")
        assertEquals(2, result.spans.size)
        assertNotNull(result.spans[0].fg)
        assertNull(result.spans[1].fg)
    }

    @Test
    fun `bright foreground color 90-97 sets non-null fg`() {
        val result = parser.parseLine("\u001B[91mbright red\u001B[0m")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].fg)
    }

    // ── 6. Background color ──

    @Test
    fun `background color code sets non-null bg`() {
        val result = parser.parseLine("\u001B[42mgreen bg\u001B[0m")
        assertEquals(1, result.spans.size)
        assertEquals("green bg", result.spans[0].text)
        assertNotNull(result.spans[0].bg)
    }

    @Test
    fun `background default code 49 resets bg to null`() {
        val result = parser.parseLine("\u001B[42mgreen\u001B[49mdefault")
        assertEquals(2, result.spans.size)
        assertNotNull(result.spans[0].bg)
        assertNull(result.spans[1].bg)
    }

    @Test
    fun `bright background color 100-107 sets non-null bg`() {
        val result = parser.parseLine("\u001B[103mbright bg\u001B[0m")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].bg)
    }

    // ── 7. Reset in the middle ──

    @Test
    fun `reset in middle produces two spans with different styles`() {
        val result = parser.parseLine("\u001B[1mbold\u001B[0mnormal")
        assertEquals(2, result.spans.size)
        assertEquals("bold", result.spans[0].text)
        assertTrue(result.spans[0].bold)
        assertEquals("normal", result.spans[1].text)
        assertFalse(result.spans[1].bold)
    }

    // ── 8. plainText computed property ──

    @Test
    fun `plainText joins all span texts`() {
        val result = parser.parseLine("\u001B[1mHello\u001B[0m, \u001B[4mworld\u001B[0m!")
        assertEquals("Hello, world!", result.plainText)
    }

    @Test
    fun `plainText of plain text equals original`() {
        val result = parser.parseLine("no ansi here")
        assertEquals("no ansi here", result.plainText)
    }

    @Test
    fun `plainText of empty string is empty`() {
        val result = parser.parseLine("")
        assertEquals("", result.plainText)
    }

    // ── 9. State persistence between parseLine calls ──

    @Test
    fun `style state persists between parseLine calls without reset`() {
        // First call: turn bold on, no reset at end
        parser.parseLine("\u001B[1m")
        // Second call: text should inherit bold
        val result = parser.parseLine("still bold")
        assertEquals(1, result.spans.size)
        assertTrue(result.spans[0].bold)
    }

    @Test
    fun `fg color persists between parseLine calls`() {
        parser.parseLine("\u001B[32m")
        val result = parser.parseLine("green text")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].fg)
    }

    @Test
    fun `bg color persists between parseLine calls`() {
        parser.parseLine("\u001B[45m")
        val result = parser.parseLine("magenta bg text")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].bg)
    }

    @Test
    fun `italic and underline persist between parseLine calls`() {
        parser.parseLine("\u001B[3;4m")
        val result = parser.parseLine("styled")
        assertTrue(result.spans[0].italic)
        assertTrue(result.spans[0].underline)
    }

    // ── 10. reset() clears state ──

    @Test
    fun `reset clears bold state`() {
        parser.parseLine("\u001B[1m")
        parser.reset()
        val result = parser.parseLine("after reset")
        assertFalse(result.spans[0].bold)
    }

    @Test
    fun `reset clears italic and underline state`() {
        parser.parseLine("\u001B[3;4m")
        parser.reset()
        val result = parser.parseLine("after reset")
        assertFalse(result.spans[0].italic)
        assertFalse(result.spans[0].underline)
    }

    @Test
    fun `reset clears fg and bg colors`() {
        parser.parseLine("\u001B[31;42m")
        parser.reset()
        val result = parser.parseLine("after reset")
        assertNull(result.spans[0].fg)
        assertNull(result.spans[0].bg)
    }

    @Test
    fun `reset via SGR code 0 clears all state`() {
        val result = parser.parseLine("\u001B[1;3;4;31;42mbefore\u001B[0mafter")
        assertEquals(2, result.spans.size)
        val after = result.spans[1]
        assertEquals("after", after.text)
        assertFalse(after.bold)
        assertFalse(after.italic)
        assertFalse(after.underline)
        assertNull(after.fg)
        assertNull(after.bg)
    }

    // ── Additional edge cases ──

    @Test
    fun `italic on and off with codes 3 and 23`() {
        val result = parser.parseLine("\u001B[3mitalic\u001B[23mnot italic")
        assertEquals(2, result.spans.size)
        assertTrue(result.spans[0].italic)
        assertFalse(result.spans[1].italic)
    }

    @Test
    fun `underline on and off with codes 4 and 24`() {
        val result = parser.parseLine("\u001B[4munderline\u001B[24mnot underline")
        assertEquals(2, result.spans.size)
        assertTrue(result.spans[0].underline)
        assertFalse(result.spans[1].underline)
    }

    @Test
    fun `256-color foreground sets non-null fg`() {
        val result = parser.parseLine("\u001B[38;5;196mred256\u001B[0m")
        assertEquals(1, result.spans.size)
        assertEquals("red256", result.spans[0].text)
        assertNotNull(result.spans[0].fg)
    }

    @Test
    fun `256-color background sets non-null bg`() {
        val result = parser.parseLine("\u001B[48;5;21mbluebg\u001B[0m")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].bg)
    }

    @Test
    fun `RGB foreground sets non-null fg`() {
        val result = parser.parseLine("\u001B[38;2;255;128;0morange\u001B[0m")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].fg)
    }

    @Test
    fun `RGB background sets non-null bg`() {
        val result = parser.parseLine("\u001B[48;2;0;128;255mbluebg\u001B[0m")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].bg)
    }

    @Test
    fun `non-SGR CSI sequences are silently consumed`() {
        // CSI H = cursor position, CSI J = erase display — should be stripped
        val result = parser.parseLine("\u001B[2Jhello\u001B[Hworld")
        assertEquals("helloworld", result.plainText)
    }

    @Test
    fun `empty SGR (ESC bracket m) acts as reset`() {
        parser.parseLine("\u001B[1m")
        val result = parser.parseLine("\u001B[mnot bold")
        assertFalse(result.spans[0].bold)
    }

    @Test
    fun `multiple color changes produce multiple spans`() {
        val result = parser.parseLine("\u001B[31mred\u001B[32mgreen\u001B[34mblue")
        assertEquals(3, result.spans.size)
        assertEquals("red", result.spans[0].text)
        assertEquals("green", result.spans[1].text)
        assertEquals("blue", result.spans[2].text)
        // All should have non-null fg
        result.spans.forEach { assertNotNull(it.fg) }
    }

    @Test
    fun `combined fg and bg in single sequence`() {
        val result = parser.parseLine("\u001B[31;42mtext\u001B[0m")
        assertEquals(1, result.spans.size)
        assertNotNull(result.spans[0].fg)
        assertNotNull(result.spans[0].bg)
    }

    @Test
    fun `text before any escape is captured`() {
        val result = parser.parseLine("before\u001B[1mafter")
        assertEquals(2, result.spans.size)
        assertEquals("before", result.spans[0].text)
        assertFalse(result.spans[0].bold)
        assertEquals("after", result.spans[1].text)
        assertTrue(result.spans[1].bold)
    }

    @Test
    fun `trailing escape with no text after it`() {
        val result = parser.parseLine("hello\u001B[1m")
        assertEquals(1, result.spans.size)
        assertEquals("hello", result.spans[0].text)
    }

    @Test
    fun `only escape codes with no visible text produces empty span`() {
        val result = parser.parseLine("\u001B[1m\u001B[31m\u001B[0m")
        assertEquals(1, result.spans.size)
        assertEquals("", result.spans[0].text)
    }
}
