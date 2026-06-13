plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":api"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.github.kdroidfilter:composemediaplayer:0.10.0") // 0.10.1 not yet synced to Maven Central
    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.11:windows-x86_64")
}

compose.desktop {
    application {
        mainClass = "cc.dvitski.tabyproto.desktop.MainKt"
    }
}

kotlin {
    jvmToolchain(21)
}
