package com.nuclearboy.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 开屏动画 — 2秒标语展示后跳转主页。
 */
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        android.util.Log.e("NuclearBoy", "[Splash] 开屏动画开始")
        alpha.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
        delay(1000)
        alpha.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        android.util.Log.e("NuclearBoy", "[Splash] 开屏动画结束 → 跳转主页")
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF08090B), Color(0xFF0D1117), Color(0xFF08090B))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "新时代  新青年  新作为",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 6.sp,
            modifier = Modifier.alpha(alpha.value).padding(horizontal = 24.dp),
        )
    }
}
