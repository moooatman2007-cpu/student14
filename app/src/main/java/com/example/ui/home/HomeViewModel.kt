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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        _uiState.update { it.copy(currentDate = todayStr) }

        // 1. Fetch teacher, grades and monthly stats concurrently in background without blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val teacherDeferred = async { teacherRepository.fetchCurrentTeacher() }
                val todayAttDeferred = async { attendanceRepository.getTodayAttendanceCount() }
                val recCountDeferred = async { recitationRepository.getRecitationsCountThisMonth(currentYear, currentMonth) }
                val exCountDeferred = async { examRepository.getExamsCountThisMonth(currentYear, currentMonth) }

                val teacher = teacherDeferred.await()
                val (todayPresent, todayAbsent) = todayAttDeferred.await()
                val recThisMonth = recCountDeferred.await()
                val exThisMonth = exCountDeferred.await()

                // Refresh grades in background
                gradeRepository.refreshGrades(teacher?.educationalStage)

                val teacherName = teacher?.fullName?.ifBlank { null } ?: teacher?.email ?: ""
                _uiState.update { current ->
                    val notRecorded = (current.stats.totalStudents - (todayPresent + todayAbsent)).coerceAtLeast(0)
                    val totalRecorded = todayPresent + todayAbsent
                    val attendancePct = if (current.stats.totalStudents > 0) (totalRecorded.toFloat() / current.stats.totalStudents.toFloat()) * 100f else 0f
                    current.copy(
                        teacherName = if (current.teacherName.isBlank()) teacherName else current.teacherName,
                        avatarUrl = current.avatarUrl ?: teacher?.avatarUrl,
                        todayPresentCount = todayPresent,
                        todayAbsentCount = todayAbsent,
                        todayNotRecordedCount = notRecorded,
                        attendancePercentage = attendancePct,
                        recitationsThisMonth = recThisMonth,
                        examsThisMonth = exThisMonth
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Observe reactive local Room streams immediately (Stale-While-Revalidate)
        viewModelScope.launch {
            val firstFive = combine(
                gradeRepository.getGrades(),
                studentRepository.getStats(),
                teacherRepository.getCurrentTeacher(),
                studentRepository.getStudents(),
                attendanceRepository.getAllAttendanceForTeacher()
            ) { liveGrades, stats, liveTeacher, students, attendance ->
                DataBundle1(liveGrades, stats, liveTeacher, students, attendance)
            }

            @OptIn(ExperimentalCoroutinesApi::class)
            val groupsFlow = teacherRepository.getCurrentTeacher().flatMapLatest { teacher ->
                if (teacher != null) groupRepository.observeGroups(teacher.id) else flowOf(emptyList())
            }

            val lastThree = combine(
                recitationRepository.getAllRecitationsForTeacher(),
                examRepository.getAllExamsForTeacher(),
                homeworkRepository.getHomeworkForTeacher(),
                groupsFlow
            ) { recitations, exams, homework, groups ->
                DataBundle2(recitations, exams, homework, groups)
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

                val currentTeacher = liveTeacher
                val name = currentTeacher?.fullName?.ifBlank { null } ?: currentTeacher?.email ?: ""
                val avatar = currentTeacher?.avatarUrl
                val teacherStage = currentTeacher?.educationalStage

                val sourceGrades = liveGrades
                val filteredGrades = if (!teacherStage.isNullOrBlank()) {
                    sourceGrades.filter { com.example.core.model.EducationalStages.isGradeMatchingStage(it.name, teacherStage) }
                } else {
                    sourceGrades
                }
                val updatedGrades = filteredGrades.map { grade ->
                    grade.copy(studentCount = stats.gradeCounts[grade.id] ?: 0)
                }.sortedBy { it.displayOrder }

                val followUp = calculateFollowUpStudents(students, attendance, recitations, exams, homework)

                val groupDays = if (currentTeacher != null) {
                    try {
                        com.example.data.local.DatabaseProvider.getDatabase().groupDayDao().getGroupDaysByTeacher(currentTeacher.id)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

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

                val currentPres = _uiState.value.todayPresentCount
                val currentAbs = _uiState.value.todayAbsentCount
                val totalStudents = stats.totalStudents
                val notRecorded = (totalStudents - (currentPres + currentAbs)).coerceAtLeast(0)
                val totalRecorded = currentPres + currentAbs
                val attendancePct = if (totalStudents > 0) (totalRecorded.toFloat() / totalStudents.toFloat()) * 100f else 0f

                HomeUiState(
                    isLoading = false,
                    grades = updatedGrades,
                    stats = stats,
                    teacherName = name,
                    avatarUrl = avatar,
                    currentDate = todayStr,
                    todayPresentCount = currentPres,
                    todayAbsentCount = currentAbs,
                    todayNotRecordedCount = notRecorded,
                    attendancePercentage = attendancePct,
                    recitationsThisMonth = _uiState.value.recitationsThisMonth,
                    examsThisMonth = _uiState.value.examsThisMonth,
                    followUpStudents = followUp,
                    todayGroups = groupsToday
                )
            }.flowOn(Dispatchers.Default)
            .collect { newState ->
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

        // O(1) indexed lookups
        val attendanceByStudent = attendance.groupBy { it.studentId }
        val recitationsByStudent = recitations.groupBy { it.studentId }
        val examsByStudent = exams.groupBy { it.studentId }
        val homeworkByStudent = homework.groupBy { it.studentId }

        students.forEach { student ->
            val studentAttendance = attendanceByStudent[student.studentId] ?: emptyList()
            val studentRecitations = recitationsByStudent[student.studentId] ?: emptyList()
            val studentExams = examsByStudent[student.studentId] ?: emptyList()
            val studentHomework = homeworkByStudent[student.studentId] ?: emptyList()

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
