package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended.CallEndedActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.isDialerRoleHeld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The phone-state broadcast as a call-ended trigger — see [PostCallMonitor] for how this fits
 * with the other two. It is also what brings [CallMonitorService] back up if the system killed
 * it: a call starting is the moment that service is needed again.
 */
class PostCallPhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val appContext = context.applicationContext

        CallMonitorService.sync(appContext)
        if (!PostCallMonitor.onPhoneStateChanged(appContext, state)) return
        if (isDialerRoleHeld(appContext) || !Settings.canDrawOverlays(appContext)) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                CallEndedActivity.launchForEndedCall(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
