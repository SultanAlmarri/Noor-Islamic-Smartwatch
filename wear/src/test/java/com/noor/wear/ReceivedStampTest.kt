package com.noor.wear

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 0.22: "Updated 3 days ago" means the phone last sent its settings three days ago. Reading the same item again from
 * the Data Layer (each time the app opens) used to stamp it as received at that moment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceivedStampTest {

    @Test fun readingTheSameSettingsAgainIsNotANewArrival() = runBlocking {
        val repo = (RuntimeEnvironment.getApplication() as NoorWearApp).config
        val sent = TestConfigs.dubai().copy(updatedAtEpochMs = 1_000L).encode()
        repo.save(sent)
        val first = repo.receivedAt.first()!!
        Thread.sleep(20)
        assertFalse(repo.save(sent))
        assertEquals("the same item read again keeps its time of arrival", first, repo.receivedAt.first())

        // The phone sends again (Sync now, a new day): a new arrival, even with nothing else changed.
        Thread.sleep(20)
        repo.save(TestConfigs.dubai().copy(updatedAtEpochMs = 2_000L).encode())
        assertNotEquals(first, repo.receivedAt.first())
    }
}
