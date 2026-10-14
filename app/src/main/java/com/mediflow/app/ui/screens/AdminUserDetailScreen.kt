package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.ui.theme.BorderColor
import com.mediflow.app.ui.theme.DarkSlate
import com.mediflow.app.ui.theme.ErrorRed
import com.mediflow.app.ui.theme.ForestEmerald
import com.mediflow.app.ui.theme.HealthcareBlue
import com.mediflow.app.ui.theme.LightBackground
import com.mediflow.app.ui.theme.SlateLight
import com.mediflow.app.ui.theme.SlateMedium
import com.mediflow.app.ui.theme.SuccessGreen
import com.mediflow.app.ui.theme.SurfaceWhite
import java.util.Locale

/**
 * Screen 20b — User Detail Screen
 *
 * Front-end only read-only display for admin management with:
 * - Read-only display of: Name, Email, Role, Created Date, Status
 * - "Disable Account" button shown when status == "active" with confirmation AlertDialog
 * - "Re-enable Account" button shown when status == "disabled" calling onEnable
 * - Form styling aligned with MediFlow design tokens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    user: AppUser,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onBack: (() -> Unit)? = null,
    isBusy: Boolean = false
) {
    var showDisableDialog by remember { mutableStateOf(false) }

    val isActive = user.status.equals("active", ignoreCase = true)
    val roleColor = when (user.role.lowercase(Locale.ROOT)) {
        "patient" -> HealthcareBlue
        "pharmacist" -> ForestEmerald
        "admin" -> DarkSlate
        else -> SlateMedium
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "User Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DarkSlate
                        )
                        Text(
                            text = "Account ID: ${user.id}",
                            fontSize = 12.sp,
                            color = SlateMedium
                        )
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
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        UserStatusBadge(status = user.status)
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
                        .navigationBarsPadding()
                ) {
                    if (isActive) {
                        // "Disable Account" Button (Active status)
                        OutlinedButton(
                            onClick = { showDisableDialog = true },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, ErrorRed),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorRed
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disable Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        // "Re-enable Account" Button (Disabled status)
                        Button(
                            onClick = onEnable,
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
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Re-enable Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Header Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val initials = user.name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.first().uppercase() }
                        .joinToString("")

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(roleColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (initials.isNotEmpty()) initials else "U",
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = roleColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = user.name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = user.email,
                        fontSize = 13.5.sp,
                        color = SlateMedium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserRoleBadge(role = user.role)
                        UserStatusBadge(status = user.status)
                    }
                }
            }

            // Account Information Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Account Information",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        text = "Read-only profile specifications",
                        fontSize = 12.sp,
                        color = SlateMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ReadOnlyDetailRow(
                        icon = Icons.Rounded.Person,
                        label = "Full Name",
                        value = user.name
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ReadOnlyDetailRow(
                        icon = Icons.Rounded.Email,
                        label = "Email Address",
                        value = user.email
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ReadOnlyDetailRow(
                        icon = Icons.Rounded.Security,
                        label = "User Role",
                        value = user.role.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ReadOnlyDetailRow(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Account Created",
                        value = user.createdAt
                    )
                }
            }

            // Security & Permissions Notice
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = (if (isActive) ForestEmerald else ErrorRed).copy(alpha = 0.06f),
                border = BorderStroke(1.dp, (if (isActive) ForestEmerald else ErrorRed).copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (isActive) ForestEmerald else ErrorRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isActive) {
                            "This user currently has active network permissions and can log in."
                        } else {
                            "This account is disabled. Login sessions and medication requests are suspended."
                        },
                        fontSize = 12.5.sp,
                        color = if (isActive) DarkSlate else ErrorRed,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }

    // Confirmation AlertDialog for Disabling Account
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = {
                Text(
                    text = "Disable Account?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkSlate
                )
            },
            text = {
                Text(
                    text = "Sets ${user.name}'s account to disabled. Signing in is refused while the account is disabled, and it stays refused until an administrator re-enables it.",
                    fontSize = 13.5.sp,
                    color = SlateMedium,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisableDialog = false
                        onDisable()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = SurfaceWhite
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Disable Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("Cancel", color = SlateMedium, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

/**
 * Form detail row displaying icon, label, and value
 */
@Composable
private fun ReadOnlyDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SlateLight.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SlateMedium,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = SlateMedium
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkSlate
            )
        }
    }
}
