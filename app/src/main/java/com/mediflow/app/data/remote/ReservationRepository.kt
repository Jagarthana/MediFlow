package com.mediflow.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.mapper.toReservation
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationItem
import com.mediflow.app.data.model.ReservationStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class InsufficientStockException(
    val medicineName: String,
    val requested: Int,
    val available: Int
) : Exception("Only $available of $medicineName left, $requested requested")

class ReservationRepository(private val db: FirebaseFirestore) {

    suspend fun forPatient(patientUid: String): List<Reservation> =
        db.collection(FirestorePaths.RESERVATIONS)
            .whereEqualTo("patientUid", patientUid)
            .orderBy("createdAtMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await().documents.map { it.toReservation() }

    suspend fun forPharmacy(pharmacyId: String): List<Reservation> =
        db.collection(FirestorePaths.RESERVATIONS)
            .whereEqualTo("pharmacyId", pharmacyId)
            .orderBy("createdAtMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await().documents.map { it.toReservation() }

    suspend fun byId(reservationId: String): Reservation? =
        runCatching {
            db.collection(FirestorePaths.RESERVATIONS).document(reservationId).get().await()
        }.getOrNull()?.takeIf { it.exists() }?.toReservation()

    suspend fun byReference(reference: String): Reservation? =
        runCatching {
            db.collection(FirestorePaths.RESERVATIONS)
                .whereEqualTo("reference", reference).get().await()
        }.getOrNull()?.documents?.firstOrNull()?.toReservation()

    /**
     * The pharmacist's live queue. A listener rather than a poll so a hold placed on a
     * patient's phone reaches the counter console while the pharmacist is looking at it.
     */
    fun observeForPharmacy(pharmacyId: String): Flow<List<Reservation>> =
        observe(queueQuery().whereEqualTo("pharmacyId", pharmacyId))

    /** The same feed for one patient, so a status the branch writes reaches their vault live. */
    fun observeForPatient(patientUid: String): Flow<List<Reservation>> =
        observe(queueQuery().whereEqualTo("patientUid", patientUid))

    private fun queueQuery() = db.collection(FirestorePaths.RESERVATIONS)

    private fun observe(query: Query): Flow<List<Reservation>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(
                    snapshot.documents
                        .map { it.toReservation() }
                        .sortedByDescending { it.createdAtMillis }
                )
            }
        }
        awaitClose { registration.remove() }
    }

    /** Gives a cancelled hold back to the queue. The shelf itself is the pharmacist's to adjust. */
    suspend fun cancel(reservationId: String, reason: String = "cancelled"): Result<Unit> = runCatching {
        val doc = db.collection(FirestorePaths.RESERVATIONS).document(reservationId)
        db.runTransaction { transaction ->
            val reservation = transaction.get(doc).toReservation()
            require(reservation.status == ReservationStatus.RESERVED) {
                "This reservation is already ${reservation.status.name.lowercase()}."
            }
            transaction.update(
                doc,
                mapOf(
                    "status" to ReservationStatus.CANCELLED.name,
                    "cancelReason" to reason
                )
            )
        }.await()
    }

    private fun stockRef(pharmacyId: String, medicineId: String) =
        db.collection(FirestorePaths.PHARMACIES).document(pharmacyId)
            .collection(FirestorePaths.STOCK).document(medicineId)

    suspend fun setStatus(reservationId: String, status: ReservationStatus): Result<Unit> =
        runCatching {
            val fields = mutableMapOf<String, Any>("status" to status.name)
            // When the branch handed the bag over is the only moment the counter can report,
            // and the dashboard's "fulfilled today" needs it: createdAt would miss a hold
            // placed yesterday and collected this morning.
            if (status == ReservationStatus.COLLECTED) fields["completedAtMillis"] = System.currentTimeMillis()
            db.collection(FirestorePaths.RESERVATIONS).document(reservationId)
                .update(fields).await()
        }

    /**
     * Places a hold for a whole basket.
     *
     * The hold IS the reservation document: patients are not allowed to write pharmacy
     * stock under the deployed rules, and only the branch should own its shelf count.
     * The shelf is read once here so a patient is told about a shortfall before they
     * commit instead of after the pharmacist opens the queue.
     */
    suspend fun reserve(
        patientUid: String,
        patientName: String,
        pharmacyId: String,
        pharmacyName: String,
        areaLabel: String,
        items: List<ReservationItem>,
        prescriptionStoragePath: String?,
        dataStatus: String = "DEMO"
    ): Result<Reservation> = runCatching {
        require(items.isNotEmpty()) { "Nothing to reserve." }

        val shelf = items.map { item ->
            item to runCatching { stockRef(pharmacyId, item.medicineId).get().await() }
                .getOrNull()
                ?.takeIf { it.exists() }
        }
        shelf.forEach { (item, snapshot) ->
            val available = (snapshot?.getLong("stockQuantity") ?: 0L).toInt()
            if (available < item.quantity) {
                throw InsufficientStockException(item.name, item.quantity, available)
            }
        }

        val now = System.currentTimeMillis()
        val nonce = Random.nextInt(1000, 9999)
        var total = 0.0
        val priced = shelf.map { (item, snapshot) ->
            val price = (snapshot?.getDouble("unitPriceLKR") ?: item.unitPriceLKR)
            total += price * item.quantity
            item.copy(unitPriceLKR = price)
        }

        val reservation = Reservation(
            reservationId = "res_${now}_$nonce",
            reference = referenceFor(now, nonce),
            patientUid = patientUid,
            patientName = patientName,
            pharmacyId = pharmacyId,
            pharmacyName = pharmacyName,
            areaLabel = areaLabel,
            items = priced,
            totalLKR = total,
            prescriptionStoragePath = prescriptionStoragePath,
            prescriptionAttached = prescriptionStoragePath != null,
            status = ReservationStatus.RESERVED,
            counterCode = counterCodeFor(now),
            createdAtMillis = now,
            expiresAtMillis = now + HOLD_MILLIS,
            dataStatus = dataStatus
        )
        db.collection(FirestorePaths.RESERVATIONS).document(reservation.reservationId)
            .set(reservation.toMap()).await()
        reservation
    }

    companion object {
        private const val HOLD_MILLIS = 24L * 60 * 60 * 1000

        private fun referenceFor(millis: Long, nonce: Int): String =
            "MF-" + millis.toString().takeLast(6) + nonce

        private fun counterCodeFor(millis: Long): String =
            Random.nextInt(100000, 999999).toString()
    }
}
