package com.example.noticereader.feature.transaction.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus

/**
 * A small coloured status label (§10.5): green SUCCESS, amber PENDING, red FAILED,
 * grey PARSING_ERROR.
 */
@Composable
fun StatusPill(status: TransactionStatus, modifier: Modifier = Modifier) {
    val (background, text) = colorsFor(status)
    Text(
        text = status.label(),
        color = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun TransactionStatus.label(): String = when (this) {
    TransactionStatus.PENDING -> "PENDING"
    TransactionStatus.SUCCESS -> "SUCCESS"
    TransactionStatus.FAILED -> "FAILED"
    TransactionStatus.PARSING_ERROR -> "PARSE ERROR"
}

/** Returns background and text colour for the pill. */
private fun colorsFor(status: TransactionStatus): Pair<Color, Color> = when (status) {
    TransactionStatus.SUCCESS -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
    TransactionStatus.PENDING -> Color(0xFFFFE0B2) to Color(0xFFE65100)
    TransactionStatus.FAILED -> Color(0xFFFFCDD2) to Color(0xFFB71C1C)
    TransactionStatus.PARSING_ERROR -> Color(0xFFE0E0E0) to Color(0xFF424242)
}
