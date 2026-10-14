package com.mediflow.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.mapper.toPharmacy
import com.mediflow.app.data.mapper.toStockItem
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.ReservationItem
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.data.model.StockOffer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PharmacyRepository(private val db: FirebaseFirestore) {

    suspend fun all(): List<PharmacyInfo> =
        db.collection(FirestorePaths.PHARMACIES)
            .orderBy("name")
            .get()
            .await()
            .documents
            .map { it.toPharmacy() }

    suspend fun byId(pharmacyId: String): PharmacyInfo? =
        runCatching {
            db.collection(FirestorePaths.PHARMACIES).document(pharmacyId).get().await()
        }.getOrNull()?.takeIf { it.exists() }?.toPharmacy()

    suspend fun stockOf(pharmacyId: String): List<StockItem> =
        db.collection(FirestorePaths.PHARMACIES).document(pharmacyId)
            .collection(FirestorePaths.STOCK)
            .get().await().documents.map { it.toStockItem() }

    suspend fun stockOf(pharmacyId: String, medicineId: String): StockItem? =
        runCatching { stockRef(pharmacyId, medicineId).get().await() }
            .getOrNull()?.takeIf { it.exists() }?.toStockItem()

    /** Live shelf for one branch, newest name first so the console list is stable. */
    fun observeStockOf(pharmacyId: String): Flow<List<StockItem>> = callbackFlow {
        val registration = db.collection(FirestorePaths.PHARMACIES).document(pharmacyId)
            .collection(FirestorePaths.STOCK)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(
                        snapshot.documents
                            .map { it.toStockItem() }
                            .sortedWith(compareBy({ it.name.lowercase() }, { it.strength.lowercase() }))
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun inDistrict(district: String): List<PharmacyInfo> =
        all().filter { it.district.equals(district, ignoreCase = true) }

    suspend fun areas(): List<String> = approved().flatMap { listOf(it.areaLabel, it.district) }
        .filter { it.isNotBlank() }.distinct().sorted()

    /**
     * Only admin-approved branches reach a patient. An unapproved branch is invisible here
     * but still fully usable by its pharmacist.
     */
    suspend fun approved(): List<PharmacyInfo> = all().filter { it.status == "approved" }

    /** Matches the town, district or area label the patient picked in the dropdown. */
    private fun List<PharmacyInfo>.inArea(area: String?): List<PharmacyInfo> {
        val key = area?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return this
        return filter { pharmacy ->
            listOf(pharmacy.areaLabel, pharmacy.district, pharmacy.province)
                .any { it.lowercase().contains(key) || key.contains(it.lowercase()) }
        }
    }

    /** Every approved pharmacy that could supply this medicine, with its stock row when held. */
    suspend fun offersFor(medicineId: String, area: String? = null): List<StockOffer> =
        approved().inArea(area).map { pharmacy ->
            StockOffer(pharmacy, stockOf(pharmacy.pharmacyId, medicineId))
        }

    /**
     * Offers for a whole basket, reading each pharmacy's stock list once instead of once per
     * medicine. A pharmacy missing any line still appears, with a null stock for that line.
     */
    suspend fun offersFor(medicineIds: List<String>, area: String? = null): Map<String, List<StockOffer>> {
        val pharmacies = approved().inArea(area)
        val stockByPharmacy = pharmacies.associateWith { pharmacy ->
            stockOf(pharmacy.pharmacyId).associateBy { it.medicineId }
        }
        return medicineIds.associateWith { medicineId ->
            pharmacies.map { pharmacy -> StockOffer(pharmacy, stockByPharmacy.getValue(pharmacy)[medicineId]) }
        }
    }

    suspend fun save(pharmacy: PharmacyInfo): Result<Unit> = runCatching {
        db.collection(FirestorePaths.PHARMACIES)
            .document(pharmacy.pharmacyId)
            .set(pharmacy.toMap())
            .await()
    }

    suspend fun saveStock(item: StockItem): Result<Unit> = runCatching {
        stockRef(item.pharmacyId, item.medicineId).set(item.toMap()).await()
    }

    suspend fun deleteStock(pharmacyId: String, medicineId: String): Result<Unit> = runCatching {
        stockRef(pharmacyId, medicineId).delete().await()
    }

    /**
     * The patient-facing switch. Written as a single field so a pharmacist marking a line
     * unavailable mid-count leaves [StockItem.stockQuantity] — and the rest of the row — alone.
     */
    suspend fun setAvailability(pharmacyId: String, medicineId: String, available: Boolean): Result<Unit> =
        runCatching { stockRef(pharmacyId, medicineId).update("available", available).await() }

    /** The whole shelf in as few batches as Firestore allows (500 writes per commit). */
    suspend fun setAvailabilityAll(
        pharmacyId: String,
        medicineIds: List<String>,
        available: Boolean
    ): Result<Unit> = runCatching {
        medicineIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(stockRef(pharmacyId, it), mapOf("available" to available)) }
            batch.commit().await()
        }
    }

    /**
     * Writes the shelf count only. Used by the hand-over decrement, where the rest of the row
     * (batch, expiry, price) is not this call's business.
     */
    suspend fun setQuantity(pharmacyId: String, medicineId: String, quantity: Int): Result<Unit> =
        runCatching {
            stockRef(pharmacyId, medicineId)
                .update("stockQuantity", quantity.coerceAtLeast(0)).await()
        }

    /**
     * Books a bag off the shelf when the branch hands it over.
     *
     * Runs as one transaction because a pharmacist can hand two patients the last strip of the
     * same tablet at the same moment; `coerceAtLeast(0)` keeps a stale shelf reading from
     * writing a negative count the patient search would then choke on.
     */
    suspend fun reduceForPickup(
        pharmacyId: String,
        items: List<ReservationItem>
    ): Result<Unit> = runCatching {
        db.runTransaction { transaction ->
            val reads = items.map { item ->
                item to transaction.get(stockRef(pharmacyId, item.medicineId))
            }
            reads.forEach { (item, snapshot) ->
                if (snapshot.exists()) {
                    val onShelf = (snapshot.getLong("stockQuantity") ?: 0L).toInt()
                    transaction.update(
                        stockRef(pharmacyId, item.medicineId),
                        mapOf("stockQuantity" to (onShelf - item.quantity).coerceAtLeast(0))
                    )
                }
            }
        }.await()
    }

    internal fun stockRef(pharmacyId: String, medicineId: String) =
        db.collection(FirestorePaths.PHARMACIES).document(pharmacyId)
            .collection(FirestorePaths.STOCK).document(medicineId)
}
