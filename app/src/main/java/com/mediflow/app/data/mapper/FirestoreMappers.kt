package com.mediflow.app.data.mapper

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.mediflow.app.data.model.AppNotification
import com.mediflow.app.data.model.Medicine
import com.mediflow.app.data.model.NotificationType
import com.mediflow.app.data.model.PharmacyInfo
import com.mediflow.app.data.model.Reservation
import com.mediflow.app.data.model.ReservationItem
import com.mediflow.app.data.model.ReservationStatus
import com.mediflow.app.data.model.StockItem
import com.mediflow.app.data.model.UserProfile
import com.mediflow.app.data.model.UserRole

private fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()
private fun DocumentSnapshot.strOrNull(field: String): String? = getString(field)
private fun DocumentSnapshot.int(field: String): Int = getLong(field)?.toInt() ?: 0
private fun DocumentSnapshot.long(field: String): Long = getLong(field) ?: 0L
private fun DocumentSnapshot.dbl(field: String): Double {
    val raw = get(field) as? Number
    return raw?.toDouble() ?: 0.0
}
private fun DocumentSnapshot.bool(field: String): Boolean = getBoolean(field) ?: false

private fun DocumentSnapshot.strList(field: String): List<String> {
    val raw = get(field) as? List<*> ?: return emptyList()
    return raw.mapNotNull { it as? String }
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.mapList(field: String): List<Map<String, Any>> =
    get(field) as? List<Map<String, Any>> ?: emptyList()

fun DocumentSnapshot.toMedicine(): Medicine = Medicine(
    medicineId = str("medicineId").ifBlank { id },
    name = str("name"),
    genericName = str("genericName"),
    form = str("form"),
    strength = str("strength"),
    packSize = str("packSize"),
    medicineCategory = str("medicineCategory"),
    therapeuticClass = str("therapeuticClass"),
    essentialMedicineLevel = str("essentialMedicineLevel"),
    requiresPrescription = bool("requiresPrescription"),
    searchTerms = strList("searchTerms"),
    dataStatus = str("dataStatus"),
    manufacturer = str("manufacturer"),
    nmraApprovalNumber = str("nmraApprovalNumber")
)

fun Medicine.toMap(): Map<String, Any> = mapOf(
    "medicineId" to medicineId,
    "name" to name,
    "genericName" to genericName,
    "form" to form,
    "strength" to strength,
    "packSize" to packSize,
    "medicineCategory" to medicineCategory,
    "therapeuticClass" to therapeuticClass,
    "essentialMedicineLevel" to essentialMedicineLevel,
    "requiresPrescription" to requiresPrescription,
    "searchTerms" to searchTerms,
    "searchKey" to searchKey,
    "dataStatus" to dataStatus,
    "manufacturer" to manufacturer,
    "nmraApprovalNumber" to nmraApprovalNumber
)

fun DocumentSnapshot.toPharmacy(): PharmacyInfo = PharmacyInfo(
    pharmacyId = str("pharmacyId").ifBlank { id },
    name = str("name"),
    address = str("address"),
    phone = str("phone"),
    licenceNumber = str("licenceNumber"),
    province = str("province"),
    district = str("district"),
    areaLabel = str("areaLabel"),
    latitude = dbl("latitude"),
    longitude = dbl("longitude"),
    openingHours = str("openingHours"),
    deliveryFeeLKR = int("deliveryFeeLKR"),
    minOrderLKR = int("minOrderLKR"),
    pharmacistName = strOrNull("pharmacistName"),
    ownerUid = strOrNull("ownerUid"),
    dataStatus = str("dataStatus"),
    status = str("status").ifBlank { "approved" },
    email = strOrNull("email"),
    slmcPin = strOrNull("slmcPin")
)

fun PharmacyInfo.toMap(): Map<String, Any?> = mapOf(
    "pharmacyId" to pharmacyId,
    "name" to name,
    "address" to address,
    "phone" to phone,
    "licenceNumber" to licenceNumber,
    "province" to province,
    "district" to district,
    "areaLabel" to areaLabel,
    "latitude" to latitude,
    "longitude" to longitude,
    "openingHours" to openingHours,
    "deliveryFeeLKR" to deliveryFeeLKR,
    "minOrderLKR" to minOrderLKR,
    "pharmacistName" to pharmacistName,
    "ownerUid" to ownerUid,
    "dataStatus" to dataStatus,
    "status" to status,
    "email" to email,
    "slmcPin" to slmcPin
)

fun DocumentSnapshot.toStockItem(): StockItem = StockItem(
    pharmacyId = str("pharmacyId"),
    medicineId = str("medicineId").ifBlank { id },
    name = str("name"),
    strength = str("strength"),
    form = str("form"),
    packSize = str("packSize"),
    batchNo = str("batchNo"),
    expiryDate = str("expiryDate"),
    stockQuantity = int("stockQuantity"),
    unitPriceLKR = dbl("unitPriceLKR"),
    lowStockThreshold = int("lowStockThreshold").ifZero(20),
    available = get("available") as? Boolean ?: true
)

fun StockItem.toMap(): Map<String, Any> = mapOf(
    "pharmacyId" to pharmacyId,
    "medicineId" to medicineId,
    "name" to name,
    "strength" to strength,
    "form" to form,
    "packSize" to packSize,
    "batchNo" to batchNo,
    "expiryDate" to expiryDate,
    "stockQuantity" to stockQuantity,
    "unitPriceLKR" to unitPriceLKR,
    "lowStockThreshold" to lowStockThreshold,
    "available" to available
)

private fun Int.ifZero(fallback: Int): Int = if (this == 0) fallback else this

fun QuerySnapshot.toStockItems(): List<StockItem> = documents.map { it.toStockItem() }

fun DocumentSnapshot.toReservation(): Reservation = Reservation(
    reservationId = str("reservationId").ifBlank { id },
    reference = str("reference"),
    patientUid = str("patientUid"),
    patientName = str("patientName"),
    pharmacyId = str("pharmacyId"),
    pharmacyName = str("pharmacyName"),
    areaLabel = str("areaLabel"),
    items = mapList("items").map { it.toReservationItem() },
    totalLKR = dbl("totalLKR"),
    prescriptionStoragePath = strOrNull("prescriptionStoragePath"),
    prescriptionAttached = bool("prescriptionAttached"),
    status = ReservationStatus.valueOf(str("status").uppercase()),
    counterCode = str("counterCode"),
    createdAtMillis = long("createdAtMillis"),
    expiresAtMillis = long("expiresAtMillis"),
    dataStatus = str("dataStatus"),
    completedAtMillis = (get("completedAtMillis") as? Number)?.toLong(),
    cancelReason = strOrNull("cancelReason")
)

fun Reservation.toMap(): Map<String, Any?> = mapOf(
    "reservationId" to reservationId,
    "reference" to reference,
    "patientUid" to patientUid,
    "patientName" to patientName,
    "pharmacyId" to pharmacyId,
    "pharmacyName" to pharmacyName,
    "areaLabel" to areaLabel,
    "items" to items.map { it.toMap() },
    "totalLKR" to totalLKR,
    "prescriptionStoragePath" to prescriptionStoragePath,
    "prescriptionAttached" to prescriptionAttached,
    "status" to status.name,
    "counterCode" to counterCode,
    "createdAtMillis" to createdAtMillis,
    "expiresAtMillis" to expiresAtMillis,
    "dataStatus" to dataStatus,
    "completedAtMillis" to completedAtMillis,
    "cancelReason" to cancelReason
)

fun ReservationItem.toMap(): Map<String, Any> = mapOf(
    "medicineId" to medicineId,
    "name" to name,
    "strength" to strength,
    "form" to form,
    "quantity" to quantity,
    "unitPriceLKR" to unitPriceLKR
)

private fun Map<String, Any>.toReservationItem(): ReservationItem = ReservationItem(
    medicineId = this["medicineId"] as? String ?: "",
    name = this["name"] as? String ?: "",
    strength = this["strength"] as? String ?: "",
    form = this["form"] as? String ?: "",
    quantity = (this["quantity"] as? Number)?.toInt() ?: 1,
    unitPriceLKR = (this["unitPriceLKR"] as? Number)?.toDouble() ?: 0.0
)

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = str("uid").ifBlank { id },
    role = when (str("role").lowercase()) {
        "pharmacist" -> UserRole.PHARMACIST
        "admin" -> UserRole.ADMIN
        else -> UserRole.PATIENT
    },
    name = str("name"),
    email = str("email"),
    identifier = str("identifier"),
    pharmacyId = strOrNull("pharmacyId"),
    status = str("status").ifBlank { "active" },
    createdAtMillis = long("createdAtMillis"),
    address = str("address"),
    phone = str("phone"),
    age = int("age"),
    gender = str("gender"),
    bloodGroup = str("bloodGroup"),
    allergies = str("allergies"),
    chronicConditions = str("chronicConditions"),
    emergencyContactName = str("emergencyContactName"),
    emergencyContactPhone = str("emergencyContactPhone"),
    notifyReservationUpdates = get("notifyReservationUpdates") as? Boolean ?: true,
    notifyNewMedicines = bool("notifyNewMedicines"),
    notifyHealthTips = bool("notifyHealthTips")
)

fun UserProfile.toMap(): Map<String, Any?> = mapOf(
    "uid" to uid,
    "role" to role.name.lowercase(),
    "name" to name,
    "email" to email,
    "identifier" to identifier,
    "pharmacyId" to pharmacyId,
    "status" to status,
    "createdAtMillis" to createdAtMillis,
    "address" to address,
    "phone" to phone,
    "age" to age,
    "gender" to gender,
    "bloodGroup" to bloodGroup,
    "allergies" to allergies,
    "chronicConditions" to chronicConditions,
    "emergencyContactName" to emergencyContactName,
    "emergencyContactPhone" to emergencyContactPhone,
    "notifyReservationUpdates" to notifyReservationUpdates,
    "notifyNewMedicines" to notifyNewMedicines,
    "notifyHealthTips" to notifyHealthTips
)

fun DocumentSnapshot.toAppNotification(): AppNotification = AppNotification(
    id = str("notificationId").ifBlank { id },
    patientUid = strOrNull("patientUid"),
    pharmacyId = strOrNull("pharmacyId"),
    type = NotificationType.fromDoc(str("type")),
    title = str("title"),
    body = str("body"),
    reservationId = strOrNull("reservationId"),
    createdAtMillis = long("createdAtMillis"),
    readAtMillis = (get("readAtMillis") as? Number)?.toLong()
)

fun AppNotification.toMap(): Map<String, Any?> = mapOf(
    "notificationId" to id,
    "patientUid" to patientUid,
    "pharmacyId" to pharmacyId,
    "type" to type.docValue,
    "title" to title,
    "body" to body,
    "reservationId" to reservationId,
    "createdAtMillis" to createdAtMillis,
    "readAtMillis" to readAtMillis
)
