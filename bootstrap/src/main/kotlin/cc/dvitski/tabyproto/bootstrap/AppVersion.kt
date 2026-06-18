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
