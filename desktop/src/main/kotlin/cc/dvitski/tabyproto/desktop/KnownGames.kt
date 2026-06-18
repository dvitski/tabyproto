package cc.dvitski.tabyproto.desktop

object KnownGames {

    // High-confidence matches: fires regardless of fullscreen state. Process names as
    // reported by System.Diagnostics.Process.ProcessName (no .exe extension).
    private val KNOWN_GAME_PROCESSES: Set<String> = setOf(
        "VALORANT-Win64-Shipping",
        "csgo",
        "cs2",
        "LeagueClientUx",
        "League of Legends",
        "Dota2",
        "FortniteClient-Win64-Shipping",
        "GTA5",
        "Overwatch",
        "RocketLeague",
        "EscapeFromTarkov",
        "ApexLegends",
        "r5apex",
    ).map { it.lowercase() }.toSet()

    // Common non-game apps that often run fullscreen/borderless; excluded from the
    // fullscreen fallback so e.g. a fullscreen browser doesn't register as a game.
    private val FULLSCREEN_EXCLUDE_PROCESSES: Set<String> = setOf(
        "chrome", "firefox", "msedge", "explorer",
        "idea64", "code", "Taby", "WindowsTerminal", "cmd", "powershell", "pwsh",
        "vlc", "mpv", "Spotify",
    ).map { it.lowercase() }.toSet()

    fun classify(processName: String?, isFullscreen: Boolean): GameState {
        if (processName == null) return GameState.Idle
        val normalized = processName.lowercase()
        return when {
            normalized in KNOWN_GAME_PROCESSES -> GameState.Active(processName)
            isFullscreen && normalized !in FULLSCREEN_EXCLUDE_PROCESSES -> GameState.Active(processName)
            else -> GameState.Idle
        }
    }
}
