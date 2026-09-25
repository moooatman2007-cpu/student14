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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.core.model.Exam
import com.example.core.model.ExamSummary
import com.example.core.model.Student
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.WarmAmber
import java.util.Locale

@Composable
fun ExamsTab(
    student: Student,
    exams: List<Exam>,
    summary: ExamSummary,
    onAddExam: () -> Unit,
    onEditExam: (Exam) -> Unit,
    onDeleteExam: (Exam) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        // Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("exam_summary_card"),
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
                    text = "ملخص الامتحانات والتقييمات",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                ) {
                    ExamStatMiniCard(
                        title = "الامتحانات",
                        value = "${summary.totalCount}",
                        unit = "اختبار",
                        color = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    ExamStatMiniCard(
                        title = "المتوسط",
                        value = "${String.format(Locale.ENGLISH, "%.1f", summary.averagePercentage)}%",
                        unit = "معدل درجات",
                        color = EmeraldGreen,
                        modifier = Modifier.weight(1f)
                    )
                    ExamStatMiniCard(
                        title = "أعلى درجة",
                        value = "${String.format(Locale.ENGLISH, "%.0f", summary.highestPercentage)}%",
                        unit = "الدرجة القصوى",
                        color = PrimaryIndigo,
                        modifier = Modifier.weight(1f)
                    )
                    ExamStatMiniCard(
                        title = "أقل درجة",
                        value = "${String.format(Locale.ENGLISH, "%.0f", summary.lowestPercentage)}%",
                        unit = "أدنى درجة",
                        color = WarmAmber,
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
                text = "قائمة الامتحانات (${exams.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onAddExam,
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_exam_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "إضافة امتحان",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Exams List / Empty State
        if (exams.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exam_empty_state"),
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
                            .background(ElectricBlueLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    Text(
                        text = "لا توجد امتحانات مسجلة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing6))

                    Text(
                        text = "قم بتسجيل الاختبارات والتقييمات الشهرية ودرجات الطالب",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing20))

                    Button(
                        onClick = onAddExam,
                        shape = RoundedCornerShape(Dimens.Radius12),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.testTag("empty_add_exam_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة أول امتحان", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                exams.forEach { item ->
                    ExamItemCard(
                        exam = item,
                        onClick = { onEditExam(item) },
                        onDelete = { onDeleteExam(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExamStatMiniCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius12),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = Dimens.Spacing8, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExamItemCard(
    exam: Exam,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percentage = if (exam.maxScore > 0) (exam.score / exam.maxScore) * 100 else 0.0
    val scoreColor = when {
        percentage >= 90 -> EmeraldGreen
        percentage >= 75 -> PrimaryIndigo
        percentage >= 60 -> WarmAmber
        else -> DangerRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius16))
            .clickable(onClick = onClick)
            .testTag("exam_item_${exam.examId}"),
        shape = RoundedCornerShape(Dimens.Radius16),
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
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.examName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (!exam.subject.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElectricBlueLight
                            ) {
                                Text(
                                    text = exam.subject,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ElectricBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = exam.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Score Badge
                Surface(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    color = scoreColor.copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${exam.score}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = scoreColor
                            )
                            Text(
                                text = " / ${exam.maxScore}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scoreColor.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = "${String.format(Locale.ENGLISH, "%.1f", percentage)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = scoreColor
                        )
                    }
                }
            }

            if (!exam.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.Spacing8))
                Surface(
                    shape = RoundedCornerShape(Dimens.Radius8),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ملاحظة: ${exam.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.Spacing10, vertical = Dimens.Spacing6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
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
