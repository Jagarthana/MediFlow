package com.mediflow.app.data.remote

object FirestorePaths {
    const val PHARMACIES = "pharmacies"
    const val MEDICINES = "medicines"
    const val USERS = "users"
    const val LOGINS = "logins"
    const val RESERVATIONS = "reservations"
    const val NOTIFICATIONS = "notifications"

    const val STOCK = "stock"

    fun pharmacy(pharmacyId: String) = "$PHARMACIES/$pharmacyId"
    fun stock(pharmacyId: String, medicineId: String) =
        "$PHARMACIES/$pharmacyId/$STOCK/$medicineId"
}
