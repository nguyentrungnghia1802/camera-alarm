package com.personal.cameraalarm.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListenerStatus { DISCONNECTED, RECONNECTING, CONNECTED }
class ListenerConnectionState {
    private val mutableStatus = MutableStateFlow(ListenerStatus.DISCONNECTED)
    val status = mutableStatus.asStateFlow()
    fun connected() { mutableStatus.value = ListenerStatus.CONNECTED }
    fun reconnecting() { mutableStatus.value = ListenerStatus.RECONNECTING }
    fun disconnected() { mutableStatus.value = ListenerStatus.DISCONNECTED }
}
