package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Grade
import com.example.core.model.Teacher
import com.example.core.model.TeacherStats
import com.example.core.model.Student
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Recitation
import com.example.core.model.Exam
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.GradeRepository
import com.example.data.repository.RecitationRepository
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.SettingsRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.TeacherRepository
import com.example.data.sync.SyncManager
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FollowUpStudent(
    val studentId: String,
    val studentName: String,
    val reason: String
)

data class TodayGroup(
    val id: String,
    val name: String,
    val gradeName: String,
    val startTime: String,
    val endTime: String,
    val location: String?,
    val studentCount: Int
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val grades: List<Grade> = emptyList(),
    val stats: TeacherStats = TeacherStats(),
    val teacherName: String = "",
    val avatarUrl: String? = null,
    val currentDate: String = "",
    val todayPresentCount: Int = 0,
    val todayAbsentCount: Int = 0,
    val todayNotRecordedCount: Int = 0,
    val attendancePercentage: Float = 0f,
    val recitationsThisMonth: Int = 0,
    val examsThisMonth: Int = 0,
    val followUpStudents: List<FollowUpStudent> = emptyList(),
    val todayGroups: List<TodayGroup> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus()
)

class HomeViewModel(
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val homeworkRepository: com.example.data.repository.HomeworkRepository = RepositoryProvider.homeworkRepository,
    private val groupRepository: com.example.data.repository.GroupRepository = RepositoryProvider.groupRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val syncManager: SyncManager = RepositoryProvider.syncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observeSyncStatus()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncManager.syncStatus.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
    }

    fun retrySync() {
        syncManager.retrySync()
    }

    fun loadData() {
        val dateFormat = SimpleDateFormat("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar"))
        val todayStr = dateFormat.format(Date())

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Fetch current teacher profile first
            val teacher = teacherRepository.fetchCurrentTeacher()

            // 2. Fetch/refresh grades
            val initialGrades = gradeRepository.refreshGrades(teacher?.educationalStage)

            // 3. Fetch monthly & daily stats
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1

            val (todayPresent, todayAbsent) = attendanceRepository.getTodayAttendanceCount()
            val recThisMonth = recitationRepository.getRecitationsCountThisMonth(currentYear, currentMonth)
            val exThisMonth = examRepository.getExamsCountThisMonth(currentYear, currentMonth)

            val initialTeacherName = teacher?.fullName?.ifBlank { null } ?: teacher?.email ?: ""
            _uiState.update {
                it.copy(
                    teacherName = initialTeacherName,
                    avatarUrl = teacher?.avatarUrl,
                    currentDate = todayStr,
                    todayPresentCount = todayPresent,
                    todayAbsentCount = todayAbsent,
                    recitationsThisMonth = recThisMonth,
                    examsThisMonth = exThisMonth
                )
            }

            // 4. Observe reactive streams continuously
            val firstFive = combine(
                gradeRepository.getGrades(),
                studentRepository.getStats(),
                teacherRepository.getCurrentTeacher(),
                studentRepository.getStudents(),
                attendanceRepository.getAllAttendanceForTeacher()
            ) { liveGrades, stats, liveTeacher, students, attendance ->
                DataBundle1(liveGrades, stats, liveTeacher, students, attendance)
            }

            val groupsFlow = if (teacher != null) groupRepository.observeGroups(teacher.id) else kotlinx.coroutines.flow.flowOf(emptyList())

            val lastThree = combine(
                recitationRepository.getAllRecitationsForTeacher(),
                examRepository.getAllExamsForTeacher(),
                homeworkRepository.getHomeworkForTeacher(),
                groupsFlow
            ) { recitations, exams, homework, groups ->
                DataBundle2(recitations, exams, homework, groups)
            }

            val groupDays = if (teacher != null) {
                try {
                    com.example.data.local.DatabaseProvider.getDatabase().groupDayDao().getGroupDaysByTeacher(teacher.id)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val dayOfWeekArabic = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> "السبت"
                Calendar.SUNDAY -> "الأحد"
                Calendar.MONDAY -> "الاثنين"
                Calendar.TUESDAY -> "الثلاثاء"
                Calendar.WEDNESDAY -> "الأربعاء"
                Calendar.THURSDAY -> "الخميس"
                Calendar.FRIDAY -> "الجمعة"
                else -> ""
            }

            combine(firstFive, lastThree) { b1, b2 ->
                val liveGrades = b1.liveGrades
                val stats = b1.stats
                val liveTeacher = b1.liveTeacher
                val students = b1.students
                val attendance = b1.attendance

                val recitations = b2.recitations
                val exams = b2.exams
                val homework = b2.homework
                val groups = b2.groups

                val currentTeacher = liveTeacher ?: teacher
                val name = currentTeacher?.fullName?.ifBlank { null } ?: currentTeacher?.email ?: ""
                val avatar = currentTeacher?.avatarUrl
                val teacherStage = currentTeacher?.educationalStage

                val sourceGrades = if (liveGrades.isNotEmpty()) liveGrades else initialGrades
                val filteredGrades = if (!teacherStage.isNullOrBlank()) {
                    sourceGrades.filter { com.example.core.model.EducationalStages.isGradeMatchingStage(it.name, teacherStage) }
                } else {
                    sourceGrades
                }
                val updatedGrades = filteredGrades.map { grade ->
                    grade.copy(studentCount = stats.gradeCounts[grade.id] ?: 0)
                }.sortedBy { it.displayOrder }

                val followUp = calculateFollowUpStudents(students, attendance, recitations, exams, homework)

                val totalStudents = stats.totalStudents
                val notRecorded = (totalStudents - (todayPresent + todayAbsent)).coerceAtLeast(0)
                val totalRecorded = todayPresent + todayAbsent
                val attendancePct = if (totalStudents > 0) (totalRecorded.toFloat() / totalStudents.toFloat()) * 100f else 0f

                val groupsToday = groups.filter { g ->
                    g.active && groupDays.any { gd -> gd.groupId == g.id && gd.dayOfWeek == dayOfWeekArabic }
                }.map { g ->
                    val gradeName = updatedGrades.find { gd -> gd.id == g.gradeId }?.name ?: ""
                    val count = students.count { st -> st.groupId == g.id }
                    TodayGroup(
                        id = g.id,
                        name = g.name,
                        gradeName = gradeName,
                        startTime = g.startTime,
                        endTime = g.endTime,
                        location = g.location,
                        studentCount = count
                    )
                }.sortedBy { tg -> tg.startTime }

                HomeUiState(
                    isLoading = false,
                    grades = updatedGrades,
                    stats = stats,
                    teacherName = name,
                    avatarUrl = avatar,
                    currentDate = todayStr,
                    todayPresentCount = todayPresent,
                    todayAbsentCount = todayAbsent,
                    todayNotRecordedCount = notRecorded,
                    attendancePercentage = attendancePct,
                    recitationsThisMonth = recThisMonth,
                    examsThisMonth = exThisMonth,
                    followUpStudents = followUp,
                    todayGroups = groupsToday
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private data class DataBundle1(
        val liveGrades: List<Grade>,
        val stats: TeacherStats,
        val liveTeacher: Teacher?,
        val students: List<Student>,
        val attendance: List<Attendance>
    )

    private data class DataBundle2(
        val recitations: List<Recitation>,
        val exams: List<Exam>,
        val homework: List<Homework>,
        val groups: List<com.example.core.model.Group>
    )

    private fun calculateFollowUpStudents(
        students: List<Student>,
        attendance: List<Attendance>,
        recitations: List<Recitation>,
        exams: List<Exam>,
        homework: List<Homework>
    ): List<FollowUpStudent> {
        val followUpList = mutableListOf<FollowUpStudent>()
        val thirtyDaysAgo = LocalDate.now().minusDays(30).toString()
        val fourteenDaysAgo = LocalDate.now().minusDays(14).toString()

        students.forEach { student ->
            val studentAttendance = attendance.filter { it.studentId == student.studentId }
            val studentRecitations = recitations.filter { it.studentId == student.studentId }
            val studentExams = exams.filter { it.studentId == student.studentId }
            val studentHomework = homework.filter { it.studentId == student.studentId }

            // Rule A: 3+ absences in last 30 days
            val absencesInLast30Days = studentAttendance.filter { 
                it.status == AttendanceStatus.ABSENT && it.date >= thirtyDaysAgo 
            }.size
            if (absencesInLast30Days >= 3) {
                followUpList.add(FollowUpStudent(student.studentId, student.fullName, "غياب متكرر — $absencesInLast30Days مرات خلال آخر 30 يوم"))
                return@forEach
            }

            // Rule B: Absent in last 2 recorded sessions
            if (studentAttendance.size >= 2) {
                val lastTwo = studentAttendance.take(2)
                if (lastTwo.all { it.status == AttendanceStatus.ABSENT }) {
                    followUpList.add(FollowUpStudent(student.studentId, student.fullName, "غياب في آخر حصتين"))
                    return@forEach
                }
            }

            // Rule C: 2+ incomplete homework in last 30 days
            val incompleteHomeworkLast30 = studentHomework.filter {
                it.status == HomeworkStatus.NOT_COMPLETED && it.date >= thirtyDaysAgo
            }.size
            if (incompleteHomeworkLast30 >= 2) {
                followUpList.add(FollowUpStudent(student.studentId, student.fullName, "واجبان غير مكتملين أو أكثر"))
                return@forEach
            }

            // Rule D: No recitation in last 14 days
            if (studentRecitations.isNotEmpty()) {
                val lastRecitationDate = studentRecitations.maxOf { it.date }
                if (lastRecitationDate < fourteenDaysAgo) {
                    followUpList.add(FollowUpStudent(student.studentId, student.fullName, "لم يسجل تسميع منذ 14 يوم"))
                    return@forEach
                }
            }

            // Rule E: Average of last 3 exams < 50%
            if (studentExams.isNotEmpty()) {
                val lastThreeExams = studentExams.take(3)
                val avgPct = lastThreeExams.map { (it.score / it.maxScore) * 100.0 }.average()
                if (avgPct < 50.0) {
                    followUpList.add(FollowUpStudent(student.studentId, student.fullName, "درجات منخفضة في آخر الاختبارات"))
                    return@forEach
                }
            }
        }

        return followUpList.take(5)
    }
}
