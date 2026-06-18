package cc.dvitski.tabyproto.bootstrap

import org.update4j.Configuration
import org.update4j.FileMetadata
import java.io.File
import java.net.URI

/**
 * Generates update.xml for the private update server.
 *
 * Usage: ManifestGenerator <serverBaseUrl> <jarsDir> <outputXml>
 * Example:
 *   ManifestGenerator https://yourserver/taby/ bootstrap/build/release-jars/ bootstrap/build/update.xml
 *
 * Only api.jar, desktop.jar, and anim-resources.jar are listed.
 * bootstrap.jar and update4j.jar are excluded — they are baked into the installer
 * and never auto-updated.
 */
object ManifestGenerator {

    private val EXCLUDED = setOf("bootstrap.jar", "update4j.jar")

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) {
            "Usage: ManifestGenerator <serverBaseUrl> <jarsDir> <outputXml>"
        }
        val serverBase = args[0]
        val jarsDir = File(args[1])
        val outputFile = File(args[2])

        require(jarsDir.isDirectory) { "Not a directory: ${jarsDir.absolutePath}" }

        val managedJars = jarsDir.listFiles { f ->
            f.extension == "jar" && f.name !in EXCLUDED
        }?.sortedBy { it.name } ?: error("No JARs found in $jarsDir")

        check(managedJars.isNotEmpty()) { "No managed JARs found in $jarsDir" }

        // basePath below references the placeholder "${taby.app.dir}" (resolved on the
        // client at runtime by Launcher.kt). Configuration.Builder.build() round-trips
        // basePath through implyPlaceholders() then immediately re-parses the result,
        // which calls resolvePlaceholders() and requires the property to actually
        // resolve to *something* at generation time too -- the resolved value itself is
        // discarded; only the literal "${taby.app.dir}" placeholder text is written to
        // the manifest. Set a dummy value here purely to satisfy that round-trip.
        if (System.getProperty("taby.app.dir") == null) {
            System.setProperty("taby.app.dir", jarsDir.absoluteFile.toPath().toString())
        }

        val files = managedJars.map { jar ->
            FileMetadata.readFrom(jar.toPath())
                .path(jar.name)
                .classpath(true)
        }

        val config = Configuration.builder()
            .baseUri(URI.create(serverBase))
            .basePath("\${taby.app.dir}")
            .files(files)
            .build()

        outputFile.parentFile?.mkdirs()
        outputFile.bufferedWriter().use { config.write(it) }

        println("Generated: ${outputFile.absolutePath}")
        println("Managed JARs (${managedJars.size}):")
        managedJars.forEach { println("  ${it.name}  (${it.length() / 1024} KB)") }
    }
}
