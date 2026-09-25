package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AuthActionContext
import com.example.data.auth.AuthFeedbackBus
import com.example.data.auth.AuthFeedbackEvent
import com.example.data.auth.SafeAuthErrorMapper
import com.example.data.auth.SafeAuthLogger
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.Phone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val userId: String, val email: String?) : AuthState
}

data class PasswordRequirements(
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean
) {
    val isValid: Boolean get() = hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecialChar
}

fun checkPasswordRequirements(password: String): PasswordRequirements {
    return PasswordRequirements(
        hasMinLength = password.length >= 8,
        hasUppercase = password.any { it.isUpperCase() },
        hasLowercase = password.any { it.isLowerCase() },
        hasDigit = password.any { it.isDigit() },
        hasSpecialChar = password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }
    )
}

class AuthViewModel : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        checkSession()
        listenToAuthFeedback()
    }

    private fun listenToAuthFeedback() {
        viewModelScope.launch {
            AuthFeedbackBus.events.collect { event ->
                when (event) {
                    is AuthFeedbackEvent.Success -> {
                        _errorMessage.value = null
                        _successMessage.value = event.message
                        _authState.value = AuthState.LoggedOut
                    }
                    is AuthFeedbackEvent.Error -> {
                        _successMessage.value = null
                        _errorMessage.value = event.message
                    }
                }
            }
        }
    }

    fun checkSession() {
        viewModelScope.launch {
            try {
                val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
                val user = SupabaseClientProvider.client.auth.currentUserOrNull()
                if (session != null && user != null) {
                    _authState.value = AuthState.LoggedIn(userId = user.id, email = user.email)
                } else {
                    _authState.value = AuthState.LoggedOut
                }
            } catch (e: Exception) {
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun signOut(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                SupabaseClientProvider.client.auth.signOut()
                _authState.value = AuthState.LoggedOut
                onSuccess()
            } catch (e: Exception) {
                SafeAuthLogger.logAuthError("AuthViewModel", "signOut", e)
                val safeMsg = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.SIGN_OUT)
                _errorMessage.value = safeMsg
                onError(safeMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signIn(identifier: String, passwordInput: String, onResult: (Boolean) -> Unit) {
        if (_isLoading.value) return
        _errorMessage.value = null
        _successMessage.value = null

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                val isEmail = identifier.contains("@")
                if (isEmail) {
                    SupabaseClientProvider.client.auth.signInWith(Email) {
                        email = identifier.trim()
                        password = passwordInput
                    }
                    checkSession()
                    onResult(true)
                } else {
                    try {
                        SupabaseClientProvider.client.auth.signInWith(Phone) {
                            phone = identifier.trim()
                            password = passwordInput
                        }
                        checkSession()
                        onResult(true)
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError("AuthViewModel", "signInPhone", e)
                        _errorMessage.value = "تسجيل الدخول برقم الهاتف يتطلب تفعيل موفّر SMS في لوحة تحكم Supabase. يرجى استخدام البريد الإلكتروني."
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                SafeAuthLogger.logAuthError("AuthViewModel", "signIn", e)
                _errorMessage.value = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.SIGN_IN)
                onResult(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUp(
        fullName: String,
        phone: String,
        emailInput: String,
        passwordInput: String,
        educationalStage: String,
        onSignUpSuccess: () -> Unit
    ) {
        // Prevent duplicate calls if already loading
        if (_isLoading.value) return

        // Clear any previous error/success messages on starting a new attempt
        _errorMessage.value = null
        _successMessage.value = null

        val validStages = listOf("ابتدائي", "إعدادي", "ثانوي")
        val trimmedStage = educationalStage.trim()
        if (!validStages.contains(trimmedStage)) {
            _errorMessage.value = "يرجى اختيار المرحلة التعليمية (ابتدائي، إعدادي، أو ثانوي)."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                // Clear any lingering session from previous accounts before creating a new one
                try {
                    SupabaseClientProvider.client.auth.signOut()
                } catch (_: Exception) {
                    // Ignore sign out error if no active session
                }
                _authState.value = AuthState.LoggedOut

                val trimmedEmail = emailInput.trim()
                SupabaseClientProvider.client.auth.signUpWith(
                    provider = Email,
                    redirectUrl = "studentmanager://login-callback"
                ) {
                    email = trimmedEmail
                    password = passwordInput
                    data = buildJsonObject {
                        put("full_name", fullName.trim())
                        put("phone", phone.trim())
                        put("educational_stage", trimmedStage)
                    }
                }

                // Strictly ensure no auto-login occurs after signup
                try {
                    SupabaseClientProvider.client.auth.signOut()
                } catch (_: Exception) {
                    // Ignore
                }
                _authState.value = AuthState.LoggedOut
                _successMessage.value = "تم إنشاء الحساب بنجاح، يرجى تسجيل الدخول باستخدام البريد الإلكتروني وكلمة المرور."
                onSignUpSuccess()
            } catch (e: Exception) {
                SafeAuthLogger.logAuthError("AuthViewModel", "signUp", e)
                _errorMessage.value = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.SIGN_UP)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPassword(emailInput: String) {
        if (_isLoading.value) return
        _errorMessage.value = null
        _successMessage.value = null

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                SupabaseClientProvider.client.auth.resetPasswordForEmail(
                    email = emailInput.trim(),
                    redirectUrl = "studentmanager://login-callback"
                )
                _successMessage.value = "تم إرسال رابط إعادة ضبط كلمة المرور إلى بريدك الإلكتروني بنجاح"
            } catch (e: Exception) {
                SafeAuthLogger.logAuthError("AuthViewModel", "resetPassword", e)
                _errorMessage.value = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.RESET_PASSWORD)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun mapAuthErrorToArabic(msg: String, context: AuthActionContext = AuthActionContext.SIGN_UP): String {
        return SafeAuthErrorMapper.mapAuthErrorToArabic(msg, context)
    }
}

enum class AuthMode { LOGIN, SIGNUP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onLoginSuccess: () -> Unit
) {
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }

    // Form inputs
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var emailOrIdentifier by rememberSaveable { mutableStateOf("") }
    var educationalStage by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    // Visibility toggles
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    // Reset password dialog state
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmailInput by remember { mutableStateOf("") }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val authState by viewModel.authState.collectAsState()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(successMessage) {
        if (!successMessage.isNullOrBlank()) {
            authMode = AuthMode.LOGIN
        }
    }

    val passwordReqs = remember(password) { checkPasswordRequirements(password) }
    val isPasswordMatching = remember(password, confirmPassword) { password == confirmPassword }

    val isSignupValid = remember(fullName, phone, emailOrIdentifier, educationalStage, passwordReqs, isPasswordMatching, confirmPassword) {
        fullName.isNotBlank() &&
                phone.isNotBlank() &&
                emailOrIdentifier.isNotBlank() &&
                educationalStage in listOf("ابتدائي", "إعدادي", "ثانوي") &&
                passwordReqs.isValid &&
                isPasswordMatching &&
                confirmPassword.isNotEmpty()
    }

    val isLoginValid = remember(emailOrIdentifier, password) {
        emailOrIdentifier.isNotBlank() && password.isNotBlank()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Branding Header
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = "شعار التطبيق",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "منصة المعلم",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "إدارة الحلقات والطلاب بمرونة واحترافية",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Mode Switcher Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (authMode != AuthMode.LOGIN) {
                                                authMode = AuthMode.LOGIN
                                                viewModel.clearMessages()
                                            }
                                        },
                                    color = if (authMode == AuthMode.LOGIN) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "تسجيل الدخول",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (authMode == AuthMode.LOGIN) FontWeight.Bold else FontWeight.Medium,
                                            color = if (authMode == AuthMode.LOGIN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (authMode != AuthMode.SIGNUP) {
                                                authMode = AuthMode.SIGNUP
                                                viewModel.clearMessages()
                                            }
                                        },
                                    color = if (authMode == AuthMode.SIGNUP) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "إنشاء حساب",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (authMode == AuthMode.SIGNUP) FontWeight.Bold else FontWeight.Medium,
                                            color = if (authMode == AuthMode.SIGNUP) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Feedback Banners
                            if (!errorMessage.isNullOrEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = errorMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            if (!successMessage.isNullOrEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFE8F5E9)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = successMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                }
                            }

                            // Form Fields
                            if (authMode == AuthMode.SIGNUP) {
                                // Field 1: Full Name
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("الاسم الكامل") },
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Field 2: Phone Number
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("رقم الهاتف") },
                                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Field 3: Email
                                OutlinedTextField(
                                    value = emailOrIdentifier,
                                    onValueChange = { emailOrIdentifier = it },
                                    label = { Text("البريد الإلكتروني") },
                                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Field 4: Educational Stage (إجباري)
                                var expandedStageMenu by remember { mutableStateOf(false) }
                                val stagesList = listOf("ابتدائي", "إعدادي", "ثانوي")

                                ExposedDropdownMenuBox(
                                    expanded = expandedStageMenu,
                                    onExpandedChange = { expandedStageMenu = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = educationalStage,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("المرحلة التعليمية *") },
                                        placeholder = { Text("اختر المرحلة التعليمية *") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.School,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStageMenu)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                            .testTag("signup_educational_stage_dropdown")
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expandedStageMenu,
                                        onDismissRequest = { expandedStageMenu = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        stagesList.forEach { stageItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stageItem,
                                                        fontWeight = if (educationalStage == stageItem) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    educationalStage = stageItem
                                                    expandedStageMenu = false
                                                },
                                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                                modifier = Modifier.testTag("stage_option_$stageItem")
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Field 4: Password
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("كلمة المرور") },
                                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                                contentDescription = if (isPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Password Requirements Validation Box
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "شروط كلمة المرور:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        PasswordValidationRow("8 أحرف على الأقل", passwordReqs.hasMinLength)
                                        PasswordValidationRow("حرف كبير (A-Z)", passwordReqs.hasUppercase)
                                        PasswordValidationRow("حرف صغير (a-z)", passwordReqs.hasLowercase)
                                        PasswordValidationRow("رقم (0-9)", passwordReqs.hasDigit)
                                        PasswordValidationRow("رمز خاص (!@#$%)", passwordReqs.hasSpecialChar)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Field 5: Confirm Password
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("تأكيد كلمة المرور") },
                                    leadingIcon = { Icon(Icons.Outlined.LockReset, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isConfirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                                contentDescription = if (isConfirmPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (confirmPassword.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isPasswordMatching) Icons.Default.CheckCircle else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (isPasswordMatching) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isPasswordMatching) "كلمة المرور متطابقة ✅" else "كلمة المرور غير متطابقة ❌",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isPasswordMatching) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                            } else {
                                // LOGIN FORM
                                OutlinedTextField(
                                    value = emailOrIdentifier,
                                    onValueChange = { emailOrIdentifier = it },
                                    label = { Text("البريد الإلكتروني أو رقم الهاتف") },
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("كلمة المرور") },
                                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                                contentDescription = if (isPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    TextButton(
                                        onClick = {
                                            resetEmailInput = emailOrIdentifier
                                            showResetDialog = true
                                        }
                                    ) {
                                        Text(
                                            text = "نسيت كلمة المرور؟",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Submit Button
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (authMode == AuthMode.SIGNUP) {
                                        val registeredEmail = emailOrIdentifier.trim()
                                        viewModel.signUp(
                                            fullName = fullName,
                                            phone = phone,
                                            emailInput = registeredEmail,
                                            passwordInput = password,
                                            educationalStage = educationalStage
                                        ) {
                                            // On SignUp success: switch to Login mode, pre-fill email, clear passwords, do NOT go to Home
                                            authMode = AuthMode.LOGIN
                                            emailOrIdentifier = registeredEmail
                                            password = ""
                                            confirmPassword = ""
                                        }
                                    } else {
                                        viewModel.signIn(
                                            identifier = emailOrIdentifier,
                                            passwordInput = password
                                        ) { success ->
                                            if (success) onLoginSuccess()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isLoading && (if (authMode == AuthMode.SIGNUP) isSignupValid else isLoginValid)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        text = if (authMode == AuthMode.SIGNUP) "إنشاء الحساب" else "تسجيل الدخول",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Reset Password Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("استعادة كلمة المرور") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("أدخل بريدك الإلكتروني لإرسال رابط إعادة ضبط كلمة المرور:")
                        OutlinedTextField(
                            value = resetEmailInput,
                            onValueChange = { resetEmailInput = it },
                            label = { Text("البريد الإلكتروني") },
                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetEmailInput.isNotBlank()) {
                                viewModel.resetPassword(resetEmailInput)
                                showResetDialog = false
                            }
                        },
                        enabled = resetEmailInput.isNotBlank() && !isLoading
                    ) {
                        Text("إرسال الرابط")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }

@Composable
private fun PasswordValidationRow(label: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isMet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isMet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
        )
    }
}

