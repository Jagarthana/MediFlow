package com.mediflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 16 — Admin Add Medicine Master (Screen A3 / Variant B)
 *
 * Requirements:
 *  • Top Bar: "Add Drug to Catalog"
 *  • Form Fields:
 *      - Generic Name (NMRA Standard): Dropdown/autocomplete (Default: "Paracetamol")
 *      - Brand Name: "Panadol"
 *      - Required Dosage: "500mg" (numeric/text input with unit selector)
 *      - Dosage Form: Dropdown (Tablet, Capsule, Syrup, Injection, Cream, etc.)
 *      - Manufacturer: Dropdown / text
 *      - NMRA Approval Number: Text field
 *      - Category: Dropdown (Analgesic, Antibiotic, Antihypertensive, etc.)
 *      - Prescription Required: Toggle (Yes/No)
 *  • Full-width Primary Button: "Add to Drug Dictionary"
 *  • Info Note: "Duplicate prevention: System checks NMRA database for existing entries"
 *
 * Adheres strictly to WCAG 2.1 AA accessibility guidelines and MediFlow theme standards.
 */

// ─────────────────────────────────────────────────────────────────────────────
// NMRA Standard Drug Catalogs & Reference Data
// ─────────────────────────────────────────────────────────────────────────────

private val nmraGenericNames = listOf(
    "Paracetamol",
    "Amoxicillin",
    "Metformin Hydrochloride",
    "Atorvastatin",
    "Omeprazole",
    "Salbutamol",
    "Cetirizine Hydrochloride",
    "Ibuprofen",
    "Azithromycin",
    "Ciprofloxacin",
    "Losartan Potassium",
    "Pantoprazole Sodium",
    "Amlodipine Besylate",
    "Doxycycline Hyclate"
)

private val dosageForms = listOf(
    "Tablet",
    "Capsule",
    "Syrup",
    "Injection",
    "Cream",
    "Ointment",
    "Inhaler",
    "Suspension",
    "Eye Drops"
)

private val drugCategories = listOf(
    "Analgesic & Antipyretic",
    "Antibiotic / Anti-infective",
    "Antihypertensive",
    "Antidiabetic",
    "Antihistamine / Anti-allergy",
    "Gastrointestinal / Antacid",
    "Respiratory & Bronchodilator",
    "Cardiovascular Agent",
    "Dermatological / Topical"
)

private val commonManufacturers = listOf(
    "GlaxoSmithKline (GSK) Ceylon Ltd",
    "State Pharmaceuticals Manufacturing Corporation (SPMC)",
    "Astron Limited",
    "Cipla Limited",
    "Sun Pharmaceutical Industries",
    "Torrent Pharmaceuticals",
    "Novartis Pharma Services",
    "Sanofi Healthcare",
    "Himalaya Wellness Company"
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen Host
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddMedicineScreen(
    onBack: () -> Unit,
    onMedicineAdded: () -> Unit = onBack
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // Form fields state
    var genericName by rememberSaveable { mutableStateOf("Paracetamol") }
    var brandName by rememberSaveable { mutableStateOf("Panadol") }
    var dosageAmount by rememberSaveable { mutableStateOf("500") }
    var dosageUnit by rememberSaveable { mutableStateOf("mg") }
    var selectedDosageForm by rememberSaveable { mutableStateOf("Tablet") }
    var manufacturer by rememberSaveable { mutableStateOf("GlaxoSmithKline (GSK) Ceylon Ltd") }
    var nmraApprovalNumber by rememberSaveable { mutableStateOf("NMRA/REG/2024/0892") }
    var selectedCategory by rememberSaveable { mutableStateOf("Analgesic & Antipyretic") }
    var isPrescriptionRequired by rememberSaveable { mutableStateOf(false) }

    // Dropdown expansions
    var genericExpanded by remember { mutableStateOf(false) }
    var dosageFormExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var manufacturerExpanded by remember { mutableStateOf(false) }

    // Submission & Duplicate Checking states
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val fullDosage = "$dosageAmount$dosageUnit"

    // Validation
    fun validateForm(): Boolean {
        if (genericName.trim().isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please select or enter the Generic Name") }
            return false
        }
        if (brandName.trim().isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please enter the Brand Name") }
            return false
        }
        if (dosageAmount.trim().isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please enter the dosage strength") }
            return false
        }
        if (nmraApprovalNumber.trim().isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please enter the NMRA approval number") }
            return false
        }
        return true
    }

    fun handleAddMedicine() {
        focusManager.clearFocus()
        if (!validateForm()) return

        scope.launch {
            isSubmitting = true
            delay(1100) // Simulate NMRA database duplicate check & catalog insert
            isSubmitting = false
            showSuccessDialog = true
        }
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AdminAddMedicineTopBar(onBack = onBack)
        },
        bottomBar = {
            AdminAddMedicineBottomBar(
                isSubmitting = isSubmitting,
                onAddClick = ::handleAddMedicine
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Header Info Callout
            NmraCatalogHeaderCard()

            Spacer(Modifier.height(18.dp))

            // Generic Name (NMRA Standard Autocomplete / Dropdown)
            FormLabel(label = "Generic Name (NMRA Standard) *")
            ExposedDropdownMenuBox(
                expanded = genericExpanded,
                onExpandedChange = { genericExpanded = !genericExpanded }
            ) {
                OutlinedTextField(
                    value = genericName,
                    onValueChange = {
                        genericName = it
                        genericExpanded = true
                    },
                    placeholder = { Text("Search NMRA standard INN...", color = SlateLight, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Biotech, null, tint = HealthcareBlue) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genericExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = formTextFieldColors()
                )
                val filteredGenerics = nmraGenericNames.filter {
                    it.contains(genericName, ignoreCase = true)
                }
                if (filteredGenerics.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = genericExpanded,
                        onDismissRequest = { genericExpanded = false },
                        modifier = Modifier.background(SurfaceWhite)
                    ) {
                        filteredGenerics.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, color = DarkSlate, fontWeight = FontWeight.Medium)
                                        if (name.equals(genericName, ignoreCase = true)) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Rounded.Check, null, tint = HealthcareBlue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    genericName = name
                                    genericExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Brand Name
            FormLabel(label = "Brand / Commercial Name *")
            OutlinedTextField(
                value = brandName,
                onValueChange = { brandName = it },
                placeholder = { Text("e.g. Panadol, Calpol, Panadeine", color = SlateLight, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Medication, null, tint = HealthcareBlue) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = formTextFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            // Required Dosage & Unit
            FormLabel(label = "Required Dosage / Strength *")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dosageAmount,
                    onValueChange = { if (it.all { ch -> ch.isDigit() || ch == '.' }) dosageAmount = it },
                    placeholder = { Text("500", color = SlateLight, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Scale, null, tint = HealthcareBlue) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.weight(1.8f),
                    shape = RoundedCornerShape(12.dp),
                    colors = formTextFieldColors()
                )

                // Dosage Units (mg, ml, g, mcg, %, IU)
                val units = listOf("mg", "ml", "g", "mcg", "mg/5ml", "IU")
                var unitExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = !unitExpanded },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = dosageUnit,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = formTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false },
                        modifier = Modifier.background(SurfaceWhite)
                    ) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u, color = DarkSlate) },
                                onClick = {
                                    dosageUnit = u
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Quick dosage chips
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("250mg", "500mg", "650mg", "1g", "10mg").forEach { chip ->
                    val isSelected = fullDosage == chip
                    Surface(
                        modifier = Modifier.clickable {
                            val num = chip.filter { it.isDigit() }
                            val unit = chip.filter { !it.isDigit() }
                            dosageAmount = num
                            dosageUnit = unit
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) HealthcareBlue.copy(alpha = 0.12f) else SurfaceWhite,
                        border = BorderStroke(1.dp, if (isSelected) HealthcareBlue else BorderColor)
                    ) {
                        Text(
                            text = chip,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) HealthcareBlue else SlateMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Dosage Form Dropdown
            FormLabel(label = "Dosage Form *")
            ExposedDropdownMenuBox(
                expanded = dosageFormExpanded,
                onExpandedChange = { dosageFormExpanded = !dosageFormExpanded }
            ) {
                OutlinedTextField(
                    value = selectedDosageForm,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Rounded.Vaccines, null, tint = HealthcareBlue) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dosageFormExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = formTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = dosageFormExpanded,
                    onDismissRequest = { dosageFormExpanded = false },
                    modifier = Modifier.background(SurfaceWhite)
                ) {
                    dosageForms.forEach { form ->
                        DropdownMenuItem(
                            text = { Text(form, color = DarkSlate) },
                            onClick = {
                                selectedDosageForm = form
                                dosageFormExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Manufacturer Dropdown / Text
            FormLabel(label = "Manufacturer / Marketing Authorization Holder *")
            ExposedDropdownMenuBox(
                expanded = manufacturerExpanded,
                onExpandedChange = { manufacturerExpanded = !manufacturerExpanded }
            ) {
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = {
                        manufacturer = it
                        manufacturerExpanded = true
                    },
                    placeholder = { Text("Select or enter pharmaceutical manufacturer", color = SlateLight, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Factory, null, tint = HealthcareBlue) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manufacturerExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = formTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = manufacturerExpanded,
                    onDismissRequest = { manufacturerExpanded = false },
                    modifier = Modifier.background(SurfaceWhite)
                ) {
                    commonManufacturers.forEach { mfg ->
                        DropdownMenuItem(
                            text = { Text(mfg, color = DarkSlate, fontSize = 13.5.sp) },
                            onClick = {
                                manufacturer = mfg
                                manufacturerExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // NMRA Approval Number
            FormLabel(label = "NMRA Approval / Certificate Number *")
            OutlinedTextField(
                value = nmraApprovalNumber,
                onValueChange = { nmraApprovalNumber = it },
                placeholder = { Text("e.g. NMRA/REG/2024/0892", color = SlateLight, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Verified, null, tint = HealthcareBlue) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = formTextFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            // Therapeutic Category Dropdown
            FormLabel(label = "Therapeutic Category *")
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Rounded.Category, null, tint = HealthcareBlue) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = formTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                    modifier = Modifier.background(SurfaceWhite)
                ) {
                    drugCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, color = DarkSlate) },
                            onClick = {
                                selectedCategory = cat
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Prescription Required Toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, if (isPrescriptionRequired) AmberWarning.copy(alpha = 0.5f) else BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPrescriptionRequired) AmberWarning.copy(alpha = 0.14f)
                                else SuccessGreen.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPrescriptionRequired) Icons.AutoMirrored.Rounded.ReceiptLong else Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = if (isPrescriptionRequired) AmberWarning else SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prescription Required",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isPrescriptionRequired) "Schedule II — Prescription Only (POM)" else "Over-The-Counter (OTC) General Sale",
                            fontSize = 12.sp,
                            color = if (isPrescriptionRequired) AmberWarning else SlateMedium,
                            fontWeight = if (isPrescriptionRequired) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    Switch(
                        checked = isPrescriptionRequired,
                        onCheckedChange = { isPrescriptionRequired = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AmberWarning,
                            uncheckedThumbColor = SlateLight,
                            uncheckedTrackColor = LightBackground
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Info Note: Duplicate Prevention
            DuplicatePreventionInfoBanner(
                genericName = genericName,
                dosage = fullDosage,
                form = selectedDosageForm
            )

            Spacer(Modifier.height(30.dp))
        }
    }

    if (showSuccessDialog) {
        AddMedicineSuccessDialog(
            brandName = brandName,
            genericName = genericName,
            dosage = fullDosage,
            form = selectedDosageForm,
            approvalNumber = nmraApprovalNumber,
            onDismiss = {
                showSuccessDialog = false
                onMedicineAdded()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminAddMedicineTopBar(onBack: () -> Unit) {
    Surface(color = SurfaceWhite, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Back" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = DarkSlate
                )
            }

            Spacer(Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add Drug to Catalog",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "National Medicine Regulatory Authority (NMRA) Master Dictionary",
                    fontSize = 11.5.sp,
                    color = HealthcareBlue,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NmraCatalogHeaderCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                    .background(HealthcareBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalPharmacy,
                    contentDescription = null,
                    tint = HealthcareBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Master Drug Catalog Entry",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "New additions will instantly synchronize across all hospital & pharmacy stock switchers.",
                    fontSize = 12.sp,
                    color = SlateMedium,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Duplicate Prevention Info Note
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DuplicatePreventionInfoBanner(
    genericName: String,
    dosage: String,
    form: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HealthcareBlue.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, HealthcareBlue.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = HealthcareBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Duplicate Prevention Active",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HealthcareBlue
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Duplicate prevention: System checks NMRA database for existing entries before registration.",
                        fontSize = 12.sp,
                        color = DarkSlate,
                        lineHeight = 16.sp
                    )
                }
            }

            if (genericName.isNotBlank() && dosage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceWhite
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Validated: $genericName $dosage $form ready for dictionary insertion.",
                            fontSize = 11.5.sp,
                            color = DarkSlate,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Action Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdminAddMedicineBottomBar(
    isSubmitting: Boolean,
    onAddClick: () -> Unit
) {
    Surface(
        color = SurfaceWhite,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onAddClick,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Add to Drug Dictionary" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HealthcareBlue,
                    contentColor = Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking NMRA Ledger & Adding...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AddCircle,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add to Drug Dictionary",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Success Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddMedicineSuccessDialog(
    brandName: String,
    genericName: String,
    dosage: String,
    form: String,
    approvalNumber: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(HealthcareBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = HealthcareBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Medicine Added!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "$brandName ($genericName $dosage $form) has been registered under $approvalNumber in the National Drug Dictionary.",
                    fontSize = 13.sp,
                    color = SlateMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = LightBackground,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Sync, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Catalog Synced · Available for pharmacy inventory mapping",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkSlate
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HealthcareBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Done & Back to Admin Console",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Common Helper UI Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormLabel(label: String) {
    Text(
        text = label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkSlate,
        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
    )
}

@Composable
private fun formTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HealthcareBlue,
    unfocusedBorderColor = BorderColor,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite,
    cursorColor = HealthcareBlue,
    focusedTextColor = DarkSlate,
    unfocusedTextColor = DarkSlate
)
