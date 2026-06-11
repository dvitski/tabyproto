plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("com.fazecast:jSerialComm:2.10.4")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.27")
}

kotlin {
    jvmToolchain(21)
}
