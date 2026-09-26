package com.example.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PrimaryIndigo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeacherProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: TeacherProfileViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatarFromUri(context, uri)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "بيانات الحساب",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("profile_back_button")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("teacher_profile_screen")
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.Spacing20,
                    vertical = Dimens.Spacing16
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
            ) {
                // Section 1: Teacher Avatar & Basic Info Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Spacing20),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "بيانات المدرس",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing16))

                            // Avatar Circle
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(
                                        width = 2.dp,
                                        color = PrimaryIndigo.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            ) {
                                if (!uiState.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(uiState.avatarUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "الصورة الشخصية",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val initial = if (uiState.fullName.isNotBlank()) {
                                        uiState.fullName.trim().take(1)
                                    } else "م"
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                if (uiState.isUploadingAvatar) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = Color.White,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            // Avatar Actions
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !uiState.isUploadingAvatar,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("change_avatar_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Dimens.Spacing8))
                                    Text("تغيير الصورة")
                                }

                                if (!uiState.avatarUrl.isNullOrBlank()) {
                                    TextButton(
                                        onClick = { viewModel.deleteAvatar() },
                                        enabled = !uiState.isUploadingAvatar,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier.testTag("delete_avatar_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Dimens.Spacing4))
                                        Text("حذف")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimens.Spacing20))

                            // Name Field
                            OutlinedTextField(
                                value = uiState.fullName,
                                onValueChange = viewModel::onFullNameChange,
                                label = { Text("اسم المدرس *") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                isError = uiState.nameError != null,
                                supportingText = {
                                    if (uiState.nameError != null) {
                                        Text(text = uiState.nameError!!, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_teacher_name")
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            // Email Field (Read-only)
                            OutlinedTextField(
                                value = uiState.email,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("البريد الإلكتروني") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "قراءة فقط",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                supportingText = { Text("البريد الإلكتروني المرتبط بحساب تسجيل الدخول") },
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_teacher_email")
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            // Phone Field
                            OutlinedTextField(
                                value = uiState.phoneNumber,
                                onValueChange = viewModel::onPhoneNumberChange,
                                label = { Text("رقم الهاتف") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_teacher_phone")
                            )
                        }
                    }
                }

                // Section 2: Subject & Center Name Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Spacing20)
                        ) {
                            Text(
                                text = "المادة والمركز التعليمي",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing16))

                            // Subject Dropdown
                            var expandedSubjectMenu by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = if (uiState.subject.isBlank()) "اختر المادة..." else uiState.subject,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("المادة التي تدرسها") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { expandedSubjectMenu = !expandedSubjectMenu }) {
                                            Icon(
                                                imageVector = if (expandedSubjectMenu) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                contentDescription = "قائمة المواد"
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSubjectMenu = true }
                                        .testTag("subject_dropdown")
                                )

                                DropdownMenu(
                                    expanded = expandedSubjectMenu,
                                    onDismissRequest = { expandedSubjectMenu = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    PREDEFINED_SUBJECTS.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item) },
                                            onClick = {
                                                viewModel.onSubjectChange(item)
                                                expandedSubjectMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = uiState.isCustomSubject) {
                                Column {
                                    Spacer(modifier = Modifier.height(Dimens.Spacing12))
                                    OutlinedTextField(
                                        value = uiState.customSubject,
                                        onValueChange = viewModel::onCustomSubjectChange,
                                        label = { Text("اسم المادة المخصص") },
                                        placeholder = { Text("أدخل اسم المادة") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_custom_subject")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimens.Spacing16))

                            // Center Name Field
                            OutlinedTextField(
                                value = uiState.centerName,
                                onValueChange = viewModel::onCenterNameChange,
                                label = { Text("اسم المركز / السنتر (اختياري)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_center_name")
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing16))

                            // Educational Stage Field
                            var expandedStageMenu by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = if (uiState.educationalStage.isBlank()) "اختر المرحلة التعليمية..." else uiState.educationalStage,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("المرحلة التعليمية") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { expandedStageMenu = !expandedStageMenu }) {
                                            Icon(
                                                imageVector = if (expandedStageMenu) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                contentDescription = "قائمة المراحل"
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedStageMenu = true }
                                        .testTag("profile_educational_stage_dropdown")
                                )

                                DropdownMenu(
                                    expanded = expandedStageMenu,
                                    onDismissRequest = { expandedStageMenu = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    PREDEFINED_STAGES.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item) },
                                            onClick = {
                                                viewModel.onEducationalStageChange(item)
                                                expandedStageMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Taught Grades Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Spacing20)
                        ) {
                            Text(
                                text = "الصفوف التي أدرسها",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "الصفوف المسجلة حالياً في حسابك مع إمكانية الوصول لطلاب كل صف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing16))

                            if (uiState.grades.isEmpty()) {
                                Text(
                                    text = "لا توجد صفوف مسجلة حالياً",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                                ) {
                                    uiState.grades.forEach { grade ->
                                        FilterChip(
                                            selected = true,
                                            onClick = { },
                                            label = {
                                                Text(
                                                    text = "${grade.name} (${grade.studentCount} طالب)",
                                                    fontWeight = FontWeight.Medium
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.School,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryIndigo.copy(alpha = 0.12f),
                                                selectedLabelColor = PrimaryIndigo,
                                                selectedLeadingIconColor = PrimaryIndigo
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Save Button
                item {
                    Spacer(modifier = Modifier.height(Dimens.Spacing8))
                    Button(
                        onClick = { viewModel.saveProfile() },
                        enabled = !uiState.isSaving && uiState.fullName.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryIndigo,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_profile_button")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(Dimens.Spacing12))
                            Text(
                                text = "جاري الحفظ...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Text(
                                text = "حفظ التعديلات",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
