package com.example.data.auth

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException

enum class AuthActionContext {
    SIGN_UP,
    SIGN_IN,
    RESET_PASSWORD,
    EMAIL_CONFIRM,
    SIGN_OUT,
    GENERAL
}

object SafeAuthErrorMapper {

    val SENSITIVE_KEYWORDS = listOf(
        "authorization",
        "bearer",
        "jwt",
        "apikey",
        "access_token",
        "refresh_token",
        "http://",
        "https://"
    )

    /**
     * Converts any Throwable encountered during Supabase auth operations
     * into a safe, user-friendly, localized Arabic message.
     * Guaranteed to never leak URLs, headers, tokens, or raw exceptions.
     */
    fun getArabicErrorMessage(
        throwable: Throwable?,
        context: AuthActionContext = AuthActionContext.SIGN_UP
    ): String {
        if (throwable == null) {
            return getDefaultFallbackMessage(context)
        }

        // 1. Check if throwable is AuthRestException with structured AuthErrorCode
        if (throwable is AuthRestException) {
            val fromCode = mapAuthErrorCodeToArabic(throwable.errorCode, context)
            if (fromCode != null) {
                return sanitizeOutput(fromCode, context)
            }
            val fromDesc = mapRawStringToSafeArabic(throwable.errorDescription, context)
            if (fromDesc != null) {
                return sanitizeOutput(fromDesc, context)
            }
        }

        // 2. Check candidate message strings safely
        val rawCandidates = listOfNotNull(
            throwable.message,
            throwable.localizedMessage,
            throwable.cause?.message
        )

        for (candidate in rawCandidates) {
            val mapped = mapRawStringToSafeArabic(candidate, context)
            if (mapped != null) {
                return sanitizeOutput(mapped, context)
            }
        }

        // 3. Fallback to context-specific safe Arabic message
        return getDefaultFallbackMessage(context)
    }

    /**
     * Maps an unparsed or raw error string safely to Arabic without ever echoing raw text.
     */
    fun mapAuthErrorToArabic(
        rawMessage: String?,
        context: AuthActionContext = AuthActionContext.SIGN_UP
    ): String {
        if (rawMessage.isNullOrBlank()) {
            return getDefaultFallbackMessage(context)
        }

        val mapped = mapRawStringToSafeArabic(rawMessage, context)
        return sanitizeOutput(mapped ?: getDefaultFallbackMessage(context), context)
    }

    private fun mapAuthErrorCodeToArabic(
        errorCode: AuthErrorCode?,
        context: AuthActionContext
    ): String? {
        if (errorCode == null) return null
        return when (errorCode) {
            AuthErrorCode.OverEmailSendRateLimit ->
                "تم تجاوز عدد محاولات التسجيل، حاول مرة أخرى بعد قليل."
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverSmsSendRateLimit ->
                "تم تجاوز الحد المسموح من الطلبات، يرجى المحاولة بعد قليل."
            AuthErrorCode.EmailNotConfirmed ->
                "لم يتم تأكيد البريد الإلكتروني بعد. يرجى فتح الرابط المرسل إلى بريدك الإلكتروني."
            AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists ->
                "البريد الإلكتروني مُسجل بالفعل"
            AuthErrorCode.InvalidCredentials ->
                "بيانات الدخول غير صحيحة، يرجى التأكد من البريد وكلمة المرور"
            AuthErrorCode.WeakPassword, AuthErrorCode.SamePassword ->
                "كلمة المرور ضعيفة أو قصيرة جداً"
            AuthErrorCode.ValidationFailed ->
                "صيغة البيانات المدخلة غير صحيحة"
            AuthErrorCode.SignupDisabled ->
                "إنشاء الحسابات غير متاح حالياً"
            AuthErrorCode.UserBanned ->
                "هذا الحساب معطل، يرجى التواصل مع الإدارة"
            AuthErrorCode.OtpExpired ->
                "انتهت صلاحية رمز التأكيد، يرجى طلب رابط جديد"
            AuthErrorCode.RequestTimeout, AuthErrorCode.HookTimeout ->
                "انتهت مهلة الطلب، يرجى المحاولة مرة أخرى."
            else -> null
        }
    }

    private fun mapRawStringToSafeArabic(
        raw: String,
        context: AuthActionContext
    ): String? {
        val lower = raw.lowercase()

        return when {
            // Over email send rate limit (Requirement 3: "تم تجاوز عدد محاولات التسجيل، حاول مرة أخرى بعد قليل.")
            lower.contains("over_email_send_rate_limit") ||
            (lower.contains("email") && lower.contains("rate limit")) ||
            lower.contains("over_request_rate_limit") ||
            (lower.contains("too many requests") && context == AuthActionContext.SIGN_UP) ->
                "تم تجاوز عدد محاولات التسجيل، حاول مرة أخرى بعد قليل."

            lower.contains("too many requests") ->
                "تم تجاوز الحد المسموح من الطلبات، يرجى المحاولة بعد قليل."

            // Email not confirmed
            lower.contains("email_not_confirmed") ||
            lower.contains("email not confirmed") ->
                "لم يتم تأكيد البريد الإلكتروني بعد. يرجى فتح الرابط المرسل إلى بريدك الإلكتروني."

            // Invalid credentials
            lower.contains("invalid login credentials") ||
            lower.contains("invalid_credentials") ||
            lower.contains("invalid password") ->
                "بيانات الدخول غير صحيحة، يرجى التأكد من البريد وكلمة المرور"

            // Already registered
            lower.contains("user already registered") ||
            lower.contains("user_already_exists") ||
            lower.contains("email_exists") ||
            lower.contains("already registered") ->
                "البريد الإلكتروني مُسجل بالفعل"

            // Weak password
            lower.contains("password should be at least") ||
            lower.contains("weak_password") ||
            lower.contains("password is too short") ->
                "كلمة المرور ضعيفة أو قصيرة جداً"

            // Validation / Invalid format
            lower.contains("invalid format") ||
            lower.contains("validation_failed") ||
            lower.contains("unable to validate email") ||
            lower.contains("invalid email") ->
                "صيغة البيانات المدخلة غير صحيحة"

            // OTP expired
            lower.contains("otp_expired") ||
            lower.contains("token has expired") ||
            (lower.contains("expired") && context == AuthActionContext.EMAIL_CONFIRM) ->
                "انتهت صلاحية رمز التأكيد، يرجى طلب رابط جديد"

            // Access denied
            lower.contains("access_denied") ->
                "تم رفض طلب التحقق من البريد الإلكتروني"

            // Signup disabled
            lower.contains("signup_disabled") ||
            lower.contains("signups not allowed") ->
                "إنشاء الحسابات غير متاح حالياً"

            // User banned
            lower.contains("user_banned") ->
                "هذا الحساب معطل، يرجى التواصل مع الإدارة"

            // Network / connectivity issues
            lower.contains("connectexception") ||
            lower.contains("unknownhostexception") ||
            lower.contains("sockettimeoutexception") ||
            lower.contains("failed to connect") ||
            lower.contains("network error") ||
            lower.contains("no internet") ||
            lower.contains("connection refused") ->
                "تعذر الاتصال بالخادم، يرجى التحقق من اتصال الإنترنت والمحاولة مرة أخرى."

            else -> null
        }
    }

    /**
     * Default safe fallback messages tailored per action context.
     * Never returns raw English, exceptions, or technical details.
     */
    fun getDefaultFallbackMessage(context: AuthActionContext): String {
        return when (context) {
            AuthActionContext.SIGN_UP -> "حدث خطأ أثناء إنشاء الحساب، حاول مرة أخرى."
            AuthActionContext.SIGN_IN -> "حدث خطأ أثناء تسجيل الدخول، حاول مرة أخرى."
            AuthActionContext.RESET_PASSWORD -> "حدث خطأ أثناء إرسال رابط إعادة الضبط، حاول مرة أخرى."
            AuthActionContext.EMAIL_CONFIRM -> "فشل التحقق من البريد الإلكتروني، حاول مرة أخرى."
            AuthActionContext.SIGN_OUT -> "حدث خطأ أثناء تسجيل الخروج، حاول مرة أخرى."
            AuthActionContext.GENERAL -> "حدث خطأ، يرجى المحاولة مرة أخرى لاحقاً."
        }
    }

    /**
     * Strict safety gate: ensures the text shown to the user contains NO technical or sensitive tokens.
     */
    fun sanitizeOutput(message: String, context: AuthActionContext): String {
        val lower = message.lowercase()
        for (keyword in SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return getDefaultFallbackMessage(context)
            }
        }
        return message
    }

    /**
     * Check if a text contains any sensitive tokens.
     */
    fun containsSensitiveData(text: String): Boolean {
        val lower = text.lowercase()
        return SENSITIVE_KEYWORDS.any { lower.contains(it) }
    }
}

object SafeAuthLogger {
    /**
     * Logs non-sensitive auth error metadata only in debug builds.
     * Strictly avoids logging headers, tokens, URLs, passwords, or full stack traces.
     */
    fun logAuthError(tag: String, action: String, throwable: Throwable?) {
        if (!BuildConfig.DEBUG || throwable == null) return
        val errorType = throwable.javaClass.simpleName
        val errorCode = if (throwable is AuthRestException) {
            throwable.errorCode?.value
        } else {
            extractSafeErrorCode(throwable.message)
        }
        Log.w(tag, "[$action] Auth error encountered. Type: $errorType, Code: ${errorCode ?: "UNKNOWN"}")
    }

    private fun extractSafeErrorCode(message: String?): String? {
        if (message == null) return null
        val regex = Regex("""["']?(code|error)["']?\s*:\s*["']?([a-zA-Z0-9_-]+)["']?""")
        val match = regex.find(message)
        return match?.groupValues?.getOrNull(2)
    }
}
