package com.example.ui.start_lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningLight
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.WhatsAppColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationReviewContent(
    reviewState: NotificationReviewState,
    isSubmitting: Boolean = false,
    creationSummary: NotificationEventCreationSummary? = null,
    onConfirmAndProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allItems = reviewState.absenceItems +
            reviewState.recitationItems +
            reviewState.homeworkItems +
            reviewState.examItems

    val readyCount = allItems.count { it.eligibleForNotification }
    val pendingCount = allItems.count { !it.isConfirmedOnline }
    val unavailableCount = allItems.count { it.isConfirmedOnline && !it.eligibleForNotification }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.Spacing16)
            .testTag("notification_review_screen"),
        contentPadding = PaddingValues(vertical = Dimens.Spacing16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
    ) {
        // Result Summary Banner if creation occurred
        if (creationSummary != null) {
            item {
                val isPartialOrFailed = creationSummary.failedCount > 0
                Card(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPartialOrFailed) AmberWarningLight else EmeraldGreenLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                        ) {
                            Icon(
                                imageVector = if (isPartialOrFailed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isPartialOrFailed) AmberWarning else EmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isPartialOrFailed) "نتيجة تجهيز الإشعارات" else "تم تجهيز الإشعارات بنجاح",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isPartialOrFailed) AmberWarning else EmeraldGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.Spacing4))
                        Text(
                            text = "تم تجهيز ${creationSummary.createdCount + creationSummary.alreadyExistsCount} إشعار بنجاح" +
                                    if (creationSummary.failedCount > 0) "، وتعذر تجهيز ${creationSummary.failedCount} إشعار (بيانات الحصة محفوظة)." else " في قائمة الانتظار.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        // Header card
        item {
            Card(
                shape = RoundedCornerShape(Dimens.Radius16),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Dimens.Spacing16)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryIndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "مراجعة الإشعارات",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "راجع ملخص الحصة وحالة إشعارات الواتساب قبل الاعتماد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    // Counts Summary Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                    ) {
                        SummaryPill(
                            label = "الطلاب",
                            count = reviewState.totalStudents,
                            bgColor = MaterialTheme.colorScheme.surface,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                        SummaryPill(
                            label = "الحاضر",
                            count = reviewState.presentStudents,
                            bgColor = EmeraldGreenLight,
                            textColor = EmeraldGreen
                        )
                        SummaryPill(
                            label = "الغائب",
                            count = reviewState.absentStudents,
                            bgColor = DangerRedLight,
                            textColor = DangerRed
                        )
                        SummaryPill(
                            label = "التسميع",
                            count = reviewState.recitationCount,
                            bgColor = ElectricBlueLight,
                            textColor = ElectricBlue
                        )
                        SummaryPill(
                            label = "الواجب",
                            count = reviewState.homeworkCount,
                            bgColor = PrimaryIndigoLight,
                            textColor = PrimaryIndigo
                        )
                        SummaryPill(
                            label = "الامتحان",
                            count = reviewState.examCount,
                            bgColor = AmberWarningLight,
                            textColor = AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    // Notification Status Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                    ) {
                        NotificationStatusChip(
                            label = "جاهز للإرسال",
                            count = readyCount,
                            color = EmeraldGreen,
                            bgColor = EmeraldGreenLight,
                            modifier = Modifier.weight(1f)
                        )
                        NotificationStatusChip(
                            label = "معلّق",
                            count = pendingCount,
                            color = AmberWarning,
                            bgColor = AmberWarningLight,
                            modifier = Modifier.weight(1f)
                        )
                        NotificationStatusChip(
                            label = "غير متاح",
                            count = unavailableCount,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            bgColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 1. Absence Notifications Section
        if (reviewState.absenceItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "إشعارات الغياب",
                    count = reviewState.absenceItems.size,
                    icon = Icons.Default.PersonOff,
                    iconTint = DangerRed
                )
            }
            items(reviewState.absenceItems, key = { "abs_${it.studentId}_${it.sourceId}" }) { item ->
                NotificationReviewItemCard(item)
            }
        }

        // 2. Recitation Notifications Section
        if (reviewState.recitationItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "إشعارات التسميع",
                    count = reviewState.recitationItems.size,
                    icon = Icons.Default.AutoStories,
                    iconTint = ElectricBlue
                )
            }
            items(reviewState.recitationItems, key = { "rec_${it.studentId}_${it.sourceId}" }) { item ->
                NotificationReviewItemCard(item)
            }
        }

        // 3. Homework Notifications Section
        if (reviewState.homeworkItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "إشعارات الواجب",
                    count = reviewState.homeworkItems.size,
                    icon = Icons.Default.Assignment,
                    iconTint = PrimaryIndigo
                )
            }
            items(reviewState.homeworkItems, key = { "hw_${it.studentId}_${it.sourceId}" }) { item ->
                NotificationReviewItemCard(item)
            }
        }

        // 4. Exam Notifications Section
        if (reviewState.examItems.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "إشعارات الامتحان",
                    count = reviewState.examItems.size,
                    icon = Icons.Default.School,
                    iconTint = AmberWarning
                )
            }
            items(reviewState.examItems, key = { "ex_${it.studentId}_${it.sourceId}" }) { item ->
                NotificationReviewItemCard(item)
            }
        }

        // Empty state if no notification items
        if (allItems.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.Spacing24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing8))
                        Text(
                            text = "لا توجد إشعارات مطلوب إرسالها لهذه الحصة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action Button
        item {
            Spacer(modifier = Modifier.height(Dimens.Spacing8))
            Button(
                onClick = onConfirmAndProceed,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(Dimens.Radius12),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_notifications_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "اعتماد الحصة وتجهيز الإشعارات",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Dimens.Spacing16))
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    count: Int,
    bgColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius8),
        color = bgColor,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
        }
    }
}

@Composable
private fun NotificationStatusChip(
    label: String,
    count: Int,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius8),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = Dimens.Spacing8, horizontal = Dimens.Spacing4),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: ImageVector,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.Spacing8, bottom = Dimens.Spacing4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun NotificationReviewItemCard(item: NotificationReviewItem) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius12),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(Dimens.Radius12)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Spacing12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.studentName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.parentPhone.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (item.whatsappEnabled) WhatsAppColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = item.parentPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "لا يوجد رقم هاتف",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed
                        )
                    }
                }
            }

            // Status Badge
            StatusBadge(item)
        }
    }
}

@Composable
private fun StatusBadge(item: NotificationReviewItem) {
    when {
        item.eligibleForNotification -> {
            Surface(
                shape = RoundedCornerShape(Dimens.Radius8),
                color = EmeraldGreenLight
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen)
                    )
                    Text(
                        text = "جاهز للإرسال",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                }
            }
        }
        !item.isConfirmedOnline -> {
            Surface(
                shape = RoundedCornerShape(Dimens.Radius8),
                color = AmberWarningLight
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "معلق حتى تكتمل المزامنة",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AmberWarning
                    )
                }
            }
        }
        else -> {
            Surface(
                shape = RoundedCornerShape(Dimens.Radius8),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = item.ineligibilityReason ?: "غير متاح",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
