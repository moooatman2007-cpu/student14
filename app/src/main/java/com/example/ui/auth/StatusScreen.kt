package com.example.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MidarFullLogo
import com.example.ui.theme.MidarBlue

@Composable
fun StatusScreen(
    status: String,
    onLogout: () -> Unit
) {
    BackHandler {
        onLogout()
    }

    val context = LocalContext.current
    val message = when (status) {
        "pending" -> "تم إنشاء حسابك بنجاح، وحسابك الآن قيد المراجعة من قِبل إدارة مدار. سيتم تفعيل حسابك قريبًا لتتمكن من استخدام التطبيق بالكامل."
        "suspended" -> "حسابك موقوف حالياً من قبل إدارة مدار. يرجى التواصل معنا للاستفسار."
        "rejected" -> "لم تتم الموافقة على طلب تفعيل حسابك من قِبل إدارة مدار."
        else -> "حالة حسابك غير معروفة حالياً."
    }
    
    val title = when (status) {
        "pending" -> "حسابك قيد المراجعة"
        "suspended" -> "الحساب موقوف"
        "rejected" -> "تم رفض الحساب"
        else -> "حالة الحساب"
    }

    val (icon, iconTint, iconBg) = when (status) {
        "pending" -> Triple(Icons.Outlined.HourglassEmpty, Color(0xFFF59E0B), Color(0xFFFEF3C7))
        "suspended" -> Triple(Icons.Outlined.Block, Color(0xFFEF4444), Color(0xFFFEE2E2))
        "rejected" -> Triple(Icons.Outlined.Cancel, Color(0xFFDC2626), Color(0xFFFEE2E2))
        else -> Triple(Icons.Outlined.Info, MidarBlue, Color(0xFFE0F2FE))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MidarFullLogo(
                iconSize = 64.dp,
                showTagline = false,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (status == "pending" || status == "suspended") {
                Button(
                    onClick = {
                        val messageBody = if (status == "pending") {
                            "السلام عليكم، أريد تفعيل حساب المدرس في تطبيق مدار."
                        } else {
                            "السلام عليكم، أريد الاستفسار عن حالة حساب المدرس في تطبيق مدار."
                        }
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/201040129414?text=" + Uri.encode(messageBody))
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("status_whatsapp_contact_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "تواصل مع الإدارة عبر واتساب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("status_logout_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تسجيل الخروج",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
