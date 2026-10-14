package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.ui.theme.AmberWarning
import com.mediflow.app.ui.theme.BorderColor
import com.mediflow.app.ui.theme.DarkSlate
import com.mediflow.app.ui.theme.ErrorRed
import com.mediflow.app.ui.theme.ForestEmerald
import com.mediflow.app.ui.theme.HealthcareBlue
import com.mediflow.app.ui.theme.LightBackground
import com.mediflow.app.ui.theme.SlateLight
import com.mediflow.app.ui.theme.SlateMedium
import com.mediflow.app.ui.theme.SurfaceWhite
import java.util.Locale

/**
 * Sri Lankan administrative districts and corresponding primary towns for dropdown selection.
 */
private val sriLankaDistrictsMap = listOf(
    "Colombo" to listOf("Colombo", "Colombo 07", "Dehiwala-Mount Lavinia", "Moratuwa", "Kaduwela", "Maharagama", "Homagama", "Nugegoda", "Rajagiriya"),
    "Gampaha" to listOf("Gampaha", "Negombo", "Wattala", "Ja-Ela", "Hendala", "Kadawatha", "Kelaniya"),
    "Kalutara" to listOf("Kalutara", "Panadura", "Wadduwa", "Beruwala", "Horana"),
    "Kandy" to listOf("Kandy", "Peradeniya", "Gampola", "Katugastota", "Nawalapitiya"),
    "Matale" to listOf("Matale", "Dambulla", "Sigiriya"),
    "Nuwara Eliya" to listOf("Nuwara Eliya", "Hatton", "Talawakele"),
    "Galle" to listOf("Galle", "Ambalangoda", "Hikkaduwa", "Karapitiya", "Baddegama"),
    "Matara" to listOf("Matara", "Weligama", "Dikwella"),
    "Hambantota" to HambantotaTowns(),
    "Jaffna" to listOf("Jaffna", "Nallur", "Chavakachcheri", "Point Pedro"),
    "Kilinochchi" to listOf("Kilinochchi", "Paranthan"),
    "Mannar" to listOf("Mannar", "Talaimannar"),
    "Vavuniya" to listOf("Vavuniya", "Cheddikulam"),
    "Mullaitivu" to listOf("Mullaitivu", "Puthukkudiyiruppu"),
    "Batticaloa" to listOf("Batticaloa", "Kattankudy", "Eravur"),
    "Ampara" to listOf("Ampara", "Kalmunai", "Sammanthurai"),
    "Trincomalee" to listOf("Trincomalee", "Kinniya", "Kantale"),
    "Kurunegala" to listOf("Kurunegala", "Kuliyapitiya", "Pannala", "Narammala"),
    "Puttalam" to listOf("Puttalam", "Chilaw", "Marawila", "Wennappuwa"),
    "Anuradhapura" to listOf("Anuradhapura", "Mihintale", "Kekirawa"),
    "Polonnaruwa" to listOf("Polonnaruwa", "Kaduruwela", "Hingurakgoda"),
    "Badulla" to listOf("Badulla", "Bandarawela", "Haputale", "Ella"),
    "Monaragala" to listOf("Monaragala", "Wellawaya", "Bibile"),
    "Ratnapura" to listOf("Ratnapura", "Balangoda", "Embilipitiya"),
    "Kegalle" to listOf("Kegalle", "Mawanella", "Warakapola")
)

private fun HambantotaTowns() = listOf("Hambantota", "Tangalle", "Beliatta", "Ambalantota")

/**
 * Screen 17b — Pharmacy Detail/Edit Screen
 *
 * Bound to a `pharmacy` document: the fields are prefilled from the record, Save writes
 * the whole document back with only the editable columns changed, and Approve / Reject /
 * Deactivate set `status` through the console's repository. The badge therefore reflects
 * what Firestore holds, not what was last tapped.
 *
 * - District and Town dropdown selection
 * - "Approve" / "Reject" buttons displayed only if status == "pending"
 * - "Deactivate Pharmacy" displayed only if status == "approved", with confirmation dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyDetailScreen(
    pharmacy: PharmacyInfo,
    onSave: (PharmacyInfo) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDeactivate: () -> Unit,
    onBack: (() -> Unit)? = null,
    isBusy: Boolean = false
) {
    var name by remember(pharmacy) { mutableStateOf(pharmacy.name) }
    var slmcReg by remember(pharmacy) { mutableStateOf(pharmacy.licenceNumber) }
    var slmcPin by remember(pharmacy) { mutableStateOf(pharmacy.slmcPin.orEmpty()) }
    var selectedDistrict by remember(pharmacy) { mutableStateOf(pharmacy.district) }
    var selectedTown by remember(pharmacy) { mutableStateOf(pharmacy.areaLabel) }
    var address by remember(pharmacy) { mutableStateOf(pharmacy.address) }
    var contact by remember(pharmacy) { mutableStateOf(pharmacy.phone) }
    var email by remember(pharmacy) { mutableStateOf(pharmacy.email.orEmpty()) }

    var isPinVisible by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }

    var isDistrictDropdownOpen by remember { mutableStateOf(false) }
    var isTownDropdownOpen by remember { mutableStateOf(false) }

    // Towns available for currently selected district
    val availableTowns = sriLankaDistrictsMap.find {
        it.first.equals(selectedDistrict, ignoreCase = true)
    }?.second ?: listOf(selectedTown).filter { it.isNotBlank() }

    val statusColor = when (pharmacy.status.lowercase(Locale.ROOT)) {
        "approved" -> ForestEmerald
        "pending" -> AmberWarning
        "rejected" -> Color(0xFF6B7280) // Gray
        else -> SlateMedium
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pharmacy Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DarkSlate
                        )
                        if (name.isNotBlank()) {
                            Text(
                                text = name,
                                fontSize = 12.5.sp,
                                color = SlateMedium
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Navigate back",
                                tint = DarkSlate
                            )
                        }
                    }
                },
                actions = {
                    // Status Badge in Top Bar
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = pharmacy.status.uppercase(Locale.ROOT),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Action: Save Changes
                    Button(
                        onClick = {
                            onSave(
                                pharmacy.copy(
                                    name = name.trim(),
                                    licenceNumber = slmcReg.trim(),
                                    slmcPin = slmcPin.trim().ifBlank { null },
                                    district = selectedDistrict.trim(),
                                    areaLabel = selectedTown.trim(),
                                    address = address.trim(),
                                    phone = contact.trim(),
                                    email = email.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestEmerald,
                            contentColor = SurfaceWhite
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SurfaceWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isBusy) "Saving…" else "Save Changes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp
                        )
                    }

                    // Approve / Reject buttons — shown only if status == "pending"
                    if (pharmacy.status.equals("pending", ignoreCase = true)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Approve Button
                            OutlinedButton(
                                onClick = onApprove,
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, ForestEmerald),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestEmerald)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            // Reject Button
                            OutlinedButton(
                                onClick = onReject,
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, ErrorRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                            ) {
                                Text("Reject", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Deactivate Pharmacy button — shown only if status == "approved"
                    if (pharmacy.status.equals("approved", ignoreCase = true)) {
                        OutlinedButton(
                            onClick = { showDeactivateDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, ErrorRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deactivate Pharmacy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Pharmacy Registration
            FormSectionCard(
                title = "Registration Details",
                subtitle = "Official regulatory identification"
            ) {
                // Pharmacy Name
                FormFieldLabel(label = "Pharmacy Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Storefront, contentDescription = null, tint = ForestEmerald)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = detailTextFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SLMC Reg #
                FormFieldLabel(label = "SLMC Registration #")
                OutlinedTextField(
                    value = slmcReg,
                    onValueChange = { slmcReg = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Badge, contentDescription = null, tint = SlateMedium)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = detailTextFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SLMC PIN
                FormFieldLabel(label = "SLMC PIN")
                OutlinedTextField(
                    value = slmcPin,
                    onValueChange = { if (it.length <= 6) slmcPin = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Pin, contentDescription = null, tint = SlateMedium)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                                tint = SlateMedium
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    colors = detailTextFieldColors()
                )
            }

            // Section 2: Location & Contact Information
            FormSectionCard(
                title = "Location & Contact",
                subtitle = "Geographic district and contact points"
            ) {
                // District Dropdown
                FormFieldLabel(label = "District")
                ExposedDropdownMenuBox(
                    expanded = isDistrictDropdownOpen,
                    onExpandedChange = { isDistrictDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value = selectedDistrict,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDistrictDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = detailTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = isDistrictDropdownOpen,
                        onDismissRequest = { isDistrictDropdownOpen = false }
                    ) {
                        sriLankaDistrictsMap.forEach { (districtName, towns) ->
                            DropdownMenuItem(
                                text = { Text(districtName, fontSize = 14.sp) },
                                onClick = {
                                    selectedDistrict = districtName
                                    if (towns.isNotEmpty() && !towns.contains(selectedTown)) {
                                        selectedTown = towns.first()
                                    }
                                    isDistrictDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Town Dropdown
                FormFieldLabel(label = "Town")
                ExposedDropdownMenuBox(
                    expanded = isTownDropdownOpen,
                    onExpandedChange = { isTownDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value = selectedTown,
                        onValueChange = { selectedTown = it },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTownDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = detailTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = isTownDropdownOpen,
                        onDismissRequest = { isTownDropdownOpen = false }
                    ) {
                        availableTowns.forEach { townName ->
                            DropdownMenuItem(
                                text = { Text(townName, fontSize = 14.sp) },
                                onClick = {
                                    selectedTown = townName
                                    isTownDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Address
                FormFieldLabel(label = "Address")
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Home, contentDescription = null, tint = SlateMedium)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = detailTextFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Contact Phone
                FormFieldLabel(label = "Contact Number")
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Phone, contentDescription = null, tint = SlateMedium)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = detailTextFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Contact Email
                FormFieldLabel(label = "Email Address")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Email, contentDescription = null, tint = SlateMedium)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = detailTextFieldColors()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirmation AlertDialog for Deactivation
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = {
                Text(
                    text = "Deactivate Pharmacy?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkSlate
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to deactivate $name? This will set status to rejected and remove it from active network searches. You can re-approve later.",
                    fontSize = 13.5.sp,
                    color = SlateMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateDialog = false
                        onDeactivate()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = SurfaceWhite
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Deactivate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text("Cancel", color = SlateMedium, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Sub-components & Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SlateMedium
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun FormFieldLabel(label: String) {
    Text(
        text = label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkSlate,
        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
    )
}

@Composable
private fun detailTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ForestEmerald,
    unfocusedBorderColor = BorderColor,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite,
    cursorColor = ForestEmerald,
    focusedTextColor = DarkSlate,
    unfocusedTextColor = DarkSlate
)
