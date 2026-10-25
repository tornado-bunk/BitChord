package com.music.bitchord.desktop

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowVisibilityTest {
    @Test
    fun closeHidesWindowAndShowRestoresItWhenKeptRunning() {
        val previous = DesktopWindowVisibility.keepRunningWhenClosed
        try {
            DesktopWindowVisibility.keepRunningWhenClosed = true
            DesktopWindowVisibility.show()
            assertFalse(DesktopWindowVisibility.onCloseRequest())
            assertFalse(DesktopWindowVisibility.visible.value)
            DesktopWindowVisibility.show()
            assertTrue(DesktopWindowVisibility.visible.value)
        } finally {
            DesktopWindowVisibility.keepRunningWhenClosed = previous
            DesktopWindowVisibility.show()
        }
    }

    @Test
    fun closeWithoutTrayKeepsRunningOnlyOnMac() {
        val previous = DesktopWindowVisibility.keepRunningWhenClosed
        try {
            DesktopWindowVisibility.keepRunningWhenClosed = false
            DesktopWindowVisibility.show()
            assertEquals(!DesktopPlatform.isMac, DesktopWindowVisibility.onCloseRequest())
            assertEquals(!DesktopPlatform.isMac, DesktopWindowVisibility.visible.value)
        } finally {
            DesktopWindowVisibility.keepRunningWhenClosed = previous
            DesktopWindowVisibility.show()
        }
    }
}
