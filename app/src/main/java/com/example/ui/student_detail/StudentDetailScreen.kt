package com.example.ui.student_detail

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DeleteConfirmationBottomSheet
import com.example.ui.student_detail.dialogs.AddEditAttendanceDialog
import com.example.ui.student_detail.dialogs.AddEditExamDialog
import com.example.ui.student_detail.dialogs.AddEditHomeworkDialog
import com.example.ui.student_detail.dialogs.AddEditRecitationDialog
import com.example.ui.student_detail.dialogs.ItemDeleteConfirmationBottomSheet
import com.example.ui.student_detail.tabs.AttendanceTab
import com.example.ui.student_detail.tabs.ExamsTab
import com.example.ui.student_detail.tabs.HomeworkTab
import com.example.ui.student_detail.tabs.MonthlyReportTab
import com.example.ui.student_detail.tabs.PaymentsTab
import com.example.ui.student_detail.tabs.RecitationsTab
import com.example.ui.student_detail.tabs.StudentProfileInfoTab
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Dimens
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    studentId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: StudentDetailViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(studentId) {
        viewModel.loadStudent(studentId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StudentDetailEvent.StudentDeleted -> {
                    Toast.makeText(context, "تم حذف الطالب بنجاح", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is StudentDetailEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.student?.fullName ?: "ملف الطالب",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onNavigateToEdit(studentId) },
                        modifier = Modifier.testTag("detail_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = viewModel::openDeleteBottomSheet,
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = DangerRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("student_detail_screen")
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.student != null) {
            val student = uiState.student!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    edgePadding = Dimens.Spacing16,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_detail_tabs")
                ) {
                    StudentDetailTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Text(
                                    text = tab.titleAr,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            },
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }

                // Tab Body
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.Spacing20,
                        end = Dimens.Spacing20,
                        top = Dimens.Spacing16,
                        bottom = Dimens.Spacing32
                    )
                ) {
                    item {
                        when (uiState.selectedTab) {
                            StudentDetailTab.OVERVIEW -> {
                                StudentProfileInfoTab(
                                    student = student,
                                    grade = uiState.grade,
                                    formattedCreatedAt = uiState.formattedCreatedAt,
                                    monthNameAr = uiState.monthNameAr,
                                    isPaymentPaid = uiState.isPaymentPaid,
                                    isTogglingPayment = uiState.isTogglingPayment,
                                    globalAttendances = uiState.globalAttendances,
                                    globalRecitations = uiState.globalRecitations,
                                    globalExams = uiState.globalExams,
                                    globalHomeworks = uiState.globalHomeworks,
                                    globalAttendanceSummary = uiState.globalAttendanceSummary,
                                    latestMonthlyPerformance = uiState.studentMonthlyPerformance,
                                    onTogglePayment = viewModel::toggleMonthlyPayment,
                                    onNavigateToEdit = onNavigateToEdit,
                                    onSelectTab = viewModel::selectTab,
                                    onRecordAttendance = { viewModel.openAddAttendanceDialog() },
                                    onRecordRecitation = viewModel::openAddRecitationDialog,
                                    onRecordHomework = viewModel::openAddHomeworkDialog,
                                    onRecordExam = viewModel::openAddExamDialog
                                )
                            }
                            StudentDetailTab.PAYMENTS -> {
                                PaymentsTab(
                                    student = student,
                                    monthNameAr = uiState.monthNameAr,
                                    monthlyPayment = uiState.monthlyPayment,
                                    isPaid = uiState.isPaymentPaid,
                                    isToggling = uiState.isTogglingPayment,
                                    onNavigateMonth = viewModel::navigateMonth,
                                    onTogglePayment = viewModel::toggleMonthlyPayment
                                )
                            }
                            StudentDetailTab.ATTENDANCE -> {
                                AttendanceTab(
                                    student = student,
                                    monthNameAr = uiState.monthNameAr,
                                    attendances = uiState.attendances,
                                    summary = uiState.attendanceSummary,
                                    onNavigateMonth = viewModel::navigateMonth,
                                    onAddAttendance = { viewModel.openAddAttendanceDialog() },
                                    onEditAttendance = viewModel::openEditAttendanceDialog,
                                    onDeleteAttendance = viewModel::confirmDeleteAttendance
                                )
                            }
                            StudentDetailTab.RECITATIONS -> {
                                RecitationsTab(
                                    student = student,
                                    recitations = uiState.recitations,
                                    summary = uiState.recitationSummary,
                                    onAddRecitation = viewModel::openAddRecitationDialog,
                                    onEditRecitation = viewModel::openEditRecitationDialog,
                                    onDeleteRecitation = viewModel::confirmDeleteRecitation
                                )
                            }
                            StudentDetailTab.EXAMS -> {
                                ExamsTab(
                                    student = student,
                                    exams = uiState.exams,
                                    summary = uiState.examSummary,
                                    onAddExam = viewModel::openAddExamDialog,
                                    onEditExam = viewModel::openEditExamDialog,
                                    onDeleteExam = viewModel::confirmDeleteExam
                                )
                            }
                            StudentDetailTab.HOMEWORK -> {
                                HomeworkTab(
                                    homeworks = uiState.homeworks,
                                    onAddHomework = viewModel::openAddHomeworkDialog,
                                    onEditHomework = viewModel::openEditHomeworkDialog,
                                    onDeleteHomework = viewModel::confirmDeleteHomework
                                )
                            }
                            StudentDetailTab.MONTHLY_REPORT -> {
                                MonthlyReportTab(
                                    student = student,
                                    monthNameAr = uiState.monthNameAr,
                                    performance = uiState.studentMonthlyPerformance,
                                    teacherNote = uiState.teacherMonthlyNote,
                                    isSavingNote = uiState.isSavingReportNote,
                                    onNavigateMonth = viewModel::navigateMonth,
                                    onTeacherNoteChange = viewModel::onTeacherReportNoteChange,
                                    onSaveReportNote = viewModel::saveMonthlyReportNote
                                )
                            }
                        }
                    }
                }
            }

            // Dialogs & Bottom Sheets
            if (uiState.showAttendanceDialog) {
                AddEditAttendanceDialog(
                    initialAttendance = uiState.attendanceToEdit,
                    defaultDate = String.format("%04d-%02d-18", uiState.selectedYear, uiState.selectedMonth),
                    onDismiss = viewModel::closeAttendanceDialog,
                    onSave = viewModel::saveAttendance
                )
            }

            if (uiState.showRecitationDialog) {
                AddEditRecitationDialog(
                    recitationToEdit = uiState.recitationToEdit,
                    onDismiss = viewModel::closeRecitationDialog,
                    onSave = viewModel::saveRecitation
                )
            }

            if (uiState.showExamDialog) {
                AddEditExamDialog(
                    examToEdit = uiState.examToEdit,
                    onDismiss = viewModel::closeExamDialog,
                    onSave = viewModel::saveExam
                )
            }

            if (uiState.showHomeworkDialog) {
                AddEditHomeworkDialog(
                    homework = uiState.homeworkToEdit,
                    onDismiss = viewModel::closeHomeworkDialog,
                    onSave = viewModel::saveHomework
                )
            }

            if (uiState.attendanceToDelete != null) {
                ItemDeleteConfirmationBottomSheet(
                    title = "حذف سجل الحضور",
                    message = "هل أنت متأكد من حذف سجل الحضور ليوم ${uiState.attendanceToDelete?.date}؟",
                    onDismiss = viewModel::dismissDeleteAttendance,
                    onConfirmDelete = viewModel::deleteAttendance
                )
            }

            if (uiState.recitationToDelete != null) {
                ItemDeleteConfirmationBottomSheet(
                    title = "حذف التسميع",
                    message = "هل أنت متأكد من حذف تسميع \"${uiState.recitationToDelete?.title}\"؟",
                    onDismiss = viewModel::dismissDeleteRecitation,
                    onConfirmDelete = viewModel::deleteRecitation
                )
            }

            if (uiState.examToDelete != null) {
                ItemDeleteConfirmationBottomSheet(
                    title = "حذف الامتحان",
                    message = "هل أنت متأكد من حذف امتحان \"${uiState.examToDelete?.examName}\"؟",
                    onDismiss = viewModel::dismissDeleteExam,
                    onConfirmDelete = viewModel::deleteExam
                )
            }

            if (uiState.homeworkToDelete != null) {
                ItemDeleteConfirmationBottomSheet(
                    title = "حذف الواجب",
                    message = "هل أنت متأكد من حذف الواجب \"${uiState.homeworkToDelete?.title}\"؟",
                    onDismiss = viewModel::dismissDeleteHomework,
                    onConfirmDelete = viewModel::deleteHomework
                )
            }

            if (uiState.showDeleteBottomSheet) {
                DeleteConfirmationBottomSheet(
                    studentName = student.fullName,
                    onDismiss = viewModel::closeDeleteBottomSheet,
                    onConfirmDelete = viewModel::deleteStudent
                )
            }
        }
    }
}
