package com.personal.cameraalarm.alarm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface BootMarker { fun current(): Int }

/** Rejects state from a prior device boot before receiver hydration. */
class BootScopedAlarmStateStore(private val delegate: AlarmStateStore, private val marker: BootMarker, private val markerStore: BootMarkerStore) : AlarmStateStore {
    private val mutex = Mutex()
    override suspend fun read(): AlarmState = mutex.withLock {
        if (markerStore.read() != marker.current()) {
            delegate.write(AlarmState.Idle)
            markerStore.write(marker.current())
        }
        delegate.read()
    }
    override suspend fun write(state: AlarmState) = mutex.withLock {
        markerStore.write(marker.current())
        delegate.write(state)
    }
}
interface BootMarkerStore { suspend fun read(): Int?; suspend fun write(marker: Int) }
