package com.mobileforge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorTest {
    @Test
    fun extractsQuotedName() {
        assertEquals("Космодром", Director.extractProjectName("создай игру «Космодром»"))
        assertEquals("NeonHunt", Director.extractProjectName("project called NeonHunt please"))
    }

    @Test
    fun overridesSkyArena() {
        val name = Director.resolveName("сделай игру Танки", "SkyArena")
        assertEquals("Танки", name)
    }

    @Test
    fun inventsWhenNoName() {
        val name = Director.resolveName("придумай игру", "SkyArena")
        assertFalse(Director.isBannedDefault(name))
        assertFalse(name.equals("SkyArena", true))
    }

    @Test
    fun controlsOnlyIfAsked() {
        assertFalse(Director.wantsControls("придумай игру Танки"))
        assertTrue(Director.wantsControls("добавь джойстик и кнопку прыжка"))
        assertFalse(Director.wantsAnimation("собери монеты"))
        assertTrue(Director.wantsAnimation("сделай анимацию открытия двери"))
    }
}
