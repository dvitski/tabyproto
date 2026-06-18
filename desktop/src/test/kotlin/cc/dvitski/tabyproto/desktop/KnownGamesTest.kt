package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class KnownGamesTest {

    @Test
    fun `known game process is Active regardless of fullscreen state`() {
        val result = KnownGames.classify("VALORANT-Win64-Shipping", isFullscreen = false)
        assertEquals(GameState.Active("VALORANT-Win64-Shipping"), result)
    }

    @Test
    fun `known game process matches case-insensitively`() {
        val result = KnownGames.classify("valorant-win64-shipping", isFullscreen = false)
        assertEquals(GameState.Active("valorant-win64-shipping"), result)
    }

    @Test
    fun `unknown fullscreen process is Active`() {
        val result = KnownGames.classify("SomeIndieGame", isFullscreen = true)
        assertEquals(GameState.Active("SomeIndieGame"), result)
    }

    @Test
    fun `unknown fullscreen process on the exclude list is Idle`() {
        val result = KnownGames.classify("chrome", isFullscreen = true)
        assertEquals(GameState.Idle, result)
    }

    @Test
    fun `unknown windowed process is Idle`() {
        val result = KnownGames.classify("notepad", isFullscreen = false)
        assertEquals(GameState.Idle, result)
    }

    @Test
    fun `null process name is Idle`() {
        val result = KnownGames.classify(null, isFullscreen = true)
        assertEquals(GameState.Idle, result)
    }
}
