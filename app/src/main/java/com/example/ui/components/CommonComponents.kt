package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.Grade
import com.example.core.model.Student
import com.example.ui.theme.AvatarBlueBg
import com.example.ui.theme.AvatarBlueText
import com.example.ui.theme.AvatarPeachBg
import com.example.ui.theme.AvatarPeachText
import com.example.ui.theme.AvatarPinkBg
import com.example.ui.theme.AvatarPinkText
import com.example.ui.theme.AvatarPurpleBg
import com.example.ui.theme.AvatarPurpleText
import com.example.ui.theme.AvatarTealBg
import com.example.ui.theme.AvatarTealText
import com.example.ui.theme.AvatarYellowBg
import com.example.ui.theme.AvatarYellowText
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.VioletPurple
import kotlin.math.abs

@Composable
fun StudentAvatar(
    fullName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    fontSize: Int = 16
) {
    val initials = remember(fullName) {
        val words = fullName.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        when {
            words.size >= 2 -> {
                val first = words[0].take(1)
                val second = words[1].take(1)
                "$first$second"
            }
            words.isNotEmpty() -> words[0].take(2)
            else -> "ط"
        }
    }

    val (bgColor, textColor) = remember(fullName) {
        val hash = abs(fullName.hashCode())
        when (hash % 6) {
            0 -> Pair(AvatarTealBg, AvatarTealText)
            1 -> Pair(AvatarYellowBg, AvatarYellowText)
            2 -> Pair(AvatarPeachBg, AvatarPeachText)
            3 -> Pair(AvatarBlueBg, AvatarBlueText)
            4 -> Pair(AvatarPinkBg, AvatarPinkText)
            else -> Pair(AvatarPurpleBg, AvatarPurpleText)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
    ) {
        Text(
            text = initials,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StudentCard(
    student: Student,
    gradeName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius20))
            .clickable(onClick = onClick)
            .testTag("student_card_${student.studentCode}"),
        shape = RoundedCornerShape(Dimens.Radius20),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Spacing16)
        ) {
            // Top Row: Avatar + Name + Code + Left Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right side: Avatar
                StudentAvatar(
                    fullName = student.fullName,
                    size = 46.dp,
                    fontSize = 16
                )

                Spacer(modifier = Modifier.width(Dimens.Spacing12))

                // Middle: Name and Code
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = student.studentCode,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PrimaryIndigo
                    )
                }

                // Left Arrow Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "عرض الملف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing12))

            // Bottom Row: WhatsApp Badge on Right + Phone on Left
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // WhatsApp Pill (Matching Screenshot 5)
                if (student.hasWhatsApp) {
                    Surface(
                        shape = RoundedCornerShape(Dimens.Radius12),
                        color = EmeraldGreenLight
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "واتساب",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF047857)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(Dimens.Radius12),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "بدون واتساب",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Phone with Call Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = student.parentPhone,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GradeCard(
    grade: Grade,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryIndigo
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Radius20))
            .clickable(onClick = onClick)
            .testTag("grade_card_${grade.id}"),
        shape = RoundedCornerShape(Dimens.Radius20),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Right edge vertical colored line (Matching Screenshots 1, 2, 4)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing20, vertical = Dimens.Spacing16),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.padding(start = Dimens.Spacing8)) {
                    Text(
                        text = grade.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${grade.studentCount} طالب",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }

                // Left Arrow Circle Button (Matching Screenshots)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigoLight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "عرض الصف",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchHeader(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "...ابحث باسم الطالب أو الكود",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "مسح البحث",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimens.Radius16),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = PrimaryIndigo,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_input_field")
    )
}

@Composable
fun EmptyStateView(
    title: String = "لا يوجد طلاب حتى الآن",
    description: String = "أضف أول طالب إلى هذا الصف للبدء في تنظيم وإدارة بياناتك.",
    buttonText: String? = "+ إضافة طالب",
    onButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.Spacing32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimaryIndigoLight)
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.Spacing16))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.Spacing6))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
        )

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(Dimens.Spacing20))

            Button(
                onClick = onButtonClick,
                shape = RoundedCornerShape(Dimens.Radius16),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                modifier = Modifier.testTag("empty_state_action_button")
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmationBottomSheet(
    studentName: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Dimens.Radius24, topEnd = Dimens.Radius24),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Spacing24, vertical = Dimens.Spacing16),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(DangerRedLight)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing16))

            Text(
                text = "حذف الطالب؟",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Dimens.Spacing8))

            Text(
                text = "سيتم حذف بيانات الطالب \"$studentName\" من القائمة نهائياً.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.Spacing24))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(Dimens.Radius16),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cancel_delete_button")
                ) {
                    Text("إلغاء", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onConfirmDelete,
                    shape = RoundedCornerShape(Dimens.Radius16),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("confirm_delete_button")
                ) {
                    Text(
                        "حذف الطالب",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing16))
        }
    }
}

@Composable
fun AddSuccessDialog(
    student: Student,
    onViewProfile: (String) -> Unit,
    onAddAnother: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(Dimens.Radius24),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimens.ElevationLarge),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing24),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreenLight)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "نجاح",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                Text(
                    text = "تمت إضافة الطالب بنجاح",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing6))

                Text(
                    text = student.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing12))

                Surface(
                    shape = RoundedCornerShape(Dimens.Radius12),
                    color = PrimaryIndigoLight,
                    modifier = Modifier.padding(vertical = Dimens.Spacing4)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8)
                    ) {
                        Text(
                            text = "كود الطالب: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = student.studentCode,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryIndigo
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing24))

                Button(
                    onClick = { onViewProfile(student.studentId) },
                    shape = RoundedCornerShape(Dimens.Radius16),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("view_added_student_button")
                ) {
                    Text(
                        "عرض ملف الطالب",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing8))

                OutlinedButton(
                    onClick = onAddAnother,
                    shape = RoundedCornerShape(Dimens.Radius16),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("add_another_student_button")
                ) {
                    Text("إضافة طالب آخر", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
