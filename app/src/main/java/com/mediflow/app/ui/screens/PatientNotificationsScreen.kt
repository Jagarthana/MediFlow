package com.mediflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediflow.app.data.AppGraph
import com.mediflow.app.data.model.AppNotification
import com.mediflow.app.data.model.NotificationType
import com.mediflow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Model
// ─────────────────────────────────────────────────────────────────────────────

private enum class PatientNotifType(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color
) {
    READY(
        Icons.Rounded.CheckCircle,
        Color(0xFF059669),
        Color(0xFFECFDF5)
    ),
    REJECTED(
        Icons.Rounded.Cancel,
        Color(0xFFDC2626),
        Color(0xFFFEF2F2)
    ),
    STOCK(
        Icons.Rounded.Inventory2,
        Color(0xFF2563EB),
        Color(0xFFEFF6FF)
    ),
    REMINDER(
        Icons.Rounded.Schedule,
        Color(0xFFD97706),
        Color(0xFFFFFBEB)
    ),
    GENERAL(
        Icons.Rounded.Info,
        Color(0xFF6B7280),
        Color(0xFFF9FAFB)
    )
}

private data class PatientNotif(
    val id: String,
    val type: PatientNotifType,
    val title: String,
    val body: String,
    val time: String,
    val isRead: Boolean
)

private fun AppNotification.toPatientNotif(now: Long): PatientNotif = PatientNotif(
    id = id,
    type = when (type) {
        NotificationType.RESERVATION_CONFIRMED, NotificationType.RESERVATION_READY ->
            PatientNotifType.READY
        NotificationType.RESERVATION_EXPIRED, NotificationType.RESERVATION_CANCELLED ->
            PatientNotifType.REJECTED
        NotificationType.STOCK_SHORTAGE -> PatientNotifType.STOCK
        NotificationType.ORDER_INCOMING -> PatientNotifType.GENERAL
    },
    title = title,
    body = body,
    time = relativeTime(createdAtMillis, now),
    isRead = isRead
)

private fun relativeTime(createdAtMillis: Long, now: Long): String {
    val seconds = ((now - createdAtMillis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 45 -> "just now"
        seconds < 90 -> "1 min ago"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 7200 -> "1 h ago"
        seconds < 86400 -> "${seconds / 3600} h ago"
        seconds < 172800 -> "Yesterday"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(createdAtMillis))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientNotificationsScreen(onBack: () -> Unit) {
    var inbox by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    val uid = AppGraph.currentProfile?.uid
    LaunchedEffect(uid) {
        inbox = if (uid == null) {
            emptyList()
        } else {
            runCatching { AppGraph.notifications.forPatient(uid) }.getOrNull().orEmpty()
        }
    }

    val scope = rememberCoroutineScope()
    val unreadCount = inbox.count { !it.isRead }
    val now = System.currentTimeMillis()

    // Patients have no delete permission, so dismissing a row marks it read instead.
    fun markReadOne(target: AppNotification) {
        if (target.isRead) return
        scope.launch {
            val marked = runCatching { AppGraph.notifications.markRead(target.id) }.isSuccess
            if (marked) {
                val stamp = System.currentTimeMillis()
                inbox = inbox.map { if (it.id == target.id) it.copy(readAtMillis = stamp) else it }
            }
        }
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = DarkSlate
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = DarkSlate,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (unreadCount > 0) {
                            Text(
                                text = "$unreadCount unread",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ForestEmerald,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = {
                                val pending = inbox
                                scope.launch {
                                    val marked = runCatching {
                                        AppGraph.notifications.markAllRead(pending)
                                    }.isSuccess
                                    if (marked) {
                                        val stamp = System.currentTimeMillis()
                                        inbox = inbox.map {
                                            it.copy(readAtMillis = it.readAtMillis ?: stamp)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                "Mark all read",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = ForestEmerald,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (inbox.isEmpty()) {
            EmptyNotificationsState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            val unread = inbox.filter { !it.isRead }
            val read = inbox.filter { it.isRead }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (unread.isNotEmpty()) {
                    item {
                        NotifSectionHeader(title = "New")
                    }
                    items(unread, key = { it.id }) { notif ->
                        NotifCard(
                            notif = notif.toPatientNotif(now),
                            onDismiss = { markReadOne(notif) },
                            onMarkRead = { markReadOne(notif) }
                        )
                    }
                }

                if (read.isNotEmpty()) {
                    item {
                        NotifSectionHeader(title = "Earlier")
                    }
                    items(read, key = { it.id }) { notif ->
                        NotifCard(
                            notif = notif.toPatientNotif(now),
                            onDismiss = { markReadOne(notif) },
                            onMarkRead = { markReadOne(notif) }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotifSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            color = SlateMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotifCard(
    notif: PatientNotif,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onMarkRead() }
            .semantics {
                contentDescription = "${notif.title}: ${notif.body}"
            },
        shape = RoundedCornerShape(16.dp),
        color = if (notif.isRead) SurfaceWhite else Color(0xFFF0FDF9),
        tonalElevation = if (notif.isRead) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon bubble
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(notif.type.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = notif.type.icon,
                    contentDescription = null,
                    tint = notif.type.tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = notif.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = DarkSlate,
                            fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Unread dot
                    if (!notif.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ForestEmerald)
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = notif.body,
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMedium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notif.time,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateLight,
                            fontSize = 11.sp
                        )
                    )
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "Dismiss",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SlateLight,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyNotificationsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFECFDF5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "You're all caught up!",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = DarkSlate,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No new notifications right now.",
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMedium)
        )
    }
}
