package com.mediflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.ui.theme.AmberWarning
import com.mediflow.app.ui.theme.BorderColor
import com.mediflow.app.ui.theme.DarkSlate
import com.mediflow.app.ui.theme.ForestEmerald
import com.mediflow.app.ui.theme.HealthcareBlue
import com.mediflow.app.ui.theme.LightBackground
import com.mediflow.app.ui.theme.SlateLight
import com.mediflow.app.ui.theme.SlateMedium
import com.mediflow.app.ui.theme.SurfaceWhite
import java.util.Locale

/**
 * Screen 17 — Pharmacy List Screen
 *
 * Live from the `pharmacies` collection: the counts, the filter chips and every card
 * come from what the admin console is listening to, so a branch registered from the
 * Quick Actions panel appears here without a restart.
 *
 * - Top bar "Pharmacies" with search toggle & "+ Add" button
 * - Filter chips: All / Approved / Pending / Rejected
 * - Search query input for name, licence #, area or district
 * - LazyColumn of styled cards with status badges
 */
@Composable
fun PharmacyListScreen(
    pharmacies: List<PharmacyInfo>,
    onAddClick: () -> Unit,
    onPharmacyClick: (PharmacyInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredPharmacies = pharmacies.filter { pharmacy ->
        val matchesFilter = when (selectedFilter.lowercase(Locale.ROOT)) {
            "approved" -> pharmacy.status.equals("approved", ignoreCase = true)
            "pending" -> pharmacy.status.equals("pending", ignoreCase = true)
            "rejected" -> pharmacy.status.equals("rejected", ignoreCase = true)
            else -> true
        }

        val matchesSearch = if (searchQuery.isBlank()) {
            true
        } else {
            val query = searchQuery.trim()
            pharmacy.name.contains(query, ignoreCase = true) ||
                pharmacy.licenceNumber.contains(query, ignoreCase = true) ||
                pharmacy.district.contains(query, ignoreCase = true) ||
                pharmacy.areaLabel.contains(query, ignoreCase = true)
        }

        matchesFilter && matchesSearch
    }

    Scaffold(
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pharmacies",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        text = "${pharmacies.size} registered in network",
                        fontSize = 12.sp,
                        color = SlateMedium
                    )
                }

                // Search Toggle Button
                IconButton(
                    onClick = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) searchQuery = ""
                    }
                ) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Search pharmacies",
                        tint = if (isSearchVisible) ForestEmerald else DarkSlate
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // + Add Button
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestEmerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ Add",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expandable Search Text Field
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search by name, SLMC reg #, or location...",
                            color = SlateLight,
                            fontSize = 13.5.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HealthcareBlue,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = DarkSlate,
                        unfocusedTextColor = DarkSlate
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = SlateMedium,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Filter Chips Row
            val filterOptions = listOf("All", "Approved", "Pending", "Rejected")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceWhite,
                            labelColor = DarkSlate,
                            selectedContainerColor = ForestEmerald,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderColor,
                            selectedBorderColor = ForestEmerald
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pharmacy Cards LazyColumn
            if (filteredPharmacies.isEmpty()) {
                EmptyPharmacyState(hasSearch = searchQuery.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPharmacies, key = { it.pharmacyId }) { pharmacy ->
                        PharmacyListItemCard(
                            pharmacy = pharmacy,
                            onClick = { onPharmacyClick(pharmacy) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Pharmacy Card in the List
 */
@Composable
fun PharmacyListItemCard(
    pharmacy: PharmacyInfo,
    onClick: () -> Unit
) {
    val statusColor = when (pharmacy.status.lowercase(Locale.ROOT)) {
        "approved" -> ForestEmerald
        "pending" -> AmberWarning
        "rejected" -> Color(0xFF6B7280) // Gray
        else -> SlateMedium
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Pill
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pharmacy.name,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SLMC: ${pharmacy.licenceNumber}",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOf(pharmacy.areaLabel, pharmacy.district)
                        .filter { it.isNotBlank() }.joinToString(", "),
                    fontSize = 12.sp,
                    color = SlateLight
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badge
            PharmacyStatusBadge(status = pharmacy.status, color = statusColor)
        }
    }
}

/**
 * Status Badge with color-coded dot & label
 * Forest Emerald (#059669) for Approved, Amber (#F59E0B) for Pending, Gray (#6B7280) for Rejected.
 */
@Composable
fun PharmacyStatusBadge(status: String, color: Color) {
    val displayStatus = when (status.lowercase(Locale.ROOT)) {
        "approved" -> "Approved"
        "pending" -> "Pending"
        "rejected" -> "Rejected"
        else -> status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = displayStatus,
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Empty state when no pharmacies match filter / query
 */
@Composable
private fun EmptyPharmacyState(hasSearch: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SlateLight.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = SlateMedium,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No pharmacies found",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkSlate
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasSearch) "No results matching your query. Try clearing filters." else "No pharmacies registered under this filter.",
            fontSize = 13.sp,
            color = SlateMedium
        )
    }
}
