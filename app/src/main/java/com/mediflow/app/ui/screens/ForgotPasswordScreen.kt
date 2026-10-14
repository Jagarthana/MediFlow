package com.mediflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 3 — Forgot Password / OTP / Reset Password
 *
 * Multi-step flow managed by a [ForgotPasswordStep] enum:
 *  STEP_1_MOBILE  → User enters Mobile/NIC → "Send OTP"
 *  STEP_2_OTP     → 6-digit PIN input → "Verify OTP"
 *  STEP_3_RESET   → New password + Confirm → "Reset Password"
 *  STEP_4_SUCCESS → Checkmark + "Password Reset Successful" + "Back to Login"
 *
 * Each step has:
 *  • Animated transition (slide + fade)
 *  • Full input validation with red error states
 *  • 48dp minimum touch targets (Fitts's Law / WCAG 2.1 AA)
 *
 * Navigation:
 *  onBack       → pops to Login
 *  onBackToLogin → clears back stack, navigates to Login
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var currentStep by remember { mutableStateOf(ForgotPasswordStep.MOBILE) }
    var mobileNic by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            ForgotPasswordTopBar(
                currentStep = currentStep,
                onBack = {
                    when (currentStep) {
                        ForgotPasswordStep.MOBILE  -> onBack()
                        ForgotPasswordStep.OTP     -> currentStep = ForgotPasswordStep.MOBILE
                        ForgotPasswordStep.RESET   -> currentStep = ForgotPasswordStep.OTP
                        ForgotPasswordStep.SUCCESS -> onBackToLogin()
                    }
                }
            )
        },
        containerColor = LightBackground
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress indicator
            ForgotPasswordProgressBar(currentStep = currentStep)

            // Step content with slide animation
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = targetState.ordinal > initialState.ordinal
                    if (direction) {
                        slideInHorizontally { it } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally { -it } + fadeOut(tween(200))
                    } else {
                        slideInHorizontally { -it } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally { it } + fadeOut(tween(200))
                    }
                },
                label = "ForgotPasswordStep"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 72.dp)
                ) {
                    when (step) {
                        ForgotPasswordStep.MOBILE -> StepMobile(
                            mobileNic = mobileNic,
                            onMobileChange = { mobileNic = it },
                            onSendOtp = { currentStep = ForgotPasswordStep.OTP }
                        )
                        ForgotPasswordStep.OTP -> StepOtp(
                            mobileNic = mobileNic,
                            onVerify = { currentStep = ForgotPasswordStep.RESET }
                        )
                        ForgotPasswordStep.RESET -> StepReset(
                            onReset = { currentStep = ForgotPasswordStep.SUCCESS }
                        )
                        ForgotPasswordStep.SUCCESS -> StepSuccess(
                            onBackToLogin = onBackToLogin
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Enum: Flow Steps
// ─────────────────────────────────────────────────────────────────────────────
enum class ForgotPasswordStep(val title: String) {
    MOBILE("Reset Password"),
    OTP("Verify OTP"),
    RESET("New Password"),
    SUCCESS("Done!")
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordTopBar(
    currentStep: ForgotPasswordStep,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = currentStep.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = DarkSlate,
                    fontSize = 18.sp
                )
            )
        },
        navigationIcon = {
            if (currentStep != ForgotPasswordStep.SUCCESS) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Go back" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = DarkSlate,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LightBackground
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress Bar (step indicator)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ForgotPasswordProgressBar(currentStep: ForgotPasswordStep) {
    val steps = ForgotPasswordStep.values()
    val progress = (currentStep.ordinal + 1).toFloat() / steps.size

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
        color = ForestEmerald,
        trackColor = BorderColor,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1: Enter Mobile / NIC
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepMobile(
    mobileNic: String,
    onMobileChange: (String) -> Unit,
    onSendOtp: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {

        StepHeader(
            icon = Icons.Rounded.PhoneLocked,
            iconDesc = "Phone with lock icon",
            title = "Forgot your password?",
            subtitle = "Enter your registered mobile number or NIC to receive a 6-digit verification code"
        )

        Spacer(modifier = Modifier.height(32.dp))

        MediFlowTextField(
            value = mobileNic,
            onValueChange = { onMobileChange(it); error = null },
            label = "Mobile / NIC Number",
            placeholder = "07X XXXX XXX or 200XXXXXXXXX",
            leadingIcon = Icons.Rounded.Phone,
            leadingIconDesc = "Phone icon",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSendOtp() }),
            errorMessage = error,
            contentDescription = "Mobile or NIC number field for password reset"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Info chip
        InfoChip(
            text = "We will send a 6-digit OTP via SMS",
            icon = Icons.Rounded.Info
        )

        Spacer(modifier = Modifier.height(32.dp))

        MediFlowPrimaryButton(
            text = "Send OTP",
            isLoading = isLoading,
            icon = Icons.Rounded.Send,
            contentDescription = "Send OTP button",
            onClick = {
                when {
                    mobileNic.isBlank() -> error = "Please enter your mobile number or NIC"
                    mobileNic.length < 9 -> error = "Please enter a valid mobile number or NIC"
                    else -> {
                        scope.launch {
                            isLoading = true
                            delay(1500) // Simulate network request
                            isLoading = false
                            onSendOtp()
                        }
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2: OTP Verification
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepOtp(
    mobileNic: String,
    onVerify: () -> Unit
) {
    val otpLength = 6
    val otpValues = remember { mutableStateListOf(*Array(otpLength) { "" }) }
    val focusRequesters = remember { List(otpLength) { FocusRequester() } }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableIntStateOf(30) }
    val scope = rememberCoroutineScope()

    // Countdown timer for resend
    LaunchedEffect(Unit) {
        while (resendTimer > 0) {
            delay(1000)
            resendTimer--
        }
    }

    // Auto-focus first field
    LaunchedEffect(Unit) {
        delay(100)
        focusRequesters[0].requestFocus()
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        StepHeader(
            icon = Icons.Rounded.Sms,
            iconDesc = "SMS icon",
            title = "Check your messages",
            subtitle = "We sent a 6-digit code to\n${maskMobile(mobileNic)}\n\nEnter it below to continue"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── OTP PIN Fields ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            repeat(otpLength) { index ->
                OtpDigitBox(
                    value = otpValues[index],
                    onValueChange = { newVal ->
                        error = null
                        val digit = newVal.filter { it.isDigit() }.take(1)
                        otpValues[index] = digit
                        if (digit.isNotEmpty() && index < otpLength - 1) {
                            focusRequesters[index + 1].requestFocus()
                        }
                        // Auto-verify when all filled
                        if (otpValues.all { it.isNotEmpty() }) {
                            scope.launch {
                                isLoading = true
                                delay(800)
                                isLoading = false
                                onVerify()
                            }
                        }
                    },
                    onBackspace = {
                        if (otpValues[index].isEmpty() && index > 0) {
                            otpValues[index - 1] = ""
                            focusRequesters[index - 1].requestFocus()
                        }
                    },
                    focusRequester = focusRequesters[index],
                    isError = error != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (error != null) {
            Text(
                text = error!!,
                style = MaterialTheme.typography.labelSmall.copy(color = ErrorRed),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Resend OTP ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Didn't receive the code? ",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateMedium)
            )
            if (resendTimer > 0) {
                Text(
                    text = "Resend in ${resendTimer}s",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SlateLight,
                        fontWeight = FontWeight.Medium
                    )
                )
            } else {
                Text(
                    text = "Resend OTP",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ForestEmerald,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.clickable {
                        resendTimer = 30
                        otpValues.fill("")
                        scope.launch {
                            delay(100)
                            focusRequesters[0].requestFocus()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        MediFlowPrimaryButton(
            text = "Verify OTP",
            isLoading = isLoading,
            icon = Icons.Rounded.CheckCircle,
            contentDescription = "Verify OTP button",
            onClick = {
                val otp = otpValues.joinToString("")
                when {
                    otp.length < otpLength -> error = "Please enter all 6 digits"
                    else -> {
                        scope.launch {
                            isLoading = true
                            delay(1000)
                            isLoading = false
                            onVerify()
                        }
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OTP Digit Box
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OtpDigitBox(
    value: String,
    onValueChange: (String) -> Unit,
    onBackspace: () -> Unit,
    focusRequester: FocusRequester,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val isFilled = value.isNotEmpty()

    OutlinedTextField(
        value = value,
        onValueChange = { newVal ->
            if (newVal.isEmpty()) onBackspace()
            else onValueChange(newVal)
        },
        modifier = modifier
            .aspectRatio(1f)
            .focusRequester(focusRequester)
            .semantics { contentDescription = "OTP digit ${if (isFilled) "filled" else "empty"}" },
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = DarkSlate,
            fontSize = 22.sp
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestEmerald,
            unfocusedBorderColor = if (isFilled) ForestEmerald.copy(alpha = 0.5f) else BorderColor,
            errorBorderColor = ErrorRed,
            focusedContainerColor = if (isFilled) Color(0xFFECFDF5) else SurfaceWhite,
            unfocusedContainerColor = if (isFilled) Color(0xFFECFDF5) else SurfaceWhite,
            errorContainerColor = Color(0xFFFFF1F2),
            cursorColor = ForestEmerald
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3: Reset Password
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepReset(onReset: () -> Unit) {
    val focusManager = LocalFocusManager.current

    var newPassword by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var newPwdVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var newPwdError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {

        StepHeader(
            icon = Icons.Rounded.LockReset,
            iconDesc = "Lock reset icon",
            title = "Create new password",
            subtitle = "Your new password must be at least 8 characters and different from previous passwords"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Password strength indicator
        if (newPassword.isNotEmpty()) {
            PasswordStrengthIndicator(password = newPassword)
            Spacer(modifier = Modifier.height(12.dp))
        }

        MediFlowTextField(
            value = newPassword,
            onValueChange = { newPassword = it; newPwdError = null },
            label = "New Password",
            placeholder = "Minimum 8 characters",
            leadingIcon = Icons.Rounded.Lock,
            leadingIconDesc = "Lock icon",
            trailingIcon = if (newPwdVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            trailingIconDesc = if (newPwdVisible) "Hide password" else "Show password",
            onTrailingIconClick = { newPwdVisible = !newPwdVisible },
            visualTransformation = if (newPwdVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
            ),
            errorMessage = newPwdError,
            contentDescription = "New password input"
        )

        Spacer(modifier = Modifier.height(16.dp))

        MediFlowTextField(
            value = confirmPwd,
            onValueChange = { confirmPwd = it; confirmError = null },
            label = "Confirm New Password",
            placeholder = "Re-enter new password",
            leadingIcon = Icons.Rounded.LockOpen,
            leadingIconDesc = "Confirm lock icon",
            trailingIcon = if (confirmVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            trailingIconDesc = if (confirmVisible) "Hide password" else "Show password",
            onTrailingIconClick = { confirmVisible = !confirmVisible },
            visualTransformation = if (confirmVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            errorMessage = confirmError,
            contentDescription = "Confirm new password input"
        )

        Spacer(modifier = Modifier.height(32.dp))

        MediFlowPrimaryButton(
            text = "Reset Password",
            isLoading = isLoading,
            icon = Icons.Rounded.LockReset,
            contentDescription = "Reset password button",
            onClick = {
                var hasError = false
                when {
                    newPassword.length < 8 -> {
                        newPwdError = "Password must be at least 8 characters"
                        hasError = true
                    }
                    !newPassword.any { it.isDigit() } -> {
                        newPwdError = "Include at least one number"
                        hasError = true
                    }
                }
                if (confirmPwd != newPassword) {
                    confirmError = "Passwords do not match"
                    hasError = true
                }
                if (!hasError) {
                    scope.launch {
                        isLoading = true
                        delay(1500)
                        isLoading = false
                        onReset()
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4: Success
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepSuccess(onBackToLogin: () -> Unit) {
    val checkScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        checkScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ── Animated success icon ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(checkScale.value)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFECFDF5),
                            Color(0xFFD1FAE5)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(ForestEmerald, MintGreen)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Password reset successful checkmark",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Password Reset\nSuccessful!",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your password has been updated successfully.\nYou can now sign in with your new password.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SlateMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Security notice
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFECFDF5),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = ForestEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Security Tip",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ForestEmerald
                        )
                    )
                    Text(
                        text = "Never share your password with anyone, including MediFlow staff.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SlateMedium,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        MediFlowPrimaryButton(
            text = "Back to Login",
            icon = Icons.Rounded.Login,
            contentDescription = "Back to login button",
            onClick = onBackToLogin
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared: Step Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepHeader(
    icon: ImageVector,
    iconDesc: String,
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GradientStart.copy(alpha = 0.15f),
                            GradientEnd.copy(alpha = 0.10f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDesc,
                tint = ForestEmerald,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SlateMedium,
                lineHeight = 20.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Password Strength Indicator
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PasswordStrengthIndicator(password: String) {
    val strength = calculatePasswordStrength(password)

    val strengthLabel = when (strength) {
        1 -> "Weak"
        2 -> "Fair"
        3 -> "Good"
        4 -> "Strong"
        else -> ""
    }
    val strengthColor = when (strength) {
        1 -> ErrorRed
        2 -> AmberWarning
        3 -> MintGreen
        4 -> ForestEmerald
        else -> SlateLight
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password strength",
                style = MaterialTheme.typography.labelSmall.copy(color = SlateMedium)
            )
            Text(
                text = strengthLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = strengthColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) { index ->
                val segmentColor by animateColorAsState(
                    targetValue = if (index < strength) strengthColor else BorderColor,
                    animationSpec = tween(300),
                    label = "segmentColor$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(segmentColor)
                )
            }
        }
    }
}

private fun calculatePasswordStrength(password: String): Int {
    var score = 0
    if (password.length >= 8) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Chip
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InfoChip(text: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEFF6FF),  // Blue-50
        modifier = Modifier.semantics { contentDescription = text }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HealthcareBlue,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = HealthcareBlue,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility: Mask mobile number for privacy
// ─────────────────────────────────────────────────────────────────────────────
private fun maskMobile(input: String): String {
    return if (input.length >= 4) {
        val visible = input.takeLast(4)
        "${"*".repeat(input.length - 4)}$visible"
    } else {
        input
    }
}
