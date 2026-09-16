package com.forcusflow.lifestream.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AppHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LifeStreamTheme.colors
    val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Status bar mimic
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeStr,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "5G 98%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
        }

        // Screen Title & Subtitle
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = colors.textSecondary
        )
    }
}
