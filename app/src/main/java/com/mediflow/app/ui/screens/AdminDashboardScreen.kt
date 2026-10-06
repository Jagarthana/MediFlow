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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen A1 — Admin Dashboard (Variant B)
 *
 * The national console: how big the network is, how much of it is waiting on
 * SLMC verification, and the three things an administrator actually does from
 * a phone. The four KPI cards are the top of the screen because they are the
 * only numbers that decide whether the rest of the day is routine, and the
 * pending-approvals queue sits directly under them so the amber "8 Pending
 * SLMC" card is one scroll away from the queue it counts.
 *
 * A1 owns the admin `Scaffold` and the admin bottom navigation (Dashboard,
 * Pharmacies, Medicines, Reports, Profile) the same way Figma P1 owns the
 * pharmacist navigation — later A-series screens slot in as panes here rather
 * than as separate nav destinations.
 *
 * Everything is local simulated state: the KPI figures are UI-phase
 * scaffolding for the admin summary endpoint, and "Mark as Reviewed" removes
 * the application from the local queue (which decrements the amber KPI) after
 * a short delay instead of calling a backend.
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
// UI-phase data — pharmacy network summary and the SLMC approval queue
// ─────────────────────────────────────────────────────────────────────────────

private data class PendingApplication(
    val id: Int,
    val pharmacy: String,
    val slmcReg: String,
    val applicant: String,
    val district: String,
    val submittedAt: String,
    val documents: List<String>
)

private val standardDocuments = listOf(
    "SLMC Registration Certificate",
    "Business Registration (BR) Certificate",
    "Pharmacist-in-Charge Licence",
    "Premises Floor Plan & Photos"
)

private val seedApplications: List<PendingApplication> = listOf(
    PendingApplication(1, "Lanka Pharmacy Wattala", "PHL-984", "Mr. Sunil Bandara", "Gampaha", "Today, 09:15 AM", standardDocuments),
    PendingApplication(2, "Suwasetha Pharmacy Kadawatha", "PHL-1027", "Ms. Renuka Fernando", "Gampaha", "Today, 08:40 AM", standardDocuments),
    PendingApplication(3, "Rajagiriya Care Pharmacy", "PHL-0871", "Dr. Ajith Perera", "Colombo", "Yesterday, 06:20 PM", standardDocuments),
    PendingApplication(4, "MediPlus Pharmacy Nugegoda", "PHL-1102", "Mrs. Dilani Silva", "Colombo", "Yesterday, 03:05 PM", standardDocuments),
    PendingApplication(5, "HealthFirst Pharmacy Kandy", "PHL-0764", "Mr. Kasun Rathnayake", "Kandy", "Yesterday, 11:48 AM", standardDocuments),
    PendingApplication(6, "Serendib Pharmacy Galle", "PHL-0995", "Ms. Tharushi Jayawardena", "Galle", "03 Oct 2026, 04:30 PM", standardDocuments),
    PendingApplication(7, "New Life Pharmacy Jaffna", "PHL-1043", "Mr. Piratheepan Kumar", "Jaffna", "03 Oct 2026, 10:12 AM", standardDocuments),
    PendingApplication(8, "City Drug Store Kurunegala", "PHL-0908", "Mrs. Chandima Ekanayake", "Kurunegala", "02 Oct 2026, 02:55 PM", standardDocuments)
)

/** Registered pharmacies and catalog size stay fixed; only the queue is live. */
private const val REGISTERED_PHARMACIES = "142"
private const val CATALOG_MEDICINES = "1,247"
private const val NETWORK_UPTIME = "98.5%"

/** How many queue cards the dashboard shows before handing off to Pharmacies. */
private const val QUEUE_PREVIEW_LIMIT = 3

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
    var isRefreshing by remember { mutableStateOf(false) }
    var isGeneratingReport by remember { mutableStateOf(false) }

    // Seeded three minutes in the past so the first frame reads "3 minutes ago".
    var lastSynced by remember { mutableLongStateOf(System.currentTimeMillis() - 3 * 60 * 1000) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val pending = remember { mutableStateListOf<PendingApplication>().apply { addAll(seedApplications) } }
    var reviewId by rememberSaveable { mutableIntStateOf(-1) }
    val reviewApplication = pending.firstOrNull { it.id == reviewId }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Keeps the relative timestamp honest without a manual refresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun touch() {
        lastSynced = System.currentTimeMillis()
        now = lastSynced
    }

    fun refresh() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            delay(900)
            isRefreshing = false
            touch()
            snackbarHostState.showSnackbar("Console data synced")
        }
    }

    val kpis = remember(pending.size) {
        listOf(
            Kpi(
                value = REGISTERED_PHARMACIES,
                label = "Pharmacies",
                caption = "Registered network",
                tint = ForestEmerald,
                icon = Icons.Rounded.Storefront,
                description = "$REGISTERED_PHARMACIES pharmacies registered on the network"
            ),
            Kpi(
                value = "${pending.size}",
                label = "Pending SLMC",
                caption = "Awaiting approval",
                tint = AmberWarning,
                icon = Icons.Rounded.PendingActions,
                description = "${pending.size} pharmacy registrations awaiting SLMC approval"
            ),
            Kpi(
                value = CATALOG_MEDICINES,
                label = "Medicines",
                caption = "In master catalog",
                tint = HealthcareBlue,
                icon = Icons.Rounded.Medication,
                description = "$CATALOG_MEDICINES medicines in the master catalog"
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
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = LightBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AdminTopBar(
                    subtitle = "Last synced ${relativeSyncTime(lastSynced, now)}",
                    isRefreshing = isRefreshing,
                    onRefresh = ::refresh
                )
            },
            bottomBar = {
                AdminBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isRefreshing || isGeneratingReport) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = ForestEmerald,
                        trackColor = ForestEmerald.copy(alpha = 0.15f)
                    )
                }

                when (selectedTab) {
                    AdminTab.DASHBOARD -> AdminDashboardPane(
                        kpis = kpis,
                        pending = pending,
                        isGeneratingReport = isGeneratingReport,
                        onReviewApplication = { reviewId = it },
                        onOpenPharmacies = {
                            selectedTab = AdminTab.PHARMACIES
                        },
                        onOpenMedicines = {
                            selectedTab = AdminTab.MEDICINES
                        },
                        onRegisterPharmacy = onRegisterPharmacy,
                        onAddMedicine = onAddMedicine,
                        onGenerateReports = {
                            scope.launch {
                                isGeneratingReport = true
                                delay(1200)
                                isGeneratingReport = false
                                touch()
                                selectedTab = AdminTab.REPORTS
                                snackbarHostState.showSnackbar(
                                    "Report generated for $REGISTERED_PHARMACIES pharmacies · $CATALOG_MEDICINES medicines"
                                )
                            }
                        }
                    )

                    AdminTab.PROFILE -> AdminProfilePane(onLogout = onLogout)

                    AdminTab.PHARMACIES -> AdminPharmaciesTabPane(
                        onRegisterPharmacy = onRegisterPharmacy,
                        onReviewApplication = { reviewId = it },
                        pending = pending
                    )

                    AdminTab.MEDICINES -> AdminMedicinesTabPane(
                        onAddMedicine = onAddMedicine
                    )

                    else -> PendingAdminTab(tab = selectedTab)
                }
            }
        }

        reviewApplication?.let { application ->
            ReviewDocumentationSheet(
                application = application,
                onDismiss = { reviewId = -1 },
                onReviewed = {
                    val remaining = pending.size - 1
                    scope.launch {
                        isRefreshing = true
                        delay(600)
                        pending.removeAll { it.id == application.id }
                        isRefreshing = false
                        touch()
                        reviewId = -1
                        snackbarHostState.showSnackbar(
                            "${application.pharmacy} marked as reviewed · $remaining still pending SLMC"
                        )
                    }
                }
            )
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
// Dashboard pane — KPIs, approvals queue, quick actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminDashboardPane(
    kpis: List<Kpi>,
    pending: List<PendingApplication>,
    isGeneratingReport: Boolean,
    onReviewApplication: (Int) -> Unit,
    onOpenPharmacies: () -> Unit,
    onOpenMedicines: () -> Unit,
    onRegisterPharmacy: () -> Unit,
    onAddMedicine: () -> Unit,
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

        PendingApprovalsHeader(
            total = pending.size,
            shown = minOf(pending.size, QUEUE_PREVIEW_LIMIT)
        )

        Spacer(Modifier.height(10.dp))

        val preview = pending.take(QUEUE_PREVIEW_LIMIT)
        if (preview.isEmpty()) {
            EmptyApprovalsState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview.forEach { application ->
                    PendingApprovalCard(
                        application = application,
                        onReview = { onReviewApplication(application.id) }
                    )
                }
            }

            if (pending.size > preview.size) {
                ViewAllRow(
                    count = pending.size,
                    onClick = onOpenPharmacies
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader(title = "Quick Actions", caption = "Common administrator tasks")

        Spacer(Modifier.height(10.dp))

        QuickActions(
            isGeneratingReport = isGeneratingReport,
            onRegisterPharmacy = onRegisterPharmacy,
            onAddMedicine = onAddMedicine,
            onGenerateReports = onGenerateReports
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
private fun PendingApprovalsHeader(total: Int, shown: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pending Approvals Queue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Text(
                text = if (total == 0) "Nothing waiting on SLMC verification"
                       else "Showing $shown of $total awaiting SLMC verification",
                fontSize = 12.sp,
                color = SlateMedium
            )
        }
        if (total > 0) {
            Surface(
                shape = CircleShape,
                color = AmberWarning.copy(alpha = 0.14f),
                modifier = Modifier.semantics { contentDescription = "$total pending approvals" }
            ) {
                Text(
                    text = "$total",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberWarning,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingApprovalCard(application: PendingApplication, onReview: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReview)
            .semantics {
                contentDescription =
                    "${application.pharmacy}, SLMC registration ${application.slmcReg}, pending approval. Tap to review documentation."
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
                        .background(AmberWarning.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = application.pharmacy,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "SLMC Reg: ${application.slmcReg}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateMedium
                    )
                }
                Spacer(Modifier.width(10.dp))
                PendingReviewPill()
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "${application.district} · Submitted ${application.submittedAt}",
                fontSize = 11.5.sp,
                color = SlateLight
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        contentDescription = "Review documentation for ${application.pharmacy}"
                    },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, ForestEmerald),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestEmerald)
            ) {
                Text(
                    text = "Review Documentation",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingReviewPill() {
    Surface(shape = CircleShape, color = AmberWarning.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AmberWarning)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Pending",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AmberWarning
            )
        }
    }
}

@Composable
private fun ViewAllRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = "View all $count pending applications" }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "View all $count pending applications",
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

@Composable
private fun EmptyApprovalsState() {
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
            text = "Approval queue is clear",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Every submitted registration has been reviewed.",
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
    isGeneratingReport: Boolean,
    onRegisterPharmacy: () -> Unit,
    onAddMedicine: () -> Unit,
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
                enabled = !isGeneratingReport,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { contentDescription = "Generate compliance reports" },
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.5.dp, MintGreen.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                if (isGeneratingReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = MintGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Working…",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                } else {
                    Icon(Icons.Rounded.Assessment, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Generate Reports",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Review Documentation sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDocumentationSheet(
    application: PendingApplication,
    onDismiss: () -> Unit,
    onReviewed: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Review Documentation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "SLMC registration evidence submitted by the applicant",
                fontSize = 12.5.sp,
                color = SlateMedium
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AmberWarning.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(AmberWarning.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = application.pharmacy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "SLMC Reg: ${application.slmcReg}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateMedium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    PendingReviewPill()
                }
            }

            Spacer(Modifier.height(20.dp))

            SheetSectionLabel("Application details")
            DetailRow(label = "Applicant", value = application.applicant)
            DetailRow(label = "District", value = application.district)
            DetailRow(label = "Submitted", value = application.submittedAt)
            DetailRow(label = "Status", value = "Awaiting SLMC verification", valueColor = AmberWarning)

            Spacer(Modifier.height(20.dp))

            SheetSectionLabel("Submitted documents (${application.documents.size})")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                application.documents.forEach { document ->
                    DocumentRow(name = document)
                }
            }

            Spacer(Modifier.height(22.dp))

            MediFlowPrimaryButton(
                text = "Mark as Reviewed",
                icon = Icons.Rounded.VerifiedUser,
                contentDescription = "Mark ${application.pharmacy} documentation as reviewed",
                onClick = onReviewed
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Approve or reject is recorded against the SLMC verification step, not from this sheet.",
                fontSize = 11.sp,
                color = SlateLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.SemiBold,
        color = SlateLight,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = DarkSlate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = SlateMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DocumentRow(name: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$name, received" },
        shape = RoundedCornerShape(12.dp),
        color = LightBackground,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = SlateMedium,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = DarkSlate,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(18.dp)
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
// Profile (holds Logout) + not-yet-built tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminProfilePane(onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HG",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Dr. Hasini Gunawardena",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = DarkSlate,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "System Administrator · MediFlow National Console",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                DetailRow(label = "Role", value = "System Admin")
                DetailRow(label = "SLMC Reg", value = "ADM-0031")
                DetailRow(label = "Scope", value = "National · all districts")
            }
        }

        Spacer(Modifier.height(24.dp))
        MediFlowPrimaryButton(
            text = "Logout",
            icon = Icons.AutoMirrored.Rounded.Logout,
            contentDescription = "Log out of the administrator account",
            onClick = onLogout
        )
    }
}

@Composable
private fun AdminPharmaciesTabPane(
    onRegisterPharmacy: () -> Unit,
    onReviewApplication: (Int) -> Unit,
    pending: List<PendingApplication>
) {
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
                    text = "Pharmacy Registry",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                )
                Text(
                    text = "National network accreditation & management",
                    fontSize = 12.sp,
                    color = SlateMedium
                )
            }

            Button(
                onClick = onRegisterPharmacy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Register", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(
            title = "Awaiting Verification (${pending.size})",
            caption = "SLMC applications pending review"
        )

        Spacer(Modifier.height(10.dp))

        if (pending.isEmpty()) {
            EmptyApprovalsState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pending.forEach { application ->
                    PendingApprovalCard(
                        application = application,
                        onReview = { onReviewApplication(application.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminMedicinesTabPane(
    onAddMedicine: () -> Unit
) {
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
                    text = "NMRA standard essential drugs directory",
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

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Column {
                        Text(
                            text = "Master Drug Dictionary (1,247 Entries)",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate
                        )
                        Text(
                            text = "Synchronized across 142 registered pharmacies",
                            fontSize = 12.sp,
                            color = SlateMedium
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onAddMedicine,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HealthcareBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Medicine to NMRA Master", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PendingAdminTab(tab: AdminTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
        Spacer(Modifier.height(16.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = DarkSlate,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This screen is next in the admin build.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SlateMedium,
                textAlign = TextAlign.Center
            )
        )
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
