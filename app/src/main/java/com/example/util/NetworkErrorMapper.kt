package com.example.util

import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

fun Throwable.toArabicErrorMessage(): String {
    val msg = this.message ?: ""
    val localized = this.localizedMessage ?: ""
    val fullText = "$msg $localized".lowercase()

    return when {
        this is UnknownHostException || this is ConnectException ||
                fullText.contains("unable to resolve host") ||
                fullText.contains("failed to connect") ->
            "تعذر الاتصال بالشبكة، يرجى التحقق من اتصال الإنترنت والمحاولة مرة أخرى."

        this is TimeoutException || fullText.contains("timeout") || fullText.contains("timed out") ->
            "استغرقت العملية وقتًا أطول من المتوقع، يرجى إعادة المحاولة."

        fullText.contains("invalid login credentials") ->
            "اسم المستخدم أو كلمة المرور غير صحيحة."

        fullText.contains("user already registered") ->
            "هذا البريد الإلكتروني مسجل بالفعل."

        else ->
            if (localized.isNotBlank() && !localized.contains("Exception") && !localized.contains("http") && !localized.contains("io.ktor")) {
                localized
            } else {
                "حدث خطأ في الاتصال، يرجى التحقق من الشبكة والمحاولة لاحقًا."
            }
    }
}
