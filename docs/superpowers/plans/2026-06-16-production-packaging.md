# Production Packaging & Auto-Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Package Taby as a Windows `.exe` installer with silent delta auto-updates from a private server, applied at every launch before the UI appears.

**Architecture:** A new `bootstrap` module holds `Launcher.kt` which runs first, fetches `update.xml` from the private server, has Update4j download only changed JARs (api, desktop, anim-resources) directly into the install's `app/` dir, then reflectively invokes `desktop.MainKt`. A new `anim-resources` module isolates the ~70 MP4 files as a separate JAR so code-only releases don't re-download animations. jpackage (via the existing Compose Desktop plugin in `desktop`) bundles everything with a bundled JRE; `desktop` adds `bootstrap` as `runtimeOnly` so `bootstrap.jar` is included.

**Tech Stack:** Update4j 1.5.9, jpackage via Compose Desktop Gradle plugin (`TargetFormat.Exe`), Kotlin/JVM 21.

---

## File Map

**New files:**
- `anim-resources/build.gradle.kts`
- `anim-resources/src/main/resources/anim/*.mp4` (moved from `desktop/`)
- `bootstrap/build.gradle.kts`
- `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/Launcher.kt`
- `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/AppVersion.kt`
- `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/ManifestGenerator.kt`

**Modified files:**
- `settings.gradle.kts` — register `anim-resources` and `bootstrap`
- `desktop/build.gradle.kts` — add `anim-resources` dep, `bootstrap` runtimeOnly dep, full jpackage config
- `desktop/src/main/resources/anim/` — emptied (files moved to `anim-resources`)

---

## Task 1: Create `anim-resources` module

**Files:**
- Create: `anim-resources/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Move: `desktop/src/main/resources/anim/*.mp4` → `anim-resources/src/main/resources/anim/`
- Modify: `desktop/build.gradle.kts`

- [ ] **Step 1: Create the module build file**

Create `anim-resources/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Register the module**

In `settings.gradle.kts`, change:
```kotlin
include("api", "app", "desktop")
```
to:
```kotlin
include("api", "app", "desktop", "anim-resources")
```

- [ ] **Step 3: Create the resource directory and move MP4s**

```bash
mkdir -p anim-resources/src/main/resources/anim
git mv desktop/src/main/resources/anim/*.mp4 anim-resources/src/main/resources/anim/
```

- [ ] **Step 4: Add dependency in `desktop`**

In `desktop/build.gradle.kts`, add to the `dependencies` block:
```kotlin
implementation(project(":anim-resources"))
```

- [ ] **Step 5: Verify it compiles**

```bash
./gradlew :desktop:compileKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run desktop tests to confirm animation loading still works**

```bash
./gradlew :desktop:test
```
Expected: `BUILD SUCCESSFUL`, all tests pass. `AnimationResources.kt` uses `getResourceAsStream("/anim/$id.mp4")` which finds files on the classpath regardless of which JAR they live in — no code changes needed.

- [ ] **Step 7: Commit**

```bash
git add anim-resources/ desktop/src/main/resources/anim/ desktop/build.gradle.kts settings.gradle.kts
git commit -m "feat: extract anim-resources module for independent JAR delta"
```

---

## Task 2: Create `bootstrap` module with launcher

**Files:**
- Create: `bootstrap/build.gradle.kts`
- Create: `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/Launcher.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create the module build file**

Create `bootstrap/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.update4j:update4j:1.5.9")
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Register the module**

In `settings.gradle.kts`, add `"bootstrap"`:
```kotlin
include("api", "app", "desktop", "anim-resources", "bootstrap")
```

- [ ] **Step 3: Create `Launcher.kt`**

Create `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/Launcher.kt`:
```kotlin
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
```

- [ ] **Step 4: Verify bootstrap compiles**

```bash
./gradlew :bootstrap:build
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add bootstrap/ settings.gradle.kts
git commit -m "feat: add bootstrap module with Update4j update-and-launch logic"
```

---

## Task 3: Configure jpackage in `desktop`

**Files:**
- Modify: `desktop/build.gradle.kts`

- [ ] **Step 1: Add `bootstrap` as a `runtimeOnly` dependency**

In `desktop/build.gradle.kts`, add to `dependencies`:
```kotlin
runtimeOnly(project(":bootstrap"))
```
This causes jpackage to include `bootstrap.jar` in the installer's `app/` directory without `desktop` compile-depending on it.

- [ ] **Step 2: Generate a stable `upgradeUuid`**

Run this once in PowerShell and save the output — paste it into the config below:
```powershell
[System.Guid]::NewGuid().ToString()
```

- [ ] **Step 3: Replace the `compose.desktop` block**

In `desktop/build.gradle.kts`, replace the existing `compose.desktop { ... }` block with:
```kotlin
compose.desktop {
    application {
        mainClass = "cc.dvitski.tabyproto.bootstrap.LauncherKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Taby"
            packageVersion = "1.0.0"
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                menuGroup = "Taby"
                upgradeUuid = "PASTE-YOUR-UUID-HERE"
                perUserInstall = true   // installs to %LocalAppData%\Taby — user-writable, no admin needed
                dirChooser = false
                shortcut = true
            }
        }
    }
}
```

Add this import at the top of `desktop/build.gradle.kts` if not already present:
```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
```

- [ ] **Step 4: Build the distributable and verify layout**

```bash
./gradlew :desktop:createDistributable
```
Expected: `BUILD SUCCESSFUL`

Verify that `bootstrap.jar` is present and that the launcher is correct:
```bash
ls desktop/build/compose/binaries/main/app/Taby/app/bootstrap*.jar
grep -i bootstrap desktop/build/compose/binaries/main/app/Taby/app/Taby.cfg
```
Expected: bootstrap JAR exists; cfg file references `cc.dvitski.tabyproto.bootstrap.LauncherKt`

- [ ] **Step 5: Commit**

```bash
git add desktop/build.gradle.kts
git commit -m "feat(desktop): configure jpackage installer — bootstrap launcher, perUserInstall, upgradeUuid"
```

---

## Task 4: Add manifest generator

The manifest generator is a Kotlin main class inside `bootstrap` that reads the three managed JARs, computes their checksums via Update4j, and writes `update.xml`. A Gradle task runs it.

**Files:**
- Create: `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/ManifestGenerator.kt`
- Modify: `bootstrap/build.gradle.kts`

- [ ] **Step 1: Create `ManifestGenerator.kt`**

Create `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/ManifestGenerator.kt`:
```kotlin
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

        val files = managedJars.map { jar ->
            FileMetadata.readFrom(jar.toPath())
                .path(jar.name)
                .classpath(true)
                .build()
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
```

- [ ] **Step 2: Add staging + generator tasks to `bootstrap/build.gradle.kts`**

In `bootstrap/build.gradle.kts`, add after the `dependencies` block:
```kotlin
val stageReleaseJars by tasks.registering(Copy::class) {
    description = "Copies the three managed JARs to a staging dir for manifest generation."
    dependsOn(":api:jar", ":desktop:jar", ":anim-resources:jar")
    into(layout.buildDirectory.dir("release-jars"))
    from(project(":api").tasks.named<org.gradle.jvm.tasks.Jar>("jar").map { it.archiveFile }) {
        rename { "api.jar" }
    }
    from(project(":desktop").tasks.named<org.gradle.jvm.tasks.Jar>("jar").map { it.archiveFile }) {
        rename { "desktop.jar" }
    }
    from(project(":anim-resources").tasks.named<org.gradle.jvm.tasks.Jar>("jar").map { it.archiveFile }) {
        rename { "anim-resources.jar" }
    }
}

tasks.register<JavaExec>("generateUpdateXml") {
    description = "Generates update.xml for upload to the private update server."
    dependsOn(stageReleaseJars, tasks.named("jar"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cc.dvitski.tabyproto.bootstrap.ManifestGenerator")
    args(
        "https://yourserver/taby/",
        layout.buildDirectory.dir("release-jars").get().asFile.absolutePath,
        layout.buildDirectory.file("update.xml").get().asFile.absolutePath
    )
}
```

- [ ] **Step 3: Run the manifest generator**

```bash
./gradlew :bootstrap:generateUpdateXml
```
Expected: `BUILD SUCCESSFUL`. Output lists three JARs with their sizes.

Inspect the output:
```bash
cat bootstrap/build/update.xml
```
Expected: XML with `<base-uri>https://yourserver/taby/</base-uri>`, `<base-path>${taby.app.dir}</base-path>`, and three `<file>` entries with `checksum` and `size` attributes.

- [ ] **Step 4: Commit**

```bash
git add bootstrap/
git commit -m "feat(bootstrap): add manifest generator and generateUpdateXml Gradle task"
```

---

## Task 5: Add `AppVersion` and version.properties

This lets the Settings screen display the running app version.

**Files:**
- Create: `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/AppVersion.kt`
- Modify: `bootstrap/build.gradle.kts`

- [ ] **Step 1: Add version generation task to `bootstrap/build.gradle.kts`**

Add after the existing tasks:
```kotlin
val generateVersionProperties by tasks.registering {
    val versionString = rootProject.version.toString()
    val outDir = layout.buildDirectory.dir("generated/resources/main")
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("version.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("version=$versionString\n")
    }
}

sourceSets["main"].resources.srcDir(
    generateVersionProperties.map { layout.buildDirectory.dir("generated/resources/main") }
)
```

- [ ] **Step 2: Create `AppVersion.kt`**

Create `bootstrap/src/main/kotlin/cc/dvitski/tabyproto/bootstrap/AppVersion.kt`:
```kotlin
package cc.dvitski.tabyproto.bootstrap

object AppVersion {
    val current: String by lazy {
        AppVersion::class.java.getResourceAsStream("/version.properties")
            ?.reader()
            ?.readText()
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("version=") }
            ?.removePrefix("version=")
            ?.trim()
            ?: "unknown"
    }
}
```

- [ ] **Step 3: Verify version.properties is generated**

```bash
./gradlew :bootstrap:processResources
cat bootstrap/build/generated/resources/main/version.properties
```
Expected: `version=1.0.0`

- [ ] **Step 4: Commit**

```bash
git add bootstrap/
git commit -m "feat(bootstrap): generate version.properties at build time; expose via AppVersion"
```

---

## Task 6: Build the installer and smoke-test

- [ ] **Step 1: Set your real server URL**

In `Launcher.kt`, replace the placeholder:
```kotlin
private const val UPDATE_URL = "https://yourserver/taby/update.xml"
```
with your actual private server URL. Do the same in `ManifestGenerator.kt`'s default arg.

Also set the real URL in the `generateUpdateXml` task's `args(...)` call in `bootstrap/build.gradle.kts`.

Commit:
```bash
git add bootstrap/
git commit -m "feat(bootstrap): set production update server URL"
```

- [ ] **Step 2: Build the installer**

```bash
./gradlew :desktop:packageExe
```
Expected: `BUILD SUCCESSFUL`. Installer at `desktop/build/compose/binaries/main/exe/Taby-1.0.0.exe`.

- [ ] **Step 3: Install it**

Run `desktop/build/compose/binaries/main/exe/Taby-1.0.0.exe`. Accept the SmartScreen warning (expected — no code signing). Install completes to `%LocalAppData%\Taby\`.

- [ ] **Step 4: Verify installation layout**

```powershell
Get-ChildItem "$env:LocalAppData\Taby\app\*.jar" | Select-Object Name, @{n='KB';e={[int]($_.Length/1KB)}}
```
Expected: `bootstrap.jar`, `update4j.jar`, `api.jar`, `desktop.jar`, `anim-resources.jar`, and Compose/Kotlin dependency JARs.

- [ ] **Step 5: Launch and verify the app starts**

Run `%LocalAppData%\Taby\Taby.exe`. The update check will fail silently if your server isn't configured yet — the app should still open normally.

- [ ] **Step 6: Document the release workflow**

After every code change, the release steps are:
1. Bump `version` in root `build.gradle.kts`
2. `./gradlew assemble`
3. `./gradlew :bootstrap:generateUpdateXml`
4. Upload `api.jar`, `desktop.jar`, `anim-resources.jar` from `bootstrap/build/release-jars/` to `https://yourserver/taby/`
5. Upload `bootstrap/build/update.xml` to `https://yourserver/taby/update.xml`
6. Users get the delta update on next app launch

A new `.exe` installer is only needed when: jpackage config changes, the bundled JRE needs updating, or Compose/Kotlin runtime dependency versions change.

---

## Release Server Layout

Your private server must serve these static files:
```
https://yourserver/taby/
  update.xml           ← regenerated on each release
  api.jar              ← only re-uploaded when api module changes
  desktop.jar          ← only re-uploaded when desktop module changes
  anim-resources.jar   ← only re-uploaded when MP4s change
```

Update4j compares checksums from `update.xml` against local files on each launch and downloads only those that differ.
