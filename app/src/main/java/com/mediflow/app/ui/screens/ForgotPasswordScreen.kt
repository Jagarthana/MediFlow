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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.auth.ResetOutcome
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 3 — Forgot Password
 *
 * Two real outcomes, decided by [com.mediflow.app.data.auth.AuthRepository.sendPasswordReset]:
 *  MOBILE → SENT  a reset email was actually delivered (account was created with an email)
 *  MOBILE → HELP  account is keyed on phone / NIC / pharmacy ID, which has no mailbox,
 *                 so the admin has to set a new password
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var currentStep by remember { mutableStateOf(ForgotPasswordStep.MOBILE) }
    var mobileNic by remember { mutableStateOf("") }
    var sentEmail by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            ForgotPasswordTopBar(
                currentStep = currentStep,
                onBack = {
                    when (currentStep) {
                        ForgotPasswordStep.MOBILE -> onBack()
                        ForgotPasswordStep.SENT,
                        ForgotPasswordStep.HELP  -> currentStep = ForgotPasswordStep.MOBILE
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
            ForgotPasswordProgressBar(currentStep = currentStep)

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
                            onEmailSent = { email ->
                                sentEmail = email
                                currentStep = ForgotPasswordStep.SENT
                            },
                            onAdminHelp = { currentStep = ForgotPasswordStep.HELP }
                        )
                        ForgotPasswordStep.SENT -> StepEmailSent(
                            email = sentEmail,
                            onBackToLogin = onBackToLogin
                        )
                        ForgotPasswordStep.HELP -> StepAdminHelp(
                            identifier = mobileNic,
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
    SENT("Check Your Email"),
    HELP("Ask the Admin")
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
            if (currentStep != ForgotPasswordStep.MOBILE) {
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
// Step 1: Enter Mobile / NIC / Pharmacy ID / Email
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepMobile(
    mobileNic: String,
    onMobileChange: (String) -> Unit,
    onEmailSent: (String) -> Unit,
    onAdminHelp: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {

        StepHeader(
            icon = Icons.Rounded.PhoneLocked,
            iconDesc = "Phone with lock icon",
            title = "Forgot your password?",
            subtitle = "Enter the mobile number, NIC, pharmacy ID or email you registered with. " +
                    "If your account uses an email we send a reset link; otherwise the admin resets it for you."
        )

        Spacer(modifier = Modifier.height(32.dp))

        MediFlowTextField(
            value = mobileNic,
            onValueChange = { onMobileChange(it); error = null },
            label = "Mobile / NIC / Pharmacy ID / Email",
            placeholder = "07X XXXX XXX, NIC, HL-KDY-DEMO or you@example.com",
            leadingIcon = Icons.Rounded.Phone,
            leadingIconDesc = "Phone icon",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    scope.launch {
                        isLoading = true
                        submitReset(mobileNic, onEmailSent, onAdminHelp) { error = it }
                        isLoading = false
                    }
                }
            ),
            errorMessage = error,
            contentDescription = "Mobile, NIC, pharmacy ID or email field for password reset"
        )

        Spacer(modifier = Modifier.height(8.dp))

        InfoChip(
            text = "Accounts registered with a phone or NIC are reset by the MediFlow admin",
            icon = Icons.Rounded.Info
        )

        Spacer(modifier = Modifier.height(32.dp))

        MediFlowPrimaryButton(
            text = "Start Reset",
            isLoading = isLoading,
            icon = Icons.Rounded.Send,
            contentDescription = "Start password reset button",
            onClick = {
                if (mobileNic.isBlank()) {
                    error = "Enter your mobile number, NIC, pharmacy ID or email"
                } else {
                    scope.launch {
                        isLoading = true
                        submitReset(mobileNic, onEmailSent, onAdminHelp) { error = it }
                        isLoading = false
                    }
                }
            }
        )
    }
}

/** Runs the real reset request and routes the outcome. `onError` receives the message. */
private suspend fun submitReset(
    identifier: String,
    onEmailSent: (String) -> Unit,
    onAdminHelp: () -> Unit,
    onError: (String) -> Unit
) {
    when (val outcome = AppGraph.auth.sendPasswordReset(identifier)) {
        is ResetOutcome.EmailSent -> onEmailSent(outcome.email)
        is ResetOutcome.AdminResetRequired -> onAdminHelp()
        is ResetOutcome.Failed -> onError(outcome.message)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2a: Reset email actually sent
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepEmailSent(email: String, onBackToLogin: () -> Unit) {
    SuccessIcon(
        icon = Icons.Rounded.MailOutline,
        iconDesc = "Reset email sent",
        title = "Reset link sent",
        subtitle = "We emailed a password reset link to $email.\nOpen it on this device or whichever " +
                "device can read that inbox — the link expires in one hour."
    ) {
        MediFlowPrimaryButton(
            text = "Back to Login",
            icon = Icons.Rounded.Login,
            contentDescription = "Back to login button",
            onClick = onBackToLogin
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2b: Account has no mailbox — admin must reset
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StepAdminHelp(identifier: String, onBackToLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SuccessIcon(
            icon = Icons.Rounded.AdminPanelSettings,
            iconDesc = "Admin reset required",
            title = "The admin resets this one",
            subtitle = "Your MediFlow account signs in with $identifier, so it has no email inbox " +
                    "for us to send a link to. Only the MediFlow admin can set a new password for it."
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFBEB),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "What to do",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = DarkSlate
                        )
                    )
                    Text(
                        text = "Tell the admin the identifier above and they will re-create your sign-in " +
                                "with a new password. You can then change it once you are back in.",
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
// Shared: animated result icon + title + subtitle, content slot below
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SuccessIcon(
    icon: ImageVector,
    iconDesc: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
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

        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale.value)
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
                    imageVector = icon,
                    contentDescription = iconDesc,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = SlateMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        content()
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
