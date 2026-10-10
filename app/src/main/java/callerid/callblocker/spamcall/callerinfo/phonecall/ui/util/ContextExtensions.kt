package callerid.callblocker.spamcall.callerinfo.phonecall.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose LocalContext (which may be a ContextWrapper around it, e.g. after
 * AppCompat's per-app-locale wrapping) — needed for APIs that must show from an Activity, like
 * InterstitialAd.show(). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
