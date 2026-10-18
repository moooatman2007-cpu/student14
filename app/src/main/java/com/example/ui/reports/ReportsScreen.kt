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
import androidx.compose.material.icons.automirrored.filled.Assignment
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var absenceConfirmTargets by remember { mutableStateOf<List<StudentDailyAttendance>?>(null) }
    var singleRecitationConfirmTarget by remember { mutableStateOf<StudentDailyRecitation?>(null) }
    var singleExamConfirmTarget by remember { mutableStateOf<StudentDailyExam?>(null) }
    var noPhoneStudentName by remember { mutableStateOf<String?>(null) }
    var isMonthlyReportMode by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotificationMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("📊 التقارير والإحصائيات", fontWeight = FontWeight.Bold) },
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
            // Report Types Tab Row
            ScrollableTabRow(
                selectedTabIndex = when {
                    isMonthlyReportMode -> 3
                    uiState.selectedTab == ReportsTab.GROUP_DAILY -> 0
                    uiState.selectedTab == ReportsTab.GRADE_DAILY -> 1
                    uiState.selectedTab == ReportsTab.STUDENT -> 2
                    uiState.selectedTab == ReportsTab.TEACHER_DAILY -> 4
                    else -> 0
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryIndigo,
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = uiState.selectedTab == ReportsTab.GROUP_DAILY && !isMonthlyReportMode,
                    onClick = {
                        isMonthlyReportMode = false
                        viewModel.selectTab(ReportsTab.GROUP_DAILY)
                    },
                    text = { Text("المجموعات", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.selectedTab == ReportsTab.GRADE_DAILY && !isMonthlyReportMode,
                    onClick = {
                        isMonthlyReportMode = false
                        viewModel.selectTab(ReportsTab.GRADE_DAILY)
                    },
                    text = { Text("الصفوف", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.selectedTab == ReportsTab.STUDENT && !isMonthlyReportMode,
                    onClick = {
                        isMonthlyReportMode = false
                        viewModel.selectTab(ReportsTab.STUDENT)
                    },
                    text = { Text("الطلاب", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = isMonthlyReportMode,
                    onClick = {
                        isMonthlyReportMode = true
                        viewModel.selectTab(ReportsTab.STUDENT)
                    },
                    text = { Text("التقرير الشهري", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.selectedTab == ReportsTab.TEACHER_DAILY && !isMonthlyReportMode,
                    onClick = {
                        isMonthlyReportMode = false
                        viewModel.selectTab(ReportsTab.TEACHER_DAILY)
                    },
                    text = { Text("يوم المعلم", fontWeight = FontWeight.Bold) }
                )
            }

            // Compact Contextual Controls Container
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.Spacing12, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Contextual Dropdown Selector
                    DailySelectionControls(uiState, viewModel)

                    // 2. Date / Period Bar according to active tab
                    if (uiState.selectedTab == ReportsTab.GROUP_DAILY) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mode Toggle (Period vs Daily)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (uiState.groupReportMode == GroupReportMode.PERIOD) PrimaryIndigo else Color.Transparent,
                                    modifier = Modifier.clickable { viewModel.setGroupReportMode(GroupReportMode.PERIOD) }
                                ) {
                                    Text(
                                        text = "فترة زمنية",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (uiState.groupReportMode == GroupReportMode.PERIOD) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (uiState.groupReportMode == GroupReportMode.DAILY) PrimaryIndigo else Color.Transparent,
                                    modifier = Modifier.clickable { viewModel.setGroupReportMode(GroupReportMode.DAILY) }
                                ) {
                                    Text(
                                        text = "تقرير يومي",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (uiState.groupReportMode == GroupReportMode.DAILY) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Period / Date summary in the same row
                            if (uiState.groupReportMode == GroupReportMode.PERIOD) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = uiState.startDate == LocalDate.now().minusDays(30),
                                        onClick = { viewModel.setDateRange(LocalDate.now().minusDays(30), LocalDate.now()) },
                                        label = { Text("30 يوم", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(32.dp)
                                    )
                                    FilterChip(
                                        selected = uiState.startDate == LocalDate.now().minusDays(7),
                                        onClick = { viewModel.setDateRange(LocalDate.now().minusDays(7), LocalDate.now()) },
                                        label = { Text("7 أيام", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(32.dp)
                                    )
                                    FilterChip(
                                        selected = uiState.startDate == LocalDate.now().withDayOfMonth(1),
                                        onClick = { viewModel.setDateRange(LocalDate.now().withDayOfMonth(1), LocalDate.now()) },
                                        label = { Text("الشهر", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            } else {
                                CompactDateBar(
                                    selectedDate = uiState.selectedDate,
                                    onPrevious = { viewModel.previousDay() },
                                    onNext = { viewModel.nextDay() },
                                    onPickDate = { showDatePicker = true },
                                    onTodayClick = { viewModel.setSelectedDate(LocalDate.now()) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else if (uiState.selectedTab != ReportsTab.STUDENT) {
                        CompactDateBar(
                            selectedDate = uiState.selectedDate,
                            onPrevious = { viewModel.previousDay() },
                            onNext = { viewModel.nextDay() },
                            onPickDate = { showDatePicker = true },
                            onTodayClick = { viewModel.setSelectedDate(LocalDate.now()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Compact Period Indicator for Student Report
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(15.dp), tint = PrimaryIndigo)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الفترة: ${uiState.startDate} — ${uiState.endDate}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Text(
                                    text = "تغيير الفترة",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryIndigo,
                                    modifier = Modifier.clickable { showDateRangePicker = true }
                                )
                            }
                        }
                    }

                    if (isMonthlyReportMode && uiState.selectedTab == ReportsTab.STUDENT) {
                        Surface(
                            color = PrimaryIndigoLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "التقرير الشهري المعتمد للطالب وتجهيز الإشعار",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryIndigo
                                )
                            }
                        }
                    }
                }
            }

            // Report Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.Spacing16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
            ) {
                when (uiState.selectedTab) {
                    ReportsTab.GROUP_DAILY -> {
                        if (uiState.groupReportMode == GroupReportMode.PERIOD) {
                            groupPeriodReportContent(uiState, onNavigateToStudentDetail)
                        } else {
                            groupDailyReportContent(
                                uiState = uiState,
                                onNavigateToStudentDetail = onNavigateToStudentDetail,
                                onSendAbsenceClick = { targets -> absenceConfirmTargets = targets },
                                onSendRecitationClick = { item ->
                                    if (item.student.parentPhone.isBlank()) {
                                        noPhoneStudentName = item.student.fullName
                                    } else {
                                        singleRecitationConfirmTarget = item
                                    }
                                },
                                onSendExamClick = { item ->
                                    if (item.student.parentPhone.isBlank()) {
                                        noPhoneStudentName = item.student.fullName
                                    } else {
                                        singleExamConfirmTarget = item
                                    }
                                }
                            )
                        }
                    }
                    ReportsTab.GRADE_DAILY -> gradeDailyReportContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToStudentDetail = onNavigateToStudentDetail,
                        onSendAbsenceClick = { targets -> absenceConfirmTargets = targets },
                        onSendRecitationClick = { item ->
                            if (item.student.parentPhone.isBlank()) {
                                noPhoneStudentName = item.student.fullName
                            } else {
                                singleRecitationConfirmTarget = item
                            }
                        },
                        onSendExamClick = { item ->
                            if (item.student.parentPhone.isBlank()) {
                                noPhoneStudentName = item.student.fullName
                            } else {
                                singleExamConfirmTarget = item
                            }
                        }
                    )
                    ReportsTab.TEACHER_DAILY -> teacherDailyReportContent(uiState, viewModel)
                    ReportsTab.STUDENT -> studentReportContent(
                        uiState = uiState,
                        onNavigateToStudentDetail = onNavigateToStudentDetail,
                        viewModel = viewModel,
                        isMonthlyReportMode = isMonthlyReportMode
                    )
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

    // Absence Confirmation Dialog
    if (absenceConfirmTargets != null) {
        val targets = absenceConfirmTargets!!
        val missingPhoneCount = targets.count { it.student.parentPhone.isBlank() }
        val validPhoneCount = targets.size - missingPhoneCount

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSendingNotification) absenceConfirmTargets = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد تجهيز إشعار غياب اليوم",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "سيتم تسجيل إشعارات الغياب لـ ${targets.size} طالب/طلاب في تاريخ ${uiState.selectedDate}:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(targets) { target ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${target.student.fullName}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (target.student.parentPhone.isBlank()) {
                                        Text(
                                            text = "⚠️ بدون رقم",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = WarmAmber
                                        )
                                    } else {
                                        Text(
                                            text = target.student.parentPhone,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (missingPhoneCount > 0) {
                        Text(
                            text = "⚠️ تنبيه: يوجد $missingPhoneCount طالب بدون رقم هاتف وسيتم تخطيهم تلقائياً.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmAmber
                        )
                    }

                    Text(
                        text = "يتم تسجيل الإشعار بحالة الانتظار ليتم تجهيزه ومعالجته بدون تكرار.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendAbsenceNotifications(targets) { _ ->
                            absenceConfirmTargets = null
                        }
                    },
                    enabled = !uiState.isSendingNotification && validPhoneCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isSendingNotification) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("تأكيد تجهيز الإشعار ($validPhoneCount)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { absenceConfirmTargets = null },
                    enabled = !uiState.isSendingNotification
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Single Recitation Confirmation Dialog
    if (singleRecitationConfirmTarget != null) {
        val target = singleRecitationConfirmTarget!!
        val hasPhone = target.student.parentPhone.isNotBlank()

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSendingNotification) singleRecitationConfirmTarget = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد تجهيز إشعار التسميع",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "سيتم تجهيز إشعار نتيجة التسميع لولي الأمر:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "👤 الطالب: ${target.student.fullName}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "📖 الموضع: ${target.recitation.title}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "⭐️ التقييم: ${target.recitation.score} / ${target.recitation.maxScore}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreen
                            )
                            if (!target.recitation.note.isNullOrBlank()) {
                                Text(
                                    text = "📝 ملاحظة: ${target.recitation.note}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (hasPhone) {
                                Text(
                                    text = "📱 هاتف ولي الأمر: ${target.student.parentPhone}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryIndigo
                                )
                            } else {
                                Text(
                                    text = "⚠️ لا يوجد رقم هاتف مسجل لولي الأمر",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = DangerRed
                                )
                            }
                        }
                    }
                    Text(
                        text = "يتم تسجيل الإشعار بحالة PENDING في النظام ويقوم العامل الآلي بالإرسال عبر واتساب.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendRecitationNotifications(listOf(target)) { _ ->
                            singleRecitationConfirmTarget = null
                        }
                    },
                    enabled = !uiState.isSendingNotification && hasPhone,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isSendingNotification) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("تأكيد الإرسال")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { singleRecitationConfirmTarget = null },
                    enabled = !uiState.isSendingNotification
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Single Exam Confirmation Dialog
    if (singleExamConfirmTarget != null) {
        val target = singleExamConfirmTarget!!
        val hasPhone = target.student.parentPhone.isNotBlank()

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSendingNotification) singleExamConfirmTarget = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد تجهيز إشعار الاختبار",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "سيتم تجهيز إشعار نتيجة الاختبار لولي الأمر:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "👤 الطالب: ${target.student.fullName}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "📋 الاختبار: ${target.exam.examName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "🎯 الدرجة: ${target.exam.score} / ${target.exam.maxScore}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryIndigo
                            )
                            if (!target.exam.note.isNullOrBlank()) {
                                Text(
                                    text = "📝 ملاحظة: ${target.exam.note}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (hasPhone) {
                                Text(
                                    text = "📱 هاتف ولي الأمر: ${target.student.parentPhone}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryIndigo
                                )
                            } else {
                                Text(
                                    text = "⚠️ لا يوجد رقم هاتف مسجل لولي الأمر",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = DangerRed
                                )
                            }
                        }
                    }
                    Text(
                        text = "يتم تسجيل الإشعار بحالة PENDING في النظام ويقوم العامل الآلي بالإرسال عبر واتساب.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendExamNotifications(listOf(target)) { _ ->
                            singleExamConfirmTarget = null
                        }
                    },
                    enabled = !uiState.isSendingNotification && hasPhone,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isSendingNotification) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("تأكيد الإرسال")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { singleExamConfirmTarget = null },
                    enabled = !uiState.isSendingNotification
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // No Phone Warning Dialog
    if (noPhoneStudentName != null) {
        AlertDialog(
            onDismissRequest = { noPhoneStudentName = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("لا يوجد رقم هاتف مسجل", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "الطالب ($noPhoneStudentName) لا يمتلك رقم هاتف مسجل لولي الأمر. يرجى تعديل بيانات الطالب وإدخال رقم هاتف ولي الأمر أولاً لتتمكن من إرسال الإشعارات."
                )
            },
            confirmButton = {
                TextButton(onClick = { noPhoneStudentName = null }) {
                    Text("حسناً")
                }
            }
        )
    }
}

@Composable
fun CompactDateBar(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = selectedDate == LocalDate.now()
    val formattedDate = remember(selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.forLanguageTag("ar"))
        selectedDate.format(formatter)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "اليوم التالي", modifier = Modifier.size(16.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPickDate)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = PrimaryIndigo)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isToday) {
                Text(
                    text = "اليوم",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryIndigo,
                    modifier = Modifier
                        .clickable(onClick = onTodayClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "اليوم السابق", modifier = Modifier.size(16.dp))
            }
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
    CompactDateBar(
        selectedDate = selectedDate,
        onPrevious = onPrevious,
        onNext = onNext,
        onPickDate = onPickDate,
        onTodayClick = onTodayClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySelectionControls(uiState: ReportsUiState, viewModel: ReportsViewModel) {
    var expanded by remember { mutableStateOf(false) }

    if (uiState.selectedTab == ReportsTab.TEACHER_DAILY) return

    Box(modifier = Modifier.fillMaxWidth()) {
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
                        placeholder = { Text("اختر المجموعة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
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
                        placeholder = { Text("اختر الصف الدراسي") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
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
                        placeholder = { Text("اختر الطالب") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
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

// ---------------- 1. Group Period & Daily Report Content ----------------
fun LazyListScope.groupPeriodReportContent(
    uiState: ReportsUiState,
    onNavigateToStudentDetail: (String) -> Unit
) {
    val report = uiState.groupPeriodReport

    if (report == null) {
        item {
            EmptyState(
                message = if (uiState.groups.isEmpty()) "لا توجد مجموعات معرفة للمدرس" else "الرجاء اختيار مجموعة لعرض تقرير الفترة",
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
                        Text(
                            text = "الفترة: ${report.startDate} إلى ${report.endDate}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${report.totalSessionsHeld} جلسة مسجلة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Average attendance rate metric
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("متوسط نسبة الحضور للمجموعة", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "%.1f%%".format(report.avgAttendanceRate),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = if (report.avgAttendanceRate >= 80f) EmeraldGreen else if (report.avgAttendanceRate >= 60f) WarmAmber else DangerRed
                    )
                }
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    // Student List Section Title
    sectionTitle("كشف طلاب المجموعة والضيوف (${report.studentList.size})")

    if (report.studentList.isEmpty()) {
        item {
            EmptyState(message = "لا توجد سجلات حضور مسجلة لهذه المجموعة خلال الفترة المحددة", icon = Icons.Default.Person)
        }
    } else {
        items(report.studentList) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToStudentDetail(item.student.studentId) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudentAvatar(
                            fullName = item.student.fullName,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.student.fullName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                if (item.isGuest) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = WarmAmberLight,
                                        contentColor = WarmAmber
                                    ) {
                                        Text(
                                            text = "ضيف (أصله: ${item.primaryGroupName})",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (item.student.studentCode.isNotBlank()) {
                                Text(
                                    text = "كود: #${item.student.studentCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Attendance Rate % Chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.attendanceRate >= 80f) EmeraldGreenLight else if (item.attendanceRate >= 60f) WarmAmberLight else DangerRedLight
                        ) {
                            Text(
                                text = "%.0f%%".format(item.attendanceRate),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (item.attendanceRate >= 80f) EmeraldGreen else if (item.attendanceRate >= 60f) WarmAmber else DangerRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Breakdown chips row: Present, Absent, Late, Excused
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldGreenLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("حاضر 🟢", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                                Text("${item.presentCount}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = EmeraldGreen)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DangerRedLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("غائب 🔴", style = MaterialTheme.typography.labelSmall, color = DangerRed)
                                Text("${item.absentCount}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DangerRed)
                            }
                        }

                        if (item.lateCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = WarmAmberLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("متأخر 🟡", style = MaterialTheme.typography.labelSmall, color = WarmAmber)
                                    Text("${item.lateCount}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = WarmAmber)
                                }
                            }
                        }

                        if (item.excusedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryIndigoLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("عذر 🔵", style = MaterialTheme.typography.labelSmall, color = PrimaryIndigo)
                                    Text("${item.excusedCount}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryIndigo)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.groupDailyReportContent(
    uiState: ReportsUiState,
    onNavigateToStudentDetail: (String) -> Unit,
    onSendAbsenceClick: (List<StudentDailyAttendance>) -> Unit = {},
    onSendRecitationClick: (StudentDailyRecitation) -> Unit = {},
    onSendExamClick: (StudentDailyExam) -> Unit = {}
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

    // Absence Notification Card
    if (report.absentStudents.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_group_absence_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
                border = BorderStroke(1.5.dp, DangerRed.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DangerRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تنبيه غياب اليوم",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = DangerRed
                            )
                            Text(
                                text = "يوجد ${report.absentStudents.size} طالب غائب مسجل في تاريخ ${report.date}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onSendAbsenceClick(report.absentStudents) },
                        enabled = !uiState.isSendingNotification,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("send_group_absence_button")
                    ) {
                        if (uiState.isSendingNotification) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "📱 تجهيز إشعار غياب اليوم",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Daily Recitations Section
    sectionTitle(if (report.dailyRecitations.isNotEmpty()) "📖 تسميعات اليوم (${report.dailyRecitations.size})" else "📖 تسميعات اليوم")
    if (report.dailyRecitations.isNotEmpty()) {
        items(report.dailyRecitations) { item ->
            DailyRecitationCard(
                item = item,
                isSending = uiState.isSendingNotification,
                onSendClick = { onSendRecitationClick(item) },
                onNavigateToStudentDetail = onNavigateToStudentDetail
            )
        }
    } else {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_group_daily_recitations_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "لم يتم تسجيل أي تسميع لهذا اليوم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Daily Exams Section
    sectionTitle(if (report.dailyExams.isNotEmpty()) "📝 اختبارات اليوم (${report.dailyExams.size})" else "📝 اختبارات اليوم")
    if (report.dailyExams.isNotEmpty()) {
        items(report.dailyExams) { item ->
            DailyExamCard(
                item = item,
                isSending = uiState.isSendingNotification,
                onSendClick = { onSendExamClick(item) },
                onNavigateToStudentDetail = onNavigateToStudentDetail
            )
        }
    } else {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_group_daily_exams_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "لم يتم تسجيل أي اختبار لهذا اليوم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    viewModel: ReportsViewModel,
    onNavigateToStudentDetail: (String) -> Unit = {},
    onSendAbsenceClick: (List<StudentDailyAttendance>) -> Unit = {},
    onSendRecitationClick: (StudentDailyRecitation) -> Unit = {},
    onSendExamClick: (StudentDailyExam) -> Unit = {}
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

    // Grade Absence Notification Card
    if (report.absentStudents.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_grade_absence_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
                border = BorderStroke(1.5.dp, DangerRed.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DangerRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تنبيه غياب الصف",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = DangerRed
                            )
                            Text(
                                text = "يوجد ${report.absentStudents.size} طالب غائب مسجل في عموم هذا الصف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onSendAbsenceClick(report.absentStudents) },
                        enabled = !uiState.isSendingNotification,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("send_grade_absence_button")
                    ) {
                        if (uiState.isSendingNotification) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "📱 تجهيز إشعار غياب اليوم",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
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

    // Grade Daily Recitations Section
    sectionTitle(if (report.dailyRecitations.isNotEmpty()) "📖 تسميعات اليوم في الصف (${report.dailyRecitations.size})" else "📖 تسميعات اليوم في الصف")
    if (report.dailyRecitations.isNotEmpty()) {
        items(report.dailyRecitations) { item ->
            DailyRecitationCard(
                item = item,
                isSending = uiState.isSendingNotification,
                onSendClick = { onSendRecitationClick(item) },
                onNavigateToStudentDetail = onNavigateToStudentDetail
            )
        }
    } else {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_grade_daily_recitations_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "لم يتم تسجيل أي تسميع لهذا اليوم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Grade Daily Exams Section
    sectionTitle(if (report.dailyExams.isNotEmpty()) "📝 اختبارات اليوم في الصف (${report.dailyExams.size})" else "📝 اختبارات اليوم في الصف")
    if (report.dailyExams.isNotEmpty()) {
        items(report.dailyExams) { item ->
            DailyExamCard(
                item = item,
                isSending = uiState.isSendingNotification,
                onSendClick = { onSendExamClick(item) },
                onNavigateToStudentDetail = onNavigateToStudentDetail
            )
        }
    } else {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_grade_daily_exams_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "لم يتم تسجيل أي اختبار لهذا اليوم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

// ---------------- 4. Student Report Content (Period & Monthly) ----------------
fun LazyListScope.studentReportContent(
    uiState: ReportsUiState,
    onNavigateToStudentDetail: (String) -> Unit,
    viewModel: ReportsViewModel,
    isMonthlyReportMode: Boolean = false
) {
    val report = uiState.studentReport

    if (report == null) {
        item {
            EmptyState(
                message = if (uiState.selectedStudentId == null) {
                    if (isMonthlyReportMode) "الرجاء اختيار طالب من القائمة أعلاه لعرض تقريره الشهري"
                    else "الرجاء اختيار طالب لعرض التقرير"
                } else "لا توجد بيانات لهذا الطالب في هذه الفترة",
                icon = if (isMonthlyReportMode) Icons.Default.CalendarMonth else Icons.Default.Assessment
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
                    icon = Icons.AutoMirrored.Filled.Assignment,
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

    // WhatsApp Monthly Report Notification Card & Action
    item {
        var showConfirmDialog by remember { mutableStateOf(false) }
        var showNoPhoneDialog by remember { mutableStateOf(false) }

        val monthlyReport = uiState.currentMonthlyReport
        if (monthlyReport != null && monthlyReport.reportId.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_report_notification_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryIndigo.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إشعار التقرير الشهري لولي الأمر",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "شهر ${monthlyReport.month} / ${monthlyReport.year}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "جاهز للتجهيز",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreen
                            )
                        }
                    }

                    Text(
                        text = "يتوفر تقرير شهري معتمد للطالب (${report.student.fullName}). يمكنك تجهيز ملخص الأداء المعتمد لإرساله إلى هاتف ولي الأمر المسجل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            if (report.student.parentPhone.isBlank()) {
                                showNoPhoneDialog = true
                            } else {
                                showConfirmDialog = true
                            }
                        },
                        enabled = !uiState.isSendingNotification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_monthly_report_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        if (uiState.isSendingNotification) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري التجهيز...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تجهيز التقرير لولي الأمر", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_monthly_report_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryIndigo.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "التقرير الشهري للطالب",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "شهر ${uiState.startDate.monthValue} / ${uiState.startDate.year}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "لم يتم إصدار تقرير شهري معتمد لهذا الطالب بعد عن هذا الشهر. يمكنك إصدار وحفظ التقرير ومراجعته من خلال صفحة تفاصيل الطالب.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { onNavigateToStudentDetail(report.student.studentId) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الانتقال لتبويب التقرير الشهري في صفحة الطالب")
                    }
                }
            }
        }

        if (showConfirmDialog && monthlyReport != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!uiState.isSendingNotification) showConfirmDialog = false
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "تأكيد تجهيز التقرير الشهري",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "سيتم تجهيز إشعار التقرير الشهري بالبيانات التالية:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "👤 الطالب: ${report.student.fullName}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "🗓 عن شهر: ${monthlyReport.month} / ${monthlyReport.year}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "📱 هاتف ولي الأمر: ${report.student.parentPhone}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryIndigo
                                )
                            }
                        }
                        Text(
                            text = "يتم تسجيل الإشعار بحالة الانتظار ليتم تجهيزه ومعالجته بدون تكرار.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.sendMonthlyReportNotification(
                                student = report.student,
                                report = monthlyReport
                            ) { _, _ ->
                                showConfirmDialog = false
                            }
                        },
                        enabled = !uiState.isSendingNotification,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isSendingNotification) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("تأكيد الإرسال")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirmDialog = false },
                        enabled = !uiState.isSendingNotification
                    ) {
                        Text("إلغاء")
                    }
                }
            )
        }

        if (showNoPhoneDialog) {
            AlertDialog(
                onDismissRequest = { showNoPhoneDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text("لا يوجد رقم هاتف مسجل", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "الطالب (${report.student.fullName}) لا يمتلك رقم هاتف مسجل لولي الأمر. يرجى تعديل بيانات الطالب وإدخال رقم هاتف ولي الأمر أولاً لتتمكن من تجهيز الإشعارات."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showNoPhoneDialog = false }) {
                        Text("حسناً")
                    }
                }
            )
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

@Composable
fun DailyRecitationCard(
    item: StudentDailyRecitation,
    isSending: Boolean,
    onSendClick: () -> Unit,
    onNavigateToStudentDetail: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToStudentDetail(item.student.studentId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentAvatar(
                fullName = item.student.fullName,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.student.fullName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "📖 ${item.recitation.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "⭐️ التقييم: ${item.recitation.score} / ${item.recitation.maxScore}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = EmeraldGreen
                )
                if (!item.recitation.note.isNullOrBlank()) {
                    Text(
                        text = "ملاحظة: ${item.recitation.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmAmber
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onSendClick,
                enabled = !isSending,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = EmeraldGreen.copy(alpha = 0.12f),
                    contentColor = EmeraldGreen
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تجهيز الإشعار",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun DailyExamCard(
    item: StudentDailyExam,
    isSending: Boolean,
    onSendClick: () -> Unit,
    onNavigateToStudentDetail: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToStudentDetail(item.student.studentId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentAvatar(
                fullName = item.student.fullName,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.student.fullName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "📝 ${item.exam.examName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "🎯 الدرجة: ${item.exam.score} / ${item.exam.maxScore}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryIndigo
                )
                if (!item.exam.note.isNullOrBlank()) {
                    Text(
                        text = "ملاحظة: ${item.exam.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmAmber
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onSendClick,
                enabled = !isSending,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PrimaryIndigo.copy(alpha = 0.12f),
                    contentColor = PrimaryIndigo
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تجهيز الإشعار",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
