package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import org.junit.Assert.*
import org.junit.Test

class AlarmRuntimeControllerTest {
    private class Player(var fail: Boolean = false) : AlarmPlayer {
        var starts = 0; var stops = 0
        override val isPlaying get() = starts > stops
        override fun start(): Result<Unit> { starts++; return if (fail) Result.failure(IllegalStateException("audio unavailable")) else Result.success(Unit) }
        override fun stop() { stops++ }
    }
    private class Vibration(var fail: Boolean = false) : VibrationController {
        var starts = 0; var stops = 0
        override fun startRepeating() { starts++; if (fail) error("no vibrator") }
        override fun stop() { stops++ }
    }
    @Test fun repeatedAndDifferentStartNeverDoubleStarts() {
        val player = Player(); val vibration = Vibration(); val controller = AlarmRuntimeController(player, vibration)
        controller.start(AlarmToken("a"), true)
        controller.start(AlarmToken("a"), true)
        controller.start(AlarmToken("b"), true)
        assertEquals(1, player.starts); assertEquals(1, vibration.starts)
        assertEquals(AlarmToken("a"), controller.activeToken)
    }
    @Test fun repeatedStopCleansOnlyOnceAndStaleStopIgnored() {
        val player = Player(); val vibration = Vibration(); val controller = AlarmRuntimeController(player, vibration)
        controller.start(AlarmToken("a"), true)
        controller.stop(AlarmToken("b")); assertEquals(0, player.stops)
        controller.stop(AlarmToken("a")); controller.stop(AlarmToken("a"))
        assertEquals(1, player.stops); assertEquals(1, vibration.stops); assertNull(controller.activeToken)
    }
    @Test fun independentAudioAndVibrationFailures() {
        val player = Player(fail = true); val vibration = Vibration(); val controller = AlarmRuntimeController(player, vibration)
        val errors = controller.start(AlarmToken("a"), true)
        assertEquals(1, errors.size); assertEquals(1, vibration.starts)
        controller.stop(null); assertEquals(1, vibration.stops)
        val player2 = Player(); val vibration2 = Vibration(fail = true)
        val errors2 = AlarmRuntimeController(player2, vibration2).start(AlarmToken("b"), true)
        assertEquals(1, errors2.size); assertEquals(1, player2.starts)
    }
}
