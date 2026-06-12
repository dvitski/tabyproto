package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
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

// ── App background colour ──────────────────────────────────────────────────────

private val AppBackground = Color(0xFF1E1E2E)
private val MutedText = Color(0xFF9399B2)

// ── AppState ───────────────────────────────────────────────────────────────────

class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hostsStore = ManualHostsStore()
    private val monitor = TabyDeviceMonitor(initialManualHosts = hostsStore.load())

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
    val totalAnimations: Int = Animation.entries.size

    init {
        monitor.start()
        thumbnailCache.preloadAll(Animation.entries)
        AnimationResources.preloadAll(Animation.entries, scope)
        scope.launch(Dispatchers.IO) { monitor.manualHosts.collect { hostsStore.save(it) } }
        scope.launch {
            monitor.devices.collect { list ->
                val activeId = _activeDeviceId.value
                if (activeId != null && list.none { it.id == activeId }) {
                    _activeDeviceId.value = null
                }
                // Auto-select the first device to come online (USB preferred);
                // afterwards selection only changes by user action or the device vanishing.
                if (_activeDeviceId.value == null) {
                    val candidate = list.filter { it.online }
                        .minByOrNull { if (it.transport == TabyTransport.USB) 0 else 1 }
                    if (candidate != null) _activeDeviceId.value = candidate.id
                }
            }
        }
    }

    fun selectDevice(id: String) {
        _activeDeviceId.value = id
    }

    fun addManualHost(host: String) = monitor.addManualHost(host)

    fun removeManualHost(host: String) = monitor.removeManualHost(host)

    fun setQuery(q: String) {
        _query.value = q
    }

    suspend fun sendAnimation(animation: Animation): Result<Unit> {
        if (_sendingAnimation.value != null) {
            return Result.failure(IllegalStateException("Still sending ${_sendingAnimation.value?.id}"))
        }
        val device = devices.value.firstOrNull { it.id == _activeDeviceId.value }
            ?: return Result.failure(IllegalStateException("No Taby connected"))
        if (!device.online) {
            return Result.failure(IllegalStateException("${device.label} is offline"))
        }
        _sendingAnimation.value = animation
        return try {
            val result = monitor.session(device).play(animation)
            if (result.ok) {
                _lastSent.value = animation
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException(result.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _sendingAnimation.value = null
        }
    }

    fun close() {
        scope.cancel()
        monitor.close()
    }
}

// ── App composable ─────────────────────────────────────────────────────────────

@Composable
fun App(appState: AppState) {
    val devices by appState.devices.collectAsState()
    val activeDeviceId by appState.activeDeviceId.collectAsState()
    val query by appState.query.collectAsState()
    val lastSent by appState.lastSent.collectAsState()
    val sendingAnimation by appState.sendingAnimation.collectAsState()
    val loadedCount by appState.thumbnailCache.loadedCount.collectAsState()
    val total = appState.totalAnimations
    val thumbnailsReady = loadedCount >= total

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        backgroundColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (!thumbnailsReady) {
            // Splash loading screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Loading animations… $loadedCount / $total",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Top bar: query field + device selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = appState::setQuery,
                        label = { Text("Filter animations") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        DeviceSelector(
                            devices = devices,
                            activeDeviceId = activeDeviceId,
                            onSelect = appState::selectDevice,
                            onAddHost = appState::addManualHost,
                            onRemoveHost = appState::removeManualHost,
                        )
                        if (lastSent != null) {
                            Text(
                                text = "Last sent: ${lastSent?.id}",
                                color = MutedText,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                // Animation grid — always visible; sends fail with a snackbar
                // when no device is connected.
                val filtered = Animation.entries.filter { animation ->
                    query.isBlank() || animation.id.contains(query, ignoreCase = true)
                }
                AnimationGrid(
                    animations = filtered,
                    thumbnailCache = appState.thumbnailCache,
                    sendingAnimation = sendingAnimation,
                    onSend = { animation ->
                        scope.launch {
                            val result = appState.sendAnimation(animation)
                            result.onFailure { e ->
                                snackbarHostState.showSnackbar(
                                    message = e.message ?: "Send failed",
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
