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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediflow.app.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 13 — Pharmacist Order Review & Dual-View Workstation (Variant B / Figma P2)
 *
 * The Orders tab of the pharmacist host. Two views share one pane: the live
 * queue (tap a card) and the per-order review (prescription preview with
 * pinch-zoom/pan, patient, shelf location, per-item verification checkboxes,
 * then Reject or Mark as Ready for Pickup). The desktop Alt+A / Alt+R
 * shortcuts become the two big bottom buttons here.
 *
 * Everything is local simulated state, in line with the UI-only prototype:
 * the queue "loads" behind a short delay, a new order arrives on a timer with
 * a ToneGenerator chime and a badge on the notification bell, and status
 * writes go nowhere yet (`// TODO(backend)` territory).
 */

// ─────────────────────────────────────────────────────────────────────────────
// Order model + UI-phase sample data
// ─────────────────────────────────────────────────────────────────────────────

internal enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    READY("Ready for Pickup"),
    REJECTED("Rejected")
}

internal data class OrderItem(val name: String, val strength: String, val quantity: Int)

internal data class PharmOrder(
    val ref: Int,
    val patient: String,
    val placedAt: String,
    val shelf: String,
    val items: List<OrderItem>,
    val status: OrderStatus = OrderStatus.PENDING
) {
    val itemCountLabel: String get() = "${items.size} ${if (items.size == 1) "item" else "items"}"
}

private val seedOrders = listOf(
    PharmOrder(
        ref = 1024,
        patient = "Kavindi Perera",
        placedAt = "Today, 10:42 AM",
        shelf = "Cabinet B-12",
        items = listOf(
            OrderItem("Paracetamol", "500 mg", 2),
            OrderItem("Amoxicillin", "250 mg", 1)
        )
    ),
    PharmOrder(
        ref = 1025,
        patient = "Nadeesha Silva",
        placedAt = "Today, 11:05 AM",
        shelf = "Cabinet A-04",
        items = listOf(
            OrderItem("Cetirizine", "10 mg", 1),
            OrderItem("Oral Rehydration Salts", "20.5 g · Sachet", 3),
            OrderItem("Omeprazole", "20 mg", 2)
        )
    ),
    PharmOrder(
        ref = 1026,
        patient = "Ruwan Jayasuriya",
        placedAt = "Today, 11:31 AM",
        shelf = "Shelf C-03",
        items = listOf(
            OrderItem("Salbutamol", "100 mcg · Inhaler", 1)
        )
    )
)

/** Arrive on a timer so the queue visibly updates while the app is open. */
private val incomingOrders = listOf(
    PharmOrder(
        ref = 1027,
        patient = "Ishara Fernando",
        placedAt = "Just now",
        shelf = "Cabinet D-07",
        items = listOf(
            OrderItem("Metformin", "500 mg", 2),
            OrderItem("Atorvastatin", "20 mg", 1)
        )
    ),
    PharmOrder(
        ref = 1028,
        patient = "Dilani Rodrigo",
        placedAt = "Just now",
        shelf = "Cabinet B-03",
        items = listOf(
            OrderItem("Azithromycin", "500 mg", 1),
            OrderItem("Paracetamol", "650 mg", 2)
        )
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Order book — simulated queue state shared by the queue and review views
// ─────────────────────────────────────────────────────────────────────────────

internal class OrderBook(
    val orders: SnapshotStateList<PharmOrder>,
    val verified: SnapshotStateMap<String, Boolean>,
    val isLoading: MutableState<Boolean>,
    val unread: MutableIntState,
    val snackbar: SnackbarHostState,
    private val chime: ToneGenerator?
) {
    private fun key(order: PharmOrder, index: Int) = "${order.ref}:$index"

    fun isVerified(order: PharmOrder, index: Int): Boolean = verified[key(order, index)] == true

    fun allVerified(order: PharmOrder): Boolean =
        order.items.indices.all { isVerified(order, it) }

    fun verifiedCount(order: PharmOrder): Int =
        order.items.indices.count { isVerified(order, it) }

    fun toggleItem(order: PharmOrder, index: Int) {
        verified[key(order, index)] = !isVerified(order, index)
    }

    fun setStatus(order: PharmOrder, status: OrderStatus) {
        val i = orders.indexOfFirst { it.ref == order.ref }
        if (i >= 0) orders[i] = orders[i].copy(status = status)
    }

    fun playChime() {
        runCatching { chime?.startTone(ToneGenerator.TONE_PROP_ACK, 250) }
    }

    /** Clears the bell badge and returns the alert summary to show. */
    fun reviewAlerts(): String {
        val count = unread.intValue
        unread.intValue = 0
        return if (count == 0) "No new order alerts."
        else "$count new order ${if (count == 1) "alert" else "alerts"} — newest at the top of the queue."
    }
}

@Composable
internal fun rememberOrderBook(snackbarHostState: SnackbarHostState): OrderBook {
    val orders = remember { mutableStateListOf<PharmOrder>() }
    val verified = remember { mutableStateMapOf<String, Boolean>() }
    val isLoading = remember { mutableStateOf(true) }
    val unread = remember { mutableIntStateOf(0) }
    val chime = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85) }.getOrNull()
    }

    DisposableEffect(chime) {
        onDispose { runCatching { chime?.release() } }
    }

    // Simulated socket: initial fetch, then push arrivals while the app is open.
    LaunchedEffect(Unit) {
        delay(900)
        orders.addAll(seedOrders)
        isLoading.value = false
        incomingOrders.forEach { incoming ->
            delay(20_000)
            orders.add(0, incoming)
            unread.intValue += 1
            runCatching { chime?.startTone(ToneGenerator.TONE_PROP_ACK, 250) }
            snackbarHostState.showSnackbar("New order received: REF# ${incoming.ref}")
        }
    }

    return remember(orders, verified, isLoading, unread, snackbarHostState, chime) {
        OrderBook(orders, verified, isLoading, unread, snackbarHostState, chime)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// View 1 — Live Orders Queue
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun OrdersQueuePane(book: OrderBook, onOpenOrder: (Int) -> Unit) {
    if (book.isLoading.value) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = ForestEmerald, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(text = "Loading live orders…", fontSize = 13.5.sp, color = SlateMedium)
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
                    text = "Live Orders Queue (${book.orders.size})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tap an order to review the prescription and verify items.",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
            }
        }

        if (book.orders.isEmpty()) {
            item { EmptyQueueState() }
        } else {
            items(book.orders.toList(), key = { it.ref }) { order ->
                OrderQueueCard(order = order, onOpen = { onOpenOrder(order.ref) })
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
                    "Order REF# ${order.ref}, ${order.patient}, ${order.itemCountLabel}, ${order.status.label}. Tap to review."
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
                    text = "REF# ${order.ref}",
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
        OrderStatus.REJECTED -> ErrorRed to ErrorRed.copy(alpha = 0.12f)
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
internal fun OrderReviewPane(book: OrderBook, order: PharmOrder, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showRejectDialog by remember { mutableStateOf(false) }
    var showFullScreen by remember { mutableStateOf(false) }
    val settled = order.status != OrderStatus.PENDING

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
                text = buildAnnotatedString {
                    pushStyle(SpanStyle(color = SlateMedium, fontWeight = FontWeight.Medium))
                    append("Patient: ")
                    pop()
                    pushStyle(SpanStyle(color = DarkSlate, fontWeight = FontWeight.Bold))
                    append(order.patient)
                    pop()
                },
                contentDescription = "Patient: ${order.patient}"
            )

            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Rounded.Place,
                tint = AmberWarning,
                text = buildAnnotatedString {
                    pushStyle(SpanStyle(color = SlateMedium, fontWeight = FontWeight.Medium))
                    append("Shelf Location: ")
                    pop()
                    pushStyle(SpanStyle(color = DarkSlate, fontWeight = FontWeight.Bold))
                    append(order.shelf)
                    pop()
                },
                contentDescription = "Shelf location ${order.shelf}"
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
                    text = "${book.verifiedCount(order)} of ${order.items.size} verified",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (book.allVerified(order)) SuccessGreen else SlateLight
                )
            }
            Spacer(Modifier.height(8.dp))

            order.items.forEachIndexed { index, item ->
                MedicineVerifyCard(
                    item = item,
                    verified = book.isVerified(order, index),
                    enabled = !settled,
                    onToggle = { book.toggleItem(order, index) }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (settled) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = LightBackground,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        text = "This order was ${order.status.label.lowercase()} — no further action is needed.",
                        fontSize = 12.5.sp,
                        color = SlateMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Order actions ──────────────────────────────────────────────────
        if (!settled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MediFlowPrimaryButton(
                        text = "Mark as Ready for Pickup",
                        icon = Icons.Rounded.CheckCircle,
                        contentDescription =
                            if (book.allVerified(order)) "Mark order REF# ${order.ref} as ready for pickup"
                            else "Verify all medicine items to enable marking ready",
                        enabled = book.allVerified(order),
                        onClick = {
                            book.setStatus(order, OrderStatus.READY)
                            scope.launch {
                                onBack()
                                book.snackbar.showSnackbar(
                                    "Order #REF-${order.ref} is ready for pickup."
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .semantics { contentDescription = "Reject order REF# ${order.ref}" },
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
                book.setStatus(order, OrderStatus.REJECTED)
                scope.launch {
                    onBack()
                    book.snackbar.showSnackbar("Order #REF-${order.ref} was rejected.")
                }
            }
        )
    }

    if (showFullScreen) {
        FullScreenPrescriptionDialog(order = order, onDismiss = { showFullScreen = false })
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                text = "Order #REF-${order.ref} for ${order.patient} will be cancelled and the " +
                    "patient notified. This cannot be undone.",
                fontSize = 13.5.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.semantics { contentDescription = "Confirm rejecting order REF# ${order.ref}" }
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
// Prescription preview — pinch-to-zoom, drag/pan, full-screen viewer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrescriptionPreviewCard(order: PharmOrder, onEnlarge: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Prescription preview for order REF# ${order.ref}" },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ZoomablePrescription(
                order = order,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
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
            .pointerInput(order.ref) {
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
                    "Prescription image for order REF# ${order.ref}. Pinch to zoom, drag to pan."
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        ) {
            PrescriptionArt(order = order)
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

/** Stand-in for the uploaded slip photo until the backend serves real images. */
@Composable
private fun PrescriptionArt(order: PharmOrder) {
    Surface(
        modifier = Modifier.padding(14.dp),
        shape = RoundedCornerShape(6.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Dr. A. Wickramasinghe",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Text(
                text = "MBBS, MD (Gen. Med.) · SLMC Reg. P-4821",
                fontSize = 10.5.sp,
                color = SlateMedium
            )
            Text(
                text = "City Clinic, Negombo · 2026-10-05",
                fontSize = 10.5.sp,
                color = SlateLight
            )
            HorizontalDivider(
                color = BorderColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Patient: ${order.patient}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )
            Spacer(Modifier.height(6.dp))
            Text(text = "℞", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
            Spacer(Modifier.height(4.dp))
            order.items.forEachIndexed { index, item ->
                Text(
                    text = "${index + 1}. ${item.name} ${item.strength} × ${item.quantity}",
                    fontSize = 12.5.sp,
                    color = DarkSlate
                )
                Text(
                    text = "     Sig: as directed, after meals",
                    fontSize = 11.sp,
                    color = SlateMedium,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Column(modifier = Modifier.align(Alignment.End)) {
                Text(
                    text = "A. Wickramasinghe",
                    fontSize = 12.5.sp,
                    color = DarkSlate,
                    fontStyle = FontStyle.Italic
                )
                HorizontalDivider(
                    color = SlateLight,
                    modifier = Modifier.width(130.dp)
                )
            }
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
                    text = "Prescription — REF# ${order.ref}",
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
