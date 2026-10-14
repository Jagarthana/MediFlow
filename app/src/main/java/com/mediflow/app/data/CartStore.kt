package com.mediflow.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mediflow.app.data.model.Medicine

/**
 * One medicine the patient intends to reserve. Prices are deliberately absent: the same
 * medicine costs different amounts per branch, so the price is read from the chosen
 * pharmacy's stock row when the offer is built.
 */
data class CartLine(
    val medicineId: String,
    val name: String,
    val strength: String,
    val form: String,
    val packSize: String,
    val quantity: Int,
    val requiresPrescription: Boolean
) {
    val title: String get() = if (strength.isBlank()) name else "$name $strength"
    val caption: String get() = listOf(form, packSize).filter { it.isNotBlank() }.joinToString(" · ")
}

fun Medicine.toCartLine(quantity: Int = 1) = CartLine(
    medicineId = medicineId,
    name = name,
    strength = strength,
    form = form,
    packSize = packSize,
    quantity = quantity,
    requiresPrescription = requiresPrescription
)

/**
 * The basket between the search, review and pickup-pharmacy screens. It lives in memory
 * only: a draft basket is not a reservation, and nothing here is safe to lose if the
 * process dies — the patient simply searches again.
 */
object CartStore {

    var lines by mutableStateOf(emptyList<CartLine>())
        private set

    /** Storage object path of the uploaded slip, once `SlipStorageRepository` succeeds. */
    var slipStoragePath by mutableStateOf<String?>(null)
        private set

    var slipFileName by mutableStateOf<String?>(null)
        private set

    val isEmpty: Boolean get() = lines.isEmpty()
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val needsPrescription: Boolean get() = lines.any { it.requiresPrescription }

    fun attachSlip(storagePath: String, fileName: String) {
        slipStoragePath = storagePath
        slipFileName = fileName
    }

    fun detachSlip() {
        slipStoragePath = null
        slipFileName = null
    }

    fun add(medicine: Medicine, quantity: Int = 1) {
        val existing = lines.firstOrNull { it.medicineId == medicine.medicineId }
        lines = if (existing == null) {
            lines + medicine.toCartLine(quantity)
        } else {
            lines.map { if (it.medicineId == medicine.medicineId) it.copy(quantity = it.quantity + quantity) else it }
        }
    }

    fun setQuantity(medicineId: String, quantity: Int) {
        lines = if (quantity <= 0) {
            lines.filterNot { it.medicineId == medicineId }
        } else {
            lines.map { if (it.medicineId == medicineId) it.copy(quantity = quantity) else it }
        }
    }

    fun remove(medicineId: String) {
        lines = lines.filterNot { it.medicineId == medicineId }
    }

    fun clear() {
        lines = emptyList()
        slipStoragePath = null
        slipFileName = null
    }
}
