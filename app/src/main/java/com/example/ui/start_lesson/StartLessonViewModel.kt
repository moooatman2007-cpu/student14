package com.example.ui.start_lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.Grade
import com.example.core.model.HomeworkStatus
import com.example.core.model.Student
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.HomeworkRepository
import com.example.data.repository.GradeRepository
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
    REVIEW
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
    val isFinished: Boolean = false
)

class StartLessonViewModel(
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val homeworkRepository: HomeworkRepository = RepositoryProvider.homeworkRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val paymentRepository: PaymentRepository = RepositoryProvider.paymentRepository
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

                // 1. Record Attendance
                val attRecords = students.map { s ->
                    val status = if (state.presentStudentIds.contains(s.studentId)) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT
                    BatchAttendanceItemDto(studentId = s.studentId, status = status.name, note = null)
                }
                attendanceRepository.recordBatchAttendance(date, attRecords)

                // 2. Record Recitations if not skipped
                if (!state.skipRecitation) {
                    state.recitationsMap.forEach { (studentId, rec) ->
                        if (rec.content.isNotBlank() || rec.title.isNotBlank()) {
                            val score = rec.scoreStr.toDoubleOrNull() ?: 10.0
                            val maxScore = rec.maxScoreStr.toDoubleOrNull() ?: 10.0
                            recitationRepository.addRecitation(
                                studentId = studentId,
                                date = date,
                                title = rec.title.ifBlank { "تسميع الحصة" },
                                content = rec.content,
                                score = score,
                                maxScore = maxScore,
                                note = rec.note.ifBlank { null }
                            )
                        }
                    }
                }

                // 3. Record Homework if not skipped
                if (!state.skipHomework) {
                    state.homeworkMap.forEach { (studentId, hw) ->
                        homeworkRepository.addHomework(
                            studentId = studentId,
                            date = date,
                            title = hw.title,
                            status = hw.status,
                            note = hw.note.ifBlank { null }
                        )
                    }
                }

                // 4. Record Exam if not skipped
                if (!state.skipExam) {
                    state.examsMap.forEach { (studentId, ex) ->
                        if (ex.scoreStr.toDoubleOrNull() != null) {
                            val score = ex.scoreStr.toDoubleOrNull() ?: 0.0
                            val maxScore = ex.maxScoreStr.toDoubleOrNull() ?: 10.0
                            examRepository.addExam(
                                studentId = studentId,
                                date = date,
                                examName = ex.examName,
                                subject = state.selectedGradeName,
                                score = score,
                                maxScore = maxScore,
                                note = ex.note.ifBlank { null }
                            )
                        }
                    }
                }

                _uiState.update { it.copy(isSaving = false, isFinished = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "حدث خطأ أثناء حفظ الحصة") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
