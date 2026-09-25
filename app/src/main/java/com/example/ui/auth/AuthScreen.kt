package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AccountSessionManager
import com.example.data.auth.AuthActionContext
import com.example.data.auth.AuthFeedbackBus
import com.example.data.auth.AuthFeedbackEvent
import com.example.data.auth.SafeAuthErrorMapper
import com.example.data.auth.SafeAuthLogger
import com.example.ui.components.MidarFullLogo
import com.example.ui.theme.MidarBlue
import com.example.ui.theme.MidarBlueLight
import com.example.ui.theme.MidarGreen
import com.example.ui.theme.MidarGreenLight
import com.example.ui.theme.MidarNavy
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
                val result = AccountSessionManager.logout()
                if (result.isSuccess) {
                    _authState.value = AuthState.LoggedOut
                    onSuccess()
                } else {
                    val ex = result.exceptionOrNull() ?: Exception("Sign out failed")
                    SafeAuthLogger.logAuthError("AuthViewModel", "signOut", ex)
                    val safeMsg = SafeAuthErrorMapper.getArabicErrorMessage(ex, AuthActionContext.SIGN_OUT)
                    _errorMessage.value = safeMsg
                    onError(safeMsg)
                }
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
        if (_isLoading.value) return
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
                try {
                    SupabaseClientProvider.client.auth.signOut()
                } catch (_: Exception) {
                    // Ignore
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
}

enum class AuthMode { LOGIN, SIGNUP }

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onLoginSuccess: () -> Unit
) {
    var authMode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }

    // Form inputs
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var emailOrIdentifier by rememberSaveable { mutableStateOf("") }
    var educationalStage by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

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
        if (authMode == AuthMode.LOGIN) {
            ExactLoginScreen(
                identifier = emailOrIdentifier,
                onIdentifierChange = { emailOrIdentifier = it },
                password = password,
                onPasswordChange = { password = it },
                isLoading = isLoading,
                errorMessage = errorMessage,
                successMessage = successMessage,
                isLoginValid = isLoginValid,
                onLogin = {
                    focusManager.clearFocus()
                    viewModel.signIn(emailOrIdentifier, password) { success ->
                        if (success) onLoginSuccess()
                    }
                },
                onSwitchToSignup = {
                    authMode = AuthMode.SIGNUP
                    viewModel.clearMessages()
                },
                onForgotPassword = {
                    resetEmailInput = emailOrIdentifier
                    showResetDialog = true
                }
            )
        } else {
            ExactSignUpScreen(
                fullName = fullName,
                onFullNameChange = { fullName = it },
                phone = phone,
                onPhoneChange = { phone = it },
                email = emailOrIdentifier,
                onEmailChange = { emailOrIdentifier = it },
                password = password,
                onPasswordChange = { password = it },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                educationalStage = educationalStage,
                onEducationalStageChange = { educationalStage = it },
                isLoading = isLoading,
                errorMessage = errorMessage,
                isSignupValid = isSignupValid,
                passwordReqs = passwordReqs,
                isPasswordMatching = isPasswordMatching,
                onBack = {
                    authMode = AuthMode.LOGIN
                    viewModel.clearMessages()
                },
                onSignUp = {
                    focusManager.clearFocus()
                    val registeredEmail = emailOrIdentifier.trim()
                    viewModel.signUp(
                        fullName = fullName,
                        phone = phone,
                        emailInput = registeredEmail,
                        passwordInput = password,
                        educationalStage = educationalStage
                    ) {
                        authMode = AuthMode.LOGIN
                        emailOrIdentifier = registeredEmail
                        password = ""
                        confirmPassword = ""
                    }
                }
            )
        }

        // Reset Password Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("استعادة كلمة المرور", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("أدخل بريدك الإلكتروني لإرسال رابط إعادة ضبط كلمة المرور:")
                        OutlinedTextField(
                            value = resetEmailInput,
                            onValueChange = { resetEmailInput = it },
                            label = { Text("البريد الإلكتروني") },
                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = MidarBlue) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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
                        colors = ButtonDefaults.buttonColors(containerColor = MidarBlue),
                        shape = RoundedCornerShape(12.dp),
                        enabled = resetEmailInput.isNotBlank() && !isLoading
                    ) {
                        Text("إرسال الرابط", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

/**
 * Exact reproduction of Reference Image 1 & 3: MIDAR Login Screen
 */
@Composable
private fun ExactLoginScreen(
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    isLoginValid: Boolean,
    onLogin: () -> Unit,
    onSwitchToSignup: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07142E),
                        Color(0xFF091C3E),
                        Color(0xFF0C244F),
                        Color(0xFF07132B)
                    )
                )
            )
    ) {
        // Decorative Desk Art in Header Background
        DecorativeDeskArt(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // MIDAR Official Logo Header
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MidarFullLogo(
                    isDarkTheme = true,
                    iconSize = 88.dp,
                    showTagline = true,
                    taglineText = "كل طلابك في مكان واحد"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dark Floating Card (Curved top 32.dp, floating layout matching screenshot exactly)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF0A1831).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B2E4E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon Badge [👤]
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF13233F),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF3B82F6).copy(alpha = 0.6f)),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.ManageAccounts,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title: مرحباً بك مجدداً
                    Text(
                        text = "مرحباً بك مجدداً",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        text = "سجّل دخولك للبدء في إدارة طلابك بسهولة",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Feedback Messages
                    if (!errorMessage.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEE2E2).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFFCA5A5),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (!successMessage.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE6F9F3).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = successMessage,
                                color = Color(0xFFA7F3D0),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Field 1: Email / Phone
                    MidarReferenceInputField(
                        value = identifier,
                        onValueChange = onIdentifierChange,
                        placeholder = "البريد الإلكتروني أو رقم الهاتف",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.MailOutline,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        isDark = true,
                        modifier = Modifier.testTag("login_identifier_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Field 2: Password
                    MidarReferenceInputField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = "كلمة المرور",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        isDark = true,
                        modifier = Modifier.testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Forgot Password Link -> Right-aligned (Left in RTL layout) with arrow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, // <- in RTL points left
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "نسيت كلمة المرور؟",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF3B82F6)
                            ),
                            modifier = Modifier
                                .clickable { onForgotPassword() }
                                .padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Button: تسجيل الدخول ->
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF1E60FF).copy(alpha = 0.3f))
                            .testTag("login_submit_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E60FF),
                            contentColor = Color.White
                        ),
                        enabled = !isLoading && isLoginValid
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "تسجيل الدخول",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider: أو
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF1E2E4A)
                        )
                        Text(
                            text = "أو",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF1E2E4A)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Secondary Outlined Button: إنشاء حساب جديد 👤+ (White outlined matching screenshot)
                    OutlinedButton(
                        onClick = onSwitchToSignup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("switch_to_signup_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "إنشاء حساب جديد",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // Bottom Trust Badge: 🛡️ بياناتك في أمان معنا
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(1.dp)
                                .background(Color(0xFF1E2E4A))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = Color(0xFF00C48C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "بياناتك في أمان معنا",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(1.dp)
                                .background(Color(0xFF1E2E4A))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Tagline Centered Outside the Card (Centering footer)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "إدارة طلابك. أبسط. أسرع. أذكى.",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/**
 * Exact reproduction of Reference Image 2: MIDAR Sign Up Screen
 */
@Composable
private fun ExactSignUpScreen(
    fullName: String,
    onFullNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    educationalStage: String,
    onEducationalStageChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    isSignupValid: Boolean,
    passwordReqs: PasswordRequirements,
    isPasswordMatching: Boolean,
    onBack: () -> Unit,
    onSignUp: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var expandedStageMenu by remember { mutableStateOf(false) }
    val stagesList = listOf("ابتدائي", "إعدادي", "ثانوي")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07142E),
                        Color(0xFF091C3E),
                        Color(0xFF0C244F),
                        Color(0xFF07132B)
                    )
                )
            )
    ) {
        // Decorative Desk Art in Header Background
        DecorativeDeskArt(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Only Back Button (No clock 9:41, matching user request)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "الرجوع",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MIDAR Brand Logo on Dark Background
            MidarFullLogo(
                isDarkTheme = true,
                iconSize = 82.dp,
                showTagline = true,
                taglineText = "كل طلابك في مكان واحد"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Dark Floating Card (Curved 32.dp, matches Login screen styling)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF0A1831).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B2E4E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Main Title: إنشاء حساب جديد
                    Text(
                        text = "إنشاء حساب جديد",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle
                    Text(
                        text = "ابدأ رحلتك مع مدار بسهولة، فقط أكمل البيانات التالية",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (!errorMessage.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEE2E2).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFFCA5A5),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 1. Full Name
                    ReferenceFieldLabel(label = "الاسم الكامل", icon = Icons.Outlined.Person, isDark = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    MidarReferenceInputField(
                        value = fullName,
                        onValueChange = onFullNameChange,
                        placeholder = "أدخل اسمك الكامل",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        isDark = true,
                        modifier = Modifier.testTag("signup_fullname_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Phone Number
                    ReferenceFieldLabel(label = "رقم الهاتف", icon = Icons.Outlined.Phone, isDark = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    MidarReferenceInputField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        placeholder = "01xxxxxxxxx",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        isDark = true,
                        modifier = Modifier.testTag("signup_phone_input")
                    )
                    Text(
                        text = "سيتم استخدامه للتواصل معك",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF94A3B8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 4.dp),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Email
                    ReferenceFieldLabel(label = "البريد الإلكتروني", icon = Icons.Outlined.Email, isDark = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    MidarReferenceInputField(
                        value = email,
                        onValueChange = onEmailChange,
                        placeholder = "example@email.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        isDark = true,
                        modifier = Modifier.testTag("signup_email_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Password
                    ReferenceFieldLabel(label = "كلمة المرور", icon = Icons.Outlined.Lock, isDark = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    MidarReferenceInputField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = "أدخل كلمة المرور",
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        isDark = true,
                        modifier = Modifier.testTag("signup_password_input")
                    )
                    Text(
                        text = "يجب أن تحتوي على 8 أحرف على الأقل",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF94A3B8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 4.dp),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Confirm Password
                    ReferenceFieldLabel(label = "تأكيد كلمة المرور", icon = Icons.Outlined.Lock, isDark = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    MidarReferenceInputField(
                        value = confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        placeholder = "أعد إدخال كلمة المرور",
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        isDark = true,
                        modifier = Modifier.testTag("signup_confirm_password_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. Educational Stage Dropdown (Selector)
                    ReferenceFieldLabel(label = "المرحلة التعليمية", icon = Icons.Outlined.School, isDark = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF233B6E), RoundedCornerShape(16.dp))
                                .clickable { expandedStageMenu = true },
                            color = Color(0xFF0F1E3A).copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (educationalStage.isNotBlank()) educationalStage else "اختر المرحلة التعليمية",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = if (educationalStage.isNotBlank()) Color.White else Color(0xFF94A3B8)
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedStageMenu,
                            onDismissRequest = { expandedStageMenu = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(Color(0xFF0F1E3A))
                        ) {
                            stagesList.forEach { stage ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stage,
                                            style = TextStyle(
                                                fontSize = 14.sp,
                                                fontWeight = if (educationalStage == stage) FontWeight.Bold else FontWeight.Normal,
                                                color = if (educationalStage == stage) Color(0xFF3B82F6) else Color.White
                                            )
                                        )
                                    },
                                    onClick = {
                                        onEducationalStageChange(stage)
                                        expandedStageMenu = false
                                    },
                                    modifier = Modifier.testTag("stage_option_$stage")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Sign Up Submit Button: إنشاء الحساب 👤+
                    Button(
                        onClick = onSignUp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF3B82F6).copy(alpha = 0.3f))
                            .testTag("signup_submit_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White
                        ),
                        enabled = !isLoading && isSignupValid
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "إنشاء الحساب",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Outlined.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Terms & Privacy Note
                    Text(
                        text = "بالضغط على إنشاء حساب، فإنك توافق على\nسياسة الخصوصية وشروط الاستخدام",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Wave and Slogan Pill: 🚀 معاً نحو تعليم أفضل
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Subtle pastel cyan-green wave
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val wavePath = Path().apply {
                        moveTo(0f, h * 0.5f)
                        cubicTo(w * 0.3f, h * 0.1f, w * 0.7f, h * 0.9f, w, h * 0.5f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        wavePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF07142E).copy(alpha = 0.5f),
                                Color(0xFF0C244F).copy(alpha = 0.5f)
                            )
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F1E3A).copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF233B6E))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🚀 معاً نحو تعليم أفضل",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Single-Line Pill-Shaped Input Field matching the reference style exactly
 */
@Composable
private fun MidarReferenceInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isDark: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF0F1E3A).copy(alpha = 0.5f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF233B6E) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = if (isDark) Color.White else Color(0xFF0B1B3D),
                        fontWeight = FontWeight.Medium
                    ),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(if (isDark) Color(0xFF3B82F6) else Color(0xFF1E60FF)),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

/**
 * Top Field Label with right-aligned icon and title matching Reference 2
 */
@Composable
private fun ReferenceFieldLabel(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0B1B3D)
            )
        )
    }
}

/**
 * Decorative Minimalist Vector Desk Art with books and pen holder matching the background header
 */
@Composable
private fun DecorativeDeskArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle gradient glow behind logo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E60FF).copy(alpha = 0.25f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.4f
            ),
            radius = w * 0.4f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Books Stack on the right side
        val bookRightX = w * 0.82f
        val bookBaseY = h * 0.85f

        // Bottom Book (Dark Blue)
        drawRoundRect(
            color = Color(0xFF123473).copy(alpha = 0.35f),
            topLeft = Offset(bookRightX - 45.dp.toPx(), bookBaseY),
            size = Size(90.dp.toPx(), 14.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        // Middle Book (Cyan Accent)
        drawRoundRect(
            color = Color(0xFF2CB9F8).copy(alpha = 0.3f),
            topLeft = Offset(bookRightX - 40.dp.toPx(), bookBaseY - 14.dp.toPx()),
            size = Size(80.dp.toPx(), 12.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        // Top Book
        drawRoundRect(
            color = Color(0xFF1E60FF).copy(alpha = 0.35f),
            topLeft = Offset(bookRightX - 35.dp.toPx(), bookBaseY - 26.dp.toPx()),
            size = Size(70.dp.toPx(), 10.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Pen Holder Cylinder
        drawRoundRect(
            color = Color(0xFF0F2B61).copy(alpha = 0.4f),
            topLeft = Offset(bookRightX + 20.dp.toPx(), bookBaseY - 28.dp.toPx()),
            size = Size(20.dp.toPx(), 36.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Pencils sticking out
        drawLine(
            color = Color(0xFF2CB9F8).copy(alpha = 0.5f),
            start = Offset(bookRightX + 24.dp.toPx(), bookBaseY - 28.dp.toPx()),
            end = Offset(bookRightX + 22.dp.toPx(), bookBaseY - 45.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF00E59D).copy(alpha = 0.5f),
            start = Offset(bookRightX + 32.dp.toPx(), bookBaseY - 28.dp.toPx()),
            end = Offset(bookRightX + 34.dp.toPx(), bookBaseY - 48.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
