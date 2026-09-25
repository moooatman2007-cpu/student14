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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.model.Recitation
import com.example.core.model.RecitationSummary
import com.example.core.model.Student
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Dimens
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.WarmAmber
import java.util.Locale

@Composable
fun RecitationsTab(
    student: Student,
    recitations: List<Recitation>,
    summary: RecitationSummary,
    onAddRecitation: () -> Unit,
    onEditRecitation: (Recitation) -> Unit,
    onDeleteRecitation: (Recitation) -> Unit,
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
                .testTag("recitation_summary_card"),
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
                    text = "ملخص التسميعات",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                ) {
                    RecitationStatMiniCard(
                        title = "عدد التسميعات",
                        value = "${summary.totalCount}",
                        unit = "تسميع",
                        modifier = Modifier.weight(1f)
                    )
                    RecitationStatMiniCard(
                        title = "متوسط الدرجات",
                        value = "${summary.averageScore}",
                        unit = "من ${summary.averageMaxScore}",
                        modifier = Modifier.weight(1f)
                    )
                    RecitationStatMiniCard(
                        title = "نسبة الإتقان",
                        value = "${String.format(Locale.ENGLISH, "%.1f", summary.averagePercentage)}%",
                        unit = "المعدل العام",
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
                text = "قائمة التسميعات (${recitations.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onAddRecitation,
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_recitation_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "إضافة تسميع",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Recitations List / Empty State
        if (recitations.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recitation_empty_state"),
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
                            .background(EmeraldGreenLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    Text(
                        text = "لا توجد تسميعات مسجلة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing6))

                    Text(
                        text = "قم بتسجيل سور وآيات الحفظ ودرجات الطالب وملاحظات التجويد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing20))

                    Button(
                        onClick = onAddRecitation,
                        shape = RoundedCornerShape(Dimens.Radius12),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.testTag("empty_add_recitation_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة أول تسميع", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                recitations.forEach { item ->
                    RecitationItemCard(
                        recitation = item,
                        onClick = { onEditRecitation(item) },
                        onDelete = { onDeleteRecitation(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecitationStatMiniCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius12),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Spacing10),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = EmeraldGreen
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
fun RecitationItemCard(
    recitation: Recitation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percentage = if (recitation.maxScore > 0) (recitation.score / recitation.maxScore) * 100 else 0.0
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
            .testTag("recitation_item_${recitation.recitationId}"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recitation.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = recitation.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (recitation.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recitation.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Score Badge
                Surface(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    color = scoreColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${recitation.score}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = scoreColor
                        )
                        Text(
                            text = " / ${recitation.maxScore}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scoreColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (!recitation.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.Spacing8))
                Surface(
                    shape = RoundedCornerShape(Dimens.Radius8),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ملاحظة: ${recitation.note}",
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
