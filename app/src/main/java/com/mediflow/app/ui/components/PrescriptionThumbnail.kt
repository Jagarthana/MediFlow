package com.mediflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediflow.app.ui.theme.BorderColor
import com.mediflow.app.ui.theme.ForestEmerald
import com.mediflow.app.ui.theme.SlateLight

/**
 * Stand-in for a captured prescription slip.
 *
 * Renders a document-like thumbnail rather than a real photo; once the camera
 * pass lands, the body is replaced by a decoded Bitmap and every caller keeps
 * working unchanged.
 */
@Composable
fun PrescriptionThumbnail(
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, BorderColor, shape)
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SlipLine(widthFraction = 0.55f, color = ForestEmerald, height = 7.dp)
            SlipLine(widthFraction = 0.85f)
            SlipLine(widthFraction = 0.70f)
            SlipLine(widthFraction = 0.80f)
            SlipLine(widthFraction = 0.40f)
        }
    }
}

@Composable
private fun SlipLine(
    widthFraction: Float,
    color: Color = SlateLight.copy(alpha = 0.55f),
    height: Dp = 4.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(color, RoundedCornerShape(2.dp))
    )
}
