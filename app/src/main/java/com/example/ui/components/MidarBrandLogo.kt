package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MidarBlue
import com.example.ui.theme.MidarCyan
import com.example.ui.theme.MidarGreen
import com.example.ui.theme.MidarNavy

/**
 * Renders the exact MIDAR brand icon with graduation cap, student head, M-body, and 3 green growth bars.
 */
@Composable
fun MidarLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Colors & Gradients exactly from reference logo
        val capColor = Color(0xFF0C2B6A)
        val studentHeadGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF28B0FF), Color(0xFF165DF6))
        )
        val leftShoulderGradient = Brush.linearGradient(
            colors = listOf(Color(0xFF1E60FF), Color(0xFF093198)),
            start = Offset(0f, 0f),
            end = Offset(w * 0.45f, h * 0.8f)
        )
        val rightShoulderGradient = Brush.linearGradient(
            colors = listOf(Color(0xFF2CB9F8), Color(0xFF1A65FF)),
            start = Offset(w * 0.4f, 0f),
            end = Offset(w, h * 0.7f)
        )
        val greenBarsGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF00E59D), Color(0xFF00B27A))
        )

        // 1. Graduation Cap (Mortarboard)
        val capCenterX = w * 0.50f
        val capCenterY = h * 0.15f
        val capHalfW = w * 0.27f
        val capHalfH = h * 0.095f

        val capPath = Path().apply {
            moveTo(capCenterX, capCenterY - capHalfH)
            lineTo(capCenterX + capHalfW, capCenterY)
            lineTo(capCenterX, capCenterY + capHalfH)
            lineTo(capCenterX - capHalfW, capCenterY)
            close()
        }
        drawPath(capPath, color = capColor)

        // Cap center button
        drawCircle(
            color = Color(0xFF2670E8),
            radius = w * 0.024f,
            center = Offset(capCenterX, capCenterY)
        )

        // Cap right tassel
        val tasselPath = Path().apply {
            moveTo(capCenterX, capCenterY)
            lineTo(capCenterX + capHalfW * 0.82f, capCenterY + capHalfH * 0.85f)
            lineTo(capCenterX + capHalfW * 0.82f, capCenterY + capHalfH * 1.6f)
        }
        drawPath(
            tasselPath,
            color = capColor,
            style = Stroke(width = w * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = capColor,
            radius = w * 0.032f,
            center = Offset(capCenterX + capHalfW * 0.82f, capCenterY + capHalfH * 1.6f)
        )

        // 2. Student Head Circle
        val headRadius = w * 0.115f
        val headCenterY = h * 0.275f
        drawCircle(
            brush = studentHeadGradient,
            radius = headRadius,
            center = Offset(capCenterX, headCenterY)
        )

        // 3. M-Shape Body - Left Arm / Wing
        val leftArmPath = Path().apply {
            moveTo(w * 0.33f, h * 0.38f)
            lineTo(w * 0.16f, h * 0.29f)
            cubicTo(w * 0.08f, h * 0.25f, w * 0.04f, h * 0.33f, w * 0.04f, h * 0.44f)
            lineTo(w * 0.04f, h * 0.68f)
            cubicTo(w * 0.04f, h * 0.75f, w * 0.10f, h * 0.77f, w * 0.17f, h * 0.77f)
            lineTo(w * 0.23f, h * 0.77f)
            cubicTo(w * 0.29f, h * 0.77f, w * 0.32f, h * 0.71f, w * 0.32f, h * 0.64f)
            lineTo(w * 0.32f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.66f)
            lineTo(w * 0.50f, h * 0.49f)
            close()
        }
        drawPath(leftArmPath, brush = leftShoulderGradient)

        // 4. M-Shape Body - Right Arm / Wing
        val rightArmPath = Path().apply {
            moveTo(w * 0.50f, h * 0.49f)
            lineTo(w * 0.50f, h * 0.66f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.68f, h * 0.38f)
            lineTo(w * 0.84f, h * 0.29f)
            cubicTo(w * 0.92f, h * 0.25f, w * 0.96f, h * 0.33f, w * 0.96f, h * 0.44f)
            lineTo(w * 0.96f, h * 0.52f)
            lineTo(w * 0.68f, h * 0.68f)
            lineTo(w * 0.50f, h * 0.49f)
            close()
        }
        drawPath(rightArmPath, brush = rightShoulderGradient)

        // 5. 3 Emerald Green Growth Bars (rising chart on bottom-right)
        val barWidth = w * 0.088f
        val barCorner = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Bar 1 (Shortest - leftmost of the 3)
        drawRoundRect(
            brush = greenBarsGradient,
            topLeft = Offset(w * 0.535f, h * 0.64f),
            size = Size(barWidth, h * 0.22f),
            cornerRadius = barCorner
        )

        // Bar 2 (Medium - middle)
        drawRoundRect(
            brush = greenBarsGradient,
            topLeft = Offset(w * 0.675f, h * 0.53f),
            size = Size(barWidth, h * 0.33f),
            cornerRadius = barCorner
        )

        // Bar 3 (Tallest - rightmost)
        drawRoundRect(
            brush = greenBarsGradient,
            topLeft = Offset(w * 0.815f, h * 0.42f),
            size = Size(barWidth, h * 0.44f),
            cornerRadius = barCorner
        )
    }
}

/**
 * Exact MIDAR Logo with MIDAR text (custom green triangle A) and tagline.
 */
@Composable
fun MidarFullLogo(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    iconSize: Dp = 82.dp,
    showTagline: Boolean = true,
    taglineText: String = "كل طلابك في مكان واحد"
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MidarLogoIcon(size = iconSize)

        Spacer(modifier = Modifier.height(10.dp))

        // Typography: MIDAR with triangle in A
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val titleColor = if (isDarkTheme) Color.White else Color(0xFF0B1B3D)

            Text(
                text = "MID",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = titleColor
            )

            // Letter 'A' with green inner triangle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 0.5.dp)
            ) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = titleColor
                )
                Canvas(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(y = 2.5.dp)
                ) {
                    val path = Path().apply {
                        moveTo(size.width * 0.5f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, color = Color(0xFF00E59D), style = Fill)
                }
            }

            Text(
                text = "R",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = titleColor
            )
        }

        if (showTagline) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = taglineText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Elegant, subtle MIDAR brand watermark for screen headers and backgrounds.
 * Ultra-low opacity (3-5%) so it never distracts or interferes with content or readability.
 */
@Composable
fun MidarWatermarkBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.03f,
    size: Dp = 220.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
    ) {
        MidarLogoIcon(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            size = size
        )
    }
}
