package com.dvstry.ble.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dvstry.ble.presentation.navigation.Screen

@Composable
fun MainScreen(
    navController: NavController
) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable {
                    navController.navigate(Screen.ANOTHER_SCREEN) {
                        popUpTo(Screen.MAIN_SCREEN) {
                            inclusive = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Click here",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
