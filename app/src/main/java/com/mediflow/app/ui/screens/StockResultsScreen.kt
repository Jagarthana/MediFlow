package com.mediflow.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.CartLine
import com.mediflow.app.data.CartStore
import com.mediflow.app.data.model.NotificationType
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.ReservationItem
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 9 — Stock Results (Variant B / Figma 5)
 *
 * The answer to "does anybody near me actually have this?". Every row comes from
 * Firestore: the approved branches in the area the patient picked, and each branch's
 * stock row for every medicine in the cart. Coverage is computed against the real
 * shelf count, so "Partial — 1 of 2 items" means one line genuinely cannot be filled
 * here. Results are grouped by that coverage and it is the loudest thing on the card,
 * because a patient decides where to travel.
 *
 * Ratings and distance stay fixed: no rating feed and no patient location exist yet.
 * Everything else on the card is read from the branch document.
 *
 * Sort and filter are one shared piece of state, reachable from the inline bar
 * and the top-bar sheet, so the two controls can never disagree.
 *
 * WCAG 2.1 AA: badges pair colour with an icon, emoji and words, so they never
 * rely on colour alone; every control is a 40dp+ target with a contrast-checked
 * label.
 */

private enum class Match { FULL, PARTIAL, NONE }

/** One branch's ability to fill the current cart. */
private data class StockResultPharmacy(
    val pharmacyId: String,
    val name: String,
    val address: String,
    val areaLabel: String,
    val distanceKm: Double,
    val rating: Float,
    val reviews: Int,
    val match: Match,
    val filled: Int,
    val requested: Int,
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    val openNow: Boolean,
    val roundTheClock: Boolean,
    val delivers: Boolean,
    val items: List<ReservationItem>,
    val unfilledLabels: List<String>
) {
    val totalLKR: Double get() = items.sumOf { it.lineTotalLKR }
}

/** No rating feed and no device location yet, so these are set decoration (per spec). */
private const val DEMO_DISTANCE_KM = 1.2
private const val DEMO_RATING = 4.6f
private const val DEMO_REVIEWS = 218

private fun toResult(
    pharmacy: PharmacyInfo,
    cart: List<CartLine>,
    stockFor: (String) -> StockItem?
): StockResultPharmacy {
    val fillable = cart.mapNotNull { line ->
        val stock = stockFor(line.medicineId) ?: return@mapNotNull null
        if (stock.stockQuantity < line.quantity) null
        else ReservationItem(
            medicineId = line.medicineId,
            name = line.name,
            strength = line.strength,
            form = line.form,
            quantity = line.quantity,
            unitPriceLKR = stock.unitPriceLKR
        )
    }
    val unfilled = cart.mapNotNull { line ->
        if (fillable.any { it.medicineId == line.medicineId }) null else line.title
    }
    return StockResultPharmacy(
        pharmacyId = pharmacy.pharmacyId,
        name = pharmacy.name,
        address = pharmacy.address,
        areaLabel = pharmacy.areaLabel,
        distanceKm = DEMO_DISTANCE_KM,
        rating = DEMO_RATING,
        reviews = DEMO_REVIEWS,
        match = when {
            fillable.isEmpty() -> Match.NONE
            unfilled.isEmpty() -> Match.FULL
            else -> Match.PARTIAL
        },
        filled = fillable.size,
        requested = cart.size,
        phone = pharmacy.phone,
        latitude = pharmacy.latitude,
        longitude = pharmacy.longitude,
        openNow = isOpenNow(pharmacy.openingHours),
        roundTheClock = pharmacy.openingHours.contains("24"),
        delivers = pharmacy.deliveryFeeLKR > 0,
        items = fillable,
        unfilledLabels = unfilled
    )
}

/** "08:00-21:00" and "08:00 AM - 08:00 PM" are both in the catalogue. */
private fun isOpenNow(openingHours: String): Boolean {
    if (openingHours.isBlank()) return false
    if (openingHours.contains("24")) return true
    val times = TIME_IN_RANGE.findAll(openingHours).map { match ->
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val meridiem = match.groupValues[3].uppercase().ifBlank { null }
        when {
            meridiem == "PM" && hour < 12 -> hour + 12
            meridiem == "AM" && hour == 12 -> 0
            else -> hour
        } * 60 + minute
    }.toList()
    if (times.size < 2) return false
    val now = java.time.LocalTime.now()
    val minutes = now.hour * 60 + now.minute
    return minutes in times.first() until times.last()
}

private val TIME_IN_RANGE = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""")

private enum class SortBy(val label: String) {
    DISTANCE("Distance"),
    AVAILABILITY("Availability"),
    RATING("Rating")
}

private enum class Filter(val label: String) {
    OPEN_NOW("Open Now"),
    ALL_HOURS("24 Hours"),
    DELIVERY("Delivery Available")
}

/** Darker amber for text — [AmberWarning] alone fails AA on a light fill. */
private val AmberText = Color(0xFF92400E)

@Composable
fun StockResultsScreen(
    medicineLabel: String,
    area: String,
    onBack: () -> Unit,
    onShowQr: (reference: String, medicineLabel: String, pharmacyName: String) -> Unit
) {
    val context = LocalContext.current
    val cart = CartStore.lines
    var sortBy by remember { mutableStateOf(SortBy.AVAILABILITY) }
    var filters by remember { mutableStateOf(setOf<Filter>()) }
    var showSheet by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<StockResultPharmacy?>(null) }
    val reserved = remember { mutableStateMapOf<String, String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf<List<StockResultPharmacy>?>(null) }
    var loadMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(area, cart) {
        // Once a hold is placed here the list keeps showing it, even though the cart
        // it was built from has been cleared.
        if (reserved.isNotEmpty()) return@LaunchedEffect
        if (cart.isEmpty()) {
            candidates = emptyList()
        } else {
            loadMessage = "Checking stock…"
            val outcome = runCatching {
                val offers = AppGraph.pharmacies.offersFor(cart.map { it.medicineId }, area)
                val byPharmacy = offers.values.flatten().groupBy({ it.pharmacy }, { it })
                byPharmacy.map { (pharmacy, stockOffers) ->
                    val held = stockOffers.mapNotNull { it.stock }
                    toResult(pharmacy, cart) { medicineId ->
                        held.firstOrNull { it.medicineId == medicineId }
                    }
                }
            }
            candidates = outcome.getOrNull().orEmpty()
            loadMessage = if (outcome.isFailure) {
                "Couldn't reach the stock list. Pull back and try again."
            } else null
        }
    }

    val matching = candidates.orEmpty().filter { pharmacy ->
        filters.all { filter ->
            when (filter) {
                Filter.OPEN_NOW -> pharmacy.openNow
                Filter.ALL_HOURS -> pharmacy.roundTheClock
                Filter.DELIVERY -> pharmacy.delivers
            }
        }
    }
    val visible = when (sortBy) {
        SortBy.DISTANCE -> matching.sortedBy { it.distanceKm }
        SortBy.AVAILABILITY -> matching.sortedBy { it.match.rank() }
        SortBy.RATING -> matching.sortedByDescending { it.rating }
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ResultsTopBar(
                onBack = onBack,
                onFilter = { showSheet = true },
                filtersActive = filters.size
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SummaryCard(
                medicineLabel = medicineLabel.ifBlank { cart.firstOrNull()?.title ?: "Reserved medicines" },
                area = area,
                found = visible.size
            )
            SortFilterBar(
                sortBy = sortBy,
                onSortChange = { sortBy = it },
                filters = filters,
                onToggleFilter = { filter ->
                    filters = if (filter in filters) filters - filter else filters + filter
                }
            )

            loadMessage?.let { message ->
                Text(
                    text = message,
                    fontSize = 13.5.sp,
                    color = SlateMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            if (candidates == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ForestEmerald, modifier = Modifier.size(30.dp))
                }
            } else if (visible.isEmpty()) {
                NoResultsCard(
                    medicineCount = cart.size,
                    onClear = { filters = emptySet() }
                )
            } else {
                LazyColumn(
                    // weight(1f), not fillMaxSize(): inside a Column the latter
                    // measures against the whole screen height, which pushes the
                    // last card below the display where no scroll can reach it.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(visible, key = { it.pharmacyId }) { pharmacy ->
                        val reference = reserved[pharmacy.pharmacyId]
                        PharmacyCard(
                            pharmacy = pharmacy,
                            heldReference = reference,
                            onReserve = { confirming = pharmacy },
                            onCall = {
                                if (!openPhone(context, pharmacy.phone)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No phone number for this branch yet.")
                                    }
                                }
                            },
                            onDirections = {
                                if (!openDirections(context, pharmacy)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No map location for this branch yet.")
                                    }
                                }
                            },
                            onRestockAlert = {
                                val uid = AppGraph.currentProfile?.uid
                                if (uid != null) {
                                    scope.launch {
                                        AppGraph.notifications.alertPharmacy(
                                            pharmacyId = pharmacy.pharmacyId,
                                            patientUid = uid,
                                            type = NotificationType.STOCK_SHORTAGE,
                                            title = "Patient wants ${cart.firstOrNull()?.title ?: "restock"}",
                                            body = "Ask ${pharmacy.name} to message you when these are back: " +
                                                cart.joinToString { it.title }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        FilterSheet(
            sortBy = sortBy,
            onSortChange = { sortBy = it },
            filters = filters,
            onToggleFilter = { filter ->
                filters = if (filter in filters) filters - filter else filters + filter
            },
            resultCount = visible.size,
            onDismiss = { showSheet = false }
        )
    }

    confirming?.let { pharmacy ->
        val profile = AppGraph.currentProfile
        ReservationConfirmationSheet(
            lines = pharmacy.items,
            pharmacyName = pharmacy.name,
            unfilledLabels = pharmacy.unfilledLabels,
            patientName = profile?.name.orEmpty(),
            hasSlip = CartStore.slipStoragePath != null,
            onDismiss = { confirming = null },
            onOpenQr = { reference, label, name ->
                confirming = null
                onShowQr(reference, label, name)
            },
            onConfirm = {
                if (profile == null) {
                    Result.failure(Exception("Sign in again to place the reservation."))
                } else {
                    AppGraph.reservations.reserve(
                        patientUid = profile.uid,
                        patientName = profile.name,
                        pharmacyId = pharmacy.pharmacyId,
                        pharmacyName = pharmacy.name,
                        areaLabel = pharmacy.areaLabel,
                        items = pharmacy.items,
                        prescriptionStoragePath = CartStore.slipStoragePath
                    ).onSuccess { reservation ->
                        reserved[pharmacy.pharmacyId] = reservation.reference
                        AppGraph.notifications.tellPatient(
                            patientUid = profile.uid,
                            type = NotificationType.RESERVATION_CONFIRMED,
                            title = "Held at ${pharmacy.name}",
                            body = "Show reference ${reservation.reference} at the counter within 24 hours.",
                            reservationId = reservation.reservationId
                        )
                        CartStore.clear()
                    }
                }
            }
        )
    }
}

private fun openPhone(context: android.content.Context, phone: String): Boolean {
    val digits = phone.filter { it.isDigit() || it == '+' }
    if (digits.isBlank()) return false
    val intent = Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun openDirections(context: android.content.Context, pharmacy: StockResultPharmacy): Boolean {
    if (pharmacy.latitude == 0.0 && pharmacy.longitude == 0.0) return false
    val label = Uri.encode("${pharmacy.name}, ${pharmacy.address}")
    val intent = Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("geo:0,0?q=${pharmacy.latitude},${pharmacy.longitude}($label)")
    )
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun Match.rank(): Int = when (this) {
    Match.FULL -> 0
    Match.PARTIAL -> 1
    Match.NONE -> 2
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultsTopBar(onBack: () -> Unit, onFilter: () -> Unit, filtersActive: Int) {
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
                .semantics { contentDescription = "Back to area selection" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = DarkSlate,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Available Pharmacies",
            style = MaterialTheme.typography.titleLarge.copy(
                color = DarkSlate,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(
                onClick = onFilter,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Open sort and filter sheet" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = if (filtersActive > 0) ForestEmerald else DarkSlate,
                    modifier = Modifier.size(23.dp)
                )
            }
            if (filtersActive > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(16.dp),
                    shape = CircleShape,
                    color = ForestEmerald
                ) {
                    Text(
                        text = "$filtersActive",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary + sort/filter chrome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(medicineLabel: String, area: String, found: Int) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Showing $found pharmacies for $medicineLabel in $area" },
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicineLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$found pharmacies found in $area",
                    fontSize = 13.5.sp,
                    color = SlateMedium
                )
            }
            Icon(
                imageVector = Icons.Rounded.Storefront,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun SortFilterBar(
    sortBy: SortBy,
    onSortChange: (SortBy) -> Unit,
    filters: Set<Filter>,
    onToggleFilter: (Filter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = "Sort results" }
        ) {
            Text(
                text = "Sort by:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SlateMedium
            )
            SortBy.values().forEachIndexed { index, option ->
                if (index > 0) {
                    Text(text = "|", fontSize = 12.sp, color = BorderColor)
                }
                TextButton(
                    onClick = { onSortChange(option) },
                    modifier = Modifier
                        .semantics { contentDescription = "Sort by ${option.label}" },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = option.label,
                        fontSize = 13.sp,
                        fontWeight = if (option == sortBy) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (option == sortBy) ForestEmerald else SlateMedium
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Filter.values().forEach { filter ->
                ResultFilterChip(
                    label = filter.label,
                    selected = filter in filters,
                    onClick = { onToggleFilter(filter) }
                )
            }
        }
    }
}

@Composable
private fun ResultFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(42.dp)
            .semantics { contentDescription = "$label filter" },
        shape = CircleShape,
        color = if (selected) ForestEmerald.copy(alpha = 0.12f) else SurfaceWhite,
        contentColor = if (selected) ForestEmerald else DarkSlate,
        border = BorderStroke(
            1.dp,
            if (selected) ForestEmerald else BorderColor
        ),
        onClick = onClick,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = ForestEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) ForestEmerald else DarkSlate
            )
        }
    }
}

@Composable
private fun NoResultsCard(medicineCount: Int, onClear: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = when {
                    medicineCount == 0 -> "Your reservation is empty."
                    else -> "No approved pharmacy in this area matches those filters."
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = DarkSlate
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    medicineCount == 0 -> "Go back and search for a medicine first."
                    else -> "Widen the search — availability changes through the day."
                },
                fontSize = 13.5.sp,
                color = SlateMedium
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onClear,
                modifier = Modifier.semantics { contentDescription = "Clear all filters" }
            ) {
                Text(
                    text = "Clear filters",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestEmerald
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PharmacyCard(
    pharmacy: StockResultPharmacy,
    heldReference: String?,
    onReserve: () -> Unit,
    onCall: () -> Unit,
    onDirections: () -> Unit,
    onRestockAlert: () -> Unit
) {
    var notified by remember(pharmacy.pharmacyId) { mutableStateOf(false) }
    val held = heldReference != null
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${pharmacy.name}, ${pharmacy.distanceKm} km away" },
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, if (held) ForestEmerald.copy(alpha = 0.55f) else BorderColor),
        shadowElevation = if (held) 3.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MatchBadge(pharmacy)
            Spacer(Modifier.height(12.dp))

            Text(
                text = pharmacy.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (pharmacy.match == Match.NONE) SlateMedium else DarkSlate
            )
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${pharmacy.rating}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkSlate
                )
                Text(
                    text = "  (${pharmacy.reviews} reviews)",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
                Text(
                    text = "  ·  ",
                    fontSize = 12.5.sp,
                    color = SlateLight
                )
                Text(
                    text = "${pharmacy.distanceKm} km away",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ForestEmerald
                )
            }
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = SlateLight,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = pharmacy.address,
                    fontSize = 13.5.sp,
                    color = SlateMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            val tags = serviceTags(pharmacy)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = LightBackground,
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = SlateMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (pharmacy.match == Match.NONE) {
                OutOfStockActions(notified = notified, onNotify = { notified = true; onRestockAlert() })
            } else {
                ActionRow(
                    held = held,
                    onReserve = onReserve,
                    onCall = onCall,
                    onDirections = onDirections
                )
            }

            if (held) {
                Spacer(Modifier.height(12.dp))
                val holdShape = RoundedCornerShape(12.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Held, reference $heldReference" },
                    shape = holdShape,
                    color = MintGreen.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "Held · $heldReference · show it at the counter within 24 hours",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkSlate
                        )
                    }
                }
            }
        }
    }
}

private fun serviceTags(pharmacy: StockResultPharmacy): List<String> = buildList {
    if (pharmacy.roundTheClock) add("Open 24 hours")
    if (pharmacy.delivers) add("Delivery")
    if (pharmacy.openNow && !pharmacy.roundTheClock) add("Open now")
}

@Composable
private fun MatchBadge(pharmacy: StockResultPharmacy) {
    val (label, background, content) = when (pharmacy.match) {
        Match.FULL -> Triple(
            "✅ Full Match — In Stock",
            SuccessGreen.copy(alpha = 0.12f),
            SuccessGreen
        )
        Match.PARTIAL -> Triple(
            "⚠️ Partial — ${pharmacy.filled} of ${pharmacy.requested} items",
            AmberWarning.copy(alpha = 0.16f),
            AmberText
        )
        Match.NONE -> Triple(
            "❌ Out of Stock",
            SlateLight.copy(alpha = 0.22f),
            SlateMedium
        )
    }
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.semantics { contentDescription = label },
        shape = shape,
        color = background
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ActionRow(
    held: Boolean,
    onReserve: () -> Unit,
    onCall: () -> Unit,
    onDirections: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onReserve,
            enabled = !held,
            modifier = Modifier
                .height(44.dp)
                .semantics { contentDescription = if (held) "Reserved" else "Reserve now" },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ForestEmerald,
                contentColor = Color.White,
                disabledContainerColor = ForestEmerald.copy(alpha = 0.14f),
                disabledContentColor = ForestEmerald
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
        ) {
            if (held) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (held) "Reserved" else "Reserve Now",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onCall,
            modifier = Modifier
                .height(44.dp)
                .semantics { contentDescription = "Call this pharmacy" },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderColor),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalPhone,
                contentDescription = null,
                tint = DarkSlate,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Call",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DarkSlate
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDirections,
            modifier = Modifier.semantics { contentDescription = "Get directions to this pharmacy" },
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "Get Directions",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = ForestEmerald,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
private fun OutOfStockActions(notified: Boolean, onNotify: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (notified) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "We'll notify you when it's back",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = ForestEmerald
            )
        } else {
            TextButton(
                onClick = onNotify,
                modifier = Modifier
                    .height(44.dp)
                    .semantics { contentDescription = "Notify me when this pharmacy restocks" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsNone,
                    contentDescription = null,
                    tint = SlateMedium,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Notify When Available",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkSlate
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sort / filter sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    sortBy: SortBy,
    onSortChange: (SortBy) -> Unit,
    filters: Set<Filter>,
    onToggleFilter: (Filter) -> Unit,
    resultCount: Int,
    onDismiss: () -> Unit
) {
    // Without this the sheet stops half-expanded and its action button sits
    // below the bottom edge of the screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Sort & filter",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )
            Spacer(Modifier.height(14.dp))

            Text(
                text = "SORT BY",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMedium
            )
            SortBy.values().forEach { option ->
                SheetOption(
                    label = option.label,
                    selected = option == sortBy,
                    onClick = { onSortChange(option) }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text(
                text = "FILTERS",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMedium
            )
            Filter.values().forEach { option ->
                SheetOption(
                    label = option.label,
                    selected = option in filters,
                    onClick = { onToggleFilter(option) }
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .semantics { contentDescription = "Show $resultCount matching pharmacies" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Show $resultCount results",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label option" }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ForestEmerald else DarkSlate,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
