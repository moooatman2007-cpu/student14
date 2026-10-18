package com.example.data.repository

import com.example.core.model.AbsenceNotificationEventInsertDto
import com.example.core.model.ExamNotificationEventInsertDto
import com.example.core.model.HomeworkNotificationEventInsertDto
import com.example.core.model.MonthlyReportNotificationEventInsertDto
import com.example.core.model.NotificationEvent
import com.example.core.model.RecitationNotificationEventInsertDto
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Production implementation of NotificationEventRepository communicating directly
 * with Supabase PostgREST for inserting PENDING events.
 */
class SupabaseNotificationEventRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : NotificationEventRepository {

    override suspend fun createNotificationEvent(
        event: NotificationEvent
    ): NotificationEventCreationResult = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
        val teacherId = SupabaseClientProvider.mockTeacherId ?: user?.id
            ?: return@withContext NotificationEventCreationResult.Failed(
                error = IllegalStateException("انتهت الجلسة، يرجى تسجيل الدخول أولاً."),
                message = "انتهت الجلسة، يرجى تسجيل الدخول أولاً."
            )

        val targetTable = event.eventType.tableName

        try {
            when (event) {
                is NotificationEvent.Absence -> {
                    val dto = AbsenceNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        attendanceId = event.attendanceId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.Recitation -> {
                    val dto = RecitationNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        recitationId = event.recitationId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.Homework -> {
                    val dto = HomeworkNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        homeworkId = event.homeworkId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.Exam -> {
                    val dto = ExamNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        examId = event.examId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
                is NotificationEvent.MonthlyReport -> {
                    val dto = MonthlyReportNotificationEventInsertDto(
                        id = event.id,
                        teacherId = teacherId,
                        studentId = event.studentId,
                        monthlyReportId = event.monthlyReportId,
                        status = "PENDING",
                        attempts = 0
                    )
                    client.postgrest[targetTable].insert(dto)
                }
            }

            NotificationEventCreationResult.Created(event)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isDuplicate = msg.contains("23505", ignoreCase = true) ||
                    msg.contains("duplicate key", ignoreCase = true) ||
                    msg.contains("unique constraint", ignoreCase = true) ||
                    msg.contains("already exists", ignoreCase = true) ||
                    msg.contains("409", ignoreCase = true)

            if (isDuplicate) {
                NotificationEventCreationResult.AlreadyExists(
                    sourceId = event.sourceId,
                    eventType = event.eventType
                )
            } else {
                NotificationEventCreationResult.Failed(
                    error = e,
                    message = "فشل إنشاء حدث الإشعار: ${e.message ?: "خطأ غير معروف"}"
                )
            }
        }
    }

    override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult = withContext(Dispatchers.IO) {
        val workerUrl = SupabaseClientProvider.getEdgeFunctionUrl("studentmanager-notification-worker")
        if (workerUrl.isBlank() || workerUrl.contains("placeholder")) {
            return@withContext NotificationWorkerResult.Skipped("بيئة اختبار أو رابط غير مهيأ")
        }

        val token = try {
            client.auth.currentAccessTokenOrNull()
        } catch (_: Exception) {
            null
        }

        if (token.isNullOrBlank()) {
            val mockId = SupabaseClientProvider.mockTeacherId
            return@withContext if (mockId != null && com.example.BuildConfig.DEBUG) {
                NotificationWorkerResult.Skipped("جلسة تجريبية محلية (Mock Teacher) دون جلسة Supabase Auth فعلية")
            } else {
                NotificationWorkerResult.Error(
                    IllegalStateException("لم يتم العثور على رمز تسجيل دخول صالح للمعلم"),
                    "انتهت جلسة تسجيل الدخول أو لا يوجد رمز تفويض صالح؛ يرجى إعادة تسجيل الدخول لتشغيل الإشعارات."
                )
            }
        }

        val currentUserId = client.auth.currentUserOrNull()?.id ?: "null"
        android.util.Log.d("NotificationWorker", "Current UserID: $currentUserId")

        try {
            val payloadObj = JSONObject().apply {
                if (!eventType.isNullOrBlank()) {
                    put("event_type", eventType.trim())
                }
            }
            val requestBody = payloadObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val requestBuilder = Request.Builder()
                .url(workerUrl)
                .post(requestBody)
                .header("Authorization", "Bearer $token")

            workerHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(bodyStr)
                        val ok = json.optBoolean("ok", false)
                        if (!ok) {
                            val errMessage = json.optString("error", "خطأ غير معروف في المعالج")
                            return@withContext NotificationWorkerResult.Error(
                                IOException(errMessage),
                                "فشل معالج الإشعارات: $errMessage"
                            )
                        }

                        val summary = json.optJSONObject("summary")
                        if (summary == null) {
                            return@withContext NotificationWorkerResult.Error(
                                IOException("استجابة معالج الإشعارات مفقودة (summary مفقود)"),
                                "استجابة غير مكتملة من معالج الإشعارات (ملخص التنفيذ مفقود)"
                            )
                        }

                        val eventTypes = arrayOf("ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT")
                        var hasFailure = false
                        val failureDetails = mutableListOf<String>()

                        for (type in eventTypes) {
                            val typeObj = summary.optJSONObject(type)
                            if (typeObj != null) {
                                val typeErr = typeObj.optString("error", "")
                                if (typeErr.isNotBlank()) {
                                    hasFailure = true
                                    failureDetails.add("$type: $typeErr")
                                }
                                val processedArray = typeObj.optJSONArray("processed")
                                if (processedArray != null) {
                                    for (i in 0 until processedArray.length()) {
                                        val item = processedArray.optJSONObject(i)
                                        val outcome = item?.optJSONObject("outcome")
                                        if (outcome != null) {
                                            val success = outcome.optBoolean("success", false)
                                            if (!success) {
                                                hasFailure = true
                                                val err = outcome.optString("error", "فشل إرسال الإشعار")
                                                failureDetails.add("$type item: $err")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (hasFailure) {
                            val detailMsg = failureDetails.joinToString("; ")
                            return@withContext NotificationWorkerResult.Error(
                                IOException("فشل إرسال بعض رسائل واتساب: $detailMsg"),
                                "تم تشغيل المعالج ولكن تعذر إرسال بعض رسائل واتساب: $detailMsg"
                            )
                        }

                        NotificationWorkerResult.Success("تم تشغيل معالج الإشعارات بنجاح", bodyStr)
                    } catch (e: Exception) {
                        return@withContext NotificationWorkerResult.Error(
                            e,
                            "فشل تحليل استجابة معالج الإشعارات: ${e.message ?: "خطأ غير معروف"}"
                        )
                    }
                } else {
                    NotificationWorkerResult.Error(
                        IOException("HTTP ${response.code}: $bodyStr"),
                        "فشل تشغيل معالج الإشعارات (رمز الخطأ: ${response.code})"
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            NotificationWorkerResult.Error(e, "انتهت مهلة الاتصال بمعالج الإشعارات (تجاوزت المهلة 130 ثانية)")
        } catch (e: java.net.UnknownHostException) {
            NotificationWorkerResult.Error(e, "تعذر الوصول لخادم الإشعارات؛ يرجى التحقق من اتصال الإنترنت")
        } catch (e: IOException) {
            NotificationWorkerResult.Error(e, "خطأ في اتصال الشبكة أثناء استدعاء معالج الإشعارات: ${e.message ?: "خطأ غير معروف"}")
        } catch (e: Exception) {
            NotificationWorkerResult.Error(e, "تعذر الاتصال بمعالج الإشعارات: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    companion object {
        private val workerHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(130, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
