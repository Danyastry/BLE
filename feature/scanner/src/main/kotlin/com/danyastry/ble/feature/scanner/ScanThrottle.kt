package com.danyastry.ble.feature.scanner

class ScanThrottle(private val maxStarts: Int = MAX_STARTS, private val windowMillis: Long = WINDOW_MILLIS) {

    private val starts = ArrayDeque<Long>()

    fun waitMillis(nowMillis: Long): Long {
        dropOld(nowMillis)
        return if (starts.size < maxStarts) 0 else starts.first() + windowMillis - nowMillis
    }

    fun tryStart(nowMillis: Long): Boolean {
        if (waitMillis(nowMillis) > 0) return false
        starts.addLast(nowMillis)
        return true
    }

    private fun dropOld(nowMillis: Long) {
        while (starts.isNotEmpty() && nowMillis - starts.first() >= windowMillis) starts.removeFirst()
    }

    companion object {
        const val MAX_STARTS = 5
        const val WINDOW_MILLIS = 30_000L
    }
}
