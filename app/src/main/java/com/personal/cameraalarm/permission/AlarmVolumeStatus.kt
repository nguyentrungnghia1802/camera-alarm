package com.personal.cameraalarm.permission

data class AlarmVolumeStatus(val current: Int, val minimum: Int, val maximum: Int) {
    init { require(minimum >= 0 && maximum >= minimum && current >= 0) }
    val isNonZero get() = current > 0
    val canReachZero get() = minimum == 0
    val warning get() = if (current == 0) "Alarm volume is zero" else null
}
