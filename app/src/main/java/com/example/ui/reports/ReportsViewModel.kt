package com.example.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.*
import com.example.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val note: String? = null
)

data class GroupDailyReportData(
    val group: Group,
    val gradeName: String,
    val date: LocalDate,
    val totalStudents: Int,
    val presentCount: Int,
    val absentCount: Int,
    val noRecordCount: Int,
    val studentList: List<StudentDailyAttendance>
)

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
    val groupBreakdowns: List<GroupBreakdown>
)

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
    val groupDailyReport: GroupDailyReportData? = null,
    val gradeDailyReport: GradeDailyReportData? = null,
    val teacherDailyReport: TeacherDailyReportData? = null,
    val studentReport: StudentReportData? = null,
    val groupReport: GroupReportData? = null
)

private data class FilterParams(
    val tab: ReportsTab,
    val date: LocalDate,
    val start: LocalDate,
    val end: LocalDate,
    val studentId: String?,
    val gradeId: String?,
    val groupId: String?
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

class ReportsViewModel(
    private val studentRepository: StudentRepository = RepositoryProvider.studentRepository,
    private val gradeRepository: GradeRepository = RepositoryProvider.gradeRepository,
    private val attendanceRepository: AttendanceRepository = RepositoryProvider.attendanceRepository,
    private val recitationRepository: RecitationRepository = RepositoryProvider.recitationRepository,
    private val examRepository: ExamRepository = RepositoryProvider.examRepository,
    private val homeworkRepository: HomeworkRepository = RepositoryProvider.homeworkRepository,
    private val groupRepository: GroupRepository = RepositoryProvider.groupRepository,
    private val teacherRepository: TeacherRepository = RepositoryProvider.teacherRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ReportsTab.GROUP_DAILY)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _startDate = MutableStateFlow(LocalDate.now().minusDays(30))
    private val _endDate = MutableStateFlow(LocalDate.now())
    private val _selectedStudentId = MutableStateFlow<String?>(null)
    private val _selectedGradeId = MutableStateFlow<String?>(null)
    private val _selectedGroupId = MutableStateFlow<String?>(null)

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
                combine(_selectedTab, _selectedDate, _startDate, _endDate) { tab, date, start, end ->
                    listOf<Any?>(tab, date, start, end)
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

                // Map students to their effective status & group for selectedDate
                val studentDailyMap = filteredStudents.associateWith { s ->
                    val att = dateAttendance.find { it.studentId == s.studentId }
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
                    Triple(effectiveGroupId, status, att?.note)
                }

                // 1. Group Daily Report
                val groupDailyReport = if (groupId != null) {
                    val group = filteredGroups.find { it.id == groupId }
                    if (group != null) {
                        val gradeName = filteredGrades.find { it.id == group.gradeId }?.name ?: ""
                        val groupStudentsWithStatus = studentDailyMap.filter { (s, triple) ->
                            triple.first == group.id
                        }.map { (s, triple) ->
                            StudentDailyAttendance(
                                student = s,
                                status = triple.second,
                                note = triple.third
                            )
                        }.sortedBy { it.student.fullName }

                        GroupDailyReportData(
                            group = group,
                            gradeName = gradeName,
                            date = selectedDate,
                            totalStudents = groupStudentsWithStatus.size,
                            presentCount = groupStudentsWithStatus.count { it.status == DailyAttendanceStatus.PRESENT },
                            absentCount = groupStudentsWithStatus.count { it.status == DailyAttendanceStatus.ABSENT },
                            noRecordCount = groupStudentsWithStatus.count { it.status == DailyAttendanceStatus.NO_RECORD },
                            studentList = groupStudentsWithStatus
                        )
                    } else null
                } else null

                // 2. Grade Daily Report
                val gradeDailyReport = if (gradeId != null) {
                    val grade = filteredGrades.find { it.id == gradeId }
                    if (grade != null) {
                        val gradeGroups = filteredGroups.filter { it.gradeId == gradeId }
                        val groupBreakdowns = gradeGroups.map { g ->
                            val gStudents = studentDailyMap.filter { (_, triple) -> triple.first == g.id }
                            GroupBreakdown(
                                group = g,
                                totalStudents = gStudents.size,
                                presentCount = gStudents.count { it.value.second == DailyAttendanceStatus.PRESENT },
                                absentCount = gStudents.count { it.value.second == DailyAttendanceStatus.ABSENT },
                                noRecordCount = gStudents.count { it.value.second == DailyAttendanceStatus.NO_RECORD }
                            )
                        }

                        val gradeStudents = studentDailyMap.filter { (s, triple) ->
                            val g = filteredGroups.find { grp -> grp.id == triple.first }
                            g?.gradeId == gradeId || (triple.first == null && s.gradeId == gradeId)
                        }

                        GradeDailyReportData(
                            grade = grade,
                            date = selectedDate,
                            totalStudents = gradeStudents.size,
                            totalGroups = gradeGroups.size,
                            presentCount = gradeStudents.count { it.value.second == DailyAttendanceStatus.PRESENT },
                            absentCount = gradeStudents.count { it.value.second == DailyAttendanceStatus.ABSENT },
                            noRecordCount = gradeStudents.count { it.value.second == DailyAttendanceStatus.NO_RECORD },
                            groupBreakdowns = groupBreakdowns
                        )
                    } else null
                } else null

                // 3. Teacher Daily Report
                val totalStudentsCount = filteredStudents.size
                val totalGroupsCount = filteredGroups.size
                val activeGradesCount = filteredGrades.count { grade ->
                    filteredGroups.any { it.gradeId == grade.id } || filteredStudents.any { it.gradeId == grade.id }
                }

                val allPresentCount = studentDailyMap.count { it.value.second == DailyAttendanceStatus.PRESENT }
                val allAbsentCount = studentDailyMap.count { it.value.second == DailyAttendanceStatus.ABSENT }
                val allNoRecordCount = studentDailyMap.count { it.value.second == DailyAttendanceStatus.NO_RECORD }

                val stages = EducationalStages.ALL_STAGES
                val stageBreakdowns = stages.mapNotNull { stageName ->
                    val stageGrades = filteredGrades.filter { grade ->
                        EducationalStages.isGradeMatchingStage(grade.name, stageName)
                    }
                    if (stageGrades.isEmpty()) return@mapNotNull null

                    val stageGradeReports = stageGrades.mapNotNull { grade ->
                        val gradeGroups = filteredGroups.filter { it.gradeId == grade.id }
                        val groupBreakdowns = gradeGroups.map { g ->
                            val gStudents = studentDailyMap.filter { (_, triple) -> triple.first == g.id }
                            GroupBreakdown(
                                group = g,
                                totalStudents = gStudents.size,
                                presentCount = gStudents.count { it.value.second == DailyAttendanceStatus.PRESENT },
                                absentCount = gStudents.count { it.value.second == DailyAttendanceStatus.ABSENT },
                                noRecordCount = gStudents.count { it.value.second == DailyAttendanceStatus.NO_RECORD }
                            )
                        }

                        val gradeStudents = studentDailyMap.filter { (s, triple) ->
                            val g = filteredGroups.find { grp -> grp.id == triple.first }
                            g?.gradeId == grade.id || (triple.first == null && s.gradeId == grade.id)
                        }

                        GradeDailyReportData(
                            grade = grade,
                            date = selectedDate,
                            totalStudents = gradeStudents.size,
                            totalGroups = gradeGroups.size,
                            presentCount = gradeStudents.count { it.value.second == DailyAttendanceStatus.PRESENT },
                            absentCount = gradeStudents.count { it.value.second == DailyAttendanceStatus.ABSENT },
                            noRecordCount = gradeStudents.count { it.value.second == DailyAttendanceStatus.NO_RECORD },
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
                val startStr = start.toString()
                val endStr = end.toString()
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
                    groupDailyReport = groupDailyReport,
                    gradeDailyReport = gradeDailyReport,
                    teacherDailyReport = teacherDailyReport,
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

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
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

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }
}
