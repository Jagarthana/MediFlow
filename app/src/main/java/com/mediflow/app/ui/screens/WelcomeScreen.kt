package com.mediflow.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mediflow.app.navigation.Screen
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 1 — Welcome / Splash Screen
 *
 * Design Spec:
 * • Full-screen gradient: Forest Emerald (#059669) → Healthcare Blue (#2563EB)
 * • Centered logo + "MediFlow" (display 32sp bold) + tagline
 * • Smooth fade-in + scale-up animation on launch
 * • Auto-navigates to Login after 2.5 seconds via LaunchedEffect
 * • No interactive elements (pure splash)
 *
 * Nielsen Heuristic #8: Aesthetic and Minimalist Design
 * WCAG 2.1 AA: White text on gradient background (4.5:1+ contrast ratio)
 */
@Composable
fun WelcomeScreen(navController: NavController) {

    // ── Animation state ───────────────────────────────────────────────────────
    val logoAlpha    = remember { Animatable(0f) }
    val logoScale    = remember { Animatable(0.7f) }
    val textAlpha    = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    // ── Auto-navigate + animate on launch ────────────────────────────────────
    LaunchedEffect(Unit) {
        // Logo fades in + scales up
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }

        // App name fades in after 400ms
        delay(400)
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )

        // Tagline fades in after 700ms
        delay(300)
        taglineAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )

        // Navigate after full 2500ms delay
        delay(1100)
        navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Welcome.route) { inclusive = true }
        }
    }

    // ── Full-screen gradient background ──────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientStart,      // Forest Emerald
                        GradientMid,        // Sky Blue
                        GradientEnd         // Healthcare Blue
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Decorative blurred circles (glassmorphism accents) ────────────
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-80).dp, y = (-200).dp)
                .alpha(0.15f)
                .clip(CircleShape)
                .background(Color.White)
                .blur(60.dp)
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 120.dp, y = 200.dp)
                .alpha(0.10f)
                .clip(CircleShape)
                .background(Color.White)
                .blur(50.dp)
        )

        // ── Center content column ─────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        ) {

            // ── Logo Container ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MedicalServices,
                        contentDescription = "MediFlow Logo — Medical cross with pill",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── App Name: "MediFlow" ──────────────────────────────────────
            // "Medi" in white, "Flow" in amber accent
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 40.sp,
                            letterSpacing = (-1).sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.25f),
                                offset = Offset(0f, 4f),
                                blurRadius = 8f
                            )
                        )
                    ) { append("Medi") }
                    withStyle(
                        style = SpanStyle(
                            color = AmberWarning,
                            fontWeight = FontWeight.Black,
                            fontSize = 40.sp,
                            letterSpacing = (-1).sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.25f),
                                offset = Offset(0f, 4f),
                                blurRadius = 8f
                            )
                        )
                    ) { append("Flow") }
                },
                modifier = Modifier.alpha(textAlpha.value),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Tagline ───────────────────────────────────────────────────
            Text(
                text = "Your Medicine, Your Pharmacy, Your Time",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                ),
                modifier = Modifier
                    .alpha(taglineAlpha.value)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(64.dp))

            // ── Loading dots ──────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(taglineAlpha.value)
            ) {
                repeat(3) { index ->
                    val dotAlpha by produceState(initialValue = 0.4f) {
                        while (true) {
                            delay(index * 200L)
                            value = 1f
                            delay(600)
                            value = 0.4f
                            delay(600 - index * 200L)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(dotAlpha)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        // ── Bottom: Sri Lanka / SLMC badge ───────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(taglineAlpha.value)
        ) {
            Text(
                text = "🇱🇰  Designed for Sri Lanka",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}
