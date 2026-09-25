package com.example.ui.students

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GradeCard
import com.example.ui.components.SearchHeader
import com.example.ui.components.StudentCard
import com.example.ui.theme.ActionAttendanceBg
import com.example.ui.theme.ActionAttendanceIcon
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.VioletPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    gradeIdArg: String? = null,
    onNavigateToStudentDetail: (String) -> Unit,
    onNavigateToAddStudent: (String?) -> Unit,
    onNavigateToFastAttendance: (String?) -> Unit = {},
    onNavigateToBarcodes: () -> Unit = {},
    viewModel: StudentListViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(gradeIdArg) {
        viewModel.setInitialGradeId(gradeIdArg)
    }

    val selectedGrade = remember(uiState.selectedGradeId, uiState.grades) {
        uiState.grades.find { it.id == uiState.selectedGradeId }
    }

    val gradeMap = remember(uiState.gradeMap, uiState.grades) {
        if (uiState.gradeMap.isNotEmpty()) {
            uiState.gradeMap
        } else {
            uiState.grades.associate { it.id to it.name }
        }
    }

    val isSearching = uiState.searchQuery.isNotBlank()
    val isGradeSelected = selectedGrade != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isGradeSelected) {
                        // Grade Detail Header (Matching Screenshot 5)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "طلاب ${selectedGrade.name}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${uiState.students.size} طالب",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Back to Grade Selection Chevron (Matching Screenshot 5)
                            IconButton(
                                onClick = { viewModel.onGradeSelected(null) },
                                modifier = Modifier.testTag("back_to_grades_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "الرجوع للصفوف",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Main Students Header (Matching Screenshot 4)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                            ) {
                                // Fast Attendance Top Action Pill Button
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = ActionAttendanceBg,
                                    contentColor = ActionAttendanceIcon,
                                    modifier = Modifier
                                        .clickable { onNavigateToFastAttendance(null) }
                                        .testTag("student_list_fast_attendance_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "الحضور السريع",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                // Student Barcodes Button
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = PrimaryIndigo.copy(alpha = 0.12f),
                                    contentColor = PrimaryIndigo,
                                    modifier = Modifier
                                        .clickable { onNavigateToBarcodes() }
                                        .testTag("student_barcodes_top_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Barcodes الطلاب",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                // Circular Add Student Button
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryIndigo)
                                        .clickable { onNavigateToAddStudent(null) }
                                        .testTag("top_add_student_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "إضافة طالب",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Title on Right
                            Text(
                                text = "الطلاب",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("student_list_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Field (Matching Screenshots 4, 5)
            Box(
                modifier = Modifier.padding(
                    start = Dimens.Spacing20,
                    end = Dimens.Spacing20,
                    top = Dimens.Spacing8,
                    bottom = Dimens.Spacing12
                )
            ) {
                SearchHeader(
                    searchQuery = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    placeholder = "...ابحث باسم الطالب أو الكود"
                )
            }

            // If user is searching or a specific grade is selected: show students list
            if (isGradeSelected || isSearching) {
                if (uiState.students.isEmpty()) {
                    EmptyStateView(
                        title = if (isSearching) "لا توجد نتائج بحث" else "لا يوجد طلاب في هذا الصف",
                        description = if (isSearching)
                            "لم يتم العثور على طالب يطابق \"${uiState.searchQuery}\""
                        else
                            "ابدأ بإضافة أول طالب إلى ${selectedGrade?.name ?: "القائمة"}",
                        buttonText = "+ إضافة طالب",
                        onButtonClick = { onNavigateToAddStudent(selectedGrade?.id) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Dimens.Spacing20,
                            end = Dimens.Spacing20,
                            bottom = Dimens.Spacing32,
                            top = Dimens.Spacing4
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                    ) {
                        items(uiState.students, key = { it.studentId }) { student ->
                            val gradeName = gradeMap[student.gradeId] ?: "صف غير محدد"
                            StudentCard(
                                student = student,
                                gradeName = gradeName,
                                onClick = { onNavigateToStudentDetail(student.studentId) }
                            )
                        }
                    }
                }
            } else {
                // Otherwise show Grades Selector List (Matching Screenshot 4)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Dimens.Spacing20,
                        end = Dimens.Spacing20,
                        bottom = Dimens.Spacing32,
                        top = Dimens.Spacing4
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                ) {
                    item {
                        Column {
                            Text(
                                text = "الصفوف الدراسية",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "اختر الصف لعرض قائمة الطلاب",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.Spacing8))
                    }

                    items(uiState.grades, key = { it.id }) { grade ->
                        val accentColor = when ((grade.displayOrder - 1) % 4) {
                            0 -> PrimaryIndigo
                            1 -> ElectricBlue
                            2 -> EmeraldGreen
                            else -> VioletPurple
                        }
                        GradeCard(
                            grade = grade,
                            accentColor = accentColor,
                            onClick = { viewModel.onGradeSelected(grade.id) }
                        )
                    }
                }
            }
        }
    }
}
