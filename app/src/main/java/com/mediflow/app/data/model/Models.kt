package com.mediflow.app.data.model

/** A medicine in the shared catalogue, independent of any pharmacy. */
data class Medicine(
    val medicineId: String,
    val name: String,
    val genericName: String,
    val form: String,
    val strength: String,
    val packSize: String,
    val medicineCategory: String,
    val therapeuticClass: String,
    val essentialMedicineLevel: String,
    val requiresPrescription: Boolean,
    val searchTerms: List<String>,
    val dataStatus: String,
    /** Marketing authorisation holder, as entered by the admin. Blank on catalogue rows predating it. */
    val manufacturer: String = "",
    val nmraApprovalNumber: String = ""
) {
    val title: String get() = if (strength.isBlank()) name else "$name $strength"
    val caption: String get() = listOf(form, packSize).filter { it.isNotBlank() }.joinToString(" · ")

    /** Written to Firestore as the prefix-search field. */
    val searchKey: String get() = name.lowercase()
}

/** A pharmacy branch that patients can search and reserve against. */
data class PharmacyInfo(
    val pharmacyId: String,
    val name: String,
    val address: String,
    val phone: String,
    val licenceNumber: String,
    val province: String,
    val district: String,
    val areaLabel: String,
    val latitude: Double,
    val longitude: Double,
    val openingHours: String,
    val deliveryFeeLKR: Int,
    val minOrderLKR: Int,
    val pharmacistName: String?,
    val ownerUid: String?,
    val dataStatus: String,
    /** `approved` pharmacies are the only ones patients see; admin sets the rest. */
    val status: String = "approved",
    val email: String? = null,
    val slmcPin: String? = null
)

/** One medicine held by one pharmacy. Document id is the medicineId. */
data class StockItem(
    val pharmacyId: String,
    val medicineId: String,
    val name: String,
    val strength: String,
    val form: String,
    val packSize: String,
    val batchNo: String,
    val expiryDate: String,
    val stockQuantity: Int,
    val unitPriceLKR: Double,
    val lowStockThreshold: Int,
    /**
     * The branch's patient-facing switch. Turning it off hides the line from search without
     * touching [stockQuantity], so a pharmacist can mark something unavailable mid-count
     * and the shelf total survives. Rows written before this field existed read as available.
     */
    val available: Boolean = true
) {
    val title: String get() = if (strength.isBlank()) name else "$name $strength"
    val isLowStock: Boolean get() = stockQuantity in 1 until lowStockThreshold
    val isOutOfStock: Boolean get() = !available || stockQuantity <= 0
}

/** A pharmacy offered to the patient for one searched medicine. */
data class StockOffer(
    val pharmacy: PharmacyInfo,
    val stock: StockItem?
) {
    val available: Boolean get() = stock != null && !stock.isOutOfStock
}

enum class ReservationStatus { RESERVED, READY, COLLECTED, CANCELLED, EXPIRED }

data class ReservationItem(
    val medicineId: String,
    val name: String,
    val strength: String,
    val form: String,
    val quantity: Int,
    val unitPriceLKR: Double
) {
    val lineTotalLKR: Double get() = quantity * unitPriceLKR
    val title: String get() = if (strength.isBlank()) name else "$name $strength"
}

data class Reservation(
    val reservationId: String,
    val reference: String,
    val patientUid: String,
    val patientName: String,
    val pharmacyId: String,
    val pharmacyName: String,
    val areaLabel: String,
    val items: List<ReservationItem>,
    val totalLKR: Double,
    val prescriptionStoragePath: String?,
    val prescriptionAttached: Boolean,
    val status: ReservationStatus,
    val counterCode: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val dataStatus: String,
    /** Set when the branch hands the bag over; the dashboard counts today's collections on it. */
    val completedAtMillis: Long? = null,
    /** Set by whoever rejected the hold; shown to the patient next to "Cancelled". */
    val cancelReason: String? = null
) {
    val medicineLabels: List<String> get() = items.map { it.title }

    /** A still-RESERVED hold past its expiry is shown as EXPIRED without a writer flipping it. */
    fun displayStatus(now: Long = System.currentTimeMillis()): ReservationStatus =
        if (status == ReservationStatus.RESERVED && now > expiresAtMillis) ReservationStatus.EXPIRED else status

    val isActive: Boolean get() = displayStatus() == ReservationStatus.RESERVED
}

/** One inbox row for a patient, or one order alert for a pharmacy. */
data class AppNotification(
    val id: String,
    val patientUid: String?,
    val pharmacyId: String?,
    val type: NotificationType,
    val title: String,
    val body: String,
    val reservationId: String?,
    val createdAtMillis: Long,
    val readAtMillis: Long?
) {
    val isRead: Boolean get() = readAtMillis != null
}

enum class NotificationType(val docValue: String) {
    RESERVATION_CONFIRMED("reservation_confirmed"),
    RESERVATION_READY("reservation_ready"),
    RESERVATION_EXPIRED("reservation_expired"),
    RESERVATION_CANCELLED("reservation_cancelled"),
    STOCK_SHORTAGE("stock_shortage"),
    ORDER_INCOMING("order_incoming");

    companion object {
        fun fromDoc(value: String): NotificationType =
            entries.firstOrNull { it.docValue == value } ?: RESERVATION_CONFIRMED
    }
}

enum class UserRole { PATIENT, PHARMACIST, ADMIN }

data class UserProfile(
    val uid: String,
    val role: UserRole,
    val name: String,
    val email: String,
    val identifier: String,
    val pharmacyId: String?,
    val status: String,
    val createdAtMillis: Long,
    val address: String = "",
    /** The pharmacist's own contact number. A branch's public number lives on the pharmacy record. */
    val phone: String = "",
    val age: Int = 0,
    val gender: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val chronicConditions: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val notifyReservationUpdates: Boolean = true,
    val notifyNewMedicines: Boolean = false,
    val notifyHealthTips: Boolean = false
)
