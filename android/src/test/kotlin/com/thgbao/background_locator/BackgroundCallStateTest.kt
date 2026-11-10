package com.thgbao.background_locator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.thgbao.background_locator.provider.supportedAndroidProviders

class BackgroundCallStateTest {
    @Test
    fun completedCallbackDoesNotReportTimeout() {
        val state = BackgroundCallState()
        assertTrue(state.complete())
        assertFalse(state.reportTimeout())
        assertFalse(state.complete())
    }

    @Test
    fun timeoutDoesNotCompleteRunningCallback() {
        val state = BackgroundCallState()
        var pending = 1
        assertTrue(state.reportTimeout())
        assertFalse(state.reportTimeout())
        assertEquals(1, pending)
        if (state.complete()) pending--
        assertEquals(0, pending)
        assertFalse(state.complete())
    }

    @Test
    fun commandCanOnlyReplyOnceWhetherAckOrDeadlineWins() {
        for (timeoutFirst in listOf(true, false)) {
            val state = BackgroundCallState()
            val events = if (timeoutFirst) listOf("deadline", "ack") else listOf("ack", "deadline")
            val replies = events.filter { state.complete() }
            assertEquals(listOf(events.first()), replies)
        }
    }

    @Test
    fun engineCannotBeDestroyedWhileServiceIsStarting() {
        assertFalse(canDestroyBackgroundEngine(true, false, 0))
        assertFalse(canDestroyBackgroundEngine(true, true, 0))
        assertFalse(canDestroyBackgroundEngine(false, false, 1))
        assertTrue(canDestroyBackgroundEngine(false, false, 0))
    }

    @Test
    fun androidProviderSelectionHandlesMissingNetworkProvider() {
        assertEquals(listOf("gps"), supportedAndroidProviders(listOf("gps", "passive")))
        assertEquals(listOf("network"), supportedAndroidProviders(listOf("network")))
        assertEquals(listOf("gps", "network"), supportedAndroidProviders(listOf("network", "gps", "fused")))
        assertTrue(supportedAndroidProviders(listOf("passive")).isEmpty())
    }

    @Test
    fun wakeLockTimeoutUsesLongArithmetic() {
        assertEquals(60000L, wakeLockTimeoutMillis(1))
        assertEquals(3600000L, wakeLockTimeoutMillis(60))
        assertEquals(128849018820000L, wakeLockTimeoutMillis(Int.MAX_VALUE))
    }

    @Test
    fun restoreBackoffIsExponentialAndCapped() {
        val backoff = RestoreRetryBackoff()
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L),
            List(7) { backoff.nextDelay() })
        repeat(100) { assertEquals(60000L, backoff.nextDelay()) }
        backoff.reset()
        assertEquals(1000L, backoff.nextDelay())
    }

    @Test
    fun restoreRetriesStopAfterMaxAttempts() {
        val backoff = RestoreRetryBackoff()
        repeat(10) {
            assertTrue(backoff.hasNext())
            backoff.nextDelay()
        }
        assertFalse(backoff.hasNext())
        backoff.reset()
        assertTrue(backoff.hasNext())
    }
}
