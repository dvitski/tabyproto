plugins {
    kotlin("jvm")
    application
}

dependencies {
    api(project(":api"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

application {
    mainClass.set("cc.dvitski.tabyproto.app.MainKt")
}

kotlin {
    jvmToolchain(21)
}
