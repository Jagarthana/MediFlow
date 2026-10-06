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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.*

/**
 * Screen 2 — Login / Sign Up (Tabbed Toggle)
 *
 * Design Spec (Variant B — Selected):
 * • Top: MediFlow logo (small) centered
 * • TabRow: "Log In" | "Sign Up" — Material3 PrimaryTabRow
 * • Log In Form: role selector (Patient | Pharmacist | Admin), Mobile/NIC,
 *   Password (eye toggle), Forgot Password, Sign In button
 * • Sign Up Form: Full Name, Mobile/NIC, Email, Password, Confirm, T&C checkbox
 * • Bottom security badge: "🔒 SLMC Compliant Data Protection"
 *
 * Nielsen Heuristics: #4 (Consistency & Standards), #8 (Aesthetic & Minimalist Design)
 * WCAG 2.1 AA: 48dp touch targets, semantic labels, error announcements
 *
 * Sign Up always creates a patient account — the pharmacist and admin tracks are
 * separate app surfaces reached by role, so the role selector only appears on
 * Log In. Until the backend issues role-scoped tokens the selector is the only
 * thing deciding which console opens.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onPharmacistLogin: () -> Unit,
    onAdminLogin: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        // ── Decorative top gradient band ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GradientStart.copy(alpha = 0.12f),
                            LightBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ── MediFlow Mini Logo ────────────────────────────────────────────
            MediFlowMiniLogo()

            Spacer(modifier = Modifier.height(32.dp))

            // ── Tab Row ───────────────────────────────────────────────────────
            MediFlowTabRow(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Animated form content ─────────────────────────────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "FormTransition"
            ) { tab ->
                if (tab == 0) {
                    LoginForm(
                        onLoginSuccess = onLoginSuccess,
                        onPharmacistLogin = onPharmacistLogin,
                        onAdminLogin = onAdminLogin,
                        onForgotPassword = onForgotPassword,
                        onSwitchToSignUp = { selectedTab = 1 }
                    )
                } else {
                    SignUpForm(
                        onSignUpSuccess = onLoginSuccess,
                        onSwitchToLogin = { selectedTab = 0 }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── SLMC Security Badge ───────────────────────────────────────────
            SecurityBadge()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini Logo Component
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MediFlowMiniLogo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MedicalServices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "MediFlow",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
        )
        Text(
            text = "Sri Lanka's Medical Companion",
            style = MaterialTheme.typography.bodySmall.copy(
                color = SlateMedium,
                letterSpacing = 0.3.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Row Component
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MediFlowTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Log In", "Sign Up")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BorderColor.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) Brush.linearGradient(
                                colors = listOf(GradientStart, GradientEnd)
                            )
                            else Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Transparent)
                            )
                        )
                        .clickable { onTabSelected(index) }
                        .semantics { contentDescription = "$label tab" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else SlateMedium,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Role Selector (Patient | Pharmacist | Admin)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RoleSelector(
    selected: LoginRole,
    onSelected: (LoginRole) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sign in as",
            style = MaterialTheme.typography.labelLarge.copy(
                color = SlateMedium,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = BorderColor.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LoginRole.entries.forEach { role ->
                    val isSelected = role == selected
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) SurfaceWhite else Color.Transparent)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = ForestEmerald,
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelected(role) }
                            .semantics { contentDescription = "Sign in as ${role.label}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = role.icon,
                            contentDescription = null,
                            tint = if (isSelected) ForestEmerald else SlateMedium,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = role.label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) ForestEmerald else SlateMedium,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Login Form
// ─────────────────────────────────────────────────────────────────────────────
private enum class LoginRole(
    val label: String,
    val icon: ImageVector,
    val signInLabel: String,
    val signInDescription: String
) {
    PATIENT(
        label = "Patient",
        icon = Icons.Rounded.Person,
        signInLabel = "Sign In Securely",
        signInDescription = "Sign in securely button"
    ),
    PHARMACIST(
        label = "Pharmacist",
        icon = Icons.Rounded.LocalPharmacy,
        signInLabel = "Sign In to Pharmacy",
        signInDescription = "Sign in to the pharmacy stock screen"
    ),
    ADMIN(
        label = "Admin",
        icon = Icons.Rounded.AdminPanelSettings,
        signInLabel = "Sign In to Console",
        signInDescription = "Sign in to the system admin console"
    )
}

@Composable
private fun LoginForm(
    onLoginSuccess: () -> Unit,
    onPharmacistLogin: () -> Unit,
    onAdminLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    onSwitchToSignUp: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    var role by remember { mutableStateOf(LoginRole.PATIENT) }
    var mobileNic by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var mobileError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Welcome text ──────────────────────────────────────────────────
        Text(
            text = "Welcome back 👋",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
        )
        Text(
            text = "Sign in to manage your prescriptions",
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMedium),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // ── Role selector ─────────────────────────────────────────────────
        RoleSelector(
            selected = role,
            onSelected = { role = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Mobile / NIC Field ────────────────────────────────────────────
        MediFlowTextField(
            value = mobileNic,
            onValueChange = {
                mobileNic = it
                mobileError = null
            },
            label = "Mobile / NIC Number",
            placeholder = "07X XXXX XXX or 200XXXXXXXXX",
            leadingIcon = Icons.Rounded.Phone,
            leadingIconDesc = "Phone icon",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            errorMessage = mobileError,
            contentDescription = "Mobile or NIC number input field"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Password Field ────────────────────────────────────────────────
        MediFlowTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = "Password",
            placeholder = "Enter your password",
            leadingIcon = Icons.Rounded.Lock,
            leadingIconDesc = "Lock icon",
            trailingIcon = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            trailingIconDesc = if (passwordVisible) "Hide password" else "Show password",
            onTrailingIconClick = { passwordVisible = !passwordVisible },
            visualTransformation = if (passwordVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            errorMessage = passwordError,
            contentDescription = "Password input field"
        )

        // ── Forgot Password ───────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(36.dp)
                    .semantics { contentDescription = "Forgot password button" }
            ) {
                Text(
                    text = "Forgot Password?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = HealthcareBlue,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Sign In Button ────────────────────────────────────────────────
        MediFlowPrimaryButton(
            text = role.signInLabel,
            isLoading = isLoading,
            icon = if (role == LoginRole.PATIENT) Icons.Rounded.Lock else role.icon,
            contentDescription = role.signInDescription,
            onClick = {
                var hasError = false
                if (mobileNic.isBlank()) {
                    mobileError = "Please enter your mobile number or NIC"
                    hasError = true
                }
                if (password.isBlank()) {
                    passwordError = "Please enter your password"
                    hasError = true
                } else if (password.length < 6) {
                    passwordError = "Password must be at least 6 characters"
                    hasError = true
                }
                if (!hasError) {
                    isLoading = true
                    // Simulate auth — replace with real auth logic. The role is a
                    // UI-only branch until the backend can issue role-scoped tokens.
                    when (role) {
                        LoginRole.PATIENT -> onLoginSuccess()
                        LoginRole.PHARMACIST -> onPharmacistLogin()
                        LoginRole.ADMIN -> onAdminLogin()
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Divider with OR ───────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
            Text(
                text = "  or continue with  ",
                style = MaterialTheme.typography.bodySmall.copy(color = SlateLight)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Social Login Placeholders ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SocialLoginButton(
                modifier = Modifier.weight(1f),
                label = "Google",
                icon = Icons.Rounded.Language,
                contentDescription = "Continue with Google",
                onClick = { /* TODO: Google sign-in */ }
            )
            SocialLoginButton(
                modifier = Modifier.weight(1f),
                label = "Facebook",
                icon = Icons.Rounded.Groups,
                contentDescription = "Continue with Facebook",
                onClick = { /* TODO: Facebook sign-in */ }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Switch to Sign Up ─────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateMedium)
            )
            Text(
                text = "Sign Up",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = ForestEmerald,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.clickable { onSwitchToSignUp() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign Up Form
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignUpForm(
    onSignUpSuccess: () -> Unit,
    onSwitchToLogin: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    var fullName      by remember { mutableStateOf("") }
    var mobileNic     by remember { mutableStateOf("") }
    var email         by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }
    var confirmPwd    by remember { mutableStateOf("") }
    var passwordVisible  by remember { mutableStateOf(false) }
    var confirmVisible   by remember { mutableStateOf(false) }
    var termsAccepted    by remember { mutableStateOf(false) }
    var isLoading        by remember { mutableStateOf(false) }

    var nameError     by remember { mutableStateOf<String?>(null) }
    var mobileError   by remember { mutableStateOf<String?>(null) }
    var emailError    by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError  by remember { mutableStateOf<String?>(null) }
    var termsError    by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "Create Account 🎉",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = DarkSlate
            )
        )
        Text(
            text = "Join MediFlow — it's free",
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMedium),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Full Name
        MediFlowTextField(
            value = fullName,
            onValueChange = { fullName = it; nameError = null },
            label = "Full Name",
            placeholder = "e.g. Kavindu Perera",
            leadingIcon = Icons.Rounded.Person,
            leadingIconDesc = "Person icon",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            errorMessage = nameError,
            contentDescription = "Full name input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Mobile / NIC
        MediFlowTextField(
            value = mobileNic,
            onValueChange = { mobileNic = it; mobileError = null },
            label = "Mobile / NIC Number",
            placeholder = "07X XXXX XXX or 200XXXXXXXXX",
            leadingIcon = Icons.Rounded.Phone,
            leadingIconDesc = "Phone icon",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            errorMessage = mobileError,
            contentDescription = "Mobile or NIC input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Email (optional)
        MediFlowTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = "Email Address (Optional)",
            placeholder = "you@example.com",
            leadingIcon = Icons.Rounded.Email,
            leadingIconDesc = "Email icon",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            errorMessage = emailError,
            contentDescription = "Email address input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password
        MediFlowTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = "Password",
            placeholder = "Minimum 8 characters",
            leadingIcon = Icons.Rounded.Lock,
            leadingIconDesc = "Lock icon",
            trailingIcon = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            trailingIconDesc = if (passwordVisible) "Hide password" else "Show password",
            onTrailingIconClick = { passwordVisible = !passwordVisible },
            visualTransformation = if (passwordVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            errorMessage = passwordError,
            contentDescription = "Password input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Confirm Password
        MediFlowTextField(
            value = confirmPwd,
            onValueChange = { confirmPwd = it; confirmError = null },
            label = "Confirm Password",
            placeholder = "Re-enter your password",
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
            contentDescription = "Confirm password input"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Terms & Conditions ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it; termsError = null },
                colors = CheckboxDefaults.colors(
                    checkedColor = ForestEmerald,
                    uncheckedColor = if (termsError != null) ErrorRed else SlateMedium
                ),
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "Accept terms and conditions" }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "I agree to the Terms & Conditions and Privacy Policy",
                    style = MaterialTheme.typography.bodySmall.copy(color = DarkSlate),
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (termsError != null) {
                    Text(
                        text = termsError!!,
                        style = MaterialTheme.typography.labelSmall.copy(color = ErrorRed),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Create Account Button ─────────────────────────────────────────
        MediFlowPrimaryButton(
            text = "Create Account",
            isLoading = isLoading,
            icon = Icons.Rounded.PersonAdd,
            contentDescription = "Create account button",
            onClick = {
                var hasError = false
                if (fullName.isBlank()) { nameError = "Full name is required"; hasError = true }
                if (mobileNic.isBlank()) { mobileError = "Mobile or NIC is required"; hasError = true }
                if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailError = "Enter a valid email address"; hasError = true
                }
                if (password.length < 8) { passwordError = "Password must be at least 8 characters"; hasError = true }
                if (confirmPwd != password) { confirmError = "Passwords do not match"; hasError = true }
                if (!termsAccepted) { termsError = "You must accept the Terms & Conditions"; hasError = true }
                if (!hasError) {
                    isLoading = true
                    onSignUpSuccess()
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Switch to Login ───────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateMedium)
            )
            Text(
                text = "Log In",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = ForestEmerald,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.clickable { onSwitchToLogin() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable: MediFlow TextField
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MediFlowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leadingIconDesc: String = "",
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIconDesc: String = "",
    onTrailingIconClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    errorMessage: String? = null,
    contentDescription: String = label,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateLight)
                )
            },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = leadingIconDesc,
                        tint = if (errorMessage != null) ErrorRed else SlateMedium,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = trailingIcon?.let {
                {
                    IconButton(
                        onClick = { onTrailingIconClick?.invoke() },
                        modifier = Modifier.semantics {
                            this.contentDescription = trailingIconDesc
                        }
                    ) {
                        Icon(
                            imageVector = it,
                            contentDescription = trailingIconDesc,
                            tint = SlateMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            isError = errorMessage != null,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestEmerald,
                unfocusedBorderColor = BorderColor,
                errorBorderColor = ErrorRed,
                focusedLabelColor = ForestEmerald,
                unfocusedLabelColor = SlateMedium,
                errorLabelColor = ErrorRed,
                cursorColor = ForestEmerald,
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                errorContainerColor = Color(0xFFFFF1F2)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .semantics { this.contentDescription = contentDescription }
        )
        // Error text
        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(color = ErrorRed)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable: MediFlow Primary Button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MediFlowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String = text,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = if (enabled && !isLoading)
                    Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
                else
                    Brush.horizontalGradient(colors = listOf(SlateLight, SlateLight))
            )
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable: Social Login Button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SocialLoginButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceWhite,
            contentColor = DarkSlate
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SlateMedium,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = DarkSlate
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Security Badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SecurityBadge() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFECFDF5),  // Emerald-50
        modifier = Modifier.semantics {
            contentDescription = "Security certification badge"
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "SLMC Compliant Data Protection",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = ForestEmerald,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp
                )
            )
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
