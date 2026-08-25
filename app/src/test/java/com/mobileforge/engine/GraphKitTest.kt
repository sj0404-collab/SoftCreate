package com.mobileforge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphKitTest {
    @Test
    fun roundtrip() {
        val g = VisualGraph.playerDefault()
        val p = VisualGraph.parse(g.toJson().toString())
        assertEquals(g.nodes.size, p.nodes.size)
        assertTrue(p.links.isNotEmpty())
    }
}
