package com.example.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.Grade
import com.example.core.model.SystemMonitoringAlert
import com.example.ui.components.GradeCard
import com.example.ui.components.SyncStatusIndicator
import com.example.ui.monitoring.SystemMonitoringViewModel
import com.example.ui.theme.ActionAddBg
import com.example.ui.theme.ActionAddIcon
import com.example.ui.theme.ActionAttendanceBg
import com.example.ui.theme.ActionAttendanceIcon
import com.example.ui.theme.ActionSearchBg
import com.example.ui.theme.ActionSearchIcon
import com.example.ui.theme.ActionStudentsBg
import com.example.ui.theme.ActionStudentsIcon
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.VioletPurple
import com.example.ui.theme.WarmAmber

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.shadow
import com.example.ui.theme.DangerRed

@Composable
fun HomeScreen(
    onNavigateToAddStudent: (String?) -> Unit,
    onNavigateToStudents: (String?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToReports: () -> Unit = {},
    onNavigateToFastAttendance: (String?) -> Unit = {},
    onNavigateToBarcodes: () -> Unit = {},
    onNavigateToStartLesson: () -> Unit = {},
    onNavigateToStudentDetail: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    monitoringViewModel: SystemMonitoringViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val monitoringState by monitoringViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("home_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Dimens.Spacing20,
                end = Dimens.Spacing20,
                bottom = Dimens.Spacing32,
                top = Dimens.Spacing16
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing20)
        ) {
            // 1. Greeting Header Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.teacherName.isNotBlank()) "صباح الخير، ${uiState.teacherName} 👋" else "أهلاً بك 👋",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = uiState.currentDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigoLight)
                            .border(1.dp, PrimaryIndigo.copy(alpha = 0.2f), CircleShape)
                    ) {
                        if (!uiState.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uiState.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // System Monitoring Alerts Banner (Only shown when there are open alerts)
            if (monitoringState.alerts.isNotEmpty()) {
                item {
                    SystemMonitoringBanner(alerts = monitoringState.alerts)
                }
            }

            // 2. Quick Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                    ) {
                        QuickActionCard(
                            title = "ابدأ الحصة",
                            icon = Icons.Default.PlayArrow,
                            color = EmeraldGreen,
                            onClick = { onNavigateToStartLesson() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "حضور سريع",
                            icon = Icons.Default.CalendarToday,
                            color = PrimaryIndigo,
                            onClick = { onNavigateToFastAttendance(null) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                    ) {
                        QuickActionCard(
                            title = "إضافة طالب",
                            icon = Icons.Default.PersonAdd,
                            color = ElectricBlue,
                            onClick = { onNavigateToAddStudent(null) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "التقارير",
                            icon = Icons.Default.Assessment,
                            color = VioletPurple,
                            onClick = { onNavigateToReports() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Students Overview Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToStudents(null) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "👥 الطلاب",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.stats.totalStudents}",
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                                color = Color.White
                            )
                            Text(
                                text = "إجمالي الطلاب",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // 4. Today's Attendance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📋 حضور اليوم",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            if (uiState.stats.totalStudents > 0 && (uiState.todayPresentCount + uiState.todayAbsentCount) == 0) {
                                Text(
                                    text = "لم يسجل بعد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DangerRed,
                                    modifier = Modifier
                                        .background(DangerRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AttendanceMetric(label = "حاضر", value = uiState.todayPresentCount, color = EmeraldGreen)
                            AttendanceMetric(label = "غائب", value = uiState.todayAbsentCount, color = DangerRed)
                            AttendanceMetric(label = "لم يسجل", value = uiState.todayNotRecordedCount, color = MaterialTheme.colorScheme.outline)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "${uiState.todayPresentCount + uiState.todayAbsentCount} من ${uiState.stats.totalStudents}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${uiState.attendancePercentage.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = PrimaryIndigo
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.attendancePercentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape),
                                color = PrimaryIndigo,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                        }

                        if (uiState.stats.totalStudents > 0 && (uiState.todayPresentCount + uiState.todayAbsentCount) == 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToStartLesson() },
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldGreen.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ابدأ تسجيل الحضور", color = EmeraldGreen, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Needs Follow-up (Feature 4 integration)
            item {
                FollowUpSection(
                    followUpStudents = uiState.followUpStudents,
                    onStudentClick = onNavigateToStudentDetail,
                    onViewAllClick = { onNavigateToStudents(null) }
                )
            }

            // 6. Sync Status (Small and clear)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SyncStatusIndicator(
                            syncStatus = uiState.syncStatus,
                            onRetry = { viewModel.retrySync() }
                        )
                        
                        Text(
                            text = "v1.0.5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f))
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun AttendanceMetric(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$value", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FollowUpSection(
    followUpStudents: List<FollowUpStudent>,
    onStudentClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🎯 يحتاج متابعة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (followUpStudents.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = WarmAmber.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${followUpStudents.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarmAmber
                        )
                    }
                }
            }

            if (followUpStudents.isNotEmpty()) {
                Text(
                    text = "عرض الكل",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryIndigo,
                    modifier = Modifier.clickable { onViewAllClick() }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.Spacing12))

        if (followUpStudents.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldGreenLight.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🎉 ممتاز. لا يوجد طلاب يحتاجون متابعة حاليًا.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = EmeraldGreen
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                followUpStudents.forEach { student ->
                    FollowUpItem(
                        student = student,
                        onClick = { onStudentClick(student.studentId) }
                    )
                }
            }
        }
    }
}

@Composable
fun FollowUpItem(
    student: FollowUpStudent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WarmAmber.copy(alpha = 0.1f))
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = student.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun HomeMetricPillCard(
    title: String,
    value: String,
    subtitle: String,
    badgeColor: Color,
    iconTint: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Spacing10, vertical = Dimens.Spacing12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing6))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = iconTint
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HomeActionButton(
    title: String,
    icon: ImageVector,
    badgeBg: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Spacing16, horizontal = Dimens.Spacing12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing10))

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SystemMonitoringBanner(
    alerts: List<SystemMonitoringAlert>,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return

    val latestAlert = alerts.first()
    val totalCount = alerts.size

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_monitoring_banner"),
        shape = RoundedCornerShape(Dimens.Radius16),
        colors = CardDefaults.cardColors(
            containerColor = DangerRed.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Spacing16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DangerRed.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "تنبيه بالنظام",
                    tint = DangerRed,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(Dimens.Spacing12))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (totalCount > 1) "تنبيه بالنظام ($totalCount تنبيهات)" else "تنبيه بالنظام",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DangerRed
                )

                Spacer(modifier = Modifier.height(2.dp))

                val alertMessage = latestAlert.message.ifBlank {
                    "يوجد تنبيه متعلق بخدمات النظام يتطلب متابعتك."
                }

                Text(
                    text = alertMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (latestAlert.failureCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عدد حالات الفشل: ${latestAlert.failureCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = DangerRed
                    )
                }
            }
        }
    }
}

