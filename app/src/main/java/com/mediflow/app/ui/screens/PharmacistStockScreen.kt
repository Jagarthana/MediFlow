package com.mediflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.model.AppNotification
import com.mediflow.app.data.model.Medicine
import com.mediflow.app.data.model.NotificationType
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationStatus
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.ui.components.QrCode
import com.mediflow.app.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 12 — Pharmacist Stock Switcher (Variant B / Figma P1)
 *
 * The pharmacist side of the same ledger the patient searches: every medicine the branch holds
 * in Firestore, with a one-tap availability switch per line. When a switch flips, the patient's
 * Screen 9 result flips with it, so the control is deliberately large, the current state is
 * written out in words ("In Stock" / "Out of Stock"), and the timestamp at the top tells the
 * pharmacist how stale the shelf really is.
 *
 * Everything here reads and writes Firestore. The shelf and the queue are snapshot listeners, so
 * a patient's reservation reaches this console unasked and an edit made here reaches the patient
 * search the same way. The availability switch writes its own field, so marking a line out does
 * not destroy the shelf count behind it.
 *
 * WCAG 2.1 AA: switches carry semantic state, status never relies on colour alone (word + tint),
 * rows are 48dp+ targets, and the bulk buttons are labelled and confirmed with a snackbar.
 */

private enum class PharmTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.Home),
    INVENTORY("Inventory", Icons.Rounded.Inventory2),
    ORDERS("Orders", Icons.Rounded.ShoppingCart),
    PROFILE("Profile", Icons.Rounded.Person)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen host
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PharmacistStockScreen(onLogout: () -> Unit) {
    var profile by remember { mutableStateOf(AppGraph.currentProfile) }
    val pharmacyId = profile?.pharmacyId?.takeIf { it.isNotBlank() }

    if (profile == null || pharmacyId == null) {
        NoBranchState(onLogout = onLogout)
        return
    }

    var selectedTab by rememberSaveable { mutableStateOf(PharmTab.DASHBOARD) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    // The two listeners the whole console is built on: this branch's shelf and its queue.
    var pharmacy by remember(pharmacyId) { mutableStateOf<PharmacyInfo?>(null) }
    var shelf by remember(pharmacyId) { mutableStateOf<List<StockItem>?>(null) }
    var queue by remember(pharmacyId) { mutableStateOf<List<Reservation>?>(null) }
    var alerts by remember(pharmacyId) { mutableStateOf<List<AppNotification>>(emptyList()) }
    var shelfFailed by remember(pharmacyId) { mutableStateOf(false) }
    var queueFailed by remember(pharmacyId) { mutableStateOf(false) }

    // Re-attaching the listeners is what "Refresh" means now: a fresh listener always
    // re-reads from the server before it goes live.
    var syncTick by remember { mutableIntStateOf(0) }
    var lastSynced by remember(pharmacyId) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var openOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<StockItem?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showAlerts by remember { mutableStateOf(false) }
    var showBranchQr by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tracker = rememberVerificationTracker()
    val chime = rememberOrderChime()

    LaunchedEffect(pharmacyId) {
        pharmacy = runCatching { AppGraph.pharmacies.byId(pharmacyId) }.getOrNull()
    }

    LaunchedEffect(pharmacyId, syncTick) {
        val settled = runCatching {
            AppGraph.pharmacies.observeStockOf(pharmacyId).collect { rows ->
                shelf = rows
                shelfFailed = false
                lastSynced = System.currentTimeMillis()
            }
        }
        if (settled.isFailure && shelf == null) shelfFailed = true
    }

    LaunchedEffect(pharmacyId, syncTick) {
        val seen = mutableSetOf<String>()
        var primed = false
        val settled = runCatching {
            AppGraph.reservations.observeForPharmacy(pharmacyId).collect { rows ->
                queue = rows
                queueFailed = false
                val pending = rows.filter { it.displayStatus() == ReservationStatus.RESERVED }
                // A re-attach re-delivers the whole queue, so only an id nobody has announced
                // may chime — and the first frame never does.
                if (primed) {
                    val arriving = pending.filter { it.reservationId !in seen }
                    if (arriving.isNotEmpty()) {
                        chime()
                        snackbarHostState.showSnackbar(
                            "New reservation ${arriving.first().reference} from ${arriving.first().patientName}"
                        )
                    }
                }
                seen += pending.map { it.reservationId }
                primed = true
            }
        }
        if (settled.isFailure && queue == null) queueFailed = true
    }

    LaunchedEffect(pharmacyId) {
        runCatching { AppGraph.notifications.observeForPharmacy(pharmacyId).collect { alerts = it } }
    }

    // Keeps the relative "shelf updated…" label honest without a manual pull.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun refresh() {
        if (isBusy) return
        syncTick += 1
        scope.launch { snackbarHostState.showSnackbar("Shelf and queue re-read from Firestore.") }
    }

    fun setAvailability(item: StockItem, available: Boolean) {
        val before = shelf
        shelf = before?.map { if (it.medicineId == item.medicineId) it.copy(available = available) else it }
        scope.launch {
            AppGraph.pharmacies.setAvailability(pharmacyId, item.medicineId, available)
                .onFailure {
                    shelf = before
                    snackbarHostState.showSnackbar(
                        "Could not update ${item.name}: ${it.message ?: "Firestore refused the write."}"
                    )
                }
        }
    }

    fun setAll(available: Boolean) {
        if (isBusy) return
        val ids = shelf.orEmpty().map { it.medicineId }
        if (ids.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("This branch holds no medicines yet.") }
            return
        }
        isBusy = true
        val before = shelf
        shelf = shelf?.map { it.copy(available = available) }
        scope.launch {
            AppGraph.pharmacies.setAvailabilityAll(pharmacyId, ids, available)
                .onSuccess {
                    snackbarHostState.showSnackbar(
                        if (available) "All medicines marked as In Stock." else "All medicines marked as Out of Stock."
                    )
                }
                .onFailure {
                    shelf = before
                    snackbarHostState.showSnackbar("Could not update the shelf: ${it.message ?: "no reason given"}")
                }
            isBusy = false
        }
    }

    fun saveStock(item: StockItem) {
        isBusy = true
        scope.launch {
            AppGraph.pharmacies.saveStock(item)
                .onSuccess { snackbarHostState.showSnackbar("${item.name} ${item.strength} saved.") }
                .onFailure { snackbarHostState.showSnackbar("Could not save ${item.name}: ${it.message ?: "no reason given"}") }
            editing = null
            showAddSheet = false
            isBusy = false
        }
    }

    fun deleteStock(item: StockItem) {
        val before = shelf
        shelf = before?.filter { it.medicineId != item.medicineId }
        scope.launch {
            AppGraph.pharmacies.deleteStock(pharmacyId, item.medicineId)
                .onSuccess { snackbarHostState.showSnackbar("${item.name} removed from this branch.") }
                .onFailure {
                    shelf = before
                    snackbarHostState.showSnackbar("Could not remove ${item.name}: ${it.message ?: "no reason given"}")
                }
            editing = null
        }
    }

    fun tellPatient(order: PharmOrder, type: NotificationType, title: String, body: String) {
        scope.launch {
            AppGraph.notifications.tellPatient(
                patientUid = order.patientUid,
                type = type,
                title = title,
                body = body,
                reservationId = order.reservationId
            )
        }
    }

    fun markReady(order: PharmOrder) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            AppGraph.reservations.setStatus(order.reservationId, ReservationStatus.READY)
                .onSuccess {
                    openOrderId = null
                    tellPatient(
                        order,
                        NotificationType.RESERVATION_READY,
                        "Your medicines are ready",
                        "Reference ${order.reference} is packed and waiting at the ${pharmacy?.name ?: "counter"}."
                    )
                    snackbarHostState.showSnackbar("Order ${order.reference} is ready for pickup.")
                }
                .onFailure {
                    snackbarHostState.showSnackbar(
                        "Could not mark ${order.reference} ready: ${it.message ?: "Firestore refused the write."}"
                    )
                }
            isBusy = false
        }
    }

    fun rejectOrder(order: PharmOrder) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            AppGraph.reservations.cancel(order.reservationId, "Rejected at the ${pharmacy?.name ?: "branch"} counter")
                .onSuccess {
                    openOrderId = null
                    tellPatient(
                        order,
                        NotificationType.RESERVATION_CANCELLED,
                        "Reservation declined",
                        "The counter could not fill reference ${order.reference}. Nothing was held for you."
                    )
                    snackbarHostState.showSnackbar("Order ${order.reference} was rejected.")
                }
                .onFailure {
                    snackbarHostState.showSnackbar(
                        "Could not reject ${order.reference}: ${it.message ?: "Firestore refused the write."}"
                    )
                }
            isBusy = false
        }
    }

    fun handOver(order: PharmOrder) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            // The status first: it is guarded by the order still being READY, so a retry of the
            // tap cannot book the same bag off the shelf twice.
            AppGraph.reservations.setStatus(order.reservationId, ReservationStatus.COLLECTED)
                .onSuccess {
                    val reduced = AppGraph.pharmacies.reduceForPickup(pharmacyId, order.shelfLines)
                    openOrderId = null
                    snackbarHostState.showSnackbar(
                        if (reduced.isSuccess) "${order.reference} handed over to ${order.patient}."
                        else "Handed over, but the shelf count could not be reduced — adjust it in Inventory."
                    )
                }
                .onFailure {
                    snackbarHostState.showSnackbar(
                        "Could not close ${order.reference}: ${it.message ?: "Firestore refused the write."}"
                    )
                }
            isBusy = false
        }
    }

    val rows = shelf.orEmpty()
    val inStockCount = rows.count { !it.isOutOfStock }
    val shortages = rows.filter { it.isOutOfStock || it.isLowStock }
    val orders = queue.orEmpty().map { it.toPharmOrder() }
    val pendingOrders = orders.filter { it.status == OrderStatus.PENDING }
    val fulfilledToday = queue.orEmpty().count {
        it.status == ReservationStatus.COLLECTED && it.completedAtMillis?.let(::isToday) == true
    }
    val unreadAlerts = alerts.count { !it.isRead }
    val needle = query.trim().lowercase()
    val filtered = if (needle.isEmpty()) rows else rows.filter {
        it.name.lowercase().contains(needle) || it.strength.lowercase().contains(needle)
    }
    val openOrder = orders.firstOrNull { it.reservationId == openOrderId }
    val closeReview: (() -> Unit)? = if (openOrder != null) { { openOrderId = null } } else null

    BackHandler(enabled = selectedTab == PharmTab.ORDERS && openOrder != null) {
        openOrderId = null
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PharmacistTopBar(
                title = when (selectedTab) {
                    PharmTab.DASHBOARD -> "Pharmacist Console"
                    PharmTab.INVENTORY -> "Pharmacy Stock"
                    PharmTab.ORDERS -> openOrder?.reference ?: "Live Orders"
                    else -> selectedTab.label
                },
                showInventoryActions = selectedTab == PharmTab.INVENTORY,
                onBack = closeReview,
                showNotification = selectedTab == PharmTab.ORDERS,
                notificationCount = unreadAlerts,
                onNotificationClick = { showAlerts = true },
                searchOpen = searchOpen,
                query = query,
                onQueryChange = { query = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                menuOpen = menuOpen,
                onDismissMenu = { menuOpen = false },
                onOpenMenu = { menuOpen = true },
                onAddMedicine = { menuOpen = false; showAddSheet = true },
                onMarkAllIn = { menuOpen = false; setAll(true) },
                onMarkAllOut = { menuOpen = false; setAll(false) },
                onRefresh = { menuOpen = false; refresh() }
            )
        },
        bottomBar = {
            Column {
                if (selectedTab == PharmTab.INVENTORY) {
                    BulkActionBar(
                        isRefreshing = isBusy,
                        onMarkAllIn = { setAll(true) },
                        onMarkAllOut = { setAll(false) }
                    )
                }
                PharmacistBottomBar(selectedTab = selectedTab, onTabSelected = {
                    selectedTab = it
                    if (it != PharmTab.ORDERS) openOrderId = null
                })
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = ForestEmerald,
                    trackColor = ForestEmerald.copy(alpha = 0.15f)
                )
            }

            when (selectedTab) {
                PharmTab.DASHBOARD -> PharmacistDashboardPane(
                    pharmacy = pharmacy,
                    pendingOrders = pendingOrders,
                    fulfilledToday = fulfilledToday,
                    inStockCount = inStockCount,
                    shortages = shortages,
                    totalCatalog = rows.size,
                    isLoading = shelf == null || queue == null,
                    loadFailed = shelfFailed || queueFailed,
                    onOpenOrders = { selectedTab = PharmTab.ORDERS },
                    onOpenInventory = { selectedTab = PharmTab.INVENTORY },
                    onOpenOrderRef = { id ->
                        selectedTab = PharmTab.ORDERS
                        openOrderId = id
                    },
                    onShowBranchQr = { showBranchQr = true }
                )

                PharmTab.INVENTORY -> StockListPane(
                    items = filtered,
                    totalInStock = inStockCount,
                    total = rows.size,
                    isLoading = shelf == null,
                    searchActive = searchOpen || needle.isNotEmpty(),
                    lastUpdatedLabel = relativeTime(lastSynced, now),
                    isRefreshing = isBusy,
                    onRefresh = ::refresh,
                    onToggle = ::setAvailability,
                    onEdit = { editing = it }
                )

                PharmTab.PROFILE -> ProfilePane(
                    profile = profile,
                    pharmacy = pharmacy,
                    pendingCount = pendingOrders.size,
                    collectedCount = queue.orEmpty().count { it.status == ReservationStatus.COLLECTED },
                    onOpenOrders = {
                        openOrderId = null
                        selectedTab = PharmTab.ORDERS
                    },
                    onLogout = onLogout,
                    onSaved = { profile = it }
                )

                PharmTab.ORDERS -> {
                    val order = openOrder
                    if (order != null) {
                        OrderReviewPane(
                            order = order,
                            tracker = tracker,
                            isBusy = isBusy,
                            onReady = { markReady(order) },
                            onReject = { rejectOrder(order) },
                            onHandOver = { handOver(order) }
                        )
                    } else {
                        OrdersQueuePane(
                            orders = orders,
                            isLoading = queue == null,
                            loadFailed = queueFailed,
                            onOpenOrder = { id -> openOrderId = id }
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        StockEditSheet(
            item = item,
            isNew = false,
            onDismiss = { editing = null },
            onSave = ::saveStock,
            onDelete = { deleteStock(item) }
        )
    }

    if (showAddSheet) {
        AddStockSheet(
            pharmacyId = pharmacyId,
            onDismiss = { showAddSheet = false },
            onSave = ::saveStock
        )
    }

    if (showAlerts) {
        PharmacyAlertsDialog(
            alerts = alerts,
            onDismiss = { showAlerts = false },
            onMarkAllRead = {
                scope.launch {
                    AppGraph.notifications.markAllRead(alerts)
                        .onFailure { snackbarHostState.showSnackbar("Could not clear the alerts: ${it.message ?: "no reason given"}") }
                }
            }
        )
    }

    if (showBranchQr) {
        BranchQrDialog(pharmacy = pharmacy, pharmacyId = pharmacyId, onDismiss = { showBranchQr = false })
    }
}

/** A pharmacist account with no branch cannot manage a shelf, so say so instead of guessing. */
@Composable
private fun NoBranchState(onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalPharmacy,
            contentDescription = null,
            tint = SlateLight,
            modifier = Modifier.size(46.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "No branch is linked to this pharmacist account.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "The admin registers a branch and its pharmacist login together. Sign in again once that is done.",
            fontSize = 13.sp,
            color = SlateMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)
        ) { Text("Sign out", color = Color.White) }
    }
}

@Composable
private fun BranchQrDialog(pharmacy: PharmacyInfo?, pharmacyId: String, onDismiss: () -> Unit) {
    val licence = pharmacy?.licenceNumber.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Branch Identity Code", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This code identifies the branch and its SLMC licence. A patient's collection " +
                        "code is on their own Counter QR screen.",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    QrCode(
                        value = "mediflow://pharmacy/$pharmacyId?licence=$licence",
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${pharmacy?.name ?: "Unregistered branch"} · $pharmacyId",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DarkSlate
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
private fun PharmacyAlertsDialog(
    alerts: List<AppNotification>,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Branch Alerts", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (alerts.isEmpty()) {
                    Text(
                        text = "No patient has asked this branch for anything yet.",
                        fontSize = 13.sp,
                        color = SlateMedium
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        alerts.forEach { row ->
                            Column(modifier = Modifier.semantics { contentDescription = "${row.title} ${row.body}" }) {
                                Text(
                                    text = row.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkSlate
                                )
                                Text(text = row.body, fontSize = 12.5.sp, color = SlateMedium)
                                Text(
                                    text = if (row.isRead) "Read" else "Unread",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (row.isRead) SlateLight else ForestEmerald
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = ForestEmerald, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (alerts.any { !it.isRead }) {
                TextButton(onClick = onMarkAllRead) {
                    Text("Mark all read", color = SlateMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

private val dayFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun isToday(millis: Long): Boolean =
    dayFormat.format(Date(millis)) == dayFormat.format(Date())

// ─────────────────────────────────────────────────────────────────────────────
// Top app bar — logo, title, search, overflow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PharmacistTopBar(
    title: String,
    showInventoryActions: Boolean,
    onBack: (() -> Unit)? = null,
    showNotification: Boolean = false,
    notificationCount: Int = 0,
    onNotificationClick: (() -> Unit)? = null,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onOpenMenu: () -> Unit,
    onAddMedicine: () -> Unit,
    onMarkAllIn: () -> Unit,
    onMarkAllOut: () -> Unit,
    onRefresh: () -> Unit
) {
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(150)
            runCatching { searchFocus.requestFocus() }
        }
    }

    Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Back to the live orders queue" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = DarkSlate,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }

                BrandMark()
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (showNotification && onNotificationClick != null) {
                    Box {
                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription =
                                        if (notificationCount > 0)
                                            "Order notifications, $notificationCount unread"
                                        else "Order notifications"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = if (notificationCount > 0) ForestEmerald else DarkSlate,
                                modifier = Modifier.size(22.dp)
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
                                    text = "$notificationCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (showInventoryActions) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription =
                                    if (searchOpen) "Close medicine search" else "Search medicines"
                            }
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = null,
                            tint = if (searchOpen) ForestEmerald else DarkSlate,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "More options" }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = DarkSlate,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                            DropdownMenuItem(
                                text = { Text("Add Medicine to Stock") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AddCircle, null, tint = ForestEmerald, modifier = Modifier.size(20.dp))
                                },
                                onClick = onAddMedicine
                            )
                            HorizontalDivider(color = BorderColor)
                            Text(
                                text = "Bulk actions",
                                style = MaterialTheme.typography.labelMedium.copy(color = SlateLight),
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("Mark All In Stock") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = ForestEmerald, modifier = Modifier.size(20.dp))
                                },
                                onClick = onMarkAllIn
                            )
                            DropdownMenuItem(
                                text = { Text("Mark All Out of Stock") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.RemoveCircle, null, tint = SlateMedium, modifier = Modifier.size(20.dp))
                                },
                                onClick = onMarkAllOut
                            )
                            HorizontalDivider(color = BorderColor)
                            DropdownMenuItem(
                                text = { Text("Refresh stock") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Refresh, null, tint = DarkSlate, modifier = Modifier.size(20.dp))
                                },
                                onClick = onRefresh
                            )
                        }
                    }
                }
            }

            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search medicines", fontSize = 14.sp, color = SlateLight) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = SlateLight, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.semantics { contentDescription = "Clear search" }
                            ) {
                                Icon(Icons.Rounded.Close, null, tint = SlateMedium, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    textStyle = TextStyle(fontSize = 15.sp, color = DarkSlate),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = ForestEmerald,
                        focusedTextColor = DarkSlate,
                        unfocusedTextColor = DarkSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp)
                        .focusRequester(searchFocus)
                        .semantics { contentDescription = "Medicine search field" }
                )
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            .semantics { contentDescription = "MediFlow" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalPharmacy,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(21.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stock list pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StockListPane(
    items: List<StockItem>,
    totalInStock: Int,
    total: Int,
    isLoading: Boolean,
    searchActive: Boolean,
    lastUpdatedLabel: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggle: (StockItem, Boolean) -> Unit,
    onEdit: (StockItem) -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = ForestEmerald, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(text = "Reading this branch's shelf…", fontSize = 13.5.sp, color = SlateMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LastUpdatedBar(
                label = lastUpdatedLabel,
                inStockCount = totalInStock,
                total = total,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
        }

        if (items.isEmpty()) {
            item { NoMatchState(searchActive = searchActive) }
        } else {
            items(items, key = { it.medicineId }) { item ->
                MedicineStockRow(
                    item = item,
                    onToggle = { onToggle(item, !item.available) },
                    onEdit = { onEdit(item) }
                )
            }
        }
    }
}

@Composable
private fun LastUpdatedBar(
    label: String,
    inStockCount: Int,
    total: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Last updated: $label",
                fontSize = 13.sp,
                color = SlateMedium,
                modifier = Modifier.semantics { contentDescription = "Last updated $label" }
            )
            Text(
                text = "$inStockCount of $total in stock",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ForestEmerald
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.semantics { contentDescription = "Refresh stock information" }
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isRefreshing) "Refreshing…" else "Refresh",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = ForestEmerald
            )
        }
    }
}

@Composable
private fun MedicineStockRow(
    item: StockItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val statusLabel = when {
        !item.available || item.stockQuantity <= 0 -> "Out of Stock"
        item.isLowStock -> "Low Stock"
        else -> "In Stock"
    }
    val statusColor = if (item.isOutOfStock || item.isLowStock) AmberWarning else SuccessGreen

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${item.name}, ${item.strength}, ${item.priceLabel}, $statusLabel, ${item.stockQuantity} on hand"
            },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.strength,
                    fontSize = 12.5.sp,
                    color = SlateMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.priceLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkSlate
                )
                Text(
                    text = "${item.stockQuantity} on hand · reorder at ${item.lowStockThreshold}",
                    fontSize = 11.5.sp,
                    color = SlateMedium
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
                Switch(
                    checked = item.available,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        // A bare Switch is ~32dp tall; this lifts the hit area to
                        // the 48dp floor so a one-tap flip stays one tap.
                        .minimumInteractiveComponentSize()
                        .padding(top = 2.dp)
                        .semantics {
                            contentDescription =
                                "Toggle availability for ${item.name}, currently $statusLabel"
                        },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen,
                        checkedBorderColor = SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = SlateLight.copy(alpha = 0.5f),
                        uncheckedBorderColor = BorderColor
                    )
                )
            }

            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = "Edit ${item.name} ${item.strength} on the shelf" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = SlateMedium,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val StockItem.priceLabel: String get() = "LKR %,.2f".format(unitPriceLKR)

@Composable
private fun NoMatchState(searchActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (searchActive) Icons.Rounded.SearchOff else Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (searchActive) "No medicines match" else "No medicines to show",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DarkSlate
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (searchActive) "Try a different name or strength." else "Use Add Medicine to Stock to put the first line on this shelf.",
            fontSize = 13.5.sp,
            color = SlateMedium,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bulk actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BulkActionBar(isRefreshing: Boolean, onMarkAllIn: () -> Unit, onMarkAllOut: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onMarkAllIn,
                enabled = !isRefreshing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = "Mark all medicines as In Stock" },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, ForestEmerald),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestEmerald),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Mark All In Stock",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onMarkAllOut,
                enabled = !isRefreshing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = "Mark all medicines as Out of Stock" },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, AmberWarning.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Rounded.RemoveCircle, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Mark All Out",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PharmacistBottomBar(selectedTab: PharmTab, onTabSelected: (PharmTab) -> Unit) {
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
            PharmTab.entries.forEach { tab ->
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
                    Spacer(Modifier.height(4.dp))
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
// Pharmacist Dashboard Pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PharmacistDashboardPane(
    pharmacy: PharmacyInfo?,
    pendingOrders: List<PharmOrder>,
    fulfilledToday: Int,
    inStockCount: Int,
    shortages: List<StockItem>,
    totalCatalog: Int,
    isLoading: Boolean,
    loadFailed: Boolean,
    onOpenOrders: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenOrderRef: (String) -> Unit,
    onShowBranchQr: () -> Unit
) {
    val outOfStockCount = (totalCatalog - inStockCount).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Header Card — the branch as the registry and the admin's licence record have it
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
                            colors = listOf(
                                DarkSlate,
                                ForestEmerald.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalPharmacy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pharmacy?.name?.takeIf { it.isNotBlank() } ?: "Loading the branch…",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pharmacy?.let {
                                    listOf(it.areaLabel.ifBlank { it.district }, "SLMC Reg: ${it.licenceNumber}")
                                        .filter { part -> part.isNotBlank() && !part.endsWith(": ") }
                                        .joinToString(" · ")
                                } ?: "Reading the pharmacy record…",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = MintGreen.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = when (pharmacy?.status) {
                                        "approved" -> "● Dispensary Active & Online"
                                        "pending" -> "● Awaiting admin approval"
                                        "rejected" -> "● Approval rejected — patients cannot search this branch"
                                        null -> "● Checking the registry…"
                                        else -> "● ${pharmacy.status}"
                                    },
                                    fontSize = 10.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onShowBranchQr,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = DarkSlate
                            )
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Branch QR", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onOpenOrders,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.ShoppingCart, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Live Orders", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Text(
                text = "Reading this branch's shelf and queue from Firestore…",
                fontSize = 12.5.sp,
                color = SlateMedium
            )
        }
        if (loadFailed) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ErrorRed.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "Firestore could not be read for this branch. Check the sign-in, then re-open the Inventory or Orders tab.",
                    fontSize = 12.5.sp,
                    color = ErrorRed,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 2. Metrics Grid (2x2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PharmMetricCard(
                title = "In Stock SKUs",
                value = "$inStockCount",
                caption = "out of $totalCatalog items",
                icon = Icons.Rounded.Inventory2,
                color = ForestEmerald,
                modifier = Modifier.weight(1f),
                onClick = onOpenInventory
            )
            PharmMetricCard(
                title = "Out of Stock",
                value = "$outOfStockCount",
                caption = "Requires reorder",
                icon = Icons.Rounded.WarningAmber,
                color = AmberWarning,
                modifier = Modifier.weight(1f),
                onClick = onOpenInventory
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PharmMetricCard(
                title = "Pending Queue",
                value = "${pendingOrders.size}",
                caption = "holds waiting to be packed",
                icon = Icons.Rounded.ShoppingCart,
                color = HealthcareBlue,
                modifier = Modifier.weight(1f),
                onClick = onOpenOrders
            )
            PharmMetricCard(
                title = "Fulfilled Today",
                value = "$fulfilledToday",
                caption = "handed over since midnight",
                icon = Icons.Rounded.TaskAlt,
                color = DarkSlate,
                modifier = Modifier.weight(1f),
                onClick = onOpenOrders
            )
        }

        // 3. Live Queue Preview Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ShoppingCart, null, tint = HealthcareBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Live Orders Queue", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkSlate)
                    }

                    TextButton(onClick = onOpenOrders) {
                        Text("View All (${pendingOrders.size})", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = HealthcareBlue)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (pendingOrders.isEmpty()) {
                    Text(
                        text = if (isLoading) "Opening the queue…" else "No pending reservations at the moment.",
                        fontSize = 13.sp,
                        color = SlateMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pendingOrders.take(2).forEach { order ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenOrderRef(order.reservationId) },
                                shape = RoundedCornerShape(12.dp),
                                color = LightBackground,
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = order.reference,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = DarkSlate
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(color = AmberWarning.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                                Text("Pending", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberWarning, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text(text = "${order.patient} · ${order.itemCountLabel}", fontSize = 11.5.sp, color = SlateMedium)
                                        Text(text = order.placedAt, fontSize = 10.5.sp, color = SlateLight)
                                    }

                                    Button(
                                        onClick = { onOpenOrderRef(order.reservationId) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = HealthcareBlue)
                                    ) {
                                        Text("Review", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Critical Stock Alerts Preview Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Inventory Shortages", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkSlate)
                    }

                    TextButton(onClick = onOpenInventory) {
                        Text("Manage Stock", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ForestEmerald)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (shortages.isEmpty()) {
                    Text(
                        text = if (isLoading) "Reading the shelf…" else "Nothing is out of stock or below its reorder level.",
                        fontSize = 13.sp,
                        color = SlateMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortages.take(5).forEach { item ->
                            PharmShortageRow(
                                name = "${item.name} ${item.strength}".trim(),
                                status = when {
                                    item.isOutOfStock -> "Out of Stock"
                                    else -> "Low · ${item.stockQuantity} left"
                                }
                            )
                        }
                        if (shortages.size > 5) {
                            Text(
                                text = "+${shortages.size - 5} more lines need attention",
                                fontSize = 11.5.sp,
                                color = SlateMedium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PharmMetricCard(
    title: String,
    value: String,
    caption: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )

            Text(
                text = caption,
                fontSize = 10.5.sp,
                color = SlateMedium
            )
        }
    }
}

@Composable
private fun PharmShortageRow(
    name: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LightBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = DarkSlate)
        Text(text = status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberWarning)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pharmacist Profile Pane — rich profile with hero header, metrics, credentials
// schedule, preferences toggles, edit sheet, and logout dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePane(
    profile: UserProfile?,
    pharmacy: PharmacyInfo?,
    pendingCount: Int,
    collectedCount: Int,
    onOpenOrders: () -> Unit,
    onLogout: () -> Unit,
    onSaved: (UserProfile) -> Unit
) {
    var showEditProfile by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var dispensingAlertsEnabled by remember { mutableStateOf(true) }
    var lowStockAlertsEnabled by remember { mutableStateOf(true) }
    var autoSyncEnabled by remember { mutableStateOf(false) }

    var pharmName by remember(profile?.uid) { mutableStateOf(profile?.name.orEmpty()) }
    var pharmPhone by remember(profile?.uid) { mutableStateOf(profile?.phone.orEmpty()) }
    var isSaving by remember(profile?.uid) { mutableStateOf(false) }

    val pharmEmail = profile?.email.orEmpty().ifBlank { "No work address on file" }
    val branchLabel = pharmacy?.let { "${it.name} · ${it.areaLabel.ifBlank { it.district }}" }?.trim()?.removeSuffix("·")
        ?: "No branch linked to this account"
    val licenceNumber = pharmacy?.licenceNumber.orEmpty().ifBlank { "Not on file" }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun saveProfile() {
        val current = profile ?: return
        if (isSaving) return
        isSaving = true
        scope.launch {
            val edited = current.copy(name = pharmName.trim(), phone = pharmPhone.trim())
            AppGraph.auth.writeProfile(edited)
                .onSuccess {
                    AppGraph.rememberProfile(edited)
                    onSaved(edited)
                    showEditProfile = false
                    snackbarHostState.showSnackbar("Profile saved to Firestore.")
                }
                .onFailure {
                    snackbarHostState.showSnackbar("Could not save the profile: ${it.message ?: "Firestore refused the write."}")
                }
            isSaving = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Hero Header ──────────────────────────────────────────────
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
                                colors = listOf(
                                    ForestEmerald,
                                    DarkSlate.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pharmName.split(" ")
                                            .mapNotNull { it.firstOrNull() }
                                            .take(2).joinToString(""),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                }
                                // Online indicator dot
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE80))
                                        .border(2.dp, DarkSlate, CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pharmName,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    if (pharmacy?.status == "approved") {
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = "Approved branch",
                                            tint = Color(0xFF86EFAC),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Licensed Pharmacist · SLMC Reg. $licenceNumber",
                                    fontSize = 12.5.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = branchLabel,
                                        fontSize = 10.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditProfile = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Profile", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onOpenOrders,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.ShoppingCart, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Live Orders", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── 2. Metric Tiles ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PharmProfileMetricTile(
                    label = "Collected",
                    value = collectedCount.toString(),
                    caption = "Handed over",
                    icon = Icons.Rounded.Medication,
                    color = ForestEmerald,
                    modifier = Modifier.weight(1f)
                )
                PharmProfileMetricTile(
                    label = "Orders",
                    value = pendingCount.toString(),
                    caption = "Pending",
                    icon = Icons.Rounded.ShoppingCart,
                    color = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                PharmProfileMetricTile(
                    label = "Rating",
                    value = "4.9",
                    caption = "Patient Score",
                    icon = Icons.Rounded.Star,
                    color = Color(0xFF6366F1),
                    modifier = Modifier.weight(1f)
                )
            }

            // ── 3. Pharmacist Credentials ────────────────────────────────────
            PharmProfileSectionCard(
                title = "Professional Credentials",
                icon = Icons.Rounded.Badge
            ) {
                PharmCredentialRow(
                    icon = Icons.Rounded.Email,
                    label = "Work Email",
                    value = pharmEmail,
                    badge = "Sign-in"
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmCredentialRow(
                    icon = Icons.Rounded.Phone,
                    label = "Contact Number",
                    value = pharmPhone,
                    badge = "Direct"
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmCredentialRow(
                    icon = Icons.Rounded.Badge,
                    label = "SLMC Registration",
                    value = licenceNumber,
                    badge = "Active"
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmCredentialRow(
                    icon = Icons.Rounded.Domain,
                    label = "Branch",
                    value = branchLabel,
                    badge = "Primary"
                )
            }

            // ── 4. Shift & Schedule ───────────────────────────────────────────
            PharmProfileSectionCard(
                title = "Shift & Schedule",
                icon = Icons.Rounded.Schedule
            ) {
                PharmShiftRow(day = "Mon – Fri", hours = "08:00 – 17:00", active = true)
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmShiftRow(day = "Saturday", hours = "09:00 – 13:00", active = true)
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmShiftRow(day = "Sunday", hours = "Off Duty", active = false)
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ForestEmerald.copy(alpha = 0.08f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = ForestEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Next On-Call",
                            fontSize = 11.sp,
                            color = SlateMedium
                        )
                        Text(
                            text = "Sunday, 12 Oct · Emergency Cover",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkSlate
                        )
                    }
                }
            }

            // ── 5. Preferences ────────────────────────────────────────────────
            PharmProfileSectionCard(
                title = "Notifications & Preferences",
                icon = Icons.Rounded.Tune
            ) {
                PharmToggleRow(
                    icon = Icons.Rounded.Notifications,
                    iconBg = AmberWarning.copy(alpha = 0.12f),
                    iconTint = AmberWarning,
                    title = "Dispensing Alerts",
                    subtitle = "Notify on each prescription dispensed",
                    checked = dispensingAlertsEnabled,
                    onCheckedChange = { dispensingAlertsEnabled = it }
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmToggleRow(
                    icon = Icons.Rounded.WarningAmber,
                    iconBg = ErrorRed.copy(alpha = 0.10f),
                    iconTint = ErrorRed,
                    title = "Low Stock Alerts",
                    subtitle = "Warn when any item falls below threshold",
                    checked = lowStockAlertsEnabled,
                    onCheckedChange = { lowStockAlertsEnabled = it }
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PharmToggleRow(
                    icon = Icons.Rounded.Sync,
                    iconBg = ForestEmerald.copy(alpha = 0.12f),
                    iconTint = ForestEmerald,
                    title = "Auto-Sync Inventory",
                    subtitle = "Sync stock levels with NMRA catalog daily",
                    checked = autoSyncEnabled,
                    onCheckedChange = { autoSyncEnabled = it }
                )
            }

            // ── 6. Logout ────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
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
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // ── Edit Profile Bottom Sheet ─────────────────────────────────────────────
    if (showEditProfile) {
        ModalBottomSheet(
            onDismissRequest = { showEditProfile = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit Pharmacist Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                )
                OutlinedTextField(
                    value = pharmName,
                    onValueChange = { pharmName = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Rounded.Person, null) }
                )
                OutlinedTextField(
                    value = pharmPhone,
                    onValueChange = { pharmPhone = it },
                    label = { Text("Contact Number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Rounded.Phone, null) }
                )
                Text(
                    text = "Branch, SLMC number and sign-in address come from the pharmacy record " +
                            "the admin owns, so they are read here and edited there.",
                    fontSize = 12.sp,
                    color = SlateMedium
                )
                Button(
                    onClick = { saveProfile() },
                    enabled = !isSaving && pharmName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)
                ) {
                    Text(
                        text = if (isSaving) "Saving…" else "Save Changes",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Logout Confirmation Dialog ────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Sign Out?",
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
            },
            text = {
                Text(
                    "You'll be signed out of the pharmacist console on this device.",
                    color = SlateMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = SlateMedium)
                }
            },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

// ── Pharmacist Profile Helper Composables ────────────────────────────────────

@Composable
private fun PharmProfileMetricTile(
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
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = DarkSlate
            )
            Text(text = label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
            Text(text = caption, fontSize = 10.sp, color = SlateMedium)
        }
    }
}

@Composable
private fun PharmProfileSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = ForestEmerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PharmCredentialRow(
    icon: ImageVector,
    label: String,
    value: String,
    badge: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ForestEmerald.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ForestEmerald, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.sp, color = SlateMedium)
            Text(text = value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DarkSlate)
        }
        Surface(
            color = ForestEmerald.copy(alpha = 0.10f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ForestEmerald,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PharmShiftRow(day: String, hours: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircle,
            contentDescription = null,
            tint = if (active) ForestEmerald else SlateMedium,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = day,
            fontSize = 13.sp,
            color = DarkSlate,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = hours,
            fontSize = 13.sp,
            color = if (active) ForestEmerald else SlateMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PharmToggleRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DarkSlate)
            Text(text = subtitle, fontSize = 11.5.sp, color = SlateMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ForestEmerald,
                uncheckedTrackColor = BorderColor
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stock sheets — the pharmacist's create / update / delete path for this branch
// ─────────────────────────────────────────────────────────────────────────────

/** Same reorder point the seeded shelf uses, so a new line behaves like the existing ones. */
private const val DEFAULT_LOW_STOCK = 20

/**
 * The editable half of a shelf line, held as text so an emptied field reads as "not entered
 * yet" instead of silently becoming a zero quantity on save.
 */
private class StockForm(initial: StockItem?) {
    var quantity by mutableStateOf(initial?.stockQuantity?.toString().orEmpty())
    var price by mutableStateOf(initial?.let { plainPrice(it.unitPriceLKR) }.orEmpty())
    var threshold by mutableStateOf(initial?.lowStockThreshold?.toString().orEmpty())
    var batch by mutableStateOf(initial?.batchNo.orEmpty())
    var expiry by mutableStateOf(initial?.expiryDate.orEmpty())

    val hasErrors: Boolean
        get() = quantity.toIntOrNull()?.coerceAtLeast(0) == null ||
                price.toDoubleOrNull()?.coerceAtLeast(0.0) == null ||
                threshold.toIntOrNull()?.takeIf { it >= 1 } == null

    fun quantityOr(fallback: Int) = quantity.toIntOrNull()?.coerceAtLeast(0) ?: fallback
    fun priceOr(fallback: Double) = price.toDoubleOrNull()?.coerceAtLeast(0.0) ?: fallback
    fun thresholdOr(fallback: Int) = threshold.toIntOrNull()?.takeIf { it >= 1 } ?: fallback

    fun applyTo(item: StockItem): StockItem = item.copy(
        stockQuantity = quantityOr(item.stockQuantity),
        unitPriceLKR = priceOr(item.unitPriceLKR),
        lowStockThreshold = thresholdOr(item.lowStockThreshold),
        batchNo = batch.trim(),
        expiryDate = expiry.trim()
    )
}

/** `12.0` prints as `12` so a re-typed price does not fight the field's own decimal hint. */
private fun plainPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun StockFormFields(form: StockForm) {
    OutlinedTextField(
        value = form.quantity,
        onValueChange = { form.quantity = it.filter(Char::isDigit).take(5) },
        label = { Text("Quantity on hand") },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.price,
        onValueChange = { form.price = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
        label = { Text("Unit price (LKR)") },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.threshold,
        onValueChange = { form.threshold = it.filter(Char::isDigit).take(4) },
        label = { Text("Low-stock threshold") },
        placeholder = { Text("20") },
        supportingText = { Text("Rows at or below this count are listed as shortages on the dashboard.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.batch,
        onValueChange = { form.batch = it.take(24) },
        label = { Text("Batch no. (optional)") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.expiry,
        onValueChange = { form.expiry = it.take(12) },
        label = { Text("Expiry (optional)") },
        placeholder = { Text("2027-03") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockEditSheet(
    item: StockItem,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (StockItem) -> Unit,
    onDelete: () -> Unit
) {
    val form = remember(item.medicineId) { StockForm(item) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = if (isNew) "Add to this branch" else "Manage stock line",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                )
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    color = SlateMedium
                )
                val caption = listOf(item.form, item.packSize).filter { it.isNotBlank() }.joinToString(" · ")
                if (caption.isNotBlank()) Text(caption, fontSize = 11.5.sp, color = SlateMedium)
            }

            StockFormFields(form)

            if (form.hasErrors) {
                Text(
                    text = "Quantity, price and threshold need to be numbers before this can be saved.",
                    fontSize = 12.sp,
                    color = ErrorRed
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onSave(form.applyTo(item)) },
                    enabled = !form.hasErrors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = ForestEmerald)
                }
                if (!isNew) {
                    Button(
                        onClick = { confirmDelete = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed.copy(alpha = 0.12f),
                            contentColor = ErrorRed
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${item.title}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This takes the line off ${item.pharmacyId}'s shelf. Patients will stop seeing it, " +
                            "and the catalogue entry itself is untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Remove", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        )
    }
}

/**
 * Create path for the shelf: pick a medicine from the shared catalogue, then give it this
 * branch's quantity, price and threshold. The catalogue itself is the admin's, so a pharmacist
 * can only add a line for a drug that already exists in `medicines`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStockSheet(
    pharmacyId: String,
    onDismiss: () -> Unit,
    onSave: (StockItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var picked by remember { mutableStateOf<Medicine?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    val form = remember(picked?.medicineId) { if (picked == null) null else StockForm(null) }

    LaunchedEffect(query) {
        val needle = query.trim()
        if (needle.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        loadFailed = false
        runCatching { AppGraph.medicines.search(needle, 12L) }
            .onSuccess { results = it }
            .onFailure {
                results = emptyList()
                loadFailed = true
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = picked?.let { "Stock ${it.title}" } ?: "Add medicine to stock",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
            )

            if (picked == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search the catalogue") },
                    placeholder = { Text("Start typing a medicine name") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    query.trim().length < 2 -> Text(
                        "Type at least two letters to search the shared medicine catalogue.",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                    loadFailed -> Text(
                        "Could not read the catalogue. Check the connection and try again.",
                        fontSize = 12.5.sp,
                        color = ErrorRed
                    )
                    results.isEmpty() -> Text(
                        "No catalogue entry starts with \"${query.trim()}\". The admin adds medicines " +
                                "from the admin console.",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                    else -> results.forEach { medicine ->
                        Surface(
                            onClick = { picked = medicine },
                            shape = RoundedCornerShape(12.dp),
                            color = LightBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(medicine.title, fontWeight = FontWeight.SemiBold, color = DarkSlate)
                                val caption = listOf(
                                    medicine.genericName,
                                    medicine.form,
                                    medicine.packSize
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                if (caption.isNotBlank()) {
                                    Text(caption, fontSize = 11.5.sp, color = SlateMedium)
                                }
                            }
                        }
                    }
                }
            } else {
                val medicine = picked!!
                val stockForm = form ?: StockForm(null)
                Text(
                    text = listOf(medicine.genericName, medicine.form, medicine.packSize)
                        .filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { "From the shared catalogue" },
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
                StockFormFields(stockForm)
                OutlinedButton(
                    onClick = { picked = null },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose a different medicine") }
                Button(
                    onClick = {
                        onSave(
                            StockItem(
                                pharmacyId = pharmacyId,
                                medicineId = medicine.medicineId,
                                name = medicine.name,
                                strength = medicine.strength,
                                form = medicine.form,
                                packSize = medicine.packSize,
                                batchNo = stockForm.batch.trim(),
                                expiryDate = stockForm.expiry.trim(),
                                stockQuantity = stockForm.quantityOr(0),
                                unitPriceLKR = stockForm.priceOr(0.0),
                                lowStockThreshold = stockForm.thresholdOr(DEFAULT_LOW_STOCK),
                                available = true
                            )
                        )
                    },
                    enabled = !stockForm.hasErrors,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Add to shelf", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun relativeTime(updatedAt: Long, now: Long): String {
    val seconds = ((now - updatedAt) / 1000).coerceAtLeast(0)
    return when {
        seconds < 45 -> "just now"
        seconds < 90 -> "1 minute ago"
        seconds < 3600 -> "${seconds / 60} minutes ago"
        seconds < 7200 -> "1 hour ago"
        else -> "${seconds / 3600} hours ago"
    }
}
