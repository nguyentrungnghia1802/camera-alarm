package com.personal.cameraalarm.util

fun interface Clock { fun nowEpochMs(): Long }
object AndroidClock : Clock { override fun nowEpochMs(): Long = System.currentTimeMillis() }
