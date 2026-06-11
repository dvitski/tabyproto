plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
}

allprojects {
    group = "cc.dvitski.tabyproto"
    version = "1.0.0"

    repositories {
        mavenCentral()
        google()
    }
}
