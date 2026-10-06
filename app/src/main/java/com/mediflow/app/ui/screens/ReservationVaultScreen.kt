package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Screen 11 — Request Vault / Order History (Variant B / Figma 8)
 *
 * This is the "go back to the pharmacy that has it" screen, so the two things
 * a patient scans for are status and the counter code. Active cards carry the
 * QR action because that is the only state where the code still opens a door;
 * completed and expired rows keep their codes hidden, which stops someone
 * presenting a dead reference at the counter.
 *
 * Rendered inside the dashboard's scrolling Column, so it is plain [Column]
 * content — a nested LazyColumn inside a vertically scrolled parent throws at
 * layout time.
 *
 * [sampleReservations] is UI-phase data; the history, expiry and per-medicine
 * fill state come from the backend.
 *
 * WCAG 2.1 AA: badges never rely on colour alone (icon + words), tabs and cards
 * are 44dp+ targets, and every control carries a semantic label.
 */

private enum class VaultStatus(val label: String) {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    EXPIRED("Expired")
}

private enum class VaultFilter(val label: String, val status: VaultStatus?) {
    ALL("All", null),
    ACTIVE("Active", VaultStatus.ACTIVE),
    COMPLETED("Completed", VaultStatus.COMPLETED),
    EXPIRED("Expired", VaultStatus.EXPIRED)
}

private data class Reservation(
    val reference: String,
    val pharmacyName: String,
    val area: String,
    val dateLabel: String,
    val medicines: List<String>,
    val status: VaultStatus,
    val expiresLabel: String? = null
)

private val sampleReservations = listOf(
    Reservation(
        reference = "4821",
        pharmacyName = "City Pharmacy Negombo",
        area = "Negombo",
        dateLabel = "Today · 05 Oct",
        medicines = listOf("Paracetamol 500mg", "Amoxicillin 250mg"),
        status = VaultStatus.ACTIVE,
        expiresLabel = "Expires 6:00 PM today"
    ),
    Reservation(
        reference = "4712",
        pharmacyName = "Serendib Pharmacy",
        area = "Kurunegala",
        dateLabel = "Tue · 30 Sep",
        medicines = listOf("Metformin 500mg"),
        status = VaultStatus.ACTIVE,
        expiresLabel = "Expires in 4 hours"
    ),
    Reservation(
        reference = "4498",
        pharmacyName = "HealthMart Wattala",
        area = "Wattala",
        dateLabel = "Fri · 26 Sep",
        medicines = listOf("Amlodipine 5mg", "Atorvastatin 10mg", "Vitamin D3 60k IU"),
        status = VaultStatus.COMPLETED
    ),
    Reservation(
        reference = "4310",
        pharmacyName = "Sevana Pharmacy Kimseriya",
        area = "Negombo",
        dateLabel = "Mon · 22 Sep",
        medicines = listOf("Cetirizine 10mg"),
        status = VaultStatus.EXPIRED
    ),
    Reservation(
        reference = "4155",
        pharmacyName = "Crown Chemists",
        area = "Nikitasthena",
        dateLabel = "Thu · 18 Sep",
        medicines = listOf("Losartan 50mg", "Bisoprolol 2.5mg", "Aspirin 75mg", "Omeprazole 20mg"),
        status = VaultStatus.COMPLETED
    )
)

@Composable
fun ReservationVaultTabContent(
    onShowQr: (reference: String, medicineLabel: String, pharmacyName: String) -> Unit,
    onStartSearch: () -> Unit
) {
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val trimmed = query.trim()
    val matching = sampleReservations.filter { reservation ->
        (filter.status == null || reservation.status == filter.status) &&
            (trimmed.isEmpty() ||
                reservation.pharmacyName.contains(trimmed, ignoreCase = true) ||
                reservation.area.contains(trimmed, ignoreCase = true) ||
                reservation.medicines.any { it.contains(trimmed, ignoreCase = true) })
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        VaultHeader(
            count = matching.size,
            searchOpen = searchOpen,
            query = query,
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
            onQueryChange = { query = it }
        )

        StatusTabs(selected = filter, onSelect = { filter = it })

        Spacer(Modifier.height(14.dp))

        if (matching.isEmpty()) {
            VaultEmptyState(
                filtered = trimmed.isNotEmpty() || filter != VaultFilter.ALL,
                onStartSearch = onStartSearch
            )
        } else {
            matching.forEach { reservation ->
                ReservationCard(
                    reservation = reservation,
                    onShowQr = {
                        onShowQr(
                            reservation.reference,
                            reservation.medicines.firstOrNull().orEmpty(),
                            reservation.pharmacyName
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header + search
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VaultHeader(count: Int, searchOpen: Boolean, query: String, onToggleSearch: () -> Unit, onQueryChange: (String) -> Unit) {
    val searchFocus = remember { FocusRequester() }

    // Revealing the field and leaving it unfocused means the patient taps the
    // magnifier, then taps again just to start typing.
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(150)
            runCatching { searchFocus.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My Reservations",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Text(
                text = "$count saved",
                fontSize = 13.sp,
                color = SlateMedium
            )
        }
        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = if (searchOpen) "Close reservation search" else "Search reservations" }
        ) {
            Icon(
                imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = null,
                tint = if (searchOpen) ForestEmerald else DarkSlate,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    if (searchOpen) {
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Pharmacy, area or medicine", fontSize = 14.sp, color = SlateLight) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, null, tint = SlateLight, modifier = Modifier.size(20.dp))
            },
            textStyle = TextStyle(fontSize = 15.sp, color = DarkSlate),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestEmerald,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = ForestEmerald,
                cursorColor = ForestEmerald,
                focusedTextColor = DarkSlate,
                unfocusedTextColor = DarkSlate
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocus)
                .semantics { contentDescription = "Reservation search field" }
        )
    }
    Spacer(Modifier.height(12.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Status tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusTabs(selected: VaultFilter, onSelect: (VaultFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VaultFilter.entries.forEach { option ->
            StatusTab(
                label = option.label,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun StatusTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label tab, ${if (selected) "selected" else "not selected"}" }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) ForestEmerald else SlateMedium
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) ForestEmerald else Color.Transparent)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reservation card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReservationCard(reservation: Reservation, onShowQr: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${reservation.status.label} reservation at ${reservation.pharmacyName}, reference ${reservation.reference}"
            },
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                PrescriptionThumb(reservation.medicines.size)
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reservation.pharmacyName,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${reservation.dateLabel} · ${reservation.area}",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = medicineSummary(reservation.medicines),
                        fontSize = 13.5.sp,
                        color = SlateMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                StatusBadge(reservation.status)
            }

            if (reservation.expiresLabel != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassTop,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = reservation.expiresLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AmberTextDeep
                    )
                }
            }

            // Only an active hold has a code worth printing; an expired one would
            // be refused at the counter and only causes confusion.
            if (reservation.status == VaultStatus.ACTIVE) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onShowQr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .semantics {
                            contentDescription =
                                "Show counter QR code ${reservation.reference} for ${reservation.pharmacyName}"
                        },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestEmerald,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "Show Counter QR Code",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun medicineSummary(medicines: List<String>): String {
    if (medicines.isEmpty()) return "No medicines recorded"
    val shown = medicines.take(2).joinToString(", ")
    val more = medicines.size - 2
    return if (more > 0) "$shown  +$more more" else shown
}

/** Darker amber for text — [AmberWarning] alone fails AA on a light fill. */
private val AmberTextDeep = Color(0xFF92400E)

@Composable
private fun StatusBadge(status: VaultStatus) {
    val (label, icon, background, content) = when (status) {
        VaultStatus.ACTIVE -> Quad(
            "Active",
            Icons.Rounded.PublishedWithChanges,
            SuccessGreen.copy(alpha = 0.12f),
            SuccessGreen
        )
        VaultStatus.COMPLETED -> Quad(
            "Completed",
            Icons.Rounded.CheckCircle,
            SlateLight.copy(alpha = 0.20f),
            SlateMedium
        )
        VaultStatus.EXPIRED -> Quad(
            "Expired",
            Icons.Rounded.History,
            ErrorRed.copy(alpha = 0.10f),
            ErrorRed
        )
    }
    Surface(
        modifier = Modifier.semantics { contentDescription = label },
        shape = CircleShape,
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Stub of the attached slip. A real thumbnail is decoded from storage by the
 * backend; here the line count is the only thing worth faking faithfully.
 */
@Composable
private fun PrescriptionThumb(lineCount: Int) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(56.dp)
            .clip(shape)
            .background(Color(0xFFF1F5F9))
            .border(1.dp, BorderColor, shape)
            .semantics { contentDescription = "Prescription thumbnail, $lineCount medicines" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            val lines = lineCount.coerceIn(2, 4)
            val gap = size.height / (lines + 1)
            drawRect(
                color = ForestEmerald.copy(alpha = 0.55f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.55f, 2.5.dp.toPx())
            )
            repeat(lines) { index ->
                val y = gap * (index + 1) + 3.dp.toPx()
                val widthFraction = if (index == lines - 1) 0.6f else 0.88f
                drawRect(
                    color = SlateLight.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                    size = androidx.compose.ui.geometry.Size(size.width * widthFraction, 2.dp.toPx())
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VaultEmptyState(filtered: Boolean, onStartSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyIllustration()
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (filtered) "No reservations match" else "No reservations yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (filtered) {
                "Try another status tab, or clear the search."
            } else {
                "Reservations you make appear here with their counter codes."
            },
            fontSize = 13.5.sp,
            color = SlateMedium,
            textAlign = TextAlign.Center
        )
        if (!filtered) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStartSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .semantics { contentDescription = "Start searching for medicines" },
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Start Searching",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyIllustration() {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(Color(0xFFF1F5F9))
            .semantics { contentDescription = "Illustration of an empty reservation vault" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
            contentDescription = null,
            tint = SlateLight,
            modifier = Modifier.size(44.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .clip(CircleShape)
                .background(ForestEmerald.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
