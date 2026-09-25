package com.example.ui.attendance

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.core.model.Student
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.Dimens
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun FastAttendanceScreen(
    gradeIdArg: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: FastAttendanceViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(gradeIdArg) {
        if (!gradeIdArg.isNullOrBlank()) {
            viewModel.setInitialGradeId(gradeIdArg)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    val gradeMap = remember(uiState.grades) {
        uiState.grades.associate { it.id to it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "تسجيل الحضور السريع",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "تاريخ اليوم: ${uiState.currentDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("fast_attendance_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing12)
                ) {
                    Button(
                        onClick = viewModel::finishAttendance,
                        enabled = !uiState.isSaving && uiState.allStudentsInScope.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryIndigo,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("fast_attendance_finish_button")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إنهاء تسجيل الحضور",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.testTag("fast_attendance_screen")
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Stats Preview Card
            val totalInScope = uiState.allStudentsInScope.size
            val presentCount = uiState.presentStudentIds.size
            val unregisteredCount = (totalInScope - presentCount).coerceAtLeast(0)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavyCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing12)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ملخص التسجيل المباشر",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "إجمالي: $totalInScope",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        // Attendance Counters
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Present Badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldGreenLight,
                                contentColor = EmeraldGreen
                            ) {
                                Text(
                                    text = "🟢 حاضر: $presentCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Unregistered Badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = "⚪ متبقي: $unregisteredCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing8))

                    // Payment Counters Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "متابعة دفع الشهر الحالي:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Paid Badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldGreenLight,
                                contentColor = EmeraldGreen
                            ) {
                                Text(
                                    text = "🟢 دفع: ${uiState.paidCount}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Unpaid Badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DangerRedLight,
                                contentColor = DangerRed
                            ) {
                                Text(
                                    text = "🔴 لم يدفع: ${uiState.unpaidCount}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Grade Filter Chips
            if (uiState.grades.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.Spacing4)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedGradeId == null,
                            onClick = { viewModel.onGradeSelected(null) },
                            label = { Text("كل الطلاب") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryIndigo,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    items(uiState.grades, key = { it.id }) { grade ->
                        FilterChip(
                            selected = uiState.selectedGradeId == grade.id,
                            onClick = { viewModel.onGradeSelected(grade.id) },
                            label = { Text(grade.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryIndigo,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Action Mode Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing4),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleScanner(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!uiState.isScannerActive) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!uiState.isScannerActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("تحديد الأسماء", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.toggleScanner(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isScannerActive) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isScannerActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fast_attendance_barcode_scan_toggle")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📷 مسح بالباركود", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.isScannerActive) {
                // Barcode Camera Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8)
                ) {
                    // Live Counters Row
                    val totalStudents = uiState.allStudentsInScope.size
                    val presentStudents = uiState.presentStudentIds.size
                    val remainingStudents = maxOf(0, totalStudents - presentStudents)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PrimaryIndigoLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("إجمالي", style = MaterialTheme.typography.labelSmall, color = PrimaryIndigo, fontWeight = FontWeight.Bold)
                                Text("$totalStudents", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryIndigo)
                            }
                        }

                        // Present
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldGreenLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("حاضر 🟢", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                Text("$presentStudents", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = EmeraldGreen)
                            }
                        }

                        // Unregistered / Remaining
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("لم يسجل ⚪", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text("$remainingStudents", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    BarcodeCameraScannerView(
                        onBarcodeScanned = viewModel::processScannedBarcode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Feedback Banner
                    uiState.scanFeedback?.let { feedback ->
                        val colorScheme = when (feedback.type) {
                            ScanResultType.SUCCESS_PRESENT -> EmeraldGreen to EmeraldGreenLight
                            ScanResultType.ALREADY_PRESENT -> Color(0xFFF57F17) to Color(0xFFFFF8E1)
                            ScanResultType.NOT_FOUND -> DangerRed to DangerRedLight
                            ScanResultType.WRONG_GROUP -> Color(0xFFE65100) to Color(0xFFFFF3E0)
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.second),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("scan_feedback_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.first.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (feedback.type) {
                                            ScanResultType.SUCCESS_PRESENT -> Icons.Default.CheckCircle
                                            ScanResultType.ALREADY_PRESENT -> Icons.Default.CheckCircle
                                            ScanResultType.NOT_FOUND -> Icons.Default.Clear
                                            ScanResultType.WRONG_GROUP -> Icons.Default.Clear
                                        },
                                        contentDescription = null,
                                        tint = colorScheme.first
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val feedbackStudentPaid = feedback.student?.let { uiState.paymentsMap[it.studentId]?.isPaid } ?: false
                                    val paymentStatusText = if (feedbackStudentPaid) "🟢 تم الدفع" else "🔴 لم يدفع"

                                    Text(
                                        text = when (feedback.type) {
                                            ScanResultType.SUCCESS_PRESENT -> "تم تسجيل الحضور ✓"
                                            ScanResultType.ALREADY_PRESENT -> "مسجل مسبقاً ✓"
                                            ScanResultType.NOT_FOUND -> "كود غير معروف ❌"
                                            ScanResultType.WRONG_GROUP -> "خارج هذه المجموعة ⚠️"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colorScheme.first
                                    )
                                    
                                    if (feedback.student != null) {
                                        Text(
                                            text = "${feedback.student.fullName} | $paymentStatusText",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else if (feedback.type == ScanResultType.NOT_FOUND) {
                                        Text(
                                            text = "الكود: ${feedback.rawCode}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = viewModel::clearScanFeedback) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "إغلاق", tint = colorScheme.first)
                                }
                            }
                        }
                    }

                    // Recent Scanned List
                    if (uiState.recentScannedStudents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "آخر المسجلين:",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.recentScannedStudents.forEach { student ->
                                val isStudentPaid = uiState.paymentsMap[student.studentId]?.isPaid ?: false
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "✓ ${student.fullName}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = student.studentCode,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PrimaryIndigo
                                            )
                                        }

                                        // Payment status chip in barcode mode
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isStudentPaid) EmeraldGreenLight else DangerRedLight,
                                            contentColor = if (isStudentPaid) EmeraldGreen else DangerRed,
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 1.dp,
                                                color = if (isStudentPaid) EmeraldGreen.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { viewModel.togglePaymentStatus(student.studentId) }
                                                .testTag("recent_scanned_payment_chip_${student.studentId}")
                                        ) {
                                            Text(
                                                text = if (isStudentPaid) "🟢 دفع" else "🔴 لم يدفع",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Finish Attendance Session Button
                    Button(
                        onClick = viewModel::finishAttendance,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fast_attendance_finish_barcode_button")
                    ) {
                        Text(
                            text = if (uiState.isSaving) "جاري إحساب وتسجيل الحضور..." else "إنهاء تسجيل الحضور",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Search Bar
                OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("ابحث باسم الطالب للحضور السريع...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "مسح"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing4)
                    .testTag("fast_attendance_search_input")
            )

            // Student List
            if (uiState.filteredStudents.isEmpty()) {
                EmptyStateView(
                    title = if (uiState.searchQuery.isNotEmpty()) "لا يوجد طالب يطابق البحث" else "لا يوجد طلاب في المجموعة المحددة",
                    description = "اكتب اسم الطالب لاختياره للحضور فورًا",
                    buttonText = null
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimens.Spacing16,
                        end = Dimens.Spacing16,
                        top = Dimens.Spacing8,
                        bottom = Dimens.Spacing16
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.filteredStudents,
                        key = { it.studentId }
                    ) { student ->
                        val isPresent = uiState.presentStudentIds.contains(student.studentId)
                        val isPaid = uiState.paymentsMap[student.studentId]?.isPaid ?: false
                        val gradeName = gradeMap[student.gradeId] ?: ""

                        FastAttendanceStudentRow(
                            student = student,
                            gradeName = gradeName,
                            isPresent = isPresent,
                            isPaid = isPaid,
                            onToggle = { viewModel.toggleStudentPresent(student.studentId) },
                            onTogglePayment = { viewModel.togglePaymentStatus(student.studentId) }
                        )
                    }
                }
            }
        }
    }
    }

    // Save Summary Dialog
    uiState.saveSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissSummary()
                onNavigateBack()
            },
            title = {
                Text(
                    text = "تم تسجيل الحضور بنجاح 🎉",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                    modifier = Modifier.padding(top = Dimens.Spacing8)
                ) {
                    Text(
                        text = "التاريخ: ${summary.date}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "عدد الحاضرين:", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${summary.presentCount} طالب",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "عدد الغائبين:", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${summary.absentCount} طالب",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DangerRed
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "إجمالي المعالجين:", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${summary.totalCount} طالب",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissSummary()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تم", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun FastAttendanceStudentRow(
    student: Student,
    gradeName: String,
    isPresent: Boolean,
    isPaid: Boolean,
    onToggle: () -> Unit,
    onTogglePayment: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPresent) EmeraldGreenLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("fast_attendance_student_row_${student.studentId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Selection Circle Checkmark Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isPresent) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (isPresent) EmeraldGreen else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                ) {
                    if (isPresent) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "حاضر",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Dimens.Spacing12))

                Column {
                    Text(
                        text = student.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (student.studentCode.isNotBlank()) {
                            Text(
                                text = "كود: ${student.studentCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (gradeName.isNotBlank()) {
                            if (student.studentCode.isNotBlank()) {
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = gradeName,
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryIndigo
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(Dimens.Spacing8))

            // Action & Status Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing6)
            ) {
                // Payment Status Chip (Clickable)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPaid) EmeraldGreenLight else DangerRedLight,
                    contentColor = if (isPaid) EmeraldGreen else DangerRed,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isPaid) EmeraldGreen.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTogglePayment() }
                        .testTag("fast_attendance_payment_chip_${student.studentId}")
                ) {
                    Text(
                        text = if (isPaid) "🟢 دفع" else "🔴 لم يدفع",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Attendance Status Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPresent) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isPresent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = if (isPresent) "حاضر ✓" else "غياب متوقع",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
