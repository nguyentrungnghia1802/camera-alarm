package com.personal.cameraalarm.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RuntimeDiagnostics {
    private val mutableLastError = MutableStateFlow<String?>(null)
    val lastError = mutableLastError.asStateFlow()
    fun record(error: String) { mutableLastError.value = error }
}
