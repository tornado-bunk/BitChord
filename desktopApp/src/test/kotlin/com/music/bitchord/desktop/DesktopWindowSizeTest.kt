package com.music.bitchord.desktop

import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowSizeTest {
    @Test
    fun retinaWorkAreaUsesLogicalDimensions() {
        assertEquals(Dimension(1220, 780), fitDesktopWindowSize(1512, 900))
    }

    @Test
    fun smallWorkAreaKeepsAllEdgesOnScreen() {
        assertEquals(Dimension(976, 652), fitDesktopWindowSize(1024, 700))
    }

    @Test
    fun minimumSizeAlsoFitsSmallDisplays() {
        assertEquals(Dimension(752, 452), fitDesktopWindowSize(800, 500, Dimension(900, 600)))
    }
}
