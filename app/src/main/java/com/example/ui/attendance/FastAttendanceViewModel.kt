package com.example.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.Grade
import com.example.core.model.LessonPayment
import com.example.core.model.Student
import com.example.BuildConfig
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.GradeRepository
import com.example.data.repository.PaymentRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.StudentRepository
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseStudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

enum class ScanResultType {
    SUCCESS_PRESENT,
    ALREADY_PRESENT,
    NOT_FOUND,
    WRONG_GROUP
}

data class ScanFeedback(
    val student: Student? = null,
    val type: ScanResultType,
    val rawCode: String = ""
)

data class FastAttendanceSummary(
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val totalCount: Int = 0,
    val date: String = ""
)

data class FastAttendanceUiState(
    val searchQuery: String = "",
    val selectedGradeId: String? = null,
    val grades: List<Grade> = emptyList(),
    val allStudentsInScope: List<Student> = emptyList(),
    val filteredStudents: List<Student> = emptyList(),
    val presentStudentIds: Set<String> = emptySet(),
    val currentDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    val currentYear: Int = LocalDate.now().year,
    val currentMonth: Int = LocalDate.now().monthValue,
    val paymentsMap: Map<String, LessonPayment> = emptyMap(),
    val paidCount: Int = 0,
    val unpaidCount: Int = 0,
    val isSaving: Boolean = false,
    val saveSummary: FastAttendanceSummary? = null,
    val errorMessage: String? = null,
    val isScannerActive: Boolean = false,
    val scanFeedback: ScanFeedback? = null,
    val recentScannedStudents: List<Student> = emptyList()
)

class FastAttendanceViewModel(
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val paymentRepository: PaymentRepository = RepositoryProvider.paymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FastAttendanceUiState())
    val uiState: StateFlow<FastAttendanceUiState> = _uiState.asStateFlow()

    private var allStudentsList: List<Student> = emptyList()

    // O(1) In-memory lookup maps for instant scanning without UI lag
    private var studentCodeMap: Map<String, Student> = emptyMap()
    private var inScopeStudentIdsSet: Set<String> = emptySet()

    init {
        loadData()
        loadMonthlyPayments()
    }

    fun setInitialGradeId(gradeId: String?) {
        if (!gradeId.isNullOrBlank()) {
            _uiState.update { it.copy(selectedGradeId = gradeId) }
            applyFilters()
        }
    }

    private fun updateLookupMaps(activeStudents: List<Student>, selectedGradeId: String?) {
        studentCodeMap = activeStudents.associateBy { it.studentCode.trim().lowercase() }
        val scope = if (selectedGradeId != null) {
            activeStudents.filter { it.gradeId == selectedGradeId }
        } else {
            activeStudents
        }
        inScopeStudentIdsSet = scope.mapTo(HashSet()) { it.studentId }
    }

    private fun loadData() {
        viewModelScope.launch {
            (gradeRepository as? SupabaseGradeRepository)?.fetchGrades()
            (studentRepository as? SupabaseStudentRepository)?.fetchStudents()
        }

        viewModelScope.launch {
            combine(
                gradeRepository.getGrades(),
                studentRepository.getStudents()
            ) { gradesList, studentsList ->
                val activeStudents = studentsList.filter { it.deletedAt == null }
                allStudentsList = activeStudents
                Pair(gradesList, activeStudents)
            }.collect { (gradesList, activeStudents) ->
                val currentGradeId = _uiState.value.selectedGradeId
                updateLookupMaps(activeStudents, currentGradeId)

                val scope = if (currentGradeId != null) {
                    activeStudents.filter { it.gradeId == currentGradeId }
                } else {
                    activeStudents
                }

                val currentPayments = _uiState.value.paymentsMap
                val paid = scope.count { currentPayments[it.studentId]?.isPaid == true }
                val unpaid = (scope.size - paid).coerceAtLeast(0)

                _uiState.update { state ->
                    state.copy(
                        grades = gradesList,
                        allStudentsInScope = scope,
                        paidCount = paid,
                        unpaidCount = unpaid
                    )
                }
                applyFilters()
            }
        }
    }

    private fun loadMonthlyPayments() {
        val year = _uiState.value.currentYear
        val month = _uiState.value.currentMonth

        viewModelScope.launch {
            paymentRepository.getMonthlyPayments(year, month)
                .catch { error ->
                    if (BuildConfig.DEBUG) android.util.Log.e("FastAttendanceVM", "Failed to load monthly payments for $year-$month", error)
                    _uiState.update {
                        it.copy(errorMessage = "تعذر تحميل بيانات اشتراكات هذا الشهر: ${error.message}")
                    }
                }
                .collect { paymentsList ->
                    val map = paymentsList.associateBy { it.studentId }
                    _uiState.update { state ->
                        val scope = state.allStudentsInScope
                        val paid = scope.count { map[it.studentId]?.isPaid == true }
                        val unpaid = (scope.size - paid).coerceAtLeast(0)
                        state.copy(
                            paymentsMap = map,
                            paidCount = paid,
                            unpaidCount = unpaid
                        )
                    }
                }
        }
    }

    fun togglePaymentStatus(studentId: String) {
        val currentState = _uiState.value
        val year = currentState.currentYear
        val month = currentState.currentMonth
        val oldPayment = currentState.paymentsMap[studentId]
        val oldIsPaid = oldPayment?.isPaid ?: false
        val newIsPaid = !oldIsPaid

        // 1. Optimistic UI update
        val updatedPayment = (oldPayment ?: LessonPayment(
            studentId = studentId,
            year = year,
            month = month,
            amount = 0.0
        )).copy(
            isPaid = newIsPaid,
            paidAt = if (newIsPaid) java.time.OffsetDateTime.now().toString() else null
        )

        val newPaymentsMap = currentState.paymentsMap.toMutableMap().apply {
            put(studentId, updatedPayment)
        }

        val scope = currentState.allStudentsInScope
        val paid = scope.count { newPaymentsMap[it.studentId]?.isPaid == true }
        val unpaid = (scope.size - paid).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                paymentsMap = newPaymentsMap,
                paidCount = paid,
                unpaidCount = unpaid
            )
        }

        // 2. Perform backend update
        viewModelScope.launch {
            val result = paymentRepository.setPaymentStatus(
                studentId = studentId,
                year = year,
                month = month,
                isPaid = newIsPaid,
                amount = oldPayment?.amount ?: 0.0
            )

            if (result.isFailure) {
                // Rollback optimistic state
                val rollbackMap = _uiState.value.paymentsMap.toMutableMap()
                if (oldPayment != null) {
                    rollbackMap[studentId] = oldPayment
                } else {
                    rollbackMap.remove(studentId)
                }
                val rollbackScope = _uiState.value.allStudentsInScope
                val rbPaid = rollbackScope.count { rollbackMap[it.studentId]?.isPaid == true }
                val rbUnpaid = (rollbackScope.size - rbPaid).coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        paymentsMap = rollbackMap,
                        paidCount = rbPaid,
                        unpaidCount = rbUnpaid,
                        errorMessage = "تعذر تحديث حالة الدفع، يرجى المحاولة لاحقاً."
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onGradeSelected(gradeId: String?) {
        updateLookupMaps(allStudentsList, gradeId)

        _uiState.update { state ->
            val scope = if (gradeId != null) {
                allStudentsList.filter { it.gradeId == gradeId }
            } else {
                allStudentsList
            }
            val paid = scope.count { state.paymentsMap[it.studentId]?.isPaid == true }
            val unpaid = (scope.size - paid).coerceAtLeast(0)
            state.copy(
                selectedGradeId = gradeId,
                allStudentsInScope = scope,
                paidCount = paid,
                unpaidCount = unpaid
            )
        }
        applyFilters()
    }

    fun toggleStudentPresent(studentId: String) {
        _uiState.update { state ->
            val currentPresent = state.presentStudentIds.toMutableSet()
            if (currentPresent.contains(studentId)) {
                currentPresent.remove(studentId)
            } else {
                currentPresent.add(studentId)
            }
            state.copy(presentStudentIds = currentPresent)
        }
    }

    fun toggleScanner(active: Boolean) {
        _uiState.update { it.copy(isScannerActive = active, scanFeedback = null) }
    }

    private var feedbackJob: Job? = null
    private val lastScannedCodeRef = AtomicReference("")

    fun processScannedBarcode(rawBarcode: String) {
        val code = rawBarcode.trim()
        if (code.isBlank()) return

        val state = _uiState.value
        // O(1) Instant lookup
        val foundGlobalStudent = studentCodeMap[code.lowercase()]

        if (foundGlobalStudent == null) {
            updateScanFeedback(ScanFeedback(
                student = null,
                type = ScanResultType.NOT_FOUND,
                rawCode = code
            ))
            return
        }

        // Grade isolation check
        if (state.selectedGradeId != null && foundGlobalStudent.gradeId != state.selectedGradeId) {
            updateScanFeedback(ScanFeedback(
                student = foundGlobalStudent,
                type = ScanResultType.WRONG_GROUP,
                rawCode = code
            ))
            return
        }

        // O(1) Instant duplicate check
        if (state.presentStudentIds.contains(foundGlobalStudent.studentId)) {
            updateScanFeedback(ScanFeedback(
                student = foundGlobalStudent,
                type = ScanResultType.ALREADY_PRESENT,
                rawCode = code
            ))
            return
        }

        // Successfully mark present
        val updatedPresent = state.presentStudentIds.toMutableSet().apply {
            add(foundGlobalStudent.studentId)
        }
        val updatedRecent = (listOf(foundGlobalStudent) + state.recentScannedStudents.filter { it.studentId != foundGlobalStudent.studentId }).take(5)

        _uiState.update {
            it.copy(
                presentStudentIds = updatedPresent,
                recentScannedStudents = updatedRecent
            )
        }
        
        updateScanFeedback(ScanFeedback(
            student = foundGlobalStudent,
            type = ScanResultType.SUCCESS_PRESENT,
            rawCode = code
        ))
    }

    private fun updateScanFeedback(feedback: ScanFeedback) {
        feedbackJob?.cancel()
        _uiState.update { it.copy(scanFeedback = feedback) }
        feedbackJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(scanFeedback = null) }
        }
    }

    fun clearScanFeedback() {
        feedbackJob?.cancel()
        _uiState.update { it.copy(scanFeedback = null) }
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val query = state.searchQuery.trim().lowercase()
            val scope = if (state.selectedGradeId != null) {
                allStudentsList.filter { it.gradeId == state.selectedGradeId }
            } else {
                allStudentsList
            }

            val filtered = scope.filter { student ->
                val matchesQuery = query.isEmpty() ||
                        student.fullName.lowercase().contains(query) ||
                        student.studentCode.lowercase().contains(query)
                matchesQuery
            }

            val paid = scope.count { state.paymentsMap[it.studentId]?.isPaid == true }
            val unpaid = (scope.size - paid).coerceAtLeast(0)

            state.copy(
                allStudentsInScope = scope,
                filteredStudents = filtered,
                paidCount = paid,
                unpaidCount = unpaid
            )
        }
    }

    fun finishAttendance() {
        val currentState = _uiState.value
        if (currentState.isSaving) return // Prevent concurrent duplicate submissions
        val scopeStudents = currentState.allStudentsInScope
        if (scopeStudents.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "لا يوجد طلاب في الصف/المجموعة المحددة.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, isScannerActive = false) }

        viewModelScope.launch {
            try {
                val date = currentState.currentDate
                val presentIds = currentState.presentStudentIds

                val records = scopeStudents.map { student ->
                    val status = if (presentIds.contains(student.studentId)) {
                        AttendanceStatus.PRESENT
                    } else {
                        AttendanceStatus.ABSENT
                    }
                    BatchAttendanceItemDto(
                        studentId = student.studentId,
                        status = status.name,
                        note = null
                    )
                }

                val result = attendanceRepository.recordBatchAttendance(
                    date = date,
                    records = records
                )

                if (result.isSuccess) {
                    val summary = result.getOrNull()
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSummary = FastAttendanceSummary(
                                presentCount = summary?.presentCount ?: 0,
                                absentCount = summary?.absentCount ?: 0,
                                totalCount = summary?.total ?: scopeStudents.size,
                                date = date
                            )
                        )
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = ex?.message ?: "فشل حفظ تسجيل الحضور، يرجى التحقق من الاتصال بالإنترنت والمحاولة مرة أخرى."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "حدث خطأ غير متوقع أثناء حفظ تسجيل الحضور."
                    )
                }
            }
        }
    }

    fun dismissSummary() {
        _uiState.update {
            it.copy(
                saveSummary = null,
                presentStudentIds = emptySet(),
                searchQuery = ""
            )
        }
        applyFilters()
    }

    fun setStudentsForScope(scopeStudents: List<Student>, allStudents: List<Student>) {
        val activeStudents = allStudents.filter { it.deletedAt == null }
        allStudentsList = activeStudents
        val selectedGradeId = _uiState.value.selectedGradeId
        updateLookupMaps(activeStudents, selectedGradeId)

        val actualScope = if (selectedGradeId != null) {
            activeStudents.filter { it.gradeId == selectedGradeId }
        } else {
            scopeStudents.filter { it.deletedAt == null }
        }

        _uiState.update { state ->
            val paid = actualScope.count { state.paymentsMap[it.studentId]?.isPaid == true }
            val unpaid = (actualScope.size - paid).coerceAtLeast(0)
            state.copy(
                allStudentsInScope = actualScope,
                filteredStudents = actualScope,
                paidCount = paid,
                unpaidCount = unpaid
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}


