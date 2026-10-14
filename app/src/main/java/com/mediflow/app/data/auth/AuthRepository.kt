package com.mediflow.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.SignInMethodQueryResult
import com.google.firebase.firestore.FirebaseFirestore
import com.mediflow.app.data.mapper.toMap
import com.mediflow.app.data.mapper.toUserProfile
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.data.model.UserRole
import com.mediflow.app.data.remote.FirestorePaths
import kotlinx.coroutines.tasks.await

sealed class AuthOutcome {
    data class SignedIn(val profile: UserProfile) : AuthOutcome()
    data class NeedsAccount(val message: String) : AuthOutcome()
    data class Failed(val message: String) : AuthOutcome()
}

sealed class ResetOutcome {
    /** A genuine Firebase reset link was queued for a reachable inbox. */
    data class EmailSent(val email: String) : ResetOutcome()

    /** The account is addressed by a synthetic mail box, so only the admin can set a new password. */
    data class AdminResetRequired(val identifier: String) : ResetOutcome()

    data class Failed(val message: String) : ResetOutcome()
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    val currentFirebaseUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signIn(role: UserRole, identifier: String, password: String): AuthOutcome {
        val email = resolveEmail(role, identifier)
            ?: return AuthOutcome.Failed(invalidIdentifierMessage(role))

        val result = runCatching {
            auth.signInWithEmailAndPassword(email, password).await()
        }

        val credentialError = result.exceptionOrNull()
        if (credentialError != null) {
            return AuthOutcome.Failed(
                when (credentialError) {
                    is FirebaseAuthInvalidCredentialsException ->
                        "Wrong password, or this ${role.name.lowercase()} account has not been created yet."
                    else -> credentialError.message ?: "Sign in failed."
                }
            )
        }

        val uid = result.getOrNull()?.user?.uid ?: return AuthOutcome.Failed("Sign in failed.")
        val profile = readProfile(uid)
            ?: return AuthOutcome.Failed("This account has no profile document yet. Run the demo seeder.")
        if (profile.status != "active") {
            return AuthOutcome.Failed("This account is disabled. Ask the MediFlow admin to re-enable it.")
        }
        if (profile.role != role) {
            return AuthOutcome.Failed("That account is registered as a ${profile.role.name.lowercase()}.")
        }
        return AuthOutcome.SignedIn(profile)
    }

    suspend fun createPatientProfile(
        uid: String,
        identifier: String,
        name: String
    ): Result<Unit> = writeProfile(
        UserProfile(
            uid = uid,
            role = UserRole.PATIENT,
            name = name.ifBlank { SyntheticEmail.displayIdentifier(identifier) },
            email = SyntheticEmail.forPatient(identifier).orEmpty(),
            identifier = identifier,
            pharmacyId = null,
            status = "active",
            createdAtMillis = System.currentTimeMillis()
        )
    )

    suspend fun signUp(
        role: UserRole,
        identifier: String,
        password: String,
        name: String,
        pharmacyId: String? = null
    ): AuthOutcome {
        val email = SyntheticEmail.forRole(role, identifier)
            ?: return AuthOutcome.Failed(invalidIdentifierMessage(role))

        val result = runCatching { auth.createUserWithEmailAndPassword(email, password).await() }
        val error = result.exceptionOrNull()
        if (error != null) {
            return AuthOutcome.Failed(
                when (error) {
                    is FirebaseAuthWeakPasswordException -> "Use at least 6 characters."
                    is FirebaseAuthUserCollisionException ->
                        "That ${role.name.lowercase()} identifier is already registered."
                    else -> error.message ?: "Could not create the account."
                }
            )
        }

        val uid = result.getOrThrow().user?.uid ?: return AuthOutcome.Failed("Could not create the account.")
        writeProfile(
            UserProfile(
                uid = uid,
                role = role,
                name = name.ifBlank { identifier },
                email = email,
                identifier = identifier,
                pharmacyId = pharmacyId,
                status = "active",
                createdAtMillis = System.currentTimeMillis()
            )
        ).onFailure { return AuthOutcome.Failed(it.message ?: "Account created but its profile could not be saved.") }
        registerLoginAlias(role, identifier, uid, email)

        val profile = UserProfile(
            uid = uid, role = role, name = name.ifBlank { identifier }, email = email,
            identifier = identifier, pharmacyId = pharmacyId, status = "active",
            createdAtMillis = System.currentTimeMillis()
        )
        return AuthOutcome.SignedIn(profile)
    }

    /**
     * The sign-in directory: an identifier can only be turned into one synthetic email by
     * [SyntheticEmail.forRole], so every extra identifier of the same account is recorded
     * here and read back by [resolveEmail]. Losing this write is not fatal — the
     * deterministic mapping still works for the identifier the account was created with.
     */
    private suspend fun registerLoginAlias(role: UserRole, identifier: String, uid: String, email: String) {
        val key = SyntheticEmail.loginDocId(role, identifier) ?: return
        runCatching {
            db.collection(FirestorePaths.LOGINS).document(key)
                .set(mapOf("uid" to uid, "email" to email, "role" to role.name.lowercase()))
                .await()
        }
    }

    private suspend fun resolveEmail(role: UserRole, identifier: String): String? {
        val deterministic = SyntheticEmail.forRole(role, identifier) ?: return null
        val key = SyntheticEmail.loginDocId(role, identifier) ?: return deterministic
        val aliased = runCatching {
            db.collection(FirestorePaths.LOGINS).document(key).get().await().getString("email")
        }.getOrNull()
        return aliased ?: deterministic
    }

    /**
     * Patients and the pharmacist sign in with a phone, NIC or pharmacy ID, which
     * Firebase addresses as a synthetic mail box that no mail server can deliver
     * to. Sending a link there would look like a success and never arrive, so the
     * outcome says who has to reset the account instead.
     */
    suspend fun sendPasswordReset(identifier: String): ResetOutcome {
        val typed = identifier.trim()
        if (typed.isBlank()) return ResetOutcome.Failed("Enter your mobile number, NIC, pharmacy ID or email.")

        if (!typed.contains('@')) {
            val mapped = resolveEmail(UserRole.PATIENT, typed) ?: resolveEmail(UserRole.PHARMACIST, typed)
                ?: return ResetOutcome.Failed("That does not look like a phone, NIC, pharmacist ID or email.")
            return if (accountExists(mapped)) ResetOutcome.AdminResetRequired(typed)
            else ResetOutcome.Failed("No account is registered for $typed.")
        }

        val result = runCatching { auth.sendPasswordResetEmail(typed).await() }
        return when (result.exceptionOrNull()) {
            null -> ResetOutcome.EmailSent(typed)
            is FirebaseAuthInvalidCredentialsException ->
                ResetOutcome.Failed("No MediFlow account uses $typed.")
            else -> ResetOutcome.Failed(result.exceptionOrNull()?.message ?: "Could not send the reset email.")
        }
    }

    /** Checks the address without signing in, so the reset screen can be truthful. */
    @Suppress("DEPRECATION")
    private suspend fun accountExists(email: String): Boolean {
        val task: com.google.android.gms.tasks.Task<SignInMethodQueryResult> =
            auth.fetchSignInMethodsForEmail(email)
        return runCatching { task.await().signInMethods?.isNotEmpty() == true }.getOrDefault(false)
    }

    suspend fun readProfile(uid: String): UserProfile? =
        runCatching { db.collection(FirestorePaths.USERS).document(uid).get().await() }
            .getOrNull()?.takeIf { it.exists() }?.toUserProfile()

    suspend fun writeProfile(profile: UserProfile): Result<Unit> = runCatching {
        db.collection(FirestorePaths.USERS).document(profile.uid).set(profile.toMap()).await()
    }

    fun signOut() = auth.signOut()

    private fun invalidIdentifierMessage(role: UserRole) = when (role) {
        UserRole.PATIENT -> "Enter your mobile number or your NIC (10 or 12 digits)."
        UserRole.PHARMACIST -> "Enter your pharmacist ID, for example 0100."
        UserRole.ADMIN -> "Enter the admin email address."
    }
}
