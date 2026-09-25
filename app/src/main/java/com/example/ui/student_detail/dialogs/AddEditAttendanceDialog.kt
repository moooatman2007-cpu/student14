package com.example.ui.student_detail.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.WarmAmber

@Composable
fun AddEditAttendanceDialog(
    initialAttendance: Attendance?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (date: String, status: AttendanceStatus, note: String?) -> Unit
) {
    var date by remember { mutableStateOf(initialAttendance?.date ?: defaultDate) }
    var selectedStatus by remember { mutableStateOf(initialAttendance?.status ?: AttendanceStatus.PRESENT) }
    var note by remember { mutableStateOf(initialAttendance?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = PrimaryIndigo
                )
                Text(
                    text = if (initialAttendance?.attendanceId?.isNotEmpty() == true) "تعديل حالة الحضور" else "تسجيل الحضور",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.Spacing8),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
            ) {
                // Date input
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("التاريخ (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_date_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )

                Text(
                    text = "اختر حالة الحضور:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 2x2 Status selector cards
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                    ) {
                        AttendanceStatusChip(
                            status = AttendanceStatus.PRESENT,
                            isSelected = selectedStatus == AttendanceStatus.PRESENT,
                            onClick = { selectedStatus = AttendanceStatus.PRESENT },
                            accentColor = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        AttendanceStatusChip(
                            status = AttendanceStatus.ABSENT,
                            isSelected = selectedStatus == AttendanceStatus.ABSENT,
                            onClick = { selectedStatus = AttendanceStatus.ABSENT },
                            accentColor = DangerRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                    ) {
                        AttendanceStatusChip(
                            status = AttendanceStatus.LATE,
                            isSelected = selectedStatus == AttendanceStatus.LATE,
                            onClick = { selectedStatus = AttendanceStatus.LATE },
                            accentColor = WarmAmber,
                            modifier = Modifier.weight(1f)
                        )
                        AttendanceStatusChip(
                            status = AttendanceStatus.EXCUSED,
                            isSelected = selectedStatus == AttendanceStatus.EXCUSED,
                            onClick = { selectedStatus = AttendanceStatus.EXCUSED },
                            accentColor = ElectricBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Note input
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري - مثل سبب التأخير أو العذر)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_note_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(date, selectedStatus, note.ifBlank { null }) },
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                modifier = Modifier.testTag("save_attendance_button")
            ) {
                Text("حفظ", fontWeight = FontWeight.Bold)
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

@Composable
fun AttendanceStatusChip(
    status: AttendanceStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius12),
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("status_chip_${status.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Spacing10, horizontal = Dimens.Spacing8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(
                text = status.labelAr,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
