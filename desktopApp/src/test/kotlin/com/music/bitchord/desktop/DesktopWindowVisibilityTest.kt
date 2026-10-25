package com.music.bitchord.desktop

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowVisibilityTest {

    @Test
    fun showRestoresVisibilityAndInvokesCallback() {
        var restored = false
        DesktopWindowVisibility.onRestoreWindow = { restored = true }
        try {
            DesktopWindowVisibility.show()
            assertTrue(DesktopWindowVisibility.visible.value)
            assertTrue(restored)
        } finally {
            DesktopWindowVisibility.onRestoreWindow = null
        }
    }

    @Test
    fun onCloseRequestHidesWindowWhenKeepRunningOrOnMac() {
        DesktopWindowVisibility.show()
        val shouldQuit = DesktopWindowVisibility.onCloseRequest()
        // On macOS, closing hides the window and keeps running
        if (DesktopPlatform.isMac) {
            assertFalse(shouldQuit)
            assertFalse(DesktopWindowVisibility.visible.value)
        }
        DesktopWindowVisibility.show()
    }
}
