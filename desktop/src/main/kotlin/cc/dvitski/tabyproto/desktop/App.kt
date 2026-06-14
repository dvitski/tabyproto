package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.Animations
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyDeviceMonitor
import cc.dvitski.tabyproto.TabyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    data class Settings(val category: SettingsCategory = SettingsCategory.PlayAnimations) : Screen()
}

enum class SettingsCategory { PlayAnimations, Music, Voice, Device, Appearance }

enum class ListeningState { Idle, WakeWordDetected, Listening, Responding }

class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hostsStore = ManualHostsStore()
    private val monitor = TabyDeviceMonitor(initialManualHosts = hostsStore.load())
    private val themeStore = ThemeStore()

    val devices: StateFlow<List<TabyDevice>> = monitor.devices

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastSent = MutableStateFlow<Animation?>(null)
    val lastSent: StateFlow<Animation?> = _lastSent.asStateFlow()

    private val _sendingAnimation = MutableStateFlow<Animation?>(null)
    val sendingAnimation: StateFlow<Animation?> = _sendingAnimation.asStateFlow()

    val thumbnailCache: ThumbnailCache = ThumbnailCache()
    val totalAnimations: Int = Animations.all.size

    private val _selectedScreen = MutableStateFlow<Screen>(Screen.Home)
    val selectedScreen: StateFlow<Screen> = _selectedScreen.asStateFlow()

    private val _voiceOverlayVisible = MutableStateFlow(false)
    val voiceOverlayVisible: StateFlow<Boolean> = _voiceOverlayVisible.asStateFlow()

    private val _listeningState = MutableStateFlow(ListeningState.Idle)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _initialTheme = themeStore.load()
    private val _isDark = MutableStateFlow(_initialTheme.first)
    private val _palette = MutableStateFlow(_initialTheme.second)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()

    fun navigate(screen: Screen) { _selectedScreen.value = screen }
    fun showVoiceOverlay() { _voiceOverlayVisible.value = true }
    fun hideVoiceOverlay() { _voiceOverlayVisible.value = false }

    fun setTheme(isDark: Boolean, palette: ColorPalette) {
        _isDark.value = isDark
        _palette.value = palette
        themeStore.save(isDark, palette)
    }

    init {
        monitor.start()
        thumbnailCache.preloadAll(Animations.all)
        AnimationResources.preloadAll(Animations.all, scope)
        scope.launch(Dispatchers.IO) { monitor.manualHosts.collect { hostsStore.save(it) } }
        scope.launch {
            monitor.devices.collect { list ->
                val activeId = _activeDeviceId.value
                if (activeId != null && list.none { it.id == activeId }) _activeDeviceId.value = null
                if (_activeDeviceId.value == null) {
                    val candidate = list.filter { it.online }
                        .minByOrNull { if (it.transport == TabyTransport.USB) 0 else 1 }
                    if (candidate != null) _activeDeviceId.value = candidate.id
                }
            }
        }
    }

    fun selectDevice(id: String) { _activeDeviceId.value = id }
    fun addManualHost(host: String) = monitor.addManualHost(host)
    fun removeManualHost(host: String) = monitor.removeManualHost(host)
    fun setQuery(q: String) { _query.value = q }

    suspend fun sendAnimation(animation: Animation): Result<Unit> {
        val device = devices.value.firstOrNull { it.id == _activeDeviceId.value }
            ?: return Result.failure(IllegalStateException("No Taby connected"))
        if (!device.online) return Result.failure(IllegalStateException("${device.label} is offline"))
        if (!_sendingAnimation.compareAndSet(null, animation)) {
            return Result.failure(IllegalStateException("Still sending ${_sendingAnimation.value?.id ?: "an animation"}"))
        }
        return try {
            val result = monitor.session(device).play(animation)
            if (result.ok) { _lastSent.value = animation; Result.success(Unit) }
            else Result.failure(RuntimeException(result.message))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _sendingAnimation.value = null
        }
    }

    fun close() { scope.cancel(); monitor.close() }
}

@Composable
fun App(appState: AppState) {
    val isDark by appState.isDark.collectAsState()
    val palette by appState.palette.collectAsState()
    val theme = buildTheme(isDark, palette)

    CompositionLocalProvider(LocalAppTheme provides theme) {
        val devices by appState.devices.collectAsState()
        val activeDeviceId by appState.activeDeviceId.collectAsState()
        val query by appState.query.collectAsState()
        val lastSent by appState.lastSent.collectAsState()
        val sendingAnimation by appState.sendingAnimation.collectAsState()
        val loadedCount by appState.thumbnailCache.loadedCount.collectAsState()
        val selectedScreen by appState.selectedScreen.collectAsState()
        val voiceOverlayVisible by appState.voiceOverlayVisible.collectAsState()
        val listeningState by appState.listeningState.collectAsState()
        val total = appState.totalAnimations
        val thumbnailsReady = loadedCount >= total
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val onSend: (Animation) -> Unit = remember(scope, snackbarHostState) {
            { animation ->
                scope.launch {
                    appState.sendAnimation(animation).onFailure { e ->
                        snackbarHostState.showSnackbar(e.message ?: "Send failed")
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
            if (!thumbnailsReady) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "LOADING $loadedCount / $total",
                        color = theme.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Sidebar(
                        selectedScreen = selectedScreen,
                        devices = devices,
                        activeDeviceId = activeDeviceId,
                        lastSent = lastSent,
                        listeningState = listeningState,
                        onNavigate = appState::navigate,
                        onVoiceClick = appState::showVoiceOverlay,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (val screen = selectedScreen) {
                            Screen.Home -> HomeScreen(
                                devices = devices,
                                activeDeviceId = activeDeviceId,
                                lastSent = lastSent,
                            )
                            is Screen.Settings -> {
                                val filtered = Animations.all.filter { animation ->
                                    query.isBlank() || animation.id.contains(query, ignoreCase = true)
                                }
                                SettingsScreen(
                                    selectedCategory = screen.category,
                                    onCategorySelect = { appState.navigate(Screen.Settings(it)) },
                                    animations = filtered,
                                    query = query,
                                    onQueryChange = appState::setQuery,
                                    thumbnailCache = appState.thumbnailCache,
                                    sendingAnimation = sendingAnimation,
                                    onSend = onSend,
                                    devices = devices,
                                    activeDeviceId = activeDeviceId,
                                    onSelectDevice = appState::selectDevice,
                                    onAddHost = appState::addManualHost,
                                    onRemoveHost = appState::removeManualHost,
                                    isDark = isDark,
                                    currentPalette = palette,
                                    onSetTheme = appState::setTheme,
                                )
                            }
                        }
                    }
                }
                if (voiceOverlayVisible) {
                    VoiceOverlay(listeningState = listeningState, onDismiss = appState::hideVoiceOverlay)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
