package com.example.ui.student_detail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.AttendanceSummary
import com.example.core.model.Student
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceTab(
    student: Student,
    monthNameAr: String,
    attendances: List<Attendance>,
    summary: AttendanceSummary,
    onNavigateMonth: (Int) -> Unit,
    onAddAttendance: () -> Unit,
    onEditAttendance: (Attendance) -> Unit,
    onDeleteAttendance: (Attendance) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        // Month Selector Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("attendance_month_header"),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing12, vertical = Dimens.Spacing8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onNavigateMonth(1) },
                    modifier = Modifier.testTag("attendance_next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, // In RTL, back arrow points right (next)
                        contentDescription = "الشهر القادم",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthNameAr,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = student.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onNavigateMonth(-1) },
                    modifier = Modifier.testTag("attendance_prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward, // In RTL, forward arrow points left (previous)
                        contentDescription = "الشهر السابق",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Summary Card with 4 metrics + Rate
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("attendance_summary_card"),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing16)
            ) {
                Text(
                    text = "ملخص الحضور الشهري",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing12))

                // Rate progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "نسبة الحضور:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format(Locale.ENGLISH, "%.1f", summary.attendanceRate)}%",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            summary.attendanceRate >= 85f -> EmeraldGreen
                            summary.attendanceRate >= 70f -> WarmAmber
                            else -> DangerRed
                        },
                        modifier = Modifier.testTag("attendance_rate_text")
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing6))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(Dimens.Radius8))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (summary.attendanceRate / 100f).coerceIn(0.01f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(Dimens.Radius8))
                            .background(
                                when {
                                    summary.attendanceRate >= 85f -> EmeraldGreen
                                    summary.attendanceRate >= 70f -> WarmAmber
                                    else -> DangerRed
                                }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                // 4 Metric Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                ) {
                    AttendanceMetricMiniCard(
                        label = "حاضر",
                        count = summary.presentCount,
                        color = EmeraldGreen,
                        bgColor = EmeraldGreenLight,
                        modifier = Modifier.weight(1f)
                    )
                    AttendanceMetricMiniCard(
                        label = "غائب",
                        count = summary.absentCount,
                        color = DangerRed,
                        bgColor = DangerRedLight,
                        modifier = Modifier.weight(1f)
                    )
                    AttendanceMetricMiniCard(
                        label = "متأخر",
                        count = summary.lateCount,
                        color = WarmAmber,
                        bgColor = WarmAmberLight,
                        modifier = Modifier.weight(1f)
                    )
                    AttendanceMetricMiniCard(
                        label = "بعذر",
                        count = summary.excusedCount,
                        color = ElectricBlue,
                        bgColor = ElectricBlue.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section Title + Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "سجل الأيام (${attendances.size} يوم)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onAddAttendance,
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_attendance_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تسجيل حضور",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Attendance List / Empty State
        if (attendances.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendance_empty_state"),
                shape = RoundedCornerShape(Dimens.Radius20),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.Spacing32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigoLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    Text(
                        text = "لا توجد سجلات حضور لهذا الشهر",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing6))

                    Text(
                        text = "اضغط على الزر أدناه لبدء تسجيل حضور وغياب الطالب",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing20))

                    Button(
                        onClick = onAddAttendance,
                        shape = RoundedCornerShape(Dimens.Radius12),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        modifier = Modifier.testTag("empty_add_attendance_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تسجيل الحضور", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)) {
                attendances.forEach { item ->
                    AttendanceDayItemCard(
                        attendance = item,
                        onClick = { onEditAttendance(item) },
                        onDelete = { onDeleteAttendance(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun AttendanceMetricMiniCard(
    label: String,
    count: Int,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius12),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = Dimens.Spacing8, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = color
            )
        }
    }
}

@Composable
fun AttendanceDayItemCard(
    attendance: Attendance,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusBg, icon) = when (attendance.status) {
        AttendanceStatus.PRESENT -> Triple(EmeraldGreen, EmeraldGreenLight, Icons.Default.Check)
        AttendanceStatus.ABSENT -> Triple(DangerRed, DangerRedLight, Icons.Default.Close)
        AttendanceStatus.LATE -> Triple(WarmAmber, WarmAmberLight, Icons.Default.HourglassEmpty)
        AttendanceStatus.EXCUSED -> Triple(ElectricBlue, ElectricBlue.copy(alpha = 0.12f), Icons.Default.Info)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius16))
            .clickable(onClick = onClick)
            .testTag("attendance_item_${attendance.date}"),
        shape = RoundedCornerShape(Dimens.Radius16),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                // Status icon badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusBg)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = attendance.status.labelAr,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(Dimens.Spacing12))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = attendance.date,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusBg
                        ) {
                            Text(
                                text = attendance.status.labelAr,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (!attendance.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = attendance.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
