package com.mediflow.app.data.seed

import android.content.Context
import com.mediflow.app.data.model.Medicine
import com.mediflow.app.data.model.PharmacyInfo
import org.json.JSONArray
import org.json.JSONObject

data class SeedCatalog(
    val pharmacies: List<PharmacyInfo>,
    val medicines: List<Medicine>,
    val stock: List<SeedStock>,
    val patients: List<SeedPatient>,
    val pharmacists: List<SeedPharmacist>,
    val admin: SeedAdmin,
    val replaced: SeedReplaced
)

data class SeedStock(
    val pharmacyId: String,
    val medicineId: String,
    val name: String,
    val strength: String,
    val form: String,
    val packSize: String,
    val batchNo: String,
    val expiryDate: String,
    val stockQuantity: Int,
    val unitPriceLKR: Double
)

/** A demo patient. `identifier` signs the account up; the rest are extra ways in. */
data class SeedPatient(
    val name: String,
    val identifier: String,
    val identifiers: List<String>,
    val password: String,
    val age: Int,
    val gender: String,
    val bloodGroup: String
)

data class SeedPharmacist(
    val pharmacyId: String,
    val identifier: String,
    val name: String,
    val password: String
)

data class SeedAdmin(val email: String, val password: String)

/** Accounts written by an earlier seed run; removed before the new ones are created. */
data class SeedReplaced(
    val password: String,
    val patients: List<String>,
    val pharmacists: List<String>
)

object SeedCatalogLoader {

    const val ASSET_PATH = "seed/seed_catalog.json"

    fun load(context: Context): SeedCatalog {
        val root = JSONObject(context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() })
        return SeedCatalog(
            pharmacies = root.getJSONArray("pharmacies").map { PharmacyInfo(it) },
            medicines = root.getJSONArray("medicines").map { Medicine(it) },
            stock = root.getJSONArray("stock").map { SeedStock(it) },
            patients = root.getJSONArray("patients").map { SeedPatient(it) },
            pharmacists = root.getJSONArray("pharmacists").map { SeedPharmacist(it) },
            admin = SeedAdmin(root.getJSONObject("admin")),
            replaced = SeedReplaced(root.optJSONObject("replaced"))
        )
    }

    private fun <T> JSONArray.map(build: (JSONObject) -> T): List<T> =
        (0 until length()).map { build(getJSONObject(it)) }
}

private fun JSONObject.stringOr(key: String): String? =
    if (isNull(key) || opt(key) == null) null else getString(key)

private fun PharmacyInfo(json: JSONObject) = PharmacyInfo(
    pharmacyId = json.getString("pharmacyId"),
    name = json.getString("name"),
    address = json.getString("address"),
    phone = json.getString("phone"),
    licenceNumber = json.getString("licenceNumber"),
    province = json.getString("province"),
    district = json.getString("district"),
    areaLabel = json.getString("areaLabel"),
    latitude = json.getDouble("latitude"),
    longitude = json.getDouble("longitude"),
    openingHours = json.getString("openingHours"),
    deliveryFeeLKR = json.getInt("deliveryFeeLKR"),
    minOrderLKR = json.getInt("minOrderLKR"),
    pharmacistName = json.stringOr("pharmacistName"),
    ownerUid = json.stringOr("ownerUid"),
    status = json.stringOr("status") ?: "approved",
    dataStatus = json.stringOr("dataStatus") ?: "DEMO"
)

private fun Medicine(json: JSONObject) = Medicine(
    medicineId = json.getString("medicineId"),
    name = json.getString("name"),
    genericName = json.getString("genericName"),
    form = json.getString("form"),
    strength = json.stringOr("strength") ?: "",
    packSize = json.getString("packSize"),
    medicineCategory = json.getString("medicineCategory"),
    therapeuticClass = json.getString("therapeuticClass"),
    essentialMedicineLevel = json.stringOr("essentialMedicineLevel") ?: "",
    requiresPrescription = json.getBoolean("requiresPrescription"),
    searchTerms = (0 until json.getJSONArray("searchTerms").length())
        .map { json.getJSONArray("searchTerms").getString(it) },
    dataStatus = json.stringOr("dataStatus") ?: "DEMO"
)

private fun SeedStock(json: JSONObject) = SeedStock(
    pharmacyId = json.getString("pharmacyId"),
    medicineId = json.getString("medicineId"),
    name = json.getString("name"),
    strength = json.stringOr("strength") ?: "",
    form = json.getString("form"),
    packSize = json.getString("packSize"),
    batchNo = json.getString("batchNo"),
    expiryDate = json.getString("expiryDate"),
    stockQuantity = json.getInt("stockQuantity"),
    unitPriceLKR = json.getDouble("unitPriceLKR")
)

private fun SeedPatient(json: JSONObject) = SeedPatient(
    name = json.getString("name"),
    identifier = json.getString("identifier"),
    identifiers = json.optJSONArray("identifiers")?.stringList().orEmpty()
        .ifEmpty { listOf(json.getString("identifier")) },
    password = json.getString("password"),
    age = json.optInt("age"),
    gender = json.stringOr("gender").orEmpty(),
    bloodGroup = json.stringOr("bloodGroup").orEmpty()
)

private fun SeedPharmacist(json: JSONObject) = SeedPharmacist(
    pharmacyId = json.getString("pharmacyId"),
    identifier = json.getString("identifier"),
    name = json.getString("name"),
    password = json.getString("password")
)

private fun SeedAdmin(json: JSONObject) = SeedAdmin(
    email = json.getString("email"),
    password = json.getString("password")
)

private fun SeedReplaced(json: JSONObject?): SeedReplaced = SeedReplaced(
    password = json?.optString("password").orEmpty().ifBlank { "mediflow123" },
    patients = json?.optJSONArray("patients").stringList(),
    pharmacists = json?.optJSONArray("pharmacists").stringList()
)

private fun JSONArray?.stringList(): List<String> =
    this?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
