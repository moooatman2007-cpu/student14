package com.example.ui.student_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchSyncStatus
import com.example.core.model.AttendanceSummary
import com.example.core.model.Exam
import com.example.core.model.ExamSummary
import com.example.core.model.Grade
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.LessonPayment
import com.example.core.model.Recitation
import com.example.core.model.RecitationSummary
import com.example.core.model.Student
import com.example.core.model.StudentMonthlyPerformance
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.GradeRepository
import com.example.data.repository.HomeworkRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.MonthlyReportRepository
import com.example.data.repository.PaymentRepository
import com.example.data.repository.RecitationRepository
import com.example.data.repository.RepositoryProvider
import com.example.core.model.NotificationEvent
import com.example.data.repository.NotificationEventCreationResult
import com.example.data.repository.NotificationEventRepository
import com.example.data.repository.StudentRepository
import io.github.jan.supabase.auth.auth
import com.example.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StudentDetailTab(val titleAr: String) {
    OVERVIEW("نظرة عامة"),
    ATTENDANCE("الحضور"),
    RECITATIONS("التسميعات"),
    EXAMS("الامتحانات"),
    PAYMENTS("الدفع الشهري"),
    HOMEWORK("الواجبات"),
    MONTHLY_REPORT("التقرير الشهري")
}

data class StudentDetailUiState(
    val isLoading: Boolean = true,
    val student: Student? = null,
    val grade: Grade? = null,
    val group: com.example.core.model.Group? = null,
    val availableGroups: List<com.example.core.model.Group> = emptyList(),
    val formattedCreatedAt: String = "",
    val selectedTab: StudentDetailTab = StudentDetailTab.OVERVIEW,
    
    // Month/Year navigation for Attendance & Reports & Payments
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthNameAr: String = "",

    // Payment State
    val monthlyPayment: LessonPayment? = null,
    val isPaymentPaid: Boolean = false,
    val isTogglingPayment: Boolean = false,

    // Attendance State
    val attendances: List<Attendance> = emptyList(),
    val attendanceSummary: AttendanceSummary = AttendanceSummary(),
    val showAttendanceDialog: Boolean = false,
    val attendanceToEdit: Attendance? = null,
    val attendanceToDelete: Attendance? = null,

    // Recitations State
    val recitations: List<Recitation> = emptyList(),
    val recitationSummary: RecitationSummary = RecitationSummary(),
    val showRecitationDialog: Boolean = false,
    val recitationToEdit: Recitation? = null,
    val recitationToDelete: Recitation? = null,

    // Exams State
    val exams: List<Exam> = emptyList(),
    val examSummary: ExamSummary = ExamSummary(),
    val showExamDialog: Boolean = false,
    val examToEdit: Exam? = null,
    val examToDelete: Exam? = null,

    // Homework State
    val homeworks: List<Homework> = emptyList(),
    val showHomeworkDialog: Boolean = false,
    val homeworkToEdit: Homework? = null,
    val homeworkToDelete: Homework? = null,

    // Overview / Global State
    val globalAttendances: List<Attendance> = emptyList(),
    val globalRecitations: List<Recitation> = emptyList(),
    val globalExams: List<Exam> = emptyList(),
    val globalHomeworks: List<Homework> = emptyList(),
    val globalAttendanceSummary: AttendanceSummary = AttendanceSummary(),
    
    // Monthly Report State
    val studentMonthlyPerformance: StudentMonthlyPerformance? = null,
    val teacherMonthlyNote: String = "",
    val isSavingReportNote: Boolean = false,

    // Delete Student
    val showDeleteBottomSheet: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false
)

sealed class StudentDetailEvent {
    data object StudentDeleted : StudentDetailEvent()
    data class ShowMessage(val message: String) : StudentDetailEvent()
}

class StudentDetailViewModel(
    savedStateHandle: SavedStateHandle? = null,
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val monthlyReportRepository: MonthlyReportRepository = RepositoryProvider.monthlyReportRepository,
    private val homeworkRepository: HomeworkRepository = RepositoryProvider.homeworkRepository,
    private val paymentRepository: PaymentRepository = RepositoryProvider.paymentRepository,
    private val groupRepository: GroupRepository = RepositoryProvider.groupRepository,
    private val notificationEventRepository: NotificationEventRepository = RepositoryProvider.notificationEventRepository
) : ViewModel() {

    private val studentIdArg: String? = savedStateHandle?.get<String>("studentId")

    private val _uiState = MutableStateFlow(StudentDetailUiState())
    val uiState: StateFlow<StudentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StudentDetailEvent>()
    val events: SharedFlow<StudentDetailEvent> = _events.asSharedFlow()

    private var loadStudentJob: Job? = null
    private var refreshJob: Job? = null

    init {
        updateMonthName()
        studentIdArg?.let { loadStudent(it) }
    }

    private fun getArabicMonthName(month: Int): String {
        return when (month) {
            1 -> "يناير"
            2 -> "فبراير"
            3 -> "مارس"
            4 -> "أبريل"
            5 -> "مايو"
            6 -> "يونيو"
            7 -> "يوليو"
            8 -> "أغسطس"
            9 -> "سبتمبر"
            10 -> "أكتوبر"
            11 -> "نوفمبر"
            12 -> "ديسمبر"
            else -> "الشهر $month"
        }
    }

    private fun updateMonthName() {
        val monthStr = getArabicMonthName(_uiState.value.selectedMonth)
        _uiState.update { it.copy(monthNameAr = "$monthStr ${_uiState.value.selectedYear}") }
    }

    fun selectTab(tab: StudentDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun navigateMonth(delta: Int) {
        val currentMonth = _uiState.value.selectedMonth
        val currentYear = _uiState.value.selectedYear

        var newMonth = currentMonth + delta
        var newYear = currentYear

        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        } else if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }

        _uiState.update {
            it.copy(
                selectedMonth = newMonth,
                selectedYear = newYear
            )
        }
        updateMonthName()
        refreshCurrentTabData()
    }

    fun setMonthYear(month: Int, year: Int) {
        _uiState.update {
            it.copy(
                selectedMonth = month.coerceIn(1, 12),
                selectedYear = year
            )
        }
        updateMonthName()
        refreshCurrentTabData()
    }

    fun loadStudent(studentId: String) {
        loadStudentJob?.cancel()
        refreshJob?.cancel()
        loadStudentJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val student = studentRepository.getStudentById(studentId)
            if (student != null) {
                val grade = gradeRepository.getGradeById(student.gradeId)
                val group = student.groupId?.let { groupId ->
                    try {
                        val teacherId = student.teacherId ?: ""
                        groupRepository.getGroupById(teacherId, groupId)
                    } catch (_: Exception) {
                        null
                    }
                }
                
                // Fetch available groups
                val availableGroups = try {
                    groupRepository.observeGroupsByGrade(student.teacherId ?: "", student.gradeId).first()
                } catch (_: Exception) {
                    emptyList()
                }

                val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ar"))
                val formattedDate = dateFormat.format(Date(student.createdAt))

                _uiState.update {
                    it.copy(
                        student = student,
                        grade = grade,
                        group = group,
                        availableGroups = availableGroups,
                        formattedCreatedAt = formattedDate,
                        error = null
                    )
                }
                
                // Load global data for overview and populate monthly slices locally from Room cache
                viewModelScope.launch {
                    combine(
                        attendanceRepository.getAttendanceForStudent(studentId),
                        recitationRepository.getRecitationsForStudent(studentId),
                        examRepository.getExamsForStudent(studentId),
                        homeworkRepository.getHomeworkForStudent(studentId)
                    ) { attendanceList, recitationList, examList, homeworkList ->
                        val summary = AttendanceSummary(
                            totalDays = attendanceList.size,
                            presentCount = attendanceList.count { it.status == AttendanceStatus.PRESENT },
                            absentCount = attendanceList.count { it.status == AttendanceStatus.ABSENT },
                            lateCount = attendanceList.count { it.status == AttendanceStatus.LATE },
                            excusedCount = attendanceList.count { it.status == AttendanceStatus.EXCUSED },
                            attendanceRate = if (attendanceList.isNotEmpty()) (attendanceList.count { it.status == AttendanceStatus.PRESENT }.toFloat() / attendanceList.size) * 100f else 0f
                        )
                        val sortedAtt = attendanceList.sortedByDescending { a -> a.date }
                        val sortedRec = recitationList.sortedByDescending { r -> r.date }
                        val sortedEx = examList.sortedByDescending { e -> e.date }
                        val sortedHw = homeworkList.sortedByDescending { h -> h.date }

                        StudentCombinedData(
                            sortedAtt = sortedAtt,
                            attendanceSummary = summary,
                            sortedRec = sortedRec,
                            sortedEx = sortedEx,
                            sortedHw = sortedHw
                        )
                    }.collectLatest { data ->
                        _uiState.update { 
                            it.copy(
                                globalAttendances = data.sortedAtt,
                                globalAttendanceSummary = data.attendanceSummary,
                                globalRecitations = data.sortedRec,
                                globalExams = data.sortedEx,
                                globalHomeworks = data.sortedHw
                            )
                        }
                        updateMonthlySlices()
                    }
                }

                _uiState.update { it.copy(isLoading = false) }
                refreshCurrentTabData()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "عذراً، لم يتم العثور على بيانات الطالب."
                    )
                }
            }
        }
    }

    private fun updateMonthlySlices() {
        val year = _uiState.value.selectedYear
        val month = _uiState.value.selectedMonth
        val globalAtt = _uiState.value.globalAttendances
        val globalRec = _uiState.value.globalRecitations
        val globalEx = _uiState.value.globalExams
        val globalHw = _uiState.value.globalHomeworks

        viewModelScope.launch {
            val updateBlock = withContext(Dispatchers.Default) {
                val dateRange = DateUtils.getMonthDateRange(year, month)
                val startDate = dateRange.startDate
                val endDate = dateRange.endDate

                val monthlyAtt = globalAtt.filter { it.date >= startDate && it.date <= endDate }
                val attSummary = AttendanceSummary(
                    totalDays = monthlyAtt.size,
                    presentCount = monthlyAtt.count { it.status == AttendanceStatus.PRESENT },
                    absentCount = monthlyAtt.count { it.status == AttendanceStatus.ABSENT },
                    lateCount = monthlyAtt.count { it.status == AttendanceStatus.LATE },
                    excusedCount = monthlyAtt.count { it.status == AttendanceStatus.EXCUSED },
                    attendanceRate = if (monthlyAtt.isNotEmpty()) (monthlyAtt.count { it.status == AttendanceStatus.PRESENT }.toFloat() / monthlyAtt.size) * 100f else 0f
                )

                val monthlyRec = globalRec.filter { it.date >= startDate && it.date <= endDate }
                val recSummary = if (monthlyRec.isEmpty()) {
                    RecitationSummary()
                } else {
                    val totalCount = monthlyRec.size
                    val avgScore = monthlyRec.map { it.score }.average()
                    val avgMaxScore = monthlyRec.map { it.maxScore }.average()
                    val avgPct = monthlyRec.map { (it.score / it.maxScore) * 100.0 }.average().toFloat()
                    RecitationSummary(
                        totalCount = totalCount,
                        averageScore = avgScore,
                        averageMaxScore = avgMaxScore,
                        averagePercentage = avgPct
                    )
                }

                val monthlyEx = globalEx.filter { it.date >= startDate && it.date <= endDate }
                val exSummary = if (monthlyEx.isEmpty()) {
                    ExamSummary()
                } else {
                    val totalCount = monthlyEx.size
                    val avgScore = monthlyEx.map { it.score }.average()
                    val percentages = monthlyEx.map { (it.score / it.maxScore) * 100.0 }
                    val avgPct = percentages.average().toFloat()
                    val highestPct = percentages.maxOrNull()?.toFloat() ?: 0f
                    val lowestPct = percentages.minOrNull()?.toFloat() ?: 0f
                    ExamSummary(
                        totalCount = totalCount,
                        averageScore = avgScore,
                        averagePercentage = avgPct,
                        highestPercentage = highestPct,
                        lowestPercentage = lowestPct
                    )
                }

                val monthlyHw = globalHw.filter { it.date >= startDate && it.date <= endDate }

                val updater: (StudentDetailUiState) -> StudentDetailUiState = { state ->
                    state.copy(
                        attendances = monthlyAtt,
                        attendanceSummary = attSummary,
                        recitations = monthlyRec,
                        recitationSummary = recSummary,
                        exams = monthlyEx,
                        examSummary = exSummary,
                        homeworks = monthlyHw
                    )
                }
                updater
            }
            _uiState.update { updateBlock(it) }
        }
    }

    private fun refreshCurrentTabData() {
        val studentId = _uiState.value.student?.studentId ?: return
        val year = _uiState.value.selectedYear
        val month = _uiState.value.selectedMonth

        updateMonthlySlices()

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            coroutineScope {
                // Load Monthly Performance & Report
                launch {
                    val performance = monthlyReportRepository.getStudentMonthlyPerformance(studentId, year, month)
                    _uiState.update {
                        it.copy(
                            studentMonthlyPerformance = performance,
                            teacherMonthlyNote = performance?.teacherNote ?: ""
                        )
                    }
                }

                // Load Monthly Payment
                launch {
                    val payment = paymentRepository.getPaymentForStudent(studentId, year, month)
                    _uiState.update {
                        it.copy(
                            monthlyPayment = payment,
                            isPaymentPaid = payment?.isPaid ?: false
                        )
                    }
                }
            }
        }
    }

    fun toggleMonthlyPayment() {
        if (_uiState.value.isTogglingPayment) return
        val studentId = _uiState.value.student?.studentId ?: return
        val year = _uiState.value.selectedYear
        val month = _uiState.value.selectedMonth
        val oldPayment = _uiState.value.monthlyPayment
        val oldIsPaid = _uiState.value.isPaymentPaid

        val newIsPaid = !oldIsPaid

        // Optimistic UI update
        _uiState.update {
            it.copy(
                isPaymentPaid = newIsPaid,
                isTogglingPayment = true
            )
        }

        viewModelScope.launch {
            val result = paymentRepository.togglePaymentStatus(studentId, year, month)
            if (result.isSuccess) {
                val updatedPayment = result.getOrNull()
                _uiState.update {
                    it.copy(
                        monthlyPayment = updatedPayment,
                        isPaymentPaid = updatedPayment?.isPaid ?: newIsPaid,
                        isTogglingPayment = false
                    )
                }
                val statusStr = if (updatedPayment?.isPaid == true) "مدفوع" else "غير مدفوع"
                _events.emit(StudentDetailEvent.ShowMessage("تم تحديث حالة الدفع إلى $statusStr"))
            } else {
                // Rollback on failure
                _uiState.update {
                    it.copy(
                        monthlyPayment = oldPayment,
                        isPaymentPaid = oldIsPaid,
                        isTogglingPayment = false
                    )
                }
                val errorMsg = result.exceptionOrNull()?.message ?: "فشل تحديث حالة الدفع"
                _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
            }
        }
    }

    // ===================================
    // ATTENDANCE ACTIONS
    // ===================================

    fun openAddAttendanceDialog(date: String? = null) {
        val targetDate = date ?: run {
            val cal = Calendar.getInstance()
            String.format("%04d-%02d-%02d", _uiState.value.selectedYear, _uiState.value.selectedMonth, cal.get(Calendar.DAY_OF_MONTH).coerceIn(1, 28))
        }
        val existing = _uiState.value.attendances.find { it.date == targetDate }
        _uiState.update {
            it.copy(
                showAttendanceDialog = true,
                attendanceToEdit = existing ?: Attendance(
                    attendanceId = "",
                    studentId = it.student?.studentId ?: "",
                    date = targetDate,
                    status = AttendanceStatus.PRESENT
                )
            )
        }
    }

    fun openEditAttendanceDialog(attendance: Attendance) {
        _uiState.update {
            it.copy(
                showAttendanceDialog = true,
                attendanceToEdit = attendance
            )
        }
    }

    fun closeAttendanceDialog() {
        _uiState.update {
            it.copy(
                showAttendanceDialog = false,
                attendanceToEdit = null
            )
        }
    }

    fun saveAttendance(date: String, status: AttendanceStatus, note: String?) {
        if (_uiState.value.isSaving) return
        val studentId = _uiState.value.student?.studentId ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = attendanceRepository.recordOrUpdateAttendance(
                    studentId = studentId,
                    date = date,
                    status = status,
                    note = note
                )
                if (result.isSuccess) {
                    closeAttendanceDialog()
                    val syncRes = result.getOrNull()
                    val msg = if (syncRes?.syncStatus == BatchSyncStatus.QUEUED_OFFLINE) {
                        "تم تسجيل الحضور محلياً وسيتم المزامنة عند توفر الاتصال 📡"
                    } else {
                        "تم تسجيل الحضور بنجاح"
                    }
                    _events.emit(StudentDetailEvent.ShowMessage(msg))
                    refreshCurrentTabData()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "تعذر تسجيل الحضور"
                    _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmDeleteAttendance(attendance: Attendance) {
        _uiState.update { it.copy(attendanceToDelete = attendance) }
    }

    fun dismissDeleteAttendance() {
        _uiState.update { it.copy(attendanceToDelete = null) }
    }

    fun deleteAttendance() {
        if (_uiState.value.isSaving) return
        val att = _uiState.value.attendanceToDelete ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                dismissDeleteAttendance()
                attendanceRepository.deleteAttendance(att.attendanceId)
                _events.emit(StudentDetailEvent.ShowMessage("تم حذف سجل الحضور"))
                refreshCurrentTabData()
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ===================================
    // RECITATION ACTIONS
    // ===================================

    fun openAddRecitationDialog() {
        _uiState.update {
            it.copy(
                showRecitationDialog = true,
                recitationToEdit = null
            )
        }
    }

    fun openEditRecitationDialog(recitation: Recitation) {
        _uiState.update {
            it.copy(
                showRecitationDialog = true,
                recitationToEdit = recitation
            )
        }
    }

    fun closeRecitationDialog() {
        _uiState.update {
            it.copy(
                showRecitationDialog = false,
                recitationToEdit = null
            )
        }
    }

    fun saveRecitation(
        title: String,
        content: String,
        date: String,
        score: Double,
        maxScore: Double,
        note: String?
    ) {
        if (_uiState.value.isSaving) return
        val studentId = _uiState.value.student?.studentId ?: return
        val currentEdit = _uiState.value.recitationToEdit

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (currentEdit == null) {
                    val result = recitationRepository.addRecitation(
                        studentId = studentId,
                        date = date,
                        title = title,
                        content = content,
                        score = score,
                        maxScore = maxScore,
                        note = note
                    )
                    if (result.isSuccess) {
                        closeRecitationDialog()
                        val syncRes = result.getOrNull()
                        if (syncRes?.syncStatus == BatchSyncStatus.SAVED_TO_CLOUD) {
                            val recitation = syncRes.data
                            val recitationId = recitation?.recitationId ?: ""
                            val teacherId = _uiState.value.student?.teacherId ?: com.example.data.SupabaseClientProvider.mockTeacherId ?: com.example.data.SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: ""
                            if (recitationId.isNotBlank() && teacherId.isNotBlank()) {
                                try {
                                    val event = NotificationEvent.Recitation(
                                        teacherId = teacherId,
                                        studentId = studentId,
                                        recitationId = recitationId
                                    )
                                    val creationRes = notificationEventRepository.createNotificationEvent(event)
                                    if (creationRes is NotificationEventCreationResult.Created || creationRes is NotificationEventCreationResult.AlreadyExists) {
                                        notificationEventRepository.triggerNotificationWorker("RECITATION")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("StudentDetailViewModel", "Failed to create recitation notification: ${e.javaClass.simpleName}")
                                }
                            }
                        }
                        val msg = if (syncRes?.syncStatus == BatchSyncStatus.QUEUED_OFFLINE) {
                            "تم حفظ التسميع محلياً وسيتم المزامنة عند توفر الاتصال 📡"
                        } else {
                            "تمت إضافة التسميع بنجاح"
                        }
                        _events.emit(StudentDetailEvent.ShowMessage(msg))
                        refreshCurrentTabData()
                    } else {
                        _events.emit(StudentDetailEvent.ShowMessage(result.exceptionOrNull()?.message ?: "حدث خطأ في حفظ التسميع"))
                    }
                } else {
                    val result = recitationRepository.updateRecitation(
                        currentEdit.copy(
                            title = title,
                            content = content,
                            date = date,
                            score = score,
                            maxScore = maxScore,
                            note = note
                        )
                    )
                    if (result.isSuccess) {
                        closeRecitationDialog()
                        _events.emit(StudentDetailEvent.ShowMessage("تم تعديل التسميع بنجاح"))
                        refreshCurrentTabData()
                    } else {
                        _events.emit(StudentDetailEvent.ShowMessage(result.exceptionOrNull()?.message ?: "حدث خطأ في حفظ التسميع"))
                    }
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmDeleteRecitation(recitation: Recitation) {
        _uiState.update { it.copy(recitationToDelete = recitation) }
    }

    fun dismissDeleteRecitation() {
        _uiState.update { it.copy(recitationToDelete = null) }
    }

    fun deleteRecitation() {
        if (_uiState.value.isSaving) return
        val rec = _uiState.value.recitationToDelete ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                dismissDeleteRecitation()
                val result = recitationRepository.deleteRecitation(rec.recitationId)
                if (result.isSuccess) {
                    _events.emit(StudentDetailEvent.ShowMessage("تم حذف التسميع بنجاح"))
                    refreshCurrentTabData()
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ===================================
    // EXAM ACTIONS
    // ===================================

    fun openAddExamDialog() {
        _uiState.update {
            it.copy(
                showExamDialog = true,
                examToEdit = null
            )
        }
    }

    fun openEditExamDialog(exam: Exam) {
        _uiState.update {
            it.copy(
                showExamDialog = true,
                examToEdit = exam
            )
        }
    }

    fun closeExamDialog() {
        _uiState.update {
            it.copy(
                showExamDialog = false,
                examToEdit = null
            )
        }
    }

    fun saveExam(
        examName: String,
        subject: String?,
        date: String,
        score: Double,
        maxScore: Double,
        note: String?
    ) {
        if (_uiState.value.isSaving) return
        val studentId = _uiState.value.student?.studentId ?: return
        val currentEdit = _uiState.value.examToEdit

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (currentEdit == null) {
                    val result = examRepository.addExam(
                        studentId = studentId,
                        date = date,
                        examName = examName,
                        subject = subject,
                        score = score,
                        maxScore = maxScore,
                        note = note
                    )
                    if (result.isSuccess) {
                        closeExamDialog()
                        val syncRes = result.getOrNull()
                        if (syncRes?.syncStatus == BatchSyncStatus.SAVED_TO_CLOUD) {
                            val exam = syncRes.data
                            val examId = exam?.examId ?: ""
                            val teacherId = _uiState.value.student?.teacherId ?: com.example.data.SupabaseClientProvider.mockTeacherId ?: com.example.data.SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: ""
                            if (examId.isNotBlank() && teacherId.isNotBlank()) {
                                try {
                                    val event = NotificationEvent.Exam(
                                        teacherId = teacherId,
                                        studentId = studentId,
                                        examId = examId
                                    )
                                    val creationRes = notificationEventRepository.createNotificationEvent(event)
                                    if (creationRes is NotificationEventCreationResult.Created || creationRes is NotificationEventCreationResult.AlreadyExists) {
                                        notificationEventRepository.triggerNotificationWorker("EXAM")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("StudentDetailViewModel", "Failed to create exam notification: ${e.javaClass.simpleName}")
                                }
                            }
                        }
                        val msg = if (syncRes?.syncStatus == BatchSyncStatus.QUEUED_OFFLINE) {
                            "تم حفظ الامتحان محلياً وسيتم المزامنة عند توفر الاتصال 📡"
                        } else {
                            "تمت إضافة الامتحان بنجاح"
                        }
                        _events.emit(StudentDetailEvent.ShowMessage(msg))
                        refreshCurrentTabData()
                    } else {
                        _events.emit(StudentDetailEvent.ShowMessage(result.exceptionOrNull()?.message ?: "حدث خطأ في حفظ الامتحان"))
                    }
                } else {
                    val result = examRepository.updateExam(
                        currentEdit.copy(
                            examName = examName,
                            subject = subject,
                            date = date,
                            score = score,
                            maxScore = maxScore,
                            note = note
                        )
                    )
                    if (result.isSuccess) {
                        closeExamDialog()
                        _events.emit(StudentDetailEvent.ShowMessage("تم تعديل الامتحان بنجاح"))
                        refreshCurrentTabData()
                    } else {
                        _events.emit(StudentDetailEvent.ShowMessage(result.exceptionOrNull()?.message ?: "حدث خطأ في حفظ الامتحان"))
                    }
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmDeleteExam(exam: Exam) {
        _uiState.update { it.copy(examToDelete = exam) }
    }

    fun dismissDeleteExam() {
        _uiState.update { it.copy(examToDelete = null) }
    }

    fun deleteExam() {
        if (_uiState.value.isSaving) return
        val ex = _uiState.value.examToDelete ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                dismissDeleteExam()
                val result = examRepository.deleteExam(ex.examId)
                if (result.isSuccess) {
                    _events.emit(StudentDetailEvent.ShowMessage("تم حذف الامتحان بنجاح"))
                    refreshCurrentTabData()
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ===================================
    // MONTHLY REPORT ACTIONS
    // ===================================

    fun onTeacherReportNoteChange(note: String) {
        _uiState.update { it.copy(teacherMonthlyNote = note) }
    }

    fun saveMonthlyReportNote() {
        val studentId = _uiState.value.student?.studentId ?: return
        val year = _uiState.value.selectedYear
        val month = _uiState.value.selectedMonth
        val note = _uiState.value.teacherMonthlyNote

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingReportNote = true) }
            val result = monthlyReportRepository.saveOrUpdateMonthlyReport(
                studentId = studentId,
                year = year,
                month = month,
                teacherNote = note
            )
            _uiState.update { it.copy(isSavingReportNote = false) }
            if (result.isSuccess) {
                _events.emit(StudentDetailEvent.ShowMessage("تم حفظ التقرير الشهري وملاحظات المدرس بنجاح"))
                refreshCurrentTabData()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "تعذر حفظ التقرير الشهري"
                _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
            }
        }
    }

    // ===================================
    // HOMEWORK ACTIONS
    // ===================================

    fun openAddHomeworkDialog() {
        _uiState.update {
            it.copy(
                showHomeworkDialog = true,
                homeworkToEdit = null
            )
        }
    }

    fun openEditHomeworkDialog(homework: Homework) {
        _uiState.update {
            it.copy(
                showHomeworkDialog = true,
                homeworkToEdit = homework
            )
        }
    }

    fun closeHomeworkDialog() {
        _uiState.update {
            it.copy(
                showHomeworkDialog = false,
                homeworkToEdit = null
            )
        }
    }

    fun saveHomework(
        date: String,
        title: String,
        status: HomeworkStatus,
        note: String?
    ) {
        if (_uiState.value.isSaving) return
        val studentId = _uiState.value.student?.studentId ?: return
        val editingHomework = _uiState.value.homeworkToEdit

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                closeHomeworkDialog()
                if (editingHomework != null) {
                    val result = homeworkRepository.updateHomework(
                        editingHomework.copy(
                            date = date,
                            title = title,
                            status = status,
                            note = note
                        )
                    )
                    if (result.isSuccess) {
                        _events.emit(StudentDetailEvent.ShowMessage("تم حفظ الواجب بنجاح"))
                        refreshCurrentTabData()
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "فشل حفظ الواجب"
                        _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
                    }
                } else {
                    val result = homeworkRepository.addHomework(
                        studentId = studentId,
                        date = date,
                        title = title,
                        status = status,
                        note = note
                    )
                    if (result.isSuccess) {
                        val syncRes = result.getOrNull()
                        val msg = if (syncRes?.syncStatus == BatchSyncStatus.QUEUED_OFFLINE) {
                            "تم حفظ الواجب محلياً وسيتم المزامنة عند توفر الاتصال 📡"
                        } else {
                            "تم حفظ الواجب بنجاح"
                        }
                        _events.emit(StudentDetailEvent.ShowMessage(msg))
                        refreshCurrentTabData()
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "فشل حفظ الواجب"
                        _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
                    }
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmDeleteHomework(homework: Homework) {
        _uiState.update { it.copy(homeworkToDelete = homework) }
    }

    fun dismissDeleteHomework() {
        _uiState.update { it.copy(homeworkToDelete = null) }
    }

    fun deleteHomework() {
        if (_uiState.value.isSaving) return
        val homework = _uiState.value.homeworkToDelete ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                dismissDeleteHomework()
                val result = homeworkRepository.deleteHomework(homework.homeworkId)
                if (result.isSuccess) {
                    _events.emit(StudentDetailEvent.ShowMessage("تم حذف الواجب بنجاح"))
                    refreshCurrentTabData()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "فشل حذف الواجب"
                    _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ===================================
    // DELETE STUDENT
    // ===================================

    fun openDeleteBottomSheet() {
        _uiState.update { it.copy(showDeleteBottomSheet = true) }
    }

    fun closeDeleteBottomSheet() {
        _uiState.update { it.copy(showDeleteBottomSheet = false) }
    }

    fun deleteStudent() {
        val studentId = _uiState.value.student?.studentId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteBottomSheet = false, isLoading = true) }
            val result = studentRepository.deleteStudent(studentId)
            if (result.isSuccess) {
                val syncRes = result.getOrNull()
                if (syncRes?.syncStatus == BatchSyncStatus.QUEUED_OFFLINE) {
                    _events.emit(StudentDetailEvent.ShowMessage("تم حذف الطالب محلياً وسيتم المزامنة عند توفر الاتصال 📡"))
                } else {
                    _events.emit(StudentDetailEvent.ShowMessage("تم حذف الطالب بنجاح"))
                }
                _events.emit(StudentDetailEvent.StudentDeleted)
            } else {
                _uiState.update { it.copy(isLoading = false) }
                val errorMsg = result.exceptionOrNull()?.message ?: "فشل حذف الطالب"
                _events.emit(StudentDetailEvent.ShowMessage(errorMsg))
            }
        }
    }

    fun assignGroup(groupId: String?) {
        val studentId = _uiState.value.student?.studentId ?: return
        val teacherId = _uiState.value.student?.teacherId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = groupRepository.assignStudentToGroup(teacherId, studentId, groupId)
            if (result.isSuccess) {
                // Refresh student
                loadStudent(studentId)
                _events.emit(StudentDetailEvent.ShowMessage("تم تحديث مجموعة الطالب"))
            } else {
                _events.emit(StudentDetailEvent.ShowMessage("فشل تحديث مجموعة الطالب"))
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}

data class StudentCombinedData(
    val sortedAtt: List<Attendance>,
    val attendanceSummary: AttendanceSummary,
    val sortedRec: List<Recitation>,
    val sortedEx: List<Exam>,
    val sortedHw: List<Homework>
)

