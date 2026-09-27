package com.example.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.EducationalStages
import com.example.core.model.Group
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateBack: () -> Unit,
    onStartAttendance: (String, String) -> Unit,
    viewModel: GroupsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isStageFilterExpanded by remember { mutableStateOf(false) }
    var isGradeFilterExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "المجموعات الدراسية",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("groups_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openAddDialog() }, modifier = Modifier.testTag("add_group_button")) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "إضافة مجموعة",
                            tint = PrimaryIndigo
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = PrimaryIndigo,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("+ إضافة مجموعة", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_group_fab")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("groups_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Bar (Arabic RTL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing20, vertical = Dimens.Spacing8),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stage Filter Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { isStageFilterExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("filter_stage_button")
                    ) {
                        Text(
                            text = uiState.selectedStage ?: "المرحلة الدراسية",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = isStageFilterExpanded,
                        onDismissRequest = { isStageFilterExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("الكل") },
                            onClick = {
                                viewModel.selectStage(null)
                                isStageFilterExpanded = false
                            }
                        )
                        EducationalStages.ALL_STAGES.forEach { stage ->
                            DropdownMenuItem(
                                text = { Text(stage) },
                                onClick = {
                                    viewModel.selectStage(stage)
                                    isStageFilterExpanded = false
                                }
                            )
                        }
                    }
                }

                // Grade Filter Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { isGradeFilterExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("filter_grade_button")
                    ) {
                        val gradeName = uiState.grades.find { it.id == uiState.selectedGradeId }?.name ?: "الصف الدراسي"
                        Text(
                            text = gradeName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = isGradeFilterExpanded,
                        onDismissRequest = { isGradeFilterExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("الكل") },
                            onClick = {
                                viewModel.selectGrade(null)
                                isGradeFilterExpanded = false
                            }
                        )
                        uiState.grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text(grade.name) },
                                onClick = {
                                    viewModel.selectGrade(grade.id)
                                    isGradeFilterExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryIndigo)
                }
            } else if (uiState.filteredGroups.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        title = "لا توجد مجموعات حتى الآن",
                        description = "أضف أول مجموعة لبدء تنظيم وتتبع حضور الطلاب بانتظام.",
                        buttonText = "+ إضافة مجموعة جديدة",
                        onButtonClick = { viewModel.openAddDialog() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = Dimens.Spacing20,
                        end = Dimens.Spacing20,
                        top = Dimens.Spacing8,
                        bottom = 80.dp // Padding for FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
                ) {
                    items(uiState.filteredGroups, key = { it.id }) { group ->
                        val studentCount = uiState.groupStudentCounts[group.id] ?: 0
                        val gradeName = uiState.grades.find { it.id == group.gradeId }?.name ?: ""
                        val days = uiState.groupDays[group.id] ?: emptyList()
                        GroupCard(
                            group = group,
                            days = days,
                            studentCount = studentCount,
                            gradeName = gradeName,
                            onEdit = { viewModel.openEditDialog(group) },
                            onDelete = { viewModel.requestDeleteGroup(group) },
                            onToggleActive = { viewModel.toggleGroupActive(group) },
                            onStartAttendance = { onStartAttendance(group.id, group.name) }
                        )
                    }
                }
            }
        }

        // Add/Edit Dialog
        if (uiState.showAddEditDialog) {
            AddEditGroupDialog(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        // Delete Confirmation Dialog
        if (uiState.groupToDelete != null) {
            val group = uiState.groupToDelete!!
            AlertDialog(
                onDismissRequest = { if (!uiState.isDeleting) viewModel.dismissDeleteGroup() },
                icon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed)
                },
                title = {
                    Text(
                        text = "حذف المجموعة",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "هل أنت متأكد من حذف مجموعة \"${group.name}\"؟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ملاحظة: إذا كانت المجموعة مرتبطة بطلاب مسجلين أو سجلات حضور سابقة، فسيمنع النظام حذفها للحفاظ على سلامة البيانات ويمكنك إلغاء تفعيلها بدلاً من حذفها.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDeleteGroup() },
                        enabled = !uiState.isDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isDeleting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissDeleteGroup() },
                        enabled = !uiState.isDeleting
                    ) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupCard(
    group: Group,
    days: List<String>,
    studentCount: Int,
    gradeName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onStartAttendance: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("group_card_${group.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.Spacing16)) {
            // Header Row: Group Name + Status Badge + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (group.active) EmeraldGreenLight else DangerRedLight,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = if (group.active) "نشطة" else "غير نشطة",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (group.active) EmeraldGreen else DangerRed,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_group_btn_${group.id}")) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل", tint = PrimaryIndigo)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_group_btn_${group.id}")) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = DangerRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Grade Name
            Text(
                text = gradeName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Group Days (FlowRow to wrap smoothly or Row with scroll)
            if (days.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    days.forEach { day ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryIndigo.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = day,
                                color = PrimaryIndigo,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Details & Attendance Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${group.startTime} - ${group.endTime}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    if (!group.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = group.location, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.People, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "$studentCount طالب مسجل", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (group.capacity != null) {
                            Text(
                                text = " (السعة: ${group.capacity})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onStartAttendance,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("attendance_group_btn_${group.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "حضور المجموعة",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تسجيل الحضور",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGroupDialog(
    uiState: GroupsUiState,
    viewModel: GroupsViewModel
) {
    AlertDialog(
        onDismissRequest = { if (!uiState.isSaving) viewModel.closeDialog() },
        title = {
            Text(
                text = if (uiState.editingGroup == null) "إضافة مجموعة جديدة" else "تعديل بيانات المجموعة",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
            ) {
                if (uiState.formError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DangerRedLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.formError,
                                    color = DangerRed,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Group Name
                item {
                    OutlinedTextField(
                        value = uiState.formName,
                        onValueChange = viewModel::onFormNameChange,
                        label = { Text("اسم المجموعة (مثال: السبت 1-2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("group_name_field")
                    )
                }

                // Grade Select
                item {
                    var isExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.grades.find { it.id == uiState.formGradeId }?.name ?: "اختر الصف الدراسي",
                            onValueChange = {},
                            label = { Text("الصف الدراسي") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { isExpanded = true }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { isExpanded = true }.testTag("group_grade_select")
                        )
                        DropdownMenu(
                            expanded = isExpanded,
                            onDismissRequest = { isExpanded = false }
                        ) {
                            uiState.grades.forEach { grade ->
                                DropdownMenuItem(
                                    text = { Text(grade.name) },
                                    onClick = {
                                        viewModel.onFormGradeChange(grade.id)
                                        isExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Days selection
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "أيام الحضور الأسبوعية",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val weekdays = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            weekdays.forEach { day ->
                                val isSelected = uiState.formDays.contains(day)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.onFormDayToggle(day) }
                                        .testTag("day_toggle_$day")
                                ) {
                                    Text(
                                        text = day,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Start/End Time
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                    ) {
                        OutlinedTextField(
                            value = uiState.formStartTime,
                            onValueChange = viewModel::onFormStartTimeChange,
                            label = { Text("وقت البدء (مثال: 13:00)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("start_time_field")
                        )
                        OutlinedTextField(
                            value = uiState.formEndTime,
                            onValueChange = viewModel::onFormEndTimeChange,
                            label = { Text("وقت الانتهاء (مثال: 14:00)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("end_time_field")
                        )
                    }
                }

                // Capacity
                item {
                    OutlinedTextField(
                        value = uiState.formCapacity,
                        onValueChange = viewModel::onFormCapacityChange,
                        label = { Text("السعة القصوى للطلاب (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("capacity_field")
                    )
                }

                // Location
                item {
                    OutlinedTextField(
                        value = uiState.formLocation,
                        onValueChange = viewModel::onFormLocationChange,
                        label = { Text("المكان / القاعة (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("location_field")
                    )
                }

                // Active toggle switch
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("حالة المجموعة", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = if (uiState.formActive) "المجموعة مفعلة وتظهر في الحضور" else "المجموعة معطلة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.formActive,
                                onCheckedChange = viewModel::onFormActiveChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = EmeraldGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = DangerRed
                                ),
                                modifier = Modifier.testTag("form_active_switch")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveGroup() },
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                modifier = Modifier.testTag("save_group_submit")
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text(if (uiState.editingGroup == null) "إنشاء المجموعة" else "حفظ التعديلات", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.closeDialog() },
                enabled = !uiState.isSaving
            ) {
                Text("إلغاء")
            }
        }
    )
}
