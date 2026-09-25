package com.example.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Exam
import com.example.core.model.Grade
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.Recitation
import com.example.core.model.Student
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.GradeRepository
import com.example.data.repository.HomeworkRepository
import com.example.data.repository.RecitationRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

enum class ReportsTab(val titleAr: String) {
    STUDENT("تقرير طالب"),
    GROUP("تقرير مجموعة")
}

data class StudentReportData(
    val student: Student,
    val attendancePercent: Float,
    val presentCount: Int,
    val absentCount: Int,
    val recitationAvg: Float,
    val recitationCount: Int,
    val homeworkPercent: Float,
    val homeworkCompletedCount: Int,
    val homeworkIncompleteCount: Int,
    val examAvg: Float,
    val examCount: Int,
    val attendanceDetails: List<Attendance>,
    val recitationDetails: List<Recitation>,
    val homeworkDetails: List<Homework>,
    val examDetails: List<Exam>
)

data class GroupReportData(
    val grade: Grade,
    val totalStudents: Int,
    val avgAttendance: Float,
    val totalRecitations: Int,
    val homeworkCompleted: Int,
    val homeworkIncomplete: Int,
    val totalExams: Int,
    val avgExamScore: Float,
    val studentStats: List<StudentGroupMetric>
)

data class StudentGroupMetric(
    val student: Student,
    val attendanceRate: Float,
    val recitationRate: Float,
    val examRate: Float
)

data class ReportsUiState(
    val isLoading: Boolean = false,
    val selectedTab: ReportsTab = ReportsTab.STUDENT,
    val startDate: LocalDate = LocalDate.now().minusDays(30),
    val endDate: LocalDate = LocalDate.now(),
    val students: List<Student> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val selectedStudentId: String? = null,
    val selectedGradeId: String? = null,
    val studentReport: StudentReportData? = null,
    val groupReport: GroupReportData? = null
)

class ReportsViewModel(
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val homeworkRepository: HomeworkRepository = RepositoryProvider.homeworkRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ReportsTab.STUDENT)
    private val _startDate = MutableStateFlow(LocalDate.now().minusDays(30))
    private val _endDate = MutableStateFlow(LocalDate.now())
    private val _selectedStudentId = MutableStateFlow<String?>(null)
    private val _selectedGradeId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                combine(
                    _selectedTab,
                    _startDate,
                    _endDate,
                    _selectedStudentId,
                    _selectedGradeId
                ) { tab, start, end, studentId, gradeId ->
                    arrayOf<Any?>(tab, start, end, studentId, gradeId)
                },
                studentRepository.getStudents(),
                gradeRepository.getGrades(),
                attendanceRepository.getAllAttendanceForTeacher(),
                recitationRepository.getAllRecitationsForTeacher(),
                examRepository.getAllExamsForTeacher(),
                homeworkRepository.getHomeworkForTeacher()
            ) { values ->
                val params = values[0] as Array<Any?>
                val students = values[1] as List<Student>
                val grades = values[2] as List<Grade>
                val attendance = values[3] as List<Attendance>
                val recitations = values[4] as List<Recitation>
                val exams = values[5] as List<Exam>
                val homework = values[6] as List<Homework>

                val tab = params[0] as ReportsTab
                val start = params[1] as LocalDate
                val end = params[2] as LocalDate
                val studentId = params[3] as String?
                val gradeId = params[4] as String?

                val filteredStudents = students.filter { it.deletedAt == null }
                val startStr = start.toString()
                val endStr = end.toString()

                val studentReport = if (tab == ReportsTab.STUDENT && studentId != null) {
                    val student = filteredStudents.find { it.studentId == studentId }
                    if (student != null) {
                        val studentAttendance = attendance.filter { it.studentId == studentId && it.date in startStr..endStr }
                        val studentRecitations = recitations.filter { it.studentId == studentId && it.date in startStr..endStr }
                        val studentExams = exams.filter { it.studentId == studentId && it.date in startStr..endStr }
                        val studentHomework = homework.filter { it.studentId == studentId && it.date in startStr..endStr }

                        val attTotal = studentAttendance.size
                        val attPresent = studentAttendance.count { it.status == AttendanceStatus.PRESENT }
                        val attPercent = if (attTotal > 0) (attPresent.toFloat() / attTotal) * 100f else 0f

                        val recTotal = studentRecitations.size
                        val recAvg = if (recTotal > 0) studentRecitations.map { (it.score / it.maxScore) * 100 }.average().toFloat() else 0f

                        val hwTotal = studentHomework.size
                        val hwDone = studentHomework.count { it.status == HomeworkStatus.COMPLETED }
                        val hwPercent = if (hwTotal > 0) (hwDone.toFloat() / hwTotal) * 100f else 0f

                        val exTotal = studentExams.size
                        val exAvg = if (exTotal > 0) studentExams.map { (it.score / it.maxScore) * 100 }.average().toFloat() else 0f

                        StudentReportData(
                            student = student,
                            attendancePercent = attPercent,
                            presentCount = attPresent,
                            absentCount = studentAttendance.count { it.status == AttendanceStatus.ABSENT },
                            recitationAvg = recAvg,
                            recitationCount = recTotal,
                            homeworkPercent = hwPercent,
                            homeworkCompletedCount = hwDone,
                            homeworkIncompleteCount = studentHomework.count { it.status == HomeworkStatus.NOT_COMPLETED },
                            examAvg = exAvg,
                            examCount = exTotal,
                            attendanceDetails = studentAttendance.sortedByDescending { it.date },
                            recitationDetails = studentRecitations.sortedByDescending { it.date },
                            homeworkDetails = studentHomework.sortedByDescending { it.date },
                            examDetails = studentExams.sortedByDescending { it.date }
                        )
                    } else null
                } else null

                val groupReport = if (tab == ReportsTab.GROUP && gradeId != null) {
                    val grade = grades.find { it.id == gradeId }
                    if (grade != null) {
                        val gradeStudents = filteredStudents.filter { it.gradeId == gradeId }
                        val studentIdsForGrade = gradeStudents.map { it.studentId }.toSet()

                        val groupAttendance = attendance.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }
                        val groupRecitations = recitations.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }
                        val groupExams = exams.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }
                        val groupHomework = homework.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }

                        val studentStats = gradeStudents.map { s ->
                            val sAtt = groupAttendance.filter { it.studentId == s.studentId }
                            val sRec = groupRecitations.filter { it.studentId == s.studentId }
                            val sEx = groupExams.filter { it.studentId == s.studentId }

                            val sAttRate = if (sAtt.isNotEmpty()) (sAtt.count { it.status == AttendanceStatus.PRESENT }.toFloat() / sAtt.size) * 100f else 0f
                            val sRecRate = if (sRec.isNotEmpty()) sRec.map { (it.score / it.maxScore) * 100 }.average().toFloat() else 0f
                            val sExRate = if (sEx.isNotEmpty()) sEx.map { (it.score / it.maxScore) * 100 }.average().toFloat() else 0f

                            StudentGroupMetric(s, sAttRate, sRecRate, sExRate)
                        }

                        val avgAtt = if (groupAttendance.isNotEmpty()) (groupAttendance.count { it.status == AttendanceStatus.PRESENT }.toFloat() / groupAttendance.size) * 100f else 0f
                        val avgEx = if (groupExams.isNotEmpty()) groupExams.map { (it.score / it.maxScore) * 100 }.average().toFloat() else 0f

                        GroupReportData(
                            grade = grade,
                            totalStudents = gradeStudents.size,
                            avgAttendance = avgAtt,
                            totalRecitations = groupRecitations.size,
                            homeworkCompleted = groupHomework.count { it.status == HomeworkStatus.COMPLETED },
                            homeworkIncomplete = groupHomework.count { it.status == HomeworkStatus.NOT_COMPLETED },
                            totalExams = groupExams.size,
                            avgExamScore = avgEx,
                            studentStats = studentStats
                        )
                    } else null
                } else null

                ReportsUiState(
                    isLoading = false,
                    selectedTab = tab,
                    startDate = start,
                    endDate = end,
                    students = filteredStudents,
                    grades = grades.sortedBy { it.displayOrder },
                    selectedStudentId = studentId,
                    selectedGradeId = gradeId,
                    studentReport = studentReport,
                    groupReport = groupReport
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectTab(tab: ReportsTab) {
        _selectedTab.value = tab
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        _startDate.value = start
        _endDate.value = end
    }

    fun selectStudent(studentId: String?) {
        _selectedStudentId.value = studentId
    }

    fun selectGrade(gradeId: String?) {
        _selectedGradeId.value = gradeId
    }
}
