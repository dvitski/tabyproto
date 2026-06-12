package cc.dvitski.tabyproto

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private class FoundTabyException(val port: TabyUsbPort) : Exception()

private const val EXCHANGE_TIMEOUT_MS = 6_000L
private const val SETTLE_TIMEOUT_MS   = 3_000L  // max wait for device to finish booting after port open
private const val SETTLE_QUIET_MS     = 300L     // silence duration that indicates boot is done
private const val PROBE_TIMEOUT_MS    = 12_000L  // settle + exchange + margin
private const val INFO_ATTEMPTS = 3
private const val INFO_RETRY_DELAY_MS = 300L
private const val READ_POLL_INTERVAL_MS = 10L

/**
 * Communicates with a Taby device over USB serial.
 *
 * The device uses a line-based ASCII protocol at 115200 baud:
 *  - Send: `<COMMAND>\n`
 *  - Receive: lines starting with `TABY:` are protocol responses;
 *             everything else is debug/log output and can be ignored.
 *
 * Usage:
 * ```kotlin
 * val client = TabyUsbClient()
 * val ports = client.listTabyPorts()
 * val info = client.readInfo(ports.first().systemPortName)
 * client.sendCommand(ports.first().systemPortName, "ANIM idle_01_loop")
 * ```
 */
internal class TabyUsbClient {
    private val logger: Logger = LoggerFactory.getLogger(TabyUsbClient::class.java)

    // ── Port discovery ─────────────────────────────────────────────────────────

    /**
     * Probes all serial ports in parallel and returns the first one that responds
     * as a Taby. When any probe succeeds it throws [FoundTabyException], which
     * propagates out of [coroutineScope] and cancels every other probe job — so
     * a slow Bluetooth COM port can't delay the result.
     */
    suspend fun findFirstTabyPort(): TabyUsbPort? = try {
        coroutineScope {
            listCandidatePorts().forEach { serialPort ->
                launch {
                    logger.debug("probing ${serialPort.systemPortName} — ${serialPort.descriptivePortName}")
                    val result = withTimeoutOrNull(PROBE_TIMEOUT_MS.milliseconds) {
                        try {
                            val info = readInfo(serialPort.systemPortName)
                            TabyUsbPort(serialPort.systemPortName, serialPort.descriptivePortName, info)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logger.debug("  ${serialPort.systemPortName} failed: ${e::class.simpleName}: ${e.message}")
                            null
                        }
                    }
                    if (result != null) throw FoundTabyException(result)
                    logger.debug("  ${serialPort.systemPortName} → no response")
                }
            }
        }
        null  // all probes finished with no result
    } catch (e: FoundTabyException) {
        e.port
    }


    /** Returns all serial ports without probing, excluding Bluetooth virtual COM ports. */
    fun listCandidatePorts(): List<SerialPort> =
        SerialPort.getCommPorts()
            .filter { !it.descriptivePortName.contains("Bluetooth", ignoreCase = true) }
            .toList()

    // ── High-level commands ────────────────────────────────────────────────────

    /**
     * Reads device info. Retries up to [INFO_ATTEMPTS] times.
     * Sends `INFO\n`, expects `TABY:INFO <json>`.
     */
    suspend fun readInfo(portName: String): DeviceInfo = withContext(Dispatchers.IO) {
        // Open once and reuse across retries — reopening each time would reset the device again.
        val port = openAndSettle(portName)
        try {
            var lastError: Exception? = null
            repeat(INFO_ATTEMPTS) { attempt ->
                if (attempt > 0) Thread.sleep(INFO_RETRY_DELAY_MS)
                try {
                    logger.debug("  $portName INFO attempt ${attempt + 1}/$INFO_ATTEMPTS")
                    val bytes = "INFO\n".toByteArray(Charsets.US_ASCII)
                    logger.debug("  $portName → INFO")
                    port.outputStream.write(bytes)
                    return@withContext parseInfoResponse(readTabyLine(port, EXCHANGE_TIMEOUT_MS))
                } catch (e: Exception) {
                    logger.debug("  $portName attempt ${attempt + 1} error: ${e::class.simpleName}: ${e.message}")
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("readInfo failed")
        } finally {
            port.closePort()
        }
    }

    /**
     * Reads the current touch signal (raw integer).
     * Sends `TOUCH_SIGNAL\n`, expects `TABY:OK <n>` or just a raw int line.
     */
    suspend fun readTouchSignal(portName: String): Int = withContext(Dispatchers.IO) {
        val line = exchange(portName, "TOUCH_SIGNAL\n", EXCHANGE_TIMEOUT_MS)
        // Response may be "TABY:OK 3" or "TABY:TOUCH_SIGNAL 3" — take last token
        line.trim().split(" ").last().toIntOrNull()
            ?: throw IllegalStateException("Unexpected touch signal response: $line")
    }

    /**
     * Reads the reusable choice signal.
     * Sends `CHOICE_SIGNAL\n`, expects `TABY:CHOICE_SIGNAL <json>`.
     */
    suspend fun readChoiceSignal(portName: String): ChoiceSignal = withContext(Dispatchers.IO) {
        val line = exchange(portName, "CHOICE_SIGNAL\n", EXCHANGE_TIMEOUT_MS)
        parseChoiceSignalResponse(line)
    }

    /**
     * Sends an arbitrary command to the device.
     * The command is normalised automatically — you can pass raw names like
     * `"ANIM task_completed"` or protocol commands like `"BRIGHTNESS 80"`.
     */
    /**
     * Opens the port for [portName] and returns a [TabyUsbSession].
     * The session keeps the port open — close it when done to avoid
     * resetting the device on every command.
     */
    suspend fun openSession(portName: String): TabyUsbSession =
        withContext(Dispatchers.IO) { TabyUsbSession(portName, openAndSettle(portName)) }

    /**
     * A persistent connection to one Taby over USB. Keeps the port open so
     * commands don't trigger a device reset on each open/close cycle.
     * Use [close] (or a `use` block) to release the port.
     */
    inner class TabyUsbSession(val portName: String, private val port: SerialPort) : Closeable {

        private val disconnected = AtomicBoolean(false)

        /**
         * Registers [callback] to fire once when the OS reports the device gone
         * (the event spams repeatedly on Windows, hence the once-guard).
         */
        fun onDisconnect(callback: () -> Unit) {
            port.addDataListener(object : SerialPortDataListener {
                override fun getListeningEvents() = SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                override fun serialEvent(event: SerialPortEvent) {
                    if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED &&
                        disconnected.compareAndSet(false, true)
                    ) {
                        logger.debug("  $portName disconnect event")
                        callback()
                    }
                }
            })
        }

        fun sendCommand(command: String): CommandResult {
            val request = normalizeUsbRequest(command)
            logger.debug("  $portName → ${request.trim()}")
            port.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            val rawResponse = readTabyLine(port, EXCHANGE_TIMEOUT_MS)
            val ok = !rawResponse.startsWith("TABY:ERR")
            return CommandResult(ok, TabyTransport.USB, command, rawResponse,
                if (ok) "USB command acknowledged: $rawResponse" else rawResponse)
        }

        override fun close() {
            logger.debug("  $portName closing session")
            port.removeDataListener()
            port.closePort()
        }
    }

    suspend fun sendCommand(portName: String, command: String): CommandResult =
        withContext(Dispatchers.IO) {
            val request = normalizeUsbRequest(command)
            val rawResponse = exchange(portName, request, EXCHANGE_TIMEOUT_MS)
            val ok = !rawResponse.startsWith("TABY:ERR")
            CommandResult(
                ok = ok,
                transport = TabyTransport.USB,
                command = command,
                rawResponse = rawResponse,
                message = if (ok) "USB command acknowledged: $rawResponse" else rawResponse,
            )
        }

    // ── Low-level exchange ─────────────────────────────────────────────────────

    /**
     * Opens the port, sends [request], waits for the first `TABY:`-prefixed
     * response line, then closes the port.
     */
    private fun exchange(portName: String, request: String, timeoutMs: Long): String {
        val port = openAndSettle(portName)
        try {
            logger.debug("  $portName → ${request.trim()}")
            port.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            return readTabyLine(port, timeoutMs)
        } finally {
            port.closePort()
        }
    }

    /**
     * Opens [portName], clears DTR/RTS, then waits until the device has been
     * silent for [SETTLE_QUIET_MS] ms (boot output drained) or [SETTLE_TIMEOUT_MS]
     * ms have elapsed — whichever comes first.
     */
    private fun openAndSettle(portName: String): SerialPort {
        val port = SerialPort.getCommPort(portName)
        port.setBaudRate(USB_BAUD_RATE)
        port.setNumDataBits(8)
        port.setNumStopBits(SerialPort.ONE_STOP_BIT)
        port.setParity(SerialPort.NO_PARITY)
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)

        logger.debug("  $portName opening port")
        check(port.openPort()) { "Could not open serial port $portName" }

        // Non-blocking reads: read() returns immediately with whatever bytes are available.
        // Prevents indefinite blocking on virtual/Bluetooth COM ports.
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)

        // ESP32 auto-reset circuit: DTR→EN (reset), RTS→GPIO0 (boot-mode select).
        // openPort() asserts both HIGH: device is held in reset with GPIO0 LOW = download mode.
        // Fix: clear RTS first (GPIO0 HIGH = app mode), wait, THEN clear DTR (release reset).
        port.clearRTS()
        Thread.sleep(50)
        port.clearDTR()

        drainUntilQuiet(port)
        return port
    }

    /**
     * Reads and discards incoming bytes until there has been no data for
     * [SETTLE_QUIET_MS] ms, or [SETTLE_TIMEOUT_MS] ms total have elapsed.
     * Logs every discarded line so boot output is visible in debug mode.
     */
    private fun drainUntilQuiet(port: SerialPort) {
        val buf = StringBuilder()
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        var lastByteAt = System.currentTimeMillis()

        while (System.currentTimeMillis() < deadline) {
            val available = port.inputStream.available()
            if (available > 0) {
                val chunk = ByteArray(available)
                port.inputStream.read(chunk)
                buf.append(String(chunk, Charsets.US_ASCII))
                lastByteAt = System.currentTimeMillis()
                // Log complete lines
                while (true) {
                    val idx = buf.indexOfFirst { it == '\r' || it == '\n' }
                    if (idx == -1) break
                    val line = buf.substring(0, idx).trim()
                    buf.delete(0, idx + 1)
                    if (line.isNotEmpty()) logger.debug("  ${port.systemPortName} [boot] $line")
                }
            } else {
                if (System.currentTimeMillis() - lastByteAt >= SETTLE_QUIET_MS) {
                    logger.debug("  ${port.systemPortName} settled after ${System.currentTimeMillis() - (deadline - SETTLE_TIMEOUT_MS)}ms")
                    return
                }
                Thread.sleep(READ_POLL_INTERVAL_MS)
            }
        }
        logger.debug("  ${port.systemPortName} settle timeout — proceeding anyway")
    }

    /**
     * Reads bytes from [port] until a line starting with `TABY:` arrives.
     * In debug mode every received line (including device log output) is printed.
     */
    private fun readTabyLine(port: SerialPort, timeoutMs: Long): String {
        val buffer = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val available = port.inputStream.available()
            if (available > 0) {
                val chunk = ByteArray(available)
                port.inputStream.read(chunk)
                buffer.append(String(chunk, Charsets.US_ASCII))

                while (true) {
                    val breakIndex = buffer.indexOfFirst { it == '\r' || it == '\n' }
                    if (breakIndex == -1) break
                    val line = buffer.substring(0, breakIndex).trim()
                    buffer.delete(0, breakIndex + 1)
                    if (line.isEmpty()) continue
                    logger.debug("  ${port.systemPortName} ← $line")
                    if (line.startsWith(PROTOCOL_PREFIX)) return line
                    // Device sometimes emits a log line with no trailing newline immediately
                    // followed by the TABY: response — both arrive as one buffer chunk.
                    val tabyIdx = line.indexOf(PROTOCOL_PREFIX)
                    if (tabyIdx > 0) return line.substring(tabyIdx)
                }
            } else {
                Thread.sleep(READ_POLL_INTERVAL_MS)
            }
        }

        throw TabyTimeoutException("USB protocol response timed out on ${port.systemPortName}")
    }

}

internal data class TabyUsbPort(
    val portName: String,
    val friendlyName: String,
    val info: DeviceInfo,
)

class TabyTimeoutException(message: String) : Exception(message)
