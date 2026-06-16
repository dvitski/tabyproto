package cc.dvitski.tabyproto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

interface TabySession : Closeable {
    val transport: TabyTransport
    val device: DeviceInfo

    suspend fun play(animation: Animation): CommandResult
    suspend fun play(raw: RawAnimation): CommandResult
    suspend fun play(command: AnimationCommand): CommandResult
    suspend fun setBrightness(percent: Int): CommandResult
    suspend fun sendRaw(command: String): CommandResult
    suspend fun reboot(): CommandResult
    suspend fun readInfo(): DeviceInfo
    suspend fun readTouchSignal(): Int
    suspend fun readChoiceSignal(): ChoiceSignal

    override fun close()
}

internal class UsbTabySession(
    private val usbSession: TabyUsbClient.TabyUsbSession,
    override val device: DeviceInfo,
) : TabySession {
    override val transport = TabyTransport.USB
    private val mutex = Mutex()

    override suspend fun play(animation: Animation): CommandResult = when (animation) {
        is Animation.Once    -> play(animation.raw)
        is Animation.Looping -> when (val intro = animation.intro) {
            null -> play(animation.body)
            else -> play(AnimationCommand(intro, animation.body))
        }
    }

    override suspend fun play(raw: RawAnimation) = sendRaw(raw.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())

    override suspend fun setBrightness(percent: Int): CommandResult {
        require(percent in 0..100) { "Brightness must be 0–100, got $percent" }
        return sendRaw("BRIGHTNESS $percent")
    }

    override suspend fun sendRaw(command: String): CommandResult =
        mutex.withLock { withContext(Dispatchers.IO) { usbSession.sendCommand(command) } }

    override suspend fun reboot(): CommandResult =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                usbSession.hardReboot()
                CommandResult(ok = true, TabyTransport.USB, "REBOOT", "ok", "Reboot sent")
            }
        }

    override suspend fun readInfo(): DeviceInfo =
        mutex.withLock { withContext(Dispatchers.IO) { usbSession.readInfoDirect() } }

    override suspend fun readTouchSignal(): Int =
        mutex.withLock { withContext(Dispatchers.IO) { usbSession.readTouchSignalDirect() } }

    override suspend fun readChoiceSignal(): ChoiceSignal =
        mutex.withLock { withContext(Dispatchers.IO) { usbSession.readChoiceSignalDirect() } }

    override fun close() = usbSession.close()
}

internal class WifiTabySession(
    private val wifiClient: TabyWifiClient,
    override val device: DeviceInfo,
) : TabySession {
    override val transport = TabyTransport.WIFI

    override suspend fun play(animation: Animation): CommandResult = when (animation) {
        is Animation.Once    -> play(animation.raw)
        is Animation.Looping -> when (val intro = animation.intro) {
            null -> play(animation.body)
            else -> play(AnimationCommand(intro, animation.body))
        }
    }

    override suspend fun play(raw: RawAnimation) = sendRaw(raw.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())

    override suspend fun setBrightness(percent: Int): CommandResult {
        require(percent in 0..100) { "Brightness must be 0–100, got $percent" }
        return sendRaw("BRIGHTNESS $percent")
    }

    override suspend fun sendRaw(command: String) = wifiClient.sendCommand(command)

    override suspend fun reboot(): CommandResult = try {
        wifiClient.sendCommand("REBOOT")
    } catch (_: Exception) {
        CommandResult(ok = true, TabyTransport.WIFI, "REBOOT", "ok", "Reboot sent")
    }

    override suspend fun readInfo(): DeviceInfo = wifiClient.readHealth()
    override suspend fun readTouchSignal(): Int = wifiClient.readTouchSignal()
    override suspend fun readChoiceSignal(): ChoiceSignal = wifiClient.readChoiceSignal()

    override fun close() {}
}
