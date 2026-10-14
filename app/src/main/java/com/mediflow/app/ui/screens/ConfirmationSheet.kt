package com.mediflow.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.components.QrCodeArt
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 10 — Confirmation Modal (Variant B / Figma 6-7)
 *
 * A bottom sheet that asks for one deliberate decision, so it is built to be
 * read top-down: what is being reserved, how long the hold lives, then proof
 * the patient actually owns the phone the counter will text.
 *
 * The SMS step gates the primary action. That is friction on purpose — the
 * reservation is a claim on a physical shelf of medicine someone else may want,
 * and the code is what stops a stray tap from holding stock.
 *
 * Everything here is local simulated state: no SMS is sent, the clock is a
 * countdown from a fixed 24 hours, and any six digits verify. All three are
 * backend concerns.
 *
 * WCAG 2.1 AA: the expiring state pairs its amber with an icon and the word
 * "soon"; digit boxes, not colour alone, carry the error border.
 */

private const val OTP_LENGTH = 6

/** 24 hours minus a second, so the first rendered frame already ticks. */
private const val EXPIRY_SECONDS = 24 * 3600 - 1

/** Below two hours the hold is treated as urgent and turns amber. */
private const val URGENT_SECONDS = 2 * 3600

private const val DEFAULT_MASKED_MOBILE = "+94 77 XXX XXXX"

private enum class ConfirmStep { FORM, SUCCESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationConfirmationSheet(
    medicineLabel: String,
    quantity: Int,
    pharmacyName: String,
    onDismiss: () -> Unit,
    onOpenQr: (reference: String, medicineLabel: String, pharmacyName: String) -> Unit,
    onConfirmed: (pharmacyName: String) -> Unit,
    maskedMobile: String = DEFAULT_MASKED_MOBILE
) {
    // skipPartiallyExpanded, or the sheet stops with the primary button below
    // the bottom edge of the screen and the patient cannot confirm at all.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ConfirmStep.FORM) }
    var codeSent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var addedToCalendar by remember { mutableStateOf(false) }
    var resendIn by remember { mutableIntStateOf(30) }
    var remaining by remember { mutableIntStateOf(EXPIRY_SECONDS) }
    val focusRequester = remember { FocusRequester() }

    // Stands in for the reference the reservation endpoint will return.
    val reference = remember { "%04d".format((1000..9999).random()) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    // The resend link is disabled while the previous code is still fresh.
    LaunchedEffect(codeSent) {
        while (resendIn > 0) {
            delay(1000)
            resendIn--
        }
    }

    LaunchedEffect(codeSent) {
        if (codeSent) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Full code = verified, so the patient never hunts for the button.
    LaunchedEffect(code) {
        if (code.length == OTP_LENGTH && step == ConfirmStep.FORM) {
            isVerifying = true
            delay(700)
            isVerifying = false
            step = ConfirmStep.SUCCESS
            onConfirmed(pharmacyName)
        }
    }

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
                    medicineLabel = medicineLabel,
                    quantity = quantity,
                    pharmacyName = pharmacyName,
                    maskedMobile = maskedMobile,
                    remainingSeconds = remaining,
                    codeSent = codeSent,
                    code = code,
                    error = error,
                    isVerifying = isVerifying,
                    resendIn = resendIn,
                    focusRequester = focusRequester,
                    onCodeChange = { input ->
                        error = null
                        code = input.filter { it.isDigit() }.take(OTP_LENGTH)
                    },
                    onSendCode = {
                        codeSent = true
                        resendIn = 30
                    },
                    onResend = {
                        code = ""
                        resendIn = 30
                    },
                    onConfirm = {
                        error = when {
                            !codeSent -> "Send the verification code first"
                            code.length < OTP_LENGTH -> "Enter all $OTP_LENGTH digits"
                            else -> null
                        }
                        if (error == null && !isVerifying) {
                            scope.launch {
                                isVerifying = true
                                delay(900)
                                isVerifying = false
                                step = ConfirmStep.SUCCESS
                                onConfirmed(pharmacyName)
                            }
                        }
                    },
                    onCancel = onDismiss
                )

                ConfirmStep.SUCCESS -> SuccessStep(
                    reference = reference,
                    pharmacyName = pharmacyName,
                    addedToCalendar = addedToCalendar,
                    onAddToCalendar = { addedToCalendar = true },
                    onShowQr = { onOpenQr(reference, medicineLabel, pharmacyName) },
                    onDone = onDismiss
                )
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
// Step 1 — summary, expiry clock, SMS verification
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormStep(
    medicineLabel: String,
    quantity: Int,
    pharmacyName: String,
    maskedMobile: String,
    remainingSeconds: Int,
    codeSent: Boolean,
    code: String,
    error: String?,
    isVerifying: Boolean,
    resendIn: Int,
    focusRequester: FocusRequester,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onResend: () -> Unit,
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
                contentDescription =
                    "Reserving $medicineLabel times $quantity at $pharmacyName"
            },
        shape = RoundedCornerShape(14.dp),
        color = LightBackground,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SummaryRow(
                icon = Icons.Rounded.Medication,
                label = "Medicine",
                value = medicineLabel.ifBlank { "Paracetamol 500mg" }
            )
            CountLine(quantity)
            SummaryRow(icon = Icons.Rounded.Storefront, label = "Pharmacy", value = pharmacyName)
            SummaryRow(icon = Icons.Rounded.Schedule, label = "Pickup", value = "Today before 6:00 PM")
        }
    }

    Spacer(Modifier.height(12.dp))
    ExpiryClock(remainingSeconds)

    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = BorderColor, thickness = 1.dp)
    Spacer(Modifier.height(16.dp))

    Text(
        text = "Verify it's you",
        fontSize = 15.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkSlate
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "We'll send a verification code to $maskedMobile",
        fontSize = 13.5.sp,
        color = SlateMedium
    )
    Spacer(Modifier.height(12.dp))

    if (!codeSent) {
        OutlinedButton(
            onClick = onSendCode,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Send verification code to $maskedMobile" },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, ForestEmerald),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = ForestEmerald
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Sms,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Send Code",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        OtpRow(code = code, isError = error != null, onCodeChange = onCodeChange, focusRequester = focusRequester)

        if (error != null) {
            Text(
                text = error,
                fontSize = 12.sp,
                color = ErrorRed,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Didn't get it? ",
                fontSize = 13.sp,
                color = SlateMedium
            )
            if (resendIn > 0) {
                Text(
                    text = "Resend in ${resendIn}s",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateLight
                )
            } else {
                Text(
                    text = "Resend Code",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestEmerald,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onResend)
                        .semantics { contentDescription = "Resend the verification code" }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onConfirm,
        enabled = !isVerifying,
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
        if (isVerifying) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Confirming…",
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

@Composable
private fun CountLine(quantity: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = ForestEmerald.copy(alpha = 0.12f),
            modifier = Modifier.semantics { contentDescription = "Quantity $quantity" }
        ) {
            Text(
                text = "× $quantity",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = ForestEmerald,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ExpiryClock(remainingSeconds: Int) {
    val urgent = remainingSeconds < URGENT_SECONDS
    val tint = if (urgent) AmberWarning else ForestEmerald
    val textTint = if (urgent) AmberTextDark else SlateMedium

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
            CountdownRing(fraction = remainingSeconds / EXPIRY_SECONDS.toFloat(), color = tint)
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

/** Darker amber for text — [AmberWarning] alone fails AA on a light fill. */
private val AmberTextDark = Color(0xFF92400E)

// ─────────────────────────────────────────────────────────────────────────────
// OTP input
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One transparent text field laid over six drawn boxes. Six real fields mean six
 * focus owners competing for the keyboard, which fights the bottom sheet's own
 * focus handling; this way the caret position is just `code.length`.
 */
@Composable
private fun OtpRow(
    code: String,
    isError: Boolean,
    onCodeChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            repeat(OTP_LENGTH) { index ->
                val filled = index < code.length
                val next = index == code.length
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isError -> ErrorRed.copy(alpha = 0.07f)
                                filled -> ForestEmerald.copy(alpha = 0.07f)
                                else -> LightBackground
                            }
                        )
                        .border(
                            width = if (filled || next) 1.6.dp else 1.dp,
                            color = when {
                                isError -> ErrorRed
                                next -> ForestEmerald
                                filled -> ForestEmerald.copy(alpha = 0.55f)
                                else -> BorderColor
                            },
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filled) code[index].toString() else "",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                }
            }
        }

        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 22.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .semantics { contentDescription = "6 digit verification code, ${code.length} entered" }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — success
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessStep(
    reference: String,
    pharmacyName: String,
    addedToCalendar: Boolean,
    onAddToCalendar: () -> Unit,
    onShowQr: () -> Unit,
    onDone: () -> Unit
) {
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
            text = "Show reference $reference at $pharmacyName.\nIt holds your medicines for 24 hours.",
            fontSize = 13.5.sp,
            color = SlateMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier
                .width(150.dp)
                .height(150.dp)
                .semantics { contentDescription = "Preview of counter QR code $reference" },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            QrCodeArt(reference = reference, modifier = Modifier.fillMaxSize().padding(8.dp))
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
                // TODO(backend): CalendarContract ACTION_INSERT with the pickup time
                onClick = onAddToCalendar,
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
