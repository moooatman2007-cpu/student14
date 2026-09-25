package com.example.ui.add_edit_student

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.Student
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Dimens
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStudentScreen(
    gradeIdArg: String? = null,
    studentIdArg: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToStudentDetail: (String) -> Unit,
    viewModel: AddEditStudentViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isGradeMenuExpanded by remember { mutableStateOf(false) }

    val selectedGradeName = remember(uiState.selectedGradeId, uiState.grades) {
        uiState.grades.find { it.id == uiState.selectedGradeId }?.name ?: "اختر الصف الدراسي"
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AddEditStudentEvent.StudentUpdated -> {
                    Toast.makeText(context, "تم تعديل بيانات الطالب بنجاح", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is AddEditStudentEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.createdStudent != null && !uiState.isEditMode) {
                                "تمت الإضافة بنجاح"
                            } else if (uiState.isEditMode) {
                                "تعديل بيانات الطالب"
                            } else {
                                "إضافة طالب"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("add_edit_close_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("add_edit_student_screen")
    ) { innerPadding ->
        if (uiState.createdStudent != null && !uiState.isEditMode) {
            val student = uiState.createdStudent!!
            val gradeName = uiState.grades.find { it.id == student.gradeId }?.name ?: selectedGradeName
            AddStudentSuccessContent(
                student = student,
                gradeName = gradeName,
                onViewStudent = onNavigateToStudentDetail,
                onAddAnother = viewModel::resetForAnotherStudent,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = Dimens.Spacing20,
                    end = Dimens.Spacing20,
                    bottom = Dimens.Spacing32,
                    top = Dimens.Spacing8
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
            ) {
                // Student Code Notice Banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryIndigoLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Spacing16),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.isEditMode) "كود الطالب: ${uiState.existingStudentCode}" else "كود الطالب",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryIndigo
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (uiState.isEditMode)
                                        "كود الطالب ثابت وفريد ولا يمكن تعديله"
                                    else
                                        "سيتم إنشاء كود الطالب تلقائيًا بعد الحفظ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(Dimens.Spacing12))

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Student Full Name Input
                item {
                    Column {
                        Text(
                            text = "اسم الطالب",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing8))
                        OutlinedTextField(
                            value = uiState.fullName,
                            onValueChange = viewModel::onFullNameChange,
                            placeholder = {
                                Text(
                                    "اكتب اسم الطالب بالكامل",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            isError = uiState.nameError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_student_name")
                        )
                        if (uiState.nameError != null) {
                            Text(
                                text = uiState.nameError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed,
                                modifier = Modifier.padding(start = Dimens.Spacing8, top = Dimens.Spacing4)
                            )
                        }
                    }
                }

                // Grade Selection Dropdown
                item {
                    Column {
                        Text(
                            text = "الصف الدراسي",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing8))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (uiState.gradeError != null) DangerRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { isGradeMenuExpanded = true }
                                    .testTag("select_grade_dropdown"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing16),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = selectedGradeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (uiState.selectedGradeId.isNotBlank())
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )

                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "اختر الصف",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isGradeMenuExpanded,
                                onDismissRequest = { isGradeMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                uiState.grades.forEach { grade ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = grade.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            viewModel.onGradeSelected(grade.id)
                                            isGradeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (uiState.gradeError != null) {
                            Text(
                                text = uiState.gradeError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed,
                                modifier = Modifier.padding(start = Dimens.Spacing8, top = Dimens.Spacing4)
                            )
                        }
                    }
                }

                // Parent Phone Input
                item {
                    Column {
                        Text(
                            text = "رقم ولي الأمر",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing8))
                        OutlinedTextField(
                            value = uiState.parentPhone,
                            onValueChange = viewModel::onParentPhoneChange,
                            placeholder = {
                                Text(
                                    "01XXXXXXXXX",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = uiState.phoneError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_parent_phone")
                        )
                        if (uiState.phoneError != null) {
                            Text(
                                text = uiState.phoneError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed,
                                modifier = Modifier.padding(start = Dimens.Spacing8, top = Dimens.Spacing4)
                            )
                        }
                    }
                }

                // WhatsApp Switch Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing14),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "الرقم عليه واتساب",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "لإرسال الإشعارات لاحقًا عبر واتساب",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = uiState.hasWhatsApp,
                                onCheckedChange = viewModel::onWhatsAppToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = EmeraldGreen
                                ),
                                modifier = Modifier.testTag("switch_whatsapp")
                            )
                        }
                    }
                }

                // Alternative Phone Input
                item {
                    Column {
                        Text(
                            text = "رقم اتصال إضافي (اختياري)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing8))
                        OutlinedTextField(
                            value = uiState.alternativePhone,
                            onValueChange = viewModel::onAlternativePhoneChange,
                            placeholder = {
                                Text(
                                    "01XXXXXXXXX",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_alternative_phone")
                        )
                    }
                }

                // Submit Button
                item {
                    Spacer(modifier = Modifier.height(Dimens.Spacing12))
                    Button(
                        onClick = viewModel::saveStudent,
                        enabled = !uiState.isSaving,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_student_button")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = if (uiState.isEditMode) "حفظ التعديلات ✓" else "حفظ الطالب ✓",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddStudentSuccessContent(
    student: Student,
    gradeName: String,
    onViewStudent: (String) -> Unit,
    onAddAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_student_success_screen"),
        contentPadding = PaddingValues(
            start = Dimens.Spacing20,
            end = Dimens.Spacing20,
            bottom = Dimens.Spacing32,
            top = Dimens.Spacing8
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        // Success Badge & Titles
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = Dimens.Spacing12)
            ) {
                // Success glowing circular badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreenLight)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "تم بنجاح",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                Text(
                    text = "تمت إضافة الطالب بنجاح",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing6))

                Text(
                    text = "تم تسجيل بيانات الطالب وتوليد الكود التعريفي تلقائيًا",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Highlighted Student Code Showcase Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryIndigoLight),
                border = BorderStroke(1.5.dp, PrimaryIndigo.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.Spacing20, horizontal = Dimens.Spacing16),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        Text(
                            text = "كود الطالب المنشأ",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryIndigo
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    // Prominent Code Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.padding(horizontal = Dimens.Spacing8)
                    ) {
                        Text(
                            text = student.studentCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            ),
                            color = PrimaryIndigo,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = Dimens.Spacing12)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing10))

                    Text(
                        text = "كود النظام الثابت للاستعلام والبحث الفوري",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Student Overview Summary Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.Spacing16),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                ) {
                    // Student row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StudentAvatar(
                            fullName = student.fullName,
                            size = 48.dp,
                            fontSize = 18
                        )
                        Spacer(modifier = Modifier.width(Dimens.Spacing12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = gradeName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Contact row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.Spacing6))
                            Text(
                                text = student.parentPhone,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (student.hasWhatsApp) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldGreenLight
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "واتساب",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF047857)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        item {
            Spacer(modifier = Modifier.height(Dimens.Spacing6))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
            ) {
                // View Student Profile Button
                Button(
                    onClick = { onViewStudent(student.studentId) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("view_created_student_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.Spacing8))
                    Text(
                        text = "عرض الطالب",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Add Another Student Button
                OutlinedButton(
                    onClick = onAddAnother,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add_another_student_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.Spacing8))
                    Text(
                        text = "إضافة طالب آخر",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

