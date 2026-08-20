package com.mobileforge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("Танки", Director.resolveName("назови Танки", "SkyArena"))
        assertEquals("Танки", Director.resolveName("сделай игру Танки", "SkyArena"))
    }

    @Test
    fun inventsWhenNoName() {
        val name = Director.resolveName("придумай игру", "SkyArena")
        assertFalse(Director.isBannedDefault(name))
        assertFalse(name.equals("SkyArena", true))
    }

    @Test
    fun doesNotExtractPreposition() {
        assertNull(Director.extractProjectName("РПГ игра для ассетов pollination ai либо сам создай"))
        val name = Director.resolveName("РПГ игра для ассетов pollination ai", "для")
        assertFalse(name.equals("для", true))
        assertFalse(name.equals("SkyArena", true))
    }

    @Test
    fun followUpKeepsCurrent() {
        assertTrue(Director.isFollowUp("ну"))
        assertTrue(Director.isFollowUp("что создал"))
        assertTrue(Director.isFollowUp("дальше"))
        assertFalse(Director.isFollowUp("сделай рпг с четырьмя биомами"))
        assertEquals("NuGame", Director.resolveName("ну", "для", "NuGame"))
    }

    @Test
    fun controlsOnlyIfAsked() {
        assertFalse(Director.wantsControls("придумай игру Танки"))
        assertTrue(Director.wantsControls("добавь джойстик и кнопку прыжка"))
        assertFalse(Director.wantsAnimation("собери монеты"))
        assertTrue(Director.wantsAnimation("сделай анимацию открытия двери"))
        assertTrue(Director.wantsWorld("РПГ игра четыре биома население"))
    }

    @Test
    fun doesNotExtractAnyGame() {
        assertNull(Director.extractProjectName("создай 2d игру любую и плагины для неё"))
        val name = Director.resolveName("создай 2d игру любую и плагины для неё", "любую")
        assertFalse(name.equals("любую", true))
        assertTrue(Director.wants2D("создай 2d игру любую"))
    }

    @Test
    fun formatMs() {
        assertEquals("0мс", Director.formatMs(0))
        assertEquals("12мс", Director.formatMs(12))
        assertTrue(Director.formatMs(1400).endsWith("с"))
    }
}
