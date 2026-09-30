package com.example.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Grade
import com.example.data.SupabaseClientProvider
import com.example.data.repository.GradeRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TeacherRepository
import com.example.data.repository.ThemeMode
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MoreUiState(
    val teacherName: String = "",
    val teacherPhone: String? = null,
    val avatarUrl: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val grades: List<Grade> = emptyList(),
    val showThemeDialog: Boolean = false,
    val showEditProfileDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val showLogoutDialog: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutError: String? = null,
    val isTestingWaha: Boolean = false,
    val wahaTestResult: String? = null,
    val showWahaPairingDialog: Boolean = false,
    val isStartingWaha: Boolean = false,
    val wahaSessionStatus: String? = null,
    val wahaQrBase64: String? = null,
    val wahaConnectedPhone: String? = null,
    val wahaPairingError: String? = null,
    val selectedPairingTab: String = "CODE", // Default is "CODE" for 1-phone optimization
    val pairingPhoneNumber: String = "",
    val pairingCode: String? = null,
    val isRequestingPairingCode: Boolean = false,
    val pairingCodeError: String? = null,
    val isPairingCodeExpired: Boolean = false,
    val isSyncingWahaConfig: Boolean = false,
    val wahaSyncSuccessMessage: String? = null,
    val wahaSyncErrorMessage: String? = null
)

class MoreViewModel(
    private val settingsRepository: SettingsRepository = RepositoryProvider.settingsRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val runInitialCheck: Boolean = true,
    edgeFunctionUrl: String? = null
) : ViewModel() {

    companion object {
        val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    // In-memory only reference to pairing code copied to clipboard (never logged/persisted)
    private var lastCopiedPairingCode: String? = null

    private val wahaSessionUrl: String by lazy {
        edgeFunctionUrl?.takeIf { it.isNotBlank() }
            ?: SupabaseClientProvider.getEdgeFunctionUrl("waha-session")
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            teacherRepository.fetchCurrentTeacher()
            (gradeRepository as? com.example.data.repository.SupabaseGradeRepository)?.fetchGrades()
            // Check status on load
            if (runInitialCheck) {
                fetchWahaStatus()
            }
        }

        viewModelScope.launch {
            combine(
                teacherRepository.getCurrentTeacher(),
                settingsRepository.getThemeMode(),
                gradeRepository.getGrades()
            ) { teacher, theme, grades ->
                val name = teacher?.fullName?.ifBlank { null } ?: teacher?.email ?: ""
                val phone = teacher?.phoneNumber?.ifBlank { null }
                _uiState.value.copy(
                    teacherName = name,
                    teacherPhone = phone,
                    avatarUrl = teacher?.avatarUrl,
                    themeMode = theme,
                    grades = grades.sortedBy { it.displayOrder }
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private suspend fun fetchWahaStatus() {
        try {
            val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()
            val jsonObject = JSONObject().apply { put("action", "START") }
            val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val requestBuilder = Request.Builder()
                .url(wahaSessionUrl)
                .post(requestBody)
            if (!token.isNullOrBlank()) requestBuilder.header("Authorization", "Bearer $token")

            val response = withContext(ioDispatcher) {
                okHttpClient.newCall(requestBuilder.build()).execute()
            }

            response.use { res ->
                if (res.isSuccessful) {
                    val resJson = JSONObject(res.body?.string() ?: "")
                    if (resJson.optBoolean("success", false)) {
                        val status = resJson.optString("status", "")
                        val connectedPhone = resJson.optString("connected_phone", "")
                        _uiState.update {
                            it.copy(
                                wahaSessionStatus = status,
                                wahaConnectedPhone = if (connectedPhone.isNotBlank() && connectedPhone != "null") connectedPhone else null
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail if network is down on init
        }
    }

    fun openThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = true) }
    }

    fun closeThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = false) }
    }

    fun selectTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            closeThemeDialog()
        }
    }

    fun openEditProfileDialog() {
        _uiState.update { it.copy(showEditProfileDialog = true) }
    }

    fun closeEditProfileDialog() {
        _uiState.update { it.copy(showEditProfileDialog = false) }
    }

    fun updateTeacherName(newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                val currentTeacher = teacherRepository.fetchCurrentTeacher()
                teacherRepository.updateTeacherProfile(
                    fullName = newName.trim(),
                    phoneNumber = currentTeacher?.phoneNumber,
                    subject = currentTeacher?.subject,
                    centerName = currentTeacher?.centerName,
                    avatarUrl = currentTeacher?.avatarUrl
                )
            }
            closeEditProfileDialog()
        }
    }

    fun openAboutDialog() {
        _uiState.update { it.copy(showAboutDialog = true) }
    }

    fun closeAboutDialog() {
        _uiState.update { it.copy(showAboutDialog = false) }
    }

    fun openLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = true, logoutError = null) }
    }

    fun closeLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = false, logoutError = null) }
    }

    fun performLogout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, logoutError = null) }
            val result = com.example.data.auth.AccountSessionManager.logout()
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoggingOut = false, showLogoutDialog = false) }
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ أثناء تسجيل الخروج، يرجى المحاولة مرة أخرى."
                _uiState.update { it.copy(isLoggingOut = false, logoutError = errorMsg) }
            }
        }
    }

    fun testWahaConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingWaha = true, wahaTestResult = null) }
            try {
                val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()
                if (token.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isTestingWaha = false,
                            wahaTestResult = "انتهت جلسة تسجيل الدخول. يرجى تسجيل الدخول مرة أخرى."
                        )
                    }
                    return@launch
                }

                val jsonObject = JSONObject()
                jsonObject.put("action", "TEST_WAHA_CONNECTION")
                val bodyString = jsonObject.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url(wahaSessionUrl)
                    .post(requestBody)
                    .header("Authorization", "Bearer $token")

                val startTime = System.currentTimeMillis()
                val response = withContext(ioDispatcher) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }
                val latencyMs = System.currentTimeMillis() - startTime

                response.use { res ->
                    val code = res.code
                    val responseStr = res.body?.string() ?: ""
                    android.util.Log.d("WahaTest", "Raw response: $responseStr (code: $code)")

                    var resJson: JSONObject? = null
                    try {
                        resJson = JSONObject(responseStr)
                    } catch (_: Exception) {
                        // JSON parsing failed
                    }

                    val reachable = if (resJson != null) {
                        resJson.optBoolean("reachable", true)
                    } else {
                        true
                    }

                    val statusVal = if (resJson != null && resJson.has("status") && !resJson.isNull("status")) {
                        resJson.optInt("status")
                    } else {
                        code
                    }

                    val success = if (resJson != null && resJson.has("success")) {
                        resJson.optBoolean("success", false)
                    } else {
                        res.isSuccessful
                    }

                    val jsonLatency = if (resJson != null && resJson.has("latency_ms")) {
                        resJson.optLong("latency_ms", latencyMs)
                    } else {
                        latencyMs
                    }

                    val sessionExists = if (resJson != null && resJson.has("session_exists")) {
                        resJson.optBoolean("session_exists", statusVal != 404)
                    } else {
                        statusVal != 404
                    }

                    val sessionStatus = resJson?.optString("session_status", if (statusVal == 404) "NOT_FOUND" else "UNKNOWN") ?: "UNKNOWN"

                    val isConnected = if (resJson != null && resJson.has("whatsapp_connected")) {
                        resJson.optBoolean("whatsapp_connected", false)
                    } else {
                        sessionStatus == "WORKING" || sessionStatus == "CONNECTED"
                    }

                    val resultText = buildString {
                        append("WhatsApp Server Connection Test\n\n")
                        append("reachable: $reachable\n")
                        append("session_exists: $sessionExists\n")
                        append("status: $statusVal\n")
                        append("session_status: $sessionStatus\n")
                        append("whatsapp_connected: $isConnected\n")
                        append("latency_ms: ${jsonLatency}ms\n")
                        append("success: $success")

                        if (!reachable) {
                            append("\n\nتعذر الوصول إلى خادم WAHA. يرجى التحقق من عمل السيرفر والمفتاح.")
                        } else if (!sessionExists) {
                            append("\n\nخادم WhatsApp متصل وجاهز للعمل ✓\n(الجلسة غير موجودة بعد - يمكنك الضغط على 'ربط WhatsApp' لبدء الربط).")
                        } else if (isConnected) {
                            append("\n\nجلسة WhatsApp متصلة وتعمل بنجاح ✓")
                        } else {
                            append("\n\nالخادم متصل، وحالة الجلسة: $sessionStatus")
                        }
                    }

                    _uiState.update {
                        it.copy(isTestingWaha = false, wahaTestResult = resultText)
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                _uiState.update {
                    it.copy(
                        isTestingWaha = false,
                        wahaTestResult = buildString {
                            append("WhatsApp Server Connection Test\n\n")
                            append("reachable: false\n")
                            append("status: 0\n")
                            append("latency_ms: 0ms\n")
                            append("success: false")
                        }
                    )
                }
            } catch (e: java.io.IOException) {
                _uiState.update {
                    it.copy(
                        isTestingWaha = false,
                        wahaTestResult = buildString {
                            append("WhatsApp Server Connection Test\n\n")
                            append("reachable: false\n")
                            append("status: 0\n")
                            append("latency_ms: 0ms\n")
                            append("success: false")
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingWaha = false,
                        wahaTestResult = buildString {
                            append("WhatsApp Server Connection Test\n\n")
                            append("reachable: false\n")
                            append("status: 0\n")
                            append("latency_ms: 0ms\n")
                            append("success: false")
                        }
                    )
                }
            }
        }
    }

    fun dismissWahaTestResult() {
        _uiState.update { it.copy(wahaTestResult = null, isTestingWaha = false) }
    }

    fun openWahaPairingDialog() {
        val initialPhone = _uiState.value.teacherPhone ?: _uiState.value.pairingPhoneNumber
        _uiState.update {
            it.copy(
                showWahaPairingDialog = true,
                wahaPairingError = null,
                wahaSyncSuccessMessage = null,
                wahaSyncErrorMessage = null,
                selectedPairingTab = "CODE",
                pairingPhoneNumber = initialPhone,
                pairingCode = null,
                isRequestingPairingCode = false,
                pairingCodeError = null
            )
        }
        
        // Only start if not already connected/working
        val status = _uiState.value.wahaSessionStatus
        if (status != "CONNECTED" && status != "WORKING") {
            startWaha()
        }
    }

    fun syncWahaConfig() {
        if (_uiState.value.isSyncingWahaConfig) return // Prevent repeated clicks

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncingWahaConfig = true,
                    wahaSyncSuccessMessage = null,
                    wahaSyncErrorMessage = null
                )
            }

            try {
                val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

                val jsonObject = JSONObject()
                jsonObject.put("action", "START")
                val bodyString = jsonObject.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url(wahaSessionUrl)
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(ioDispatcher) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }

                response.use { res ->
                    val responseStr = res.body?.string() ?: ""
                    val resJson = try {
                        if (responseStr.isNotBlank()) JSONObject(responseStr) else null
                    } catch (_: Exception) {
                        null
                    }

                    if (!res.isSuccessful) {
                        val errorMsg = resJson?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "فشل مزامنة إعدادات WhatsApp (رمز الخطأ: ${res.code})"
                        _uiState.update {
                            it.copy(
                                isSyncingWahaConfig = false,
                                wahaSyncErrorMessage = errorMsg
                            )
                        }
                        return@launch
                    }

                    val success = resJson?.optBoolean("success", false) ?: false
                    if (success) {
                        val status = resJson?.optString("session_status", resJson.optString("status", "")) ?: ""
                        val connectedPhone = resJson?.optString("connected_phone", "")

                        _uiState.update {
                            it.copy(
                                isSyncingWahaConfig = false,
                                wahaSyncSuccessMessage = "تمت مزامنة إعدادات WhatsApp بنجاح",
                                wahaSessionStatus = if (status.isNotBlank()) status else it.wahaSessionStatus,
                                wahaConnectedPhone = if (!connectedPhone.isNullOrBlank() && connectedPhone != "null") connectedPhone else it.wahaConnectedPhone
                            )
                        }
                    } else {
                        val errorMsg = resJson?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "تعذر مزامنة إعدادات WhatsApp"
                        _uiState.update {
                            it.copy(
                                isSyncingWahaConfig = false,
                                wahaSyncErrorMessage = errorMsg
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncingWahaConfig = false,
                        wahaSyncErrorMessage = e.localizedMessage ?: "حدث خطأ أثناء الاتصال بالخادم"
                    )
                }
            }
        }
    }

    fun dismissWahaSyncStatus() {
        _uiState.update {
            it.copy(
                wahaSyncSuccessMessage = null,
                wahaSyncErrorMessage = null
            )
        }
    }

    fun closeWahaPairingDialog() {
        stopWahaPolling()
        _uiState.update { it.copy(showWahaPairingDialog = false) }
    }

    fun startWaha() {
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingWaha = true, wahaPairingError = null) }
            try {
                val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

                val jsonObject = JSONObject()
                jsonObject.put("action", "START")
                val bodyString = jsonObject.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url(wahaSessionUrl)
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(ioDispatcher) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }

                response.use { res ->
                    if (!res.isSuccessful) {
                        _uiState.update {
                            it.copy(
                                isStartingWaha = false,
                                wahaPairingError = "تعذر الاتصال بخادم واتساب حالياً. يرجى التحقق من الاتصال بالإنترنت."
                            )
                        }
                        return@launch
                    }

                    val responseStr = res.body?.string() ?: ""
                    val resJson = JSONObject(responseStr)

                    val success = resJson.optBoolean("success", false)
                    if (!success) {
                        val msg = resJson.optString("message", "تعذر الاتصال بخدمة واتساب")
                        val isQrPending = msg.contains("not available yet", ignoreCase = true) ||
                                msg.contains("QR code is not available", ignoreCase = true) ||
                                msg.contains("جاري تجهيز", ignoreCase = true) ||
                                msg.contains("started but QR", ignoreCase = true)

                        if (isQrPending) {
                            // WAHA session is running and generating the QR code in background
                            _uiState.update {
                                it.copy(
                                    isStartingWaha = false,
                                    wahaSessionStatus = "SCAN_QR_CODE",
                                    wahaPairingError = null
                                )
                            }
                            startWahaPolling()
                            return@launch
                        }

                        _uiState.update {
                            it.copy(
                                isStartingWaha = false,
                                wahaPairingError = msg
                            )
                        }
                        return@launch
                    }

                    val status = resJson.optString("session_status", resJson.optString("status", ""))
                    val connectedPhone = resJson.optString("connected_phone", "")
                    val qrData = if (resJson.has("qr") && !resJson.isNull("qr")) {
                        val qrObj = resJson.optJSONObject("qr")
                        if (qrObj != null) {
                            qrObj.optString("data", qrObj.optString("qr", ""))
                        } else {
                            resJson.optString("qr", "")
                        }
                    } else null

                    _uiState.update {
                        it.copy(
                            isStartingWaha = false,
                            wahaSessionStatus = status,
                            wahaConnectedPhone = if (connectedPhone.isNotBlank() && connectedPhone != "null") connectedPhone else null,
                            wahaQrBase64 = if (!qrData.isNullOrBlank()) qrData else null
                        )
                    }

                    if (status == "CONNECTED" || status == "WORKING") {
                        stopWahaPolling()
                    } else if (status == "SCAN_QR_CODE" && _uiState.value.selectedPairingTab == "QR") {
                        startWahaPolling()
                    }
                }
            } catch (e: java.io.IOException) {
                _uiState.update {
                    it.copy(
                        isStartingWaha = false,
                        wahaPairingError = "تعذر الاتصال بالخادم. يرجى التحقق من اتصال الإنترنت."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStartingWaha = false,
                        wahaPairingError = "حدث خطأ غير متوقع: ${e.localizedMessage ?: "خطأ بالاتصال"}"
                    )
                }
            }
        }
    }

    private var pollingAttemptCount = 0

    fun startWahaPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob?.cancel()
        pollingAttemptCount = 0
        pollingJob = viewModelScope.launch {
            // Poll for up to 60 iterations (~2.5 minutes timeout)
            while (isActive && pollingAttemptCount < 60) {
                kotlinx.coroutines.delay(2500)
                pollingAttemptCount++
                pollWahaStatus()
            }
            if (pollingAttemptCount >= 60 && isActive) {
                val currentStatus = _uiState.value.wahaSessionStatus
                if (currentStatus != "CONNECTED" && currentStatus != "WORKING") {
                    _uiState.update {
                        it.copy(
                            isPairingCodeExpired = true,
                            pairingCodeError = "انتهت صلاحية الكود"
                        )
                    }
                    stopWahaPolling()
                }
            }
        }
    }

    fun checkStatusImmediately() {
        viewModelScope.launch {
            pollWahaStatus()
            val currentStatus = _uiState.value.wahaSessionStatus
            if (currentStatus != "CONNECTED" && currentStatus != "WORKING" && currentStatus != "FAILED") {
                if (pollingJob?.isActive != true) {
                    startWahaPolling()
                }
            }
        }
    }

    fun stopWahaPolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingAttemptCount = 0
    }

    fun recordPairingCodeCopied(code: String) {
        lastCopiedPairingCode = code
    }

    fun shouldClearClipboard(currentClipText: String?): Boolean {
        val target = lastCopiedPairingCode
        if (!target.isNullOrBlank() && currentClipText == target) {
            lastCopiedPairingCode = null
            return true
        }
        return false
    }

    fun requestNewPairingCode() {
        _uiState.update {
            it.copy(
                pairingCode = null,
                isPairingCodeExpired = false,
                pairingCodeError = null
            )
        }
        requestPairingCode()
    }

    private suspend fun pollWahaStatus() {
        try {
            val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

            val jsonObject = JSONObject()
            jsonObject.put("action", "START")
            val bodyString = jsonObject.toString()

            val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val requestBuilder = Request.Builder()
                .url(wahaSessionUrl)
                .post(requestBody)

            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = withContext(ioDispatcher) {
                okHttpClient.newCall(requestBuilder.build()).execute()
            }

            response.use { res ->
                if (res.isSuccessful) {
                    val responseStr = res.body?.string() ?: ""
                    val resJson = JSONObject(responseStr)

                    if (resJson.optBoolean("success", false)) {
                        val status = resJson.optString("session_status", resJson.optString("status", ""))
                        val connectedPhone = resJson.optString("connected_phone", "")
                        val qrData = if (resJson.has("qr") && !resJson.isNull("qr")) {
                            val qrObj = resJson.optJSONObject("qr")
                            if (qrObj != null) {
                                qrObj.optString("data", qrObj.optString("qr", ""))
                            } else {
                                resJson.optString("qr", "")
                            }
                        } else null

                        _uiState.update {
                            it.copy(
                                wahaSessionStatus = status,
                                wahaConnectedPhone = if (connectedPhone.isNotBlank() && connectedPhone != "null") connectedPhone else null,
                                wahaQrBase64 = if (!qrData.isNullOrBlank()) qrData else null
                            )
                        }

                        if (status == "CONNECTED" || status == "WORKING" || status == "FAILED") {
                            stopWahaPolling()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore temporary network glitches during polling
        }
    }

    fun logoutWaha() {
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingWaha = true, wahaPairingError = null) }
            try {
                val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

                val jsonObject = JSONObject()
                jsonObject.put("action", "LOGOUT")
                val bodyString = jsonObject.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url(wahaSessionUrl)
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(ioDispatcher) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }

                response.use { res ->
                    if (res.isSuccessful) {
                        val responseStr = res.body?.string() ?: ""
                        val resJson = JSONObject(responseStr)

                        if (resJson.optBoolean("success", false)) {
                            _uiState.update {
                                it.copy(
                                    isStartingWaha = false,
                                    wahaSessionStatus = "DISCONNECTED",
                                    wahaConnectedPhone = null,
                                    wahaQrBase64 = null
                                )
                            }
                            stopWahaPolling()
                            return@launch
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isStartingWaha = false,
                            wahaPairingError = "تعذر قطع اتصال واتساب."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStartingWaha = false,
                        wahaPairingError = "حدث خطأ غير متوقع أثناء قطع الاتصال."
                    )
                }
            }
        }
    }

    fun selectPairingTab(tab: String) {
        _uiState.update { it.copy(selectedPairingTab = tab, pairingCodeError = null) }
    }

    fun onPairingPhoneNumberChange(num: String) {
        val normalized = com.example.util.PhoneUtil.normalizeDigits(num)
        val clean = normalized.replace(Regex("[^0-9]"), "")
        _uiState.update { it.copy(pairingPhoneNumber = clean, pairingCodeError = null) }
    }

    fun requestPairingCode() {
        val phoneNumber = _uiState.value.pairingPhoneNumber
        if (phoneNumber.isBlank()) {
            _uiState.update { it.copy(pairingCodeError = "يرجى إدخال رقم الهاتف أولاً.") }
            return
        }

        val formattedPhone = com.example.util.PhoneUtil.formatForWhatsApp(phoneNumber)
        if (formattedPhone.length < 8) {
            _uiState.update { it.copy(pairingCodeError = "رقم الهاتف غير صالح. يرجى إدخال رقم هاتف صحيح مع رمز الدولة (مثال: 201012345678).") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingPairingCode = true, pairingCodeError = null, pairingCode = null) }
            try {
                val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

                val jsonObject = JSONObject()
                jsonObject.put("action", "REQUEST_PAIRING_CODE")
                jsonObject.put("phoneNumber", formattedPhone)
                val bodyString = jsonObject.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url(wahaSessionUrl)
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(ioDispatcher) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }

                response.use { res ->
                    if (!res.isSuccessful) {
                        val code = res.code
                        _uiState.update {
                            it.copy(
                                isRequestingPairingCode = false,
                                pairingCodeError = if (code == 401) "غير مصرح بالدخول. يرجى تسجيل الدخول مرة أخرى."
                                               else if (code == 404) "خادم واتساب غير متوفر حالياً."
                                               else "فشل طلب كود الربط. رمز الخطأ: $code"
                            )
                        }
                        return@launch
                    }

                    val responseStr = res.body?.string() ?: ""
                    val resJson = JSONObject(responseStr)

                    val success = resJson.optBoolean("success", false)
                    if (!success) {
                        val msg = resJson.optString("message", "فشل جلب كود الربط")
                        _uiState.update {
                            it.copy(
                                isRequestingPairingCode = false,
                                pairingCodeError = msg
                            )
                        }
                        return@launch
                    }

                    var code = resJson.optString("code", "").trim()
                    if (code.isEmpty()) {
                        code = resJson.optJSONObject("data")?.optString("code", "")?.trim() ?: ""
                    }
                    if (code.isEmpty()) {
                        code = resJson.optJSONObject("waha_response")?.optString("code", "")?.trim() ?: ""
                    }
                    if (code.isEmpty()) {
                        code = resJson.optString("pairingCode", "").trim()
                    }
                    lastCopiedPairingCode = code
                    _uiState.update {
                        it.copy(
                            isRequestingPairingCode = false,
                            pairingCode = code,
                            isPairingCodeExpired = false,
                            wahaSessionStatus = "SCAN_QR_CODE"
                        )
                    }

                    startWahaPolling()
                }
            } catch (e: java.io.IOException) {
                _uiState.update {
                    it.copy(
                        isRequestingPairingCode = false,
                        pairingCodeError = "فشل الاتصال بالخادم. يرجى التحقق من الاتصال بالإنترنت."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRequestingPairingCode = false,
                        pairingCodeError = "حدث خطأ غير متوقع: ${e.localizedMessage ?: "خطأ داخلي"}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWahaPolling()
    }
}
