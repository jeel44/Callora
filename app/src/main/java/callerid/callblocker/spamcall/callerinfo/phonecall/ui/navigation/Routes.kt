package callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation

import android.net.Uri

/** Route names for [CalloraNavHost]. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING_1 = "onboarding_1"
    const val ONBOARDING_2 = "onboarding_2"
    const val ONBOARDING_3 = "onboarding_3"
    // fromSettings distinguishes Settings-originated navigation to this screen from first-run's
    // Splash -> Language Select entry — see LANGUAGE_SELECT's composable in CalloraNavHost, whose
    // onContinue branches on it (pop back to Settings vs. proceed into Onboarding 1).
    const val LANGUAGE_SELECT = "language_select?fromSettings={fromSettings}"

    fun languageSelectRoute(fromSettings: Boolean): String = "language_select?fromSettings=$fromSettings"
    const val SET_DEFAULT = "set_default"
    const val HOME = "home"
    // Home's bell icon — a UI-only activity feed, distinct from NOTIFICATION_SETTINGS below.
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    // Settings' "Notifications" row — the toggle preferences sub-page, not the activity feed above.
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val SEARCH = "search"
    const val ALL_CONTACTS = "all_contacts"
    const val DIALPAD = "dialpad"
    const val SPAM_CENTER = "spam_center"
    const val BLOCKED_LIST = "blocked_list"
    const val SETTINGS = "settings"
    const val OFFLINE = "offline"
    const val NO_RESULTS = "no_results"

    // Phone number arrives as a nav argument, encoded via [contactDetailRoute]/[blockConfirmRoute]
    // since numbers can contain "+" and other characters a raw path segment can't carry safely.
    const val CONTACT_DETAIL = "contact_detail/{phoneNumber}"
    const val BLOCK_CONFIRM = "block_confirm/{phoneNumber}"
    const val OUTGOING_DIALING = "outgoing_dialing/{phoneNumber}"

    fun contactDetailRoute(phoneNumber: String): String = "contact_detail/${Uri.encode(phoneNumber)}"
    fun blockConfirmRoute(phoneNumber: String): String = "block_confirm/${Uri.encode(phoneNumber)}"
    fun outgoingDialingRoute(phoneNumber: String): String = "outgoing_dialing/${Uri.encode(phoneNumber)}"

    // Call-lifecycle screens, backed by real telephony state (CallRepository).
    const val INCOMING_CALL = "incoming_call"
    const val ACTIVE_CALL = "active_call"
}
