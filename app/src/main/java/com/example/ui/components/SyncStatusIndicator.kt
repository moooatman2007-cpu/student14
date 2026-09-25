package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.sync.SyncState
import com.example.data.sync.SyncStatus

@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isVisible = syncStatus.state == SyncState.OFFLINE ||
            syncStatus.state == SyncState.FAILED ||
            (syncStatus.state == SyncState.SYNCING && syncStatus.pendingCount > 0)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val (bgColor, contentColor, icon) = when (syncStatus.state) {
            SyncState.OFFLINE -> Triple(
                Color(0xFFFFF3E0),
                Color(0xFFE65100),
                Icons.Default.CloudOff
            )
            SyncState.FAILED -> Triple(
                Color(0xFFFFEBEE),
                Color(0xFFC62828),
                Icons.Default.ErrorOutline
            )
            SyncState.SYNCING -> Triple(
                Color(0xFFE3F2FD),
                Color(0xFF1565C0),
                Icons.Default.Sync
            )
            SyncState.SYNCED -> Triple(
                Color(0xFFE8F5E9),
                Color(0xFF2E7D32),
                Icons.Default.CloudDone
            )
            SyncState.IDLE -> Triple(
                Color.Transparent,
                Color.Transparent,
                Icons.Default.Sync
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("sync_status_indicator")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Sync Status",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncStatus.message,
                        color = contentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("sync_status_text")
                    )
                }

                if (syncStatus.state == SyncState.FAILED) {
                    OutlinedButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("sync_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "إعادة المحاولة",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
