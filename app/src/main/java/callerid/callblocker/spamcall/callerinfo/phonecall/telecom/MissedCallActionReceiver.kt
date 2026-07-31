package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the missed-call notification's "Block" and "Dismiss" actions (see
 * [MissedCallNotifier]) — both need to run without ever bringing Callora's UI to the foreground,
 * which rules out an Activity PendingIntent. "Block" additionally needs a real suspend
 * repository call ([BlockedNumbersRepository.blockNumber]) to complete before the notification
 * is cancelled, hence [goAsync] to keep this receiver alive past onReceive returning.
 */
class MissedCallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        when (intent.action) {
            ACTION_BLOCK -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                if (phoneNumber == null) {
                    cancelNotification(context, notificationId)
                    return
                }
                val appContext = context.applicationContext
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        BlockedNumbersRepository().blockNumber(appContext, phoneNumber)
                    } finally {
                        cancelNotification(appContext, notificationId)
                        pendingResult.finish()
                    }
                }
            }
            ACTION_DISMISS -> cancelNotification(context, notificationId)
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId == -1) return
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        const val ACTION_BLOCK =
            "callerid.callblocker.spamcall.callerinfo.phonecall.action.MISSED_CALL_BLOCK"
        const val ACTION_DISMISS =
            "callerid.callblocker.spamcall.callerinfo.phonecall.action.MISSED_CALL_DISMISS"
        const val EXTRA_PHONE_NUMBER =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_MISSED_CALL_PHONE_NUMBER"
        const val EXTRA_NOTIFICATION_ID =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_MISSED_CALL_NOTIFICATION_ID"
    }
}
