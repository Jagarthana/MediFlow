package com.mediflow.app.data.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.tasks.await

/** Why an account could not be opened or undone, in words the admin console can show directly. */
class ProvisionException(message: String) : Exception(message)

/**
 * Firebase Auth holds exactly one signed-in user per `FirebaseApp`, so creating an account on the
 * default instance would sign the admin out halfway through registering a pharmacy.
 *
 * A second, named `FirebaseApp` shares the same project and credentials but keeps its own session,
 * which is what a provisioning path needs: the work happens there and the instance is dropped
 * again, while the admin stays signed in on the default instance and their Firestore writes keep
 * their admin claims.
 */
class AccountProvisioner {

    suspend fun createAccount(email: String, password: String): String =
        withProvisionedSession { auth ->
            val result = runCatching { auth.createUserWithEmailAndPassword(email, password).await() }
            val error = result.exceptionOrNull()
            if (error != null) {
                throw ProvisionException(
                    when (error) {
                        is FirebaseAuthUserCollisionException ->
                            "That pharmacist ID is already registered as $email."
                        else -> error.message ?: "Firebase refused to create the account."
                    }
                )
            }
            result.getOrThrow().user?.uid
                ?: throw ProvisionException("Firebase created no account.")
        }

    /** Signs in as the account first, because Firebase only lets a session delete its own user. */
    suspend fun deleteAccount(email: String, password: String) {
        withProvisionedSession { auth ->
            val session = runCatching { auth.signInWithEmailAndPassword(email, password).await() }
            session.exceptionOrNull()?.let {
                throw ProvisionException("Could not open the new account to undo it: ${it.message}")
            }
            session.getOrThrow().user?.delete()?.await()
        }
    }

    private suspend fun <T> withProvisionedSession(
        block: suspend (FirebaseAuth) -> T
    ): T {
        val defaultApp = FirebaseApp.getInstance()
        val context = defaultApp.applicationContext
        val app = FirebaseApp.getApps(context).find { it.name == PROVISIONING_APP }
            ?: FirebaseApp.initializeApp(context, defaultApp.options, PROVISIONING_APP)
            ?: throw ProvisionException("Could not open a provisioning session with Firebase.")
        return try {
            val auth = FirebaseAuth.getInstance(app)
            block(auth).also { auth.signOut() }
        } finally {
            // Frees the name for the next registration. The delete is asynchronous, so a call
            // made before it settles reuses the live instance found through getApps().
            runCatching { app.delete() }
        }
    }

    companion object {
        private const val PROVISIONING_APP = "mediflow-provisioning"
    }
}
