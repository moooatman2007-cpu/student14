package com.example.ui.start_lesson

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.Grade
import com.example.core.model.HomeworkStatus
import com.example.core.model.Student
import com.example.ui.attendance.BarcodeCameraScannerView
import com.example.ui.attendance.ScanResultType
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.VioletPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartLessonScreen(
    onNavigateBack: () -> Unit,
    viewModel: StartLessonViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            Toast.makeText(context, "تم إنهاء الحصة بنجاح 🎉", Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    val stepTitle = when (uiState.currentStep) {
        StartLessonStep.SELECT_GROUP -> "1. اختيار المجموعة"
        StartLessonStep.ATTENDANCE -> "2. تسجيل الحضور"
        StartLessonStep.RECITATION -> "3. التسميع"
        StartLessonStep.HOMEWORK -> "4. الواجب"
        StartLessonStep.EXAM -> "5. الامتحان"
        StartLessonStep.REVIEW -> "6. مراجعة الحصة"
    }

    val stepProgress = when (uiState.currentStep) {
        StartLessonStep.SELECT_GROUP -> 0.15f
        StartLessonStep.ATTENDANCE -> 0.35f
        StartLessonStep.RECITATION -> 0.55f
        StartLessonStep.HOMEWORK -> 0.75f
        StartLessonStep.EXAM -> 0.90f
        StartLessonStep.REVIEW -> 1.0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ابدأ الحصة: $stepTitle",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.selectedGradeName.isNotBlank() && uiState.currentStep != StartLessonStep.SELECT_GROUP) {
                            Text(
                                text = "المجموعة: ${uiState.selectedGradeName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryIndigo
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (uiState.currentStep) {
                                StartLessonStep.SELECT_GROUP -> onNavigateBack()
                                StartLessonStep.ATTENDANCE -> viewModel.goToStep(StartLessonStep.SELECT_GROUP)
                                StartLessonStep.RECITATION -> viewModel.goToStep(StartLessonStep.ATTENDANCE)
                                StartLessonStep.HOMEWORK -> viewModel.goToStep(StartLessonStep.RECITATION)
                                StartLessonStep.EXAM -> viewModel.goToStep(StartLessonStep.HOMEWORK)
                                StartLessonStep.REVIEW -> viewModel.goToStep(StartLessonStep.EXAM)
                            }
                        },
                        modifier = Modifier.testTag("start_lesson_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { stepProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = PrimaryIndigo,
                )
            }
        },
        modifier = modifier.testTag("start_lesson_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentStep) {
                StartLessonStep.SELECT_GROUP -> StepSelectGroupContent(uiState, viewModel)
                StartLessonStep.ATTENDANCE -> StepAttendanceContent(uiState, viewModel)
                StartLessonStep.RECITATION -> StepRecitationContent(uiState, viewModel)
                StartLessonStep.HOMEWORK -> StepHomeworkContent(uiState, viewModel)
                StartLessonStep.EXAM -> StepExamContent(uiState, viewModel)
                StartLessonStep.REVIEW -> StepReviewContent(uiState, viewModel)
            }
        }
    }

    // Unrecorded Attendance Warning Dialog
    if (uiState.showUnrecordedWarning) {
        val unrecorded = uiState.studentsInGroup.size - uiState.presentStudentIds.size
        AlertDialog(
            onDismissRequest = viewModel::dismissUnrecordedWarning,
            title = { Text("تنبيه الحضور", fontWeight = FontWeight.Bold) },
            text = { Text("يوجد $unrecorded طلاب لم يتم تسجيل حضورهم.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUnrecordedWarning()
                        viewModel.finishLesson()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("إنهاء الحصة على أي حال", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    viewModel.dismissUnrecordedWarning()
                    viewModel.goToStep(StartLessonStep.ATTENDANCE)
                }) {
                    Text("مراجعة الحضور")
                }
            }
        )
    }
}

@Composable
fun StepSelectGroupContent(uiState: StartLessonUiState, viewModel: StartLessonViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Spacing20),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        Text(
            text = "اختر المجموعة أو الصف لبدء الحصة:",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        if (uiState.grades.isEmpty()) {
            EmptyStateView(
                title = "لا توجد مجموعات مسجلة",
                description = "يرجى إضافة صفوف أو مجموعات أولاً",
                buttonText = null
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.grades, key = { it.id }) { grade ->
                    val isSelected = uiState.selectedGradeId == grade.id
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimaryIndigoLight else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, PrimaryIndigo) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectGrade(grade.id) }
                            .testTag("start_lesson_grade_card_${grade.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Spacing16),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column {
                                    Text(
                                        text = grade.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "عدد الطلاب: ${grade.studentCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "مختار", tint = PrimaryIndigo)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.goToStep(StartLessonStep.ATTENDANCE) },
                enabled = uiState.selectedGradeId != null,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_lesson_proceed_attendance_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("ابدأ الحضور", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun StepAttendanceContent(uiState: StartLessonUiState, viewModel: StartLessonViewModel) {
    val total = uiState.studentsInGroup.size
    val present = uiState.presentStudentIds.size
    val absent = (total - present).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Spacing16)
    ) {
        // Summary bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("تسجيل حضور المجموعة", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Text("${uiState.selectedGradeName} ($total طالب)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = EmeraldGreenLight, contentColor = EmeraldGreen) {
                        Text("حاضر: $present", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = DangerRedLight, contentColor = DangerRed) {
                        Text("غائب: $absent", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scanner / List Toggle
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.toggleScanner(false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!uiState.isScannerActive) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (!uiState.isScannerActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("قائمة الطلاب", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.toggleScanner(true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isScannerActive) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (uiState.isScannerActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("📷 مسح بالباركود", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isScannerActive) {
            Column(modifier = Modifier.weight(1f)) {
                BarcodeCameraScannerView(
                    onBarcodeScanned = viewModel::processScannedBarcode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                uiState.scanFeedback?.let { feedback ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (feedback.type) {
                                ScanResultType.SUCCESS_PRESENT -> EmeraldGreenLight
                                ScanResultType.ALREADY_PRESENT -> Color(0xFFFFF8E1)
                                ScanResultType.NOT_FOUND -> DangerRedLight
                                ScanResultType.WRONG_GROUP -> Color(0xFFFFF3E0)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (feedback.type) {
                                    ScanResultType.SUCCESS_PRESENT -> "${feedback.student?.fullName} ✓ حاضر"
                                    ScanResultType.ALREADY_PRESENT -> "⚠️ تم تسجيل حضوره مسبقاً"
                                    ScanResultType.NOT_FOUND -> "❌ طالب غير مسجل (${feedback.rawCode})"
                                    ScanResultType.WRONG_GROUP -> "⚠️ الطالب ينتمي لمجموعة أخرى"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = viewModel::clearScanFeedback) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.studentsInGroup, key = { it.studentId }) { student ->
                    val isPresent = uiState.presentStudentIds.contains(student.studentId)
                    val isPaid = uiState.paymentsMap[student.studentId]?.isPaid ?: false

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPresent) EmeraldGreenLight.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleStudentPresent(student.studentId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isPresent) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (isPresent) Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                                }
                                Column {
                                    Text(text = student.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(text = "كود: ${student.studentCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPaid) EmeraldGreenLight else DangerRedLight,
                                contentColor = if (isPaid) EmeraldGreen else DangerRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.togglePaymentStatus(student.studentId) }
                            ) {
                                Text(
                                    text = if (isPaid) "🟢 دفع" else "🔴 لم يدفع",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.goToStep(StartLessonStep.RECITATION) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("التالي: التسميع", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun StepRecitationContent(uiState: StartLessonUiState, viewModel: StartLessonViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Spacing16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تسجيل التسميع (اختياري)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedButton(
                onClick = { viewModel.goToStep(StartLessonStep.HOMEWORK) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("تخطي التسميع")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.studentsInGroup, key = { it.studentId }) { student ->
                val rec = uiState.recitationsMap[student.studentId] ?: StudentRecitationInput()

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = student.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryIndigo)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = rec.title,
                                onValueChange = { t -> viewModel.updateRecitationInput(studentId = student.studentId) { it.copy(title = t) } },
                                label = { Text("عنوان التسميع") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = rec.scoreStr,
                                onValueChange = { s -> viewModel.updateRecitationInput(studentId = student.studentId) { it.copy(scoreStr = s) } },
                                label = { Text("الدرجة") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.goToStep(StartLessonStep.HOMEWORK) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("التالي: الواجب", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun StepHomeworkContent(uiState: StartLessonUiState, viewModel: StartLessonViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Spacing16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تسجيل الواجب (اختياري)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedButton(
                onClick = { viewModel.goToStep(StartLessonStep.EXAM) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("تخطي الواجب")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.studentsInGroup, key = { it.studentId }) { student ->
                val hw = uiState.homeworkMap[student.studentId] ?: StudentHomeworkInput()

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = student.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryIndigo)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = hw.title,
                                onValueChange = { t -> viewModel.updateHomeworkInput(student.studentId) { it.copy(title = t) } },
                                label = { Text("عنوان الواجب") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    val nextStatus = when (hw.status) {
                                        HomeworkStatus.COMPLETED -> HomeworkStatus.NOT_COMPLETED
                                        else -> HomeworkStatus.COMPLETED
                                    }
                                    viewModel.updateHomeworkInput(student.studentId) { it.copy(status = nextStatus) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hw.status == HomeworkStatus.COMPLETED) EmeraldGreen else DangerRed
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (hw.status == HomeworkStatus.COMPLETED) "مكتمل ✓" else "غير مكتمل ✗", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.goToStep(StartLessonStep.EXAM) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("التالي: الامتحان", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun StepExamContent(uiState: StartLessonUiState, viewModel: StartLessonViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Spacing16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تسجيل الامتحان (اختياري)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedButton(
                onClick = { viewModel.goToStep(StartLessonStep.REVIEW) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("تخطي الامتحان")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.studentsInGroup, key = { it.studentId }) { student ->
                val ex = uiState.examsMap[student.studentId] ?: StudentExamInput()

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = student.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryIndigo)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = ex.examName,
                                onValueChange = { t -> viewModel.updateExamInput(student.studentId) { it.copy(examName = t) } },
                                label = { Text("اسم الامتحان") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = ex.scoreStr,
                                onValueChange = { s -> viewModel.updateExamInput(student.studentId) { it.copy(scoreStr = s) } },
                                label = { Text("الدرجة") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.goToStep(StartLessonStep.REVIEW) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("التالي: مراجعة الحصة", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun StepReviewContent(uiState: StartLessonUiState, viewModel: StartLessonViewModel) {
    val total = uiState.studentsInGroup.size
    val present = uiState.presentStudentIds.size
    val absent = (total - present).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Spacing20),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        Text(
            text = "مراجعة الحصة قبل الإنهاء",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing20),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
            ) {
                Text(
                    text = "المجموعة: ${uiState.selectedGradeName}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryIndigo
                )
                Text(
                    text = "إجمالي الطلاب: $total",
                    style = MaterialTheme.typography.bodyLarge
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("✅ حاضر: $present", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    Text("❌ غائب: $absent", color = DangerRed, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("التسميع:")
                    Text("مسجل (${uiState.recitationsMap.size} طالب)")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الواجب:")
                    Text("مسجل (${uiState.homeworkMap.size} طالب)")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الامتحان:")
                    Text("مسجل (${uiState.examsMap.size} طالب)")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = viewModel::checkReviewBeforeFinish,
            enabled = !uiState.isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_lesson_finish_button")
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنهاء الحصة", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
