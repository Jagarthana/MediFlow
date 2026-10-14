package com.mediflow.app.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mediflow.app.data.auth.AccountProvisioner
import com.mediflow.app.data.auth.AuthRepository
import com.mediflow.app.data.auth.ProvisionException
import com.mediflow.app.data.auth.SyntheticEmail
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.mapper.toMedicine
import com.mediflow.app.data.mapper.toPharmacy
import com.mediflow.app.data.mapper.toReservation
import com.mediflow.app.data.mapper.toStockItem
import com.mediflow.app.data.mapper.toUserProfile
import com.mediflow.app.data.model.Medicine
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.data.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Everything the admin console collects when it registers a branch and its pharmacist. */
data class PharmacyRegistration(
    val name: String,
    val licenceNumber: String,
    val slmcPin: String,
    val province: String,
    val district: String,
    val town: String,
    val addressLine1: String,
    val addressLine2: String,
    val branchPhone: String,
    val branchEmail: String,
    val pharmacistName: String,
    val pharmacistId: String,
    val temporaryPassword: String
)

/** What was actually created, so the console can name the login that now works. */
data class RegisteredBranch(val pharmacyId: String, val pharmacistUid: String, val signInIdentifier: String)

/**
 * The admin's own reads and writes: whole-collection live feeds for the console, the branch
 * registry and the user directory.
 *
 * The deployed rules let an admin read and write every collection, so a whole-collection
 * listener is legal here in a way it is not for a patient or a pharmacist. Registering a branch
 * touches three places — the pharmacist's Auth account, their profile document and the pharmacy
 * document — so it runs in the order that can be undone: account first, then the documents that
 * point at it.
 */
class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val provisioning: AccountProvisioner
) {

    fun observePharmacies(): Flow<List<PharmacyInfo>> =
        observe(db.collection(FirestorePaths.PHARMACIES).orderBy("name")) { it.toPharmacy() }

    fun observeMedicines(): Flow<List<Medicine>> =
        observe(db.collection(FirestorePaths.MEDICINES).orderBy("name")) { it.toMedicine() }

    fun observeUsers(): Flow<List<UserProfile>> =
        observe(db.collection(FirestorePaths.USERS)) { it.toUserProfile() }

    fun observeReservations(): Flow<List<Reservation>> =
        observe(db.collection(FirestorePaths.RESERVATIONS)) { it.toReservation() }

    /**
     * Every branch's shelf in one listener, for the admin's shortage report. A collection
     * group query needs no field index because nothing is filtered or ordered, and the rules
     * already let anyone read a stock line.
     */
    fun observeNetworkStock(): Flow<List<StockItem>> =
        observe(db.collectionGroup(FirestorePaths.STOCK)) { it.toStockItem() }

    private fun <T> observe(
        query: Query,
        parse: (DocumentSnapshot) -> T
    ): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) trySend(snapshot.documents.map(parse))
        }
        awaitClose { registration.remove() }
    }

    /**
     * Creates the Auth account before the documents, because a document with a dangling
     * `ownerUid` outlives the failed call while a half-made account can be deleted again.
     */
    suspend fun registerPharmacy(request: PharmacyRegistration): Result<RegisteredBranch> = runCatching {
        val identifier = request.pharmacistId.trim()
        val email = SyntheticEmail.forPharmacist(identifier)
            ?: throw ProvisionException("A pharmacist ID is 3 to 32 letters, numbers or dashes.")
        if (request.temporaryPassword.length < MIN_PASSWORD) {
            throw ProvisionException(
                "The temporary password needs at least $MIN_PASSWORD characters."
            )
        }
        if (request.name.isBlank()) throw ProvisionException("The branch needs a name.")

        val pharmacyId = freePharmacyId(request.name, request.town)
        val uid = provisioning.createAccount(email, request.temporaryPassword)

        val profile = UserProfile(
            uid = uid,
            role = UserRole.PHARMACIST,
            name = request.pharmacistName.trim().ifBlank { identifier },
            email = email,
            identifier = identifier,
            pharmacyId = pharmacyId,
            status = "active",
            createdAtMillis = System.currentTimeMillis(),
            phone = request.branchPhone.trim()
        )
        val pharmacy = PharmacyInfo(
            pharmacyId = pharmacyId,
            name = request.name.trim(),
            address = listOf(request.addressLine1.trim(), request.addressLine2.trim())
                .filter { it.isNotBlank() }.joinToString(", "),
            phone = request.branchPhone.trim(),
            licenceNumber = request.licenceNumber.trim(),
            province = request.province.trim(),
            district = request.district.trim(),
            areaLabel = request.town.trim(),
            latitude = 0.0,
            longitude = 0.0,
            openingHours = "",
            deliveryFeeLKR = 0,
            minOrderLKR = 0,
            pharmacistName = profile.name,
            ownerUid = uid,
            dataStatus = DATA_STATUS_ADMIN,
            status = STATUS_PENDING,
            email = request.branchEmail.trim().ifBlank { null },
            slmcPin = request.slmcPin.trim().ifBlank { null }
        )

        val profileWrite = auth.writeProfile(profile)
        if (profileWrite.isFailure) {
            rollback(uid, email, request.temporaryPassword, pharmacyId = null)
            throw profileWrite.exceptionOrNull() ?: ProvisionException("The profile could not be saved.")
        }
        val pharmacyWrite = savePharmacy(pharmacy)
        if (pharmacyWrite.isFailure) {
            rollback(uid, email, request.temporaryPassword, pharmacyId = pharmacy.pharmacyId)
            throw pharmacyWrite.exceptionOrNull() ?: ProvisionException("The branch could not be saved.")
        }
        RegisteredBranch(pharmacy.pharmacyId, uid, identifier)
    }

    /** Best-effort undo of a half-finished registration. */
    private suspend fun rollback(uid: String, email: String, password: String, pharmacyId: String?) {
        pharmacyId?.let { id ->
            runCatching { db.collection(FirestorePaths.PHARMACIES).document(id).delete().await() }
        }
        runCatching { db.collection(FirestorePaths.USERS).document(uid).delete().await() }
        runCatching { provisioning.deleteAccount(email, password) }
    }

    suspend fun savePharmacy(pharmacy: PharmacyInfo): Result<Unit> = runCatching {
        db.collection(FirestorePaths.PHARMACIES)
            .document(pharmacy.pharmacyId)
            .set(pharmacy.toMap())
            .await()
    }

    /** Approve, hold or reject a branch. Only `approved` branches are visible to patients. */
    suspend fun setPharmacyStatus(pharmacyId: String, status: String): Result<Unit> = runCatching {
        db.collection(FirestorePaths.PHARMACIES).document(pharmacyId)
            .update("status", status).await()
    }

    /** Disables a sign-in without deleting the person's history; `active` turns it back on. */
    suspend fun setUserStatus(uid: String, status: String): Result<Unit> = runCatching {
        db.collection(FirestorePaths.USERS).document(uid).update("status", status).await()
    }

    /** A catalogue row that already carries this brand at this strength, if there is one. */
    suspend fun findMedicine(name: String, strength: String): Medicine? =
        runCatching {
            db.collection(FirestorePaths.MEDICINES).whereEqualTo("name", name).get().await().documents
        }.getOrNull()
            .orEmpty()
            .map { it.toMedicine() }
            .firstOrNull { it.strength.equals(strength, ignoreCase = true) }

    /**
     * Adds a row to the master catalogue. The id is a readable slug of name, strength and form —
     * the shape the seeded catalogue already uses — with a counter if that slug is taken.
     */
    suspend fun registerMedicine(medicine: Medicine): Result<Medicine> = runCatching {
        val candidate = medicine.copy(medicineId = freeMedicineId(medicine))
        db.collection(FirestorePaths.MEDICINES)
            .document(candidate.medicineId)
            .set(candidate.toMap())
            .await()
        candidate
    }

    private suspend fun freeMedicineId(medicine: Medicine): String {
        val base = listOf(medicine.name, medicine.strength, medicine.form)
            .joinToString("-") { it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(60)
            .ifBlank { "medicine" }

        var candidate = base
        var suffix = 2
        val collection = db.collection(FirestorePaths.MEDICINES)
        while (collection.document(candidate).get().await().exists()) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }

    /**
     * A readable document id from the branch name and its town, with a numeric suffix when that
     * id is already taken, so the registry stays legible instead of turning into random ids.
     */
    private suspend fun freePharmacyId(name: String, town: String): String {
        val words = name.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.length > 2 }
        val base = buildList {
            add(words.firstOrNull() ?: "BRANCH")
            words.getOrNull(1)?.let { add(it) }
            val townSlug = town.uppercase().replace(Regex("[^A-Z0-9]"), "").take(6)
            if (townSlug.isNotBlank()) add(townSlug)
        }.distinct().joinToString("-").take(24).trim('-')

        var candidate = base.ifBlank { "BRANCH" }
        var suffix = 2
        val collection = db.collection(FirestorePaths.PHARMACIES)
        while (collection.document(candidate).get().await().exists()) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }

    companion object {
        const val MIN_PASSWORD = 6
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        private const val DATA_STATUS_ADMIN = "ADMIN-ENTERED"
    }
}
