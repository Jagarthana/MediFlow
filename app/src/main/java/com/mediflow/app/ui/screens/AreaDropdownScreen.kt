package com.mediflow.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*

/**
 * Screen 8 — Area Picker (Variant B / Figma 4)
 *
 * Three cascading pickers — Province → District → Town — each opening a
 * searchable bottom sheet, plus quick-select chips for areas the patient has
 * used before.
 *
 * Location is chosen by hand on purpose: the app never asks for GPS. That is a
 * privacy stance for an app that associates a person with the medicines they
 * buy, and it is stated on screen so the patient can see it.
 *
 * [areaTree] mirrors the NMRA-style administrative division the stock lookup
 * will eventually be queried against.
 *
 * WCAG 2.1 AA: every picker is a full-width 56dp target, chips are 40dp+, and
 * the disabled CTA keeps a legible label.
 */

private data class District(val name: String, val towns: List<String>)

private data class Province(val name: String, val districts: List<District>)

private val areaTree = listOf(
    Province(
        "Western Province",
        listOf(
            District(
                "Colombo District",
                listOf("Colombo", "Colombo 07", "Dehiwala-Mount Lavinia", "Moratuwa", "Kaduwela", "Maharagama", "Homagama")
            ),
            District(
                "Gampaha District",
                listOf("Gampaha", "Negombo", "Wattala", "Ja-Ela", "Hendala", "Kadawatha")
            ),
            District(
                "Kalutara District",
                listOf("Kalutara", "Panadura", "Wadduwa", "Beruwala", "Ingiriya")
            )
        )
    ),
    Province(
        "North Western Province",
        listOf(
            District(
                "Kurunegala District",
                listOf("Kurunegala", "Ibbagamuwa", "Mawathagama", "Nikitasthena", "Galewata")
            ),
            District("Puttalam District", listOf("Puttalam", "Chilaw", "Wennappuwa"))
        )
    ),
    Province(
        "Central Province",
        listOf(
            District("Kandy District", listOf("Kandy", "Gampola", "Nawalapitiya", "Kadugannawa", "Peradeniya")),
            District("Matale District", listOf("Matale", "Dambulla", "Nanu Oya")),
            District("Nuwara Eliya District", listOf("Nuwara Eliya", "Hatton"))
        )
    ),
    Province(
        "Southern Province",
        listOf(
            District("Galle District", listOf("Galle", "Ambalangoda", "Hikkaduwa", "Talpe")),
            District("Matara District", listOf("Matara", "Dikwella", "Welipatha")),
            District("Hambantota District", listOf("Hambantota", "Tangalle", "Tissamaharama"))
        )
    ),
    Province(
        "Sabaragamuwa Province",
        listOf(
            District("Ratnapura District", listOf("Ratnapura", "Balangoda", "Embilipitiya")),
            District("Kegalle District", listOf("Kegalle", "Warakapola", "Mawanella"))
        )
    ),
    Province(
        "North Central Province",
        listOf(
            District("Anuradhapura District", listOf("Anuradhapura", "Mihintale", "Thambuttegama")),
            District("Polonnaruwa District", listOf("Polonnaruwa", "Hingurakgoda"))
        )
    ),
    Province(
        "Eastern Province",
        listOf(
            District("Trincomalee District", listOf("Trincomalee", "China Bay", "Kantale")),
            District("Batticaloa District", listOf("Batticaloa", "Katankudi")),
            District("Ampara District", listOf("Ampara", "Kalmunai", "Sammanthurai"))
        )
    ),
    Province(
        "Northern Province",
        listOf(
            District("Jaffna District", listOf("Jaffna", "Nallur", "Chavakachcheri")),
            District("Vavuniya District", listOf("Vavuniya", "Omanthai"))
        )
    ),
    Province(
        "Uva Province",
        listOf(
            District("Badulla District", listOf("Badulla", "Bandarawela", "Haputale")),
            District("Monaragala District", listOf("Monaragala", "Wellawaya"))
        )
    )
)

private val recentAreas = listOf("Negombo", "Wattala", "Colombo 07", "Kandy")

private enum class Picker { PROVINCE, DISTRICT, TOWN }

@Composable
fun AreaDropdownScreen(
    onBack: () -> Unit,
    onApply: (province: String, district: String, town: String) -> Unit
) {
    var province by remember { mutableStateOf<Province?>(null) }
    var district by remember { mutableStateOf<District?>(null) }
    var town by remember { mutableStateOf<String?>(null) }
    var openPicker by remember { mutableStateOf<Picker?>(null) }

    Scaffold(
        containerColor = LightBackground,
        topBar = { AreaTopBar(onBack = onBack) },
        bottomBar = {
            AreaFooter(
                enabled = province != null && district != null && town != null,
                onApply = {
                    val p = province
                    val d = district
                    val t = town
                    if (p != null && d != null && t != null) onApply(p.name, d.name, t)
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
            Text(
                text = "Pick the area to check stock in.",
                fontSize = 14.sp,
                color = SlateMedium,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            AreaField(
                label = "Province",
                value = province?.name,
                placeholder = "Select province",
                onClick = { openPicker = Picker.PROVINCE }
            )
            Spacer(Modifier.height(14.dp))

            AreaField(
                label = "District",
                value = district?.name,
                placeholder = if (province == null) "Choose a province first" else "Select district",
                enabled = province != null,
                onClick = { openPicker = Picker.DISTRICT }
            )
            Spacer(Modifier.height(14.dp))

            AreaField(
                label = "Town",
                value = town,
                placeholder = if (district == null) "Choose a district first" else "Select town",
                enabled = district != null,
                onClick = { openPicker = Picker.TOWN }
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "RECENT AREAS",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateMedium
            )
            Spacer(Modifier.height(10.dp))
            RecentAreaChips { picked ->
                areaTree.forEach { p ->
                    p.districts.forEach { d ->
                        if (picked in d.towns) {
                            province = p
                            district = d
                            town = picked
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            PrivacyNotice()

            Spacer(Modifier.height(24.dp))
        }
    }

    val picker = openPicker
    if (picker != null) {
        val options = when (picker) {
            Picker.PROVINCE -> areaTree.map { it.name }
            Picker.DISTRICT -> province?.districts?.map { it.name }.orEmpty()
            Picker.TOWN -> district?.towns.orEmpty()
        }
        OptionSheet(
            title = when (picker) {
                Picker.PROVINCE -> "Select Province"
                Picker.DISTRICT -> "Select District"
                Picker.TOWN -> "Select Town"
            },
            options = options,
            current = when (picker) {
                Picker.PROVINCE -> province?.name
                Picker.DISTRICT -> district?.name
                Picker.TOWN -> town
            },
            onPick = { option ->
                when (picker) {
                    Picker.PROVINCE -> {
                        province = areaTree.firstOrNull { it.name == option }
                        district = null
                        town = null
                    }
                    Picker.DISTRICT -> {
                        district = province?.districts?.firstOrNull { it.name == option }
                        town = null
                    }
                    Picker.TOWN -> town = option
                }
                openPicker = null
            },
            onDismiss = { openPicker = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AreaTopBar(onBack: () -> Unit) {
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
                .semantics { contentDescription = "Back to reservation review" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = DarkSlate,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Select Your Area",
            style = MaterialTheme.typography.titleLarge.copy(
                color = DarkSlate,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cascading picker fields
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AreaField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    // A read-only field that is empty also keeps its label at rest, which hides
    // the placeholder Material3 would otherwise draw. Rendering the hint as the
    // value keeps the label floated and the hint visible.
    val shown = value ?: placeholder
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label picker" }
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            textStyle = TextStyle(
                color = if (value == null && !enabled) SlateLight else DarkSlate,
                fontSize = 16.sp,
                fontWeight = if (value == null) FontWeight.Normal else FontWeight.Medium
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) ForestEmerald else SlateLight,
                    modifier = Modifier.size(26.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestEmerald,
                unfocusedBorderColor = BorderColor,
                disabledBorderColor = BorderColor.copy(alpha = 0.6f),
                focusedLabelColor = ForestEmerald,
                unfocusedLabelColor = SlateMedium,
                disabledLabelColor = SlateLight,
                cursorColor = ForestEmerald,
                unfocusedTextColor = if (enabled) DarkSlate else SlateLight
            ),
            modifier = Modifier.fillMaxWidth()
        )
        // Swallows the tap so the field never takes focus and raises the
        // keyboard — it is a button wearing a text field's clothes.
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClick)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Searchable bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    title: String,
    options: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf("") }
    val visible = remember(options, filter) {
        val needle = filter.trim().lowercase()
        if (needle.isEmpty()) options else options.filter { it.lowercase().contains(needle) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )
            Spacer(Modifier.height(12.dp))

            SheetSearchField(
                query = filter,
                onQueryChange = { filter = it },
                fieldLabel = title
            )

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = BorderColor, thickness = 1.dp)

            if (visible.isEmpty()) {
                Text(
                    text = "No match for \"$filter\".",
                    fontSize = 14.sp,
                    color = SlateMedium,
                    modifier = Modifier.padding(vertical = 22.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(visible) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(option) }
                                .semantics { contentDescription = "Choose $option" }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                fontSize = 15.5.sp,
                                fontWeight = if (option == current) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (option == current) ForestEmerald else DarkSlate,
                                modifier = Modifier.weight(1f)
                            )
                            if (option == current) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = ForestEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetSearchField(query: String, onQueryChange: (String) -> Unit, fieldLabel: String) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = "Search within $fieldLabel" },
        shape = shape,
        color = LightBackground,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = SlateMedium,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(ForestEmerald),
                textStyle = TextStyle(color = DarkSlate, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Search...", color = DarkSlate, fontSize = 15.sp)
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Clear area search" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = null,
                        tint = SlateMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent areas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentAreaChips(onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        recentAreas.forEach { area ->
            Surface(
                modifier = Modifier
                    .height(44.dp)
                    .semantics { contentDescription = "Recent area $area" },
                shape = CircleShape,
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderColor),
                onClick = { onPick(area) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = ForestEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = area,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkSlate
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Privacy notice
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyNotice() {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Privacy notice: no GPS location is required" },
        shape = shape,
        color = MintGreen.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\uD83D\uDCCD", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "We respect your privacy. No GPS required.",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = DarkSlate,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AreaFooter(enabled: Boolean, onApply: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = SurfaceWhite,
        shadowElevation = 8.dp
    ) {
        Button(
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp)
                .semantics { contentDescription = "Apply area filter" },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ForestEmerald,
                contentColor = Color.White,
                disabledContainerColor = BorderColor,
                disabledContentColor = SlateMedium
            )
        ) {
            Text(
                text = "Apply Filter",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
