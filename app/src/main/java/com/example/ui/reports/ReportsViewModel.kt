package com.example.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.*
import com.example.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit
import com.example.data.SupabaseClientProvider

enum class ReportsTab(val titleAr: String) {
    GROUP_DAILY("تقرير مجموعة"),
    GRADE_DAILY("تقرير صف"),
    TEACHER_DAILY("تقرير المدرس"),
    STUDENT("تقرير طالب");

    companion object {
        val GROUP = GROUP_DAILY
    }
}

enum class DailyAttendanceStatus(val labelAr: String, val iconSymbol: String) {
    PRESENT("حاضر", "🟢"),
    ABSENT("غائب", "🔴"),
    NO_RECORD("لم يسجل", "⚪")
}

data class StudentDailyAttendance(
    val student: Student,
    val status: DailyAttendanceStatus,
    val note: String? = null,
    val attendance: Attendance? = null
) {
    val attendanceId: String? get() = attendance?.attendanceId
}

data class StudentDailyRecitation(
    val student: Student,
    val recitation: Recitation
)

data class StudentDailyExam(
    val student: Student,
    val exam: Exam
)

data class NotificationBatchSummary(
    val totalCount: Int,
    val createdCount: Int,
    val alreadyExistsCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val message: String
)

enum class GroupReportMode(val labelAr: String) {
    PERIOD("فترة زمنية"),
    DAILY("تقرير يومي")
}

data class StudentGroupPeriodAttendance(
    val student: Student,
    val primaryGroupName: String,
    val presentCount: Int,
    val absentCount: Int,
    val lateCount: Int,
    val excusedCount: Int,
    val totalRecordedSessions: Int,
    val attendanceRate: Float,
    val isGuest: Boolean
)

data class GroupPeriodReportData(
    val group: Group,
    val gradeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalSessionsHeld: Int,
    val totalStudents: Int,
    val avgAttendanceRate: Float,
    val studentList: List<StudentGroupPeriodAttendance>
)

data class GroupDailyReportData(
    val group: Group,
    val gradeName: String,
    val date: LocalDate,
    val totalStudents: Int,
    val presentCount: Int,
    val absentCount: Int,
    val noRecordCount: Int,
    val studentList: List<StudentDailyAttendance>,
    val dailyRecitations: List<StudentDailyRecitation> = emptyList(),
    val dailyExams: List<StudentDailyExam> = emptyList()
) {
    val absentStudents: List<StudentDailyAttendance>
        get() = studentList.filter { it.status == DailyAttendanceStatus.ABSENT && it.attendance != null }
}

data class GroupBreakdown(
    val group: Group,
    val totalStudents: Int,
    val presentCount: Int,
    val absentCount: Int,
    val noRecordCount: Int
)

data class GradeDailyReportData(
    val grade: Grade,
    val date: LocalDate,
    val totalStudents: Int,
    val totalGroups: Int,
    val presentCount: Int,
    val absentCount: Int,
    val noRecordCount: Int,
    val groupBreakdowns: List<GroupBreakdown>,
    val studentList: List<StudentDailyAttendance> = emptyList(),
    val dailyRecitations: List<StudentDailyRecitation> = emptyList(),
    val dailyExams: List<StudentDailyExam> = emptyList()
) {
    val absentStudents: List<StudentDailyAttendance>
        get() = studentList.filter { it.status == DailyAttendanceStatus.ABSENT && it.attendance != null }
}

data class StageDailyReport(
    val stageName: String,
    val totalStudents: Int,
    val totalGroups: Int,
    val presentCount: Int,
    val absentCount: Int,
    val noRecordCount: Int,
    val gradeBreakdowns: List<GradeDailyReportData>
)

data class TeacherDailyReportData(
    val date: LocalDate,
    val totalStudents: Int,
    val totalGroups: Int,
    val totalGradesWithGroups: Int,
    val presentCount: Int,
    val absentCount: Int,
    val noRecordCount: Int,
    val stageBreakdowns: List<StageDailyReport>
)

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
    val selectedTab: ReportsTab = ReportsTab.GROUP_DAILY,
    val selectedDate: LocalDate = LocalDate.now(),
    val startDate: LocalDate = LocalDate.now().minusDays(30),
    val endDate: LocalDate = LocalDate.now(),
    val students: List<Student> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedStudentId: String? = null,
    val selectedGradeId: String? = null,
    val selectedGroupId: String? = null,
    val groupReportMode: GroupReportMode = GroupReportMode.PERIOD,
    val groupPeriodReport: GroupPeriodReportData? = null,
    val groupDailyReport: GroupDailyReportData? = null,
    val gradeDailyReport: GradeDailyReportData? = null,
    val teacherDailyReport: TeacherDailyReportData? = null,
    val studentReport: StudentReportData? = null,
    val groupReport: GroupReportData? = null,
    val currentMonthlyReport: MonthlyReport? = null,
    val isSendingNotification: Boolean = false,
    val notificationMessage: String? = null
)

private data class FilterParams(
    val tab: ReportsTab,
    val date: LocalDate,
    val start: LocalDate,
    val end: LocalDate,
    val studentId: String?,
    val gradeId: String?,
    val groupId: String?,
    val groupReportMode: GroupReportMode
)

private data class RepoData(
    val students: List<Student>,
    val grades: List<Grade>,
    val groups: List<Group>,
    val attendance: List<Attendance>,
    val recitations: List<Recitation>,
    val exams: List<Exam>,
    val homework: List<Homework>
)

private data class StudentDailyStatusInfo(
    val effectiveGroupId: String?,
    val status: DailyAttendanceStatus,
    val note: String?,
    val attendance: Attendance?
)

class ReportsViewModel(
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val homeworkRepository: HomeworkRepository = RepositoryProvider.homeworkRepository,
    private val groupRepository: GroupRepository = RepositoryProvider.groupRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository,
    private val notificationEventRepository: NotificationEventRepository = RepositoryProvider.notificationEventRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ReportsTab.GROUP_DAILY)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _startDate = MutableStateFlow(LocalDate.now().minusDays(30))
    private val _endDate = MutableStateFlow(LocalDate.now())
    private val _selectedStudentId = MutableStateFlow<String?>(null)
    private val _selectedGradeId = MutableStateFlow<String?>(null)
    private val _selectedGroupId = MutableStateFlow<String?>(null)
    private val _groupReportMode = MutableStateFlow(GroupReportMode.PERIOD)
    private val _currentMonthlyReport = MutableStateFlow<MonthlyReport?>(null)
    private val _isSendingNotification = MutableStateFlow(false)
    private val _notificationMessage = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val teacher = teacherRepository.fetchCurrentTeacher()
            val teacherId = teacher?.id ?: ""

            val paramsFlow = combine(
                combine(_selectedTab, _selectedDate, _startDate, _endDate, _groupReportMode) { tab, date, start, end, mode ->
                    listOf<Any?>(tab, date, start, end, mode)
                },
                combine(_selectedStudentId, _selectedGradeId, _selectedGroupId) { studentId, gradeId, groupId ->
                    listOf<Any?>(studentId, gradeId, groupId)
                }
            ) { list1, list2 ->
                FilterParams(
                    tab = list1[0] as ReportsTab,
                    date = list1[1] as LocalDate,
                    start = list1[2] as LocalDate,
                    end = list1[3] as LocalDate,
                    groupReportMode = list1[4] as GroupReportMode,
                    studentId = list2[0] as String?,
                    gradeId = list2[1] as String?,
                    groupId = list2[2] as String?
                )
            }

            val repoDataFlow = combine(
                combine(
                    studentRepository.getStudents(),
                    gradeRepository.getGrades(),
                    groupRepository.observeGroups(teacherId),
                    attendanceRepository.getAllAttendanceForTeacher()
                ) { students, grades, groups, attendance ->
                    listOf(students, grades, groups, attendance)
                },
                combine(
                    recitationRepository.getAllRecitationsForTeacher(),
                    examRepository.getAllExamsForTeacher(),
                    homeworkRepository.getHomeworkForTeacher()
                ) { rec, ex, hw ->
                    listOf(rec, ex, hw)
                }
            ) { part1, part2 ->
                @Suppress("UNCHECKED_CAST")
                RepoData(
                    students = part1[0] as List<Student>,
                    grades = part1[1] as List<Grade>,
                    groups = part1[2] as List<Group>,
                    attendance = part1[3] as List<Attendance>,
                    recitations = part2[0] as List<Recitation>,
                    exams = part2[1] as List<Exam>,
                    homework = part2[2] as List<Homework>
                )
            }

            combine(paramsFlow, repoDataFlow) { params, repo ->
                val tab = params.tab
                val selectedDate = params.date
                val start = params.start
                val end = params.end
                val studentId = params.studentId
                var gradeId = params.gradeId
                var groupId = params.groupId

                val allStudents = repo.students
                val allGrades = repo.grades
                val allGroups = repo.groups
                val allAttendance = repo.attendance
                val allRecitations = repo.recitations
                val allExams = repo.exams
                val allHomework = repo.homework

                // Tenant filtering:
                val filteredStudents = allStudents.filter {
                    it.deletedAt == null && (teacherId.isEmpty() || it.teacherId == null || it.teacherId == teacherId)
                }
                val filteredGrades = allGrades.filter {
                    teacherId.isEmpty() || it.teacherId == null || it.teacherId == teacherId
                }.sortedBy { it.displayOrder }

                val filteredGroups = allGroups.filter {
                    teacherId.isEmpty() || it.teacherId == teacherId
                }

                // Default auto-selections if null:
                if (groupId == null && filteredGroups.isNotEmpty()) {
                    groupId = filteredGroups.first().id
                }
                if (gradeId == null && filteredGrades.isNotEmpty()) {
                    gradeId = filteredGrades.first().id
                }

                val dateStr = selectedDate.toString()
                val dateAttendance = allAttendance.filter { it.date == dateStr }
                val dateRecitations = allRecitations.filter { it.date == dateStr }
                val dateExams = allExams.filter { it.date == dateStr }
                val dateAttendanceByStudent = dateAttendance.associateBy { it.studentId }

                // Map students to their effective status & group for selectedDate (O(1) indexed lookup)
                val studentDailyMap = filteredStudents.associateWith { s ->
                    val att = dateAttendanceByStudent[s.studentId]
                    val effectiveGroupId = att?.groupId ?: s.groupId
                    val status = if (att != null) {
                        if (att.status == AttendanceStatus.PRESENT || att.status == AttendanceStatus.LATE) {
                            DailyAttendanceStatus.PRESENT
                        } else {
                            DailyAttendanceStatus.ABSENT
                        }
                    } else {
                        DailyAttendanceStatus.NO_RECORD
                    }
                    StudentDailyStatusInfo(effectiveGroupId, status, att?.note, att)
                }

                // 1. Group Daily & Period Report
                val group = if (groupId != null) filteredGroups.find { it.id == groupId } else null
                val groupDailyReport = if (group != null) {
                    val gradeName = filteredGrades.find { it.id == group.gradeId }?.name ?: ""
                    val groupStudentsWithStatus = studentDailyMap.filter { (s, info) ->
                        info.effectiveGroupId == group.id
                    }.map { (s, info) ->
                        StudentDailyAttendance(
                            student = s,
                            status = info.status,
                            note = info.note,
                            attendance = info.attendance
                        )
                    }.sortedBy { it.student.fullName }

                    val groupStudentMap = groupStudentsWithStatus.associateBy { it.student.studentId }
                    val groupDailyRecitations = dateRecitations.mapNotNull { rec ->
                        val s = groupStudentMap[rec.studentId]?.student ?: filteredStudents.find { it.studentId == rec.studentId && it.groupId == group.id }
                        if (s != null) StudentDailyRecitation(student = s, recitation = rec) else null
                    }.sortedBy { it.student.fullName }

                    val groupDailyExams = dateExams.mapNotNull { exam ->
                        val s = groupStudentMap[exam.studentId]?.student ?: filteredStudents.find { it.studentId == exam.studentId && it.groupId == group.id }
                        if (s != null) StudentDailyExam(student = s, exam = exam) else null
                    }.sortedBy { it.student.fullName }

                    GroupDailyReportData(
                        group = group,
                        gradeName = gradeName,
                        date = selectedDate,
                        totalStudents = groupStudentsWithStatus.size,
                        presentCount = groupStudentsWithStatus.count { it.status == DailyAttendanceStatus.PRESENT },
                        absentCount = groupStudentsWithStatus.count { it.status == DailyAttendanceStatus.ABSENT },
                        noRecordCount = groupStudentsWithStatus.count { it.status == DailyAttendanceStatus.NO_RECORD },
                        studentList = groupStudentsWithStatus,
                        dailyRecitations = groupDailyRecitations,
                        dailyExams = groupDailyExams
                    )
                } else null

                val startStr = start.toString()
                val endStr = end.toString()
                val groupPeriodReport = if (group != null) {
                    val gradeName = filteredGrades.find { it.id == group.gradeId }?.name ?: ""
                    val groupAttendanceInRange = allAttendance.filter {
                        it.groupId == group.id && it.date in startStr..endStr
                    }
                    val sessionDates = groupAttendanceInRange.map { it.date }.distinct()
                    val totalSessionsHeld = sessionDates.size

                    val baseStudents = filteredStudents.filter { it.groupId == group.id }
                    val guestStudentIds = groupAttendanceInRange.map { it.studentId }.toSet() - baseStudents.map { it.studentId }.toSet()
                    val guestStudents = filteredStudents.filter { guestStudentIds.contains(it.studentId) }
                    val allGroupReportingStudents = (baseStudents + guestStudents).distinctBy { it.studentId }

                    val studentStatsList = allGroupReportingStudents.map { student ->
                        val studentRecords = groupAttendanceInRange.filter { it.studentId == student.studentId }
                        val presentCount = studentRecords.count { it.status == AttendanceStatus.PRESENT }
                        val absentCount = studentRecords.count { it.status == AttendanceStatus.ABSENT }
                        val lateCount = studentRecords.count { it.status == AttendanceStatus.LATE }
                        val excusedCount = studentRecords.count { it.status == AttendanceStatus.EXCUSED }
                        val totalRecorded = studentRecords.size

                        val attendedCount = presentCount + lateCount
                        val rate = if (totalRecorded > 0) {
                            (attendedCount.toFloat() / totalRecorded) * 100f
                        } else 0f

                        val primaryGroup = filteredGroups.find { it.id == student.groupId }
                        val primaryGroupName = primaryGroup?.name ?: "بدون مجموعة"
                        val isGuest = student.groupId != group.id

                        StudentGroupPeriodAttendance(
                            student = student,
                            primaryGroupName = primaryGroupName,
                            presentCount = presentCount,
                            absentCount = absentCount,
                            lateCount = lateCount,
                            excusedCount = excusedCount,
                            totalRecordedSessions = totalRecorded,
                            attendanceRate = rate,
                            isGuest = isGuest
                        )
                    }.sortedWith(compareBy({ it.isGuest }, { it.student.fullName }))

                    val avgRate = if (studentStatsList.isNotEmpty()) {
                        studentStatsList.map { it.attendanceRate }.average().toFloat()
                    } else 0f

                    GroupPeriodReportData(
                        group = group,
                        gradeName = gradeName,
                        startDate = start,
                        endDate = end,
                        totalSessionsHeld = totalSessionsHeld,
                        totalStudents = studentStatsList.size,
                        avgAttendanceRate = avgRate,
                        studentList = studentStatsList
                    )
                } else null

                // 2. Grade Daily Report
                val gradeDailyReport = if (gradeId != null) {
                    val grade = filteredGrades.find { it.id == gradeId }
                    if (grade != null) {
                        val gradeGroups = filteredGroups.filter { it.gradeId == gradeId }
                        val groupBreakdowns = gradeGroups.map { g ->
                            val gStudents = studentDailyMap.filter { (_, info) -> info.effectiveGroupId == g.id }
                            GroupBreakdown(
                                group = g,
                                totalStudents = gStudents.size,
                                presentCount = gStudents.count { it.value.status == DailyAttendanceStatus.PRESENT },
                                absentCount = gStudents.count { it.value.status == DailyAttendanceStatus.ABSENT },
                                noRecordCount = gStudents.count { it.value.status == DailyAttendanceStatus.NO_RECORD }
                            )
                        }

                        val gradeStudents = studentDailyMap.filter { (s, info) ->
                            val g = filteredGroups.find { grp -> grp.id == info.effectiveGroupId }
                            g?.gradeId == gradeId || (info.effectiveGroupId == null && s.gradeId == gradeId)
                        }

                        val gradeDailyStudentsWithStatus = gradeStudents.map { (s, info) ->
                            StudentDailyAttendance(
                                student = s,
                                status = info.status,
                                note = info.note,
                                attendance = info.attendance
                            )
                        }.sortedBy { it.student.fullName }

                        val gradeStudentMap = gradeDailyStudentsWithStatus.associateBy { it.student.studentId }
                        val gradeDailyRecitations = dateRecitations.mapNotNull { rec ->
                            val s = gradeStudentMap[rec.studentId]?.student ?: filteredStudents.find { it.studentId == rec.studentId && it.gradeId == gradeId }
                            if (s != null) StudentDailyRecitation(student = s, recitation = rec) else null
                        }.sortedBy { it.student.fullName }

                        val gradeDailyExams = dateExams.mapNotNull { exam ->
                            val s = gradeStudentMap[exam.studentId]?.student ?: filteredStudents.find { it.studentId == exam.studentId && it.gradeId == gradeId }
                            if (s != null) StudentDailyExam(student = s, exam = exam) else null
                        }.sortedBy { it.student.fullName }

                        GradeDailyReportData(
                            grade = grade,
                            date = selectedDate,
                            totalStudents = gradeStudents.size,
                            totalGroups = gradeGroups.size,
                            presentCount = gradeStudents.count { it.value.status == DailyAttendanceStatus.PRESENT },
                            absentCount = gradeStudents.count { it.value.status == DailyAttendanceStatus.ABSENT },
                            noRecordCount = gradeStudents.count { it.value.status == DailyAttendanceStatus.NO_RECORD },
                            groupBreakdowns = groupBreakdowns,
                            studentList = gradeDailyStudentsWithStatus,
                            dailyRecitations = gradeDailyRecitations,
                            dailyExams = gradeDailyExams
                        )
                    } else null
                } else null

                // 3. Teacher Daily Report
                val totalStudentsCount = filteredStudents.size
                val totalGroupsCount = filteredGroups.size
                val activeGradesCount = filteredGrades.count { grade ->
                    filteredGroups.any { it.gradeId == grade.id } || filteredStudents.any { it.gradeId == grade.id }
                }

                val allPresentCount = studentDailyMap.count { it.value.status == DailyAttendanceStatus.PRESENT }
                val allAbsentCount = studentDailyMap.count { it.value.status == DailyAttendanceStatus.ABSENT }
                val allNoRecordCount = studentDailyMap.count { it.value.status == DailyAttendanceStatus.NO_RECORD }

                val stages = EducationalStages.ALL_STAGES
                val stageBreakdowns = stages.mapNotNull { stageName ->
                    val stageGrades = filteredGrades.filter { grade ->
                        EducationalStages.isGradeMatchingStage(grade.name, stageName)
                    }
                    if (stageGrades.isEmpty()) return@mapNotNull null

                    val stageGradeReports = stageGrades.mapNotNull { grade ->
                        val gradeGroups = filteredGroups.filter { it.gradeId == grade.id }
                        val groupBreakdowns = gradeGroups.map { g ->
                            val gStudents = studentDailyMap.filter { (_, info) -> info.effectiveGroupId == g.id }
                            GroupBreakdown(
                                group = g,
                                totalStudents = gStudents.size,
                                presentCount = gStudents.count { it.value.status == DailyAttendanceStatus.PRESENT },
                                absentCount = gStudents.count { it.value.status == DailyAttendanceStatus.ABSENT },
                                noRecordCount = gStudents.count { it.value.status == DailyAttendanceStatus.NO_RECORD }
                            )
                        }

                        val gradeStudents = studentDailyMap.filter { (s, info) ->
                            val g = filteredGroups.find { grp -> grp.id == info.effectiveGroupId }
                            g?.gradeId == grade.id || (info.effectiveGroupId == null && s.gradeId == grade.id)
                        }

                        GradeDailyReportData(
                            grade = grade,
                            date = selectedDate,
                            totalStudents = gradeStudents.size,
                            totalGroups = gradeGroups.size,
                            presentCount = gradeStudents.count { it.value.status == DailyAttendanceStatus.PRESENT },
                            absentCount = gradeStudents.count { it.value.status == DailyAttendanceStatus.ABSENT },
                            noRecordCount = gradeStudents.count { it.value.status == DailyAttendanceStatus.NO_RECORD },
                            groupBreakdowns = groupBreakdowns
                        )
                    }

                    val stageTotalStudents = stageGradeReports.sumOf { it.totalStudents }
                    val stageTotalGroups = stageGradeReports.sumOf { it.totalGroups }
                    val stagePresent = stageGradeReports.sumOf { it.presentCount }
                    val stageAbsent = stageGradeReports.sumOf { it.absentCount }
                    val stageNoRecord = stageGradeReports.sumOf { it.noRecordCount }

                    StageDailyReport(
                        stageName = stageName,
                        totalStudents = stageTotalStudents,
                        totalGroups = stageTotalGroups,
                        presentCount = stagePresent,
                        absentCount = stageAbsent,
                        noRecordCount = stageNoRecord,
                        gradeBreakdowns = stageGradeReports
                    )
                }

                val teacherDailyReport = TeacherDailyReportData(
                    date = selectedDate,
                    totalStudents = totalStudentsCount,
                    totalGroups = totalGroupsCount,
                    totalGradesWithGroups = activeGradesCount,
                    presentCount = allPresentCount,
                    absentCount = allAbsentCount,
                    noRecordCount = allNoRecordCount,
                    stageBreakdowns = stageBreakdowns
                )

                // 4. Student Report (Period based)
                val studentReport = if (studentId != null) {
                    val student = filteredStudents.find { it.studentId == studentId }
                    if (student != null) {
                        val studentAttendance = allAttendance.filter { it.studentId == studentId && it.date in startStr..endStr }
                        val studentRecitations = allRecitations.filter { it.studentId == studentId && it.date in startStr..endStr }
                        val studentExams = allExams.filter { it.studentId == studentId && it.date in startStr..endStr }
                        val studentHomework = allHomework.filter { it.studentId == studentId && it.date in startStr..endStr }

                        val attTotal = studentAttendance.size
                        val attPresent = studentAttendance.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                        val attPercent = if (attTotal > 0) (attPresent.toFloat() / attTotal) * 100f else 0f

                        val recTotal = studentRecitations.size
                        val recAvg = if (recTotal > 0) studentRecitations.map { if (it.maxScore > 0) (it.score / it.maxScore) * 100 else 0.0 }.average().toFloat() else 0f

                        val hwTotal = studentHomework.size
                        val hwDone = studentHomework.count { it.status == HomeworkStatus.COMPLETED }
                        val hwPercent = if (hwTotal > 0) (hwDone.toFloat() / hwTotal) * 100f else 0f

                        val exTotal = studentExams.size
                        val exAvg = if (exTotal > 0) studentExams.map { if (it.maxScore > 0) (it.score / it.maxScore) * 100 else 0.0 }.average().toFloat() else 0f

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

                // 5. Group Report (Period based)
                val groupReport = if (gradeId != null) {
                    val grade = filteredGrades.find { it.id == gradeId }
                    if (grade != null) {
                        val gradeStudents = filteredStudents.filter { it.gradeId == gradeId }
                        val studentIdsForGrade = gradeStudents.map { it.studentId }.toSet()

                        val groupAttendance = allAttendance.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }
                        val groupRecitations = allRecitations.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }
                        val groupExams = allExams.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }
                        val groupHomework = allHomework.filter { it.studentId in studentIdsForGrade && it.date in startStr..endStr }

                        val studentStats = gradeStudents.map { s ->
                            val sAtt = groupAttendance.filter { it.studentId == s.studentId }
                            val sRec = groupRecitations.filter { it.studentId == s.studentId }
                            val sEx = groupExams.filter { it.studentId == s.studentId }

                            val sAttRate = if (sAtt.isNotEmpty()) (sAtt.count { it.status == AttendanceStatus.PRESENT }.toFloat() / sAtt.size) * 100f else 0f
                            val sRecRate = if (sRec.isNotEmpty()) sRec.map { if (it.maxScore > 0) (it.score / it.maxScore) * 100 else 0.0 }.average().toFloat() else 0f
                            val sExRate = if (sEx.isNotEmpty()) sEx.map { if (it.maxScore > 0) (it.score / it.maxScore) * 100 else 0.0 }.average().toFloat() else 0f

                            StudentGroupMetric(s, sAttRate, sRecRate, sExRate)
                        }

                        val avgAtt = if (groupAttendance.isNotEmpty()) (groupAttendance.count { it.status == AttendanceStatus.PRESENT }.toFloat() / groupAttendance.size) * 100f else 0f
                        val avgEx = if (groupExams.isNotEmpty()) groupExams.map { if (it.maxScore > 0) (it.score / it.maxScore) * 100 else 0.0 }.average().toFloat() else 0f

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
                    selectedDate = selectedDate,
                    startDate = start,
                    endDate = end,
                    students = filteredStudents,
                    grades = filteredGrades,
                    groups = filteredGroups,
                    selectedStudentId = studentId,
                    selectedGradeId = gradeId,
                    selectedGroupId = groupId,
                    groupReportMode = params.groupReportMode,
                    groupPeriodReport = groupPeriodReport,
                    groupDailyReport = groupDailyReport,
                    gradeDailyReport = gradeDailyReport,
                    teacherDailyReport = teacherDailyReport,
                    studentReport = studentReport,
                    groupReport = groupReport
                )
            }.combine(_currentMonthlyReport) { state, monthlyReport ->
                state.copy(currentMonthlyReport = monthlyReport)
            }.combine(_isSendingNotification) { state, isSending ->
                state.copy(isSendingNotification = isSending)
            }.combine(_notificationMessage) { state, msg ->
                state.copy(notificationMessage = msg)
            }.flowOn(Dispatchers.Default)
            .collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectTab(tab: ReportsTab) {
        _selectedTab.value = tab
    }

    fun setGroupReportMode(mode: GroupReportMode) {
        _groupReportMode.value = mode
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        fetchMonthlyReportForStudent(_selectedStudentId.value, date.year, date.monthValue)
    }

    fun previousDay() {
        val newDate = _selectedDate.value.minusDays(1)
        _selectedDate.value = newDate
        fetchMonthlyReportForStudent(_selectedStudentId.value, newDate.year, newDate.monthValue)
    }

    fun nextDay() {
        val newDate = _selectedDate.value.plusDays(1)
        _selectedDate.value = newDate
        fetchMonthlyReportForStudent(_selectedStudentId.value, newDate.year, newDate.monthValue)
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        _startDate.value = start
        _endDate.value = end
        fetchMonthlyReportForStudent(_selectedStudentId.value, start.year, start.monthValue)
    }

    fun selectStudent(studentId: String?) {
        _selectedStudentId.value = studentId
        fetchMonthlyReportForStudent(studentId, _startDate.value.year, _startDate.value.monthValue)
    }

    fun selectGrade(gradeId: String?) {
        _selectedGradeId.value = gradeId
    }

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun fetchMonthlyReportForStudent(studentId: String?, year: Int, month: Int) {
        if (studentId.isNullOrBlank()) {
            _currentMonthlyReport.value = null
            return
        }
        viewModelScope.launch {
            try {
                val report = RepositoryProvider.monthlyReportRepository.getMonthlyReport(studentId, year, month)
                _currentMonthlyReport.value = report
            } catch (_: Exception) {
                _currentMonthlyReport.value = null
            }
        }
    }

    /**
     * Creates a PENDING monthly_report_notification_events record in Supabase.
     * Android only inserts as PENDING with attempts = 0 and does not perform UPDATE/DELETE.
     */
    fun sendMonthlyReportNotification(
        student: Student,
        report: MonthlyReport,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (_isSendingNotification.value) return

        if (student.parentPhone.isBlank()) {
            val msg = "لا يوجد رقم هاتف مسجل لولي الأمر."
            _notificationMessage.value = msg
            onComplete(false, msg)
            return
        }

        if (report.reportId.isBlank()) {
            val msg = "معرّف التقرير الشهري غير صالح."
            _notificationMessage.value = msg
            onComplete(false, msg)
            return
        }

        viewModelScope.launch {
            _isSendingNotification.value = true
            try {
                val event = NotificationEvent.MonthlyReport(
                    teacherId = "", // Filled by repository from authenticated user session
                    studentId = student.studentId,
                    monthlyReportId = report.reportId
                )

                val result = notificationEventRepository.createNotificationEvent(event)
                when (result) {
                    is NotificationEventCreationResult.Created,
                    is NotificationEventCreationResult.AlreadyExists -> {
                        val isNew = result is NotificationEventCreationResult.Created
                        val baseMsg = if (isNew) {
                            "تم جدولة إرسال التقرير الشهري بنجاح، وجارٍ استدعاء خادم الإشعارات."
                        } else {
                            "تم تسجيل طلب هذا التقرير مسبقاً، وجارٍ استدعاء خادم الإشعارات لمعالجته."
                        }
                        _notificationMessage.value = baseMsg
                        onComplete(true, baseMsg)

                        val workerResult = notificationEventRepository.triggerNotificationWorker("MONTHLY_REPORT")
                        when (workerResult) {
                            is NotificationWorkerResult.Success -> {
                                _notificationMessage.value = if (isNew) {
                                    "تمت جدولة إشعار التقرير الشهري وتفعيل المعالجة بنجاح ✅"
                                } else {
                                    "تم تفعيل إعادة معالجة إشعار التقرير الشهري بنجاح ✅"
                                }
                            }
                            is NotificationWorkerResult.Error -> {
                                _notificationMessage.value = "تمت جدولة إشعار التقرير في خادم Supabase، ولكن تعذر تشغيل المعالج الفوري: ${workerResult.message}"
                            }
                            is NotificationWorkerResult.Skipped -> {
                                // Worker execution skipped in offline / unconfigured environments
                            }
                        }
                    }
                    is NotificationEventCreationResult.Failed -> {
                        val msg = result.message
                        _notificationMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: Exception) {
                val msg = "حدث خطأ أثناء جدولة الإشعار: ${e.message ?: "خطأ غير معروف"}"
                _notificationMessage.value = msg
                onComplete(false, msg)
            } finally {
                _isSendingNotification.value = false
            }
        }
    }

    fun getAbsentStudentsForCurrentGroup(): List<StudentDailyAttendance> {
        return _uiState.value.groupDailyReport?.absentStudents ?: emptyList()
    }

    fun getRecitationsForCurrentGroup(): List<StudentDailyRecitation> {
        return _uiState.value.groupDailyReport?.dailyRecitations ?: emptyList()
    }

    fun getExamsForCurrentGroup(): List<StudentDailyExam> {
        return _uiState.value.groupDailyReport?.dailyExams ?: emptyList()
    }

    fun getAbsentStudentsForCurrentGrade(): List<StudentDailyAttendance> {
        return _uiState.value.gradeDailyReport?.absentStudents ?: emptyList()
    }

    fun getRecitationsForCurrentGrade(): List<StudentDailyRecitation> {
        return _uiState.value.gradeDailyReport?.dailyRecitations ?: emptyList()
    }

    fun getExamsForCurrentGrade(): List<StudentDailyExam> {
        return _uiState.value.gradeDailyReport?.dailyExams ?: emptyList()
    }

    /**
     * Builds a list of candidate notification recipients for the current group or grade report
     * based on the requested notification event type.
     */
    fun getNotificationRecipients(eventType: NotificationEventType): List<NotificationRecipient> {
        val state = _uiState.value
        return when (state.selectedTab) {
            ReportsTab.GRADE_DAILY -> {
                val gradeReport = state.gradeDailyReport ?: return emptyList()
                when (eventType) {
                    NotificationEventType.ABSENCE -> gradeReport.absentStudents.map {
                        NotificationRecipient(
                            studentId = it.student.studentId,
                            studentName = it.student.fullName,
                            parentPhone = it.student.parentPhone.takeIf { p -> p.isNotBlank() },
                            sourceId = it.attendance?.attendanceId ?: "",
                            notificationType = NotificationEventType.ABSENCE,
                            previewDetails = "غائب في تاريخ ${gradeReport.date}",
                            isSelectable = it.student.parentPhone.isNotBlank() && it.attendance != null,
                            isSelected = it.student.parentPhone.isNotBlank() && it.attendance != null,
                            deliveryStatus = if (it.student.parentPhone.isBlank()) NotificationDeliveryStatus.NO_PHONE else NotificationDeliveryStatus.IDLE
                        )
                    }
                    NotificationEventType.RECITATION -> gradeReport.dailyRecitations.map {
                        NotificationRecipient(
                            studentId = it.student.studentId,
                            studentName = it.student.fullName,
                            parentPhone = it.student.parentPhone.takeIf { p -> p.isNotBlank() },
                            sourceId = it.recitation.recitationId,
                            notificationType = NotificationEventType.RECITATION,
                            previewDetails = "${it.recitation.title}: ${it.recitation.score}/${it.recitation.maxScore}",
                            isSelectable = it.student.parentPhone.isNotBlank(),
                            isSelected = it.student.parentPhone.isNotBlank(),
                            deliveryStatus = if (it.student.parentPhone.isBlank()) NotificationDeliveryStatus.NO_PHONE else NotificationDeliveryStatus.IDLE
                        )
                    }
                    NotificationEventType.EXAM -> gradeReport.dailyExams.map {
                        NotificationRecipient(
                            studentId = it.student.studentId,
                            studentName = it.student.fullName,
                            parentPhone = it.student.parentPhone.takeIf { p -> p.isNotBlank() },
                            sourceId = it.exam.examId,
                            notificationType = NotificationEventType.EXAM,
                            previewDetails = "${it.exam.examName}: ${it.exam.score}/${it.exam.maxScore}",
                            isSelectable = it.student.parentPhone.isNotBlank(),
                            isSelected = it.student.parentPhone.isNotBlank(),
                            deliveryStatus = if (it.student.parentPhone.isBlank()) NotificationDeliveryStatus.NO_PHONE else NotificationDeliveryStatus.IDLE
                        )
                    }
                    else -> emptyList()
                }
            }
            else -> {
                val groupReport = state.groupDailyReport ?: return emptyList()
                when (eventType) {
                    NotificationEventType.ABSENCE -> groupReport.absentStudents.map {
                        NotificationRecipient(
                            studentId = it.student.studentId,
                            studentName = it.student.fullName,
                            parentPhone = it.student.parentPhone.takeIf { p -> p.isNotBlank() },
                            sourceId = it.attendance?.attendanceId ?: "",
                            notificationType = NotificationEventType.ABSENCE,
                            previewDetails = "غائب في تاريخ ${groupReport.date}",
                            isSelectable = it.student.parentPhone.isNotBlank() && it.attendance != null,
                            isSelected = it.student.parentPhone.isNotBlank() && it.attendance != null,
                            deliveryStatus = if (it.student.parentPhone.isBlank()) NotificationDeliveryStatus.NO_PHONE else NotificationDeliveryStatus.IDLE
                        )
                    }
                    NotificationEventType.RECITATION -> groupReport.dailyRecitations.map {
                        NotificationRecipient(
                            studentId = it.student.studentId,
                            studentName = it.student.fullName,
                            parentPhone = it.student.parentPhone.takeIf { p -> p.isNotBlank() },
                            sourceId = it.recitation.recitationId,
                            notificationType = NotificationEventType.RECITATION,
                            previewDetails = "${it.recitation.title}: ${it.recitation.score}/${it.recitation.maxScore}",
                            isSelectable = it.student.parentPhone.isNotBlank(),
                            isSelected = it.student.parentPhone.isNotBlank(),
                            deliveryStatus = if (it.student.parentPhone.isBlank()) NotificationDeliveryStatus.NO_PHONE else NotificationDeliveryStatus.IDLE
                        )
                    }
                    NotificationEventType.EXAM -> groupReport.dailyExams.map {
                        NotificationRecipient(
                            studentId = it.student.studentId,
                            studentName = it.student.fullName,
                            parentPhone = it.student.parentPhone.takeIf { p -> p.isNotBlank() },
                            sourceId = it.exam.examId,
                            notificationType = NotificationEventType.EXAM,
                            previewDetails = "${it.exam.examName}: ${it.exam.score}/${it.exam.maxScore}",
                            isSelectable = it.student.parentPhone.isNotBlank(),
                            isSelected = it.student.parentPhone.isNotBlank(),
                            deliveryStatus = if (it.student.parentPhone.isBlank()) NotificationDeliveryStatus.NO_PHONE else NotificationDeliveryStatus.IDLE
                        )
                    }
                    else -> emptyList()
                }
            }
        }
    }

    /**
     * Sends targeted batch notification events to Supabase as PENDING records.
     * Uses notificationEventRepository.createNotificationEvent(...) directly.
     * Android does not send WhatsApp messages directly.
     */
    fun sendBatchNotifications(
        recipients: List<NotificationRecipient>,
        onComplete: (NotificationBatchSummary) -> Unit = {}
    ) {
        if (_isSendingNotification.value) return
        if (recipients.isEmpty()) {
            val summary = NotificationBatchSummary(
                totalCount = 0,
                createdCount = 0,
                alreadyExistsCount = 0,
                failedCount = 0,
                skippedCount = 0,
                message = "لا يوجد طلاب محددين للإرسال"
            )
            _notificationMessage.value = summary.message
            onComplete(summary)
            return
        }

        viewModelScope.launch {
            _isSendingNotification.value = true
            try {
                var created = 0
                var alreadyExists = 0
                var failed = 0
                var skipped = 0

                for (r in recipients) {
                    if (r.parentPhone.isNullOrBlank() || r.sourceId.isBlank() || r.studentId.isBlank()) {
                        skipped++
                        continue
                    }

                    val domainEvent: NotificationEvent = when (r.notificationType) {
                        NotificationEventType.ABSENCE -> NotificationEvent.Absence(
                            teacherId = "",
                            studentId = r.studentId,
                            attendanceId = r.sourceId
                        )
                        NotificationEventType.RECITATION -> NotificationEvent.Recitation(
                            teacherId = "",
                            studentId = r.studentId,
                            recitationId = r.sourceId
                        )
                        NotificationEventType.EXAM -> NotificationEvent.Exam(
                            teacherId = "",
                            studentId = r.studentId,
                            examId = r.sourceId
                        )
                        NotificationEventType.HOMEWORK -> NotificationEvent.Homework(
                            teacherId = "",
                            studentId = r.studentId,
                            homeworkId = r.sourceId
                        )
                        NotificationEventType.MONTHLY_REPORT -> NotificationEvent.MonthlyReport(
                            teacherId = "",
                            studentId = r.studentId,
                            monthlyReportId = r.sourceId
                        )
                    }

                    when (val result = notificationEventRepository.createNotificationEvent(domainEvent)) {
                        is NotificationEventCreationResult.Created -> created++
                        is NotificationEventCreationResult.AlreadyExists -> alreadyExists++
                        is NotificationEventCreationResult.Failed -> failed++
                    }
                }

                val msg = buildString {
                    append("تم تسجيل ${created} إشعار جديد في خادم Supabase: ")
                    if (alreadyExists > 0) append("($alreadyExists مسجل مسبقاً). ")
                    if (skipped > 0) append("($skipped تم تخطيه لعدم وجود هاتف). ")
                    if (failed > 0) append("($failed فشل).")
                }.trim()

                val summary = NotificationBatchSummary(
                    totalCount = recipients.size,
                    createdCount = created,
                    alreadyExistsCount = alreadyExists,
                    failedCount = failed,
                    skippedCount = skipped,
                    message = msg
                )
                _notificationMessage.value = msg
                onComplete(summary)

                val totalEligible = created + alreadyExists
                if (totalEligible > 0) {
                    val workerResult = notificationEventRepository.triggerNotificationWorker("MONTHLY_REPORT")
                    when (workerResult) {
                        is NotificationWorkerResult.Success -> {
                            _notificationMessage.value = if (created > 0) {
                                "تمت جدولة $created إشعار وتفعيل المعالجة عبر خادم الإشعارات بنجاح ✅"
                            } else {
                                "تم تفعيل إعادة المعالجة لـ $alreadyExists إشعار مسجل مسبقاً عبر خادم الإشعارات بنجاح ✅"
                            }
                        }
                        is NotificationWorkerResult.Error -> {
                            _notificationMessage.value = "تمت جدولة الإشعارات في خادم Supabase، لكن تعذر تشغيل المعالج الفوري: ${workerResult.message}"
                        }
                        is NotificationWorkerResult.Skipped -> {
                            // No-op in test environments or when endpoint not configured
                        }
                    }
                }
            } catch (e: Exception) {
                val msg = "حدث خطأ أثناء جدولة الإشعارات: ${e.message ?: "خطأ غير معروف"}"
                val summary = NotificationBatchSummary(
                    totalCount = recipients.size,
                    createdCount = 0,
                    alreadyExistsCount = 0,
                    failedCount = recipients.size,
                    skippedCount = 0,
                    message = msg
                )
                _notificationMessage.value = msg
                onComplete(summary)
            } finally {
                _isSendingNotification.value = false
            }
        }
    }

    fun sendAbsenceNotifications(
        targets: List<StudentDailyAttendance>,
        onComplete: (NotificationBatchSummary) -> Unit = {}
    ) {
        val recipients = targets.mapNotNull { target ->
            val attId = target.attendance?.attendanceId ?: return@mapNotNull null
            NotificationRecipient(
                studentId = target.student.studentId,
                studentName = target.student.fullName,
                parentPhone = target.student.parentPhone.takeIf { it.isNotBlank() },
                sourceId = attId,
                notificationType = NotificationEventType.ABSENCE,
                previewDetails = "غائب في تاريخ ${_selectedDate.value}"
            )
        }
        sendBatchNotifications(recipients, onComplete)
    }

    fun sendRecitationNotifications(
        targets: List<StudentDailyRecitation>,
        onComplete: (NotificationBatchSummary) -> Unit = {}
    ) {
        val recipients = targets.map { target ->
            NotificationRecipient(
                studentId = target.student.studentId,
                studentName = target.student.fullName,
                parentPhone = target.student.parentPhone.takeIf { it.isNotBlank() },
                sourceId = target.recitation.recitationId,
                notificationType = NotificationEventType.RECITATION,
                previewDetails = "${target.recitation.title}: ${target.recitation.score}/${target.recitation.maxScore}"
            )
        }
        sendBatchNotifications(recipients, onComplete)
    }

    fun sendExamNotifications(
        targets: List<StudentDailyExam>,
        onComplete: (NotificationBatchSummary) -> Unit = {}
    ) {
        val recipients = targets.map { target ->
            NotificationRecipient(
                studentId = target.student.studentId,
                studentName = target.student.fullName,
                parentPhone = target.student.parentPhone.takeIf { it.isNotBlank() },
                sourceId = target.exam.examId,
                notificationType = NotificationEventType.EXAM,
                previewDetails = "${target.exam.examName}: ${target.exam.score}/${target.exam.maxScore}"
            )
        }
        sendBatchNotifications(recipients, onComplete)
    }

    fun sendTargetedNotifications(
        eventType: NotificationEventType,
        targets: List<Pair<Student, String>>,
        onComplete: (NotificationBatchSummary) -> Unit = {}
    ) {
        val recipients = targets.map { (student, sourceId) ->
            NotificationRecipient(
                studentId = student.studentId,
                studentName = student.fullName,
                parentPhone = student.parentPhone.takeIf { it.isNotBlank() },
                sourceId = sourceId,
                notificationType = eventType
            )
        }
        sendBatchNotifications(recipients, onComplete)
    }

    fun clearNotificationMessage() {
        _notificationMessage.value = null
    }
}
