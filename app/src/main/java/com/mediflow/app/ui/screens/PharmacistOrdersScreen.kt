package com.mediflow.app.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationItem
import com.mediflow.app.data.model.ReservationStatus
import com.mediflow.app.ui.components.SlipImage
import com.mediflow.app.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Screen 13 — Pharmacist Order Review & Dual-View Workstation (Variant B / Figma P2)
 *
 * The Orders tab of the pharmacist host. Two views share one pane: the live queue and the
 * per-order review (the patient's real photographed slip with pinch-zoom/pan, the basket with
 * per-item verification checkboxes, then Reject, Mark as Ready for Pickup, or hand the bag
 * over). The desktop Alt+A / Alt+R shortcuts become the big bottom buttons here.
 *
 * The queue is a Firestore snapshot listener over this branch's reservations, so a hold placed
 * on a patient's phone arrives here while the pharmacist is looking at the screen. Every button
 * writes that document and answers it with a message in the patient's inbox.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Queue view model
// ─────────────────────────────────────────────────────────────────────────────

internal enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    READY("Ready for Pickup"),
    COLLECTED("Collected"),
    REJECTED("Rejected"),
    EXPIRED("Expired")
}

internal data class OrderItem(val medicineId: String, val name: String, val strength: String, val quantity: Int)

internal data class PharmOrder(
    val reservationId: String,
    val patientUid: String,
    val reference: String,
    val patient: String,
    val placedAt: String,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val slipPath: String?,
    val counterCode: String,
    val totalLabel: String,
    val completedAt: String?,
    val cancelReason: String?
) {
    val itemCountLabel: String get() = "${items.size} ${if (items.size == 1) "item" else "items"}"

    /** The shelf lines the hand-over books off, in the form the repository expects. */
    val shelfLines: List<ReservationItem>
        get() = items.map { line ->
            ReservationItem(
                medicineId = line.medicineId,
                name = line.name,
                strength = line.strength,
                form = "",
                quantity = line.quantity,
                unitPriceLKR = 0.0
            )
        }
}

private val queueStampFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

internal fun Reservation.toPharmOrder(): PharmOrder = PharmOrder(
    reservationId = reservationId,
    patientUid = patientUid,
    reference = reference,
    patient = patientName,
    placedAt = queueStampFormat.format(Date(createdAtMillis)),
    items = items.map { OrderItem(it.medicineId, it.name, it.strength, it.quantity) },
    status = when (displayStatus()) {
        ReservationStatus.RESERVED -> OrderStatus.PENDING
        ReservationStatus.READY -> OrderStatus.READY
        ReservationStatus.COLLECTED -> OrderStatus.COLLECTED
        ReservationStatus.CANCELLED -> OrderStatus.REJECTED
        ReservationStatus.EXPIRED -> OrderStatus.EXPIRED
    },
    slipPath = prescriptionStoragePath,
    counterCode = counterCode,
    totalLabel = "LKR %,.2f".format(totalLKR),
    completedAt = completedAtMillis?.let { queueStampFormat.format(Date(it)) },
    cancelReason = cancelReason?.takeIf { it.isNotBlank() }
)

// ─────────────────────────────────────────────────────────────────────────────
// Per-item verification — the pharmacist's tick-list before a status write
// ─────────────────────────────────────────────────────────────────────────────

internal class VerificationTracker(private val marks: SnapshotStateMap<String, Boolean>) {
    fun isVerified(order: PharmOrder, index: Int): Boolean = marks["${order.reservationId}:$index"] == true

    fun allVerified(order: PharmOrder): Boolean = order.items.indices.all { isVerified(order, it) }

    fun verifiedCount(order: PharmOrder): Int = order.items.indices.count { isVerified(order, it) }

    fun toggleItem(order: PharmOrder, index: Int) {
        val key = "${order.reservationId}:$index"
        marks[key] = marks[key] != true
    }
}

@Composable
internal fun rememberVerificationTracker(): VerificationTracker {
    val marks = remember { mutableStateMapOf<String, Boolean>() }
    return remember(marks) { VerificationTracker(marks) }
}

/** The counter chime, fired by the host when a genuinely new hold reaches the queue. */
@Composable
internal fun rememberOrderChime(): () -> Unit {
    val player = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85) }.getOrNull() }
    DisposableEffect(player) {
        onDispose { runCatching { player?.release() } }
    }
    return remember(player) { { runCatching { player?.startTone(ToneGenerator.TONE_PROP_ACK, 250) } } }
}

// ─────────────────────────────────────────────────────────────────────────────
// View 1 — Live Orders Queue
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun OrdersQueuePane(
    orders: List<PharmOrder>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onOpenOrder: (String) -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = ForestEmerald, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(text = "Opening the branch queue…", fontSize = 13.5.sp, color = SlateMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = "Live Orders Queue (${orders.size})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (loadFailed)
                        "The queue could not be read from Firestore. Check the sign-in, then re-open this tab."
                    else "Tap an order to review the prescription and verify items.",
                    fontSize = 12.5.sp,
                    color = if (loadFailed) ErrorRed else SlateMedium
                )
            }
        }

        if (orders.isEmpty()) {
            item { EmptyQueueState() }
        } else {
            items(orders, key = { it.reservationId }) { order ->
                OrderQueueCard(order = order, onOpen = { onOpenOrder(order.reservationId) })
            }
        }
    }
}

@Composable
private fun EmptyQueueState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ReceiptLong,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "No live orders", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "New reservations will appear here the moment patients confirm them.",
            fontSize = 13.5.sp,
            color = SlateMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OrderQueueCard(order: PharmOrder, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription =
                    "Order ${order.reference}, ${order.patient}, ${order.itemCountLabel}, ${order.status.label}. Tap to review."
            },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.reference,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = order.patient,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(text = order.itemCountLabel, fontSize = 12.sp, color = SlateLight)
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                OrderStatusBadge(status = order.status)
                Spacer(Modifier.height(6.dp))
                Text(text = order.placedAt, fontSize = 11.sp, color = SlateLight)
            }

            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun OrderStatusBadge(status: OrderStatus) {
    val (tint, background) = when (status) {
        OrderStatus.PENDING -> AmberWarning to AmberWarning.copy(alpha = 0.14f)
        OrderStatus.READY -> SuccessGreen to SuccessGreen.copy(alpha = 0.14f)
        OrderStatus.COLLECTED -> HealthcareBlue to HealthcareBlue.copy(alpha = 0.12f)
        OrderStatus.REJECTED -> ErrorRed to ErrorRed.copy(alpha = 0.12f)
        OrderStatus.EXPIRED -> SlateMedium to SlateMedium.copy(alpha = 0.12f)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        modifier = Modifier.semantics { contentDescription = "Status: ${status.label}" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// View 2 — Order review
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun OrderReviewPane(
    order: PharmOrder,
    tracker: VerificationTracker,
    isBusy: Boolean,
    onReady: () -> Unit,
    onReject: () -> Unit,
    onHandOver: () -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var showFullScreen by remember { mutableStateOf(false) }
    val awaitingAction = order.status == OrderStatus.PENDING
    val readyForHandover = order.status == OrderStatus.READY

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            PrescriptionPreviewCard(order = order, onEnlarge = { showFullScreen = true })

            Spacer(Modifier.height(12.dp))
            InfoRow(
                icon = Icons.Rounded.Person,
                tint = HealthcareBlue,
                text = labelled("Patient: ", order.patient),
                contentDescription = "Patient: ${order.patient}"
            )

            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Rounded.Schedule,
                tint = AmberWarning,
                text = labelled("Placed: ", order.placedAt),
                contentDescription = "Placed ${order.placedAt}"
            )

            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Rounded.Payments,
                tint = ForestEmerald,
                text = labelled("Total: ", order.totalLabel),
                contentDescription = "Total ${order.totalLabel}"
            )

            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Rounded.ConfirmationNumber,
                tint = DarkSlate,
                text = labelled("Counter code: ", order.counterCode.ifBlank { "Not issued" }),
                contentDescription = "Counter code ${order.counterCode.ifBlank { "not issued" }}"
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Medicine items — tap to verify",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${tracker.verifiedCount(order)} of ${order.items.size} verified",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tracker.allVerified(order)) SuccessGreen else SlateLight
                )
            }
            Spacer(Modifier.height(8.dp))

            order.items.forEachIndexed { index, item ->
                MedicineVerifyCard(
                    item = item,
                    verified = tracker.isVerified(order, index),
                    enabled = awaitingAction,
                    onToggle = { tracker.toggleItem(order, index) }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (!awaitingAction) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = LightBackground,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        text = when (order.status) {
                            OrderStatus.READY -> "Packed and waiting at the counter. Hand it over to close it out."
                            OrderStatus.COLLECTED ->
                                "Collected${order.completedAt?.let { " at $it" } ?: ""} — booked off the shelf."
                            OrderStatus.REJECTED ->
                                "Rejected${order.cancelReason?.let { " — $it" } ?: ""}. The patient has been notified."
                            OrderStatus.EXPIRED -> "The patient never collected this hold; it lapsed after 24 hours."
                            OrderStatus.PENDING -> ""
                        },
                        fontSize = 12.5.sp,
                        color = SlateMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Order actions ──────────────────────────────────────────────────
        if (awaitingAction || readyForHandover) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (awaitingAction) {
                        MediFlowPrimaryButton(
                            text = "Mark as Ready for Pickup",
                            icon = Icons.Rounded.CheckCircle,
                            contentDescription =
                                if (tracker.allVerified(order)) "Mark order ${order.reference} as ready for pickup"
                                else "Verify all medicine items to enable marking ready",
                            enabled = tracker.allVerified(order) && !isBusy,
                            onClick = onReady
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showRejectDialog = true },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .semantics { contentDescription = "Reject order ${order.reference}" },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, ErrorRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Reject Order",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        MediFlowPrimaryButton(
                            text = "Hand Over — Collected",
                            icon = Icons.Rounded.LocalShipping,
                            contentDescription =
                                "Hand over ${order.reference} to ${order.patient} and book it off the shelf",
                            enabled = !isBusy,
                            onClick = onHandOver
                        )
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        RejectOrderDialog(
            order = order,
            onDismiss = { showRejectDialog = false },
            onConfirm = {
                showRejectDialog = false
                onReject()
            }
        )
    }

    if (showFullScreen) {
        FullScreenPrescriptionDialog(order = order, onDismiss = { showFullScreen = false })
    }
}

private fun labelled(prefix: String, value: String): AnnotatedString = buildAnnotatedString {
    pushStyle(SpanStyle(color = SlateMedium, fontWeight = FontWeight.Medium))
    append(prefix)
    pop()
    pushStyle(SpanStyle(color = DarkSlate, fontWeight = FontWeight.Bold))
    append(value)
    pop()
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    tint: Color,
    text: AnnotatedString,
    contentDescription: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text = text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MedicineVerifyCard(
    item: OrderItem,
    verified: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (verified) SuccessGreen.copy(alpha = 0.06f) else SurfaceWhite,
        border = BorderStroke(1.dp, if (verified) SuccessGreen.copy(alpha = 0.45f) else BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .semantics {
                    contentDescription =
                        "${item.name}, ${item.strength}, quantity ${item.quantity}, " +
                            if (verified) "verified" else "not verified"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = verified,
                onCheckedChange = { if (enabled) onToggle() },
                enabled = enabled,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = "Verify ${item.name} ${item.strength}" },
                colors = CheckboxDefaults.colors(
                    checkedColor = ForestEmerald,
                    checkmarkColor = Color.White,
                    uncheckedColor = SlateLight,
                    disabledCheckedColor = ForestEmerald.copy(alpha = 0.5f),
                    disabledUncheckedColor = SlateLight.copy(alpha = 0.5f)
                )
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.strength} — Quantity: ${item.quantity}",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
            }
            if (verified) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RejectOrderDialog(order: PharmOrder, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = SurfaceWhite,
        titleContentColor = DarkSlate,
        textContentColor = SlateMedium,
        title = {
            Text(
                text = "Reject this order?",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Text(
                text = "Order ${order.reference} for ${order.patient} will be cancelled and the " +
                    "patient notified. This cannot be undone.",
                fontSize = 13.5.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.semantics { contentDescription = "Confirm rejecting order ${order.reference}" }
            ) {
                Text(
                    text = "Reject Order",
                    color = ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = SlateMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Prescription preview — the patient's uploaded slip, pinch-to-zoom and pan
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrescriptionPreviewCard(order: PharmOrder, onEnlarge: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Prescription preview for order ${order.reference}" },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ZoomablePrescription(order = order, modifier = Modifier.fillMaxWidth().height(260.dp))
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onEnlarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics { contentDescription = "Enlarge prescription to full screen" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = null,
                    tint = ForestEmerald,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Enlarge Prescription",
                    color = ForestEmerald,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

@Composable
private fun ZoomablePrescription(order: PharmOrder, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8EEF4))
            .pointerInput(order.reservationId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset = if (scale <= 1.01f) {
                        Offset.Zero
                    } else {
                        Offset(
                            (offset.x + pan.x).coerceIn(-450f, 450f),
                            (offset.y + pan.y).coerceIn(-650f, 650f)
                        )
                    }
                }
            }
            .semantics {
                contentDescription =
                    "Prescription image for order ${order.reference}. Pinch to zoom, drag to pan."
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .fillMaxSize()
        ) {
            SlipImage(
                path = order.slipPath,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                label = "Prescription slip for order ${order.reference}"
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            color = DarkSlate.copy(alpha = 0.72f)
        ) {
            Text(
                text = if (scale > 1.01f) "${(scale * 100).roundToInt()}% · drag to pan"
                       else "Pinch to zoom · drag to pan",
                fontSize = 10.5.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun FullScreenPrescriptionDialog(order: PharmOrder, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSlate.copy(alpha = 0.97f))
        ) {
            ZoomablePrescription(
                order = order,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 64.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prescription — ${order.reference}",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Close full-screen prescription" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
