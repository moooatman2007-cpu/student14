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
    val selectedPairingTab: String = "QR", // "QR" or "CODE"
    val pairingPhoneNumber: String = "",
    val pairingCode: String? = null,
    val isRequestingPairingCode: Boolean = false,
    val pairingCodeError: String? = null
)

class MoreViewModel(
    private val settingsRepository: SettingsRepository = RepositoryProvider.settingsRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
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

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            teacherRepository.fetchCurrentTeacher()
            (gradeRepository as? com.example.data.repository.SupabaseGradeRepository)?.fetchGrades()
        }

        viewModelScope.launch {
            combine(
                teacherRepository.getCurrentTeacher(),
                settingsRepository.getThemeMode(),
                gradeRepository.getGrades()
            ) { teacher, theme, grades ->
                val name = teacher?.fullName?.ifBlank { null } ?: teacher?.email ?: ""
                _uiState.value.copy(
                    teacherName = name,
                    avatarUrl = teacher?.avatarUrl,
                    themeMode = theme,
                    grades = grades.sortedBy { it.displayOrder }
                )
            }.collect { newState ->
                _uiState.value = newState
            }
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
            try {
                SupabaseClientProvider.client.auth.signOut()
                _uiState.update { it.copy(isLoggingOut = false, showLogoutDialog = false) }
                onSuccess()
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "حدث خطأ أثناء تسجيل الخروج، يرجى المحاولة مرة أخرى."
                _uiState.update { it.copy(isLoggingOut = false, logoutError = errorMsg) }
            }
        }
    }

    fun testWahaConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingWaha = true, wahaTestResult = null) }
            try {
                val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

                val jsonObject = JSONObject()
                jsonObject.put("action", "TEST_WAHA_CONNECTION")
                val bodyString = jsonObject.toString()

                val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val requestBuilder = Request.Builder()
                    .url("https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session")
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }

                response.use { res ->
                    val responseStr = res.body?.string() ?: ""
                    val resJson = JSONObject(responseStr)

                    val reachable = resJson.optBoolean("reachable", false)
                    val statusVal = if (resJson.has("status") && !resJson.isNull("status")) resJson.optInt("status") else null
                    val latencyMs = resJson.optLong("latency_ms", 0)
                    val success = resJson.optBoolean("success", false)

                    val statusText = statusVal?.toString() ?: "null"
                    val resultText = "reachable: $reachable\nstatus: $statusText\nlatency_ms: ${latencyMs}ms\nsuccess: $success"

                    _uiState.update {
                        it.copy(isTestingWaha = false, wahaTestResult = resultText)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingWaha = false,
                        wahaTestResult = "reachable: false\nstatus: null\nlatency_ms: 0ms\nsuccess: false"
                    )
                }
            }
        }
    }

    fun dismissWahaTestResult() {
        _uiState.update { it.copy(wahaTestResult = null, isTestingWaha = false) }
    }

    fun openWahaPairingDialog() {
        _uiState.update {
            it.copy(
                showWahaPairingDialog = true,
                wahaPairingError = null,
                wahaSessionStatus = null,
                wahaQrBase64 = null,
                wahaConnectedPhone = null,
                selectedPairingTab = "QR",
                pairingPhoneNumber = "",
                pairingCode = null,
                isRequestingPairingCode = false,
                pairingCodeError = null
            )
        }
        startWaha()
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
                    .url("https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session")
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(requestBuilder.build()).execute()
                }

                response.use { res ->
                    if (!res.isSuccessful) {
                        _uiState.update {
                            it.copy(
                                isStartingWaha = false,
                                wahaPairingError = "تعذر بدء ربط واتساب. الرجاء المحاولة لاحقاً."
                            )
                        }
                        return@launch
                    }

                    val responseStr = res.body?.string() ?: ""
                    val resJson = JSONObject(responseStr)

                    val success = resJson.optBoolean("success", false)
                    if (!success) {
                        val msg = resJson.optString("message", "تعذر الاتصال بخدمة واتساب")
                        _uiState.update {
                            it.copy(
                                isStartingWaha = false,
                                wahaPairingError = msg
                            )
                        }
                        return@launch
                    }

                    val status = resJson.optString("status", "")
                    val connectedPhone = resJson.optString("connected_phone", "")
                    val qrObj = resJson.optJSONObject("qr")
                    val qrData = qrObj?.optString("data", "")

                    _uiState.update {
                        it.copy(
                            isStartingWaha = false,
                            wahaSessionStatus = status,
                            wahaConnectedPhone = if (connectedPhone.isNotBlank()) connectedPhone else null,
                            wahaQrBase64 = if (!qrData.isNullOrBlank()) qrData else null
                        )
                    }

                    if (status == "SCAN_QR_CODE") {
                        startWahaPolling()
                    } else if (status == "CONNECTED" || status == "WORKING") {
                        stopWahaPolling()
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStartingWaha = false,
                        wahaPairingError = "حدث خطأ غير متوقع أثناء ربط واتساب."
                    )
                }
            }
        }
    }

    fun startWahaPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(4000)
                pollWahaStatus()
            }
        }
    }

    fun stopWahaPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollWahaStatus() {
        try {
            val token = SupabaseClientProvider.client.auth.currentAccessTokenOrNull()

            val jsonObject = JSONObject()
            jsonObject.put("action", "START")
            val bodyString = jsonObject.toString()

            val requestBody = bodyString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val requestBuilder = Request.Builder()
                .url("https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session")
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
                        val status = resJson.optString("status", "")
                        val connectedPhone = resJson.optString("connected_phone", "")
                        val qrObj = resJson.optJSONObject("qr")
                        val qrData = qrObj?.optString("data", "")

                        _uiState.update {
                            it.copy(
                                wahaSessionStatus = status,
                                wahaConnectedPhone = if (connectedPhone.isNotBlank()) connectedPhone else null,
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
            // Ignore polling errors gracefully
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
                    .url("https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session")
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(Dispatchers.IO) {
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
                    .url("https://oknpfsvmopdsgsfbbdcs.supabase.co/functions/v1/waha-session")
                    .post(requestBody)

                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val response = withContext(Dispatchers.IO) {
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

                    val code = resJson.optString("code", "")
                    _uiState.update {
                        it.copy(
                            isRequestingPairingCode = false,
                            pairingCode = code,
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
