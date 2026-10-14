package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationStatus
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Patient Profile Screen
 *
 * Rich, comprehensive patient profile interface featuring:
 *  • Hero Identity Card with avatar, verification status, and quick chips
 *  • Health & Vault Care Metrics (Active reservations, vaulted slips, MediPoints)
 *  • Personal Information (NIC, Phone, Email, Residential address)
 *  • Medical & Emergency Profile (Blood group, allergies, conditions, emergency contact)
 *  • Insurance & Coverage Details
 *  • Interactive Preference Toggles (Refill reminders, WhatsApp alerts, Biometrics)
 *  • Interactive Edit Profile Bottom Sheet with real-time state updates
 *  • Digital Health Card QR Modal for counter check-in
 *  • Logout Confirmation Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    // ── Local Patient State (editable) ──────────────────────────────────────────
    val profile = AppGraph.currentProfile
    val uid = profile?.uid
    var patientName by remember { mutableStateOf(profile?.name.orEmpty()) }
    var patientPhone by remember { mutableStateOf(profile?.identifier.orEmpty()) }
    var patientEmail by remember { mutableStateOf(profile?.email.orEmpty()) }
    var patientAddress by remember { mutableStateOf(profile?.address.orEmpty()) }
    var patientAge by remember { mutableStateOf(profile?.age ?: 0) }
    var patientGender by remember { mutableStateOf(profile?.gender.orEmpty()) }
    var bloodGroup by remember { mutableStateOf(profile?.bloodGroup.orEmpty()) }
    var allergies by remember { mutableStateOf(profile?.allergies.orEmpty()) }
    var chronicConditions by remember { mutableStateOf(profile?.chronicConditions.orEmpty()) }
    var emergencyContactName by remember { mutableStateOf(profile?.emergencyContactName.orEmpty()) }
    var emergencyContactPhone by remember { mutableStateOf(profile?.emergencyContactPhone.orEmpty()) }

    val mediFlowId = "#MF-PT-" + (profile?.uid?.takeLast(6) ?: "")
    val ageAndGender = listOfNotNull(
        patientAge.takeIf { it > 0 }?.let { "$it Yrs" },
        patientGender.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    // ── Preference Switches ───────────────────────────────────────────────────
    // Only three notification flags persist; the biometric lock has no backend field.
    var refillRemindersEnabled by remember { mutableStateOf(profile?.notifyReservationUpdates ?: true) }
    var whatsappAlertsEnabled by remember { mutableStateOf(profile?.notifyNewMedicines ?: false) }
    var autoVaultEnabled by remember { mutableStateOf(profile?.notifyHealthTips ?: false) }
    var biometricEnabled by remember { mutableStateOf(false) }

    // ── Vault metrics ─────────────────────────────────────────────────────────
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    LaunchedEffect(uid) {
        reservations = if (uid == null) {
            emptyList()
        } else {
            runCatching { AppGraph.reservations.forPatient(uid) }.getOrNull().orEmpty()
        }
    }
    val readyCount = reservations.count { it.displayStatus() == ReservationStatus.READY }
    val reservedCount = reservations.count { it.displayStatus() == ReservationStatus.RESERVED }
    val slipCount = reservations.count { it.prescriptionAttached }

    // ── Dialog / Sheet visibility ─────────────────────────────────────────────
    var showEditSheet by remember { mutableStateOf(false) }
    var showHealthCardDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Patient Profile",
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkSlate
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHealthCardDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = "Digital Health Card QR",
                            tint = ForestEmerald
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Hero Identity Card ─────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = DarkSlate,
                shadowElevation = 5.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF065F46), // Deep Emerald
                                    Color(0xFF0F172A), // Dark Slate
                                    Color(0xFF1E3A8A)  // Healthcare Deep Blue
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .border(2.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = patientName.split(" ")
                                            .mapNotNull { it.firstOrNull() }
                                            .take(2).joinToString(""),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                }
                                // Active verified badge
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MintGreen)
                                        .border(2.dp, DarkSlate, CircleShape)
                                        .align(Alignment.BottomEnd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = DarkSlate,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = patientName,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = "Verified Patient",
                                        tint = Color(0xFF86EFAC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = "MediFlow ID: $mediFlowId",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "🩸 $bloodGroup",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                        )
                                    }

                                    if (ageAndGender.isNotEmpty()) {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = ageAndGender,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(Modifier.height(14.dp))

                        // Quick buttons on hero card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditSheet = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Profile", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { showHealthCardDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.22f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Health QR", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── 2. Metric Care Tiles ──────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PatientMetricTile(
                    label = "Active Order",
                    value = "$readyCount Ready",
                    caption = "$reservedCount Reserved",
                    icon = Icons.Rounded.LocalPharmacy,
                    color = ForestEmerald,
                    onClick = onNavigateToVault,
                    modifier = Modifier.weight(1f)
                )
                PatientMetricTile(
                    label = "Vaulted",
                    value = "$slipCount Slips",
                    caption = "Prescriptions",
                    icon = Icons.Rounded.Medication,
                    color = HealthcareBlue,
                    onClick = onNavigateToVault,
                    modifier = Modifier.weight(1f)
                )
                PatientMetricTile(
                    label = "MediPoints",
                    value = "450",
                    caption = "Pts (Rs 450)",
                    icon = Icons.Rounded.Star,
                    color = AmberWarning,
                    onClick = {
                        scope.launch { snackbarHostState.showSnackbar("You have 450 MediPoints redeemable on your next refill.") }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── 3. Personal & Contact Details ─────────────────────────────────
            PatientSectionCard(
                title = "Personal & Contact Information",
                icon = Icons.Rounded.Person
            ) {
                PatientInfoRow(
                    icon = Icons.Rounded.Phone,
                    label = "Mobile Phone",
                    value = patientPhone,
                    badge = "Primary",
                    badgeColor = HealthcareBlue
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.Email,
                    label = "Email Address",
                    value = patientEmail,
                    badge = "Linked",
                    badgeColor = SlateMedium
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.Person,
                    label = "Age & Gender",
                    value = ageAndGender,
                    badge = "On file",
                    badgeColor = SlateMedium
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.Home,
                    label = "Residential Address",
                    value = patientAddress,
                    badge = "Delivery Area",
                    badgeColor = MintGreen
                )
            }

            // ── 4. Medical & Emergency Information ────────────────────────────
            PatientSectionCard(
                title = "Medical & Emergency Profile",
                icon = Icons.Rounded.Favorite
            ) {
                PatientInfoRow(
                    icon = Icons.Rounded.LocalHospital,
                    label = "Blood Group",
                    value = bloodGroup,
                    badge = "Critical",
                    badgeColor = ErrorRed
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.Warning,
                    label = "Known Drug Allergies",
                    value = allergies,
                    badge = "Alert Pharmacist",
                    badgeColor = ErrorRed
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.Medication,
                    label = "Chronic Conditions",
                    value = chronicConditions,
                    badge = "Ongoing Care",
                    badgeColor = AmberWarning
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.ContactPhone,
                    label = "Emergency Contact",
                    value = "$emergencyContactName\n$emergencyContactPhone",
                    badge = "Primary Kin",
                    badgeColor = ForestEmerald
                )
            }

            // ── 5. Insurance & Healthcare Coverage ────────────────────────────
            PatientSectionCard(
                title = "Insurance & Health Coverage",
                icon = Icons.Rounded.Shield
            ) {
                PatientInfoRow(
                    icon = Icons.Rounded.Security,
                    label = "Provider",
                    value = "Ceylinco Life Health Protect",
                    badge = "Active Policy",
                    badgeColor = ForestEmerald
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientInfoRow(
                    icon = Icons.Rounded.Numbers,
                    label = "Policy & Card Number",
                    value = "CL-MED-884920 · Tier A Outpatient",
                    badge = "Pharmacy Covered",
                    badgeColor = HealthcareBlue
                )
            }

            // ── 6. Preferences & Notifications ────────────────────────────────
            PatientSectionCard(
                title = "Preferences & App Settings",
                icon = Icons.Rounded.Settings
            ) {
                PatientToggleRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Refill & Prescription Reminders",
                    description = "Alerts 3 days before medications run out",
                    checked = refillRemindersEnabled,
                    onCheckedChange = { refillRemindersEnabled = it }
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientToggleRow(
                    icon = Icons.Rounded.Chat,
                    title = "WhatsApp Order Notifications",
                    description = "Receive instant pickup QR via WhatsApp",
                    checked = whatsappAlertsEnabled,
                    onCheckedChange = { whatsappAlertsEnabled = it }
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientToggleRow(
                    icon = Icons.Rounded.Inventory2,
                    title = "Auto-Vault Scanned Slips",
                    description = "Automatically save paper slip scans into vault",
                    checked = autoVaultEnabled,
                    onCheckedChange = { autoVaultEnabled = it }
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientToggleRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Biometric Lock",
                    description = "Require Face ID or Fingerprint for prescription vault",
                    checked = biometricEnabled,
                    onCheckedChange = { biometricEnabled = it }
                )
            }

            // ── 7. Quick Shortcuts & Help ─────────────────────────────────────
            PatientSectionCard(
                title = "Support & Actions",
                icon = Icons.Rounded.Help
            ) {
                PatientActionRow(
                    icon = Icons.Rounded.Medication,
                    title = "My Prescriptions Vault",
                    subtitle = "View active and past medicine slips",
                    onClick = onNavigateToVault
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientActionRow(
                    icon = Icons.Rounded.Search,
                    title = "Find Nearby Pharmacies",
                    subtitle = "Search registered stocks in Kurunegala",
                    onClick = onNavigateToSearch
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientActionRow(
                    icon = Icons.Rounded.SupportAgent,
                    title = "MediFlow 24/7 Helpline",
                    subtitle = "Call emergency pharmacy dispatch: 1990",
                    onClick = {
                        scope.launch { snackbarHostState.showSnackbar("Calling MediFlow Helpline 1990...") }
                    }
                )
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                PatientActionRow(
                    icon = Icons.Rounded.Lock,
                    title = "Health Data Privacy Policy",
                    subtitle = "Encrypted per Sri Lanka National Health Data Standards",
                    onClick = {
                        scope.launch { snackbarHostState.showSnackbar("All patient health data is 256-bit AES encrypted.") }
                    }
                )
            }

            // ── 8. Sign Out ───────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ErrorRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Log Out of MediFlow",
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Securely sign out of this device",
                            color = SlateMedium,
                            fontSize = 11.5.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = ErrorRed
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    // ── Edit Profile Bottom Sheet ─────────────────────────────────────────────
    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            var tempName by remember { mutableStateOf(patientName) }
            var tempPhone by remember { mutableStateOf(patientPhone) }
            var tempEmail by remember { mutableStateOf(patientEmail) }
            var tempAddress by remember { mutableStateOf(patientAddress) }
            var tempAge by remember { mutableStateOf(if (patientAge > 0) patientAge.toString() else "") }
            var tempGender by remember { mutableStateOf(patientGender) }
            var tempBlood by remember { mutableStateOf(bloodGroup) }
            var tempAllergies by remember { mutableStateOf(allergies) }
            var tempKin by remember { mutableStateOf(emergencyContactName) }
            var tempKinPhone by remember { mutableStateOf(emergencyContactPhone) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Patient Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkSlate
                    )
                    IconButton(onClick = { showEditSheet = false }) {
                        Icon(Icons.Rounded.Close, "Close", tint = SlateMedium)
                    }
                }

                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        focusedLabelColor = ForestEmerald
                    )
                )

                OutlinedTextField(
                    value = tempPhone,
                    onValueChange = { tempPhone = it },
                    label = { Text("Mobile Phone") },
                    leadingIcon = { Icon(Icons.Rounded.Phone, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        focusedLabelColor = ForestEmerald
                    )
                )

                OutlinedTextField(
                    value = tempEmail,
                    onValueChange = { tempEmail = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Rounded.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        focusedLabelColor = ForestEmerald
                    )
                )

                OutlinedTextField(
                    value = tempAddress,
                    onValueChange = { tempAddress = it },
                    label = { Text("Delivery Address") },
                    leadingIcon = { Icon(Icons.Rounded.Home, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        focusedLabelColor = ForestEmerald
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = tempBlood,
                        onValueChange = { tempBlood = it },
                        label = { Text("Blood Group") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestEmerald,
                            focusedLabelColor = ForestEmerald
                        )
                    )
                    OutlinedTextField(
                        value = tempAllergies,
                        onValueChange = { tempAllergies = it },
                        label = { Text("Drug Allergies") },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestEmerald,
                            focusedLabelColor = ForestEmerald
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tempAge,
                        onValueChange = { input -> tempAge = input.filter { it.isDigit() }.take(3) },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestEmerald,
                            focusedLabelColor = ForestEmerald
                        )
                    )
                    Column(modifier = Modifier.weight(2f)) {
                        Text("Gender", fontSize = 11.sp, color = SlateMedium)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Male", "Female", "Other").forEach { option ->
                                FilterChip(
                                    selected = tempGender == option,
                                    onClick = { tempGender = option },
                                    label = { Text(option, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForestEmerald.copy(alpha = 0.16f),
                                        selectedLabelColor = ForestEmerald
                                    )
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = tempKin,
                    onValueChange = { tempKin = it },
                    label = { Text("Emergency Contact Name") },
                    leadingIcon = { Icon(Icons.Rounded.ContactPhone, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        focusedLabelColor = ForestEmerald
                    )
                )

                OutlinedTextField(
                    value = tempKinPhone,
                    onValueChange = { tempKinPhone = it },
                    label = { Text("Emergency Phone") },
                    leadingIcon = { Icon(Icons.Rounded.Phone, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestEmerald,
                        focusedLabelColor = ForestEmerald
                    )
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        patientName = tempName
                        patientPhone = tempPhone
                        patientEmail = tempEmail
                        patientAddress = tempAddress
                        patientAge = tempAge.toIntOrNull() ?: 0
                        patientGender = tempGender
                        bloodGroup = tempBlood
                        allergies = tempAllergies
                        emergencyContactName = tempKin
                        emergencyContactPhone = tempKinPhone
                        showEditSheet = false
                        val base = AppGraph.currentProfile
                        if (base == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No signed-in profile to update.")
                            }
                        } else {
                            val updated = base.copy(
                                name = tempName,
                                identifier = tempPhone,
                                email = tempEmail,
                                address = tempAddress,
                                age = patientAge,
                                gender = tempGender,
                                bloodGroup = tempBlood,
                                allergies = tempAllergies,
                                emergencyContactName = tempKin,
                                emergencyContactPhone = tempKinPhone,
                                notifyReservationUpdates = refillRemindersEnabled,
                                notifyNewMedicines = whatsappAlertsEnabled,
                                notifyHealthTips = autoVaultEnabled
                            )
                            scope.launch {
                                if (AppGraph.auth.writeProfile(updated).isSuccess) {
                                    AppGraph.rememberProfile(updated)
                                    snackbarHostState.showSnackbar(
                                        "Patient profile updated successfully."
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        "Couldn't save your changes. Please try again."
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // ── Digital Health Card QR Dialog ─────────────────────────────────────────
    if (showHealthCardDialog) {
        AlertDialog(
            onDismissRequest = { showHealthCardDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { showHealthCardDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ForestEmerald)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHealthCardDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Health Card saved to device.")
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Card")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QrCode2, null, tint = ForestEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Digital Health ID", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Present this QR at any MediFlow affiliated pharmacy counter for instant verification.",
                        fontSize = 12.5.sp,
                        color = SlateMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    // QR Card Container
                    Surface(
                        modifier = Modifier.size(190.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(2.dp, ForestEmerald.copy(alpha = 0.3f)),
                        shadowElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = "QR Code",
                                tint = DarkSlate,
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Surface(
                        color = LightBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = patientName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkSlate
                            )
                            Text(
                                text = "ID: $mediFlowId",
                                fontSize = 11.sp,
                                color = SlateMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Blood Group: $bloodGroup · Allergies: $allergies",
                                fontSize = 11.sp,
                                color = ErrorRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Logout Confirmation Dialog ────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Log Out of MediFlow?",
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
            },
            text = {
                Text(
                    text = "You will need to enter your credentials or use biometric login to access your prescription vault again.",
                    color = SlateMedium,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold)
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

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Component Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PatientMetricTile(
    label: String,
    value: String,
    caption: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
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
                fontSize = 17.sp,
                color = DarkSlate
            )
            Text(text = label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
            Text(text = caption, fontSize = 10.sp, color = SlateMedium)
        }
    }
}

@Composable
private fun PatientSectionCard(
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
private fun PatientInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    badge: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ForestEmerald.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ForestEmerald, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.sp, color = SlateMedium)
            Text(
                text = value.ifBlank { "Not added yet" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate,
                lineHeight = 17.sp
            )
        }
        Surface(
            color = badgeColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PatientToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
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
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ForestEmerald.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ForestEmerald, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkSlate)
            Text(text = description, fontSize = 11.sp, color = SlateMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ForestEmerald
            )
        )
    }
}

@Composable
private fun PatientActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ForestEmerald.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ForestEmerald, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkSlate)
            Text(text = subtitle, fontSize = 11.sp, color = SlateMedium)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SlateMedium,
            modifier = Modifier.size(18.dp)
        )
    }
}
