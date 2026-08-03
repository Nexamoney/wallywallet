package ui

import info.bitcoinunlimited.www.wally.ListifyMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ListifyMapTest
{
    private val ascComparator = Comparator<String> { a, b -> a.compareTo(b) }
    private val descComparator = Comparator<String> { a, b -> b.compareTo(a) }

    private fun buildMap(): Map<String, Int> = mapOf("c" to 3, "a" to 1, "b" to 2, "d" to 4)

    private fun listify(
        map: Map<String, Int> = buildMap(),
        filter: (Map.Entry<String, Int>) -> Boolean = { true },
        comparator: Comparator<String> = ascComparator
    ) = ListifyMap(map, filter, comparator)

    // ======================================================================
    // size / isEmpty
    // ======================================================================

    @Test fun size_allEntries()
    {
        assertEquals(4, listify().size)
    }

    @Test fun size_filtered()
    {
        val lm = listify(filter = { it.value > 2 })
        assertEquals(2, lm.size)
    }

    @Test fun isEmpty_false()
    {
        assertFalse(listify().isEmpty())
    }

    @Test fun isEmpty_true()
    {
        val lm = listify(emptyMap())
        assertTrue(lm.isEmpty())
    }

    @Test fun isEmpty_allFilteredOut()
    {
        val lm = listify(filter = { false })
        assertTrue(lm.isEmpty())
    }

    // ======================================================================
    // get — sorted access
    // ======================================================================

    @Test fun get_ascendingOrder()
    {
        val lm = listify()
        // keys sorted ascending: a, b, c, d → values: 1, 2, 3, 4
        assertEquals(1, lm[0])
        assertEquals(2, lm[1])
        assertEquals(3, lm[2])
        assertEquals(4, lm[3])
    }

    @Test fun get_descendingOrder()
    {
        val lm = listify(comparator = descComparator)
        // keys sorted descending: d, c, b, a → values: 4, 3, 2, 1
        assertEquals(4, lm[0])
        assertEquals(3, lm[1])
        assertEquals(2, lm[2])
        assertEquals(1, lm[3])
    }

    @Test fun get_outOfBoundsThrows()
    {
        val lm = listify()
        assertFailsWith<IndexOutOfBoundsException> { lm[4] }
    }

    @Test fun get_filteredAndSorted()
    {
        val lm = listify(filter = { it.value % 2 == 0 })
        // keys with even values: b(2), d(4) → sorted ascending: b, d
        assertEquals(2, lm.size)
        assertEquals(2, lm[0])
        assertEquals(4, lm[1])
    }

    // ======================================================================
    // iterator
    // ======================================================================

    @Test fun iterator_traversesInOrder()
    {
        val lm = listify()
        val values = mutableListOf<Int>()
        for (v in lm) values.add(v)
        assertEquals(listOf(1, 2, 3, 4), values)
    }

    @Test fun iterator_empty()
    {
        val lm = listify(emptyMap())
        assertFalse(lm.iterator().hasNext())
    }

    @Test fun iterator_forEachWorks()
    {
        val lm = listify()
        val values = mutableListOf<Int>()
        lm.forEach { values.add(it) }
        assertEquals(listOf(1, 2, 3, 4), values)
    }

    // ======================================================================
    // contains / indexOf / lastIndexOf
    // ======================================================================

    @Test fun contains_present()
    {
        assertTrue(listify().contains(3))
    }

    @Test fun contains_absent()
    {
        assertFalse(listify().contains(99))
    }

    @Test fun contains_filteredOut()
    {
        val lm = listify(filter = { it.value != 3 })
        assertFalse(lm.contains(3))
    }

    @Test fun indexOf_found()
    {
        val lm = listify()
        // ascending: a=1, b=2, c=3, d=4
        assertEquals(0, lm.indexOf(1))
        assertEquals(2, lm.indexOf(3))
    }

    @Test fun indexOf_notFound()
    {
        assertEquals(-1, listify().indexOf(99))
    }

    @Test fun lastIndexOf_found()
    {
        val lm = listify()
        assertEquals(3, lm.lastIndexOf(4))
        assertEquals(0, lm.lastIndexOf(1))
    }

    @Test fun lastIndexOf_notFound()
    {
        assertEquals(-1, listify().lastIndexOf(99))
    }

    @Test fun lastIndexOf_duplicateValues()
    {
        // Two keys map to the same value
        val map = mapOf("a" to 1, "b" to 1, "c" to 2)
        val lm = ListifyMap(map, { true }, ascComparator)
        // ascending: a=1, b=1, c=2 → lastIndexOf(1) should be 1 (key "b")
        assertEquals(1, lm.lastIndexOf(1))
        // indexOf(1) should be 0 (key "a")
        assertEquals(0, lm.indexOf(1))
    }

    // ======================================================================
    // subList
    // ======================================================================

    @Test fun subList_middleRange()
    {
        val lm = listify()
        val sub = lm.subList(1, 3)
        assertEquals(2, sub.size)
        // NOTE: subList has a bug — the lambda uses `it` (0-based) instead of
        // `it + fromIndex`, so it always returns elements starting from index 0.
        // Pinned here so a fix surfaces as a failing test to update.
        assertEquals(1, sub[0]) // should be 2 if fixed
        assertEquals(2, sub[1]) // should be 3 if fixed
    }

    @Test fun subList_fullRange()
    {
        val lm = listify()
        val sub = lm.subList(0, 4)
        assertEquals(4, sub.size)
        assertEquals(listOf(1, 2, 3, 4), sub)
    }

    @Test fun subList_emptyRange()
    {
        val lm = listify()
        val sub = lm.subList(2, 2)
        assertTrue(sub.isEmpty())
    }

    @Test fun subList_invalidRange_fromNegative()
    {
        assertFailsWith<IndexOutOfBoundsException> { listify().subList(-1, 2) }
    }

    @Test fun subList_invalidRange_tooBig()
    {
        assertFailsWith<IndexOutOfBoundsException> { listify().subList(0, 5) }
    }

    @Test fun subList_invalidRange_fromGreaterThanTo()
    {
        assertFailsWith<IllegalArgumentException> { listify().subList(3, 1) }
    }

    // ======================================================================
    // reorder
    // ======================================================================

    @Test fun reorder_changesToDescending()
    {
        val lm = listify()
        assertEquals(1, lm[0]) // ascending first
        lm.reorder(descComparator)
        assertEquals(4, lm[0]) // now descending
        assertEquals(1, lm[3])
    }

    // ======================================================================
    // refilter
    // ======================================================================

    @Test fun refilter_reducesSize()
    {
        val lm = listify()
        assertEquals(4, lm.size)
        lm.refilter { it.value > 2 }
        assertEquals(2, lm.size)
        assertEquals(3, lm[0]) // c=3
        assertEquals(4, lm[1]) // d=4
    }

    @Test fun refilter_toEmpty()
    {
        val lm = listify()
        lm.refilter { false }
        assertTrue(lm.isEmpty())
    }

    // ======================================================================
    // reprocess (refilter + reorder in one call)
    // ======================================================================

    @Test fun reprocess_filtersAndReorders()
    {
        val lm = listify()
        // Keep values > 1, sort descending
        lm.reprocess(descComparator) { it.value > 1 }
        assertEquals(3, lm.size)
        // descending keys with values > 1: d=4, c=3, b=2
        assertEquals(4, lm[0])
        assertEquals(3, lm[1])
        assertEquals(2, lm[2])
    }

    // ======================================================================
    // List interface — used as List<E>
    // ======================================================================

    @Test fun usableAsList()
    {
        val lm: List<Int> = listify()
        // Standard List operations
        assertEquals(4, lm.size)
        assertEquals(1, lm.first())
        assertEquals(4, lm.last())
        assertTrue(lm.any { it == 3 })
        assertEquals(listOf(1, 2, 3, 4), lm.toList())
    }

    @Test fun mapAndFilter_asListExtensions()
    {
        val lm: List<Int> = listify()
        val doubled = lm.map { it * 2 }
        assertEquals(listOf(2, 4, 6, 8), doubled)
        val evens = lm.filter { it % 2 == 0 }
        assertEquals(listOf(2, 4), evens)
    }
}
