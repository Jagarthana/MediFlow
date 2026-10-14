package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.model.Medicine
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationStatus
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Screen A4 — Analytics & Reports
 *
 * The reports pane of the admin console. It is handed the console's live snapshot feeds
 * instead of reading them again, so a hold that lands while the admin is looking at this
 * screen changes these numbers the same moment it changes the dashboard.
 *
 * Nothing here is projected, modelled or compared against a previous period: every figure
 * is a count over the rows currently held in memory.
 */

/** A drug ranked by how often patients reserved it in the chosen window. */
data class MedicineRankItem(
    val name: String,
    val count: Int,
    val units: Int,
    val category: String,
    val isPom: Boolean
)

/** One branch's shelf as it stands right now. */
data class BranchShortage(
    val pharmacyId: String,
    val name: String,
    val district: String,
    val lines: Int,
    val unavailable: Int,
    val low: Int,
    val examples: List<String>
)

/** One district's branches and their share of the holds in the window. */
data class DistrictAnalyticsItem(
    val district: String,
    val activePharmacies: Int,
    val totalReservations: Int,
    val percentageShare: Float
)

private const val ALL_DISTRICTS = "All districts"

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun horizonCutoff(range: String, now: Long): Long = when (range) {
    "7" -> now - 7 * DAY_MILLIS
    "30" -> now - 30 * DAY_MILLIS
    "90" -> now - 90 * DAY_MILLIS
    "YTD" -> LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
        .withDayOfYear(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    else -> 0L
}

private fun durationText(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    return if (minutes < 60) "${minutes} min" else "%.1f hrs".format(minutes / 60.0)
}

@Composable
fun AdminReportsScreen(
    reservations: List<Reservation>?,
    pharmacies: List<PharmacyInfo>?,
    medicines: List<Medicine>?
) {
    val now = remember { System.currentTimeMillis() }

    var selectedTimeRange by remember { mutableStateOf("30") }
    var selectedDistrictFilter by remember { mutableStateOf(ALL_DISTRICTS) }
    var selectedReportTab by remember { mutableStateOf(0) }

    // Nothing is honest until the console's first snapshots have landed.
    val ready = reservations != null && pharmacies != null

    val districtById = pharmacies.orEmpty()
        .associate { it.pharmacyId to it.district.ifBlank { "Unassigned" } }
    val districtOptions = listOf(ALL_DISTRICTS) + districtById.values.distinct().sorted()

    val cutoff = horizonCutoff(selectedTimeRange, now)
    val holdsInRange = reservations.orEmpty().filter { reservation ->
        reservation.createdAtMillis >= cutoff &&
            (selectedDistrictFilter == ALL_DISTRICTS ||
                districtById[reservation.pharmacyId] == selectedDistrictFilter)
    }

    val collected = holdsInRange.count { it.status == ReservationStatus.COLLECTED }
    val openHolds = holdsInRange.count {
        it.status == ReservationStatus.RESERVED || it.status == ReservationStatus.READY
    }
    val expired = holdsInRange.count { it.status == ReservationStatus.EXPIRED }
    val cancelled = holdsInRange.count { it.status == ReservationStatus.CANCELLED }
    val totalHolds = holdsInRange.size
    val fulfillmentPercent = if (totalHolds == 0) 0 else collected * 100 / totalHolds

    val pickupDurations = holdsInRange
        .filter { it.status == ReservationStatus.COLLECTED && it.completedAtMillis != null }
        .map { (it.completedAtMillis ?: 0L) - it.createdAtMillis }
        .filter { it > 0L }
    val avgPickupText = if (pickupDurations.isEmpty()) "No collections yet"
        else durationText(pickupDurations.sum() / pickupDurations.size)

    val medicineById = medicines.orEmpty().associateBy { it.medicineId }
    val demand = holdsInRange
        .flatMap { it.items }
        .groupBy { it.medicineId }
        .map { (medicineId, lines) ->
            val first = lines.first()
            val catalogued = medicineById[medicineId]
            MedicineRankItem(
                name = first.title,
                count = lines.size,
                units = lines.sumOf { it.quantity },
                category = catalogued?.medicineCategory?.ifBlank { null }
                    ?: catalogued?.therapeuticClass?.ifBlank { null }
                    ?: "Not in the catalog",
                isPom = catalogued?.requiresPrescription ?: false
            )
        }
        .sortedWith(compareByDescending<MedicineRankItem> { it.count }.thenByDescending { it.units })
        .take(6)

    // One collection-group listener covers every branch's shelf, so the shortage tab moves
    // on the same snapshot the rest of the console does. The read is wrapped because a
    // failing listener must not take the whole pane down with it.
    var networkStock by remember { mutableStateOf<List<StockItem>?>(null) }
    var stockFailure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            AppGraph.admin.observeNetworkStock().collect { networkStock = it }
        }.onFailure { stockFailure = it.message ?: "The shelves could not be read." }
    }
    val linesByBranch = networkStock?.groupBy { it.pharmacyId }.orEmpty()
    val shelvesReady = networkStock != null

    val shortages = pharmacies.orEmpty()
        .filter {
            selectedDistrictFilter == ALL_DISTRICTS ||
                it.district.ifBlank { "Unassigned" } == selectedDistrictFilter
        }
        .map { pharmacy ->
            val lines = linesByBranch[pharmacy.pharmacyId].orEmpty()
            BranchShortage(
                pharmacyId = pharmacy.pharmacyId,
                name = pharmacy.name,
                district = pharmacy.district.ifBlank { "Unassigned" },
                lines = lines.size,
                unavailable = lines.count { it.isOutOfStock },
                low = lines.count { it.isLowStock },
                examples = lines.filter { it.isOutOfStock }.take(3).map { it.title }
            )
        }
        .sortedWith(
            compareByDescending<BranchShortage> { it.unavailable }.thenByDescending { it.low }
        )
    val unavailableLines = shortages.sumOf { it.unavailable }

    val districtRows = pharmacies.orEmpty()
        .groupBy { it.district.ifBlank { "Unassigned" } }
        .map { (district, branches) ->
            val holds = holdsInRange.count { districtById[it.pharmacyId] == district }
            DistrictAnalyticsItem(
                district = district,
                activePharmacies = branches.count { it.status.equals("approved", ignoreCase = true) },
                totalReservations = holds,
                percentageShare = if (totalHolds == 0) 0f else holds.toFloat() / totalHolds
            )
        }
        .sortedByDescending { it.totalReservations }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Analytics & Reports",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = DarkSlate,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = if (ready) "Counted from the rows the console is holding"
                else "Reading the collections…",
                fontSize = 11.5.sp,
                color = SlateMedium
            )
        }

        TimeHorizonSegmentedControl(
            selectedRange = selectedTimeRange,
            onRangeSelected = { selectedTimeRange = it }
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(districtOptions) { district ->
                val isSelected = selectedDistrictFilter == district
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedDistrictFilter = district },
                    label = {
                        Text(
                            text = district,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DarkSlate,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceWhite,
                        labelColor = SlateMedium
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = BorderColor,
                        selectedBorderColor = DarkSlate,
                        enabled = true,
                        selected = isSelected
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsKpiCard(
                title = "Holds in window",
                value = if (ready) totalHolds.toString() else "—",
                caption = when (selectedTimeRange) {
                    "YTD" -> "since 1 January"
                    else -> "last ${selectedTimeRange} days"
                },
                icon = Icons.Rounded.ShoppingBag,
                color = HealthcareBlue,
                modifier = Modifier.weight(1f)
            )
            AnalyticsKpiCard(
                title = "Fulfilled",
                value = if (ready) "$fulfillmentPercent%" else "—",
                caption = if (ready) "$collected of $totalHolds collected" else "counting",
                icon = Icons.Rounded.TaskAlt,
                color = ForestEmerald,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsKpiCard(
                title = "Avg pickup",
                value = if (ready) avgPickupText else "—",
                caption = if (ready) "$openHolds still open" else "counting",
                icon = Icons.Rounded.Schedule,
                color = DarkSlate,
                modifier = Modifier.weight(1f)
            )
            AnalyticsKpiCard(
                title = "Shelf gaps",
                value = if (ready && shelvesReady) "$unavailableLines lines" else "—",
                caption = when {
                    !ready -> "counting"
                    stockFailure != null -> "shelves unreadable"
                    else -> "${shortages.size} branch(es) watched"
                },
                icon = Icons.Rounded.WarningAmber,
                color = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        TabRow(
            selectedTabIndex = selectedReportTab,
            containerColor = SurfaceWhite,
            contentColor = ForestEmerald,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
            listOf("Overview", "Drug Demand", "Stock-Outs", "Districts").forEachIndexed { index, label ->
                Tab(
                    selected = selectedReportTab == index,
                    onClick = { selectedReportTab = index },
                    text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (!ready) {
            ReadingState()
        } else {
            when (selectedReportTab) {
                0 -> {
                    FulfillmentFunnelCard(
                        collected = collected,
                        open = openHolds,
                        expired = expired,
                        cancelled = cancelled,
                        total = totalHolds
                    )
                    MostReservedMedicinesCard(topMedicines = demand)
                    StockOutsCard(shortages = shortages, shelvesReady = shelvesReady, failure = stockFailure)
                }

                1 -> MostReservedMedicinesCard(topMedicines = demand)

                2 -> StockOutsCard(shortages = shortages, shelvesReady = shelvesReady, failure = stockFailure)

                3 -> DistrictBreakdownCard(districts = districtRows)
            }
        }

        Text(
            text = "A hold only counts as collected once the branch has scanned it; the pickup " +
                "time is the gap between placing it and that scan.",
            fontSize = 11.sp,
            color = SlateLight,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadingState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ForestEmerald,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Reading the network. The cards fill in as the first snapshots land.",
                fontSize = 12.5.sp,
                color = SlateMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeHorizonSegmentedControl(
    selectedRange: String,
    onRangeSelected: (String) -> Unit
) {
    val options = listOf(
        "7" to "7 Days",
        "30" to "30 Days",
        "90" to "90 Days",
        "YTD" to "YTD"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = selectedRange == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) DarkSlate else Color.Transparent)
                        .clickable { onRangeSelected(value) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else SlateMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsKpiCard(
    title: String,
    value: String,
    caption: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "$title: $value, $caption" },
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    color = SlateMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(9.dp))

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                fontSize = 10.5.sp,
                color = SlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FulfillmentFunnelCard(
    collected: Int,
    open: Int,
    expired: Int,
    cancelled: Int,
    total: Int
) {
    val ratePercent = if (total == 0) 0 else collected * 100 / total
    val rateColor = when {
        total == 0 -> SlateMedium
        ratePercent >= 70 -> ForestEmerald
        ratePercent in 40..69 -> AmberWarning
        else -> ErrorRed
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(rateColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = rateColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Reservation Fulfilment",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        "How the holds in this window ended",
                        fontSize = 11.5.sp,
                        color = SlateMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$ratePercent%",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = rateColor
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = when {
                            total == 0 -> "Nothing to measure yet"
                            ratePercent >= 70 -> "Healthy"
                            else -> "Attention required"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = rateColor
                    )
                    Text(
                        text = "$collected collected out of $total holds",
                        fontSize = 11.5.sp,
                        color = SlateMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (total > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BorderColor)
                ) {
                    FunnelSegment(collected.toFloat(), ForestEmerald)
                    FunnelSegment(open.toFloat(), HealthcareBlue)
                    FunnelSegment(expired.toFloat(), ErrorRed)
                    FunnelSegment(cancelled.toFloat(), SlateMedium)
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FunnelLegendItem(label = "Collected", count = "$collected", color = ForestEmerald)
                    FunnelLegendItem(label = "Open", count = "$open", color = HealthcareBlue)
                    FunnelLegendItem(label = "Expired", count = "$expired", color = ErrorRed)
                    FunnelLegendItem(label = "Cancelled", count = "$cancelled", color = SlateMedium)
                }
            } else {
                Text(
                    text = "No holds fall inside this window, so the funnel is empty rather than " +
                        "zero-filled.",
                    fontSize = 12.sp,
                    color = SlateMedium,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun RowScope.FunnelSegment(weight: Float, color: Color) {
    if (weight <= 0f) return
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
private fun FunnelLegendItem(
    label: String,
    count: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Column {
            Text(text = count, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
            Text(text = label, fontSize = 10.sp, color = SlateMedium)
        }
    }
}

@Composable
private fun MostReservedMedicinesCard(topMedicines: List<MedicineRankItem>) {
    val maxCount = (topMedicines.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(HealthcareBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Medication, null, tint = HealthcareBlue, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Most Reserved Drugs",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        "Ranked by holds placed in this window",
                        fontSize = 11.5.sp,
                        color = SlateMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (topMedicines.isEmpty()) {
                Text(
                    text = "Nothing reserved in this window yet. Reserve a medicine as a patient " +
                        "and the ranking builds itself.",
                    fontSize = 12.sp,
                    color = SlateMedium,
                    lineHeight = 16.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    topMedicines.forEachIndexed { index, item ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(HealthcareBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "#${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HealthcareBlue
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.name,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkSlate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = if (item.isPom) ErrorRed.copy(alpha = 0.1f)
                                            else ForestEmerald.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (item.isPom) "POM" else "OTC",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isPom) ErrorRed else ForestEmerald,
                                                modifier = Modifier.padding(
                                                    horizontal = 4.dp,
                                                    vertical = 1.dp
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${item.category} · ${item.units} unit(s)",
                                        fontSize = 11.sp,
                                        color = SlateMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = item.count.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkSlate
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(HealthcareBlue.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = item.count.toFloat() / maxCount)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(HealthcareBlue)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockOutsCard(shortages: List<BranchShortage>, shelvesReady: Boolean, failure: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AmberWarning.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Branch Shelf Gaps",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        text = when {
                            failure != null -> "The shelves could not be read"
                            shelvesReady -> "Lines switched off or at zero quantity right now"
                            else -> "Reading each branch's stock lines…"
                        },
                        fontSize = 11.5.sp,
                        color = SlateMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (failure != null) {
                Text(
                    text = "$failure — the branch list below is therefore incomplete, not empty.",
                    fontSize = 12.sp,
                    color = ErrorRed,
                    lineHeight = 16.sp
                )
            } else if (shortages.isEmpty()) {
                Text(
                    text = "No branches for this filter, so there is nothing to read.",
                    fontSize = 12.sp,
                    color = SlateMedium,
                    lineHeight = 16.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    shortages.forEach { item ->
                        val tint = when {
                            item.lines == 0 -> SlateMedium
                            item.unavailable == 0 -> ForestEmerald
                            else -> ErrorRed
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = LightBackground,
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Storefront,
                                        contentDescription = null,
                                        tint = DarkSlate,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = DarkSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = tint.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = item.district,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tint,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 3.dp
                                            )
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = when {
                                        item.lines == 0 ->
                                            "No shelf lines recorded — the branch has not stocked " +
                                                "anything yet"
                                        item.unavailable == 0 ->
                                            "All ${item.lines} line(s) available" +
                                                if (item.low > 0) " · ${item.low} low on quantity" else ""
                                        else -> "${item.unavailable} of ${item.lines} line(s) cannot " +
                                            "be collected" + if (item.low > 0) " · ${item.low} low" else ""
                                    },
                                    fontSize = 11.5.sp,
                                    color = SlateMedium,
                                    lineHeight = 16.sp
                                )

                                if (item.examples.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Out: ${item.examples.joinToString(", ")}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ErrorRed,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (item.lines > 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(tint.copy(alpha = 0.12f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(
                                                    fraction = (item.unavailable.toFloat() / item.lines)
                                                        .coerceIn(0f, 1f)
                                                )
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(tint)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistrictBreakdownCard(districts: List<DistrictAnalyticsItem>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ForestEmerald.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Map, null, tint = ForestEmerald, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "District Breakdown",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        "Branches on the registry and their share of holds in this window",
                        fontSize = 11.5.sp,
                        color = SlateMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (districts.isEmpty()) {
                Text(
                    text = "The pharmacies collection has no districts to group yet.",
                    fontSize = 12.sp,
                    color = SlateMedium,
                    lineHeight = 16.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    districts.forEach { district ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        district.district,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = DarkSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${district.activePharmacies} open to patients",
                                        fontSize = 11.sp,
                                        color = SlateMedium
                                    )
                                }
                                Text(
                                    text = "${district.totalReservations} holds " +
                                        "(${(district.percentageShare * 100).toInt()}%)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkSlate
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ForestEmerald.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            fraction = district.percentageShare.coerceIn(0f, 1f)
                                        )
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ForestEmerald)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
