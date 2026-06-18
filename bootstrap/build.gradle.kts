plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.update4j:update4j:1.5.9")
}

kotlin {
    jvmToolchain(21)
}

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
