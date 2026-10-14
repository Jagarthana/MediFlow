package com.mediflow.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.CartStore
import com.mediflow.app.ui.theme.*
import java.io.File
import kotlinx.coroutines.launch

/**
 * Screen 5 — Prescription Slip Capture (Variant B / Figma 3A)
 *
 * Layout, top to bottom:
 *  • Top Bar     — back arrow + "Prescription Slip Capture"
 *  • Viewfinder  — large rounded surface with 4 L-shaped corner brackets,
 *                  amber "Avoid Glare" warning, alignment hint, encryption notice
 *  • Controls    — gallery picker | circular shutter | flash toggle
 *
 * The viewfinder is a stand-in: it renders a dark gradient rather than a live
 * camera feed. Swapping in a CameraX Preview replaces [MockViewfinderSurface]
 * only; the overlays, brackets and controls sit in a Box on top and stay as-is.
 * Likewise [glareDetected] is a fixed flag until frame analysis exists.
 *
 * WCAG 2.1 AA: 48dp+ touch targets, semantic labels on every control
 */

private val ViewfinderTop = Color(0xFF16202E)
private val ViewfinderBottom = Color(0xFF060A12)

@Composable
fun PhotoCaptureScreen(onBack: () -> Unit, onCaptured: () -> Unit) {
    var flashOn by remember { mutableStateOf(false) }
    var glareDetected by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val cameraTargetPath = rememberSaveable { mutableStateOf<String?>(null) }
    // The camera runs in another app and writes into a file this app owns, so the handoff
    // goes through a FileProvider uri. Two things silently kill that contract: the camera
    // process needs an explicit grant on the uri (TakePicture() never adds one, so many
    // camera apps open and immediately close), and the target must survive this activity
    // being recreated while the camera is on top of it, which plain remember() does not.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val path = cameraTargetPath.value
        cameraTargetPath.value = null
        val captured = capturedImage(result, path)
        if (captured == null) {
            scope.launch { snackbarHostState.showSnackbar("Capture cancelled") }
        } else {
            upload(captured, context, scope, snackbarHostState, onCaptured, { uploading = it }) {
                path?.let { runCatching { File(it).delete() } }
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) upload(uri, context, scope, snackbarHostState, onCaptured, onUploading = { uploading = it })
    }

    ForceLightSystemBars()

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CaptureTopBar(title = "Prescription Slip Capture", onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                if (uploading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MintGreen, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Uploading your slip…",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                        }
                    }
                } else {
                    MockViewfinderSurface()
                    CornerBrackets()

                    if (glareDetected) {
                        GlareWarning(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 18.dp)
                        )
                    }

                    Text(
                        text = "Align prescription within frame",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                    )

                    PrivacyNotice(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }

            CaptureControls(
                enabled = !uploading,
                flashOn = flashOn,
                onToggleFlash = { flashOn = !flashOn },
                onPickFromGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCapture = {
                    val target = File(context.cacheDir, "slip_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        .addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    val camera = intent.resolveActivity(context.packageManager)
                    if (camera == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No camera app on this device. Use the gallery.")
                        }
                    } else {
                        runCatching {
                            context.grantUriPermission(
                                camera.packageName, uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                        cameraTargetPath.value = target.absolutePath
                        cameraLauncher.launch(intent)
                    }
                }
            )
        }
    }
}

/**
 * Resolves what the camera actually produced. Most camera apps honour EXTRA_OUTPUT and leave
 * the bytes in the file we handed them; a few return a content uri for their own copy instead,
 * so that is the fallback. Neither present means nothing was captured — historically the
 * signature of a camera app that was never granted write access and quit immediately.
 */
private fun capturedImage(result: ActivityResult, path: String?): Uri? {
    if (result.resultCode != Activity.RESULT_OK) return null
    File(path.orEmpty()).takeIf { it.length() > 0L }?.let { return Uri.fromFile(it) }
    return result.data?.data
}

/**
 * Uploads straight to the signed-in patient's own Storage folder, then hands the object path
 * to [CartStore] so the cart keeps it across the navigation round trip. If Storage is not
 * enabled yet the cart simply stays without a slip and says why.
 */
private fun upload(
    uri: Uri,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onCaptured: () -> Unit,
    onUploading: (Boolean) -> Unit,
    cleanup: () -> Unit = {}
) {
    val uid = AppGraph.currentProfile?.uid
    if (uid == null) {
        scope.launch { snackbarHostState.showSnackbar("Sign in again before attaching a slip.") }
        return
    }
    onUploading(true)
    scope.launch {
        val outcome = AppGraph.slips.uploadSlip(uid, uri)
        onUploading(false)
        outcome.fold(
            onSuccess = { path ->
                cleanup()
                CartStore.attachSlip(path, uri.lastPathSegment?.substringAfterLast('/').orEmpty()
                    .ifBlank { "prescription.jpg" })
                onCaptured()
            },
            onFailure = { throwable ->
                snackbarHostState.showSnackbar(
                    "Couldn't upload the slip (${throwable.message?.take(60) ?: "no connection"}). " +
                        "You can still reserve and bring the paper copy."
                )
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureTopBar(title: String, onBack: () -> Unit) {
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
                .semantics { contentDescription = "Back to dashboard" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewfinder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MockViewfinderSurface() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(ViewfinderTop, ViewfinderBottom)
                )
            )
    )
}

@Composable
private fun CornerBrackets() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Prescription alignment frame" }
    ) {
        val inset = 18.dp.toPx()
        val arm = 28.dp.toPx()
        val stroke = 3.dp.toPx()
        val right = size.width - inset
        val bottom = size.height - inset
        val bracket = Color.White.copy(alpha = 0.92f)

        // Top-left
        drawLine(bracket, Offset(inset, inset), Offset(inset + arm, inset), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(inset, inset), Offset(inset, inset + arm), stroke, StrokeCap.Round)
        // Top-right
        drawLine(bracket, Offset(right, inset), Offset(right - arm, inset), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(right, inset), Offset(right, inset + arm), stroke, StrokeCap.Round)
        // Bottom-left
        drawLine(bracket, Offset(inset, bottom), Offset(inset + arm, bottom), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(inset, bottom), Offset(inset, bottom - arm), stroke, StrokeCap.Round)
        // Bottom-right
        drawLine(bracket, Offset(right, bottom), Offset(right - arm, bottom), stroke, StrokeCap.Round)
        drawLine(bracket, Offset(right, bottom), Offset(right, bottom - arm), stroke, StrokeCap.Round)
    }
}

@Composable
private fun GlareWarning(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = "Warning: avoid glare on the prescription" },
        shape = RoundedCornerShape(20.dp),
        color = AmberWarning.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = AmberWarning,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Avoid Glare",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AmberWarning,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun PrivacyNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Only your account and the pharmacy you reserve with can see this image" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MintGreen,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "Private to your account",
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White.copy(alpha = 0.62f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureControls(
    enabled: Boolean,
    flashOn: Boolean,
    onToggleFlash: () -> Unit,
    onPickFromGallery: () -> Unit,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ControlIconButton(
            icon = Icons.Rounded.PhotoLibrary,
            label = "Choose from gallery",
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            onClick = onPickFromGallery
        )

        ShutterButton(onClick = onCapture, enabled = enabled)

        ControlIconButton(
            icon = if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
            label = if (flashOn) "Flash on" else "Flash off",
            tint = if (flashOn) AmberWarning else Color.White,
            onClick = onToggleFlash
        )
    }
}

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .semantics { contentDescription = label }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Capture prescription slip" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            )
        }
    }
}

/**
 * MediFlowTheme forces dark status-bar icons while the app runs in light mode,
 * which disappears against this screen's near-black background. Light icons are
 * forced for as long as this screen is composed, then handed back.
 */
@Composable
private fun ForceLightSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }
}
