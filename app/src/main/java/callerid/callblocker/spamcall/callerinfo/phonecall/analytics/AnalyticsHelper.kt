package callerid.callblocker.spamcall.callerinfo.phonecall.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin wrapper around the app's single [FirebaseAnalytics] instance so call sites elsewhere in the
 * app can log events without each needing a [Context] of their own — mirrors how
 * [com.google.firebase.crashlytics.FirebaseCrashlytics] is reached via its own no-Context
 * getInstance(), except FirebaseAnalytics.getInstance(context) does need one, so [initialize]
 * captures it exactly once from CalloraApplication.onCreate() (via applicationContext, so this
 * can never end up holding an Activity). No explicit FirebaseApp.initializeApp() call is required
 * for this to work — the Firebase SDKs self-initialize via an auto-included ContentProvider
 * (see CalloraApplication's own doc comment on Firebase init).
 */
object AnalyticsHelper {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    fun initialize(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun logEvent(name: String, params: Bundle? = null) {
        firebaseAnalytics.logEvent(name, params)
    }
}
