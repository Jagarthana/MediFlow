package com.mediflow.app.data.seed

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mediflow.app.data.auth.SyntheticEmail
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.data.model.UserRole
import com.mediflow.app.data.remote.FirestorePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SeedException(message: String) : Exception(message)

data class SeedResult(
    val medicinesWritten: Int,
    val stockWritten: Int,
    val accountsCreated: List<String>,
    val accountsAlreadyPresent: List<String>,
    val accountsRemoved: List<String>,
    val aliasesWritten: List<String>
)

private data class Account(val uid: String, val justCreated: Boolean)

/**
 * Debug-only. Writes the workbook-backed catalogue and the stated demo accounts with
 * fixed document ids, so a second run updates the same documents instead of duplicating
 * them. Accounts from an earlier seed run listed in `replaced` are removed first.
 *
 * The admin session is opened first and restored after every account, because the
 * deployed rules only let an admin write catalogue, pharmacy and `logins` documents.
 * A signed-in user may always write their own `users/{uid}` profile — and delete their
 * own Auth account, which is the only way an account can be removed from a phone.
 */
class FirestoreSeeder(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private var adminEmail = ""
    private var adminPassword = ""
    private var lastEnsureError: String? = null

    suspend fun run(email: String, password: String): SeedResult = withContext(Dispatchers.IO) {
        adminEmail = email.trim()
        adminPassword = password
        if (!adminEmail.contains('@')) throw SeedException("Enter the admin email address.")
        if (adminPassword.length < 6) throw SeedException("Admin password needs at least 6 characters.")

        val catalog = SeedCatalogLoader.load(context)
        val created = mutableListOf<String>()
        val present = mutableListOf<String>()
        val aliases = mutableListOf<String>()

        val admin = ensureAccount(adminEmail, adminPassword)
            ?: throw SeedException(
                "Could not create or open $adminEmail. Firebase said: ${lastEnsureError ?: "no reason given"} — " +
                        "if that mentions an invalid auth method, enable the Email/Password sign-in provider " +
                        "for project mediflow-d4ccc in the Firebase console."
            )
        writeProfile(UserProfile(admin.uid, UserRole.ADMIN, "Admin", adminEmail, adminEmail, null, "active", System.currentTimeMillis()))
        record(admin, adminEmail, created, present)

        val removed = removeReplacedAccounts(catalog.replaced)
        signInAsAdmin()

        writeCatalog(catalog)

        catalog.pharmacists.forEach { pharmacist ->
            val account = createAccount(
                role = UserRole.PHARMACIST,
                identifier = pharmacist.identifier,
                password = pharmacist.password
            )
            signInAsAdmin()
            writeProfile(
                UserProfile(
                    account.uid, UserRole.PHARMACIST, pharmacist.name,
                    SyntheticEmail.forPharmacist(pharmacist.identifier).orEmpty(),
                    pharmacist.identifier, pharmacist.pharmacyId, "active", System.currentTimeMillis()
                )
            )
            linkOwner(pharmacist.pharmacyId, account.uid)
            record(account, pharmacist.identifier, created, present)
        }

        catalog.patients.forEach { patient ->
            val account = createAccount(
                role = UserRole.PATIENT,
                identifier = patient.identifier,
                password = patient.password
            )
            signInAsAdmin()
            val email = SyntheticEmail.forPatient(patient.identifier).orEmpty()
            writeProfile(
                UserProfile(
                    uid = account.uid, role = UserRole.PATIENT, name = patient.name, email = email,
                    identifier = patient.identifier, pharmacyId = null, status = "active",
                    createdAtMillis = System.currentTimeMillis(),
                    age = patient.age, gender = patient.gender, bloodGroup = patient.bloodGroup
                )
            )
            record(account, patient.identifier, created, present)

            patient.identifiers
                .filter { SyntheticEmail.forPatient(it) != email }
                .forEach { key ->
                    writeAlias(UserRole.PATIENT, key, account.uid, email)
                    aliases += key
                }
        }

        auth.signOut()
        SeedResult(
            medicinesWritten = catalog.medicines.size,
            stockWritten = catalog.stock.size,
            accountsCreated = created,
            accountsAlreadyPresent = present,
            accountsRemoved = removed,
            aliasesWritten = aliases
        )
    }

    /** Signs in as each superseded account to delete it, then clears its documents as admin. */
    private suspend fun removeReplacedAccounts(replaced: SeedReplaced): List<String> {
        val removed = mutableListOf<String>()
        val legacy = listOf(
            UserRole.PATIENT to replaced.patients,
            UserRole.PHARMACIST to replaced.pharmacists
        )
        legacy.forEach { (role, identifiers) ->
            identifiers.forEach { identifier ->
                val email = SyntheticEmail.forRole(role, identifier) ?: return@forEach
                val uid = runCatching { auth.signInWithEmailAndPassword(email, replaced.password).await().user?.uid }
                    .getOrNull()
                if (uid != null) {
                    runCatching { auth.currentUser?.delete()?.await() }
                        .onSuccess { removed += identifier }
                    signInAsAdmin()
                    runCatching { db.collection(FirestorePaths.USERS).document(uid).delete().await() }
                    SyntheticEmail.loginDocId(role, identifier)?.let { key ->
                        runCatching { db.collection(FirestorePaths.LOGINS).document(key).delete().await() }
                    }
                }
            }
        }
        return removed
    }

    private suspend fun createAccount(role: UserRole, identifier: String, password: String): Account {
        val email = SyntheticEmail.forRole(role, identifier)
            ?: throw SeedException("$identifier is not a valid ${role.name.lowercase()} sign-in identifier.")
        return ensureAccount(email, password)
            ?: throw SeedException(
                "Could not create or open the $role account for $identifier. " +
                        "Firebase said: ${lastEnsureError ?: "no reason given"}"
            )
    }

    private suspend fun writeAlias(role: UserRole, identifier: String, uid: String, email: String) {
        val key = SyntheticEmail.loginDocId(role, identifier) ?: return
        db.collection(FirestorePaths.LOGINS).document(key)
            .set(mapOf("uid" to uid, "email" to email, "role" to role.name.lowercase()))
            .await()
    }

    private suspend fun signInAsAdmin() {
        runCatching { auth.signInWithEmailAndPassword(adminEmail, adminPassword).await() }
    }

    private fun record(account: Account, label: String, created: MutableList<String>, present: MutableList<String>) {
        if (account.justCreated) created += label else present += label
    }

    private suspend fun writeCatalog(catalog: SeedCatalog) {
        val batch = db.batch()
        catalog.pharmacies.forEach { pharmacy ->
            batch.set(db.collection(FirestorePaths.PHARMACIES).document(pharmacy.pharmacyId), pharmacy.toMap())
        }
        catalog.medicines.forEach { medicine ->
            batch.set(db.collection(FirestorePaths.MEDICINES).document(medicine.medicineId), medicine.toMap())
        }
        catalog.stock.forEach { item ->
            batch.set(
                db.collection(FirestorePaths.PHARMACIES).document(item.pharmacyId)
                    .collection(FirestorePaths.STOCK).document(item.medicineId),
                item.toStock().toMap()
            )
        }
        batch.commit().await()
    }

    private suspend fun writeProfile(profile: UserProfile) {
        db.collection(FirestorePaths.USERS).document(profile.uid).set(profile.toMap()).await()
    }

    private suspend fun linkOwner(pharmacyId: String, uid: String) {
        db.collection(FirestorePaths.PHARMACIES).document(pharmacyId)
            .update("ownerUid", uid).await()
    }

    private suspend fun ensureAccount(email: String, password: String): Account? {
        lastEnsureError = null
        val signIn = runCatching { auth.signInWithEmailAndPassword(email, password).await().user?.uid }
        signIn.exceptionOrNull()?.let { lastEnsureError = it.message }
        signIn.getOrNull()?.let { return Account(it, justCreated = false) }

        val create = runCatching { auth.createUserWithEmailAndPassword(email, password).await().user?.uid }
        create.exceptionOrNull()?.let { lastEnsureError = it.message }
        return create.getOrNull()?.let { Account(it, justCreated = true) }
    }

    private fun SeedStock.toStock() = StockItem(
        pharmacyId = pharmacyId,
        medicineId = medicineId,
        name = name,
        strength = strength,
        form = form,
        packSize = packSize,
        batchNo = batchNo,
        expiryDate = expiryDate,
        stockQuantity = stockQuantity,
        unitPriceLKR = unitPriceLKR,
        lowStockThreshold = LOW_STOCK
    )

    companion object {
        const val LOW_STOCK = 20
        val DEFAULT_ADMIN_EMAIL = "admin@gmail.com"
        val DEFAULT_ADMIN_PASSWORD = "mediflow"
    }
}
