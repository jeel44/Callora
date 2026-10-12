package callerid.callblocker.spamcall.callerinfo.phonecall.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.AppOpenAdManager

/** The Activity behind a Compose LocalContext (which may be a ContextWrapper around it, e.g. after
 * AppCompat's per-app-locale wrapping) — needed for APIs that must show from an Activity, like
 * InterstitialAd.show(). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * [Context.startActivity], but first tells [AppOpenAdManager] to skip the App Open ad it would
 * otherwise show the moment this app regains the foreground — build-2 QA #15: "full-screen ad on
 * almost every return to the app" (coming back from SMS, email, share, the contact editor, the
 * Play Store listing, or a plain web link). [AppOpenAdManager] already has exactly this
 * suppression mechanism for call-related intents (see its own [AppOpenAdManager.suppressCurrentForeground]
 * doc) — the bug was simply that nothing called it for any of these other "we ourselves just sent
 * the user to another app for a second" hand-offs, so every one of them was indistinguishable
 * from the user genuinely backgrounding the app and coming back to it later, which is exactly the
 * case the ad is supposed to show for. Every external, app-leaving `startActivity` call in the UI
 * layer should go through this instead of calling `context.startActivity` directly.
 */
fun Context.startExternalActivity(intent: Intent) {
    AppOpenAdManager.suppressNextForeground()
    startActivity(intent)
}

private val WhatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Opens a WhatsApp chat via wa.me, which wants digits only with the country code — so the number
 * goes through [normalizeDialedNumber] first (bare Indian numbers gain 91). The intent is pinned
 * to the WhatsApp/WhatsApp Business package: unpinned, wa.me would just open in the browser when
 * WhatsApp isn't installed. Returns false when neither app is installed.
 */
fun Context.openWhatsAppChat(phoneNumber: String): Boolean {
    val digits = normalizeDialedNumber(phoneNumber).filter { it.isDigit() }
    val uri = Uri.parse("https://wa.me/$digits")
    for (packageName in WhatsAppPackages) {
        try {
            startExternalActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
            return true
        } catch (e: ActivityNotFoundException) {
            // Not installed — try the next package.
        }
    }
    return false
}
