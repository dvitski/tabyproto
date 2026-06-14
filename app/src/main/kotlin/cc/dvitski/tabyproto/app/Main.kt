package cc.dvitski.tabyproto.app

import cc.dvitski.tabyproto.Animations
import cc.dvitski.tabyproto.RawAnimation
import cc.dvitski.tabyproto.Taby
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger: Logger = LoggerFactory.getLogger("App-Main")

fun main() = runBlocking {
    val session = Taby.connect()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("\nShutting down...")
        try {
            runBlocking { session.play(RawAnimation.TURN_OFF_TV) }
        } catch (e: Exception) {
            logger.info("  teardown error: ${e.message}")
        } finally {
            session.close()
        }
    })

    logger.info("Connected via ${session.transport} — ${session.device.deviceId}")
    logger.info("  Firmware : ${session.device.firmwareVersion}")
    logger.info("  State    : ${session.device.state}")
    logger.info("")

    logger.info("Playing startup animation...")
    val result = session.play(Animations.all.random())
    logger.info("  → ${result.rawResponse}")

    logger.info("Press Ctrl+C to disconnect.")
    while (true) {
        delay(7.seconds)
        session.play(Animations.all.random())
    }
}
