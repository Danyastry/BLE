package com.danyastry.ble.feature.peripheral.logic

class NotificationQueue<K, T> {

    private val waiting = mutableMapOf<K, ArrayDeque<T>>()
    private val inFlight = mutableSetOf<K>()

    @Synchronized
    fun enqueue(key: K, item: T): T? {
        if (key !in inFlight) {
            inFlight += key
            return item
        }
        val queue = waiting.getOrPut(key) { ArrayDeque() }
        if (item !in queue) queue.addLast(item)
        return null
    }

    @Synchronized
    fun sent(key: K): T? {
        val next = waiting[key]?.removeFirstOrNull()
        if (next == null) inFlight -= key
        return next
    }

    @Synchronized
    fun clear(key: K) {
        waiting.remove(key)
        inFlight -= key
    }

    @Synchronized
    fun waitingFor(key: K): Int = waiting[key]?.size ?: 0
}
