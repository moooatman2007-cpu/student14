package com.example

import com.example.data.auth.AuthActionContext
import com.example.data.auth.SafeAuthErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeAuthErrorMapperTest {

    @Test
    fun testOverEmailSendRateLimitMapping() {
        val rawMsg = "over_email_send_rate_limit: For security purposes, you can only sign up once every 5 minutes"
        val mapped = SafeAuthErrorMapper.mapAuthErrorToArabic(rawMsg, AuthActionContext.SIGN_UP)
        assertEquals("تم تجاوز عدد محاولات التسجيل، حاول مرة أخرى بعد قليل.", mapped)

        // Test with different casing/spacing
        val rawMsgCased = "Over Email Send Rate Limit"
        val mappedCased = SafeAuthErrorMapper.mapAuthErrorToArabic(rawMsgCased, AuthActionContext.SIGN_UP)
        assertEquals("تم تجاوز عدد محاولات التسجيل، حاول مرة أخرى بعد قليل.", mappedCased)

        // Test with exception
        val throwable = Exception("over_email_send_rate_limit")
        val errorMsg = SafeAuthErrorMapper.getArabicErrorMessage(throwable, AuthActionContext.SIGN_UP)
        assertEquals("تم تجاوز عدد محاولات التسجيل، حاول مرة أخرى بعد قليل.", errorMsg)
    }

    @Test
    fun testEmailNotConfirmedMapping() {
        val rawMsg = "Email not confirmed"
        val mapped = SafeAuthErrorMapper.mapAuthErrorToArabic(rawMsg, AuthActionContext.SIGN_UP)
        assertEquals("لم يتم تأكيد البريد الإلكتروني بعد. يرجى فتح الرابط المرسل إلى بريدك الإلكتروني.", mapped)
    }

    @Test
    fun testInvalidCredentialsMapping() {
        val rawMsg = "invalid login credentials"
        val mapped = SafeAuthErrorMapper.mapAuthErrorToArabic(rawMsg, AuthActionContext.SIGN_IN)
        assertEquals("بيانات الدخول غير صحيحة، يرجى التأكد من البريد وكلمة المرور", mapped)
    }

    @Test
    fun testNoSensitiveDataInMessages() {
        val sensitiveInputs = listOf(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
            "Authorization token failed to parse",
            "https://api.supabase.co/auth/v1/signup error 500",
            "apikey invalid or missing",
            "access_token revoked",
            "refresh_token expired",
            "http://localhost:8000/auth/callback"
        )

        for (input in sensitiveInputs) {
            // Mapping via mapAuthErrorToArabic
            val mappedStr = SafeAuthErrorMapper.mapAuthErrorToArabic(input, AuthActionContext.SIGN_UP)
            
            // The resulting Arabic text must not contain any sensitive keywords
            assertFalse(mappedStr.contains("bearer", ignoreCase = true))
            assertFalse(mappedStr.contains("authorization", ignoreCase = true))
            assertFalse(mappedStr.contains("jwt", ignoreCase = true))
            assertFalse(mappedStr.contains("apikey", ignoreCase = true))
            assertFalse(mappedStr.contains("access_token", ignoreCase = true))
            assertFalse(mappedStr.contains("refresh_token", ignoreCase = true))
            assertFalse(mappedStr.contains("http://", ignoreCase = true))
            assertFalse(mappedStr.contains("https://", ignoreCase = true))

            // Mapping via getArabicErrorMessage with Exception
            val throwable = Exception(input)
            val mappedExc = SafeAuthErrorMapper.getArabicErrorMessage(throwable, AuthActionContext.SIGN_UP)
            
            assertFalse(mappedExc.contains("bearer", ignoreCase = true))
            assertFalse(mappedExc.contains("authorization", ignoreCase = true))
            assertFalse(mappedExc.contains("jwt", ignoreCase = true))
            assertFalse(mappedExc.contains("apikey", ignoreCase = true))
            assertFalse(mappedExc.contains("access_token", ignoreCase = true))
            assertFalse(mappedExc.contains("refresh_token", ignoreCase = true))
            assertFalse(mappedExc.contains("http://", ignoreCase = true))
            assertFalse(mappedExc.contains("https://", ignoreCase = true))
        }
    }

    @Test
    fun testFallbackMessageOnUnknownError() {
        val rawMsg = "Some completely unknown error message 9928"
        val mapped = SafeAuthErrorMapper.mapAuthErrorToArabic(rawMsg, AuthActionContext.SIGN_UP)
        assertEquals("حدث خطأ أثناء إنشاء الحساب، حاول مرة أخرى.", mapped)

        val mappedSignIn = SafeAuthErrorMapper.mapAuthErrorToArabic(rawMsg, AuthActionContext.SIGN_IN)
        assertEquals("حدث خطأ أثناء تسجيل الدخول، حاول مرة أخرى.", mappedSignIn)
    }
}
