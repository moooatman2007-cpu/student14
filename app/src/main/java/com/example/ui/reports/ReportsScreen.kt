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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.time.format.DateTimeFormatter
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
    var showDateRangePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 التقارير اليومية", fontWeight = FontWeight.Bold) },
                actions = {
                    if (uiState.selectedTab == ReportsTab.STUDENT) {
                        IconButton(onClick = { showDateRangePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "فترة التقرير")
                        }
                    } else {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "تاريخ التقرير")
                        }
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
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryIndigo,
                edgePadding = 12.dp
            ) {
                ReportsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.titleAr, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Date Bar Navigation for Daily Reports
            if (uiState.selectedTab != ReportsTab.STUDENT) {
                DailyDateBar(
                    selectedDate = uiState.selectedDate,
                    onPrevious = { viewModel.previousDay() },
                    onNext = { viewModel.nextDay() },
                    onPickDate = { showDatePicker = true },
                    onTodayClick = { viewModel.setSelectedDate(LocalDate.now()) }
                )
            } else {
                // Period Indicator for Student Report
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
            }

            // Controls Dropdown
            DailySelectionControls(uiState, viewModel)

            // Report Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.Spacing16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
            ) {
                when (uiState.selectedTab) {
                    ReportsTab.GROUP_DAILY -> groupDailyReportContent(uiState, onNavigateToStudentDetail)
                    ReportsTab.GRADE_DAILY -> gradeDailyReportContent(uiState, viewModel)
                    ReportsTab.TEACHER_DAILY -> teacherDailyReportContent(uiState, viewModel)
                    ReportsTab.STUDENT -> studentReportContent(uiState, onNavigateToStudentDetail)
                }
            }
        }
    }

    // Single Date Picker Dialog for Daily Reports
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setSelectedDate(date)
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
            DatePicker(state = datePickerState)
        }
    }

    // Date Range Picker for Student Report
    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
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
                    showDateRangePicker = false
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
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

@Composable
fun DailyDateBar(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onTodayClick: () -> Unit
) {
    val isToday = selectedDate == LocalDate.now()
    val formattedDate = remember(selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("ar"))
        selectedDate.format(formatter)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Spacing12, vertical = Dimens.Spacing8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "اليوم التالي")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickDate)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = PrimaryIndigo)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isToday) {
                    TextButton(onClick = onTodayClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("اليوم", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "اليوم السابق")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySelectionControls(uiState: ReportsUiState, viewModel: ReportsViewModel) {
    var expanded by remember { mutableStateOf(false) }

    if (uiState.selectedTab == ReportsTab.TEACHER_DAILY) return

    Surface(
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8)) {
            when (uiState.selectedTab) {
                ReportsTab.GROUP_DAILY -> {
                    val currentGroup = uiState.groups.find { it.id == uiState.selectedGroupId }
                    val groupGradeName = uiState.grades.find { it.id == currentGroup?.gradeId }?.name ?: ""
                    val displayText = if (currentGroup != null) "${currentGroup.name} ($groupGradeName)" else "اختر المجموعة"

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المجموعة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.groups.forEach { group ->
                                val gradeName = uiState.grades.find { it.id == group.gradeId }?.name ?: ""
                                DropdownMenuItem(
                                    text = { Text("${group.name} - $gradeName") },
                                    onClick = {
                                        viewModel.selectGroup(group.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                ReportsTab.GRADE_DAILY -> {
                    val currentGrade = uiState.grades.find { it.id == uiState.selectedGradeId }
                    val displayText = currentGrade?.name ?: "اختر الصف"

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الصف الدراسي") },
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

                ReportsTab.STUDENT -> {
                    val currentStudent = uiState.students.find { it.studentId == uiState.selectedStudentId }
                    val displayText = currentStudent?.fullName ?: "اختر الطالب"

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الطالب") },
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
                }

                else -> {}
            }
        }
    }
}

// ---------------- 1. Group Daily Report Content ----------------
fun LazyListScope.groupDailyReportContent(
    uiState: ReportsUiState,
    onNavigateToStudentDetail: (String) -> Unit
) {
    val report = uiState.groupDailyReport

    if (report == null) {
        item {
            EmptyState(
                message = if (uiState.groups.isEmpty()) "لا توجد مجموعات معرفة للمدرس" else "الرجاء اختيار مجموعة لعرض التقرير اليومي",
                icon = Icons.Default.Groups
            )
        }
        return
    }

    // Header Card
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryIndigoLight)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = report.group.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = PrimaryIndigo
                        )
                        Text(
                            text = "الصف: ${report.gradeName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryIndigo
                    ) {
                        Text(
                            text = "${report.totalStudents} طالب",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Attendance Summary Metrics
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportMetricCard(
                title = "حاضر",
                value = "${report.presentCount}",
                subtitle = "🟢 حاضر",
                color = EmeraldGreen,
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
            ReportMetricCard(
                title = "غائب",
                value = "${report.absentCount}",
                subtitle = "🔴 غائب",
                color = DangerRed,
                icon = Icons.Default.Cancel,
                modifier = Modifier.weight(1f)
            )
            ReportMetricCard(
                title = "لم يسجل",
                value = "${report.noRecordCount}",
                subtitle = "⚪ بدون سجل",
                color = MaterialTheme.colorScheme.outline,
                icon = Icons.Default.RemoveCircleOutline,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Student List Section Title
    sectionTitle("قائمة الطلاب (${report.studentList.size})")

    if (report.studentList.isEmpty()) {
        item {
            EmptyState(message = "لا يوجد طلاب مسجلين في هذه المجموعة لهذه الفترة", icon = Icons.Default.Person)
        }
    } else {
        items(report.studentList) { studentAtt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToStudentDetail(studentAtt.student.studentId) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudentAvatar(
                        fullName = studentAtt.student.fullName,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = studentAtt.student.fullName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (studentAtt.student.studentCode.isNotBlank()) {
                            Text(
                                text = "كود: #${studentAtt.student.studentCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!studentAtt.note.isNullOrBlank()) {
                            Text(
                                text = "ملاحظة: ${studentAtt.note}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmAmber
                            )
                        }
                    }

                    // Attendance Status Badge
                    val (statusColor, statusBg, statusText) = when (studentAtt.status) {
                        DailyAttendanceStatus.PRESENT -> Triple(EmeraldGreen, EmeraldGreenLight, "🟢 حاضر")
                        DailyAttendanceStatus.ABSENT -> Triple(DangerRed, DangerRed.copy(alpha = 0.1f), "🔴 غائب")
                        DailyAttendanceStatus.NO_RECORD -> Triple(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant, "⚪ لم يسجل")
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 2. Grade Daily Report Content ----------------
fun LazyListScope.gradeDailyReportContent(
    uiState: ReportsUiState,
    viewModel: ReportsViewModel
) {
    val report = uiState.gradeDailyReport

    if (report == null) {
        item {
            EmptyState(
                message = if (uiState.grades.isEmpty()) "لا توجد صفوف معرفة" else "الرجاء اختيار صف لعرض التقرير اليومي",
                icon = Icons.Default.School
            )
        }
        return
    }

    // Grade Overview Header Card
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🏫 ${report.grade.name}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeaderMetric(label = "إجمالي الطلاب", value = "${report.totalStudents}", color = Color.White)
                    HeaderMetric(label = "عدد المجموعات", value = "${report.totalGroups}", color = PrimaryIndigoLight)
                    HeaderMetric(label = "حاضر", value = "${report.presentCount}", color = EmeraldGreen)
                    HeaderMetric(label = "غائب", value = "${report.absentCount}", color = DangerRed)
                    HeaderMetric(label = "لم يسجل", value = "${report.noRecordCount}", color = Color.LightGray)
                }
            }
        }
    }

    // Group Breakdown Section
    sectionTitle("تفاصيل المجموعات (${report.groupBreakdowns.size})")

    if (report.groupBreakdowns.isEmpty()) {
        item {
            EmptyState(message = "لا توجد مجموعات بهذا الصف الدراسي", icon = Icons.Default.Groups)
        }
    } else {
        items(report.groupBreakdowns) { breakdown ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.selectGroup(breakdown.group.id)
                        viewModel.selectTab(ReportsTab.GROUP_DAILY)
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = breakdown.group.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${breakdown.totalStudents} طالب",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillMetric(label = "حاضر", count = breakdown.presentCount, color = EmeraldGreen, modifier = Modifier.weight(1f))
                        PillMetric(label = "غائب", count = breakdown.absentCount, color = DangerRed, modifier = Modifier.weight(1f))
                        PillMetric(label = "لم يسجل", count = breakdown.noRecordCount, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ---------------- 3. Teacher Daily Report Content ----------------
fun LazyListScope.teacherDailyReportContent(
    uiState: ReportsUiState,
    viewModel: ReportsViewModel
) {
    val report = uiState.teacherDailyReport

    if (report == null) {
        item {
            EmptyState(message = "لا توجد بيانات متاحة للمدرس في هذا اليوم", icon = Icons.Default.Assessment)
        }
        return
    }

    // Teacher Overview Card
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryIndigo)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "👨‍🏫 التقرير الشامل للمدرس",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeaderMetric(label = "الطلاب", value = "${report.totalStudents}", color = Color.White)
                    HeaderMetric(label = "المجموعات", value = "${report.totalGroups}", color = PrimaryIndigoLight)
                    HeaderMetric(label = "الصفوف النشطة", value = "${report.totalGradesWithGroups}", color = WarmAmber)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeaderMetric(label = "🟢 حاضر", value = "${report.presentCount}", color = EmeraldGreen)
                    HeaderMetric(label = "🔴 غائب", value = "${report.absentCount}", color = DangerRed)
                    HeaderMetric(label = "⚪ لم يسجل", value = "${report.noRecordCount}", color = Color.LightGray)
                }
            }
        }
    }

    // Stages Breakdown
    sectionTitle("التفصيل حسب المراحل الدراسية")

    if (report.stageBreakdowns.isEmpty()) {
        item {
            EmptyState(message = "لا توجد مراحل دراسية بها بيانات لهذا اليوم", icon = Icons.Default.School)
        }
    } else {
        items(report.stageBreakdowns) { stageReport ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🏫 المرحلة: ${stageReport.stageName}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PrimaryIndigo
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillMetric(label = "طلاب", count = stageReport.totalStudents, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        PillMetric(label = "حاضر", count = stageReport.presentCount, color = EmeraldGreen, modifier = Modifier.weight(1f))
                        PillMetric(label = "غائب", count = stageReport.absentCount, color = DangerRed, modifier = Modifier.weight(1f))
                        PillMetric(label = "لم يسجل", count = stageReport.noRecordCount, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inner Grade Cards
                    stageReport.gradeBreakdowns.forEach { gradeReport ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.selectGrade(gradeReport.grade.id)
                                    viewModel.selectTab(ReportsTab.GRADE_DAILY)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = gradeReport.grade.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${gradeReport.totalStudents} طالب | ${gradeReport.totalGroups} مجموعات",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("🟢 ${gradeReport.presentCount}", style = MaterialTheme.typography.labelMedium)
                                    Text("🔴 ${gradeReport.absentCount}", style = MaterialTheme.typography.labelMedium)
                                    Text("⚪ ${gradeReport.noRecordCount}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 4. Student Report Content (Period) ----------------
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
                color = if (att.status == AttendanceStatus.PRESENT || att.status == AttendanceStatus.LATE) EmeraldGreen else DangerRed
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
                value = "${if (rec.maxScore > 0) (rec.score / rec.maxScore * 100).toInt() else 0}%",
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
                value = "${if (exam.maxScore > 0) (exam.score / exam.maxScore * 100).toInt() else 0}%",
                color = VioletPurple
            )
        }
    }
}

// ---------------- Helpers & UI Components ----------------

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
fun HeaderMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun PillMetric(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text("$count", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
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
