package com.mediflow.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 5 — Prescription Slip Capture (Variant B / Figma 3A)
 *
 * Layout, top to bottom:
 *  • Top Bar     — back arrow + "Prescription Slip Capture"
 *  • Viewfinder  — large rounded surface with 4 L-shaped corner brackets,
 *                  amber "Avoid Glare" warning, alignment hint, encryption notice
 *  • Controls    — gallery picker | circular shutter | flash toggle
 *
 * The viewfinder is a stand-in: it renders a dark gradient rather than a live
 * camera feed. Swapping in a CameraX Preview replaces [MockViewfinderSurface]
 * only; the overlays, brackets and controls sit in a Box on top and stay as-is.
 * Likewise [glareDetected] is a fixed flag until frame analysis exists.
 *
 * WCAG 2.1 AA: 48dp+ touch targets, semantic labels on every control
 */

private val ViewfinderTop = Color(0xFF16202E)
private val ViewfinderBottom = Color(0xFF060A12)

@Composable
fun PhotoCaptureScreen(onBack: () -> Unit, onCaptured: () -> Unit) {
    var flashOn by remember { mutableStateOf(false) }
    var glareDetected by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ForceLightSystemBars()

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CaptureTopBar(title = "Prescription Slip Capture", onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                MockViewfinderSurface()
                CornerBrackets()

                if (glareDetected) {
                    GlareWarning(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp)
                    )
                }

                Text(
                    text = "Align prescription within frame",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )

                EncryptionNotice(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }

            CaptureControls(
                flashOn = flashOn,
                onToggleFlash = { flashOn = !flashOn },
                onPickFromGallery = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Gallery picking arrives with the camera pass")
                    }
                },
                onCapture = onCaptured
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Back to dashboard" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewfinder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MockViewfinderSurface() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(ViewfinderTop, ViewfinderBottom)
                )
            )
    )
}

@Composable
private fun CornerBrackets() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Prescription alignment frame" }
    ) {
        val inset = 18.dp.toPx()
        val arm = 28.dp.toPx()
        val stroke = 3.dp.toPx()
        val right = size.width - inset
        val bottom = size.height - inset
        val bracket = Color.White.copy(alpha = 0.92f)

        // Top-left
        drawLine(bracket, Offset(inset, inset), Offset(inset + arm, inset), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(inset, inset), Offset(inset, inset + arm), stroke, StrokeCap.Round)
        // Top-right
        drawLine(bracket, Offset(right, inset), Offset(right - arm, inset), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(right, inset), Offset(right, inset + arm), stroke, StrokeCap.Round)
        // Bottom-left
        drawLine(bracket, Offset(inset, bottom), Offset(inset + arm, bottom), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(inset, bottom), Offset(inset, bottom - arm), stroke, StrokeCap.Round)
        // Bottom-right
        drawLine(bracket, Offset(right, bottom), Offset(right - arm, bottom), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(right, bottom), Offset(right, bottom - arm), stroke, StrokeCap.Round)
    }
}

@Composable
private fun GlareWarning(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = "Warning: avoid glare on the prescription" },
        shape = RoundedCornerShape(20.dp),
        color = AmberWarning.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = AmberWarning,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Avoid Glare",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AmberWarning,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun EncryptionNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Prescription images are encrypted with AES-256" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MintGreen,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "AES-256 Encryption",
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White.copy(alpha = 0.62f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureControls(
    flashOn: Boolean,
    onToggleFlash: () -> Unit,
    onPickFromGallery: () -> Unit,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ControlIconButton(
            icon = Icons.Rounded.PhotoLibrary,
            label = "Choose from gallery",
            tint = Color.White,
            onClick = onPickFromGallery
        )

        ShutterButton(onClick = onCapture)

        ControlIconButton(
            icon = if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
            label = if (flashOn) "Flash on" else "Flash off",
            tint = if (flashOn) AmberWarning else Color.White,
            onClick = onToggleFlash
        )
    }
}

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .semantics { contentDescription = label }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Capture prescription slip" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * MediFlowTheme forces dark status-bar icons while the app runs in light mode,
 * which disappears against this screen's near-black background. Light icons are
 * forced for as long as this screen is composed, then handed back.
 */
@Composable
private fun ForceLightSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }
}
