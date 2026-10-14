package com.mediflow.app.ui.screens

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.components.PrescriptionThumbnail
import com.mediflow.app.ui.theme.*

/**
 * Screen 7 — Cart & Verification (Variant B / Figma 3C)
 *
 * Layout, top to bottom:
 *  • Top Bar   — back arrow + "Reservation Review"
 *  • Reserved  — "Items Reserved" card; each row has name, strength, qty, remove
 *  • Slip      — thumbnail, file name and a green verified mark
 *  • Legal     — SLMC disclaimer checkbox, mandatory
 *  • Bottom    — "Select Pickup Pharmacy", grey until the disclaimer is accepted
 *
 * The two seeded rows are the Figma sample list; anything carried in from
 * Screen 6 is prepended. Both disappear when the backend owns the cart.
 *
 * WCAG 2.1 AA: the whole disclaimer row is a single 48dp+ tap target, and the
 * disabled CTA keeps its label legible rather than fading to near-invisible.
 */

private const val SLIP_FILE_NAME = "rx_photo_001.jpg"

private data class ReservedItem(
    val name: String,
    val detail: String,
    val quantity: Int
)

private val sampleReserved = listOf(
    ReservedItem("Paracetamol 500mg", "Tablet · Panadol", 2),
    ReservedItem("Amoxicillin 500mg", "Capsule", 1)
)

@Composable
fun ReservationReviewScreen(
    prescriptionAttached: Boolean,
    incomingLabel: String?,
    incomingVariant: String?,
    onBack: () -> Unit,
    onPickPharmacy: (summaryLabel: String) -> Unit
) {
    val items = remember {
        mutableStateListOf<ReservedItem>().apply {
            if (!incomingLabel.isNullOrBlank()) {
                add(ReservedItem(incomingLabel, incomingVariant.orEmpty(), 1))
            }
            addAll(sampleReserved)
        }
    }
    var disclaimerAccepted by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LightBackground,
        topBar = { ReviewTopBar(onBack = onBack) },
        bottomBar = {
            ReviewFooter(
                enabled = disclaimerAccepted && items.isNotEmpty(),
                onSelectPharmacy = {
                    onPickPharmacy(items.firstOrNull()?.name.orEmpty())
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            ReservedItemsCard(
                items = items,
                onRemove = { item -> items.remove(item) }
            )

            Spacer(Modifier.height(14.dp))

            SlipVerificationCard(attached = prescriptionAttached)

            Spacer(Modifier.height(14.dp))

            DisclaimerCard(
                accepted = disclaimerAccepted,
                onToggle = { disclaimerAccepted = !disclaimerAccepted }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewTopBar(onBack: () -> Unit) {
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
                .semantics { contentDescription = "Back to search" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = DarkSlate,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Reservation Review",
            style = MaterialTheme.typography.titleLarge.copy(
                color = DarkSlate,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Items reserved
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReservedItemsCard(
    items: List<ReservedItem>,
    onRemove: (ReservedItem) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Items reserved, ${items.size} medicines" },
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items Reserved",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate,
                    modifier = Modifier.weight(1f)
                )
                CountChip(count = items.size)
            }

            if (items.isEmpty()) {
                Text(
                    text = "Nothing reserved yet. Go back and search for a medicine.",
                    fontSize = 13.5.sp,
                    color = SlateMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                items.forEachIndexed { index, item ->
                    ReservedItemRow(item = item, onRemove = { onRemove(item) })
                    if (index != items.lastIndex) {
                        HorizontalDivider(color = BorderColor, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(count: Int) {
    Surface(
        shape = CircleShape,
        color = MintGreen.copy(alpha = 0.16f)
    ) {
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ForestEmerald,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ReservedItemRow(item: ReservedItem, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )
            if (item.detail.isNotBlank()) {
                Text(
                    text = item.detail,
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
            }
        }
        Text(
            text = "Qty: ${item.quantity}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ForestEmerald
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .padding(start = 6.dp)
                .semantics { contentDescription = "Remove ${item.name}" }
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = SlateMedium,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ErrorRed.copy(alpha = 0.10f))
                    .padding(4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slip verification
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlipVerificationCard(attached: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Prescription attachment status" },
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, if (attached) MintGreen.copy(alpha = 0.45f) else BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (attached) {
                PrescriptionThumbnail(modifier = Modifier.size(width = 56.dp, height = 68.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Attached Slip: $SLIP_FILE_NAME",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkSlate
                    )
                    Text(
                        text = "Encrypted with AES-256",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Prescription verified",
                    tint = SuccessGreen,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(shape)
                        .background(LightBackground)
                        .border(1.dp, BorderColor, shape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = SlateLight,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No slip attached",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkSlate
                    )
                    Text(
                        text = "The pharmacist may ask for the prescription at pickup",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legal disclaimer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisclaimerCard(accepted: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = "Prescription disclaimer checkbox" },
        shape = shape,
        color = if (accepted) MintGreen.copy(alpha = 0.07f) else SurfaceWhite,
        border = BorderStroke(
            1.dp,
            if (accepted) ForestEmerald else BorderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ForestEmerald,
                    checkmarkColor = Color.White,
                    uncheckedColor = SlateMedium
                )
            )
            Column(modifier = Modifier.weight(1f).padding(top = 9.dp)) {
                Text(
                    text = "I confirm that this prescription is issued by a registered " +
                        "Sri Lankan Medical Council doctor.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = DarkSlate,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Required",
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (accepted) ForestEmerald else ErrorRed
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewFooter(enabled: Boolean, onSelectPharmacy: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = SurfaceWhite,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = onSelectPharmacy,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Select pickup pharmacy" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.White,
                    disabledContainerColor = BorderColor,
                    disabledContentColor = SlateMedium
                )
            ) {
                Text(
                    text = "Select Pickup Pharmacy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
