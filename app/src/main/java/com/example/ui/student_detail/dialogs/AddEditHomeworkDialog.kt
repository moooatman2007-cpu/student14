package com.example.ui.student_detail.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddEditHomeworkDialog(
    homework: Homework?,
    onDismiss: () -> Unit,
    onSave: (date: String, title: String, status: HomeworkStatus, note: String?) -> Unit
) {
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()) }

    var date by remember { mutableStateOf(homework?.date ?: todayStr) }
    var title by remember { mutableStateOf(homework?.title ?: "") }
    var selectedStatus by remember { mutableStateOf(homework?.status ?: HomeworkStatus.PENDING) }
    var note by remember { mutableStateOf(homework?.note ?: "") }
    var titleError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (homework == null) "إضافة واجب جديد" else "تعديل الواجب",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("التاريخ (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("homework_date_input")
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = null
                    },
                    label = { Text("عنوان الواجب") },
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("homework_title_input")
                )

                Text(
                    text = "حالة الواجب:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeworkStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.labelAr) },
                            modifier = Modifier.testTag("status_chip_${st.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظات (اختياري)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("homework_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = "يرجى إدخال عنوان الواجب"
                        return@Button
                    }
                    onSave(date.trim(), title.trim(), selectedStatus, note.trim().ifBlank { null })
                },
                modifier = Modifier.testTag("save_homework_button")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_homework_button")
            ) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
