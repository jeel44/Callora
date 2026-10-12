package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.AfterCallPreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended.CallEndedActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.isDialerRoleHeld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "CallMonitorService"
private const val CHANNEL_ID = "call_monitor"
private const val NOTIFICATION_ID = 4201

/**
 * Keeps the process alive and listening to the call state so the call-ended screen shows after
 * every call while this app is NOT the default phone app (see [PostCallMonitor]). The manifest
 * phone-state receiver alone isn't dependable: several OEMs don't start a stopped app for it.
 * Foreground service type specialUse — none of the standard types covers "watch for the end of
 * a call this app doesn't own" (phoneCall is for apps handling the call themselves).
 *
 * Only runs when it has a job to do — see [shouldRun]. As default phone app it stops itself.
 */
class CallMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telephonyCallback: Any? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        registerCallStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasPrerequisites(this)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallStateListener()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onCallStateChanged(state: Int) {
        if (isDialerRoleHeld(this)) {
            // Became the default phone app since this started: InCallServiceImpl takes over.
            stopSelf()
            return
        }
        val stateName = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> TelephonyManager.EXTRA_STATE_RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> TelephonyManager.EXTRA_STATE_OFFHOOK
            else -> TelephonyManager.EXTRA_STATE_IDLE
        }
        if (!PostCallMonitor.onPhoneStateChanged(applicationContext, stateName)) return
        if (!Settings.canDrawOverlays(this)) return
        serviceScope.launch { CallEndedActivity.launchForEndedCall(applicationContext) }
    }

    private fun registerCallStateListener() {
        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = this@CallMonitorService.onCallStateChanged(state)
                }
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                        this@CallMonitorService.onCallStateChanged(state)
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            }
        } catch (e: SecurityException) {
            // READ_PHONE_STATE revoked between the hasPrerequisites check and here.
            Log.w(TAG, "registerCallStateListener: no phone-state access", e)
            stopSelf()
        }
    }

    private fun unregisterCallStateListener() {
        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let(telephonyManager::unregisterTelephonyCallback)
        }
        @Suppress("DEPRECATION")
        phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
        telephonyCallback = null
        phoneStateListener = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.call_monitor_channel_name),
            // Silent and collapsed: this notification only exists because a foreground service
            // must have one.
            NotificationManager.IMPORTANCE_MIN,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_call)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.call_monitor_notification_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // Not default (the default phone app gets every call through InCallServiceImpl instead)
        // and able to hear the call state at all.
        private fun hasPrerequisites(context: Context): Boolean =
            !isDialerRoleHeld(context) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Starts or stops the service to match whether it has a job to do right now: the
         * prerequisites above, plus Settings' "After call screen" toggle being on. Called
         * whenever that could have changed — app brought to the foreground, boot, a call
         * starting. Starting from the background can be refused by the system (only allowed here
         * once "display over other apps" is granted); the next foreground visit then starts it.
         */
        fun sync(context: Context) {
            val appContext = context.applicationContext
            syncScope.launch {
                val shouldRun = hasPrerequisites(appContext) &&
                    AfterCallPreferencesRepository().afterCallScreenEnabled(appContext).first()
                val intent = Intent(appContext, CallMonitorService::class.java)
                if (!shouldRun) {
                    appContext.stopService(intent)
                    return@launch
                }
                try {
                    ContextCompat.startForegroundService(appContext, intent)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "sync: background start refused", e)
                }
            }
        }
    }
}
