package cc.dvitski.tabyproto

import kotlinx.coroutines.Dispatchers
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

    override fun close()
}

internal class UsbTabySession(
    private val usbSession: TabyUsbClient.TabyUsbSession,
    override val device: DeviceInfo,
) : TabySession {
    override val transport = TabyTransport.USB

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
        withContext(Dispatchers.IO) { usbSession.sendCommand(command) }

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

    override fun close() {}
}
