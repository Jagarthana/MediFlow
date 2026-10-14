package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.model.Medicine
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationStatus
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen A1 — Admin Dashboard
 *
 * The national console. The four KPI cards are the top of the screen because they
 * are the only numbers that decide whether the rest of the day is routine, and the
 * live reservations feed sits directly under them so an open hold is one scroll
 * away from the counts that include it.
 *
 * A1 owns the admin `Scaffold` and the admin bottom navigation (Dashboard,
 * Pharmacies, Medicines, Reports, Profile) the same way Figma P1 owns the
 * pharmacist navigation — later A-series screens slot in as panes here rather
 * than as separate nav destinations.
 *
 * Every count, card and row below is read from Firestore through `AppGraph.admin`
 * snapshot listeners; writes go back through the same repository and the listeners
 * re-render the panes. The one number that is not measured is the uptime KPI.
 *
 * WCAG 2.1 AA: cards carry a full-sentence semantic label so the value never
 * depends on colour alone, every control is 48dp+, and destructive-free
 * actions confirm with a snackbar.
 */

private enum class AdminTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.SpaceDashboard),
    PHARMACIES("Pharmacies", Icons.Rounded.LocalPharmacy),
    MEDICINES("Medicines", Icons.Rounded.Medication),
    REPORTS("Reports", Icons.Rounded.Assessment),
    PROFILE("Profile", Icons.Rounded.Person)
}

// ─────────────────────────────────────────────────────────────────────────────
// Console feeds — every number here is a Firestore snapshot
// ─────────────────────────────────────────────────────────────────────────────

/** No telemetry endpoint exists, so this is set decoration, not a measurement. */
private const val NETWORK_UPTIME = "98.5%"

private fun counted(size: Int?): String = size?.toString() ?: "Counting"

private data class Kpi(
    val value: String,
    val label: String,
    val caption: String,
    val tint: Color,
    val icon: ImageVector,
    val description: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen host
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onRegisterPharmacy: () -> Unit = {},
    onAddMedicine: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(AdminTab.DASHBOARD) }
    var syncTick by remember { mutableIntStateOf(0) }
    var isSyncing by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var feedFailure by remember { mutableStateOf<String?>(null) }

    var lastSynced by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var pharmacies by remember { mutableStateOf<List<PharmacyInfo>?>(null) }
    var medicines by remember { mutableStateOf<List<Medicine>?>(null) }
    var users by remember { mutableStateOf<List<UserProfile>?>(null) }
    var reservations by remember { mutableStateOf<List<Reservation>?>(null) }

    var selectedPharmacyId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingManageUsers by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Keeps the relative timestamps honest without a manual refresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // One listener per collection: the console never polls, it is told. Re-attaching the
    // four of them is all "Refresh" does.
    LaunchedEffect(syncTick) {
        val admin = AppGraph.admin
        var awaiting = 4
        fun settle() {
            if (awaiting > 0) {
                awaiting -= 1
                if (awaiting == 0) {
                    isSyncing = false
                    lastSynced = System.currentTimeMillis()
                    now = lastSynced
                }
            }
        }
        isSyncing = true
        coroutineScope {
            launch {
                runCatching { admin.observePharmacies().collect { pharmacies = it; settle() } }
                    .onFailure { feedFailure = it.message; settle() }
            }
            launch {
                runCatching { admin.observeMedicines().collect { medicines = it; settle() } }
                    .onFailure { feedFailure = it.message; settle() }
            }
            launch {
                runCatching { admin.observeUsers().collect { users = it; settle() } }
                    .onFailure { feedFailure = it.message; settle() }
            }
            launch {
                runCatching { admin.observeReservations().collect { reservations = it; settle() } }
                    .onFailure { feedFailure = it.message; settle() }
            }
        }
    }

    fun refresh() {
        if (isSyncing) return
        feedFailure = null
        syncTick += 1
        scope.launch { snackbarHostState.showSnackbar("Console data synced") }
    }

    fun savePharmacy(updated: PharmacyInfo) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            AppGraph.admin.savePharmacy(updated)
                .onSuccess { snackbarHostState.showSnackbar("${updated.name} updated") }
                .onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "The branch could not be saved.")
                }
            isBusy = false
            selectedPharmacyId = null
        }
    }

    fun setPharmacyStatus(pharmacyId: String, status: String, message: String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            AppGraph.admin.setPharmacyStatus(pharmacyId, status)
                .onSuccess { snackbarHostState.showSnackbar(message) }
                .onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "The status could not be changed.")
                }
            isBusy = false
            selectedPharmacyId = null
        }
    }

    fun setUserStatus(uid: String, status: String, message: String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            AppGraph.admin.setUserStatus(uid, status)
                .onSuccess { snackbarHostState.showSnackbar(message) }
                .onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "The account could not be changed.")
                }
            isBusy = false
            selectedUserId = null
        }
    }

    /** Writes the signed-in admin's own `users` document; the feed re-renders it. */
    fun saveAdminProfile(updated: UserProfile) {
        if (isSaving) return
        isSaving = true
        scope.launch {
            AppGraph.auth.writeProfile(updated)
                .onSuccess { snackbarHostState.showSnackbar("Administrator profile saved") }
                .onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "The profile could not be saved.")
                }
            isSaving = false
        }
    }

    val adminUid = AppGraph.currentProfile?.uid
    val adminProfile = users?.firstOrNull { it.uid == adminUid } ?: AppGraph.currentProfile

    val selectedPharmacy = selectedPharmacyId?.let { id ->
        pharmacies?.firstOrNull { it.pharmacyId == id }
    }
    val userRows = users?.map { it.toAdminUserRow() }.orEmpty()
    val selectedUser = selectedUserId?.let { id -> userRows.firstOrNull { it.id == id } }

    val holds = reservations?.count {
        it.status == ReservationStatus.RESERVED || it.status == ReservationStatus.READY
    }
    val approvedPharmacies = pharmacies?.count { it.status.equals("approved", ignoreCase = true) }
    val pharmacyCount = pharmacies?.size
    val medicineCount = medicines?.size

    val kpis = listOf(
        Kpi(
            value = counted(pharmacyCount),
            label = "Pharmacies",
            caption = "${approvedPharmacies?.toString() ?: "Counting"} open to patients",
            tint = ForestEmerald,
            icon = Icons.Rounded.Storefront,
            description = "${pharmacyCount?.toString() ?: "Counting"} pharmacies on the network"
        ),
        Kpi(
            value = counted(holds),
            label = "Open holds",
            caption = "Reserved or ready",
            tint = AmberWarning,
            icon = Icons.Rounded.PendingActions,
            description = "${holds?.toString() ?: "Counting"} reservations awaiting collection"
        ),
        Kpi(
            value = counted(medicineCount),
            label = "Medicines",
            caption = "In master catalog",
            tint = HealthcareBlue,
            icon = Icons.Rounded.Medication,
            description = "${medicineCount?.toString() ?: "Counting"} medicines in the master catalog"
        ),
        Kpi(
            value = NETWORK_UPTIME,
            label = "Uptime",
            caption = "Rolling 30 days",
            tint = MintGreen,
            icon = Icons.Rounded.TrendingUp,
            description = "$NETWORK_UPTIME platform uptime over the last 30 days"
        )
    )

    val recentHolds = reservations.orEmpty()
        .filter { it.status == ReservationStatus.RESERVED || it.status == ReservationStatus.READY }
        .sortedByDescending { it.createdAtMillis }
        .take(4)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = LightBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AdminTopBar(
                    subtitle = if (lastSynced == 0L) "Connecting to the network…"
                        else "Last synced ${relativeSyncTime(lastSynced, now)}",
                    isRefreshing = isSyncing,
                    onRefresh = ::refresh
                )
            },
            bottomBar = {
                AdminBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        selectedPharmacyId = null
                        selectedUserId = null
                        showingManageUsers = false
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isSyncing || isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = ForestEmerald,
                        trackColor = ForestEmerald.copy(alpha = 0.15f)
                    )
                }

                feedFailure?.let { message ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "The console could not read the network: $message",
                                fontSize = 12.sp,
                                color = DarkSlate,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = ::refresh) {
                                Icon(Icons.Rounded.Refresh, "Retry", tint = ErrorRed)
                            }
                        }
                    }
                }

                when {
                    selectedPharmacy != null -> {
                        val pharmacy = selectedPharmacy
                        PharmacyDetailScreen(
                            pharmacy = pharmacy,
                            isBusy = isBusy,
                            onSave = ::savePharmacy,
                            onApprove = {
                                setPharmacyStatus(
                                    pharmacy.pharmacyId,
                                    "approved",
                                    "${pharmacy.name} approved — patients can now find it"
                                )
                            },
                            onReject = {
                                setPharmacyStatus(
                                    pharmacy.pharmacyId,
                                    "rejected",
                                    "${pharmacy.name} rejected"
                                )
                            },
                            onDeactivate = {
                                setPharmacyStatus(
                                    pharmacy.pharmacyId,
                                    "rejected",
                                    "${pharmacy.name} removed from patient searches"
                                )
                            },
                            onBack = { selectedPharmacyId = null }
                        )
                    }

                    selectedUser != null -> {
                        val user = selectedUser
                        UserDetailScreen(
                            user = user,
                            isBusy = isBusy,
                            onDisable = {
                                setUserStatus(user.id, "disabled", "${user.name} can no longer sign in")
                            },
                            onEnable = {
                                setUserStatus(user.id, "active", "${user.name} can sign in again")
                            },
                            onBack = { selectedUserId = null }
                        )
                    }

                    showingManageUsers -> {
                        ManageUsersScreen(
                            users = userRows,
                            onUserClick = { user -> selectedUserId = user.id }
                        )
                    }

                    selectedTab == AdminTab.DASHBOARD -> AdminDashboardPane(
                        kpis = kpis,
                        recentHolds = recentHolds,
                        holdsLoading = reservations == null,
                        now = now,
                        onOpenPharmacies = {
                            selectedTab = AdminTab.PHARMACIES
                        },
                        onRegisterPharmacy = onRegisterPharmacy,
                        onAddMedicine = onAddMedicine,
                        onManageUsers = {
                            showingManageUsers = true
                        },
                        onGenerateReports = {
                            selectedTab = AdminTab.REPORTS
                        }
                    )

                    selectedTab == AdminTab.PHARMACIES -> PharmacyListScreen(
                        pharmacies = pharmacies.orEmpty(),
                        onAddClick = onRegisterPharmacy,
                        onPharmacyClick = { pharmacy ->
                            selectedPharmacyId = pharmacy.pharmacyId
                        }
                    )

                    selectedTab == AdminTab.REPORTS -> AdminReportsScreen(
                        reservations = reservations,
                        pharmacies = pharmacies,
                        medicines = medicines
                    )

                    selectedTab == AdminTab.PROFILE -> AdminProfilePane(
                        profile = adminProfile,
                        pharmacyCount = pharmacyCount,
                        medicineCount = medicineCount,
                        userCount = users?.size,
                        isSaving = isSaving,
                        onLogout = onLogout,
                        onManageUsers = {
                            showingManageUsers = true
                        },
                        onSave = ::saveAdminProfile
                    )

                    selectedTab == AdminTab.MEDICINES -> AdminMedicinesTabPane(
                        medicines = medicines,
                        onAddMedicine = onAddMedicine
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top app bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminTopBar(
    subtitle: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdminBrandMark()
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Admin Console",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = SlateMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Sync console data" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = if (isRefreshing) SlateLight else DarkSlate,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminBrandMark() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            .semantics { contentDescription = "MediFlow" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.AdminPanelSettings,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(21.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard pane — KPIs, live reservations, quick actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminDashboardPane(
    kpis: List<Kpi>,
    recentHolds: List<Reservation>,
    holdsLoading: Boolean,
    now: Long,
    onOpenPharmacies: () -> Unit,
    onRegisterPharmacy: () -> Unit,
    onAddMedicine: () -> Unit,
    onManageUsers: () -> Unit = {},
    onGenerateReports: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 20.dp)
    ) {
        KpiGrid(kpis = kpis)

        Spacer(Modifier.height(22.dp))

        SectionHeader(
            title = "Live Reservations",
            caption = "Holds patients have placed at branches right now"
        )

        Spacer(Modifier.height(10.dp))

        if (recentHolds.isEmpty()) {
            EmptyHoldsState(loading = holdsLoading)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentHolds.forEach { reservation ->
                    HoldFeedCard(reservation = reservation, now = now)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPharmacies)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open the pharmacy registry",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestEmerald,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = ForestEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader(title = "Quick Actions", caption = "Common administrator tasks")

        Spacer(Modifier.height(10.dp))

        QuickActions(
            onRegisterPharmacy = onRegisterPharmacy,
            onAddMedicine = onAddMedicine,
            onManageUsers = onManageUsers,
            onGenerateReports = onGenerateReports
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Counts come from the Firestore collections as they change. Uptime has no " +
                "measurement behind it and is shown as decoration.",
            fontSize = 11.sp,
            color = SlateLight,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun KpiGrid(kpis: List<Kpi>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        kpis.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { kpi ->
                    KpiCard(kpi = kpi, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(kpi: Kpi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = kpi.description },
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 15.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(kpi.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = kpi.icon,
                    contentDescription = null,
                    tint = kpi.tint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = kpi.value,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = kpi.tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = kpi.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = kpi.caption,
                fontSize = 11.sp,
                color = SlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HoldFeedCard(reservation: Reservation, now: Long) {
    val ready = reservation.status == ReservationStatus.READY
    val tint = if (ready) ForestEmerald else AmberWarning
    val itemCount = reservation.items.sumOf { it.quantity }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${reservation.reference} at ${reservation.pharmacyName} for " +
                        "${reservation.patientName}, ${if (ready) "ready for collection" else "reserved"}."
            },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShoppingCart,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reservation.patientName.ifBlank { "Patient" },
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = reservation.pharmacyName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                HoldStatusPill(label = if (ready) "Ready" else "Reserved", color = tint)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Ref ${reservation.reference} · $itemCount item" +
                    (if (itemCount == 1) "" else "s") + " · ${holdTime(reservation.createdAtMillis, now)}",
                fontSize = 11.5.sp,
                color = SlateLight
            )
        }
    }
}

@Composable
private fun HoldStatusPill(label: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun EmptyHoldsState(loading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (loading) "Reading the reservations collection…" else "No holds open right now",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (loading) "The cards appear as soon as the first snapshot lands."
                else "Every hold a patient placed has been collected or cancelled.",
            fontSize = 12.5.sp,
            color = SlateMedium,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, caption: String) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
        Text(
            text = caption,
            fontSize = 12.sp,
            color = SlateMedium
        )
    }
}

@Composable
private fun QuickActions(
    onRegisterPharmacy: () -> Unit,
    onAddMedicine: () -> Unit,
    onManageUsers: () -> Unit = {},
    onGenerateReports: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onRegisterPharmacy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "Register a new pharmacy" },
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ForestEmerald,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Rounded.AddBusiness, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Register New Pharmacy",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onAddMedicine,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { contentDescription = "Add a medicine to the master catalog" },
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.5.dp, HealthcareBlue.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HealthcareBlue),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Rounded.AddCircleOutline, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Add Medicine Master",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onGenerateReports,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { contentDescription = "Open the network reports" },
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.5.dp, MintGreen.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Rounded.Assessment, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "View Reports",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Button(
            onClick = onManageUsers,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .semantics { contentDescription = "Manage system users" },
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkSlate,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Rounded.Person, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Manage Users",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminBottomBar(selectedTab: AdminTab, onTabSelected: (AdminTab) -> Unit) {
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
            AdminTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab) }
                        .semantics {
                            contentDescription =
                                "${tab.label} tab, ${if (isSelected) "selected" else "not selected"}"
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) ForestEmerald else SlateLight,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile — the signed-in admin's own `users` document
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminProfilePane(
    profile: UserProfile?,
    pharmacyCount: Int?,
    medicineCount: Int?,
    userCount: Int?,
    isSaving: Boolean,
    onLogout: () -> Unit,
    onManageUsers: () -> Unit = {},
    onSave: (UserProfile) -> Unit
) {
    var showEditProfile by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val adminName = profile?.name.orEmpty().ifBlank { "Administrator" }
    val initials = adminName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = DarkSlate,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(DarkSlate, ForestEmerald.copy(alpha = 0.85f))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials.ifBlank { "A" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = adminName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = profile?.email?.takeIf { it.isNotBlank() } ?: "No email on record",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Admin role · ${profile?.uid ?: "not signed in"}",
                                    fontSize = 10.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showEditProfile = true },
                        enabled = profile != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Profile", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileMetricTile(
                label = "Pharmacies",
                value = counted(pharmacyCount),
                caption = "On the network",
                icon = Icons.Rounded.LocalPharmacy,
                color = ForestEmerald,
                modifier = Modifier.weight(1f)
            )
            ProfileMetricTile(
                label = "Medicines",
                value = counted(medicineCount),
                caption = "In catalog",
                icon = Icons.Rounded.Medication,
                color = HealthcareBlue,
                modifier = Modifier.weight(1f)
            )
            ProfileMetricTile(
                label = "Accounts",
                value = counted(userCount),
                caption = "In users",
                icon = Icons.Rounded.Person,
                color = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        ProfileSectionCard(
            title = "Administrator Record",
            icon = Icons.Rounded.Badge
        ) {
            CredentialDetailRow(
                icon = Icons.Rounded.Email,
                label = "Sign-in Email",
                value = profile?.email.orEmpty().ifBlank { "Not recorded" },
                badge = "Auth"
            )
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
            CredentialDetailRow(
                icon = Icons.Rounded.Phone,
                label = "Contact Number",
                value = profile?.phone.orEmpty().ifBlank { "Not recorded" },
                badge = "Editable"
            )
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
            CredentialDetailRow(
                icon = Icons.Rounded.Home,
                label = "Duty Station",
                value = profile?.address.orEmpty().ifBlank { "Not recorded" },
                badge = "Editable"
            )
        }

        ProfileSectionCard(
            title = "Console Access",
            icon = Icons.Rounded.Security
        ) {
            InteractiveProfileRow(
                icon = Icons.Rounded.Person,
                iconBg = ForestEmerald.copy(alpha = 0.12f),
                iconTint = ForestEmerald,
                title = "Manage Users",
                subtitle = "Patients, pharmacists and admins in the users collection",
                onClick = onManageUsers
            )
        }

        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ErrorRed.copy(alpha = 0.12f),
                contentColor = ErrorRed
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Sign Out of Admin Console",
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    val current = profile
    if (showEditProfile && current != null) {
        EditAdminProfileSheet(
            profile = current,
            isSaving = isSaving,
            onDismiss = { showEditProfile = false },
            onSave = { updated ->
                onSave(updated)
                showEditProfile = false
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out of Admin Console?") },
            text = { Text("You will need the admin email address and its password to sign back in.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Sign Out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = SlateMedium)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAdminProfileSheet(
    profile: UserProfile,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var address by remember(profile) { mutableStateOf(profile.address) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Edit Administrator Profile",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Rounded.Person, null) }
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Rounded.Phone, null) }
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Duty Station / Office") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Rounded.Home, null) }
            )

            Text(
                text = "The email address is the sign-in identity, so it stays as it is; the " +
                    "three fields above are written to this admin's document in `users`.",
                fontSize = 11.5.sp,
                color = SlateMedium,
                lineHeight = 16.sp
            )

            Button(
                onClick = {
                    onSave(
                        profile.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim()
                        )
                    )
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)
            ) {
                Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSaving) "Saving…" else "Save Changes",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileMetricTile(
    label: String,
    value: String,
    caption: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = DarkSlate
            )

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SlateMedium
            )
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = ForestEmerald, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DarkSlate
                )
            }

            Spacer(Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun CredentialDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    badge: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = SlateMedium, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.5.sp, color = SlateMedium)
            Text(text = value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DarkSlate)
        }
        Surface(
            color = ForestEmerald.copy(alpha = 0.08f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ForestEmerald,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun InteractiveProfileRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = DarkSlate
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = SlateMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SlateMedium,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medicines tab — the live `medicines` collection
// ─────────────────────────────────────────────────────────────────────────────

private val categoryIcons = listOf(
    Icons.Rounded.Healing, Icons.Rounded.Biotech, Icons.Rounded.MonitorHeart,
    Icons.Rounded.Bloodtype, Icons.Rounded.Air, Icons.Rounded.LocalDining,
    Icons.Rounded.FavoriteBorder, Icons.Rounded.Spa, Icons.Rounded.Medication
)

private val categoryTints = listOf(
    ForestEmerald, HealthcareBlue, Color(0xFF8B5CF6),
    AmberWarning, MintGreen, Color(0xFFEC4899),
    Color(0xFF06B6D4), ErrorRed, Color(0xFFF97316)
)

private const val CATEGORY_PREVIEW_LIMIT = 9

private fun Medicine.toCatalogueRow(): RecentMedicine = RecentMedicine(
    name = "$name $strength",
    packSize = packSize,
    form = form,
    category = medicineCategory.ifBlank { therapeuticClass },
    addedAt = if (dataStatus.equals("ADMIN-ENTERED", ignoreCase = true)) {
        "Entered from this console"
    } else {
        "Seeded catalogue entry"
    },
    prescriptionRequired = requiresPrescription
)

@Composable
private fun AdminMedicinesTabPane(
    medicines: List<Medicine>?,
    onAddMedicine: () -> Unit
) {
    val rows = medicines.orEmpty()
    val prescriptionCount = rows.count { it.requiresPrescription }
    val categories = rows
        .groupBy { it.medicineCategory.ifBlank { "Uncategorised" } }
        .entries
        .sortedByDescending { it.value.size }
    val enteredFromConsole = rows
        .filter { it.dataStatus.equals("ADMIN-ENTERED", ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Medicine Catalog",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                )
                Text(
                    text = if (medicines == null) "Reading the medicines collection…"
                    else "$prescriptionCount need a prescription · ${rows.size - prescriptionCount} over the counter",
                    fontSize = 12.sp,
                    color = SlateMedium
                )
            }

            Button(
                onClick = onAddMedicine,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HealthcareBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Drug", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MedicineKpiMini(
                value = counted(rows.size.takeIf { medicines != null }),
                label = "Total Drugs",
                icon = Icons.Rounded.Medication,
                tint = HealthcareBlue,
                modifier = Modifier.weight(1f)
            )
            MedicineKpiMini(
                value = (rows.size - prescriptionCount).toString(),
                label = "OTC",
                icon = Icons.Rounded.Storefront,
                tint = ForestEmerald,
                modifier = Modifier.weight(1f)
            )
            MedicineKpiMini(
                value = prescriptionCount.toString(),
                label = "POM",
                icon = Icons.Rounded.Lock,
                tint = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(
            title = "Categories",
            caption = if (categories.isEmpty()) "No entries yet"
            else "${categories.size} categories in use, by entry count"
        )
        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.take(CATEGORY_PREVIEW_LIMIT).chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIndex, (name, entries) ->
                        val colorIdx = rowIndex * 3 + colIndex
                        CategoryChipCard(
                            name = name,
                            count = "${entries.size}",
                            icon = categoryIcons[colorIdx % categoryIcons.size],
                            tint = categoryTints.getOrElse(colorIdx) { HealthcareBlue },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(
            title = "Entered From This Console",
            caption = "Drugs the admin added; the rest came from the seed"
        )
        Spacer(Modifier.height(10.dp))

        if (enteredFromConsole.isEmpty()) {
            Text(
                text = "Nothing yet. Use “Add Drug” and the new entry appears here and in every " +
                    "branch's stock picker.",
                fontSize = 12.5.sp,
                color = SlateMedium,
                lineHeight = 17.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                enteredFromConsole.take(6).forEach { medicine ->
                    RecentMedicineCard(medicine = medicine.toCatalogueRow())
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = HealthcareBlue.copy(alpha = 0.07f),
            border = BorderStroke(1.dp, HealthcareBlue.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Info, null, tint = HealthcareBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Duplicate prevention: the console compares brand name and strength " +
                        "against the medicines collection before writing.",
                    fontSize = 11.5.sp,
                    color = DarkSlate,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private data class RecentMedicine(
    val name: String,
    val packSize: String,
    val form: String,
    val category: String,
    val addedAt: String,
    val prescriptionRequired: Boolean
)

@Composable
private fun MedicineKpiMini(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = SlateMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryChipCard(
    name: String,
    count: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = count,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Text(
                text = name.split(" ").first(),
                fontSize = 9.5.sp,
                color = DarkSlate,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentMedicineCard(medicine: RecentMedicine) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(HealthcareBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Medication,
                    contentDescription = null,
                    tint = HealthcareBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = medicine.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (medicine.prescriptionRequired) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AmberWarning.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "POM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(medicine.packSize, medicine.form, medicine.category)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = SlateMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = medicine.addedAt,
                    fontSize = 10.5.sp,
                    color = SlateLight
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun relativeSyncTime(syncedAt: Long, now: Long): String {
    val seconds = ((now - syncedAt) / 1000).coerceAtLeast(0)
    return when {
        seconds < 45 -> "just now"
        seconds < 90 -> "1 minute ago"
        seconds < 3600 -> "${seconds / 60} minutes ago"
        seconds < 7200 -> "1 hour ago"
        else -> "${seconds / 3600} hours ago"
    }
}

/** How long a hold has been open, in the same plain words the rest of the console uses. */
private fun holdTime(placedAtMillis: Long, now: Long): String {
    if (placedAtMillis <= 0L) return "no timestamp"
    val minutes = ((now - placedAtMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "placed just now"
        minutes < 60 -> "placed ${minutes} min ago"
        minutes < 24 * 60 -> "placed ${minutes / 60} h ago"
        else -> "placed ${minutes / (60 * 24)} d ago"
    }
}
