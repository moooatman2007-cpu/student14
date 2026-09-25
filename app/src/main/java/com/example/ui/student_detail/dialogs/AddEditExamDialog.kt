package com.example.ui.student_detail.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.model.Exam
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddEditExamDialog(
    examToEdit: Exam?,
    defaultDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
    onDismiss: () -> Unit,
    onSave: (examName: String, subject: String?, date: String, score: Double, maxScore: Double, note: String?) -> Unit
) {
    var examName by remember { mutableStateOf(examToEdit?.examName ?: "") }
    var subject by remember { mutableStateOf(examToEdit?.subject ?: "") }
    var date by remember { mutableStateOf(examToEdit?.date ?: defaultDate) }
    var scoreStr by remember { mutableStateOf(examToEdit?.score?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var maxScoreStr by remember { mutableStateOf(examToEdit?.maxScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "100") }
    var note by remember { mutableStateOf(examToEdit?.note ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    tint = ElectricBlue
                )
                Text(
                    text = if (examToEdit != null) "تعديل الامتحان" else "إضافة امتحان جديد",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Dimens.Spacing8),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("exam_error_text")
                    )
                }

                // Exam Name Input
                OutlinedTextField(
                    value = examName,
                    onValueChange = {
                        examName = it
                        errorMessage = null
                    },
                    label = { Text("اسم الامتحان * (مثال: امتحان سبتمبر)") },
                    singleLine = true,
                    isError = errorMessage != null && examName.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_name_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )

                // Subject Input
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("المادة (اختياري - مثال: القرآن الكريم)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_subject_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )

                // Date Input
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("التاريخ (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_date_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )

                // Scores Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                ) {
                    OutlinedTextField(
                        value = scoreStr,
                        onValueChange = {
                            scoreStr = it
                            errorMessage = null
                        },
                        label = { Text("الدرجة *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exam_score_input"),
                        shape = RoundedCornerShape(Dimens.Radius12)
                    )

                    OutlinedTextField(
                        value = maxScoreStr,
                        onValueChange = {
                            maxScoreStr = it
                            errorMessage = null
                        },
                        label = { Text("الدرجة الكاملة *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exam_max_score_input"),
                        shape = RoundedCornerShape(Dimens.Radius12)
                    )
                }

                // Note Input
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("الملاحظات (اختياري)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_note_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (examName.isBlank()) {
                        errorMessage = "يرجى كتابة اسم الامتحان"
                        return@Button
                    }
                    val score = scoreStr.toDoubleOrNull()
                    if (score == null) {
                        errorMessage = "يرجى إدخال درجة صحيحة"
                        return@Button
                    }
                    if (score < 0.0) {
                        errorMessage = "الدرجة لا يمكن أن تكون سالبة"
                        return@Button
                    }
                    val maxScore = maxScoreStr.toDoubleOrNull()
                    if (maxScore == null || maxScore <= 0.0) {
                        errorMessage = "الدرجة الكاملة يجب أن تكون أكبر من صفر"
                        return@Button
                    }
                    if (score > maxScore) {
                        errorMessage = "الدرجة لا يمكن أن تتجاوز الدرجة الكاملة ($maxScore)"
                        return@Button
                    }

                    onSave(examName.trim(), subject.ifBlank { null }, date.trim(), score, maxScore, note.ifBlank { null })
                },
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("save_exam_button")
            ) {
                Text("حفظ الامتحان", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(Dimens.Radius12)
            ) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(Dimens.Radius24),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
