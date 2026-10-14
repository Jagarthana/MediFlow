package com.mediflow.app.data.auth

import com.mediflow.app.data.model.UserRole

/**
 * The app signs in with typed identifiers (phone, NIC, pharmacist ID) instead of
 * email addresses, so each identifier is mapped onto a synthetic email that only
 * Firebase Auth ever sees. `@mediflow.lk` is not a real mail domain; no mail is sent.
 *
 * One account can be reached through more than one identifier (a patient who knows
 * their phone number and their NIC). The deterministic mapping below can only name
 * one of them, so the extra identifiers live as documents in `logins/{key}` that
 * point back at the account's real synthetic email — see [AuthRepository.resolveEmail].
 */
object SyntheticEmail {

    const val PATIENT_DOMAIN = "@patient.mediflow.lk"
    const val PHARMACIST_DOMAIN = "@pharmacist.mediflow.lk"

    /** Old-format NIC: 9 digits plus a check letter, e.g. 1990123456V. */
    private val LEGACY_NIC = Regex("^\\d{9}[vVxX]$")

    /** New-format NIC: 12 digits, e.g. 200355812728. */
    private val MODERN_NIC = Regex("^\\d{12}$")

    fun phoneIdentifier(identifier: String): String? {
        val digits = identifier.filter { it.isDigit() }
        return if (digits.length in 9..10) digits else null
    }

    fun nicIdentifier(identifier: String): String? {
        val cleaned = identifier.replace(" ", "").replace("-", "")
        return when {
            MODERN_NIC.matches(cleaned) -> cleaned
            LEGACY_NIC.matches(cleaned) -> cleaned.lowercase()
            else -> null
        }
    }

    /** The identifier exactly as it is stored in a profile: phone or NIC, spaces removed. */
    fun normalizedPatientIdentifier(identifier: String): String? =
        phoneIdentifier(identifier) ?: nicIdentifier(identifier)

    fun forPatient(identifier: String): String? {
        val cleaned = identifier.trim()
        phoneIdentifier(cleaned)?.let { return it + PATIENT_DOMAIN }
        nicIdentifier(cleaned)?.let { return "nic$it" + PATIENT_DOMAIN }
        return null
    }

    fun pharmacistKey(pharmacyOrStaffId: String): String? {
        val cleaned = pharmacyOrStaffId.trim().lowercase().replace(" ", "")
        return cleaned.takeIf { it.length in 3..32 && it.matches(Regex("^[a-z0-9-]+$")) }
    }

    fun forPharmacist(pharmacyOrStaffId: String): String? =
        pharmacistKey(pharmacyOrStaffId)?.plus(PHARMACIST_DOMAIN)

    /** Firestore-safe document id for one sign-in identifier of one role. */
    fun loginDocId(role: UserRole, identifier: String): String? = when (role) {
        UserRole.PATIENT -> normalizedPatientIdentifier(identifier)?.let { "patient_$it" }
        UserRole.PHARMACIST -> pharmacistKey(identifier)?.let { "pharmacist_$it" }
        UserRole.ADMIN -> null
    }

    fun forRole(role: UserRole, identifier: String): String? = when (role) {
        UserRole.PATIENT -> forPatient(identifier)
        UserRole.PHARMACIST -> forPharmacist(identifier)
        UserRole.ADMIN -> identifier.trim().takeIf { it.contains('@') && it.length >= 6 }
    }

    /** Recovers the typed identifier for display, e.g. "0772942522", "nic200355812728". */
    fun displayIdentifier(email: String): String = email.substringBefore('@')
}
