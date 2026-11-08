package com.noor.wear

import androidx.test.core.app.ApplicationProvider
import com.noor.prayer.WatchFaces
import com.noor.wear.facepush.AttemptsUsedException
import com.noor.wear.facepush.FacePush
import com.noor.wear.facepush.FaceSlots
import com.noor.wear.facepush.FaceState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 0.9.8: the watch face on the watch, against a stand-in for Wear OS's Watch Face Push.
 * - Choosing another design or colour replaces the face in its slot (an update, never a second face, never a removal),
 *   so it stays the active face and the change shows at once.
 * - The app asks Wear OS to make its face active at most once; when the system says the one chance was used (the
 *   0.9.7 error, "maximum number of attempts"), the state says "choose it on the watch" instead of an error.
 * - A face from an older build (0.9.7's, build 1) is replaced with this build's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FacePushTest {
    private class FakeSlots(var slot: FaceSlots.Slot? = null, var active: Boolean = false, val refuse: Boolean = false) : FaceSlots {
        val calls = mutableListOf<String>()
        var lastApk = ""
        override suspend fun find() = slot
        override suspend fun add(apk: File, token: String): String {
            calls += "add:$token"; lastApk = apk.readText(); slot = FaceSlots.Slot("s1", 2, null); return "s1"
        }
        override suspend fun update(slot: String, apk: File, token: String) {
            calls += "update:$slot:$token"; lastApk = apk.readText(); this.slot = FaceSlots.Slot(slot, 2, null)
        }
        override suspend fun isActive() = active
        override suspend fun setActive(slot: String) {
            calls += "setActive"
            if (refuse) throw AttemptsUsedException(null)
            active = true
        }
    }

    private val ids = WatchFaces.ids
    private val catalogue = FacePush.Catalogue(ids, ids.map { "token-$it" }, 2) { id -> "apk of $id".byteInputStream() }
    private fun push(fake: FakeSlots) = FacePush(ApplicationProvider.getApplicationContext(), fake, catalogue)

    @Test fun addThenSwitchInPlace() = runBlocking {
        val fake = FakeSlots()
        val p = push(fake)
        var st = p.state() as FaceState.Ready
        assertFalse(st.installed); assertEquals("horizon_gold", st.chosen)
        st = p.installOrUpdate() as FaceState.Ready
        assertEquals(listOf("add:token-horizon_gold"), fake.calls); assertEquals("apk of horizon_gold", fake.lastApk)
        assertTrue(st.installed); assertEquals("horizon_gold", st.installedVariant); assertFalse(st.updateAvailable)
        // The user is using it; the phone asks for the dial in rose.
        fake.active = true
        st = p.choose("dial_rose") as FaceState.Ready
        assertEquals("update:s1:token-dial_rose", fake.calls.last()); assertEquals(1, fake.calls.count { it.startsWith("add") })
        assertEquals("dial_rose", st.installedVariant); assertTrue(st.active); assertFalse(st.updateAvailable)
        assertEquals("apk of dial_rose", fake.lastApk)
        // An unknown id changes nothing.
        p.choose("dial_blue"); assertEquals("dial_rose", p.chosen)
    }

    @Test fun activatesOnceAndNeverShowsTheLimitAsAnError() = runBlocking {
        // 0.9.7 already used the one chance: the system refuses.
        val fake = FakeSlots(FaceSlots.Slot("s1", 2, "horizon_gold"), active = false, refuse = true)
        val p = push(fake)
        var st = p.activate()
        assertTrue("$st", st is FaceState.Ready && st.needsPick)
        assertEquals(1, fake.calls.count { it == "setActive" })
        // Asking again (the screen opened from the phone again) does not try again.
        st = p.activate()
        assertTrue(st is FaceState.Ready && st.needsPick); assertEquals(1, fake.calls.count { it == "setActive" })
        // Once the user picks Noor on the watch, it simply shows as in use.
        fake.active = true
        st = p.state() as FaceState.Ready
        assertTrue(st.active); assertFalse(st.needsPick)
    }

    @Test fun firstActivationWorks() = runBlocking {
        val fake = FakeSlots(FaceSlots.Slot("s1", 2, "minimal_silver"))
        val p = push(fake)
        p.choose("minimal_silver")
        val st = p.activate() as FaceState.Ready
        assertTrue(st.active); assertEquals(1, fake.calls.count { it == "setActive" })
    }

    @Test fun anOlderBuildIsReplaced() = runBlocking {
        // 0.9.7's face: build 1, the original design, no variant reported.
        val fake = FakeSlots(FaceSlots.Slot("old", 1, null), active = true)
        val p = push(fake)
        var st = p.state() as FaceState.Ready
        assertEquals(WatchFaces.ORIGINAL_ID, st.installedVariant); assertTrue(st.updateAvailable)
        st = p.installOrUpdate() as FaceState.Ready
        assertEquals("update:old:token-horizon_gold", fake.calls.single())
        assertFalse(st.updateAvailable); assertTrue(st.active)
    }

    @Test fun notBundledOrUnsupported() = runBlocking {
        val none = FacePush(ApplicationProvider.getApplicationContext(), FakeSlots(), FacePush.Catalogue(emptyList(), emptyList(), 0) { null })
        assertEquals(FaceState.NotBundled, none.state())
        val old = FacePush(ApplicationProvider.getApplicationContext(), null, catalogue)
        assertEquals(FaceState.Unsupported, old.state())
    }
}
