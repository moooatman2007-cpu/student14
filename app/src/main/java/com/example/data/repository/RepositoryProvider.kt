package com.example.data.repository

object RepositoryProvider {
    val teacherRepository: TeacherRepository by lazy {
        SupabaseTeacherRepository()
    }

    val gradeRepository: GradeRepository by lazy {
        SupabaseGradeRepository()
    }

    val studentRepository: StudentRepository by lazy {
        SupabaseStudentRepository()
    }

    val settingsRepository: SettingsRepository by lazy {
        MockSettingsRepository()
    }

    val attendanceRepository: AttendanceRepository by lazy {
        SupabaseAttendanceRepository()
    }

    val recitationRepository: RecitationRepository by lazy {
        SupabaseRecitationRepository()
    }

    val examRepository: ExamRepository by lazy {
        SupabaseExamRepository()
    }

    val monthlyReportRepository: MonthlyReportRepository by lazy {
        SupabaseMonthlyReportRepository()
    }

    val homeworkRepository: HomeworkRepository by lazy {
        SupabaseHomeworkRepository()
    }

    val groupRepository: GroupRepository by lazy {
        SupabaseGroupRepository()
    }

    val paymentRepository: PaymentRepository by lazy {
        SupabasePaymentRepository()
    }

    val notificationEventRepository: NotificationEventRepository by lazy {
        SupabaseNotificationEventRepository()
    }

    val syncManager: com.example.data.sync.SyncManager by lazy {
        val context = com.example.data.local.DatabaseProvider.context
        val outboxDao = try { com.example.data.local.DatabaseProvider.getDatabase().outboxDao() } catch (_: Exception) { null }
        com.example.data.sync.DefaultSyncManager(
            networkMonitor = com.example.data.sync.ConnectivityNetworkMonitor(context),
            outboxDao = outboxDao
        )
    }
}
