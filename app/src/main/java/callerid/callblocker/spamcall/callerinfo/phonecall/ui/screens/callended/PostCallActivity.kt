package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.lifecycle.lifecycleScope
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.PostCallMonitor
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.isDialerRoleHeld
import kotlinx.coroutines.launch

/**
 * The call-ended screen as started by the system itself: on API 30+ Telecom sends
 * [TelecomManager.ACTION_POST_CALL] to the app holding the CALL_SCREENING role when an incoming
 * (not blocked) or outgoing call completes. That is what shows the screen after every call once
 * the user has handed the default phone app to another dialer — no "display over other apps"
 * needed, since the system does the launching.
 *
 * A separate, exported entry point so [CallEndedActivity] itself can stay internal. While this
 * app IS the default phone app the same intent still arrives, alongside InCallServiceImpl's own
 * launch of [CallEndedActivity] — so this one just closes.
 */
class PostCallActivity : CallEndedActivity() {

    override fun onCreateWithoutDetails() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            intent.action != TelecomManager.ACTION_POST_CALL ||
            isDialerRoleHeld(this) ||
            !PostCallMonitor.tryClaim()
        ) {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        val handle = intent.getParcelableExtra<Uri>(TelecomManager.EXTRA_HANDLE)
        val disconnectCause = if (intent.hasExtra(TelecomManager.EXTRA_DISCONNECT_CAUSE)) {
            intent.getIntExtra(TelecomManager.EXTRA_DISCONNECT_CAUSE, 0)
        } else {
            null
        }
        lifecycleScope.launch {
            val details = PostCallMonitor.resolveEndedCall(
                context = applicationContext,
                hintNumber = handle?.schemeSpecificPart,
                hintDisconnectCause = disconnectCause,
            )
            if (details == null) finish() else showCallEnded(details, System.currentTimeMillis())
        }
    }
}
