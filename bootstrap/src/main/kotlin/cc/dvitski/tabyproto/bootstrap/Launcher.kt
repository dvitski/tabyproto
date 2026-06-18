package cc.dvitski.tabyproto.bootstrap

import org.update4j.Configuration
import java.net.URI
import java.nio.file.Paths
import java.util.logging.Logger

private val log = Logger.getLogger("taby-bootstrap")

// Replace with your actual server URL before shipping.
private const val UPDATE_URL = "https://yourserver/taby/update.xml"

fun main(args: Array<String>) {
    // Compute install app/ dir from the bundled JRE location.
    // In jpackage builds: java.home = <install>/runtime, app jars = <install>/app/
    // In dev (./gradlew run): app/ won't exist — update check is skipped gracefully.
    val appDir = Paths.get(System.getProperty("java.home")).parent.resolve("app")
    System.setProperty("taby.app.dir", appDir.toAbsolutePath().toString())

    if (appDir.toFile().isDirectory) {
        // Fetch manifest and download only changed JARs.
        // Update4j writes directly to basePath (= app/) before desktop classes are loaded,
        // so the JVM's lazy classpath opening means those JARs aren't locked yet.
        // If the write fails (e.g. locked on an unusual JVM), runCatching swallows it
        // and the existing version launches normally.
        val config = runCatching {
            val conn = URI(UPDATE_URL).toURL().openConnection()
            conn.connectTimeout = 3_000
            conn.readTimeout = 10_000
            Configuration.read(conn.getInputStream().reader())
        }.onFailure { log.warning("Update check failed: ${it.message}") }.getOrNull()

        config?.runCatching { update() }
            ?.onFailure { log.warning("Update apply failed: ${it.message}") }
    }

    // Reflectively invoke desktop MainKt — this is the first moment desktop.jar is opened
    // by the classloader, so any updated JAR written above is what gets loaded.
    @Suppress("UNCHECKED_CAST")
    Class.forName("cc.dvitski.tabyproto.desktop.MainKt")
        .getMethod("main", Array<String>::class.java)
        .invoke(null, args)
}
