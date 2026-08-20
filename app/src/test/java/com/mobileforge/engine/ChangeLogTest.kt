package com.mobileforge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeLogTest {
    @Test
    fun showsAddedAndRemovedLines() {
        val lines = ChangeLog.diff("a\nb\nc\n", "a\nx\nc\n")
        assertTrue(lines.any { it.kind == '-' && it.text == "b" })
        assertTrue(lines.any { it.kind == '+' && it.text == "x" })
        val ch = FileChange(1, "a.txt", 0, "вы", "a\nb", "a\nx")
        assertTrue(ChangeLog.summary(ch).contains("+"))
        assertEquals("a.txt", ChangeLog.fromJson(ChangeLog.toJson(ch)).path)
    }
}
