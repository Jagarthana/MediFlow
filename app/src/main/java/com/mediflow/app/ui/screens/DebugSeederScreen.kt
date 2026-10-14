package com.mediflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mediflow.app.data.seed.FirestoreSeeder
import com.mediflow.app.data.seed.SeedException
import com.mediflow.app.data.seed.SeedResult
import com.mediflow.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Debug-only screen that writes the workbook-backed demo catalogue into Firestore.
 * The admin account is the caller's own; nothing here invents credentials.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSeederScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(FirestoreSeeder.DEFAULT_ADMIN_EMAIL) }
    var password by remember { mutableStateOf(FirestoreSeeder.DEFAULT_ADMIN_PASSWORD) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Demo seeder", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = DarkSlate
                )
            )
        },
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Removes the superseded demo accounts, then writes 2 pharmacies, 50 medicines and 100 stock rows plus the Jagarthana, Uthkarsha, Health Lanka pharmacist and admin accounts.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SlateMedium
            )
            Spacer(Modifier.height(16.dp))

            SeederCard {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Admin email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Admin email field" }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Admin password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Admin password field" }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sign-ins listed in assets/seed/seed_catalog.json: both patients and the admin use " +
                            "mediflow, the Health Lanka pharmacist uses mediflow123. " +
                            "Re-running updates the same documents and removes superseded accounts.",
                    fontSize = 12.5.sp,
                    color = SlateMedium
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    busy = true
                    message = null
                    val seeder = FirestoreSeeder(
                        context.applicationContext,
                        FirebaseFirestore.getInstance(),
                        FirebaseAuth.getInstance()
                    )
                    val enteredEmail = email.trim()
                    val enteredPassword = password
                    scope.launch {
                        message = runCatching { seeder.run(enteredEmail, enteredPassword) }
                            .fold(
                                onSuccess = { it.describe() },
                                onFailure = { cause ->
                                    when (cause) {
                                        is SeedException -> cause.message ?: "Seeding failed."
                                        else -> "Seeding failed: ${cause.message}"
                                    }
                                }
                            )
                        busy = false
                    }
                },
                enabled = !busy && email.isNotBlank() && password.length >= 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.White,
                    disabledContainerColor = BorderColor,
                    disabledContentColor = SlateMedium
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.DataObject, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Seed Firestore", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            message?.let { text ->
                Spacer(Modifier.height(16.dp))
                SeederCard(borderColor = if (text.startsWith("Seeded")) SuccessGreen else ErrorRed) {
                    Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = DarkSlate)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SeederCard(
    borderColor: Color = BorderColor,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

private fun SeedResult.describe(): String = buildString {
    append("Seeded $medicinesWritten medicines and $stockWritten stock rows. ")
    if (accountsRemoved.isNotEmpty()) append("Removed ${accountsRemoved.joinToString()}. ")
    if (accountsCreated.isNotEmpty()) append("Created ${accountsCreated.joinToString()}. ")
    if (accountsAlreadyPresent.isNotEmpty()) append("Already existed: ${accountsAlreadyPresent.joinToString()}. ")
    if (aliasesWritten.isNotEmpty()) append("Extra sign-ins: ${aliasesWritten.joinToString()}.")
}
