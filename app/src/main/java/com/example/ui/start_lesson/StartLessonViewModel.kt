package com.example.ui.start_lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.BatchSyncStatus
import com.example.core.model.Grade
import com.example.core.model.HomeworkStatus
import com.example.core.model.NotificationEvent
import com.example.core.model.NotificationEventType
import com.example.core.model.Student
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.HomeworkRepository
import com.example.data.repository.GradeRepository
import com.example.data.repository.NotificationEventCreationResult
import com.example.data.repository.NotificationEventRepository
import com.example.data.repository.PaymentRepository
import com.example.data.repository.RecitationRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.StudentRepository
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseStudentRepository
import com.example.ui.attendance.ScanFeedback
import com.example.ui.attendance.ScanResultType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class StartLessonStep {
    SELECT_GROUP,
    ATTENDANCE,
    RECITATION,
    HOMEWORK,
    EXAM,
    REVIEW,
    NOTIFICATION_REVIEW
}

data class StudentRecitationInput(
    val title: String = "تسميع الحصة",
    val content: String = "",
    val scoreStr: String = "10",
    val maxScoreStr: String = "10",
    val note: String = "",
    val isRecorded: Boolean = false
)

data class StudentHomeworkInput(
    val title: String = "واجب الحصة",
    val status: HomeworkStatus = HomeworkStatus.COMPLETED,
    val note: String = "",
    val isRecorded: Boolean = false
)

data class StudentExamInput(
    val examName: String = "امتحان الحصة",
    val scoreStr: String = "10",
    val maxScoreStr: String = "10",
    val note: String = "",
    val isRecorded: Boolean = false
)

data class LessonSavedItem(
    val studentId: String,
    val sourceId: String,
    val eventType: NotificationEventType,
    val isConfirmedOnline: Boolean = true
)

data class LessonSavedContext(
    val absenceItems: List<LessonSavedItem> = emptyList(),
    val recitationItems: List<LessonSavedItem> = emptyList(),
    val homeworkItems: List<LessonSavedItem> = emptyList(),
    val examItems: List<LessonSavedItem> = emptyList()
) {
    val totalItemsCount: Int
        get() = absenceItems.size + recitationItems.size + homeworkItems.size + examItems.size
}

data class NotificationReviewItem(
    val studentId: String,
    val sourceId: String,
    val eventType: NotificationEventType,
    val studentName: String,
    val parentPhone: String,
    val whatsappEnabled: Boolean,
    val isConfirmedOnline: Boolean,
    val eligibleForNotification: Boolean,
    val ineligibilityReason: String? = null
)

data class NotificationReviewState(
    val absenceItems: List<NotificationReviewItem> = emptyList(),
    val recitationItems: List<NotificationReviewItem> = emptyList(),
    val homeworkItems: List<NotificationReviewItem> = emptyList(),
    val examItems: List<NotificationReviewItem> = emptyList(),
    val totalStudents: Int = 0,
    val presentStudents: Int = 0,
    val absentStudents: Int = 0,
    val recitationCount: Int = 0,
    val homeworkCount: Int = 0,
    val examCount: Int = 0
) {
    val totalReviewItems: Int
        get() = absenceItems.size + recitationItems.size + homeworkItems.size + examItems.size

    val totalEligibleItems: Int
        get() = (absenceItems + recitationItems + homeworkItems + examItems).count { it.eligibleForNotification }
}

data class FailedNotificationItem(
    val studentId: String,
    val sourceId: String,
    val eventType: NotificationEventType,
    val message: String
)

data class NotificationEventCreationSummary(
    val createdCount: Int = 0,
    val alreadyExistsCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedItems: List<FailedNotificationItem> = emptyList()
) {
    val totalProcessed: Int
        get() = createdCount + alreadyExistsCount + failedCount + skippedCount
}

data class StartLessonUiState(
    val currentStep: StartLessonStep = StartLessonStep.SELECT_GROUP,
    val grades: List<Grade> = emptyList(),
    val selectedGradeId: String? = null,
    val selectedGradeName: String = "",
    val studentsInGroup: List<Student> = emptyList(),
    val presentStudentIds: Set<String> = emptySet(),
    val currentDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    val currentYear: Int = LocalDate.now().year,
    val currentMonth: Int = LocalDate.now().monthValue,
    val paymentsMap: Map<String, com.example.core.model.LessonPayment> = emptyMap(),
    
    // Step inputs per studentId
    val recitationsMap: Map<String, StudentRecitationInput> = emptyMap(),
    val homeworkMap: Map<String, StudentHomeworkInput> = emptyMap(),
    val examsMap: Map<String, StudentExamInput> = emptyMap(),
    
    // Skip flags
    val skipRecitation: Boolean = false,
    val skipHomework: Boolean = false,
    val skipExam: Boolean = false,

    // Scanner
    val isScannerActive: Boolean = false,
    val scanFeedback: ScanFeedback? = null,
    val recentScannedStudents: List<Student> = emptyList(),

    // Saving / dialogs
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val showUnrecordedWarning: Boolean = false,
    val isFinished: Boolean = false,
    val savedContext: LessonSavedContext? = null,
    val reviewState: NotificationReviewState? = null,
    val isSubmittingNotifications: Boolean = false,
    val notificationCreationSummary: NotificationEventCreationSummary? = null
)

class StartLessonViewModel(
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val homeworkRepository: HomeworkRepository = RepositoryProvider.homeworkRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val paymentRepository: PaymentRepository = RepositoryProvider.paymentRepository,
    private val notificationEventRepository: NotificationEventRepository = RepositoryProvider.notificationEventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartLessonUiState())
    val uiState: StateFlow<StartLessonUiState> = _uiState.asStateFlow()

    private var allStudentsList: List<Student> = emptyList()
    private var studentCodeMap: Map<String, Student> = emptyMap()

    init {
        loadData()
        loadMonthlyPayments()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                (gradeRepository as? SupabaseGradeRepository)?.fetchGrades()
                (studentRepository as? SupabaseStudentRepository)?.fetchStudents()
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            combine(
                gradeRepository.getGrades(),
                studentRepository.getStudents()
            ) { grades, students ->
                val active = students.filter { it.deletedAt == null }
                allStudentsList = active
                studentCodeMap = active.associateBy { it.studentCode.trim().lowercase() }
                Pair(grades, active)
            }.collect { (grades, active) ->
                _uiState.update { state ->
                    val gradeName = state.selectedGradeId?.let { id -> grades.firstOrNull { it.id == id }?.name } ?: ""
                    state.copy(
                        grades = grades,
                        selectedGradeName = gradeName
                    )
                }
            }
        }
    }

    private fun loadMonthlyPayments() {
        viewModelScope.launch {
            val state = _uiState.value
            paymentRepository.getMonthlyPayments(state.currentYear, state.currentMonth)
                .catch { }
                .collect { payments ->
                    _uiState.update { it.copy(paymentsMap = payments.associateBy { p -> p.studentId }) }
                }
        }
    }

    fun selectGrade(gradeId: String) {
        val grade = _uiState.value.grades.firstOrNull { it.id == gradeId }
        val students = allStudentsList.filter { it.gradeId == gradeId }
        val recMap = students.associate { it.studentId to StudentRecitationInput() }
        val hwMap = students.associate { it.studentId to StudentHomeworkInput() }
        val exMap = students.associate { it.studentId to StudentExamInput() }

        _uiState.update {
            it.copy(
                selectedGradeId = gradeId,
                selectedGradeName = grade?.name ?: "المجموعة",
                studentsInGroup = students,
                recitationsMap = recMap,
                homeworkMap = hwMap,
                examsMap = exMap
            )
        }
    }

    fun goToStep(step: StartLessonStep) {
        _uiState.update { it.copy(currentStep = step, scanFeedback = null, isScannerActive = false) }
    }

    fun toggleStudentPresent(studentId: String) {
        _uiState.update { state ->
            val set = state.presentStudentIds.toMutableSet()
            if (set.contains(studentId)) set.remove(studentId) else set.add(studentId)
            state.copy(presentStudentIds = set)
        }
    }

    fun togglePaymentStatus(studentId: String) {
        val state = _uiState.value
        val payment = state.paymentsMap[studentId]
        val newIsPaid = !(payment?.isPaid ?: false)
        viewModelScope.launch {
            paymentRepository.setPaymentStatus(studentId, state.currentYear, state.currentMonth, newIsPaid, payment?.amount ?: 0.0)
        }
    }

    fun toggleScanner(active: Boolean) {
        _uiState.update { it.copy(isScannerActive = active, scanFeedback = null) }
    }

    fun processScannedBarcode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return
        val state = _uiState.value
        val student = studentCodeMap[trimmed.lowercase()]

        if (student == null) {
            _uiState.update { it.copy(scanFeedback = ScanFeedback(null, ScanResultType.NOT_FOUND, trimmed)) }
            return
        }
        if (student.gradeId != state.selectedGradeId) {
            _uiState.update { it.copy(scanFeedback = ScanFeedback(student, ScanResultType.WRONG_GROUP, trimmed)) }
            return
        }
        if (state.presentStudentIds.contains(student.studentId)) {
            _uiState.update { it.copy(scanFeedback = ScanFeedback(student, ScanResultType.ALREADY_PRESENT, trimmed)) }
            return
        }

        val updatedPresent = state.presentStudentIds + student.studentId
        val updatedRecent = (listOf(student) + state.recentScannedStudents.filter { it.studentId != student.studentId }).take(5)

        _uiState.update {
            it.copy(
                presentStudentIds = updatedPresent,
                recentScannedStudents = updatedRecent,
                scanFeedback = ScanFeedback(student, ScanResultType.SUCCESS_PRESENT, trimmed)
            )
        }
    }

    fun clearScanFeedback() {
        _uiState.update { it.copy(scanFeedback = null) }
    }

    // Recitation updates
    fun updateRecitationInput(studentId: String, update: (StudentRecitationInput) -> StudentRecitationInput) {
        _uiState.update { state ->
            val map = state.recitationsMap.toMutableMap()
            val current = map[studentId] ?: StudentRecitationInput()
            map[studentId] = update(current)
            state.copy(recitationsMap = map)
        }
    }

    fun setSkipRecitation(skip: Boolean) {
        _uiState.update { it.copy(skipRecitation = skip) }
    }

    // Homework updates
    fun updateHomeworkInput(studentId: String, update: (StudentHomeworkInput) -> StudentHomeworkInput) {
        _uiState.update { state ->
            val map = state.homeworkMap.toMutableMap()
            val current = map[studentId] ?: StudentHomeworkInput()
            map[studentId] = update(current)
            state.copy(homeworkMap = map)
        }
    }

    fun setSkipHomework(skip: Boolean) {
        _uiState.update { it.copy(skipHomework = skip) }
    }

    // Exam updates
    fun updateExamInput(studentId: String, update: (StudentExamInput) -> StudentExamInput) {
        _uiState.update { state ->
            val map = state.examsMap.toMutableMap()
            val current = map[studentId] ?: StudentExamInput()
            map[studentId] = update(current)
            state.copy(examsMap = map)
        }
    }

    fun setSkipExam(skip: Boolean) {
        _uiState.update { it.copy(skipExam = skip) }
    }

    fun checkReviewBeforeFinish() {
        val state = _uiState.value
        val unrecordedCount = state.studentsInGroup.size - state.presentStudentIds.size
        // If there are students not marked present, check if we want to show warning or finish directly
        if (unrecordedCount > 0 && !state.showUnrecordedWarning) {
            _uiState.update { it.copy(showUnrecordedWarning = true) }
        } else {
            finishLesson()
        }
    }

    fun dismissUnrecordedWarning() {
        _uiState.update { it.copy(showUnrecordedWarning = false) }
    }

    fun finishLesson() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true, showUnrecordedWarning = false, errorMessage = null) }

        viewModelScope.launch {
            try {
                val date = state.currentDate
                val students = state.studentsInGroup
                val groupId = state.selectedGradeId

                // Identify students who are absent
                val absentStudentIds = students
                    .filter { !state.presentStudentIds.contains(it.studentId) }
                    .map { it.studentId }
                    .toSet()

                // 1. Record Attendance
                val attRecords = students.map { s ->
                    val status = if (state.presentStudentIds.contains(s.studentId)) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT
                    BatchAttendanceItemDto(studentId = s.studentId, status = status.name, note = null)
                }
                val attResult = attendanceRepository.recordBatchAttendance(date, attRecords, groupId)
                val isLessonCloudConfirmed = attResult.isSuccess && attResult.getOrNull()?.syncStatus == BatchSyncStatus.SAVED_TO_CLOUD

                // 1.1 Match and collect Attendance Absence Source IDs
                val absenceItems = mutableListOf<LessonSavedItem>()
                if (absentStudentIds.isNotEmpty()) {
                    try {
                        val savedAttendanceList = attendanceRepository.getAttendanceForDateAndGroup(date, groupId)
                        val savedMap = savedAttendanceList.associateBy { it.studentId }
                        for (studentId in absentStudentIds) {
                            val att = savedMap[studentId]
                            if (att != null && att.status == AttendanceStatus.ABSENT && att.attendanceId.isNotBlank()) {
                                val isOnline = isLessonCloudConfirmed && isServerUUID(att.attendanceId)
                                absenceItems.add(
                                    LessonSavedItem(
                                        studentId = studentId,
                                        sourceId = att.attendanceId,
                                        eventType = NotificationEventType.ABSENCE,
                                        isConfirmedOnline = isOnline
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Resilient: lesson save does not fail if ID staging query encounters an issue
                    }
                }

                // 2. Record Recitations if not skipped
                val recitationItems = mutableListOf<LessonSavedItem>()
                if (!state.skipRecitation) {
                    state.recitationsMap.forEach { (studentId, rec) ->
                        if (rec.content.isNotBlank() || rec.title.isNotBlank()) {
                            val score = rec.scoreStr.toDoubleOrNull() ?: 10.0
                            val maxScore = rec.maxScoreStr.toDoubleOrNull() ?: 10.0
                            val recRes = recitationRepository.addRecitation(
                                studentId = studentId,
                                date = date,
                                title = rec.title.ifBlank { "تسميع الحصة" },
                                content = rec.content,
                                score = score,
                                maxScore = maxScore,
                                note = rec.note.ifBlank { null }
                            )
                            recRes.getOrNull()?.let { savedRec ->
                                val isOnline = isLessonCloudConfirmed && isServerUUID(savedRec.recitationId)
                                recitationItems.add(
                                    LessonSavedItem(
                                        studentId = studentId,
                                        sourceId = savedRec.recitationId,
                                        eventType = NotificationEventType.RECITATION,
                                        isConfirmedOnline = isOnline
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Record Homework if not skipped
                val homeworkItems = mutableListOf<LessonSavedItem>()
                if (!state.skipHomework) {
                    state.homeworkMap.forEach { (studentId, hw) ->
                        val hwRes = homeworkRepository.addHomework(
                            studentId = studentId,
                            date = date,
                            title = hw.title,
                            status = hw.status,
                            note = hw.note.ifBlank { null }
                        )
                        hwRes.getOrNull()?.let { savedHw ->
                            val isOnline = isLessonCloudConfirmed && isServerUUID(savedHw.homeworkId)
                            homeworkItems.add(
                                LessonSavedItem(
                                    studentId = studentId,
                                    sourceId = savedHw.homeworkId,
                                    eventType = NotificationEventType.HOMEWORK,
                                    isConfirmedOnline = isOnline
                                )
                            )
                        }
                    }
                }

                // 4. Record Exam if not skipped
                val examItems = mutableListOf<LessonSavedItem>()
                if (!state.skipExam) {
                    state.examsMap.forEach { (studentId, ex) ->
                        if (ex.scoreStr.toDoubleOrNull() != null) {
                            val score = ex.scoreStr.toDoubleOrNull() ?: 0.0
                            val maxScore = ex.maxScoreStr.toDoubleOrNull() ?: 10.0
                            val exRes = examRepository.addExam(
                                studentId = studentId,
                                date = date,
                                examName = ex.examName,
                                subject = state.selectedGradeName,
                                score = score,
                                maxScore = maxScore,
                                note = ex.note.ifBlank { null }
                            )
                            exRes.getOrNull()?.let { savedEx ->
                                val isOnline = isLessonCloudConfirmed && isServerUUID(savedEx.examId)
                                examItems.add(
                                    LessonSavedItem(
                                        studentId = studentId,
                                        sourceId = savedEx.examId,
                                        eventType = NotificationEventType.EXAM,
                                        isConfirmedOnline = isOnline
                                    )
                                )
                            }
                        }
                    }
                }

                val savedContext = LessonSavedContext(
                    absenceItems = absenceItems,
                    recitationItems = recitationItems,
                    homeworkItems = homeworkItems,
                    examItems = examItems
                )

                val reviewState = buildNotificationReviewState(
                    savedContext = savedContext,
                    students = students,
                    presentStudentIds = state.presentStudentIds
                )

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isFinished = true,
                        currentStep = StartLessonStep.NOTIFICATION_REVIEW,
                        savedContext = savedContext,
                        reviewState = reviewState
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "حدث خطأ أثناء حفظ الحصة") }
            }
        }
    }

    private fun buildNotificationReviewState(
        savedContext: LessonSavedContext,
        students: List<Student>,
        presentStudentIds: Set<String>
    ): NotificationReviewState {
        val studentMap = students.associateBy { it.studentId }

        fun mapToReviewItem(item: LessonSavedItem): NotificationReviewItem {
            val student = studentMap[item.studentId]
            val studentName = student?.fullName ?: "طالب"
            val parentPhone = student?.parentPhone?.trim() ?: ""
            val whatsappEnabled = student?.hasWhatsApp ?: false

            val (isEligible, reason) = when {
                !item.isConfirmedOnline -> Pair(false, "معلق حتى تكتمل المزامنة")
                parentPhone.isBlank() -> Pair(false, "لا يوجد رقم هاتف لولي الأمر")
                !whatsappEnabled -> Pair(false, "واتساب غير مفعّل للطالب")
                item.sourceId.isBlank() -> Pair(false, "معرف المصدر غير صالح")
                else -> Pair(true, null)
            }

            return NotificationReviewItem(
                studentId = item.studentId,
                sourceId = item.sourceId,
                eventType = item.eventType,
                studentName = studentName,
                parentPhone = parentPhone,
                whatsappEnabled = whatsappEnabled,
                isConfirmedOnline = item.isConfirmedOnline,
                eligibleForNotification = isEligible,
                ineligibilityReason = reason
            )
        }

        val totalStudents = students.size
        val presentStudents = presentStudentIds.size
        val absentStudents = totalStudents - presentStudents

        return NotificationReviewState(
            absenceItems = savedContext.absenceItems.map(::mapToReviewItem),
            recitationItems = savedContext.recitationItems.map(::mapToReviewItem),
            homeworkItems = savedContext.homeworkItems.map(::mapToReviewItem),
            examItems = savedContext.examItems.map(::mapToReviewItem),
            totalStudents = totalStudents,
            presentStudents = presentStudents,
            absentStudents = absentStudents,
            recitationCount = savedContext.recitationItems.size,
            homeworkCount = savedContext.homeworkItems.size,
            examCount = savedContext.examItems.size
        )
    }

    fun submitNotificationEvents(onComplete: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.isSubmittingNotifications) return // UX guard against rapid double taps

        val review = state.reviewState ?: run {
            onComplete?.invoke()
            return
        }

        _uiState.update { it.copy(isSubmittingNotifications = true) }

        viewModelScope.launch {
            try {
                val allReviewItems = review.absenceItems +
                        review.recitationItems +
                        review.homeworkItems +
                        review.examItems

                var createdCount = 0
                var alreadyExistsCount = 0
                var failedCount = 0
                var skippedCount = 0
                val failedList = mutableListOf<FailedNotificationItem>()

                for (item in allReviewItems) {
                    // Eligibility filter: strictly confirmed online, eligible, and valid IDs
                    val isEligible = item.isConfirmedOnline &&
                            item.eligibleForNotification &&
                            item.studentId.isNotBlank() &&
                            item.sourceId.isNotBlank()

                    if (!isEligible) {
                        skippedCount++
                        continue
                    }

                    // Map domain NotificationEvent (teacherId is populated by repository from auth session)
                    val domainEvent: NotificationEvent = when (item.eventType) {
                        NotificationEventType.ABSENCE -> NotificationEvent.Absence(
                            teacherId = "",
                            studentId = item.studentId,
                            attendanceId = item.sourceId
                        )
                        NotificationEventType.RECITATION -> NotificationEvent.Recitation(
                            teacherId = "",
                            studentId = item.studentId,
                            recitationId = item.sourceId
                        )
                        NotificationEventType.HOMEWORK -> NotificationEvent.Homework(
                            teacherId = "",
                            studentId = item.studentId,
                            homeworkId = item.sourceId
                        )
                        NotificationEventType.EXAM -> NotificationEvent.Exam(
                            teacherId = "",
                            studentId = item.studentId,
                            examId = item.sourceId
                        )
                    }

                    // Direct insert attempt without SELECT before INSERT
                    val result = notificationEventRepository.createNotificationEvent(domainEvent)
                    when (result) {
                        is NotificationEventCreationResult.Created -> {
                            createdCount++
                        }
                        is NotificationEventCreationResult.AlreadyExists -> {
                            alreadyExistsCount++
                        }
                        is NotificationEventCreationResult.Failed -> {
                            failedCount++
                            failedList.add(
                                FailedNotificationItem(
                                    studentId = item.studentId,
                                    sourceId = item.sourceId,
                                    eventType = item.eventType,
                                    message = result.message
                                )
                            )
                        }
                    }
                }

                val summary = NotificationEventCreationSummary(
                    createdCount = createdCount,
                    alreadyExistsCount = alreadyExistsCount,
                    failedCount = failedCount,
                    skippedCount = skippedCount,
                    failedItems = failedList
                )

                _uiState.update {
                    it.copy(
                        isSubmittingNotifications = false,
                        notificationCreationSummary = summary
                    )
                }

                // If all attempted events succeeded or already existed without errors, proceed
                if (failedCount == 0) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmittingNotifications = false,
                        errorMessage = e.message ?: "حدث خطأ أثناء إنشاء أحداث الإشعارات"
                    )
                }
            }
        }
    }

    private fun isServerUUID(id: String): Boolean {
        if (id.isBlank() || id.contains("_")) return false
        return try {
            java.util.UUID.fromString(id)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
