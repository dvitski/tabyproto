package cc.dvitski.tabyproto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val DEFAULT_TIMEOUT_S = 8L

/**
 * Communicates with a Taby device over WiFi.
 *
 * The device runs an HTTP server (default mDNS hostname: `taby.local`).
 * All endpoints are under `/v1/`.
 *
 * Usage:
 * ```kotlin
 * val client = TabyWifiClient()
 * val info = client.readHealth()
 * client.sendCommand("ANIM task_completed")
 * ```
 */
internal class TabyWifiClient(
    host: String = WIFI_DEFAULT_HOST,
    timeoutSeconds: Long = DEFAULT_TIMEOUT_S,
) {
    private val baseUrl = "http://$host"
    private val http = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    // ── Device info ────────────────────────────────────────────────────────────

    /** GET /v1/health — device info and diagnostics. */
    suspend fun readHealth(): DeviceInfo = withContext(Dispatchers.IO) {
        val raw = getJson<DeviceInfoRaw>("/v1/health")
        raw.toDomain()
    }

    /** Returns true if the device is reachable. */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            readHealth()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Commands ───────────────────────────────────────────────────────────────

    /**
     * POST /v1/command — send a command string to the device.
     * The server handles routing it to the firmware; no normalisation needed.
     */
    suspend fun sendCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        @Serializable data class Body(val command: String)
        postOk("/v1/command", Body(command))
        CommandResult(
            ok = true,
            transport = TabyTransport.WIFI,
            command = command,
            rawResponse = "OK",
            message = "Sent $command over Wi-Fi to $baseUrl",
        )
    }

    // ── Touch / choice signals ─────────────────────────────────────────────────

    /** GET /touch — raw touch signal integer. */
    suspend fun readTouchSignal(): Int = withContext(Dispatchers.IO) {
        val body = getText("/touch")
        body.trim().toIntOrNull()
            ?: throw IllegalStateException("Unexpected touch signal response: $body")
    }

    /** GET /v1/reusable/choice-signal */
    suspend fun readChoiceSignal(): ChoiceSignal = withContext(Dispatchers.IO) {
        getJson<ChoiceSignalRaw>("/v1/reusable/choice-signal").toDomain()
    }

    // ── WiFi management ────────────────────────────────────────────────────────

    /** GET /v1/wifi/networks — saved WiFi networks. */
    suspend fun getSavedWifiNetworks(): List<SavedWifiNetworkRaw> = withContext(Dispatchers.IO) {
        @Serializable data class Response(val networks: List<SavedWifiNetworkRaw>)
        getJson<Response>("/v1/wifi/networks").networks
    }

    /** GET /v1/wifi/nearby — nearby WiFi networks. */
    suspend fun getNearbyWifiNetworks(): List<NearbyWifiNetworkRaw> = withContext(Dispatchers.IO) {
        @Serializable data class Response(val networks: List<NearbyWifiNetworkRaw>)
        getJson<Response>("/v1/wifi/nearby").networks
    }

    /** POST /v1/provision — configure WiFi credentials. */
    suspend fun provisionWifi(ssid: String, password: String) = withContext(Dispatchers.IO) {
        @Serializable data class Body(val ssid: String, val password: String)
        postOk("/v1/provision", Body(ssid, password))
    }

    /** POST /v1/wifi/forget */
    suspend fun forgetWifiNetwork(ssid: String) = withContext(Dispatchers.IO) {
        @Serializable data class Body(val ssid: String)
        postOk("/v1/wifi/forget", Body(ssid))
    }

    /** POST /v1/wifi/prefer */
    suspend fun preferWifiNetwork(ssid: String) = withContext(Dispatchers.IO) {
        @Serializable data class Body(val ssid: String)
        postOk("/v1/wifi/prefer", Body(ssid))
    }

    // ── Provisioning ───────────────────────────────────────────────────────────

    /** GET /v1/provisioning/info */
    suspend fun getProvisioningInfo(): ProvisioningInfo = withContext(Dispatchers.IO) {
        getJson<ProvisioningInfoRaw>("/v1/provisioning/info").toDomain()
    }

    /** POST /v1/setup/start */
    suspend fun startSetup(): ProvisioningInfo = withContext(Dispatchers.IO) {
        val raw = postJson<Unit, ProvisioningInfoRaw>("/v1/setup/start", null)
        raw.toDomain()
    }

    // ── Device management ──────────────────────────────────────────────────────

    /** POST /v1/transport-default */
    suspend fun setTransportDefault(transport: TabyTransport) = withContext(Dispatchers.IO) {
        @Serializable data class Body(val mode: String)
        val mode = transport.name.lowercase()
        postOk("/v1/transport-default", Body(mode))
    }

    /** POST /v1/claim */
    suspend fun claimDevice(claimedBy: String) = withContext(Dispatchers.IO) {
        @Serializable data class Body(@SerialName("claimed_by") val claimedBy: String)
        postOk("/v1/claim", Body(claimedBy))
    }

    /** POST /v1/onboarding/reset */
    suspend fun resetOnboarding() = withContext(Dispatchers.IO) {
        postOk("/v1/onboarding/reset", EmptyBody)
    }

    /** POST /v1/factory-reset */
    suspend fun factoryReset() = withContext(Dispatchers.IO) {
        postOk("/v1/factory-reset", EmptyBody)
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private inline fun <reified T> getJson(path: String): T {
        val request = Request.Builder().url("$baseUrl$path").get().build()
        val body = execute(request)
        return json.decodeFromString(body)
    }

    private fun getText(path: String): String {
        val request = Request.Builder().url("$baseUrl$path").get().build()
        return execute(request)
    }

    private inline fun <reified B> postOk(path: String, body: B) {
        val bodyJson = json.encodeToString(body)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(request)
    }

    private inline fun <reified B, reified R> postJson(path: String, body: B?): R {
        val bodyJson = if (body != null) json.encodeToString(body) else "{}"
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return json.decodeFromString(execute(request))
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} from ${request.url}: ${response.body?.string()}"
            }
            return response.body?.string() ?: ""
        }
    }
}

@Serializable
private object EmptyBody
