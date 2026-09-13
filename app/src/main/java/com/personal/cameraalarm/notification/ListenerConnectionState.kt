package com.personal.cameraalarm.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListenerStatus { DISCONNECTED, CONNECTED }
class ListenerConnectionState {
    private val mutableStatus = MutableStateFlow(ListenerStatus.DISCONNECTED)
    val status = mutableStatus.asStateFlow()
    fun connected() { mutableStatus.value = ListenerStatus.CONNECTED }
    fun disconnected() { mutableStatus.value = ListenerStatus.DISCONNECTED }
}
