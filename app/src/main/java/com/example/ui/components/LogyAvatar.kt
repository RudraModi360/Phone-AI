package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LogyAvatar(
    modifier: Modifier = Modifier.size(36.dp),
    isThinking: Boolean = false
) {
    // Elegant pulsing / hover animation if thinking
    val transition = rememberInfiniteTransition(label = "logy_anim")
    
    val animatedScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val animatedEyeSquish by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeSquish"
    )

    val scale = if (isThinking) animatedScale else 1.0f
    val eyeSquish = if (isThinking) animatedEyeSquish else 1.0f

    Box(
        modifier = modifier
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val width = size.width
            val height = size.height

            // Scale factor to animate whole head
            val currentScale = scale
            val cx = width / 2f
            val cy = height / 2f

            // Ear ring parameters
            val earRadius = width * 0.12f
            val lEarX = width * 0.20f
            val rEarX = width * 0.80f
            val earY = height * 0.16f

            // 1. Draw ears (The loops on top)
            // Left Google Red loop
            drawCircle(
                color = Color(0xFFEA4335),
                radius = earRadius * currentScale,
                center = Offset(lEarX, earY),
                style = Stroke(width = width * 0.05f)
            )

            // Right Google Green loop
            drawCircle(
                color = Color(0xFF34A853),
                radius = earRadius * currentScale,
                center = Offset(rEarX, earY),
                style = Stroke(width = width * 0.05f)
            )

            // Inner dark holes for the ears to look hollow/ring-like
            drawCircle(
                color = Color(0xFF1E1F22).copy(alpha = 0.4f),
                radius = earRadius * 0.45f * currentScale,
                center = Offset(lEarX, earY)
            )
            drawCircle(
                color = Color(0xFF1E1F22).copy(alpha = 0.4f),
                radius = earRadius * 0.45f * currentScale,
                center = Offset(rEarX, earY)
            )

            // 2. Draw Outer Shield/Border of head (Google Yellow)
            val headOuterSize = width * 0.70f * currentScale
            val headOuterLeft = cx - headOuterSize / 2f
            val headOuterTop = cy - headOuterSize / 2f + (height * 0.05f)

            drawRoundRect(
                color = Color(0xFFFBBC05), // Google Yellow frame
                topLeft = Offset(headOuterLeft, headOuterTop),
                size = Size(headOuterSize, headOuterSize),
                cornerRadius = CornerRadius(headOuterSize * 0.35f),
                style = Stroke(width = width * 0.06f)
            )

            // Sub-stroke to create double-bevel highlight
            drawRoundRect(
                color = Color(0xFFF79C00), // border shadow
                topLeft = Offset(headOuterLeft + width * 0.01f, headOuterTop + width * 0.01f),
                size = Size(headOuterSize - width * 0.02f, headOuterSize - width * 0.02f),
                cornerRadius = CornerRadius(headOuterSize * 0.35f),
                style = Stroke(width = width * 0.015f)
            )

            // 3. Draw Inner Face Plate (Google Blue)
            val facePlateSize = headOuterSize - width * 0.10f
            val facePlateLeft = cx - facePlateSize / 2f
            val facePlateTop = headOuterTop + (headOuterSize - facePlateSize) / 2f

            drawRoundRect(
                color = Color(0xFF4285F4), // Google Blue Inner face
                topLeft = Offset(facePlateLeft, facePlateTop),
                size = Size(facePlateSize, facePlateSize),
                cornerRadius = CornerRadius(facePlateSize * 0.32f)
            )

            // Accent reflection gradient-like light sheen highlight on upper plate
            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(facePlateLeft + width * 0.02f, facePlateTop + width * 0.02f),
                size = Size(facePlateSize - width * 0.04f, facePlateSize * 0.4f),
                cornerRadius = CornerRadius(facePlateSize * 0.15f)
            )

            // 4. Draw Eyes: Grey with white outer rims
            val eyeRadius = facePlateSize * 0.11f
            val eyeSpacing = facePlateSize * 0.23f
            val eyeY = facePlateTop + facePlateSize * 0.44f

            val leftEyeX = cx - eyeSpacing
            val rightEyeX = cx + eyeSpacing

            // Left Eye Ring
            drawCircle(
                color = Color.White,
                radius = eyeRadius,
                center = Offset(leftEyeX, eyeY),
                style = Stroke(width = width * 0.025f)
            )
            // Left Eye center pupil (dark metallic grey)
            drawRoundRect(
                color = Color(0xFF3F424A),
                topLeft = Offset(leftEyeX - eyeRadius * 0.7f, eyeY - eyeRadius * 0.7f * eyeSquish),
                size = Size(eyeRadius * 1.4f, eyeRadius * 1.4f * eyeSquish),
                cornerRadius = CornerRadius(eyeRadius * 0.6f)
            )

            // Right Eye Ring
            drawCircle(
                color = Color.White,
                radius = eyeRadius,
                center = Offset(rightEyeX, eyeY),
                style = Stroke(width = width * 0.025f)
            )
            // Right Eye center pupil
            drawRoundRect(
                color = Color(0xFF3F424A),
                topLeft = Offset(rightEyeX - eyeRadius * 0.7f, eyeY - eyeRadius * 0.7f * eyeSquish),
                size = Size(eyeRadius * 1.4f, eyeRadius * 1.4f * eyeSquish),
                cornerRadius = CornerRadius(eyeRadius * 0.6f)
            )

            // 5. Draw Smile (Smiley Arc matching uploaded)
            val smileWidth = facePlateSize * 0.32f
            val smileHeight = facePlateSize * 0.16f
            val smileLeft = cx - smileWidth / 2f
            val smileTop = facePlateTop + facePlateSize * 0.52f

            drawArc(
                color = Color(0xFF1E1F22),
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(smileLeft, smileTop),
                size = Size(smileWidth, smileHeight),
                style = Stroke(
                    width = width * 0.06f,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
