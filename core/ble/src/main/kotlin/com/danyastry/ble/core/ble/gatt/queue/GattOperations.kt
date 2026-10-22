package com.danyastry.ble.core.ble.gatt.queue

import com.danyastry.ble.core.ble.gatt.format.GattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class GattOperations(
    private val transport: GattTransport,
    private val clock: () -> Long,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val onRecord: (OperationRecord) -> Unit = {},
) {

    private class Pending(val request: GattRequest, val response: CompletableDeferred<GattResponse>)

    private val mutex = Mutex()

    @Volatile
    private var pending: Pending? = null

    @Volatile
    private var isConnected = true

    suspend fun execute(request: GattRequest, timeoutMillis: Long = this.timeoutMillis): GattResponse {
        val queuedAt = clock()
        return mutex.withLock {
            val startedAt = clock()
            var outcome: OperationOutcome = OperationOutcome.Success
            try {
                if (!isConnected) throw GattException.Disconnected(request)
                val response = CompletableDeferred<GattResponse>()
                pending = Pending(request, response)
                if (!runCatching { transport.start(request) }.getOrDefault(false)) throw GattException.NotStarted(request)
                val answer = try {
                    withTimeout(timeoutMillis) { response.await() }
                } catch (timeout: TimeoutCancellationException) {
                    throw GattException.TimedOut(request)
                }
                if (answer.status != GattStatus.SUCCESS) throw GattException.Failed(request, answer.status)
                answer
            } catch (failure: GattException) {
                outcome = failure.outcome
                throw failure
            } finally {
                pending = null
                onRecord(OperationRecord(request, queuedAt, startedAt, clock(), outcome))
            }
        }
    }

    suspend fun executeWithoutAnswer(request: GattRequest) {
        val queuedAt = clock()
        mutex.withLock {
            val startedAt = clock()
            var outcome: OperationOutcome = OperationOutcome.Success
            try {
                if (!isConnected) throw GattException.Disconnected(request)
                if (!runCatching { transport.start(request) }.getOrDefault(false)) throw GattException.NotStarted(request)
            } catch (failure: GattException) {
                outcome = failure.outcome
                throw failure
            } finally {
                onRecord(OperationRecord(request, queuedAt, startedAt, clock(), outcome))
            }
        }
    }

    fun onResponse(response: GattResponse) {
        val current = pending ?: return
        if (response.answers(current.request)) current.response.complete(response)
    }

    fun onDisconnected() {
        isConnected = false
        pending?.let { it.response.completeExceptionally(GattException.Disconnected(it.request)) }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
