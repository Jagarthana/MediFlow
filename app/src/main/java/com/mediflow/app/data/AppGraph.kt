package com.mediflow.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.mediflow.app.data.auth.AccountProvisioner
import com.mediflow.app.data.auth.AuthRepository
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.data.remote.AdminRepository
import com.mediflow.app.data.remote.MedicineRepository
import com.mediflow.app.data.remote.NotificationRepository
import com.mediflow.app.data.remote.PharmacyRepository
import com.mediflow.app.data.remote.ReservationRepository
import com.mediflow.app.data.remote.SlipStorageRepository

/** Hand-rolled dependency graph; the app is small enough not to need Hilt. */
object AppGraph {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    val auth: AuthRepository by lazy { AuthRepository(firebaseAuth, firestore) }
    val provisioning: AccountProvisioner by lazy { AccountProvisioner() }
    val admin: AdminRepository by lazy { AdminRepository(firestore, auth, provisioning) }
    val medicines: MedicineRepository by lazy { MedicineRepository(firestore) }
    val pharmacies: PharmacyRepository by lazy { PharmacyRepository(firestore) }
    val reservations: ReservationRepository by lazy { ReservationRepository(firestore) }
    val notifications: NotificationRepository by lazy { NotificationRepository(firestore) }
    val slips: SlipStorageRepository by lazy { SlipStorageRepository(storage) }

    /** Signed-in profile, kept in memory for the lifetime of the process. */
    var currentProfile: UserProfile? = null
        private set

    fun rememberProfile(profile: UserProfile?) {
        currentProfile = profile
    }

    fun signOut() {
        currentProfile = null
        CartStore.clear()
        auth.signOut()
    }
}
