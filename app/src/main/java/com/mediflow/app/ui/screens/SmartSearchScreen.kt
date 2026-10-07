package com.mediflow.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.components.PrescriptionThumbnail
import com.mediflow.app.ui.theme.*

/**
 * Screen 6 — Smart Medicine Search (Variant B / Figma 3B)
 *
 * Layout, top to bottom:
 *  • Top Bar    — back arrow + "Search Essential Medicines"
 *  • Search     — magnifier, high-contrast placeholder, clear button
 *  • Autocomplete — live-filtered matches while the field carries a query
 *  • Variants   — strength/form pills, revealed once a medicine is picked
 *  • Slip       — attached prescription thumbnail, only when one was captured
 *  • Bottom     — NMRA hint + "Proceed to Verification"
 *
 * [sampleMedicines] is UI-phase scaffolding standing in for the NMRA essential
 * medicines list the search endpoint will return later.
 *
 * WCAG 2.1 AA: 48dp+ touch targets, semantic labels on every control,
 * placeholder rendered in DarkSlate rather than the usual low-contrast grey.
 */

private data class Medicine(
    val name: String,
    val strength: String,
    val form: String,
    val brand: String? = null,
    val variants: List<String>
) {
    val title: String get() = if (brand == null) "$name $strength" else "$name $strength ($brand)"
    val caption: String get() = "$form · $strength"

    /** Used once a variant is chosen, where the variant itself carries the strength. */
    val heading: String get() = if (brand == null) name else "$name ($brand)"

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        return needle.isEmpty() ||
            name.lowercase().contains(needle) ||
            brand?.lowercase()?.contains(needle) == true ||
            strength.lowercase().contains(needle) ||
            form.lowercase().contains(needle)
    }

    /**
     * Prefix hits first: "amo" must surface Amoxicillin, not every medicine
     * that happens to contain those letters ("parac`amo`lol").
     */
    fun relevance(query: String): Int {
        val needle = query.trim().lowercase()
        return when {
            name.lowercase().startsWith(needle) -> 0
            brand?.lowercase()?.startsWith(needle) == true -> 1
            else -> 2
        }
    }
}

private val sampleMedicines = listOf(
    Medicine("Paracetamol", "500mg", "Tablet", brand = "Panadol",
        variants = listOf("500mg Tablet", "650mg Tablet")),
    Medicine("Paracetamol", "120mg/5ml", "Syrup",
        variants = listOf("120mg/5ml Syrup", "250mg/5ml Syrup")),
    Medicine("Amoxicillin", "500mg", "Capsule", brand = "Amoxil",
        variants = listOf("250mg Capsule", "500mg Capsule")),
    Medicine("Ibuprofen", "400mg", "Tablet", brand = "Brufen",
        variants = listOf("400mg Tablet", "600mg Tablet")),
    Medicine("Cetirizine", "10mg", "Tablet", brand = "Zyrtec",
        variants = listOf("10mg Tablet")),
    Medicine("Azithromycin", "250mg", "Tablet", brand = "Zithromax",
        variants = listOf("250mg Tablet", "500mg Tablet")),
    Medicine("Omeprazole", "20mg", "Capsule", brand = "Losec",
        variants = listOf("20mg Capsule", "40mg Capsule")),
    Medicine("Metformin", "500mg", "Tablet", brand = "Glucophage",
        variants = listOf("500mg Tablet", "850mg Tablet", "1000mg Tablet")),
    Medicine("Amlodipine", "5mg", "Tablet", brand = "Norvasc",
        variants = listOf("5mg Tablet", "10mg Tablet")),
    Medicine("Losartan", "50mg", "Tablet", brand = "Cozaar",
        variants = listOf("50mg Tablet", "100mg Tablet"))
)

private const val MAX_VISIBLE_RESULTS = 6

@Composable
fun SmartSearchScreen(
    prescriptionAttached: Boolean,
    onBack: () -> Unit,
    onChangePhoto: () -> Unit,
    onProceed: (label: String, variant: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Medicine?>(null) }
    var variant by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val matches = remember(query) {
        sampleMedicines.filter { it.matches(query) }
            .sortedBy { it.relevance(query) }
            .take(MAX_VISIBLE_RESULTS)
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SearchTopBar(onBack = onBack) },
        bottomBar = {
            SearchFooter(
                enabled = selected != null && variant != null,
                onProceed = {
                    val medicine = selected
                    val chosen = variant
                    if (medicine != null && chosen != null) {
                        onProceed(medicine.heading, chosen)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            MedicineSearchField(
                query = query,
                onQueryChange = {
                    query = it
                    showResults = true
                    if (it.isEmpty()) {
                        selected = null
                        variant = null
                    }
                },
                onClear = {
                    query = ""
                    showResults = false
                    selected = null
                    variant = null
                    focusManager.clearFocus()
                },
                onSubmit = {
                    showResults = false
                    focusManager.clearFocus()
                }
            )

            Spacer(Modifier.height(10.dp))

            if (showResults && query.isNotBlank()) {
                if (matches.isEmpty()) {
                    NoMatchesLabel(query = query)
                } else {
                    ResultCard(
                        medicines = matches,
                        selected = selected,
                        onSelect = { medicine ->
                            selected = medicine
                            variant = null
                            query = medicine.name
                            showResults = false
                            focusManager.clearFocus()
                        }
                    )
                }
            }

            val chosen = selected
            if (chosen != null) {
                Spacer(Modifier.height(14.dp))
                SelectedMedicineCard(
                    medicine = chosen,
                    variant = variant,
                    onVariantSelected = { variant = it }
                )
            }

            if (prescriptionAttached) {
                Spacer(Modifier.height(14.dp))
                AttachedSlipCard(onChangePhoto = onChangePhoto)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchTopBar(onBack: () -> Unit) {
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
                .semantics { contentDescription = "Back" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = DarkSlate,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Search Essential Medicines",
            style = MaterialTheme.typography.titleLarge.copy(
                color = DarkSlate,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MedicineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "Search medicine" },
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(
            width = if (focused) 1.5.dp else 1.dp,
            color = if (focused) ForestEmerald else BorderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (focused) ForestEmerald else SlateMedium,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(ForestEmerald),
                textStyle = TextStyle(
                    color = DarkSlate,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            // DarkSlate, not the usual grey: the spec calls out
                            // placeholder contrast explicitly for WCAG AA.
                            Text(
                                text = "Search medicine...",
                                color = DarkSlate,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Clear search" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = null,
                        tint = SlateMedium,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Autocomplete
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    medicines: List<Medicine>,
    selected: Medicine?,
    onSelect: (Medicine) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column {
            Text(
                text = "MATCHING MEDICINES",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            medicines.forEachIndexed { index, medicine ->
                ResultRow(
                    medicine = medicine,
                    isSelected = medicine == selected,
                    onClick = { onSelect(medicine) }
                )
                if (index != medicines.lastIndex) {
                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(medicine: Medicine, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Select ${medicine.title}" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSelected) ForestEmerald else MintGreen.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (isSelected) Color.White else ForestEmerald,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = medicine.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )
            Text(
                text = medicine.caption,
                fontSize = 12.5.sp,
                color = SlateMedium
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = SlateLight,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun NoMatchesLabel(query: String) {
    Text(
        text = "No medicine matches \"$query\".",
        fontSize = 13.5.sp,
        color = SlateMedium,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Strength / form selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectedMedicineCard(
    medicine: Medicine,
    variant: String?,
    onVariantSelected: (String) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SELECTED MEDICINE",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = medicine.heading,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Choose strength and form",
                fontSize = 13.sp,
                color = SlateMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                medicine.variants.forEach { option ->
                    VariantPill(
                        label = option,
                        isSelected = option == variant,
                        onClick = { onVariantSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(48.dp)
            .semantics { contentDescription = "$label, strength option" },
        shape = CircleShape,
        color = if (isSelected) ForestEmerald else LightBackground,
        border = BorderStroke(
            1.dp,
            if (isSelected) ForestEmerald else BorderColor
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else DarkSlate
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Attached slip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AttachedSlipCard(onChangePhoto: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Attached prescription slip" },
        shape = shape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrescriptionThumbnail(modifier = Modifier.size(width = 56.dp, height = 68.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Prescription attached",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkSlate
                )
                Text(
                    text = "We will verify this against your search",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
                TextButton(
                    onClick = onChangePhoto,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .semantics { contentDescription = "Change photo" },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Change Photo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestEmerald
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchFooter(enabled: Boolean, onProceed: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = SurfaceWhite,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = SlateMedium,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Can't find your medicine? Try searching by generic name (NMRA approved)",
                    fontSize = 12.5.sp,
                    color = SlateMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onProceed,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Proceed to verification" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.White,
                    disabledContainerColor = BorderColor,
                    disabledContentColor = SlateLight
                )
            ) {
                Text(
                    text = "Proceed to Verification",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
