import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":anim-resources"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.github.kdroidfilter:composemediaplayer:0.10.0") // 0.10.1 not yet synced to Maven Central
    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.11:windows-x86_64")
    runtimeOnly(project(":bootstrap"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

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
                upgradeUuid = "2647eff9-74d7-4b2b-aee6-62f5277d349c"
                perUserInstall = true   // installs to %LocalAppData%\Taby — user-writable, no admin needed
                dirChooser = false
                shortcut = true
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
