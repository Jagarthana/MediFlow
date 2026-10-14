package com.mediflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 12 — Pharmacist Stock Switcher (Variant B / Figma P1)
 *
 * The pharmacist side of the same ledger the patient searches: every essential
 * medicine the pharmacy stocks, with a one-tap availability switch per line.
 * When a switch flips, the patient's Screen 9 result flips with it, so the
 * control is deliberately large, the current state is written out in words
 * ("In Stock" / "Out of Stock"), and the timestamp at the top tells the
 * pharmacist how stale the shelf really is.
 *
 * Everything here is local simulated state. The catalog is UI-phase
 * scaffolding for the NMRA essential-medicines list the inventory endpoint
 * will return, and each write "refreshes" behind a short delay rather than
 * calling a backend.
 *
 * WCAG 2.1 AA: switches carry semantic state, status never relies on colour
 * alone (word + tint), rows are 48dp+ targets, and the bulk buttons are
 * labelled and confirmed with a snackbar.
 */

// ─────────────────────────────────────────────────────────────────────────────
// UI-phase catalog — the top essential medicines, expanded across strengths
// ─────────────────────────────────────────────────────────────────────────────

private data class Variant(val strength: String, val price: Double)

private data class Molecule(val name: String, val variants: List<Variant>)

private data class StockItem(
    val id: Int,
    val name: String,
    val strength: String,
    val priceLkr: Double,
    val defaultInStock: Boolean
) {
    val priceLabel: String get() = "LKR %,.2f".format(priceLkr)

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        return needle.isEmpty() ||
            name.lowercase().contains(needle) ||
            strength.lowercase().contains(needle)
    }
}

private val essentialMedicines: List<Molecule> = listOf(
    Molecule("Paracetamol", listOf(Variant("500 mg · Tablet", 150.0), Variant("650 mg · Tablet", 220.0), Variant("1 g · Tablet", 260.0), Variant("120 mg/5 ml · Syrup", 340.0))),
    Molecule("Ibuprofen", listOf(Variant("400 mg · Tablet", 180.0), Variant("600 mg · Tablet", 240.0), Variant("100 mg/5 ml · Syrup", 300.0))),
    Molecule("Aspirin", listOf(Variant("75 mg · Tablet", 90.0), Variant("300 mg · Tablet", 120.0))),
    Molecule("Diclofenac Sodium", listOf(Variant("50 mg · Tablet", 200.0), Variant("75 mg/3 ml · Injection", 260.0), Variant("1% · Gel", 450.0))),
    Molecule("Tramadol", listOf(Variant("50 mg · Capsule", 240.0), Variant("100 mg/2 ml · Injection", 300.0))),
    Molecule("Morphine Sulfate", listOf(Variant("10 mg · Tablet", 320.0), Variant("10 mg/ml · Injection", 480.0))),
    Molecule("Naproxen", listOf(Variant("250 mg · Tablet", 180.0), Variant("500 mg · Tablet", 260.0))),
    Molecule("Meloxicam", listOf(Variant("7.5 mg · Tablet", 200.0), Variant("15 mg · Tablet", 280.0))),
    Molecule("Amoxicillin", listOf(Variant("250 mg · Capsule", 160.0), Variant("500 mg · Capsule", 240.0), Variant("125 mg/5 ml · Syrup", 320.0))),
    Molecule("Amoxicillin + Clavulanate", listOf(Variant("625 mg · Tablet", 480.0), Variant("1 g · Tablet", 620.0), Variant("457 mg/5 ml · Syrup", 780.0))),
    Molecule("Azithromycin", listOf(Variant("250 mg · Tablet", 380.0), Variant("500 mg · Tablet", 540.0), Variant("200 mg/5 ml · Syrup", 620.0))),
    Molecule("Cephalexin", listOf(Variant("250 mg · Capsule", 220.0), Variant("500 mg · Capsule", 320.0))),
    Molecule("Ceftriaxone", listOf(Variant("1 g · Injection", 560.0), Variant("2 g · Injection", 900.0))),
    Molecule("Ciprofloxacin", listOf(Variant("250 mg · Tablet", 180.0), Variant("500 mg · Tablet", 260.0))),
    Molecule("Levofloxacin", listOf(Variant("500 mg · Tablet", 420.0), Variant("750 mg · Tablet", 560.0))),
    Molecule("Doxycycline", listOf(Variant("100 mg · Capsule", 160.0))),
    Molecule("Metronidazole", listOf(Variant("200 mg · Tablet", 120.0), Variant("400 mg · Tablet", 160.0), Variant("200 mg/5 ml · Syrup", 240.0))),
    Molecule("Clarithromycin", listOf(Variant("250 mg · Tablet", 380.0), Variant("500 mg · Tablet", 520.0))),
    Molecule("Gentamicin", listOf(Variant("80 mg/2 ml · Injection", 240.0))),
    Molecule("Benzylpenicillin", listOf(Variant("600 mg · Injection", 180.0))),
    Molecule("Nitrofurantoin", listOf(Variant("50 mg · Capsule", 140.0), Variant("100 mg · Capsule", 200.0))),
    Molecule("Co-trimoxazole", listOf(Variant("480 mg · Tablet", 90.0), Variant("960 mg · Tablet", 140.0))),
    Molecule("Cefixime", listOf(Variant("200 mg · Tablet", 420.0), Variant("400 mg · Tablet", 640.0))),
    Molecule("Erythromycin", listOf(Variant("250 mg · Tablet", 200.0), Variant("500 mg · Tablet", 300.0))),
    Molecule("Cloxacillin", listOf(Variant("500 mg · Capsule", 240.0))),
    Molecule("Clindamycin", listOf(Variant("300 mg · Capsule", 380.0))),
    Molecule("Vancomycin", listOf(Variant("1 g · Injection", 2400.0))),
    Molecule("Meropenem", listOf(Variant("1 g · Injection", 3200.0))),
    Molecule("Fluconazole", listOf(Variant("150 mg · Capsule", 220.0), Variant("200 mg · Capsule", 300.0))),
    Molecule("Aciclovir", listOf(Variant("200 mg · Tablet", 240.0), Variant("400 mg · Tablet", 340.0), Variant("5% · Cream", 480.0))),
    Molecule("Oseltamivir", listOf(Variant("75 mg · Capsule", 620.0))),
    Molecule("Albendazole", listOf(Variant("400 mg · Tablet", 120.0), Variant("200 mg/5 ml · Syrup", 180.0))),
    Molecule("Artemether + Lumefantrine", listOf(Variant("20/120 mg · Tablet", 320.0))),
    Molecule("Chloroquine", listOf(Variant("250 mg · Tablet", 90.0))),
    Molecule("Primaquine", listOf(Variant("15 mg · Tablet", 110.0))),
    Molecule("Chlorpheniramine", listOf(Variant("4 mg · Tablet", 80.0), Variant("2 mg/5 ml · Syrup", 140.0))),
    Molecule("Amlodipine", listOf(Variant("5 mg · Tablet", 140.0), Variant("10 mg · Tablet", 200.0))),
    Molecule("Losartan", listOf(Variant("50 mg · Tablet", 220.0), Variant("100 mg · Tablet", 320.0))),
    Molecule("Enalapril", listOf(Variant("5 mg · Tablet", 160.0), Variant("10 mg · Tablet", 220.0))),
    Molecule("Bisoprolol", listOf(Variant("2.5 mg · Tablet", 180.0), Variant("5 mg · Tablet", 240.0))),
    Molecule("Atenolol", listOf(Variant("50 mg · Tablet", 150.0), Variant("100 mg · Tablet", 210.0))),
    Molecule("Propranolol", listOf(Variant("40 mg · Tablet", 130.0))),
    Molecule("Furosemide", listOf(Variant("40 mg · Tablet", 120.0), Variant("20 mg/2 ml · Injection", 180.0))),
    Molecule("Spironolactone", listOf(Variant("25 mg · Tablet", 200.0), Variant("100 mg · Tablet", 480.0))),
    Molecule("Digoxin", listOf(Variant("0.25 mg · Tablet", 90.0))),
    Molecule("Warfarin", listOf(Variant("5 mg · Tablet", 140.0))),
    Molecule("Clopidogrel", listOf(Variant("75 mg · Tablet", 280.0))),
    Molecule("Atorvastatin", listOf(Variant("10 mg · Tablet", 240.0), Variant("20 mg · Tablet", 320.0), Variant("40 mg · Tablet", 460.0))),
    Molecule("Simvastatin", listOf(Variant("20 mg · Tablet", 220.0), Variant("40 mg · Tablet", 340.0))),
    Molecule("Isosorbide Dinitrate", listOf(Variant("5 mg · Tablet", 120.0), Variant("20 mg · Tablet", 200.0))),
    Molecule("Glyceryl Trinitrate", listOf(Variant("0.5 mg · Sublingual Tablet", 260.0))),
    Molecule("Nifedipine", listOf(Variant("10 mg · Capsule", 140.0), Variant("20 mg MR · Tablet", 240.0))),
    Molecule("Hydrochlorothiazide", listOf(Variant("25 mg · Tablet", 90.0))),
    Molecule("Tranexamic Acid", listOf(Variant("500 mg · Tablet", 220.0))),
    Molecule("Adrenaline", listOf(Variant("1 mg/ml · Injection", 220.0))),
    Molecule("Atropine", listOf(Variant("0.6 mg/ml · Injection", 180.0))),
    Molecule("Lidocaine 2%", listOf(Variant("20 ml · Injection", 320.0))),
    Molecule("Naloxone", listOf(Variant("0.4 mg/ml · Injection", 640.0))),
    Molecule("Magnesium Sulphate", listOf(Variant("50% 10 ml · Injection", 260.0))),
    Molecule("Calcium Gluconate", listOf(Variant("10% 10 ml · Injection", 200.0))),
    Molecule("Metformin", listOf(Variant("500 mg · Tablet", 160.0), Variant("850 mg · Tablet", 220.0), Variant("1000 mg · Tablet", 300.0))),
    Molecule("Glimepiride", listOf(Variant("1 mg · Tablet", 180.0), Variant("2 mg · Tablet", 240.0), Variant("4 mg · Tablet", 340.0))),
    Molecule("Gliclazide", listOf(Variant("80 mg · Tablet", 260.0), Variant("60 mg MR · Tablet", 380.0))),
    Molecule("Sitagliptin", listOf(Variant("100 mg · Tablet", 620.0))),
    Molecule("Insulin Soluble", listOf(Variant("100 IU/ml · Vial", 1450.0))),
    Molecule("Insulin Isophane", listOf(Variant("100 IU/ml · Vial", 1520.0))),
    Molecule("Omeprazole", listOf(Variant("20 mg · Capsule", 220.0), Variant("40 mg · Capsule", 340.0), Variant("40 mg · Injection", 480.0))),
    Molecule("Pantoprazole", listOf(Variant("40 mg · Tablet", 300.0), Variant("40 mg · Injection", 520.0))),
    Molecule("Ranitidine", listOf(Variant("150 mg · Tablet", 140.0), Variant("300 mg · Tablet", 220.0))),
    Molecule("Famotidine", listOf(Variant("20 mg · Tablet", 160.0), Variant("40 mg · Tablet", 240.0))),
    Molecule("Ondansetron", listOf(Variant("4 mg · Tablet", 180.0), Variant("8 mg · Tablet", 260.0), Variant("4 mg/2 ml · Injection", 300.0))),
    Molecule("Domperidone", listOf(Variant("10 mg · Tablet", 160.0), Variant("5 mg/5 ml · Syrup", 240.0))),
    Molecule("Loperamide", listOf(Variant("2 mg · Capsule", 120.0))),
    Molecule("Oral Rehydration Salts", listOf(Variant("20.5 g · Sachet", 60.0))),
    Molecule("Activated Charcoal", listOf(Variant("50 g · Suspension", 320.0))),
    Molecule("Simethicone", listOf(Variant("40 mg · Tablet", 100.0))),
    Molecule("Dicyclomine", listOf(Variant("20 mg · Tablet", 130.0))),
    Molecule("Metoclopramide", listOf(Variant("10 mg · Tablet", 110.0), Variant("10 mg/2 ml · Injection", 180.0))),
    Molecule("Hyoscine Butylbromide", listOf(Variant("10 mg · Tablet", 190.0))),
    Molecule("Sucralfate", listOf(Variant("1 g · Tablet", 240.0))),
    Molecule("Senna", listOf(Variant("7.5 mg · Tablet", 90.0))),
    Molecule("Lactulose", listOf(Variant("10 g/15 ml · Syrup", 320.0))),
    Molecule("Salbutamol", listOf(Variant("4 mg · Tablet", 90.0), Variant("100 mcg · Inhaler", 620.0), Variant("5 mg/2.5 ml · Nebule", 480.0), Variant("2 mg/5 ml · Syrup", 160.0))),
    Molecule("Ipratropium Bromide", listOf(Variant("20 mcg · Inhaler", 780.0))),
    Molecule("Budesonide", listOf(Variant("200 mcg · Inhaler", 940.0), Variant("0.5 mg/2 ml · Nebule", 620.0))),
    Molecule("Beclometasone", listOf(Variant("100 mcg · Inhaler", 720.0), Variant("50 mcg · Nasal Spray", 680.0))),
    Molecule("Montelukast", listOf(Variant("10 mg · Tablet", 340.0), Variant("5 mg · Chewable Tablet", 280.0))),
    Molecule("Cetirizine", listOf(Variant("10 mg · Tablet", 120.0), Variant("5 mg/5 ml · Syrup", 180.0))),
    Molecule("Loratadine", listOf(Variant("10 mg · Tablet", 140.0), Variant("5 mg/5 ml · Syrup", 200.0))),
    Molecule("Fexofenadine", listOf(Variant("120 mg · Tablet", 260.0), Variant("180 mg · Tablet", 340.0))),
    Molecule("Ambroxol", listOf(Variant("30 mg · Tablet", 120.0), Variant("15 mg/5 ml · Syrup", 180.0))),
    Molecule("Dextromethorphan", listOf(Variant("15 mg/5 ml · Syrup", 200.0))),
    Molecule("Diazepam", listOf(Variant("5 mg · Tablet", 100.0), Variant("10 mg/2 ml · Injection", 220.0))),
    Molecule("Amitriptyline", listOf(Variant("25 mg · Tablet", 120.0), Variant("50 mg · Tablet", 180.0))),
    Molecule("Fluoxetine", listOf(Variant("20 mg · Capsule", 220.0), Variant("40 mg · Capsule", 340.0))),
    Molecule("Sertraline", listOf(Variant("50 mg · Tablet", 280.0), Variant("100 mg · Tablet", 420.0))),
    Molecule("Risperidone", listOf(Variant("1 mg · Tablet", 200.0), Variant("2 mg · Tablet", 300.0))),
    Molecule("Haloperidol", listOf(Variant("5 mg · Tablet", 180.0), Variant("5 mg/ml · Drops", 340.0))),
    Molecule("Sodium Valproate", listOf(Variant("200 mg · Tablet", 220.0), Variant("500 mg · Tablet", 420.0))),
    Molecule("Carbamazepine", listOf(Variant("200 mg · Tablet", 180.0), Variant("400 mg · Tablet", 300.0))),
    Molecule("Phenytoin", listOf(Variant("100 mg · Capsule", 160.0))),
    Molecule("Methylphenidate", listOf(Variant("10 mg · Tablet", 380.0))),
    Molecule("Gabapentin", listOf(Variant("100 mg · Capsule", 220.0), Variant("300 mg · Capsule", 380.0))),
    Molecule("Sumatriptan", listOf(Variant("50 mg · Tablet", 420.0))),
    Molecule("Prednisolone", listOf(Variant("5 mg · Tablet", 120.0), Variant("20 mg · Tablet", 300.0))),
    Molecule("Dexamethasone", listOf(Variant("0.5 mg · Tablet", 90.0), Variant("4 mg/ml · Injection", 260.0))),
    Molecule("Hydrocortisone", listOf(Variant("20 mg · Tablet", 160.0), Variant("100 mg · Injection", 380.0))),
    Molecule("Levothyroxine", listOf(Variant("50 mcg · Tablet", 140.0), Variant("100 mcg · Tablet", 200.0))),
    Molecule("Allopurinol", listOf(Variant("100 mg · Tablet", 160.0), Variant("300 mg · Tablet", 320.0))),
    Molecule("Colchicine", listOf(Variant("0.5 mg · Tablet", 180.0))),
    Molecule("Methotrexate", listOf(Variant("2.5 mg · Tablet", 220.0))),
    Molecule("Ferrous Sulphate + Folic Acid", listOf(Variant("200/0.25 mg · Tablet", 90.0))),
    Molecule("Folic Acid", listOf(Variant("5 mg · Tablet", 80.0))),
    Molecule("Cyanocobalamin", listOf(Variant("500 mcg · Tablet", 120.0), Variant("1000 mcg · Injection", 240.0))),
    Molecule("Cholecalciferol", listOf(Variant("60000 IU · Sachet", 140.0), Variant("1000 IU · Tablet", 90.0))),
    Molecule("Ascorbic Acid", listOf(Variant("100 mg · Tablet", 70.0), Variant("500 mg · Tablet", 130.0))),
    Molecule("Calcium + Vitamin D", listOf(Variant("500/400 IU · Tablet", 110.0))),
    Molecule("Zinc Sulphate", listOf(Variant("20 mg · Tablet", 80.0))),
    Molecule("Potassium Chloride", listOf(Variant("600 mg · Tablet", 100.0))),
    Molecule("Betamethasone Cream", listOf(Variant("0.1% 20 g · Tube", 340.0))),
    Molecule("Mupirocin Ointment", listOf(Variant("2% 10 g · Tube", 620.0))),
    Molecule("Silver Sulphadiazine Cream", listOf(Variant("1% 50 g · Jar", 480.0))),
    Molecule("Povidone Iodine", listOf(Variant("5% 100 ml · Solution", 260.0))),
    Molecule("Timolol Eye Drops", listOf(Variant("0.5% 5 ml · Drops", 300.0))),
    Molecule("Carboxymethylcellulose", listOf(Variant("0.5% 10 ml · Eye Drops", 420.0))),
    Molecule("Oxytocin", listOf(Variant("10 IU/ml · Injection", 240.0))),
    Molecule("Misoprostol", listOf(Variant("200 mcg · Tablet", 180.0))),
    Molecule("Sodium Chloride 0.9%", listOf(Variant("1 L · IV Bag", 380.0))),
    Molecule("Dextrose 5%", listOf(Variant("1 L · IV Bag", 360.0))),
    Molecule("Ringer's Lactate", listOf(Variant("1 L · IV Bag", 400.0)))
)

/** Flattened SKU list with a stable id and a seeded starting availability. */
private val stockCatalog: List<StockItem> = buildList {
    var id = 0
    essentialMedicines.forEach { molecule ->
        molecule.variants.forEach { variant ->
            add(
                StockItem(
                    id = id,
                    name = molecule.name,
                    strength = variant.strength,
                    priceLkr = variant.price,
                    // Deterministic seed so the list looks lived-in but is stable.
                    defaultInStock = id % 6 != 4
                )
            )
            id++
        }
    }
}

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
    var selectedTab by rememberSaveable { mutableStateOf(PharmTab.INVENTORY) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Seeded two minutes in the past so the first frame reads "2 minutes ago".
    var lastUpdated by remember { mutableLongStateOf(System.currentTimeMillis() - 2 * 60 * 1000) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val inStock = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            stockCatalog.forEach { item -> put(item.id, item.defaultInStock) }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Screen 13 (P2): the Orders tab owns a simulated live queue. The reviewed
    // order ref lives here so the top bar can swap in a back button and the
    // order title while the review view is open.
    val orderBook = rememberOrderBook(snackbarHostState)
    var openOrderRef by rememberSaveable { mutableIntStateOf(-1) }
    val reviewOrder = orderBook.orders.firstOrNull { it.ref == openOrderRef }
    val isReviewing = reviewOrder != null
    val onBackToQueue = { openOrderRef = -1 }

    BackHandler(enabled = selectedTab == PharmTab.ORDERS && isReviewing) {
        openOrderRef = -1
    }

    // Keeps the relative timestamp honest without a manual pull-to-refresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun touch() {
        lastUpdated = System.currentTimeMillis()
        now = lastUpdated
    }

    fun setAll(value: Boolean) {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            delay(700)
            stockCatalog.forEach { inStock[it.id] = value }
            isRefreshing = false
            touch()
            snackbarHostState.showSnackbar(
                if (value) "All medicines marked as In Stock." else "All medicines marked as Out of Stock."
            )
        }
    }

    fun refresh() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            delay(900)
            isRefreshing = false
            touch()
            snackbarHostState.showSnackbar("Stock refreshed")
        }
    }

    val inStockCount = inStock.values.count { it }
    val filtered = remember(query) { stockCatalog.filter { it.matches(query) } }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PharmacistTopBar(
                title = when (selectedTab) {
                    PharmTab.INVENTORY -> "Pharmacy Stock"
                    PharmTab.ORDERS -> reviewOrder?.let { "Order #REF-${it.ref}" } ?: "Live Orders"
                    else -> selectedTab.label
                },
                showInventoryActions = selectedTab == PharmTab.INVENTORY,
                onBack = if (isReviewing) onBackToQueue else null,
                showNotification = selectedTab == PharmTab.ORDERS,
                notificationCount = orderBook.unread.intValue,
                onNotificationClick = {
                    scope.launch { snackbarHostState.showSnackbar(orderBook.reviewAlerts()) }
                },
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
                onMarkAllIn = { menuOpen = false; setAll(true) },
                onMarkAllOut = { menuOpen = false; setAll(false) },
                onRefresh = { menuOpen = false; refresh() }
            )
        },
        bottomBar = {
            Column {
                if (selectedTab == PharmTab.INVENTORY) {
                    BulkActionBar(
                        isRefreshing = isRefreshing,
                        onMarkAllIn = { setAll(true) },
                        onMarkAllOut = { setAll(false) }
                    )
                }
                PharmacistBottomBar(selectedTab = selectedTab, onTabSelected = {
                    selectedTab = it
                    if (it != PharmTab.ORDERS) openOrderRef = -1
                })
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isRefreshing || (selectedTab == PharmTab.ORDERS && orderBook.isLoading.value)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = ForestEmerald,
                    trackColor = ForestEmerald.copy(alpha = 0.15f)
                )
            }

            when (selectedTab) {
                PharmTab.INVENTORY -> StockListPane(
                    items = filtered,
                    inStock = inStock,
                    totalInStock = inStockCount,
                    total = stockCatalog.size,
                    searchActive = searchOpen || query.isNotBlank(),
                    lastUpdatedLabel = relativeTime(lastUpdated, now),
                    isRefreshing = isRefreshing,
                    onRefresh = ::refresh,
                    onToggle = { item ->
                        inStock[item.id] = !(inStock[item.id] ?: item.defaultInStock)
                        touch()
                    }
                )

                PharmTab.PROFILE -> ProfilePane(onLogout = onLogout)

                PharmTab.ORDERS -> {
                    val order = reviewOrder
                    if (order != null) {
                        OrderReviewPane(
                            book = orderBook,
                            order = order,
                            onBack = onBackToQueue
                        )
                    } else {
                        OrdersQueuePane(
                            book = orderBook,
                            onOpenOrder = { ref -> openOrderRef = ref }
                        )
                    }
                }

                else -> PendingPharmTab(tab = selectedTab)
            }
        }
    }
}

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
    inStock: Map<Int, Boolean>,
    totalInStock: Int,
    total: Int,
    searchActive: Boolean,
    lastUpdatedLabel: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggle: (StockItem) -> Unit
) {
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
            items(items, key = { it.id }) { item ->
                val available = inStock[item.id] ?: item.defaultInStock
                MedicineStockRow(
                    item = item,
                    inStock = available,
                    onToggle = { onToggle(item) }
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
private fun MedicineStockRow(item: StockItem, inStock: Boolean, onToggle: () -> Unit) {
    val statusLabel = if (inStock) "In Stock" else "Out of Stock"
    val statusColor = if (inStock) SuccessGreen else SlateMedium

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${item.name}, ${item.strength}, ${item.priceLabel}, $statusLabel"
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
                    checked = inStock,
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
        }
    }
}

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
            text = if (searchActive) "Try a different name or strength." else "Tap Refresh to reload the catalog.",
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
                border = BorderStroke(1.5.dp, SlateLight),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateMedium),
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
// Profile (holds Logout) + not-yet-built tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfilePane(onLogout: () -> Unit) {
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
                        text = "SF",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Sunimal Fernando",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = DarkSlate,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Pharmacist · MediFlow Pharmacy, Negombo",
                        fontSize = 12.5.sp,
                        color = SlateMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        MediFlowPrimaryButton(
            text = "Logout",
            icon = Icons.AutoMirrored.Rounded.Logout,
            contentDescription = "Log out of the pharmacist account",
            onClick = onLogout
        )
    }
}

@Composable
private fun PendingPharmTab(tab: PharmTab) {
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
            text = "This screen is next in the pharmacist build.",
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
