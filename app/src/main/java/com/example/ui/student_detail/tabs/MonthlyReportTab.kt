package com.example.ui.student_detail.tabs

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Student
import com.example.core.model.StudentMonthlyPerformance
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.WarmAmber
import java.util.Locale

@Composable
fun MonthlyReportTab(
    student: Student,
    monthNameAr: String,
    performance: StudentMonthlyPerformance?,
    teacherNote: String,
    isSavingNote: Boolean,
    onNavigateMonth: (Int) -> Unit,
    onTeacherNoteChange: (String) -> Unit,
    onSaveReportNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val attSummary = performance?.attendanceSummary
    val recSummary = performance?.recitationSummary
    val examSummary = performance?.examSummary

    // Overall estimation
    val overallScore = if (attSummary != null && recSummary != null && examSummary != null) {
        val attRate = attSummary.attendanceRate
        val recRate = recSummary.averagePercentage
        val examRate = examSummary.averagePercentage
        (attRate * 0.3f + recRate * 0.4f + examRate * 0.3f)
    } else 0f

    val (ratingText, ratingColor) = when {
        overallScore >= 90f -> Pair("ممتاز مع مرتبة الشرف", EmeraldGreen)
        overallScore >= 80f -> Pair("جيد جداً مرتفع", PrimaryIndigo)
        overallScore >= 70f -> Pair("جيد", WarmAmber)
        else -> Pair("يحتاج تكثيف المتابعة", DangerRed)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        // Month Selector Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_month_header"),
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
                    modifier = Modifier.testTag("report_next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "الشهر القادم",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "التقرير الشهري - $monthNameAr",
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
                    modifier = Modifier.testTag("report_prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "الشهر السابق",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Overall Performance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_overall_performance_card"),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing16),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ratingColor.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = ratingColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(Dimens.Spacing12))

                    Column {
                        Text(
                            text = "التقييم العام للطالب",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = ratingText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = ratingColor
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    color = ratingColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${String.format(Locale.ENGLISH, "%.1f", overallScore)}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ratingColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 3 Pillars Overview
        // Pillar 1: Attendance
        ReportPillarCard(
            title = "1. الحضور والغياب",
            icon = Icons.Default.CalendarMonth,
            color = PrimaryIndigo,
            mainStat = "${String.format(Locale.ENGLISH, "%.1f", attSummary?.attendanceRate ?: 0f)}%",
            mainStatLabel = "نسبة الحضور",
            details = listOf(
                "حاضر: ${attSummary?.presentCount ?: 0} يوم",
                "غائب: ${attSummary?.absentCount ?: 0} يوم",
                "متأخر: ${attSummary?.lateCount ?: 0}",
                "بعذر: ${attSummary?.excusedCount ?: 0}"
            )
        )

        // Pillar 2: Recitations
        ReportPillarCard(
            title = "2. التسميعات والحفظ",
            icon = Icons.Default.AutoStories,
            color = EmeraldGreen,
            mainStat = "${String.format(Locale.ENGLISH, "%.1f", recSummary?.averagePercentage ?: 0f)}%",
            mainStatLabel = "معدل الإتقان",
            details = listOf(
                "إجمالي التسميعات: ${recSummary?.totalCount ?: 0}",
                "متوسط الدرجات: ${recSummary?.averageScore ?: 0.0} / ${recSummary?.averageMaxScore ?: 10.0}"
            )
        )

        // Pillar 3: Exams
        ReportPillarCard(
            title = "3. الامتحانات والتقييمات",
            icon = Icons.Default.Assignment,
            color = ElectricBlue,
            mainStat = "${String.format(Locale.ENGLISH, "%.1f", examSummary?.averagePercentage ?: 0f)}%",
            mainStatLabel = "متوسط الامتحانات",
            details = listOf(
                "عدد الامتحانات: ${examSummary?.totalCount ?: 0}",
                "أعلى درجة: ${String.format(Locale.ENGLISH, "%.0f", examSummary?.highestPercentage ?: 0f)}%",
                "أقل درجة: ${String.format(Locale.ENGLISH, "%.0f", examSummary?.lowestPercentage ?: 0f)}%"
            )
        )

        // Pillar 4: Teacher's Monthly Note & Comments
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_teacher_notes_card"),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "ملاحظات وتوصيات المدرس للشهر",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "اكتب تقييمك الشامل لسلوك ومستوى الطالب وتوصياتك لولي الأمر:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = teacherNote,
                    onValueChange = onTeacherNoteChange,
                    placeholder = { Text("اكتب ملاحظاتك الشهرية هنا...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teacher_monthly_note_input"),
                    shape = RoundedCornerShape(Dimens.Radius12)
                )

                Button(
                    onClick = onSaveReportNote,
                    enabled = !isSavingNote,
                    shape = RoundedCornerShape(Dimens.Radius12),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_report_note_button")
                ) {
                    if (isSavingNote) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري الحفظ...")
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ التقرير الشهري والملاحظات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportPillarCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    mainStat: String,
    mainStatLabel: String,
    details: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Radius20),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Spacing16)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    color = color.copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = mainStat,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = color
                        )
                        Text(
                            text = mainStatLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing12))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
            ) {
                details.forEach { detail ->
                    Surface(
                        shape = RoundedCornerShape(Dimens.Radius8),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
