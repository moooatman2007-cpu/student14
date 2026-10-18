package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.Student
import com.example.ui.theme.Dimens
import com.example.ui.theme.PrimaryIndigo
import com.example.util.BarcodeGenerator
import com.example.util.BarcodePdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudentQrDialog(
    student: Student,
    gradeName: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val qrContent = student.studentCode.ifBlank { "ST-000" }

    // Generate Compose ImageBitmap for high-speed, crisp display
    val qrImageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = qrContent) {
        value = withContext(Dispatchers.Default) {
            BarcodeGenerator.generateQRCodeImageBitmap(qrContent, 600)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(Dimens.Spacing16)
                .testTag("student_qr_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Spacing20),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "رمز QR للطالبة",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("student_qr_dialog_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing8))

                // Student Name and Grade
                Text(
                    text = student.fullName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryIndigo,
                    textAlign = TextAlign.Center
                )

                if (!gradeName.isNullOrBlank()) {
                    Text(
                        text = gradeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing16))

                // QR Code Display Box
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(Dimens.Spacing12),
                    contentAlignment = Alignment.Center
                ) {
                    val currentBitmap = qrImageBitmap
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap,
                            contentDescription = "رمز QR ${student.fullName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_qr_image")
                        )
                    } else {
                        CircularProgressIndicator(
                            color = PrimaryIndigo,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing12))

                // Student Code Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryIndigo.copy(alpha = 0.1f),
                    modifier = Modifier.padding(horizontal = Dimens.Spacing8)
                ) {
                    Text(
                        text = "كود الطالبة: $qrContent",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryIndigo,
                        modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing6)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing24))

                // Actions: Save / Download Image & Print
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                ) {
                    // Download / Save to Gallery Button
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    val cardBitmap = withContext(Dispatchers.Default) {
                                        BarcodeGenerator.generateStudentQrCardBitmap(
                                            student = student,
                                            gradeName = gradeName
                                        )
                                    }
                                    val result = withContext(Dispatchers.IO) {
                                        BarcodeGenerator.saveBitmapToGallery(
                                            context = context,
                                            bitmap = cardBitmap,
                                            fileName = student.studentCode.ifBlank { student.fullName }
                                        )
                                    }
                                    isSaving = false
                                    if (result.isSuccess) {
                                        Toast.makeText(
                                            context,
                                            "تم حفظ بطاقة رمز QR في مجلد الصور بنجاح ✅",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        val err = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ غير متوقع"
                                        Toast.makeText(
                                            context,
                                            "فشل حفظ الصورة: $err",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    isSaving = false
                                    Toast.makeText(context, "فشل حفظ الصورة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_qr_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري الحفظ...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ الصورة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Print Button
                    OutlinedButton(
                        onClick = {
                            try {
                                BarcodePdfExporter.printStudentQr(
                                    context = context,
                                    student = student,
                                    gradeName = gradeName
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل بدء الطباعة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("print_qr_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طباعة QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.Spacing8))

                // Share Button
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val cardBitmap = withContext(Dispatchers.Default) {
                                BarcodeGenerator.generateStudentQrCardBitmap(
                                    student = student,
                                    gradeName = gradeName
                                )
                            }
                            BarcodeGenerator.shareBitmap(
                                context = context,
                                bitmap = cardBitmap,
                                title = "رمز QR للطالبة ${student.fullName}",
                                studentName = student.fullName
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_qr_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة بطاقة QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
