@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.dvitski.tabyproto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun anim(id: String) = Animation.Once(id, id, RawAnimation.LISTENING_MUSIC_LOOP)

private fun fakeDeviceInfo() = DeviceInfo(
    firmwareVersion = "1.0", assetsVersion = null, deviceId = "test",
    state = DeviceState.UNKNOWN, wifiMode = WifiMode.UNKNOWN, ip = null,
    claimed = null, claimedBy = null, usbMode = null, bootCount = null,
    brightnessPercent = null, freeHeapBytes = null, preferredTransport = null,
    transportOnboardingComplete = null, mdnsHost = null, stationSsid = null,
    wifiError = null, bluetoothReady = null, bluetoothConnected = null,
    bluetoothName = null, powerVoltageMv = null, batteryPercent = null,
    externalPower = null,
)

private class RecordingSession : TabySession {
    val played = mutableListOf<String>()
    override val transport = TabyTransport.USB
    override val device = fakeDeviceInfo()
    override suspend fun play(animation: Animation) = sendRaw(animation.id)
    override suspend fun play(raw: RawAnimation) = sendRaw(raw.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())
    override suspend fun setBrightness(percent: Int) = sendRaw("BRIGHTNESS $percent")
    override suspend fun sendRaw(command: String): CommandResult {
        played += command
        return CommandResult(true, transport, command, "TABY:OK", "fake")
    }
    override suspend fun readInfo() = device
    override suspend fun readTouchSignal() = 0
    override suspend fun readChoiceSignal() = ChoiceSignal(0, ChoiceSelection.NONE)
    override fun close() {}
}

/** Scope + mutable clock for fully synchronous, time-controlled tests. */
private fun makeCtrl(session: RecordingSession, clock: () -> Long = { 0L }): Pair<AnimationController, CoroutineScope> {
    val scope = CoroutineScope(UnconfinedTestDispatcher())
    return AnimationController(session, scope, clock) to scope
}

class AnimationControllerTest {

    @Test
    fun `request plays immediately when nothing active`() = runTest {
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session)
        val played = c.request(AnimationPriority.MANUAL, anim("a"), 2_000L)
        assertTrue(played)
        assertEquals(listOf("a"), session.played)
        scope.cancel()
    }

    @Test
    fun `higher priority preempts with sufficient remaining time`() = runTest {
        var t = 0L
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session) { t }
        c.request(AnimationPriority.MUSIC, anim("music"), 2_000L)  // startedAt=0, duration=2000
        t = 100  // 1900ms remaining → well above 500ms threshold
        val played = c.request(AnimationPriority.MANUAL, anim("manual"), 1_000L)
        assertTrue(played)
        assertEquals(listOf("music", "manual"), session.played)
        scope.cancel()
    }

    @Test
    fun `higher priority waits when remaining time is 500ms or less`() = runTest {
        var t = 0L
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session) { t }
        c.request(AnimationPriority.MUSIC, anim("music"), 2_000L)  // startedAt=0
        t = 1_600  // remaining = 2000 - 1600 = 400ms ≤ 500ms → queued
        val played = c.request(AnimationPriority.MANUAL, anim("manual"))
        assertFalse(played)
        assertEquals(listOf("music"), session.played)
        // Simulate MUSIC finishing (stop acts like expiry calling advancePending)
        c.stop(AnimationPriority.MUSIC)
        advanceUntilIdle()
        assertEquals(listOf("music", "manual"), session.played)
        scope.cancel()
    }

    @Test
    fun `lower priority is queued and plays after stop`() = runTest {
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session)
        c.request(AnimationPriority.MANUAL, anim("manual"))        // loops — no duration
        val played = c.request(AnimationPriority.MUSIC, anim("music"), 2_000L)
        assertFalse(played)
        assertEquals(listOf("manual"), session.played)
        c.stop(AnimationPriority.MANUAL)
        advanceUntilIdle()
        assertEquals(listOf("manual", "music"), session.played)
        scope.cancel()
    }

    @Test
    fun `stop triggers next pending`() = runTest {
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session)
        c.request(AnimationPriority.MANUAL, anim("manual"))
        c.request(AnimationPriority.MUSIC,  anim("music"))
        c.stop(AnimationPriority.MANUAL)
        advanceUntilIdle()
        assertEquals(listOf("manual", "music"), session.played)
        scope.cancel()
    }

    @Test
    fun `cancel removes pending request`() = runTest {
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session)
        c.request(AnimationPriority.MANUAL, anim("manual"))
        c.request(AnimationPriority.MUSIC, anim("music"))
        c.cancel(AnimationPriority.MUSIC)
        c.stop(AnimationPriority.MANUAL)
        advanceUntilIdle()
        assertEquals(listOf("manual"), session.played)
        scope.cancel()
    }

    @Test
    fun `highest priority pending plays first`() = runTest {
        val session = RecordingSession()
        val (c, scope) = makeCtrl(session)
        c.request(AnimationPriority.MANUAL, anim("manual"))
        c.request(AnimationPriority.IDLE,   anim("idle"))
        c.request(AnimationPriority.MUSIC,  anim("music"))
        c.stop(AnimationPriority.MANUAL)
        advanceUntilIdle()
        assertEquals(listOf("manual", "music"), session.played) // MUSIC(50) beats IDLE(10)
        scope.cancel()
    }
}
