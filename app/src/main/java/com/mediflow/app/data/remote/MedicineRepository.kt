package com.mediflow.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.mapper.toMedicine
import com.mediflow.app.data.model.Medicine
import kotlinx.coroutines.tasks.await

class MedicineRepository(private val db: FirebaseFirestore) {

    /** Over-fetched, then trimmed in memory so no range sentinel is needed. */
    suspend fun search(query: String, limit: Long = 20): List<Medicine> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        return db.collection(FirestorePaths.MEDICINES)
            .whereGreaterThanOrEqualTo("searchKey", key)
            .orderBy("searchKey")
            .limit(SCAN_WINDOW)
            .get()
            .await()
            .documents
            .map { it.toMedicine() }
            .filter { it.searchKey.startsWith(key) }
            .take(limit.toInt())
    }

    suspend fun all(): List<Medicine> =
        db.collection(FirestorePaths.MEDICINES)
            .orderBy("name")
            .get()
            .await()
            .documents
            .map { it.toMedicine() }

    suspend fun byId(medicineId: String): Medicine? =
        runCatching {
            db.collection(FirestorePaths.MEDICINES).document(medicineId).get().await()
        }.getOrNull()?.takeIf { it.exists() }?.toMedicine()

    /**
     * Every strength/form the catalogue holds for one molecule. The search screen offers
     * these as the variant picker, so a patient lands on a real medicineId rather than a
     * label string.
     */
    suspend fun variantsOf(genericName: String): List<Medicine> =
        db.collection(FirestorePaths.MEDICINES)
            .whereEqualTo("genericName", genericName)
            .get().await().documents
            .map { it.toMedicine() }
            .sortedBy { it.strength }

    suspend fun save(medicine: Medicine): Result<Unit> = runCatching {
        db.collection(FirestorePaths.MEDICINES)
            .document(medicine.medicineId)
            .set(medicine.toMap())
            .await()
    }

    companion object {
        private const val SCAN_WINDOW = 60L
    }
}