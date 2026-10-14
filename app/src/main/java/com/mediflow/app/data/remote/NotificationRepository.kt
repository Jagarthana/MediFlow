package com.mediflow.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mediflow.app.data.mapper.toAppNotification
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.model.AppNotification
import com.mediflow.app.data.model.NotificationType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The in-app inbox. Firestore rules let a patient read only rows addressed to them and a
 * pharmacist only rows addressed to their pharmacy, so queries never need a signed-in admin.
 *
 * Rows are read without an `orderBy` and sorted in memory: a `whereEqualTo` plus a
 * descending sort on another field would need a composite index for every audience.
 */
class NotificationRepository(private val db: FirebaseFirestore) {

    suspend fun forPatient(patientUid: String): List<AppNotification> =
        db.collection(FirestorePaths.NOTIFICATIONS)
            .whereEqualTo("patientUid", patientUid)
            .get().await().documents
            .map { it.toAppNotification() }
            .sortedByDescending { it.createdAtMillis }

    suspend fun forPharmacy(pharmacyId: String): List<AppNotification> =
        db.collection(FirestorePaths.NOTIFICATIONS)
            .whereEqualTo("pharmacyId", pharmacyId)
            .get().await().documents
            .map { it.toAppNotification() }
            .sortedByDescending { it.createdAtMillis }

    /** Live inbox, so a message the other side posts arrives without leaving the screen. */
    fun observeForPharmacy(pharmacyId: String): Flow<List<AppNotification>> =
        observe(db.collection(FirestorePaths.NOTIFICATIONS).whereEqualTo("pharmacyId", pharmacyId))

    fun observeForPatient(patientUid: String): Flow<List<AppNotification>> =
        observe(db.collection(FirestorePaths.NOTIFICATIONS).whereEqualTo("patientUid", patientUid))

    private fun observe(query: Query): Flow<List<AppNotification>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(
                    snapshot.documents
                        .map { it.toAppNotification() }
                        .sortedByDescending { it.createdAtMillis }
                )
            }
        }
        awaitClose { registration.remove() }
    }

    /** `id` is filled in by Firestore, so callers pass an AppNotification with a blank one. */
    private suspend fun post(notification: AppNotification): Result<String> = runCatching {
        val reference = db.collection(FirestorePaths.NOTIFICATIONS).document()
        reference.set(notification.copy(id = reference.id).toMap()).await()
        reference.id
    }

    suspend fun tellPatient(
        patientUid: String,
        type: NotificationType,
        title: String,
        body: String,
        reservationId: String? = null
    ): Result<String> = post(
        AppNotification(
            id = "",
            patientUid = patientUid,
            pharmacyId = null,
            type = type,
            title = title,
            body = body,
            reservationId = reservationId,
            createdAtMillis = System.currentTimeMillis(),
            readAtMillis = null
        )
    )

    /**
     * A patient asks a branch to ping them when something comes back in. The row carries both
     * addresses so the patient sees their request and the branch sees the demand signal, and it
     * passes the create rule either way.
     */
    suspend fun alertPharmacy(
        pharmacyId: String,
        patientUid: String,
        type: NotificationType,
        title: String,
        body: String
    ): Result<String> = post(
        AppNotification(
            id = "",
            patientUid = patientUid,
            pharmacyId = pharmacyId,
            type = type,
            title = title,
            body = body,
            reservationId = null,
            createdAtMillis = System.currentTimeMillis(),
            readAtMillis = null
        )
    )

    suspend fun markRead(notificationId: String): Result<Unit> = runCatching {
        db.collection(FirestorePaths.NOTIFICATIONS).document(notificationId)
            .update("readAtMillis", System.currentTimeMillis()).await()
    }

    suspend fun markAllRead(notifications: List<AppNotification>): Result<Unit> = runCatching {
        val unread = notifications.filter { !it.isRead }
        if (unread.isNotEmpty()) {
            val batch = db.batch()
            val now = System.currentTimeMillis()
            unread.forEach { batch.update(db.collection(FirestorePaths.NOTIFICATIONS).document(it.id), "readAtMillis", now) }
            batch.commit().await()
        }
    }
}
