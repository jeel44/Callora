package callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.OnboardingRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptKind
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallAccount
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.getCallCapableAccounts
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallBannerHost
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPreviewBottomSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SimPickerBottomSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.activecall.ActiveCallScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.allcontacts.AllContactsScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.blockconfirm.BlockConfirmScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.blockedlist.BlockedListScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.contactdetail.ContactDetailScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad.DialpadScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.home.HomeScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall.IncomingCallViewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall.IncomingKnownScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall.IncomingSpamScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.languageselect.LanguageSelectScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.noresults.NoResultsScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notifications.NotificationsScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notificationsettings.NotificationSettingsScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.offline.OfflineScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding1.Onboarding1Screen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding2.Onboarding2Screen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding3.Onboarding3Screen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.outgoingdialing.OutgoingDialingScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.profile.ProfileScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.search.SearchScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.SetDefaultScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.isDialerRoleHeld
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.settings.SettingsScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.spamcenter.SpamCenterScreen
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.splash.SplashScreen
import kotlinx.coroutines.launch

private const val DIAG_TAG = "DIAG_CalloraNavHost"

/** Held while [CalloraNavHostContent]'s SimPickerBottomSheet is up — the number the user tapped
 * plus the 2+ call-capable accounts to choose between (see placeCallWithSimCheck). */
private data class SimPickerRequest(val phoneNumber: String, val accounts: List<CallAccount>)

/**
 * Decides where the NavHost graph starts on this launch — the single decision point for the
 * "only run Splash/Language/Onboarding once" behavior. Onboarding not yet completed (first
 * install, or an install killed mid-way through Onboarding 1-3) always replays the full sequence
 * from [Routes.SPLASH]. Once onboarding has completed (flag persisted via [OnboardingRepository]
 * right when Onboarding 3's "Get Started" is tapped — see the ONBOARDING_3 composable below),
 * every later launch skips straight past Splash/Language/Onboarding and re-derives where to land
 * from real system state, checked fresh on every call: [isDialerRoleHeld] (RoleManager,
 * same check [SetDefaultScreen] itself uses) — never a cached flag. Runtime permissions
 * (contacts/call log/notifications) are no longer a gate here — they're requested contextually,
 * just-in-time, from within Home/Contacts/Notification Settings themselves (see
 * PermissionPreviewBottomSheet), so a missing permission never blocks reaching Home.
 *
 * Checked before any of that: a currently-ringing incoming call. InCallServiceImpl updates
 * [CallRepository.callState] synchronously, strictly before it ever launches/relaunches
 * MainActivity for that same call — so by the time this runs (cold start or otherwise), the
 * state is already there to read. Without this check, a cold start triggered by an incoming
 * call would resolve to Home like any other launch, and only the reactive
 * LaunchedEffect(callUiState.state, callUiState.isIncoming) below would eventually redirect to
 * [Routes.INCOMING_CALL] — visibly flashing Home first. Reading it here instead means the
 * NavHost's very first destination is already correct; that LaunchedEffect remains in place as
 * a fallback for a call that starts ringing after this has already resolved.
 */
private suspend fun resolveStartDestination(context: Context): String {
    val callInfo = CallRepository.callState.value
    if (callInfo.state == CallUiState.RINGING && callInfo.isIncoming) {
        return Routes.INCOMING_CALL
    }
    val onboardingComplete = OnboardingRepository().isOnboardingComplete(context)
    if (!onboardingComplete) return Routes.SPLASH
    return if (!isDialerRoleHeld(context)) Routes.SET_DEFAULT else Routes.HOME
}

@Composable
fun CalloraNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    pendingDeepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingCallPhoneNumber: String? = null,
    onCallConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    // null while the async DataStore read (onboarding-complete flag) + sync system checks
    // (dialer role, permissions) are still resolving. Rendered as a blank, theme-background
    // Surface rather than the NavHost itself — this is what prevents a visible flash of Home (or
    // any other wrong screen) before redirecting; the resolution is a single local DataStore read
    // plus a couple of ContextCompat calls, so in practice this is on-screen for well under a
    // frame.
    val startDestination by produceState<String?>(initialValue = null, context) {
        value = resolveStartDestination(context)
    }

    val resolvedStartDestination = startDestination
    if (resolvedStartDestination == null) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {}
        return
    }

    CalloraNavHostContent(
        startDestination = resolvedStartDestination,
        navController = navController,
        modifier = modifier,
        pendingDeepLinkRoute = pendingDeepLinkRoute,
        onDeepLinkConsumed = onDeepLinkConsumed,
        pendingCallPhoneNumber = pendingCallPhoneNumber,
        onCallConsumed = onCallConsumed,
    )
}

@Composable
private fun CalloraNavHostContent(
    startDestination: String,
    navController: NavHostController,
    modifier: Modifier,
    pendingDeepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit,
    pendingCallPhoneNumber: String?,
    onCallConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val callUiState by CallRepository.callState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val alreadyOnCallMessage = stringResource(R.string.call_in_progress_snackbar_message)
    val viewActionLabel = stringResource(R.string.call_in_progress_view_action)

    // Single shared gate for every "place a call" entry point (Recents tap/swipe, Contact Detail,
    // the dialpad) — real telephony only ever supports one call at a time, so a second attempt
    // while callUiState.state is anything other than NONE/DISCONNECTED must never even reach
    // OutgoingDialingScreen: that screen's LaunchedEffect would call CallRepository.reset(),
    // wiping out the call that's already in progress, then place a second TelecomManager call on
    // top of it. Centralized here rather than duplicated in each call site's own onCall.
    //
    // Also guards on the current destination, not just callUiState.state: TelecomManager.placeCall
    // is an async IPC to the telecom stack, so callUiState.state can still read NONE for a brief
    // window after the first navigate() already fired (before the real onCallAdded callback
    // arrives). A caller invoked twice in quick succession (e.g. a double-fired tap) would pass
    // the state check both times and push OUTGOING_DIALING onto the backstack twice, and the
    // second instance's own placeCall() would call CallRepository.reset() again — wiping the
    // Call reference the first instance is tracking mid-dial. Checking the destination closes
    // that window regardless of what causes the double invocation.
    // The SIM the user picked (see placeCallWithSimCheck/SimPickerBottomSheet below) for the
    // call currently being navigated to — read by the OUTGOING_DIALING composable further down
    // and forwarded into OutgoingDialingScreen. Stays set (harmlessly stale) across a banner tap
    // re-entering an already-in-progress call: OutgoingDialingViewModel.placeCall is idempotent
    // for that case and never re-reads it to actually place a second TelecomManager call.
    var activeCallAccountHandle by remember { mutableStateOf<PhoneAccountHandle?>(null) }

    val proceedWithCall: (String, PhoneAccountHandle?) -> Unit = { phoneNumber, phoneAccountHandle ->
        val alreadyOnDialingScreen = navController.currentDestination?.route == Routes.OUTGOING_DIALING
        if (alreadyOnDialingScreen) {
            // No-op: a second call-placing tap while already navigating to/on that screen for
            // this same in-flight attempt.
        } else if (callUiState.state == CallUiState.NONE || callUiState.state == CallUiState.DISCONNECTED) {
            activeCallAccountHandle = phoneAccountHandle
            navController.navigate(Routes.outgoingDialingRoute(phoneNumber))
        } else {
            coroutineScope.launch {
                // Only offer the "View" tap-through once the existing call is truly Active — while
                // it's still Dialing/Ringing, Routes.ACTIVE_CALL would show a blank/wrong screen
                // since there's no active call to display yet.
                val result = snackbarHostState.showSnackbar(
                    message = alreadyOnCallMessage,
                    actionLabel = if (callUiState.state == CallUiState.ACTIVE) viewActionLabel else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    navController.navigate(Routes.ACTIVE_CALL) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            }
        }
    }

    // Held while SimPickerBottomSheet is up — see placeCallWithSimCheck immediately below.
    var pendingSimPicker by remember { mutableStateOf<SimPickerRequest?>(null) }

    /**
     * Every real call site now routes through here instead of calling [proceedWithCall] directly
     * (see the three call sites below) — this is what makes the dual-SIM picker apply everywhere
     * with no per-call-site changes, the same way [attemptPlaceCall] already centralizes the
     * CALL_PHONE permission popup. On a single-SIM device (or READ_PHONE_STATE not granted —
     * see getCallCapableAccounts) this is a no-op wrapper: fewer than 2 call-capable accounts
     * means [proceedWithCall] fires immediately with a null handle, identical to pre-dual-SIM
     * behavior. Only a genuinely multi-SIM device (2+ accounts) shows the picker first.
     */
    val placeCallWithSimCheck: (String) -> Unit = { phoneNumber ->
        val accounts = getCallCapableAccounts(context)
        if (accounts.size < 2) {
            proceedWithCall(phoneNumber, null)
        } else {
            pendingSimPicker = SimPickerRequest(phoneNumber, accounts)
        }
    }

    // CALL_PHONE popup state — the number from the tap that triggered it, held until the user
    // responds (either way) or the real permission result comes back, at which point
    // proceedWithCall finally runs for it. Nothing else in the app requests CALL_PHONE now that
    // the old upfront Permissions screen is gone (see OutgoingDialingViewModel.placeCall, which
    // silently bails without it), so this is the only place that does.
    //
    // Named distinctly from the `pendingCallPhoneNumber` function PARAMETER above (the real
    // CallEndedActivity call-back deep-link number) — a local var previously reused that exact
    // name here, which shadowed the parameter for the rest of this function and silently broke
    // the LaunchedEffect(pendingCallPhoneNumber) deep-link handoff below: it was reading this
    // permission-prompt state (always null for that flow) instead of the real parameter.
    var pendingCallPhoneNumberForPermission by remember { mutableStateOf<String?>(null) }
    var showCallPhonePrompt by remember { mutableStateOf(false) }

    val callPhonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        pendingCallPhoneNumberForPermission?.let { placeCallWithSimCheck(it) }
        pendingCallPhoneNumberForPermission = null
    }

    /**
     * Public entry point every call-placing screen uses (Recents tap/swipe, Contact Detail, the
     * dialpad — same single shared gate as before, see [proceedWithCall]'s own doc). Wraps it
     * with a one-time CALL_PHONE permission popup on the very first outgoing-call attempt (see
     * [PermissionPromptRepository]). The tap that triggered this always still goes through —
     * via [placeCallWithSimCheck] either immediately (permission already shown-before, or the
     * user tapped "Not now"/"Got it") or once the real runtime permission result comes back (user
     * tapped "Allow access", handled by [callPhonePermissionLauncher] above).
     */
    val attemptPlaceCall: (String) -> Unit = { phoneNumber ->
        coroutineScope.launch {
            if (PermissionPromptRepository().hasBeenShown(context, PermissionPromptKind.CALL_PHONE)) {
                placeCallWithSimCheck(phoneNumber)
            } else {
                pendingCallPhoneNumberForPermission = phoneNumber
                showCallPhonePrompt = true
            }
        }
    }

    // CallEndedActivity's "Call" action (calling a number back right after a call ended) — that
    // popup is a separate Activity from this one, so it can't call attemptPlaceCall directly.
    // It hands the number off via MainActivity.placeCallIntent instead, and this is what actually
    // feeds it through the exact same gate every other call-placing entry point uses, so it
    // navigates straight to OUTGOING_DIALING before the real call is placed — same as Home/
    // Dialpad/Contact Detail — instead of only surfacing CallBannerHost once CallRepository
    // reports the call already in progress with nothing having navigated there first.
    //
    // This reads the `pendingCallPhoneNumber` function PARAMETER (from MainActivity), not the
    // similarly-purposed permission-prompt state above — see
    // pendingCallPhoneNumberForPermission's own doc for the shadowing bug this used to have.
    LaunchedEffect(pendingCallPhoneNumber) {
        pendingCallPhoneNumber?.let { phoneNumber ->
            attemptPlaceCall(phoneNumber)
            onCallConsumed()
        }
    }

    // An incoming call can start ringing while the user is anywhere else in the app — or right
    // as the app cold-starts, since InCallServiceImpl brought MainActivity to the foreground for
    // exactly this reason. This is the one place that reacts to that regardless of what screen
    // is currently on top (screens like OutgoingDialingScreen/ActiveCallScreen only react to call
    // state changes relevant to themselves, once already open).
    //
    // Must check isIncoming: an outgoing call can also report CallUiState.RINGING once the far
    // end starts ringing (see OutgoingDialingScreen's "Ringing…" label) — without this check that
    // transition would hijack the outgoing call and shove the user onto IncomingCallScreen
    // instead, making OutgoingDialingScreen appear to never show at all.
    LaunchedEffect(callUiState.state, callUiState.isIncoming) {
        if (callUiState.state == CallUiState.RINGING &&
            callUiState.isIncoming &&
            navController.currentDestination?.route != Routes.INCOMING_CALL
        ) {
            navController.navigate(Routes.INCOMING_CALL)
        }
    }

    // The ongoing-call notification's tap target — carried in from MainActivity's intent
    // (cold-start extra or onNewIntent while already running), so tapping it lands straight on
    // the live call instead of Home/Splash/whatever screen was last visible. No popUpTo here
    // deliberately: the target route (SPLASH on cold start, or Home/whatever mid-app) varies by
    // how the app was reached, and a popUpTo naming a route that isn't actually on the back
    // stack yet would throw — a plain navigate always works regardless of what's underneath.
    LaunchedEffect(pendingDeepLinkRoute) {
        Log.i(DIAG_TAG, "pendingDeepLinkRoute effect: value=$pendingDeepLinkRoute, currentRoute=${navController.currentDestination?.route}")
        if (pendingDeepLinkRoute != null) {
            navController.navigate(pendingDeepLinkRoute) { launchSingleTop = true }
            Log.i(DIAG_TAG, "pendingDeepLinkRoute effect: navigated to $pendingDeepLinkRoute, backstack=${navController.currentBackStack.value.map { it.destination.route }}")
            onDeepLinkConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onTimeout = {
                    Log.i(
                        DIAG_TAG,
                        "SplashScreen.onTimeout fired: backstack=" +
                            navController.currentBackStack.value.map { it.destination.route } +
                            ", about to popUpTo(SPLASH, inclusive=true) and navigate to LANGUAGE_SELECT",
                    )
                    navController.navigate(Routes.languageSelectRoute(fromSettings = false)) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.LANGUAGE_SELECT,
            arguments = listOf(navArgument("fromSettings") { type = NavType.BoolType; defaultValue = false }),
        ) { backStackEntry ->
            val fromSettings = backStackEntry.arguments?.getBoolean("fromSettings") ?: false
            LanguageSelectScreen(
                onContinue = {
                    if (fromSettings) {
                        // Reached via Settings, not first-run — go back to Settings/Home rather
                        // than falling into the Splash->Onboarding chain below.
                        navController.popBackStack()
                    } else {
                        // Every step of the first-run chain (Language -> Onboarding 1/2/3 -> Set
                        // Default -> Permissions -> Home) must pop the step it's leaving, not just
                        // navigate on top of it — otherwise each screen piles up underneath the
                        // next and a single back press from Home lands back in the middle of
                        // onboarding instead of exiting the app.
                        navController.navigate(Routes.ONBOARDING_1) {
                            popUpTo(Routes.LANGUAGE_SELECT) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING_1) {
            Onboarding1Screen(
                onNext = {
                    navController.navigate(Routes.ONBOARDING_2) {
                        popUpTo(Routes.ONBOARDING_1) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING_2) {
            Onboarding2Screen(
                onNext = {
                    navController.navigate(Routes.ONBOARDING_3) {
                        popUpTo(Routes.ONBOARDING_2) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING_3) {
            Onboarding3Screen(
                onGetStarted = {
                    // Persisted here — the moment the education carousel itself finishes — not
                    // after Set Default/Permissions resolve, so an app kill partway through
                    // those two never replays Splash/Language/Onboarding on the next launch; the
                    // resolveStartDestination re-check above already sends a relaunch straight
                    // back to whichever of Set Default/Permissions is still outstanding.
                    coroutineScope.launch { OnboardingRepository().markOnboardingComplete(context) }
                    // Popping ONBOARDING_3 (rather than SPLASH) since Splash already popped
                    // itself off the back stack in its own onTimeout transition above — by this
                    // point Splash is long gone, so popUpTo(SPLASH) here would never find it and
                    // silently pop nothing.
                    navController.navigate(Routes.SET_DEFAULT) {
                        popUpTo(Routes.ONBOARDING_3) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SET_DEFAULT) {
            // Must pop SET_DEFAULT off (not just navigate on top of it) — otherwise it's left
            // sitting in the back stack beneath every screen from here on, including Home, so a
            // single back press from Home would land back on Set Default instead of exiting.
            SetDefaultScreen(
                onContinue = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SET_DEFAULT) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSpamCenter = { navController.navigate(Routes.SPAM_CENTER) },
                onOpenContactDetail = { phoneNumber ->
                    navController.navigate(Routes.contactDetailRoute(phoneNumber))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAllContacts = { navController.navigate(Routes.ALL_CONTACTS) },
                onOpenDialpad = { navController.navigate(Routes.DIALPAD) },
                onCall = attemptPlaceCall,
            )
        }
        composable(Routes.DIALPAD) {
            DialpadScreen(
                onBack = { navController.popBackStack() },
                onCall = attemptPlaceCall,
            )
        }
        composable(Routes.ALL_CONTACTS) {
            // Scoped to Home's NavBackStackEntry, not this (Contacts') own entry — Contacts is
            // reached via a plain navigate()/popBackStack() push-pop pair, so its own entry (and
            // the default viewModel() tied to it) is destroyed every time the user backs out,
            // unlike Home's entry which survives the whole session (see every "return to Home"
            // call site's popUpTo(HOME){inclusive=false}+launchSingleTop pattern). Without this,
            // AllContactsViewModel — and its cachedIn Pager — was recreated from scratch on every
            // single Home -> Contacts visit, forcing a genuine (correctly-shown, but unwanted)
            // first-load spinner each time instead of reusing already-loaded data. Home is always
            // on the back stack here: ALL_CONTACTS has exactly two navigate() call sites, Home
            // itself and Settings, and Settings is itself only ever reached from Home.
            val homeEntry = remember(navController) { navController.getBackStackEntry(Routes.HOME) }
            AllContactsScreen(
                onBack = { navController.popBackStack() },
                onOpenContactDetail = { phoneNumber ->
                    navController.navigate(Routes.contactDetailRoute(phoneNumber))
                },
                onCall = attemptPlaceCall,
                viewModel = viewModel(viewModelStoreOwner = homeEntry),
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenContactDetail = { phoneNumber ->
                    navController.navigate(Routes.contactDetailRoute(phoneNumber))
                },
            )
        }
        composable(
            route = Routes.CONTACT_DETAIL,
            arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType }),
        ) { backStackEntry ->
            val phoneNumber = Uri.decode(backStackEntry.arguments?.getString("phoneNumber").orEmpty())
            ContactDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToBlockConfirm = { pn -> navController.navigate(Routes.blockConfirmRoute(pn)) },
                onCall = { attemptPlaceCall(phoneNumber) },
            )
        }
        composable(
            route = Routes.BLOCK_CONFIRM,
            arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType }),
        ) {
            BlockConfirmScreen(
                onBlockConfirmed = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.SPAM_CENTER) {
            SpamCenterScreen(
                onClose = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenBlockedList = { navController.navigate(Routes.BLOCKED_LIST) },
            )
        }
        composable(Routes.BLOCKED_LIST) {
            BlockedListScreen(
                onBack = { navController.popBackStack() },
                onOpenSetDefault = { navController.navigate(Routes.SET_DEFAULT) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenBlockedList = { navController.navigate(Routes.BLOCKED_LIST) },
                onOpenLanguageSelect = {
                    navController.navigate(Routes.languageSelectRoute(fromSettings = true))
                },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                onOpenHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenAllContacts = { navController.navigate(Routes.ALL_CONTACTS) },
                onOpenSpamCenter = { navController.navigate(Routes.SPAM_CENTER) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NOTIFICATION_SETTINGS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.OFFLINE) {
            OfflineScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NO_RESULTS) {
            NoResultsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.INCOMING_CALL) {
            val viewModel: IncomingCallViewModel = viewModel()
            val callInfo by viewModel.callState.collectAsState()
            val isSpamMarked by viewModel.isSpamMarked.collectAsState()
            val context = LocalContext.current

            // Covers the call ending on its own (far end hangs up, network drops it, etc.)
            // without the user touching Answer/Decline — Answer itself doesn't navigate directly
            // because there's no synchronous "now show Active Call" moment, only the real
            // STATE_ACTIVE callback arriving via CallRepository, same as OutgoingDialingScreen.
            val currentCallState = callInfo.state
            LaunchedEffect(currentCallState) {
                when (currentCallState) {
                    CallUiState.ACTIVE -> navController.navigate(Routes.ACTIVE_CALL) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                    CallUiState.DISCONNECTED -> navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                    else -> Unit
                }
            }

            val onDecline: () -> Unit = {
                // Real reject, but also navigate unconditionally so the button always works
                // even if no telecom callback ever arrives (mirrors End Call/Cancel elsewhere).
                viewModel.reject()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }

            // isSpamMarked (this device's own local spam-mark record) decides the screen variant —
            // not merely "not a saved contact", so a genuinely unknown-but-never-marked number
            // still gets the neutral Known-screen treatment (no tag), and only numbers the user
            // has actually marked as spam before get the red variant + Block action.
            if (isSpamMarked) {
                IncomingSpamScreen(
                    displayName = callInfo.displayName,
                    phoneNumber = callInfo.phoneNumber,
                    photoUri = callInfo.photoUri,
                    simAccountLabel = callInfo.simAccountLabel,
                    onDecline = onDecline,
                    onAnswer = { viewModel.answer() },
                    onBlock = { viewModel.blockCaller() },
                )
            } else {
                IncomingKnownScreen(
                    displayName = callInfo.displayName,
                    phoneNumber = callInfo.phoneNumber,
                    photoUri = callInfo.photoUri,
                    simAccountLabel = callInfo.simAccountLabel,
                    isSavedContact = callInfo.displayName != null,
                    onDecline = onDecline,
                    onAnswer = { viewModel.answer() },
                    onMessage = {
                        callInfo.phoneNumber?.let { number ->
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
        }
        composable(
            route = Routes.OUTGOING_DIALING,
            arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType }),
        ) { backStackEntry ->
            val phoneNumber = Uri.decode(backStackEntry.arguments?.getString("phoneNumber").orEmpty())
            OutgoingDialingScreen(
                phoneNumber = phoneNumber,
                phoneAccountHandle = activeCallAccountHandle,
                onCancel = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onCallAnswered = {
                    navController.navigate(Routes.ACTIVE_CALL) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onMinimize = {
                    // The call keeps dialing/ringing regardless — mirrors Active Call's own
                    // Minimize, falling back to Home if there's nowhere to pop back to.
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(Routes.ACTIVE_CALL) {
            ActiveCallScreen(
                onEndCall = {
                    // CallEndedActivity (triggered separately, from CallRepository.onCallRemoved)
                    // now owns the entire post-call experience, so this just returns to Home
                    // rather than routing through the old AfterCallScreen destination.
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onMinimize = {
                    // The call keeps running regardless — CallBannerHost is what lets the user
                    // get back to this screen. Falls back to Home if there's nowhere to pop back
                    // to (e.g. the call was answered from a cold app start).
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onOpenContact = { phoneNumber ->
                    navController.navigate(Routes.contactDetailRoute(phoneNumber))
                },
            )
        }
    }

        CallBannerHost(
            navController = navController,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showCallPhonePrompt) {
        // Reuses the call-log Lottie — no dedicated "phone" asset exists yet, and it's the
        // closest visual fit (a call-history list) of the three already bundled.
        PermissionPreviewBottomSheet(
            lottieAsset = "calllog_permission_preview.json",
            heading = stringResource(R.string.call_phone_permission_heading),
            subtext = stringResource(R.string.call_phone_permission_subtext),
            permissions = arrayOf(Manifest.permission.CALL_PHONE),
            onAllowAccess = {
                showCallPhonePrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.CALL_PHONE)
                }
                callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            },
            onNotNow = {
                showCallPhonePrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.CALL_PHONE)
                }
                pendingCallPhoneNumberForPermission?.let { placeCallWithSimCheck(it) }
                pendingCallPhoneNumberForPermission = null
            },
        )
    }

    pendingSimPicker?.let { request ->
        SimPickerBottomSheet(
            accounts = request.accounts,
            onAccountSelected = { handle ->
                pendingSimPicker = null
                proceedWithCall(request.phoneNumber, handle)
            },
            onDismiss = { pendingSimPicker = null },
        )
    }
}
