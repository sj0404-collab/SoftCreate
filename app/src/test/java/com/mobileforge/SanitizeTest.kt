package com.mobileforge

import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizeTest {
    @Test
    fun stripsUnsafeCharacters() {
        assertEquals("Sky_Runner", ProjectStore.sanitizeName("Sky Runner!"))
        assertEquals("Demo", ProjectStore.sanitizeName("  Demo  "))
        assertEquals("", ProjectStore.sanitizeName("@@@"))
        assertEquals("Лес", ProjectStore.sanitizeName("Лес"))
        assertEquals("Сборщик_звёзд", ProjectStore.sanitizeName("Сборщик звёзд!"))
    }
}
