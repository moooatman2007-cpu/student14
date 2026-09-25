package com.example.ui.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.*
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateToStudentDetail: (String) -> Unit,
    viewModel: ReportsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 التقارير", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "فترة التقرير")
                    }
                }
            )
        },
        modifier = modifier.testTag("reports_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryIndigo
            ) {
                ReportsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.titleAr) }
                    )
                }
            }

            // Period Indicator
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.Spacing12),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الفترة: ${uiState.startDate} — ${uiState.endDate}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Selection Controls (Student/Grade)
            SelectionControls(uiState, viewModel)

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.Spacing16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
            ) {
                if (uiState.selectedTab == ReportsTab.STUDENT) {
                    studentReportContent(uiState, onNavigateToStudentDetail)
                } else {
                    groupReportContent(uiState, onNavigateToStudentDetail)
                }
            }
        }
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    val end = dateRangePickerState.selectedEndDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    if (start != null && end != null) {
                        viewModel.setDateRange(start, end)
                    }
                    showDatePicker = false
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("اختر الفترة", modifier = Modifier.padding(16.dp)) },
                headline = { Text("فترة التقرير", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionControls(uiState: ReportsUiState, viewModel: ReportsViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(Dimens.Spacing16)) {
            if (uiState.selectedTab == ReportsTab.STUDENT) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.students.find { it.studentId == uiState.selectedStudentId }?.fullName ?: "اختر الطالب",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.students.forEach { student ->
                            DropdownMenuItem(
                                text = { Text(student.fullName) },
                                onClick = {
                                    viewModel.selectStudent(student.studentId)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.grades.find { it.id == uiState.selectedGradeId }?.name ?: "اختر المجموعة",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text(grade.name) },
                                onClick = {
                                    viewModel.selectGrade(grade.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.studentReportContent(
    uiState: ReportsUiState,
    onNavigateToStudentDetail: (String) -> Unit
) {
    val report = uiState.studentReport

    if (report == null) {
        item {
            EmptyState(
                message = if (uiState.selectedStudentId == null) "الرجاء اختيار طالب لعرض التقرير" else "لا توجد بيانات لهذا الطالب في هذه الفترة",
                icon = Icons.Default.Assessment
            )
        }
        return
    }

    // Summary Cards Grid
    item {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                ReportMetricCard(
                    title = "الحضور",
                    value = String.format(Locale.US, "%.0f%%", report.attendancePercent),
                    subtitle = "حاضر: ${report.presentCount} | غائب: ${report.absentCount}",
                    color = EmeraldGreen,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                ReportMetricCard(
                    title = "التسميع",
                    value = String.format(Locale.US, "%.0f%%", report.recitationAvg),
                    subtitle = "عدد المرات: ${report.recitationCount}",
                    color = PrimaryIndigo,
                    icon = Icons.Default.AutoStories,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                ReportMetricCard(
                    title = "الواجبات",
                    value = String.format(Locale.US, "%.0f%%", report.homeworkPercent),
                    subtitle = "تم: ${report.homeworkCompletedCount} | لم يتم: ${report.homeworkIncompleteCount}",
                    color = ElectricBlue,
                    icon = Icons.Default.Assignment,
                    modifier = Modifier.weight(1f)
                )
                ReportMetricCard(
                    title = "الامتحانات",
                    value = String.format(Locale.US, "%.0f%%", report.examAvg),
                    subtitle = "عدد الامتحانات: ${report.examCount}",
                    color = VioletPurple,
                    icon = Icons.Default.School,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Details Sections
    sectionTitle("تفاصيل الحضور")
    if (report.attendanceDetails.isEmpty()) {
        item { Text("لا توجد سجلات حضور", modifier = Modifier.padding(Dimens.Spacing12), color = MaterialTheme.colorScheme.outline) }
    } else {
        items(report.attendanceDetails) { att ->
            DetailItem(
                title = att.date,
                value = att.status.labelAr,
                color = if (att.status == AttendanceStatus.PRESENT) EmeraldGreen else DangerRed
            )
        }
    }

    sectionTitle("سجلات التسميع")
    if (report.recitationDetails.isEmpty()) {
        item { Text("لا توجد سجلات تسميع", modifier = Modifier.padding(Dimens.Spacing12), color = MaterialTheme.colorScheme.outline) }
    } else {
        items(report.recitationDetails) { rec ->
            DetailItem(
                title = rec.date,
                subtitle = rec.title,
                value = "${(rec.score / rec.maxScore * 100).toInt()}%",
                color = PrimaryIndigo
            )
        }
    }

    sectionTitle("الواجبات")
    if (report.homeworkDetails.isEmpty()) {
        item { Text("لا توجد سجلات واجبات", modifier = Modifier.padding(Dimens.Spacing12), color = MaterialTheme.colorScheme.outline) }
    } else {
        items(report.homeworkDetails) { hw ->
            DetailItem(
                title = hw.date,
                subtitle = hw.title,
                value = hw.status.labelAr,
                color = if (hw.status == HomeworkStatus.COMPLETED) EmeraldGreen else DangerRed
            )
        }
    }

    sectionTitle("الامتحانات")
    if (report.examDetails.isEmpty()) {
        item { Text("لا توجد سجلات امتحانات", modifier = Modifier.padding(Dimens.Spacing12), color = MaterialTheme.colorScheme.outline) }
    } else {
        items(report.examDetails) { exam ->
            DetailItem(
                title = exam.date,
                subtitle = exam.examName,
                value = "${(exam.score / exam.maxScore * 100).toInt()}%",
                color = VioletPurple
            )
        }
    }
}

fun LazyListScope.groupReportContent(
    uiState: ReportsUiState,
    onNavigateToStudentDetail: (String) -> Unit
) {
    val report = uiState.groupReport

    if (report == null) {
        item {
            EmptyState(
                message = if (uiState.selectedGradeId == null) "الرجاء اختيار مجموعة لعرض التقرير" else "لا توجد بيانات لهذه المجموعة في هذه الفترة",
                icon = Icons.Default.Assessment
            )
        }
        return
    }

    // Summary Cards
    item {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                ReportMetricCard(
                    title = "متوسط الحضور",
                    value = String.format(Locale.US, "%.0f%%", report.avgAttendance),
                    subtitle = "طلاب المجموعة: ${report.totalStudents}",
                    color = EmeraldGreen,
                    icon = Icons.Default.Groups,
                    modifier = Modifier.weight(1f)
                )
                ReportMetricCard(
                    title = "التسميع",
                    value = "${report.totalRecitations}",
                    subtitle = "إجمالي عمليات التسميع",
                    color = PrimaryIndigo,
                    icon = Icons.Default.AutoStories,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                ReportMetricCard(
                    title = "الواجبات",
                    value = "${report.homeworkCompleted}",
                    subtitle = "مكتملة | ${report.homeworkIncomplete} غير مكتملة",
                    color = ElectricBlue,
                    icon = Icons.Default.Assignment,
                    modifier = Modifier.weight(1f)
                )
                ReportMetricCard(
                    title = "الامتحانات",
                    value = String.format(Locale.US, "%.0f%%", report.avgExamScore),
                    subtitle = "متوسط درجات المجموعة",
                    color = VioletPurple,
                    icon = Icons.Default.School,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    sectionTitle("أداء الطلاب")
    items(report.studentStats) { stat ->
        StudentPerformanceItem(
            stat = stat,
            onClick = { onNavigateToStudentDetail(stat.student.studentId) }
        )
    }
}

fun LazyListScope.sectionTitle(title: String) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ReportMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f))
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = color)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun DetailItem(
    title: String,
    subtitle: String? = null,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

@Composable
fun StudentPerformanceItem(
    stat: StudentGroupMetric,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentAvatar(
                fullName = stat.student.fullName,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stat.student.fullName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricLabel("حضور", stat.attendanceRate, EmeraldGreen)
                    MetricLabel("تسميع", stat.recitationRate, PrimaryIndigo)
                    MetricLabel("امتحانات", stat.examRate, VioletPurple)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun MetricLabel(label: String, rate: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: ${rate.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyState(message: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.Spacing32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
