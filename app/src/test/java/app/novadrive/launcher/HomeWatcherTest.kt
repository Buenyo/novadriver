package app.novadrive.launcher

import app.novadrive.launcher.system.HomeWatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWatcherTest {
    private val oem = setOf("oem.launcher")

    @Test fun bouncesOnlyOemLauncher() {
        assertTrue(HomeWatcher.shouldBounce("oem.launcher", oem, enabled = true, now = 10_000, lastJump = 0))
        assertFalse(HomeWatcher.shouldBounce("com.spotify.music", oem, enabled = true, now = 10_000, lastJump = 0))
    }

    @Test fun respectsToggle() {
        assertFalse(HomeWatcher.shouldBounce("oem.launcher", oem, enabled = false, now = 10_000, lastJump = 0))
    }

    @Test fun debounces500ms() {
        assertFalse(HomeWatcher.shouldBounce("oem.launcher", oem, enabled = true, now = 10_499, lastJump = 10_000))
        assertTrue(HomeWatcher.shouldBounce("oem.launcher", oem, enabled = true, now = 10_500, lastJump = 10_000))
    }

    @Test fun explicitOpenIsKeptUntilAnotherAppComesToFront() {
        HomeWatcher.allow("oem.launcher")
        assertFalse(HomeWatcher.onWindow("oem.launcher", oem, enabled = true, now = 10_000, lastJump = 0))
        assertFalse(HomeWatcher.onWindow("oem.launcher", oem, enabled = true, now = 20_000, lastJump = 0))
        assertFalse(HomeWatcher.onWindow("com.spotify.music", oem, enabled = true, now = 30_000, lastJump = 0))
        assertTrue(HomeWatcher.onWindow("oem.launcher", oem, enabled = true, now = 40_000, lastJump = 0))
    }
}
