package com.example.ui.students

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.Student
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.Dimens
import com.example.ui.theme.PrimaryIndigo
import com.example.util.BarcodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentBarcodesScreen(
    onNavigateBack: () -> Unit,
    viewModel: StudentBarcodesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Barcodes الطلاب",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("student_barcodes_back_button")
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
        modifier = modifier.testTag("student_barcodes_screen")
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header actions: Download PDF & Print
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
            ) {
                Button(
                    onClick = { viewModel.downloadPdf(context) },
                    enabled = !uiState.isExporting && uiState.filteredStudents.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("student_barcodes_download_pdf_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جاري التصدير...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحميل Barcodes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.printBarcodes(context) },
                    enabled = !uiState.isExporting && uiState.filteredStudents.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("student_barcodes_print_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("طباعة Barcodes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Grade Filters
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
                            label = { Text("كل الصفوف") },
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

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("ابحث باسم الطالب أو الكود...") },
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
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
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
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryIndigo)
                }
            } else if (uiState.filteredStudents.isEmpty()) {
                EmptyStateView(
                    title = "لا يوجد طلاب لعرض الباركود",
                    description = "تأكد من وجود طلاب مسجلين في النظام أو غيّر خيارات البحث",
                    buttonText = null
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.filteredStudents,
                        key = { it.studentId }
                    ) { student ->
                        StudentBarcodeCard(student = student)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentBarcodeCard(student: Student) {
    val barcodeCode = student.studentCode.ifBlank { "ST-000" }
    val imageBitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = barcodeCode) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            BarcodeGenerator.generateCode128ImageBitmap(
                content = barcodeCode,
                width = 500,
                height = 180
            )
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Barcode Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentBitmap = imageBitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = "Barcode ${student.studentCode}",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "|||| ||| ||||",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Student Name
            Text(
                text = student.fullName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Student Code
            Text(
                text = barcodeCode,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
