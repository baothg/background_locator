package com.thgbao.background_locator

internal fun wakeLockTimeoutMillis(minutes: Int): Long = minutes.coerceAtLeast(1).toLong() * 60_000L

internal fun canDestroyBackgroundEngine(serviceCreated: Boolean, serviceRunning: Boolean, pendingCalls: Int): Boolean =
    !serviceCreated && !serviceRunning && pendingCalls == 0

internal class BackgroundCallState {
    private var completed = false
    private var timeoutReported = false

    @Synchronized
    fun complete(): Boolean {
        if (completed) return false
        completed = true
        return true
    }

    @Synchronized
    fun reportTimeout(): Boolean {
        if (completed || timeoutReported) return false
        timeoutReported = true
        return true
    }
}

internal class RestoreRetryBackoff(private val maxAttempts: Int = 10) {
    private var delay = 1000L
    private var attempts = 0

    fun hasNext(): Boolean = attempts < maxAttempts

    fun nextDelay(): Long {
        attempts++
        val current = delay
        delay = (delay * 2).coerceAtMost(60000L)
        return current
    }

    fun reset() {
        delay = 1000L
        attempts = 0
    }
}
