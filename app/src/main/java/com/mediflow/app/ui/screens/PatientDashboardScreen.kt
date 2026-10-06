package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * Screen 4 — Patient Dashboard (Variant B)
 *
 * Layout, top to bottom:
 *  • Top App Bar   — avatar + time-aware greeting, notification bell with badge
 *  • Reservation   — dark slate card, only rendered when a reservation is active
 *  • Hero Actions  — Upload Slip (Emerald) | Direct Search (Blue), equal width
 *  • Pharmacies    — "Recently Registered Pharmacies" list with stock badges
 *  • Bottom Bar    — Home / Search / Vault / Alerts / Profile, persistent
 *
 * Sample data below is UI-phase scaffolding; it is replaced by the repository
 * layer once the backend exists.
 *
 * Nielsen Heuristics: #4 (Consistency), #6 (Recognition over recall), #8 (Aesthetic)
 * WCAG 2.1 AA: 48dp+ touch targets, semantic labels on every control
 */

// ─────────────────────────────────────────────────────────────────────────────
// UI-phase sample data
// ─────────────────────────────────────────────────────────────────────────────

private const val PATIENT_NAME = "Kavindu Perera"
private const val NOTIFICATION_COUNT = 3

private data class ActiveReservation(val pharmacyName: String, val reference: String)

private data class RegisteredPharmacy(
    val name: String,
    val area: String,
    val distanceKm: Double,
    val inStock: Boolean
)

private val sampleReservation: ActiveReservation? = ActiveReservation("Serendib Pharmacy", "4521")

private val sampleRegisteredPharmacies = listOf(
    RegisteredPharmacy("Serendib Pharmacy", "Kurunegala", 1.2, inStock = true),
    RegisteredPharmacy("MedLife Pharmacy", "Ibbagamuwa", 2.8, inStock = false),
    RegisteredPharmacy("Health Plus", "Mawathagama", 4.5, inStock = true),
    RegisteredPharmacy("Crown Chemists", "Nikitasthena", 6.1, inStock = false)
)

private enum class DashboardTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    VAULT("Vault", Icons.Rounded.Medication),
    ALERTS("Alerts", Icons.Rounded.Notifications),
    PROFILE("Profile", Icons.Rounded.Person)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen host
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PatientDashboardScreen(
    onLogout: () -> Unit,
    onUploadSlip: () -> Unit,
    onDirectSearch: () -> Unit,
    onShowCounterQr: (reference: String, medicineLabel: String, pharmacyName: String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MediFlowBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                DashboardTab.HOME -> HomeTabContent(
                    onNotificationsClick = {
                        scope.launch { snackbarHostState.showSnackbar("Notifications arrive next") }
                    },
                    onUploadSlip = onUploadSlip,
                    onDirectSearch = onDirectSearch,
                    onViewDetails = {
                        scope.launch { snackbarHostState.showSnackbar("Reservation details are pending") }
                    },
                    onPharmacyClick = { pharmacy ->
                        scope.launch { snackbarHostState.showSnackbar("${pharmacy.name} catalog is pending") }
                    }
                )

                DashboardTab.VAULT -> ReservationVaultTabContent(
                    onShowQr = onShowCounterQr,
                    onStartSearch = onDirectSearch
                )

                else -> PendingTabContent(
                    tab = selectedTab,
                    onLogout = onLogout.takeIf { selectedTab == DashboardTab.PROFILE }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTabContent(
    onNotificationsClick: () -> Unit,
    onUploadSlip: () -> Unit,
    onDirectSearch: () -> Unit,
    onViewDetails: () -> Unit,
    onPharmacyClick: (RegisteredPharmacy) -> Unit
) {
    DashboardTopBar(
        name = PATIENT_NAME,
        notificationCount = NOTIFICATION_COUNT,
        onNotificationsClick = onNotificationsClick
    )

    sampleReservation?.let { reservation ->
        ActiveReservationCard(
            reservation = reservation,
            onViewDetails = onViewDetails,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
    }

    PrimaryActionCards(
        onUploadSlip = onUploadSlip,
        onDirectSearch = onDirectSearch,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
    )

    SectionTitle("Recently Registered Pharmacies")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sampleRegisteredPharmacies.forEach { pharmacy ->
            PharmacyListItem(
                pharmacy = pharmacy,
                onClick = { onPharmacyClick(pharmacy) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar — avatar + greeting, notification bell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardTopBar(
    name: String,
    notificationCount: Int,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            PatientAvatar(name = name)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "${greetingForNow()} 👋",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SlateMedium,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = name.substringBefore(' '),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        NotificationBell(
            notificationCount = notificationCount,
            onClick = onNotificationsClick
        )
    }
}

@Composable
private fun PatientAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.semantics { contentDescription = "Profile of $name" }
        )
    }
}

@Composable
private fun NotificationBell(notificationCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier.semantics {
            contentDescription =
                if (notificationCount > 0) "Notifications, $notificationCount unread"
                else "Notifications, none unread"
        }
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = DarkSlate,
                modifier = Modifier.size(24.dp)
            )
        }
        if (notificationCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(ErrorRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = notificationCount.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Reservation Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveReservationCard(
    reservation: ActiveReservation,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSlate)
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 14.dp)
            .semantics {
                contentDescription =
                    "Active reservation at ${reservation.pharmacyName}, reference ${reservation.reference}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MintGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ACTIVE RESERVATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MintGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = reservation.pharmacyName,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "REF# ${reservation.reference}",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateLight),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        TextButton(
            onClick = onViewDetails,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text(
                text = "View Details",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = MintGreen,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MintGreen,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primary Action Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrimaryActionCards(
    onUploadSlip: () -> Unit,
    onDirectSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroActionCard(
            modifier = Modifier.weight(1f),
            label = "Upload Slip",
            caption = "Scan a prescription",
            icon = Icons.Rounded.PhotoCamera,
            background = ForestEmerald,
            onClick = onUploadSlip
        )
        HeroActionCard(
            modifier = Modifier.weight(1f),
            label = "Direct Search",
            caption = "Find any medicine",
            icon = Icons.Rounded.Search,
            background = HealthcareBlue,
            onClick = onDirectSearch
        )
    }
}

@Composable
private fun HeroActionCard(
    label: String,
    caption: String,
    icon: ImageVector,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label — $caption" }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.80f)
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Registered Pharmacies list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = DarkSlate,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun PharmacyListItem(pharmacy: RegisteredPharmacy, onClick: () -> Unit) {
    val stockLabel = if (pharmacy.inStock) "In Stock" else "Partial"

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .semantics {
                contentDescription =
                    "${pharmacy.name}, ${pharmacy.distanceKm} kilometres away, $stockLabel"
            },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFECFDF5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalPharmacy,
                    contentDescription = null,
                    tint = ForestEmerald,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pharmacy.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${pharmacy.distanceKm} km away · ${pharmacy.area}",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateLight)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            StockBadge(inStock = pharmacy.inStock, label = stockLabel)

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.padding(start = 6.dp).size(20.dp)
            )
        }
    }
}

@Composable
private fun StockBadge(inStock: Boolean, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (inStock) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (inStock) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (inStock) ForestEmerald else AmberWarning,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (inStock) ForestEmerald else AmberWarning,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar — persistent across the authenticated area
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediFlowBottomBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
        ) {
            DashboardTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab) }
                        .semantics {
                            contentDescription = "${tab.label} tab, ${if (isSelected) "selected" else "not selected"}"
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) ForestEmerald else SlateLight,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) ForestEmerald else SlateLight,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Not-yet-built tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PendingTabContent(tab: DashboardTab, onLogout: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = SlateMedium,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = DarkSlate,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This screen is next in the UI build.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SlateMedium,
                textAlign = TextAlign.Center
            )
        )

        if (onLogout != null) {
            Spacer(modifier = Modifier.height(32.dp))
            MediFlowPrimaryButton(
                text = "Sign Out",
                icon = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = "Sign out button",
                onClick = onLogout
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun greetingForNow(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

private fun initialsOf(name: String): String =
    name.split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
