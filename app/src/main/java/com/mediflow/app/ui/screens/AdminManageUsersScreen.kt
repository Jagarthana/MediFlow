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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
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
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.ui.theme.BorderColor
import com.mediflow.app.ui.theme.DarkSlate
import com.mediflow.app.ui.theme.ErrorRed
import com.mediflow.app.ui.theme.ForestEmerald
import com.mediflow.app.ui.theme.HealthcareBlue
import com.mediflow.app.ui.theme.LightBackground
import com.mediflow.app.ui.theme.SlateLight
import com.mediflow.app.ui.theme.SlateMedium
import com.mediflow.app.ui.theme.SuccessGreen
import com.mediflow.app.ui.theme.SurfaceWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Screen 20: AppUser row
 * What the directory shows for one `users` document.
 */
data class AppUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String, // "patient" | "pharmacist" | "admin"
    val status: String, // "active" | "disabled"
    val createdAt: String
)

private val joinedAtFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

/** `[id]` stays the Firebase uid, so disabling a row writes to that account. */
fun UserProfile.toAdminUserRow(): AppUser = AppUser(
    id = uid,
    name = name.ifBlank { identifier },
    email = email,
    role = role.name.lowercase(Locale.ROOT),
    status = status,
    createdAt = if (createdAtMillis <= 0L) "Not recorded"
        else Instant.ofEpochMilli(createdAtMillis).atZone(ZoneId.systemDefault())
            .format(joinedAtFormatter)
)

/**
 * Screen 20 — Manage Users Screen
 *
 * Reads the `users` collection through the console:
 * - Top bar "Manage Users"
 * - Search field with local text state
 * - Filter chips: All / Patients / Pharmacists / Admins
 * - LazyColumn of user cards:
 *   - Name (bold)
 *   - Email (muted)
 *   - Role badge (Healthcare Blue for Patient, Forest Emerald for Pharmacist, Dark Slate for Admin)
 *   - Status badge (Active / Disabled)
 */
@Composable
fun ManageUsersScreen(
    users: List<AppUser>,
    onUserClick: (AppUser) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredUsers = users.filter { user ->
        val matchesFilter = when (selectedFilter) {
            "Patients" -> user.role.equals("patient", ignoreCase = true)
            "Pharmacists" -> user.role.equals("pharmacist", ignoreCase = true)
            "Admins" -> user.role.equals("admin", ignoreCase = true)
            else -> true
        }

        val matchesSearch = if (searchQuery.isBlank()) {
            true
        } else {
            val query = searchQuery.trim()
            user.name.contains(query, ignoreCase = true) ||
                user.email.contains(query, ignoreCase = true) ||
                user.role.contains(query, ignoreCase = true)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Manage Users",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Text(
                    text = "${users.size} registered accounts across network",
                    fontSize = 12.sp,
                    color = SlateMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search by name, email, or role...",
                            color = SlateLight,
                            fontSize = 13.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = SlateMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    },
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Filter Chips
            val filterOptions = listOf("All", "Patients", "Pharmacists", "Admins")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            selectedContainerColor = DarkSlate,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderColor,
                            selectedBorderColor = DarkSlate
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Users LazyColumn
            if (filteredUsers.isEmpty()) {
                EmptyUsersState(hasSearch = searchQuery.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredUsers, key = { it.id }) { user ->
                        UserListItemCard(
                            user = user,
                            onClick = { onUserClick(user) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * User Card Component
 */
@Composable
fun UserListItemCard(
    user: AppUser,
    onClick: () -> Unit
) {
    val roleColor = when (user.role.lowercase(Locale.ROOT)) {
        "patient" -> HealthcareBlue
        "pharmacist" -> ForestEmerald
        "admin" -> DarkSlate
        else -> SlateMedium
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar Initials
            val initials = user.name.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .map { it.first().uppercase() }
                .joinToString("")

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(roleColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (initials.isNotEmpty()) initials else "U",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = roleColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Name & Email
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = user.email,
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserRoleBadge(role = user.role)
                    UserStatusBadge(status = user.status)
                }
            }
        }
    }
}

/**
 * Role Badge:
 * - Healthcare Blue for Patient
 * - Forest Emerald for Pharmacist
 * - Dark Slate for Admin
 */
@Composable
fun UserRoleBadge(role: String) {
    val (label, badgeColor) = when (role.lowercase(Locale.ROOT)) {
        "patient" -> "Patient" to HealthcareBlue
        "pharmacist" -> "Pharmacist" to ForestEmerald
        "admin" -> "Admin" to DarkSlate
        else -> role.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } to SlateMedium
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = badgeColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Status Badge: Active / Disabled
 */
@Composable
fun UserStatusBadge(status: String) {
    val isActive = status.equals("active", ignoreCase = true)
    val color = if (isActive) SuccessGreen else Color(0xFF6B7280) // Gray
    val label = if (isActive) "Active" else "Disabled"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Empty State for User Search/Filter
 */
@Composable
private fun EmptyUsersState(hasSearch: Boolean) {
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
            text = "No users found",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkSlate
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasSearch) "No user accounts match your search query." else "No accounts found under this role filter.",
            fontSize = 13.sp,
            color = SlateMedium
        )
    }
}
