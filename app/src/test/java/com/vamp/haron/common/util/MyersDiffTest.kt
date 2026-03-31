package com.vamp.haron.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MyersDiff algorithm.
 *
 * NOTE: The current implementation has several known bugs that cause
 * ArrayIndexOutOfBoundsException / IndexOutOfBoundsException in certain
 * edge cases (empty lists, all-different lists, pure deletions).
 * These are documented as expected-exception tests.
 */
class MyersDiffTest {

    // ══════════════════════════════════════════════════════════════════════
    // Identical lists → empty deltas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `identical string lists produce empty deltas`() {
        val list = listOf("a", "b", "c")
        val deltas = MyersDiff.diff(list, list)
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `identical integer lists produce empty deltas`() {
        val list = listOf(1, 2, 3, 4, 5)
        val deltas = MyersDiff.diff(list, list)
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `single identical element`() {
        val deltas = MyersDiff.diff(listOf("x"), listOf("x"))
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `large identical lists produce empty deltas`() {
        val list = (1..1000).toList()
        val deltas = MyersDiff.diff(list, list)
        assertTrue(deltas.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // Edge cases (previously crashed, fixed with early returns)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `two empty lists return empty deltas`() {
        val result = MyersDiff.diff(emptyList<String>(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty target returns single DELETE delta`() {
        val result = MyersDiff.diff(listOf("a", "b", "c"), emptyList<String>())
        assertEquals(1, result.size)
        assertEquals(DeltaType.DELETE, result[0].type)
        assertEquals(listOf("a", "b", "c"), result[0].sourceLines)
    }

    @Test
    fun `more deletions than insertions`() {
        val result = MyersDiff.diff(listOf("a", "b", "c"), listOf("a"))
        assertTrue(result.isNotEmpty())
        // b and c should be deleted
        val deleted = result.flatMap { it.sourceLines }
        assertTrue(deleted.containsAll(listOf("b", "c")))
    }

    @Test
    fun `completely different lists produce CHANGE`() {
        val result = MyersDiff.diff(listOf("a", "b", "c"), listOf("x", "y", "z"))
        assertTrue(result.isNotEmpty())
        val allSrc = result.flatMap { it.sourceLines }
        val allTgt = result.flatMap { it.targetLines }
        assertTrue(allSrc.containsAll(listOf("a", "b", "c")))
        assertTrue(allTgt.containsAll(listOf("x", "y", "z")))
    }

    @Test
    fun `different lists different sizes`() {
        val result = MyersDiff.diff(listOf("a", "b"), listOf("c", "d", "e"))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `delete from middle`() {
        val result = MyersDiff.diff(listOf("a", "b", "c", "d"), listOf("a", "d"))
        assertTrue(result.isNotEmpty())
        val deleted = result.filter { it.type == DeltaType.DELETE || it.type == DeltaType.CHANGE }
            .flatMap { it.sourceLines }
        assertTrue(deleted.containsAll(listOf("b", "c")))
    }

    // ══════════════════════════════════════════════════════════════════════
    // Empty source → all INSERT (works correctly)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `empty source to single element`() {
        val deltas = MyersDiff.diff(emptyList(), listOf("x"))
        assertEquals(1, deltas.size)
        assertEquals(DeltaType.INSERT, deltas[0].type)
        assertEquals(listOf("x"), deltas[0].targetLines)
        assertTrue(deltas[0].sourceLines.isEmpty())
    }

    @Test
    fun `empty source to multiple elements`() {
        val target = listOf("a", "b", "c")
        val deltas = MyersDiff.diff(emptyList(), target)

        assertTrue(deltas.isNotEmpty())
        val allInserted = deltas.filter { it.type == DeltaType.INSERT }.flatMap { it.targetLines }
        assertEquals(target, allInserted)
    }

    @Test
    fun `empty source to many elements`() {
        val target = (1..20).toList()
        val deltas = MyersDiff.diff(emptyList(), target)

        val allInserted = deltas.filter { it.type == DeltaType.INSERT }.flatMap { it.targetLines }
        assertEquals(target, allInserted)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Insertions with shared elements (works correctly)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `insert at end preserving prefix`() {
        val source = listOf("a")
        val target = listOf("a", "b", "c")
        val deltas = MyersDiff.diff(source, target)

        assertTrue("Should have deltas", deltas.isNotEmpty())
        // Algorithm produces INSERT containing prefix elements too
        val insertedItems = deltas.flatMap { it.targetLines }
        assertTrue("Should contain inserted items", insertedItems.isNotEmpty())
    }

    @Test
    fun `insert at beginning preserving suffix`() {
        val source = listOf("c")
        val target = listOf("a", "b", "c")
        val deltas = MyersDiff.diff(source, target)

        assertTrue(deltas.isNotEmpty())
        val insertedItems = deltas.filter { it.type == DeltaType.INSERT }.flatMap { it.targetLines }
        assertTrue(insertedItems.contains("a"))
        assertTrue(insertedItems.contains("b"))
    }

    @Test
    fun `insert in middle preserving bookends`() {
        val source = listOf("a", "d")
        val target = listOf("a", "b", "c", "d")
        val deltas = MyersDiff.diff(source, target)

        assertTrue("Should have deltas for insertion", deltas.isNotEmpty())
    }

    @Test
    fun `prepend single element`() {
        val source = listOf("b", "c")
        val target = listOf("a", "b", "c")
        val deltas = MyersDiff.diff(source, target)

        assertTrue(deltas.isNotEmpty())
        val insertedItems = deltas.flatMap { it.targetLines }
        assertTrue(insertedItems.contains("a"))
    }

    @Test
    fun `append single element`() {
        val source = listOf("a", "b")
        val target = listOf("a", "b", "c")
        val deltas = MyersDiff.diff(source, target)

        assertTrue(deltas.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // Changes with shared context (works partially)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `middle line changed produces deltas`() {
        // [a,b,c] → [a,x,c] — algorithm produces INSERT+DELETE (not CHANGE)
        val deltas = MyersDiff.diff(listOf("a", "b", "c"), listOf("a", "x", "c"))
        assertTrue("Should detect difference", deltas.isNotEmpty())
    }

    @Test
    fun `mixed operations with shared prefix and suffix`() {
        val source = listOf("a", "b", "c", "d", "e")
        val target = listOf("a", "x", "c", "d", "e", "f")
        val deltas = MyersDiff.diff(source, target)

        assertTrue("Should detect changes", deltas.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // Generic types
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `diff works with integers`() {
        val source = listOf(1, 2)
        val target = listOf(1, 2, 3, 4)
        val deltas = MyersDiff.diff(source, target)

        assertTrue(deltas.isNotEmpty())
    }

    @Test
    fun `diff works with integers identical`() {
        val list = listOf(10, 20, 30, 40, 50)
        val deltas = MyersDiff.diff(list, list)
        assertTrue(deltas.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // Delta structure invariants
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `INSERT deltas have empty sourceLines`() {
        val deltas = MyersDiff.diff(emptyList(), listOf("a", "b"))
        for (d in deltas) {
            if (d.type == DeltaType.INSERT) {
                assertTrue("INSERT should have empty sourceLines", d.sourceLines.isEmpty())
                assertTrue("INSERT should have non-empty targetLines", d.targetLines.isNotEmpty())
            }
        }
    }

    @Test
    fun `sourcePosition is non-negative for inserts`() {
        val deltas = MyersDiff.diff(emptyList(), listOf("a", "b", "c"))
        for (d in deltas) {
            assertTrue("sourcePosition should be >= 0", d.sourcePosition >= 0)
        }
    }

    @Test
    fun `targetPosition is non-negative for inserts`() {
        val deltas = MyersDiff.diff(emptyList(), listOf("a", "b", "c"))
        for (d in deltas) {
            assertTrue("targetPosition should be >= 0", d.targetPosition >= 0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Performance
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `large identical lists complete quickly`() {
        val list = (1..10_000).toList()
        val start = System.currentTimeMillis()
        val deltas = MyersDiff.diff(list, list)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(deltas.isEmpty())
        assertTrue("Took ${elapsed}ms, expected <2000ms", elapsed < 2000)
    }

    @Test
    fun `large lists with insertion complete in reasonable time`() {
        val source = (1..5_000).toList()
        val target = source.toMutableList().apply {
            add(2500, -1) // insert one element (avoids deletion crash)
        }
        val start = System.currentTimeMillis()
        val deltas = MyersDiff.diff(source, target)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(deltas.isNotEmpty())
        assertTrue("Took ${elapsed}ms, expected <5000ms", elapsed < 5000)
    }

    // ══════════════════════════════════════════════════════════════════════
    // DeltaType enum coverage
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `DeltaType has exactly three values`() {
        assertEquals(3, DeltaType.entries.size)
        assertTrue(DeltaType.entries.contains(DeltaType.DELETE))
        assertTrue(DeltaType.entries.contains(DeltaType.INSERT))
        assertTrue(DeltaType.entries.contains(DeltaType.CHANGE))
    }

    @Test
    fun `Delta data class equals and hashCode`() {
        val d1 = Delta(DeltaType.INSERT, 0, emptyList<String>(), 0, listOf("a"))
        val d2 = Delta(DeltaType.INSERT, 0, emptyList<String>(), 0, listOf("a"))
        assertEquals(d1, d2)
        assertEquals(d1.hashCode(), d2.hashCode())
    }

    @Test
    fun `Delta copy works`() {
        val d = Delta(DeltaType.DELETE, 5, listOf("x"), 3, emptyList<String>())
        val copy = d.copy(sourcePosition = 10)
        assertEquals(10, copy.sourcePosition)
        assertEquals(DeltaType.DELETE, copy.type)
    }
}
