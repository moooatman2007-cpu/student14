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
import androidx.compose.material.icons.filled.AutoStories
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
import com.example.core.model.Recitation
import com.example.ui.theme.Dimens
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryIndigo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddEditRecitationDialog(
    recitationToEdit: Recitation?,
    defaultDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, date: String, score: Double, maxScore: Double, note: String?) -> Unit
) {
    var title by remember { mutableStateOf(recitationToEdit?.title ?: "") }
    var content by remember { mutableStateOf(recitationToEdit?.content ?: "") }
    var date by remember { mutableStateOf(recitationToEdit?.date ?: defaultDate) }
    var scoreStr by remember { mutableStateOf(recitationToEdit?.score?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var maxScoreStr by remember { mutableStateOf(recitationToEdit?.maxScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "10") }
    var note by remember { mutableStateOf(recitationToEdit?.note ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = EmeraldGreen
                )
                Text(
                    text = if (recitationToEdit != null) "تعديل التسميع" else "إضافة تسميع جديد",
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
                        modifier = Modifier.testTag("recitation_error_text")
                    )
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("اسم التسميع * (مثال: سورة البقرة)") },
                    singleLine = true,
                    isError = errorMessage != null && title.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recitation_title_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )

                // Content Input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("المحتوى / الآيات (مثال: من الآية 1 إلى 20)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recitation_content_input"),
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
                        .testTag("recitation_date_input"),
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
                            .testTag("recitation_score_input"),
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
                            .testTag("recitation_max_score_input"),
                        shape = RoundedCornerShape(Dimens.Radius12)
                    )
                }

                // Note Input
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("الملاحظات (اختياري - مثل ممتاز، يراجع التجويد)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recitation_note_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "يرجى كتابة اسم التسميع"
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

                    onSave(title.trim(), content.trim(), date.trim(), score, maxScore, note.ifBlank { null })
                },
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("save_recitation_button")
            ) {
                Text("حفظ التسميع", fontWeight = FontWeight.Bold)
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
