package com.mediflow.app.navigation

import android.net.Uri

/**
 * Sealed class representing all navigation destinations (routes) in MediFlow.
 * Using sealed class ensures type-safety and prevents typos in route strings.
 */
sealed class Screen(val route: String) {

    /** Splash / Welcome screen shown on cold launch */
    data object Welcome : Screen("welcome")

    /** Login + Sign Up tabbed screen */
    data object Login : Screen("login")

    /** Forgot Password / OTP / Reset flow */
    data object ForgotPassword : Screen("forgot_password")

    /** Debug-build-only Firestore seeder. Never registered in a release build. */
    data object DebugSeeder : Screen("debug_seeder")

    /** Main patient dashboard */
    data object Dashboard : Screen("dashboard")

    /**
     * Prescription slip capture (camera viewfinder).
     * [ARG_RETURN_TO] decides where a finished capture goes: by default the flow
     * continues into SmartSearch, [RETURN_REVIEW] sends it back to ReservationReview.
     */
    data object PhotoCapture : Screen("photo_capture?returnTo={returnTo}") {

        const val ARG_RETURN_TO = "returnTo"

        /** Capture was opened from Reservation Review to attach a slip to an existing cart. */
        const val RETURN_REVIEW = "review"

        fun routeFor(returnTo: String? = null) =
            "photo_capture?returnTo=${Uri.encode(returnTo.orEmpty())}"
    }

    /**
     * Smart medicine search with live autocomplete.
     * The slip a patient captured is read from [com.mediflow.app.data.CartStore], not
     * passed through the route, so the cart can never claim an attachment that failed to upload.
     */
    data object SmartSearch : Screen("smart_search")

    /** Cart, prescription verification and the legal disclaimer */
    data object ReservationReview :
        Screen("reservation_review?label={label}&variant={variant}") {

        const val ARG_LABEL = "label"
        const val ARG_VARIANT = "variant"

        fun routeFor(label: String?, variant: String?) =
            "reservation_review?label=${Uri.encode(label.orEmpty())}" +
                "&variant=${Uri.encode(variant.orEmpty())}"
    }

    /** Province → District → Town picker that scopes the stock lookup */
    data object AreaDropdown : Screen("area_dropdown?label={label}") {

        /** Cart summary carried through so the area sheet can hand it to Screen 9 */
        const val ARG_LABEL = "label"

        fun routeFor(label: String?) = "area_dropdown?label=${Uri.encode(label.orEmpty())}"
    }

    /** Pharmacies near the chosen area, ranked by how well they cover the cart */
    data object StockResults : Screen("stock_results?label={label}&area={area}") {

        const val ARG_LABEL = "label"
        const val ARG_AREA = "area"

        fun routeFor(label: String?, area: String?) =
            "stock_results?label=${Uri.encode(label.orEmpty())}&area=${Uri.encode(area.orEmpty())}"
    }

    /** Full-screen counter code, shown at the pharmacy desk */
    data object CounterQr :
        Screen("counter_qr?reference={reference}&label={label}&pharmacy={pharmacy}") {

        const val ARG_REFERENCE = "reference"
        const val ARG_LABEL = "label"
        const val ARG_PHARMACY = "pharmacy"

        fun routeFor(reference: String?, label: String?, pharmacy: String?) =
            "counter_qr?reference=${Uri.encode(reference.orEmpty())}" +
                "&label=${Uri.encode(label.orEmpty())}" +
                "&pharmacy=${Uri.encode(pharmacy.orEmpty())}"
    }

    /** Pharmacist stock switcher — availability toggles over the essential-medicines catalog */
    data object PharmacistStock : Screen("pharmacist_stock")

    /** System admin console — live network counts, registry, catalog, reports, own profile */
    data object AdminConsole : Screen("admin_console")

    /** Screen 15: Admin Register Pharmacy — 3-Step Guided Wizard (Screen A2) */
    data object AdminRegisterPharmacy : Screen("admin_register_pharmacy")

    /** Screen 16: Admin Add Medicine Master — Add Drug to Catalog (Screen A3) */
    data object AdminAddMedicine : Screen("admin_add_medicine")
}
