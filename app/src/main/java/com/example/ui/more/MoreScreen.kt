package com.example.ui.more

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.repository.ThemeMode
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.Dimens
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.PrimaryIndigo

@Composable
fun MoreScreen(
    onNavigateToProfile: () -> Unit = {},
    onLogoutSuccess: () -> Unit = {},
    onNavigateToGroups: () -> Unit = {},
    viewModel: MoreViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("more_screen")
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
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
        ) {
            // Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "المزيد",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "الإعدادات والتفضيلات",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Profile Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToProfile() }
                        .testTag("setting_profile_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.Spacing20, vertical = Dimens.Spacing16),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                        ) {
                            if (!uiState.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(uiState.avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "صورة المعلم",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initial = if (uiState.teacherName.isNotBlank()) uiState.teacherName.trim().take(1) else "م"
                                Text(
                                    text = initial,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(Dimens.Spacing16))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.teacherName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "معلم • Student Manager",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Settings Grouped Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        // 1. الحساب
                        SettingMenuItem(
                            icon = Icons.Default.Person,
                            title = "الحساب",
                            subtitle = "بياناتك الشخصية",
                            iconBg = Color(0xFFDBEAFE),
                            iconColor = Color(0xFF2563EB),
                            onClick = onNavigateToProfile,
                            testTag = "setting_account"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 2. إدارة الصفوف
                        SettingMenuItem(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = "إدارة الصفوف",
                            subtitle = if (uiState.grades.isNotEmpty()) "الصفوف المسجلة (${uiState.grades.size})" else "إضافة صف أو تعديله",
                            iconBg = Color(0xFFEDE9FE),
                            iconColor = Color(0xFF7C3AED),
                            onClick = {
                                val gradeNames = uiState.grades.joinToString(" • ") { it.name }
                                val msg = if (gradeNames.isNotBlank()) {
                                    "الصفوف المسجلة: $gradeNames"
                                } else {
                                    "لا توجد صفوف مسجلة حاليًا"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            testTag = "setting_grades"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 2.b إدارة المجموعات
                        SettingMenuItem(
                            icon = Icons.Default.Groups,
                            title = "إدارة المجموعات",
                            subtitle = "المجموعات ومواعيد حضور الطلاب",
                            iconBg = Color(0xFFE0F2FE),
                            iconColor = Color(0xFF0284C7),
                            onClick = onNavigateToGroups,
                            testTag = "setting_groups"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 3. الإشعارات
                        SettingMenuItem(
                            icon = Icons.Default.Notifications,
                            title = "الإشعارات",
                            subtitle = "تفضيلات التنبيهات",
                            iconBg = Color(0xFFFEF3C7),
                            iconColor = Color(0xFFD97706),
                            onClick = {
                                Toast.makeText(context, "إعدادات الإشعارات جاهزة للمرحلة الثانية", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "setting_notifications"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 4. المظهر
                        SettingMenuItem(
                            icon = Icons.Default.Palette,
                            title = "المظهر",
                            subtitle = when (uiState.themeMode) {
                                ThemeMode.SYSTEM -> "فاتح / داكن / تلقائي"
                                ThemeMode.LIGHT -> "الوضع الفاتح"
                                ThemeMode.DARK -> "الوضع الداكن"
                            },
                            iconBg = Color(0xFFFCE7F3),
                            iconColor = Color(0xFFDB2777),
                            onClick = viewModel::openThemeDialog,
                            testTag = "setting_theme"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 5. اللغة
                        SettingMenuItem(
                            icon = Icons.Default.Language,
                            title = "اللغة",
                            subtitle = "العربية",
                            iconBg = Color(0xFFCCFBF1),
                            iconColor = Color(0xFF0D9488),
                            onClick = {
                                Toast.makeText(context, "اللغة الحالية: العربية (RTL)", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "setting_language"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 6. اختبار اتصال WhatsApp Server (زر مؤقت)
                        SettingMenuItem(
                            icon = Icons.Default.Notifications,
                            title = "اختبار اتصال WhatsApp Server",
                            subtitle = "فحص حالة خادم الواتساب عبر Supabase Edge Function",
                            iconBg = Color(0xFFDCFCE7),
                            iconColor = Color(0xFF16A34A),
                            onClick = viewModel::testWahaConnection,
                            testTag = "setting_waha_test"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 6.b ربط WhatsApp
                        SettingMenuItem(
                            icon = Icons.Default.Language,
                            title = "ربط WhatsApp",
                            subtitle = "ربط حسابك لإرسال الرسائل والتقارير تلقائياً",
                            iconBg = Color(0xFFE8F5E9),
                            iconColor = Color(0xFF2E7D32),
                            onClick = viewModel::openWahaPairingDialog,
                            testTag = "setting_waha_pairing"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 7. حول التطبيق
                        SettingMenuItem(
                            icon = Icons.Default.Info,
                            title = "حول التطبيق",
                            subtitle = "الإصدار 1.0.0",
                            iconBg = Color(0xFFEFF6FF),
                            iconColor = Color(0xFF3B82F6),
                            onClick = viewModel::openAboutDialog,
                            testTag = "setting_about"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = Dimens.Spacing16)
                        )

                        // 8. تسجيل الخروج
                        SettingMenuItem(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = "تسجيل الخروج",
                            subtitle = "إنهاء الجلسة الحالية والعودة لصفحة الدخول",
                            iconBg = Color(0xFFFEE2E2),
                            iconColor = Color(0xFFDC2626),
                            onClick = viewModel::openLogoutDialog,
                            testTag = "setting_logout"
                        )
                    }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(Dimens.Spacing8))
                Text(
                    text = "Student Manager • v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Theme Dialog
        if (uiState.showThemeDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeThemeDialog,
                title = {
                    Text(
                        text = "اختر المظهر",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column {
                        ThemeOptionRow(
                            title = "تلقائي حسب النظام",
                            selected = uiState.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.selectTheme(ThemeMode.SYSTEM) }
                        )
                        ThemeOptionRow(
                            title = "الوضع الفاتح (Light Mode)",
                            selected = uiState.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.selectTheme(ThemeMode.LIGHT) }
                        )
                        ThemeOptionRow(
                            title = "الوضع الداكن (Dark Mode)",
                            selected = uiState.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.selectTheme(ThemeMode.DARK) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::closeThemeDialog) {
                        Text("إغلاق", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }

        // Edit Teacher Name Dialog
        if (uiState.showEditProfileDialog) {
            var tempName by remember { mutableStateOf(uiState.teacherName) }
            AlertDialog(
                onDismissRequest = viewModel::closeEditProfileDialog,
                title = {
                    Text(
                        text = "تعديل اسم المدرس",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "يظهر هذا الاسم في ترويسة الشاشة الرئيسية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing12))
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("الاسم الكامل") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.updateTeacherName(tempName) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Text("حفظ")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::closeEditProfileDialog) {
                        Text("إلغاء")
                    }
                }
            )
        }

        // About Dialog
        if (uiState.showAboutDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeAboutDialog,
                icon = {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_ios),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                },
                title = {
                    Text(
                        text = "Student Manager",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "تطبيق احترافي لإدارة بيانات الطلاب والصفوف المدرسية للمدرسين.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing12))
                        Text(
                            text = "• توليد تلقائي وثابت لأكواد الطلاب ST-XXXXX\n• تصفية سريعة وبحث فوري بالاسم والكود\n• إدارة بيانات أولياء الأمور والواتساب\n• بنية نظيفة (Clean Architecture & MVVM)\n• دعم كامل للوضع الداكن وRTL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::closeAboutDialog) {
                        Text("حسناً")
                    }
                }
            )
        }

        // Logout Confirmation Dialog
        if (uiState.showLogoutDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeLogoutDialog,
                title = {
                    Text(
                        text = "تسجيل الخروج",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "هل أنت متأكد أنك تريد تسجيل الخروج؟",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.logoutError != null) {
                            Spacer(modifier = Modifier.height(Dimens.Spacing12))
                            Text(
                                text = uiState.logoutError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.performLogout(onSuccess = onLogoutSuccess)
                        },
                        enabled = !uiState.isLoggingOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_logout_button")
                    ) {
                        if (uiState.isLoggingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        }
                        Text(
                            text = "تسجيل الخروج",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::closeLogoutDialog,
                        enabled = !uiState.isLoggingOut
                    ) {
                        Text("إلغاء")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // WAHA Connection Test Dialog
        if (uiState.isTestingWaha || uiState.wahaTestResult != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissWahaTestResult,
                title = {
                    Text(
                        text = "اختبار اتصال WhatsApp Server",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    if (uiState.isTestingWaha) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.Spacing16)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(Dimens.Spacing12))
                            Text("جاري اختبار الاتصال بالخادم...")
                        }
                    } else if (uiState.wahaTestResult != null) {
                        Text(
                            text = uiState.wahaTestResult!!,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {
                    if (!uiState.isTestingWaha) {
                        TextButton(onClick = viewModel::dismissWahaTestResult) {
                            Text("إغلاق")
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // WhatsApp Pairing Dialog
        if (uiState.showWahaPairingDialog) {
            val context = LocalContext.current
            val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

            // Auto-copy pairing code whenever a new valid code is generated
            LaunchedEffect(uiState.pairingCode) {
                val code = uiState.pairingCode
                if (!code.isNullOrBlank()) {
                    try {
                        val clip = android.content.ClipData.newPlainText("WhatsApp Pairing Code", code)
                        clipboardManager.setPrimaryClip(clip)
                        viewModel.recordPairingCodeCopied(code)
                    } catch (_: Exception) {}
                }
            }

            // Auto-clear clipboard on connection success ONLY if it still holds the pairing code MIDAR copied
            LaunchedEffect(uiState.wahaSessionStatus) {
                val status = uiState.wahaSessionStatus
                if (status == "CONNECTED" || status == "WORKING") {
                    try {
                        if (clipboardManager.hasPrimaryClip()) {
                            val clip = clipboardManager.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val currentText = clip.getItemAt(0).text?.toString()
                                if (viewModel.shouldClearClipboard(currentText)) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                        clipboardManager.clearPrimaryClip()
                                    } else {
                                        val emptyClip = android.content.ClipData.newPlainText("", "")
                                        clipboardManager.setPrimaryClip(emptyClip)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Lifecycle check: when returning from WhatsApp to MIDAR, poll status immediately
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (uiState.showWahaPairingDialog && (uiState.wahaSessionStatus == "SCAN_QR_CODE" || uiState.pairingCode != null)) {
                    viewModel.checkStatusImmediately()
                }
            }

            val qrBitmap = remember(uiState.wahaQrBase64) {
                uiState.wahaQrBase64?.let { base64Str ->
                    try {
                        var cleanBase64 = base64Str
                        if (cleanBase64.contains(",")) {
                            cleanBase64 = cleanBase64.substringAfter(",")
                        }
                        cleanBase64 = cleanBase64.replace("\\s".toRegex(), "")
                        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            AlertDialog(
                onDismissRequest = viewModel::closeWahaPairingDialog,
                title = {
                    Text(
                        text = "ربط WhatsApp",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                    ) {
                        if (uiState.wahaSessionStatus == "CONNECTED" || uiState.wahaSessionStatus == "WORKING") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = Dimens.Spacing8)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDCFCE7))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(Dimens.Spacing16))
                                Text(
                                    text = "WhatsApp متصل ✓",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF16A34A),
                                    textAlign = TextAlign.Center
                                )
                                if (uiState.wahaConnectedPhone != null) {
                                    Spacer(modifier = Modifier.height(Dimens.Spacing8))
                                    Text(
                                        text = "الرقم المرتبط: ${uiState.wahaConnectedPhone}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(Dimens.Spacing24))
                                Button(
                                    onClick = viewModel::logoutWaha,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("logout_waha_button")
                                ) {
                                    Text("قطع الاتصال بالحساب")
                                }
                            }
                        } else {
                            // Tabs: "كود ربط (لهاتف واحد)" as primary tab, "مسح QR" as secondary
                            TabRow(
                                selectedTabIndex = if (uiState.selectedPairingTab == "CODE") 0 else 1,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = PrimaryIndigo,
                                indicator = {}
                            ) {
                                Tab(
                                    selected = uiState.selectedPairingTab == "CODE",
                                    onClick = { viewModel.selectPairingTab("CODE") },
                                    modifier = Modifier.testTag("code_tab_button")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (uiState.selectedPairingTab == "CODE") PrimaryIndigo else Color.Transparent
                                            )
                                            .padding(vertical = Dimens.Spacing8),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ربط برقم الهاتف (موصى به)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (uiState.selectedPairingTab == "CODE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                                Tab(
                                    selected = uiState.selectedPairingTab == "QR",
                                    onClick = { viewModel.selectPairingTab("QR") },
                                    modifier = Modifier.testTag("qr_tab_button")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (uiState.selectedPairingTab == "QR") PrimaryIndigo else Color.Transparent
                                            )
                                            .padding(vertical = Dimens.Spacing8),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "مسح QR (بجهاز آخر)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (uiState.selectedPairingTab == "QR") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            if (uiState.selectedPairingTab == "QR") {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                                ) {
                                    Text(
                                        text = "افتح WhatsApp ← الأجهزة المرتبطة ← ربط جهاز، ثم امسح الكود",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (uiState.isStartingWaha) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = Dimens.Spacing16)
                                        ) {
                                            CircularProgressIndicator(color = PrimaryIndigo)
                                            Spacer(modifier = Modifier.height(Dimens.Spacing12))
                                            Text(
                                                text = "جاري الاتصال بالخادم وتحديث رمز QR...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else if (uiState.wahaPairingError != null) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = Dimens.Spacing8)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(Dimens.Spacing8))
                                            Text(
                                                text = uiState.wahaPairingError!!,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(Dimens.Spacing12))
                                            Button(
                                                onClick = viewModel::startWaha,
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("start_waha_button")
                                            ) {
                                                Text("المحاولة مرة أخرى")
                                            }
                                        }
                                    } else if (qrBitmap != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White)
                                                .padding(Dimens.Spacing16),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = qrBitmap,
                                                contentDescription = "رمز QR لربط واتساب",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(color = PrimaryIndigo)
                                                Spacer(modifier = Modifier.height(Dimens.Spacing8))
                                                Text(
                                                    text = "جاري تجهيز رمز QR...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(Dimens.Spacing8))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = PrimaryIndigo
                                        )
                                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                                        Text(
                                            text = "جاري فحص حالة الاتصال تلقائياً...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.pairingCode == null) {
                                        Text(
                                            text = "اربط WhatsApp بحساب MIDAR لإرسال التنبيهات للطلاب وأولياء الأمور.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = uiState.pairingPhoneNumber,
                                            onValueChange = viewModel::onPairingPhoneNumberChange,
                                            label = { Text("رقم الهاتف (مثال: 201012345678)") },
                                            placeholder = { Text("201012345678") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("pairing_phone_input"),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )

                                        uiState.pairingCodeError?.let { errorText ->
                                            Text(
                                                text = errorText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Button(
                                            onClick = viewModel::requestPairingCode,
                                            enabled = !uiState.isRequestingPairingCode,
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("request_code_button")
                                        ) {
                                            if (uiState.isRequestingPairingCode) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                            } else {
                                                Text("الحصول على كود الربط", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else if (uiState.isPairingCodeExpired) {
                                        // Expired state
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(Dimens.Spacing16),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "انتهت صلاحية الكود",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(Dimens.Spacing4))
                                                Text(
                                                    text = "يرجى إنشاء كود جديد والمحاولة مرة أخرى.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(Dimens.Spacing8))

                                        Button(
                                            onClick = viewModel::requestNewPairingCode,
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("request_new_code_button")
                                        ) {
                                            Text("إنشاء كود جديد", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        // Active Pairing Code Card
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = Dimens.Spacing16, horizontal = Dimens.Spacing20),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "كود الربط",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(Dimens.Spacing8))
                                                Text(
                                                    text = uiState.pairingCode ?: "",
                                                    style = MaterialTheme.typography.displaySmall.copy(
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 4.sp
                                                    ),
                                                    color = PrimaryIndigo,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.testTag("pairing_code_display")
                                                )
                                                Spacer(modifier = Modifier.height(Dimens.Spacing8))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
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
                                                            text = "تم نسخ الكود تلقائيًا",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = Color(0xFF047857)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(Dimens.Spacing4))

                                        // Concise Instructions
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(Dimens.Spacing12),
                                                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing4)
                                            ) {
                                                Text(
                                                    text = "افتح WhatsApp ← الأجهزة المرتبطة ← ربط جهاز، ثم الصق الكود.",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Right,
                                                    lineHeight = 22.sp,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(Dimens.Spacing4))

                                        // Primary "Open WhatsApp" Button
                                        Button(
                                            onClick = {
                                                val pm = context.packageManager
                                                val intent = pm.getLaunchIntentForPackage("com.whatsapp")
                                                    ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
                                                if (intent != null) {
                                                    context.startActivity(intent)
                                                } else {
                                                    Toast.makeText(context, "WhatsApp غير مثبت على هذا الجهاز.", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF25D366),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("open_whatsapp_button")
                                        ) {
                                            Text(
                                                text = "فتح WhatsApp",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        // Secondary "Copy Code" Button
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                val code = uiState.pairingCode ?: ""
                                                val clip = android.content.ClipData.newPlainText("WhatsApp Pairing Code", code)
                                                clipboardManager.setPrimaryClip(clip)
                                                viewModel.recordPairingCodeCopied(code)
                                                Toast.makeText(context, "تم نسخ الكود بنجاح ✓", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("copy_code_button")
                                        ) {
                                            Text(
                                                text = "نسخ الكود",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = PrimaryIndigo
                                            )
                                        }

                                        // Polling Progress Indicator
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = Dimens.Spacing4)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = PrimaryIndigo
                                            )
                                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                                            Text(
                                                text = "جاري انتظار إدخال الكود وتأكيد الاتصال...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Action when needed: "إنشاء كود جديد"
                                        TextButton(
                                            onClick = viewModel::requestNewPairingCode,
                                            modifier = Modifier.testTag("request_new_code_button")
                                        ) {
                                            Text("إنشاء كود جديد", style = MaterialTheme.typography.bodySmall, color = PrimaryIndigo)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::closeWahaPairingDialog) {
                        Text("إغلاق", style = MaterialTheme.typography.labelLarge)
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun SettingMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconBg: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Spacing20, vertical = Dimens.Spacing14)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(Dimens.Spacing16))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.Spacing8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(Dimens.Spacing8))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
