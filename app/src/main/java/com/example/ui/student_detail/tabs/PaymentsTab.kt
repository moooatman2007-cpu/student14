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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.core.model.LessonPayment
import com.example.core.model.Student
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.Dimens
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight

@Composable
fun PaymentsTab(
    student: Student,
    monthNameAr: String,
    monthlyPayment: LessonPayment?,
    isPaid: Boolean,
    isToggling: Boolean,
    onNavigateMonth: (Int) -> Unit,
    onTogglePayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing20)
    ) {
        // Month Navigation Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius20),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing12),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onNavigateMonth(1) },
                    modifier = Modifier.testTag("payments_next_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "الشهر التالي",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = monthNameAr,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { onNavigateMonth(-1) },
                    modifier = Modifier.testTag("payments_prev_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "الشهر السابق",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Main Payment Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.Radius24),
            colors = CardDefaults.cardColors(
                containerColor = if (isPaid) EmeraldGreenLight.copy(alpha = 0.5f) else DangerRedLight.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isPaid) EmeraldGreen else DangerRed)
                ) {
                    Icon(
                        imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = if (isPaid) "مدفوع" else "غير مدفوع",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                Text(
                    text = if (isPaid) "🟢 تم دفع الاشتراك الشهري" else "🔴 لم يتم دفع الاشتراك الشهري",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isPaid) Color(0xFF047857) else DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("payment_status_text")
                )

                Spacer(modifier = Modifier.height(Dimens.Spacing8))

                Text(
                    text = "الطالب: ${student.fullName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isPaid && !monthlyPayment?.paidAt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Dimens.Spacing4))
                    Text(
                        text = "تاريخ السداد: ${monthlyPayment?.paidAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing20))

                // Toggle Action Button
                Button(
                    onClick = onTogglePayment,
                    enabled = !isToggling,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaid) DangerRed else EmeraldGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(Dimens.Radius16),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp)
                        .testTag("toggle_payment_button")
                ) {
                    if (isToggling) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                            Text(
                                text = if (isPaid) "تغيير الحالة إلى: غير مدفوع" else "تغيير الحالة إلى: مدفوع",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
