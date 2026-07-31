package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat

/**
 * Declaring this receiver for [TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION] is what stops
 * the platform's own Telecom service (see the "TelecomMissedCalls" channel owned by
 * com.android.server.telecom, separate from [MISSED_CALL_CHANNEL_ID]) from posting its own generic
 * missed-call notification for the default dialer: per that action's own platform doc, the
 * broadcast is sent to a custom component if the current default dialer declares one, and only
 * falls back to the system's own notification if no such component exists. Callora's own,
 * richer notification is already posted independently by [MissedCallNotifier] (driven from real
 * [InCallServiceImpl] call-state callbacks, with contact/spam lookups this broadcast's extras
 * can't provide), so this receiver's only remaining job is the count-reaches-zero case the
 * platform doc calls out — clearing Callora's own notification(s) so the two stay in sync when
 * something outside Callora (e.g. marking the call log read from another surface) tells the
 * default dialer the missed-call count dropped to zero.
 */
class MissedCallSystemNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, -1)
        if (count == 0) {
            cancelAllMissedCallNotifications(context)
        }
    }

    private fun cancelAllMissedCallNotifications(context: Context) {
        val activeIds = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .filter { it.notification.channelId == MISSED_CALL_CHANNEL_ID }
            .map { it.id }
        val notificationManager = NotificationManagerCompat.from(context)
        activeIds.forEach { notificationManager.cancel(it) }
    }
}
