package com.personal.cameraalarm.alarm

import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AlarmStateStore {
    suspend fun read(): AlarmState
    suspend fun write(state: AlarmState)
}
class InMemoryAlarmStateStore : AlarmStateStore {
    private var stored: AlarmState = AlarmState.Idle
    override suspend fun read() = stored
    override suspend fun write(state: AlarmState) { stored = state }
}
fun interface AlarmEffectObserver { suspend fun onEffect(effect: AlarmEffect) }

class AlarmCoordinator(
    private val clock: Clock,
    private val scheduler: AlarmScheduler,
    private val store: AlarmStateStore,
    private val policy: () -> AlarmPolicy,
    private val observer: AlarmEffectObserver = AlarmEffectObserver { }
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<AlarmState>(AlarmState.Idle)
    val state = mutableState.asStateFlow()

    suspend fun onValidTrigger(trigger: TriggerSnapshot) = dispatch(AlarmEvent.ValidTrigger(trigger))
    suspend fun onExactAlarmFired(trigger: TriggerSnapshot) = dispatch(AlarmEvent.ExactAlarmFired(trigger))
    suspend fun onStopRequested(token: AlarmToken?) = dispatch(AlarmEvent.StopRequested(token))

    private suspend fun dispatch(event: AlarmEvent) = mutex.withLock {
        val current = store.read()
        val transition = AlarmReducer.reduce(current, event, clock.nowEpochMs(), policy())
        if (transition.nextState != current) {
            store.write(transition.nextState)
            mutableState.value = transition.nextState
        }
        for (effect in transition.effects) when (effect) {
            is AlarmEffect.ScheduleExact -> {
                val result = scheduler.scheduleExact(effect.trigger.alarmToken, effect.triggerAtEpochMs)
                if (result != ScheduleResult.Scheduled) {
                    val failed = AlarmReducer.reduce(transition.nextState, AlarmEvent.ScheduleFailed(effect.trigger.alarmToken), clock.nowEpochMs(), policy())
                    store.write(failed.nextState)
                    mutableState.value = failed.nextState
                    failed.effects.forEach { observer.onEffect(it) }
                    observer.onEffect(AlarmEffect.RecordFailure(result.toString()))
                } else observer.onEffect(effect)
            }
            is AlarmEffect.CancelExact -> { scheduler.cancel(effect.alarmToken); observer.onEffect(effect) }
            else -> observer.onEffect(effect)
        }
    }
}
