package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.Taby
import cc.dvitski.tabyproto.TabySession
import cc.dvitski.tabyproto.TabyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── App background colour ──────────────────────────────────────────────────────

private val AppBackground = Color(0xFF1E1E2E)
private val MutedText = Color(0xFF9399B2)

// ── Connection state ───────────────────────────────────────────────────────────

sealed class ConnectionState {
    object Connecting : ConnectionState()
    data class Connected(val session: TabySession, val transport: TabyTransport) : ConnectionState()
    data class Failed(val message: String) : ConnectionState()
}

// ── AppState ───────────────────────────────────────────────────────────────────

class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastSent = MutableStateFlow<Animation?>(null)
    val lastSent: StateFlow<Animation?> = _lastSent.asStateFlow()

    private val _sendingAnimation = MutableStateFlow<Animation?>(null)
    val sendingAnimation: StateFlow<Animation?> = _sendingAnimation.asStateFlow()

    val thumbnailCache: ThumbnailCache = ThumbnailCache()
    val totalAnimations: Int = Animation.entries.size

    private var connectJob: Job? = null

    init {
        connect()
        thumbnailCache.preloadAll(Animation.entries)
        AnimationResources.preloadAll(Animation.entries, scope)
    }

    fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting
            try {
                val session = Taby.connect()
                _connectionState.value = ConnectionState.Connected(session, session.transport)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    suspend fun sendAnimation(animation: Animation): Result<Unit> {
        val state = _connectionState.value
        if (state !is ConnectionState.Connected) return Result.failure(IllegalStateException("Not connected"))
        _sendingAnimation.value = animation
        return try {
            val result = state.session.play(animation)
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
        val state = _connectionState.value
        if (state is ConnectionState.Connected) {
            state.session.close()
        }
    }
}

// ── App composable ─────────────────────────────────────────────────────────────

@Composable
fun App(appState: AppState) {
    val connectionState by appState.connectionState.collectAsState()
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
                // Top bar: query field + status indicator
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

                    StatusIndicator(connectionState = connectionState, lastSent = lastSent)
                }

                // Main content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val state = connectionState) {
                        is ConnectionState.Connecting -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Text("Connecting…", color = Color.White)
                            }
                        }

                        is ConnectionState.Failed -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "Connection failed: ${state.message}",
                                    color = Color(0xFFFF5555),
                                )
                                Button(onClick = appState::connect) {
                                    Text("Reconnect")
                                }
                            }
                        }

                        is ConnectionState.Connected -> {
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
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Status indicator ───────────────────────────────────────────────────────────

@Composable
private fun StatusIndicator(
    connectionState: ConnectionState,
    lastSent: Animation?,
) {
    val dotColor = when (connectionState) {
        is ConnectionState.Connected -> Color(0xFF00C853)
        is ConnectionState.Connecting -> Color(0xFFFFD600)
        is ConnectionState.Failed -> Color(0xFFFF5555)
    }

    val transportLabel = when (connectionState) {
        is ConnectionState.Connected -> when (connectionState.transport) {
            TabyTransport.USB -> "USB"
            TabyTransport.WIFI -> "WiFi"
            TabyTransport.BLUETOOTH -> "Bluetooth"
        }
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Failed -> "Disconnected"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = transportLabel,
                color = Color.White,
                fontSize = 12.sp,
            )
            if (lastSent != null) {
                Text(
                    text = "Last sent: ${lastSent.id}",
                    color = MutedText,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
