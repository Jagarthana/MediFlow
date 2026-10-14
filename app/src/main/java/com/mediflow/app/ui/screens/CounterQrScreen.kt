package com.mediflow.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mediflow.app.ui.components.QrCodeArt
import com.mediflow.app.ui.theme.*

/**
 * Counter code — the screen a patient turns to face the pharmacist.
 *
 * Two things matter here and nothing else does: the code has to scan through a
 * laminated, glare-heavy counter screen, so the display is driven to full
 * brightness and kept awake; and the reference has to be readable out loud, so
 * it is set large on a dark field.
 *
 * Brightness and the keep-awake flag are restored when the screen is disposed —
 * the patient walks away with the phone as bright as they came.
 */

@Composable
fun CounterQrScreen(reference: String, medicineLabel: String, pharmacyName: String, onBack: () -> Unit) {
    MaxBrightnessWhileVisible()

    val shownReference = reference.ifBlank { "4821" }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
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
                        .semantics { contentDescription = "Close the counter code" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = DarkOnSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Counter Code",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkOnSurface
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hold this up to the counter scanner.",
                fontSize = 14.sp,
                color = SlateLight
            )
            Spacer(Modifier.height(20.dp))

            Surface(
                modifier = Modifier.size(280.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White
            ) {
                QrCodeArt(reference = shownReference, modifier = Modifier.fillMaxSize().padding(14.dp))
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = shownReference,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { contentDescription = "Reservation reference $shownReference" }
            )
            Spacer(Modifier.height(18.dp))

            DetailLine("Medicine", medicineLabel.ifBlank { "Paracetamol 500mg" })
            Spacer(Modifier.height(10.dp))
            DetailLine("Pharmacy", pharmacyName.ifBlank { "City Pharmacy Negombo" })
            Spacer(Modifier.height(10.dp))
            DetailLine("Pickup", "Today before 6:00 PM")

            Spacer(Modifier.height(28.dp))
            Text(
                text = "This code is single use. It expires 24 hours after the reservation.",
                fontSize = 12.5.sp,
                color = SlateLight,
                modifier = Modifier.semantics { contentDescription = "Code validity notice" }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label $value" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = SlateLight,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MaxBrightnessWhileVisible() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val attributes = window.attributes
        val controller = WindowCompat.getInsetsController(window, view)
        val previousBrightness = attributes.screenBrightness
        val wasLightIcons = controller.isAppearanceLightStatusBars
        val hadKeepAwake = attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

        // MediFlowTheme forces dark status icons in light mode, which vanish on
        // this near-black screen.
        controller.isAppearanceLightStatusBars = false
        attributes.screenBrightness = 1f
        attributes.flags = attributes.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        window.attributes = attributes

        onDispose {
            controller.isAppearanceLightStatusBars = wasLightIcons
            attributes.screenBrightness = previousBrightness
            attributes.flags = if (hadKeepAwake) {
                attributes.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            } else {
                attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            }
            window.attributes = attributes
        }
    }
}
