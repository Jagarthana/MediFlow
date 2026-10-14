package com.mediflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * Screen 15 — Admin Register Pharmacy (Screen A2 / Variant B)
 *
 * 3-Step Guided Wizard for National Administrators registering pharmacies:
 *  • Step 1: Pharmacy Info (Name, SLMC Reg #, 4-digit PIN)
 *  • Step 2: License Upload (Drag/Drop area, Attach SLMC Certificate, File preview & remove)
 *  • Step 3: Location & Contact (Province/District/Town cascading selectors, Address 1 & 2, Phone, Email)
 *
 * Progress Indicator (1 — 2 — 3) at top, Primary "Save & Next" / "Complete", and "Back" action.
 * Adheres strictly to WCAG 2.1 AA accessibility guidelines and MediFlow design system.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Administrative Division Data (mirrors Sri Lanka area taxonomy)
// ─────────────────────────────────────────────────────────────────────────────

private data class DistrictData(val name: String, val towns: List<String>)
private data class ProvinceData(val name: String, val districts: List<DistrictData>)

private val srilankaProvinces = listOf(
    ProvinceData(
        "Western Province",
        listOf(
            DistrictData("Colombo District", listOf("Colombo", "Colombo 07", "Dehiwala-Mount Lavinia", "Moratuwa", "Kaduwela", "Maharagama", "Homagama")),
            DistrictData("Gampaha District", listOf("Gampaha", "Negombo", "Wattala", "Ja-Ela", "Hendala", "Kadawatha")),
            DistrictData("Kalutara District", listOf("Kalutara", "Panadura", "Wadduwa", "Beruwala", "Ingiriya"))
        )
    ),
    ProvinceData(
        "North Western Province",
        listOf(
            DistrictData("Kurunegala District", listOf("Kurunegala", "Ibbagamuwa", "Mawathagama", "Nikitasthena", "Galewata")),
            DistrictData("Puttalam District", listOf("Puttalam", "Chilaw", "Wennappuwa"))
        )
    ),
    ProvinceData(
        "Central Province",
        listOf(
            DistrictData("Kandy District", listOf("Kandy", "Gampola", "Nawalapitiya", "Kadugannawa", "Peradeniya")),
            DistrictData("Matale District", listOf("Matale", "Dambulla", "Nanu Oya")),
            DistrictData("Nuwara Eliya District", listOf("Nuwara Eliya", "Hatton"))
        )
    ),
    ProvinceData(
        "Southern Province",
        listOf(
            DistrictData("Galle District", listOf("Galle", "Ambalangoda", "Hikkaduwa", "Talpe")),
            DistrictData("Matara District", listOf("Matara", "Dikwella", "Welipatha")),
            DistrictData("Hambantota District", listOf("Hambantota", "Tangalle", "Tissamaharama"))
        )
    ),
    ProvinceData(
        "Sabaragamuwa Province",
        listOf(
            DistrictData("Ratnapura District", listOf("Ratnapura", "Balangoda", "Embilipitiya")),
            DistrictData("Kegalle District", listOf("Kegalle", "Warakapola", "Mawanella"))
        )
    ),
    ProvinceData(
        "North Central Province",
        listOf(
            DistrictData("Anuradhapura District", listOf("Anuradhapura", "Mihintale", "Thambuttegama")),
            DistrictData("Polonnaruwa District", listOf("Polonnaruwa", "Hingurakgoda"))
        )
    ),
    ProvinceData(
        "Eastern Province",
        listOf(
            DistrictData("Trincomalee District", listOf("Trincomalee", "China Bay", "Kantale")),
            DistrictData("Batticaloa District", listOf("Batticaloa", "Katankudi")),
            DistrictData("Ampara District", listOf("Ampara", "Kalmunai", "Sammanthurai"))
        )
    ),
    ProvinceData(
        "Northern Province",
        listOf(
            DistrictData("Jaffna District", listOf("Jaffna", "Nallur", "Chavakachcheri")),
            DistrictData("Vavuniya District", listOf("Vavuniya", "Omanthai"))
        )
    ),
    ProvinceData(
        "Uva Province",
        listOf(
            DistrictData("Badulla District", listOf("Badulla", "Bandarawela", "Haputale")),
            DistrictData("Monaragala District", listOf("Monaragala", "Wellawaya"))
        )
    )
)

private data class UploadedLicense(
    val fileName: String,
    val fileSize: String,
    val uploadDate: String,
    val mimeType: String = "application/pdf"
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen Host
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRegisterPharmacyScreen(
    onBack: () -> Unit,
    onRegistrationComplete: () -> Unit = onBack
) {
    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Step 1 State: Pharmacy Info
    var pharmacyName by rememberSaveable { mutableStateOf("HealthNet Central Pharmacy") }
    var slmcRegNumber by rememberSaveable { mutableStateOf("SLMC-PH-8492") }
    var slmcPin by rememberSaveable { mutableStateOf("4819") }
    var isPinVisible by rememberSaveable { mutableStateOf(false) }

    // Step 2 State: License Upload
    var uploadedLicense by remember {
        mutableStateOf<UploadedLicense?>(
            UploadedLicense(
                fileName = "SLMC_Accreditation_Certificate_2026.pdf",
                fileSize = "1.8 MB",
                uploadDate = "Today, 11:30 AM"
            )
        )
    }
    var isUploading by remember { mutableStateOf(false) }

    // Step 3 State: Location & Contact
    var selectedProvince by rememberSaveable { mutableStateOf("Western Province") }
    var selectedDistrict by rememberSaveable { mutableStateOf("Colombo District") }
    var selectedTown by rememberSaveable { mutableStateOf("Colombo 07") }
    var addressLine1 by rememberSaveable { mutableStateOf("No. 142, Ward Place") }
    var addressLine2 by rememberSaveable { mutableStateOf("Colombo 00700") }
    var contactNumber by rememberSaveable { mutableStateOf("+94 11 268 4590") }
    var contactEmail by rememberSaveable { mutableStateOf("admin@healthnet.lk") }

    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    fun validateStep1(): Boolean {
        if (pharmacyName.trim().length < 3) {
            scope.launch { snackbarHostState.showSnackbar("Please enter a valid pharmacy name (at least 3 characters)") }
            return false
        }
        if (slmcRegNumber.trim().isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please enter the SLMC registration number") }
            return false
        }
        if (slmcPin.length != 4 || !slmcPin.all { it.isDigit() }) {
            scope.launch { snackbarHostState.showSnackbar("SLMC PIN must be exactly 4 numeric digits") }
            return false
        }
        return true
    }

    fun validateStep2(): Boolean {
        if (uploadedLicense == null) {
            scope.launch { snackbarHostState.showSnackbar("Please upload the SLMC license certificate to proceed") }
            return false
        }
        return true
    }

    fun validateStep3(): Boolean {
        if (selectedTown.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please select Province, District, and Town") }
            return false
        }
        if (addressLine1.trim().isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Please enter Address line 1") }
            return false
        }
        if (contactNumber.trim().length < 9) {
            scope.launch { snackbarHostState.showSnackbar("Please enter a valid contact phone number") }
            return false
        }
        if (!contactEmail.contains("@") || !contactEmail.contains(".")) {
            scope.launch { snackbarHostState.showSnackbar("Please enter a valid official email address") }
            return false
        }
        return true
    }

    fun handleNext() {
        focusManager.clearFocus()
        when (currentStep) {
            1 -> {
                if (validateStep1()) currentStep = 2
            }
            2 -> {
                if (validateStep2()) currentStep = 3
            }
            3 -> {
                if (validateStep3()) {
                    scope.launch {
                        isSubmitting = true
                        delay(1200)
                        isSubmitting = false
                        showSuccessDialog = true
                    }
                }
            }
        }
    }

    fun handleBack() {
        focusManager.clearFocus()
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RegisterPharmacyTopBar(
                currentStep = currentStep,
                onBack = { handleBack() }
            )
        },
        bottomBar = {
            RegisterPharmacyBottomBar(
                currentStep = currentStep,
                isSubmitting = isSubmitting,
                onNext = ::handleNext,
                onBack = { handleBack() }
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
            // Stepper Progress Header
            WizardStepIndicator(currentStep = currentStep)

            Spacer(Modifier.height(20.dp))

            // Animated step container
            AnimatedContent(
                targetState = currentStep,
                label = "WizardStepTransition"
            ) { step ->
                when (step) {
                    1 -> Step1PharmacyInfo(
                        pharmacyName = pharmacyName,
                        onPharmacyNameChange = { pharmacyName = it },
                        slmcRegNumber = slmcRegNumber,
                        onSlmcRegNumberChange = { slmcRegNumber = it },
                        slmcPin = slmcPin,
                        onSlmcPinChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) slmcPin = it },
                        isPinVisible = isPinVisible,
                        onTogglePinVisibility = { isPinVisible = !isPinVisible }
                    )

                    2 -> Step2LicenseUpload(
                        license = uploadedLicense,
                        isUploading = isUploading,
                        onAttachDefaultLicense = {
                            scope.launch {
                                isUploading = true
                                delay(800)
                                uploadedLicense = UploadedLicense(
                                    fileName = "SLMC_Accreditation_Certificate_2026.pdf",
                                    fileSize = "1.8 MB",
                                    uploadDate = "Today, Just now"
                                )
                                isUploading = false
                                snackbarHostState.showSnackbar("SLMC License certificate attached successfully")
                            }
                        },
                        onRemoveLicense = {
                            uploadedLicense = null
                            scope.launch { snackbarHostState.showSnackbar("Document removed") }
                        }
                    )

                    3 -> Step3LocationContact(
                        selectedProvince = selectedProvince,
                        onProvinceChange = {
                            selectedProvince = it
                            val firstDist = srilankaProvinces.firstOrNull { p -> p.name == it }?.districts?.firstOrNull()
                            selectedDistrict = firstDist?.name.orEmpty()
                            selectedTown = firstDist?.towns?.firstOrNull().orEmpty()
                        },
                        selectedDistrict = selectedDistrict,
                        onDistrictChange = {
                            selectedDistrict = it
                            val curProvince = srilankaProvinces.firstOrNull { p -> p.name == selectedProvince }
                            val curDist = curProvince?.districts?.firstOrNull { d -> d.name == it }
                            selectedTown = curDist?.towns?.firstOrNull().orEmpty()
                        },
                        selectedTown = selectedTown,
                        onTownChange = { selectedTown = it },
                        addressLine1 = addressLine1,
                        onAddressLine1Change = { addressLine1 = it },
                        addressLine2 = addressLine2,
                        onAddressLine2Change = { addressLine2 = it },
                        contactNumber = contactNumber,
                        onContactNumberChange = { contactNumber = it },
                        contactEmail = contactEmail,
                        onContactEmailChange = { contactEmail = it }
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (showSuccessDialog) {
        RegistrationSuccessDialog(
            pharmacyName = pharmacyName,
            slmcRegNumber = slmcRegNumber,
            town = selectedTown,
            onDismiss = {
                showSuccessDialog = false
                onRegistrationComplete()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegisterPharmacyTopBar(
    currentStep: Int,
    onBack: () -> Unit
) {
    val stepTitle = when (currentStep) {
        1 -> "Pharmacy Info"
        2 -> "License Upload"
        else -> "Location & Contact"
    }

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
                    text = "Register Pharmacy",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = DarkSlate,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "Step $currentStep of 3 · $stepTitle",
                    fontSize = 12.sp,
                    color = ForestEmerald,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                shape = CircleShape,
                color = ForestEmerald.copy(alpha = 0.12f),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "$currentStep/3",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wizard Step Progress Indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WizardStepIndicator(currentStep: Int) {
    val steps = listOf("Pharmacy Info", "License Upload", "Location & Contact")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, title ->
                    val stepNumber = index + 1
                    val isDone = stepNumber < currentStep
                    val isCurrent = stepNumber == currentStep

                    // Step circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDone -> ForestEmerald
                                    isCurrent -> ForestEmerald
                                    else -> Color(0xFFE2E8F0)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "$stepNumber",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) Color.White else SlateLight
                            )
                        }
                    }

                    // Connecting bar
                    if (index < steps.size - 1) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (stepNumber < currentStep) ForestEmerald else Color(0xFFE2E8F0)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Step labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                steps.forEachIndexed { index, title ->
                    val stepNumber = index + 1
                    val isCurrent = stepNumber == currentStep
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) ForestEmerald else SlateMedium,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            1 -> TextAlign.Center
                            else -> TextAlign.End
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1: Pharmacy Info
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Step1PharmacyInfo(
    pharmacyName: String,
    onPharmacyNameChange: (String) -> Unit,
    slmcRegNumber: String,
    onSlmcRegNumberChange: (String) -> Unit,
    slmcPin: String,
    onSlmcPinChange: (String) -> Unit,
    isPinVisible: Boolean,
    onTogglePinVisibility: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "Pharmacy Information",
            subtitle = "Official registration and SLMC identification details"
        )

        Spacer(Modifier.height(16.dp))

        // Pharmacy Name
        FormLabel(label = "Pharmacy Name *")
        OutlinedTextField(
            value = pharmacyName,
            onValueChange = onPharmacyNameChange,
            placeholder = { Text("e.g. HealthNet Central Pharmacy", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Storefront, contentDescription = null, tint = ForestEmerald)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )

        Spacer(Modifier.height(16.dp))

        // SLMC Registration Number
        FormLabel(label = "SLMC Registration # *")
        OutlinedTextField(
            value = slmcRegNumber,
            onValueChange = onSlmcRegNumberChange,
            placeholder = { Text("e.g. SLMC-PH-8492", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Badge, contentDescription = null, tint = ForestEmerald)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )
        Text(
            text = "Sri Lanka Medical Council license number assigned to the pharmacy",
            fontSize = 11.5.sp,
            color = SlateLight,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        // SLMC PIN (4 digits)
        FormLabel(label = "SLMC Authorization PIN (4 digits) *")
        OutlinedTextField(
            value = slmcPin,
            onValueChange = onSlmcPinChange,
            placeholder = { Text("4-digit security PIN", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Pin, contentDescription = null, tint = ForestEmerald)
            },
            trailingIcon = {
                IconButton(onClick = onTogglePinVisibility) {
                    Icon(
                        imageVector = if (isPinVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                        tint = SlateMedium
                    )
                }
            },
            visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )
        Text(
            text = "4-digit administrative PIN for verifying pharmacist dispensing credentials",
            fontSize = 11.5.sp,
            color = SlateLight,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Security Assurance Note
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = HealthcareBlue.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, HealthcareBlue.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Shield, null, tint = HealthcareBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Credentials are encrypted and cross-referenced with the SLMC registry ledger.",
                    fontSize = 12.sp,
                    color = DarkSlate,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2: License Upload
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Step2LicenseUpload(
    license: UploadedLicense?,
    isUploading: Boolean,
    onAttachDefaultLicense: () -> Unit,
    onRemoveLicense: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "License & Accreditation",
            subtitle = "Attach official SLMC pharmacy operating license (PDF or JPG/PNG)"
        )

        Spacer(Modifier.height(16.dp))

        // Upload Drag & Drop area simulation
        val strokeColor = if (license != null) SuccessGreen else ForestEmerald.copy(alpha = 0.6f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .drawBehind {
                    val stroke = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                    )
                    drawRoundRect(
                        color = strokeColor,
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = stroke
                    )
                }
                .clip(RoundedCornerShape(14.dp))
                .background(if (license != null) SuccessGreen.copy(alpha = 0.04f) else ForestEmerald.copy(alpha = 0.03f))
                .clickable(enabled = !isUploading) { onAttachDefaultLicense() }
                .semantics { contentDescription = "Upload License. Tap to browse or attach certificate" },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = ForestEmerald,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Verifying & Uploading document...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ForestEmerald
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ForestEmerald.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            tint = ForestEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Upload License (PDF / JPG)",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Drag and drop or tap to select from device (Max 10 MB)",
                        fontSize = 12.sp,
                        color = SlateMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Direct Attach SLMC Certificate Button
        OutlinedButton(
            onClick = onAttachDefaultLicense,
            enabled = !isUploading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Attach SLMC Certificate" },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, ForestEmerald),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestEmerald)
        ) {
            Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Attach SLMC Certificate",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))

        // File preview with remove option
        if (license != null) {
            FormLabel(label = "Attached Certificate")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ForestEmerald.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = ForestEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = license.fileName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${license.fileSize} · ${license.uploadDate}",
                            fontSize = 11.5.sp,
                            color = SlateMedium
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ready", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onRemoveLicense,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "Remove attached certificate" }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AmberWarning.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "No document attached yet. Please upload a valid SLMC registration certificate.",
                        fontSize = 12.sp,
                        color = DarkSlate,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3: Location & Contact
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step3LocationContact(
    selectedProvince: String,
    onProvinceChange: (String) -> Unit,
    selectedDistrict: String,
    onDistrictChange: (String) -> Unit,
    selectedTown: String,
    onTownChange: (String) -> Unit,
    addressLine1: String,
    onAddressLine1Change: (String) -> Unit,
    addressLine2: String,
    onAddressLine2Change: (String) -> Unit,
    contactNumber: String,
    onContactNumberChange: (String) -> Unit,
    contactEmail: String,
    onContactEmailChange: (String) -> Unit
) {
    var provinceExpanded by remember { mutableStateOf(false) }
    var districtExpanded by remember { mutableStateOf(false) }
    var townExpanded by remember { mutableStateOf(false) }

    val currentProvince = srilankaProvinces.firstOrNull { it.name == selectedProvince }
        ?: srilankaProvinces.first()
    val availableDistricts = currentProvince.districts
    val currentDistrict = availableDistricts.firstOrNull { it.name == selectedDistrict }
        ?: availableDistricts.first()
    val availableTowns = currentDistrict.towns

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            title = "Location & Contact Details",
            subtitle = "Specify administrative jurisdiction and contact channels"
        )

        Spacer(Modifier.height(16.dp))

        // Province Dropdown
        FormLabel(label = "Province *")
        ExposedDropdownMenuBox(
            expanded = provinceExpanded,
            onExpandedChange = { provinceExpanded = !provinceExpanded }
        ) {
            OutlinedTextField(
                value = selectedProvince,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                leadingIcon = { Icon(Icons.Rounded.Map, null, tint = ForestEmerald) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = formTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = provinceExpanded,
                onDismissRequest = { provinceExpanded = false },
                modifier = Modifier.background(SurfaceWhite)
            ) {
                srilankaProvinces.forEach { prov ->
                    DropdownMenuItem(
                        text = { Text(prov.name, color = DarkSlate) },
                        onClick = {
                            onProvinceChange(prov.name)
                            provinceExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // District Dropdown
        FormLabel(label = "District *")
        ExposedDropdownMenuBox(
            expanded = districtExpanded,
            onExpandedChange = { districtExpanded = !districtExpanded }
        ) {
            OutlinedTextField(
                value = selectedDistrict,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                leadingIcon = { Icon(Icons.Rounded.LocationCity, null, tint = ForestEmerald) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = formTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = districtExpanded,
                onDismissRequest = { districtExpanded = false },
                modifier = Modifier.background(SurfaceWhite)
            ) {
                availableDistricts.forEach { dist ->
                    DropdownMenuItem(
                        text = { Text(dist.name, color = DarkSlate) },
                        onClick = {
                            onDistrictChange(dist.name)
                            districtExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Town Dropdown
        FormLabel(label = "Town / Area *")
        ExposedDropdownMenuBox(
            expanded = townExpanded,
            onExpandedChange = { townExpanded = !townExpanded }
        ) {
            OutlinedTextField(
                value = selectedTown,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = townExpanded) },
                leadingIcon = { Icon(Icons.Rounded.Place, null, tint = ForestEmerald) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = formTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = townExpanded,
                onDismissRequest = { townExpanded = false },
                modifier = Modifier.background(SurfaceWhite)
            ) {
                availableTowns.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t, color = DarkSlate) },
                        onClick = {
                            onTownChange(t)
                            townExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Address Line 1
        FormLabel(label = "Address Line 1 *")
        OutlinedTextField(
            value = addressLine1,
            onValueChange = onAddressLine1Change,
            placeholder = { Text("e.g. No. 142, Ward Place", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Home, null, tint = ForestEmerald) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )

        Spacer(Modifier.height(14.dp))

        // Address Line 2
        FormLabel(label = "Address Line 2 (City / Postal)")
        OutlinedTextField(
            value = addressLine2,
            onValueChange = onAddressLine2Change,
            placeholder = { Text("e.g. Colombo 00700", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Signpost, null, tint = ForestEmerald) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )

        Spacer(Modifier.height(16.dp))

        // Contact Phone
        FormLabel(label = "Contact Number *")
        OutlinedTextField(
            value = contactNumber,
            onValueChange = onContactNumberChange,
            placeholder = { Text("e.g. +94 11 268 4590", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Phone, null, tint = ForestEmerald) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )

        Spacer(Modifier.height(14.dp))

        // Contact Email
        FormLabel(label = "Official Email *")
        OutlinedTextField(
            value = contactEmail,
            onValueChange = onContactEmailChange,
            placeholder = { Text("e.g. admin@healthnet.lk", color = SlateLight, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Email, null, tint = ForestEmerald) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = formTextFieldColors()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Action Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegisterPharmacyBottomBar(
    currentStep: Int,
    isSubmitting: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val nextButtonText = when (currentStep) {
        1 -> "Save & Next: License"
        2 -> "Save & Next: Location"
        else -> "Complete Registration"
    }

    val nextIcon = when (currentStep) {
        3 -> Icons.Rounded.CheckCircle
        else -> Icons.AutoMirrored.Rounded.ArrowForward
    }

    Surface(
        color = SurfaceWhite,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onNext,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = nextButtonText },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
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
                    Text("Registering Pharmacy...", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        text = nextButtonText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = nextIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = onBack,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (currentStep == 1) "Cancel & Return to Console" else "Back to Previous Step",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateMedium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Success Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegistrationSuccessDialog(
    pharmacyName: String,
    slmcRegNumber: String,
    town: String,
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
                        .background(SuccessGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Pharmacy Registered!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "$pharmacyName has been registered under SLMC Reg #$slmcRegNumber ($town) and added to the national directory.",
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
                        Icon(Icons.Rounded.VerifiedUser, null, tint = ForestEmerald, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Status: Provisionally Approved & Ready for Dispatch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestEmerald
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
                        containerColor = ForestEmerald,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Return to Admin Console",
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
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 12.5.sp,
            color = SlateMedium
        )
    }
}

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
    focusedBorderColor = ForestEmerald,
    unfocusedBorderColor = BorderColor,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite,
    cursorColor = ForestEmerald,
    focusedTextColor = DarkSlate,
    unfocusedTextColor = DarkSlate
)
