package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings [CallMonitorService] back after a reboot, without waiting for the app to be opened. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CallMonitorService.sync(context)
    }
}
