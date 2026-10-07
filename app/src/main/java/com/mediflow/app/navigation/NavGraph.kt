package com.mediflow.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mediflow.app.ui.screens.AdminAddMedicineScreen
import com.mediflow.app.ui.screens.AdminDashboardScreen
import com.mediflow.app.ui.screens.AdminRegisterPharmacyScreen
import com.mediflow.app.ui.screens.AreaDropdownScreen
import com.mediflow.app.ui.screens.CounterQrScreen
import com.mediflow.app.ui.screens.ForgotPasswordScreen
import com.mediflow.app.ui.screens.LoginScreen
import com.mediflow.app.ui.screens.PatientDashboardScreen
import com.mediflow.app.ui.screens.PharmacistStockScreen
import com.mediflow.app.ui.screens.PhotoCaptureScreen
import com.mediflow.app.ui.screens.ReservationReviewScreen
import com.mediflow.app.ui.screens.SmartSearchScreen
import com.mediflow.app.ui.screens.StockResultsScreen
import com.mediflow.app.ui.screens.WelcomeScreen

/**
 * MediFlow Navigation Graph
 *
 * Route flow:
 *  Welcome (2.5s auto) → Login ← → ForgotPassword → Dashboard
 *      Dashboard → PhotoCapture → SmartSearch(attached) → ReservationReview
 *      Dashboard → SmartSearch(not attached) → ReservationReview
 *      ReservationReview → AreaDropdown → StockResults → confirmation sheet
 *      StockResults / Dashboard(Vault) → CounterQr
 *
 *  Pharmacist track: Login(role = Pharmacist) → PharmacistStock → Login on logout.
 *  Admin track:      Login(role = Admin) → AdminConsole → Login on logout.
 *  The role is chosen on the login form, not derived from credentials — there is
 *  no backend yet, so nothing validates that the caller really is a pharmacist
 *  or an administrator.
 */
@Composable
fun MediFlowNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        // ── Screen 1: Welcome / Splash ────────────────────────────────────
        composable(Screen.Welcome.route) {
            WelcomeScreen(navController = navController)
        }

        // ── Screen 2: Login / Sign Up (Tabbed) ───────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onPharmacistLogin = {
                    navController.navigate(Screen.PharmacistStock.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onAdminLogin = {
                    navController.navigate(Screen.AdminConsole.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }

        // ── Screen 3: Forgot Password / OTP / Reset ───────────────────────
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onBackToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ForgotPassword.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Screen 4: Patient Dashboard ───────────────────────────────────
        composable(Screen.Dashboard.route) {
            PatientDashboardScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onUploadSlip = { navController.navigate(Screen.PhotoCapture.route) },
                onDirectSearch = {
                    navController.navigate(Screen.SmartSearch.routeFor(attached = false))
                },
                onShowCounterQr = { reference, label, pharmacy ->
                    navController.navigate(
                        Screen.CounterQr.routeFor(reference = reference, label = label, pharmacy = pharmacy)
                    )
                }
            )
        }

        // ── Screen 5: Prescription Slip Capture ────────────────────────────
        composable(Screen.PhotoCapture.route) {
            PhotoCaptureScreen(
                onBack = { navController.popBackStack() },
                onCaptured = {
                    navController.navigate(Screen.SmartSearch.routeFor(attached = true))
                }
            )
        }

        // ── Screen 6: Smart Medicine Search ────────────────────────────────
        composable(
            route = Screen.SmartSearch.route,
            arguments = listOf(
                navArgument(Screen.SmartSearch.ARG_ATTACHED) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            val attached = entry.arguments?.getBoolean(Screen.SmartSearch.ARG_ATTACHED) ?: false
            SmartSearchScreen(
                prescriptionAttached = attached,
                onBack = { navController.popBackStack() },
                onChangePhoto = { navController.navigate(Screen.PhotoCapture.route) },
                onProceed = { label, variant ->
                    navController.navigate(
                        Screen.ReservationReview.routeFor(
                            attached = attached,
                            label = label,
                            variant = variant
                        )
                    )
                }
            )
        }

        // ── Screen 7: Cart & Verification ──────────────────────────────────
        composable(
            route = Screen.ReservationReview.route,
            arguments = listOf(
                navArgument(Screen.ReservationReview.ARG_ATTACHED) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(Screen.ReservationReview.ARG_LABEL) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.ReservationReview.ARG_VARIANT) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val args = entry.arguments
            ReservationReviewScreen(
                prescriptionAttached = args?.getBoolean(Screen.ReservationReview.ARG_ATTACHED) ?: false,
                incomingLabel = args?.getString(Screen.ReservationReview.ARG_LABEL),
                incomingVariant = args?.getString(Screen.ReservationReview.ARG_VARIANT),
                onBack = { navController.popBackStack() },
                onPickPharmacy = { summaryLabel ->
                    navController.navigate(Screen.AreaDropdown.routeFor(summaryLabel))
                }
            )
        }

        // ── Screen 8: Area Dropdown ────────────────────────────────────────
        composable(
            route = Screen.AreaDropdown.route,
            arguments = listOf(
                navArgument(Screen.AreaDropdown.ARG_LABEL) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val label = entry.arguments?.getString(Screen.AreaDropdown.ARG_LABEL)
            AreaDropdownScreen(
                onBack = { navController.popBackStack() },
                onApply = { _, _, town ->
                    navController.navigate(Screen.StockResults.routeFor(label = label, area = town))
                }
            )
        }

        // ── Screen 9: Stock Results ────────────────────────────────────────
        composable(
            route = Screen.StockResults.route,
            arguments = listOf(
                navArgument(Screen.StockResults.ARG_LABEL) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.StockResults.ARG_AREA) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val args = entry.arguments
            StockResultsScreen(
                medicineLabel = args?.getString(Screen.StockResults.ARG_LABEL).orEmpty(),
                area = args?.getString(Screen.StockResults.ARG_AREA).orEmpty(),
                onBack = { navController.popBackStack() },
                onShowQr = { reference, label, pharmacy ->
                    navController.navigate(
                        Screen.CounterQr.routeFor(reference = reference, label = label, pharmacy = pharmacy)
                    )
                }
            )
        }

        // ── Counter QR code (full-screen, brightness boosted) ──────────────
        composable(
            route = Screen.CounterQr.route,
            arguments = listOf(
                navArgument(Screen.CounterQr.ARG_REFERENCE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.CounterQr.ARG_LABEL) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.CounterQr.ARG_PHARMACY) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val args = entry.arguments
            CounterQrScreen(
                reference = args?.getString(Screen.CounterQr.ARG_REFERENCE).orEmpty(),
                medicineLabel = args?.getString(Screen.CounterQr.ARG_LABEL).orEmpty(),
                pharmacyName = args?.getString(Screen.CounterQr.ARG_PHARMACY).orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        // ── Screen P1: Pharmacist Stock Switcher ───────────────────────────
        composable(Screen.PharmacistStock.route) {
            PharmacistStockScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.PharmacistStock.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Screen A1: Admin Dashboard ─────────────────────────────────────
        composable(Screen.AdminConsole.route) {
            AdminDashboardScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.AdminConsole.route) { inclusive = true }
                    }
                },
                onRegisterPharmacy = {
                    navController.navigate(Screen.AdminRegisterPharmacy.route)
                },
                onAddMedicine = {
                    navController.navigate(Screen.AdminAddMedicine.route)
                }
            )
        }

        // ── Screen 15 (A2): Admin Register Pharmacy (3-Step Guided Wizard) ─
        composable(Screen.AdminRegisterPharmacy.route) {
            AdminRegisterPharmacyScreen(
                onBack = { navController.popBackStack() },
                onRegistrationComplete = { navController.popBackStack() }
            )
        }

        // ── Screen 16 (A3): Admin Add Medicine Master ─────────────────────
        composable(Screen.AdminAddMedicine.route) {
            AdminAddMedicineScreen(
                onBack = { navController.popBackStack() },
                onMedicineAdded = { navController.popBackStack() }
            )
        }
    }
}
