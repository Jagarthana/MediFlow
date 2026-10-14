package com.mediflow.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationItem
import com.mediflow.app.ui.components.QrCode
import com.mediflow.app.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 10 — Confirmation Modal (Variant B / Figma 6-7)
 *
 * One deliberate decision before stock is held: what is being reserved, at which branch,
 * and what it costs. Confirm writes the reservation and only then does the sheet show a
 * reference — nothing on this screen exists before the backend has agreed to the hold.
 *
 * There is no SMS step. Phone Auth needs the paid Blaze plan and the demo accounts live on
 * synthetic mailboxes, so a six-digit code here could only ever be theatre; the button
 * below is the confirmation instead.
 *
 * WCAG 2.1 AA: the expiring state pairs its amber with an icon and the word "soon"; the
 * totals row is read as one labelled value, not colour.
 */

/** Below two hours the hold is treated as urgent and turns amber. */
private const val URGENT_SECONDS = 2 * 3600

/** The hold window the reservation endpoint grants, used only for the pre-confirm caption. */
private const val HOLD_HOURS = 24

private enum class ConfirmStep { FORM, SUCCESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationConfirmationSheet(
    lines: List<ReservationItem>,
    pharmacyName: String,
    unfilledLabels: List<String>,
    patientName: String,
    hasSlip: Boolean,
    onDismiss: () -> Unit,
    onConfirm: suspend () -> Result<Reservation>,
    onOpenQr: (reference: String, medicineLabel: String, pharmacyName: String) -> Unit
) {
    // skipPartiallyExpanded, or the sheet stops with the primary button below
    // the bottom edge of the screen and the patient cannot confirm at all.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ConfirmStep.FORM) }
    var reservation by remember { mutableStateOf<Reservation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isReserving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        sheetState = sheetState,
        dragHandle = { ConfirmDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            when (step) {
                ConfirmStep.FORM -> FormStep(
                    lines = lines,
                    unfilledLabels = unfilledLabels,
                    pharmacyName = pharmacyName,
                    patientName = patientName,
                    hasSlip = hasSlip,
                    error = error,
                    isReserving = isReserving,
                    onConfirm = {
                        if (!isReserving) {
                            scope.launch {
                                isReserving = true
                                error = null
                                val outcome = onConfirm()
                                isReserving = false
                                outcome.fold(
                                    onSuccess = { created ->
                                        reservation = created
                                        step = ConfirmStep.SUCCESS
                                    },
                                    onFailure = { throwable ->
                                        error = when (throwable) {
                                            is com.mediflow.app.data.remote.InsufficientStockException ->
                                                "Only ${throwable.available} of ${throwable.medicineName} " +
                                                    "left at $pharmacyName."
                                            else -> throwable.message
                                                ?: "Couldn't reach Firebase. Check your connection and try again."
                                        }
                                    }
                                )
                            }
                        }
                    },
                    onCancel = onDismiss
                )

                ConfirmStep.SUCCESS -> {
                    val created = reservation
                    if (created == null) {
                        onDismiss()
                    } else {
                        SuccessStep(
                            reservation = created,
                            pharmacyName = pharmacyName,
                            onShowQr = {
                                onOpenQr(
                                    created.reference,
                                    created.items.firstOrNull()?.title.orEmpty(),
                                    pharmacyName
                                )
                            },
                            onDone = onDismiss
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drag handle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfirmDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SlateLight.copy(alpha = 0.45f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — the basket and the ask
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormStep(
    lines: List<ReservationItem>,
    unfilledLabels: List<String>,
    pharmacyName: String,
    patientName: String,
    hasSlip: Boolean,
    error: String?,
    isReserving: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Text(
        text = "Confirm Reservation",
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkSlate
    )
    Spacer(Modifier.height(16.dp))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Reserving ${lines.size} medicines at $pharmacyName"
            },
        shape = RoundedCornerShape(14.dp),
        color = LightBackground,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            lines.forEach { item ->
                ReservedLine(item)
                HorizontalDivider(color = BorderColor, thickness = 1.dp)
            }
            SummaryRow(icon = Icons.Rounded.Storefront, label = "Pharmacy", value = pharmacyName)
            SummaryRow(
                icon = Icons.Rounded.Person,
                label = "Name",
                value = patientName.ifBlank { "Not set on your profile" }
            )
            SummaryRow(
                icon = Icons.Rounded.Description,
                label = "Slip",
                value = if (hasSlip) "Attached" else "Bring the paper prescription"
            )
        }
    }

    if (unfilledLabels.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Not available here: ${unfilledLabels.joinToString()}" },
            shape = RoundedCornerShape(12.dp),
            color = AmberWarning.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PriorityHigh,
                    contentDescription = null,
                    tint = AmberTextDark,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        text = "Not in stock here",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AmberTextDark
                    )
                    Text(
                        text = unfilledLabels.joinToString(),
                        fontSize = 12.5.sp,
                        color = DarkSlate,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = "Estimate  ${formatLKR(lines.sumOf { it.lineTotalLKR })} · held for $HOLD_HOURS hours",
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        color = SlateMedium
    )

    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = BorderColor, thickness = 1.dp)
    Spacer(Modifier.height(16.dp))

    Text(
        text = "What happens next",
        fontSize = 15.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkSlate
    )
    Spacer(Modifier.height(8.dp))
    NextStep("The branch holds these packs under your name.")
    NextStep("You get a counter reference and a $HOLD_HOURS hour window to collect.")
    NextStep("Show the code at the desk; the pharmacist closes it out.")

    error?.let { message ->
        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Error: $message" },
            shape = RoundedCornerShape(12.dp),
            color = ErrorRed.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f))
        ) {
            Text(
                text = message,
                fontSize = 13.5.sp,
                color = ErrorRed,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onConfirm,
        enabled = !isReserving && lines.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Confirm reservation at $pharmacyName" },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ForestEmerald,
            contentColor = Color.White,
            disabledContainerColor = ForestEmerald.copy(alpha = 0.55f),
            disabledContentColor = Color.White
        )
    ) {
        if (isReserving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Reserving…",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = "Confirm Reservation",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    TextButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = "Cancel the reservation" }
    ) {
        Text(
            text = "Cancel",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = SlateMedium
        )
    }
}

@Composable
private fun ReservedLine(item: ReservationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Medication,
            contentDescription = null,
            tint = ForestEmerald,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DarkSlate
            )
            if (item.form.isNotBlank()) {
                Text(text = item.form, fontSize = 12.sp, color = SlateMedium)
            }
        }
        Surface(
            shape = CircleShape,
            color = ForestEmerald.copy(alpha = 0.12f),
            modifier = Modifier.semantics { contentDescription = "Quantity ${item.quantity}" }
        ) {
            Text(
                text = "× ${item.quantity}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = ForestEmerald,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Text(
            text = "  ${formatLKR(item.lineTotalLKR)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkSlate,
            modifier = Modifier.width(74.dp)
        )
    }
}

@Composable
private fun NextStep(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "· ", fontSize = 13.5.sp, color = ForestEmerald, fontWeight = FontWeight.Bold)
        Text(text = text, fontSize = 13.5.sp, color = SlateMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ForestEmerald,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$label:",
            fontSize = 13.5.sp,
            color = SlateMedium,
            modifier = Modifier.width(66.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = DarkSlate,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatLKR(amount: Double): String = "LKR %,.0f".format(amount)

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — success, counted down from the real expiry
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessStep(
    reservation: Reservation,
    pharmacyName: String,
    onShowQr: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var addedToCalendar by remember(reservation.reservationId) { mutableStateOf(false) }
    var calendarMissing by remember(reservation.reservationId) { mutableStateOf(false) }
    var remaining by remember(reservation.reservationId) {
        mutableIntStateOf(((reservation.expiresAtMillis - System.currentTimeMillis()) / 1000).toInt())
    }
    LaunchedEffect(reservation.reservationId) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(6.dp))
        CheckmarkBurst()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Reservation Confirmed!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Show reference ${reservation.reference} at $pharmacyName.\n" +
                "It holds your medicines until ${formatDateTime(reservation.expiresAtMillis)}.",
            fontSize = 13.5.sp,
            color = SlateMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
        ExpiryClock(remainingSeconds = remaining, expiresAtMillis = reservation.expiresAtMillis)

        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier
                .width(150.dp)
                .height(150.dp)
                .semantics { contentDescription = "Preview of counter QR code ${reservation.reference}" },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            QrCode(value = reservation.reference, modifier = Modifier.fillMaxSize().padding(8.dp))
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onShowQr,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "Show counter QR code full screen" },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ForestEmerald,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Show QR Code",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(6.dp))
        if (addedToCalendar) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Added to your calendar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SuccessGreen
                )
            }
        } else {
            TextButton(
                onClick = {
                    addedToCalendar = addToCalendar(context, reservation)
                    if (!addedToCalendar) calendarMissing = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .semantics { contentDescription = "Add the pickup to your calendar" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Event,
                    contentDescription = null,
                    tint = SlateMedium,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add to Calendar",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateMedium
                )
            }
            if (calendarMissing) {
                Text(
                    text = "No calendar app accepted the pickup.",
                    fontSize = 12.5.sp,
                    color = ErrorRed
                )
            }
        }

        TextButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics { contentDescription = "Close the confirmation" }
        ) {
            Text(
                text = "Done",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = SlateMedium
            )
        }
    }
}

@Composable
private fun ExpiryClock(remainingSeconds: Int, expiresAtMillis: Long) {
    val urgent = remainingSeconds < URGENT_SECONDS
    val tint = if (urgent) AmberWarning else ForestEmerald
    val textTint = if (urgent) AmberTextDark else SlateMedium
    val window = HOLD_HOURS * 3600

    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (urgent) {
                    "Reservation expires soon, ${formatClock(remainingSeconds)} left"
                } else {
                    "Reservation expires in ${formatClock(remainingSeconds)}"
                }
            },
        shape = shape,
        color = if (urgent) AmberWarning.copy(alpha = 0.14f) else ForestEmerald.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CountdownRing(fraction = remainingSeconds.toFloat() / window, color = tint)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (urgent) "Expiring soon" else "Expires in",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textTint,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = formatClock(remainingSeconds),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
            Icon(
                imageVector = Icons.Rounded.HourglassTop,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Ring that empties as the hold runs down — the clock you can read at a glance. */
@Composable
private fun CountdownRing(fraction: Float, color: Color) {
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(modifier = Modifier.size(34.dp)) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            style = stroke
        )
    }
}

private fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(safe / 3600, (safe % 3600) / 60, safe % 60)
}

private fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/**
 * Hands the pickup window to the device calendar app. ACTION_INSERT needs no permission
 * because the calendar app itself confirms the entry, so a false return means no app
 * on this phone accepted the intent.
 */
private fun addToCalendar(context: Context, reservation: Reservation): Boolean {
    val title = "Collect " + reservation.items.joinToString { it.title }
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, reservation.pharmacyName)
        .putExtra(CalendarContract.Events.DESCRIPTION, "MediFlow reference ${reservation.reference}")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, reservation.createdAtMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, reservation.expiresAtMillis)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** Darker amber for text — [AmberWarning] alone fails AA on a light fill. */
private val AmberTextDark = Color(0xFF92400E)

// ─────────────────────────────────────────────────────────────────────────────
// Success animation
// ─────────────────────────────────────────────────────────────────────────────

/** Green ring sweeps closed, then the tick draws itself. */
@Composable
private fun CheckmarkBurst() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 650))
    }
    val value = progress.value
    Canvas(modifier = Modifier.size(84.dp)) {
        val radius = size.minDimension / 2
        drawCircle(color = SuccessGreen.copy(alpha = 0.12f), radius = radius)
        drawArc(
            color = SuccessGreen,
            startAngle = -90f,
            sweepAngle = 360f * value.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
        val tick = ((value - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (tick > 0f) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.52f)
            val corner = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.66f)
            val end = androidx.compose.ui.geometry.Offset(size.width * 0.71f, size.height * 0.36f)
            if (tick <= 0.45f) {
                val t = tick / 0.45f
                drawLine(
                    color = SuccessGreen,
                    start = start,
                    end = androidx.compose.ui.geometry.lerp(start, corner, t),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
            } else {
                drawLine(color = SuccessGreen, start = start, end = corner,
                    strokeWidth = stroke.width, cap = stroke.cap)
                val t = ((tick - 0.45f) / 0.55f).coerceIn(0f, 1f)
                drawLine(
                    color = SuccessGreen,
                    start = corner,
                    end = androidx.compose.ui.geometry.lerp(corner, end, t),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
            }
        }
    }
}
