package com.example.ui.student_detail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.*
import com.example.ui.components.StudentAvatar
import com.example.ui.student_detail.StudentDetailTab
import com.example.ui.theme.*
import com.example.util.PhoneUtil

@Composable
fun StudentProfileInfoTab(
    student: Student,
    grade: Grade?,
    formattedCreatedAt: String,
    monthNameAr: String,
    isPaymentPaid: Boolean,
    isTogglingPayment: Boolean,
    globalAttendances: List<Attendance>,
    globalRecitations: List<Recitation>,
    globalExams: List<Exam>,
    globalHomeworks: List<Homework>,
    globalAttendanceSummary: AttendanceSummary,
    latestMonthlyPerformance: StudentMonthlyPerformance?,
    onTogglePayment: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onSelectTab: (StudentDetailTab) -> Unit,
    onRecordAttendance: () -> Unit,
    onRecordRecitation: () -> Unit,
    onRecordHomework: () -> Unit,
    onRecordExam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasValidPhone = PhoneUtil.hasValidPhoneNumber(student.parentPhone)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
    ) {
        // --- Header Profile Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius24),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing20),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudentAvatar(
                        fullName = student.fullName,
                        size = 80.dp,
                        fontSize = 24
                    )
                    Spacer(modifier = Modifier.width(Dimens.Spacing16))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "كود الطالب: ${student.studentCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { onNavigateToEdit(student.studentId) }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الصف / المجموعة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(grade?.name ?: "غير محدد", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("رقم ولي الأمر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(student.parentPhone.ifBlank { "غير مسجل" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (hasValidPhone) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).clickable { PhoneUtil.launchCallIntent(context, student.parentPhone) },
                                    tint = PrimaryIndigo
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 1: Quick Summary ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
        ) {
            SummaryCard(
                title = "الحضور",
                value = globalAttendanceSummary.presentCount.toString(),
                color = EmeraldGreen,
                bgColor = EmeraldGreenLight,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "الغياب",
                value = globalAttendanceSummary.absentCount.toString(),
                color = DangerRed,
                bgColor = DangerRedLight,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "التسميع",
                value = globalRecitations.size.toString(),
                color = PrimaryIndigo,
                bgColor = PrimaryIndigoLight,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "الامتحانات",
                value = globalExams.size.toString(),
                color = ElectricBlue,
                bgColor = ElectricBlue.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f)
            )
        }

        // --- SECTION 2: Attendance Summary ---
        ProfileSectionHeader(title = "الحضور", onSeeMore = { onSelectTab(StudentDetailTab.ATTENDANCE) })
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                val lastPresent = globalAttendances.firstOrNull { it.status == AttendanceStatus.PRESENT }
                val lastAbsent = globalAttendances.firstOrNull { it.status == AttendanceStatus.ABSENT }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("آخر حضور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lastPresent?.date ?: "لا يوجد", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("آخر غياب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lastAbsent?.date ?: "لا يوجد", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("آخر السجلات", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                Spacer(modifier = Modifier.height(8.dp))

                globalAttendances.take(3).forEach { att ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(att.date, style = MaterialTheme.typography.bodyMedium)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when(att.status) {
                                AttendanceStatus.PRESENT -> EmeraldGreenLight
                                AttendanceStatus.ABSENT -> DangerRedLight
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = att.status.labelAr,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = when(att.status) {
                                    AttendanceStatus.PRESENT -> EmeraldGreen
                                    AttendanceStatus.ABSENT -> DangerRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                if (globalAttendances.isEmpty()) {
                    Text("لا توجد سجلات حضور حتى الآن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- SECTION 3: Recitation ---
        ProfileSectionHeader(title = "التسميع", onSeeMore = { onSelectTab(StudentDetailTab.RECITATIONS) })
        val lastRecitation = globalRecitations.firstOrNull()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                if (lastRecitation != null) {
                    Text("آخر تسميع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lastRecitation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(lastRecitation.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = PrimaryIndigoLight) {
                            Text("${lastRecitation.score} / ${lastRecitation.maxScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryIndigo, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Text(lastRecitation.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("لا يوجد سجل تسميع حتى الآن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- SECTION 4: Homework ---
        ProfileSectionHeader(title = "الواجب", onSeeMore = { onSelectTab(StudentDetailTab.HOMEWORK) })
        val lastHomework = globalHomeworks.firstOrNull()
        val completedHomeworks = globalHomeworks.count { it.status == HomeworkStatus.COMPLETED }
        val incompleteHomeworks = globalHomeworks.count { it.status == HomeworkStatus.NOT_COMPLETED }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("مكتمل", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                        Text(completedHomeworks.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                    Column {
                        Text("غير مكتمل", style = MaterialTheme.typography.labelSmall, color = DangerRed)
                        Text(incompleteHomeworks.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DangerRed)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (lastHomework != null) {
                    Text("آخر واجب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(lastHomework.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (lastHomework.status == HomeworkStatus.COMPLETED) EmeraldGreenLight else DangerRedLight
                        ) {
                            Text(
                                lastHomework.status.labelAr,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (lastHomework.status == HomeworkStatus.COMPLETED) EmeraldGreen else DangerRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Text("لا يوجد سجل واجبات حتى الآن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- SECTION 5: Exams ---
        ProfileSectionHeader(title = "الامتحانات", onSeeMore = { onSelectTab(StudentDetailTab.EXAMS) })
        val lastExam = globalExams.firstOrNull()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                if (lastExam != null) {
                    Text("آخر امتحان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lastExam.examName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = ElectricBlue.copy(alpha = 0.1f)) {
                            Text("${lastExam.score} / ${lastExam.maxScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ElectricBlue, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Text(lastExam.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("لا يوجد سجل امتحانات حتى الآن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- SECTION 6: Monthly Report ---
        ProfileSectionHeader(title = "التقرير الشهري", onSeeMore = { onSelectTab(StudentDetailTab.MONTHLY_REPORT) })
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                if (latestMonthlyPerformance != null) {
                    Text("تقرير شهر ${getArabicMonthName(latestMonthlyPerformance.month)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReportItem("الحضور", "${latestMonthlyPerformance.attendanceSummary.attendanceRate.toInt()}%")
                        ReportItem("التسميع", "${latestMonthlyPerformance.recitationSummary.averagePercentage.toInt()}%")
                        ReportItem("الامتحان", "${latestMonthlyPerformance.examSummary.averagePercentage.toInt()}%")
                    }
                } else {
                    Text("لا يوجد تقرير شهري حتى الآن.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- SECTION 7: Quick Actions ---
        Text("إجراءات سريعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                QuickActionButton(title = "تسجيل حضور", icon = Icons.Default.CheckCircle, color = EmeraldGreen, onClick = onRecordAttendance, modifier = Modifier.weight(1f))
                QuickActionButton(title = "تسجيل تسميع", icon = Icons.Default.History, color = PrimaryIndigo, onClick = onRecordRecitation, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionButton(title = "تسجيل واجب", icon = Icons.Default.Assignment, color = ElectricBlue, onClick = onRecordHomework, modifier = Modifier.weight(1f))
                QuickActionButton(title = "تسجيل امتحان", icon = Icons.Default.Quiz, color = Color(0xFFF57F17), onClick = onRecordExam, modifier = Modifier.weight(1f))
            }
            QuickActionButton(title = "فتح التقرير الشهري", icon = Icons.Default.ReceiptLong, color = MaterialTheme.colorScheme.onSurfaceVariant, onClick = { onSelectTab(StudentDetailTab.MONTHLY_REPORT) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, color: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileSectionHeader(title: String, onSeeMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
            "عرض الكل",
            style = MaterialTheme.typography.labelMedium,
            color = PrimaryIndigo,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onSeeMore() }
        )
    }
}

@Composable
private fun QuickActionButton(title: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ReportItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun getArabicMonthName(month: Int): String {
    return when (month) {
        1 -> "يناير"
        2 -> "فبراير"
        3 -> "مارس"
        4 -> "أبريل"
        5 -> "مايو"
        6 -> "يونيو"
        7 -> "يوليو"
        8 -> "أغسطس"
        9 -> "سبتمبر"
        10 -> "أكتوبر"
        11 -> "نوفمبر"
        12 -> "ديسمبر"
        else -> "الشهر $month"
    }
}

@Composable
fun InfoDetailRow(
    label: String,
    value: String,
    icon: ImageVector,
    isPrimary: Boolean = false,
    isMuted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(Dimens.Spacing12))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value,
            style = if (isPrimary) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            },
            color = when {
                isPrimary -> MaterialTheme.colorScheme.primary
                isMuted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun InfoActionButton(
    title: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.Radius16))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimens.Radius16),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Spacing12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.Spacing6))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
