package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended.CallEndedActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DIAG_TAG = "DIAG_InCallService"
private const val NOTIFICATION_CHANNEL_ID = "active_call_channel"
private const val NOTIFICATION_ID = 1001
private const val ACTION_HANGUP = "callerid.callblocker.spamcall.callerinfo.phonecall.action.HANGUP_FROM_NOTIFICATION"

// Matches CalloraPrimary (ui/theme/Color.kt) — Compose colors aren't reachable from this plain
// Service, so the notification's fallback avatar circle hardcodes the same ARGB value to stay
// visually consistent with the in-app avatar style (colored circle + initial letter).
private const val AVATAR_BACKGROUND_COLOR = 0xFF42A5F5.toInt()
private const val AVATAR_SIZE_PX = 192

/**
 * Bound by the system whenever this app holds RoleManager.ROLE_DIALER and a call exists —
 * declaring this service (see AndroidManifest.xml) is also what makes the app eligible for
 * that role in the first place. Forwards real Call objects to [CallRepository], which is the
 * only way for the Compose UI to observe real call state (the system owns this service's
 * lifecycle, so nothing here can be constructor-injected into it).
 *
 * Also owns the persistent "ongoing call" notification: it must keep tracking/showing this
 * regardless of whether Callora's Activity is even running, since the system can keep this
 * service bound (and the call itself active) long after the user backgrounds or closes the app.
 */
class InCallServiceImpl : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.i(DIAG_TAG, "onCreate: system is instantiating InCallServiceImpl")
        CallRepository.attachContext(applicationContext)
        CallRepository.attachInCallService(this)
        // Post-call popup — fires for every ended call regardless of whether it was ever
        // answered (see CallRepository.onCallEnded's own doc for why this hook exists separately
        // from the Compose nav layer's answered-only DISCONNECTED effects). Also the real
        // missed-call-notification hook (see MissedCallNotifier.notifyIfMissed's own doc for how
        // it narrows this down to genuinely missed calls, and why a screening-service-blocked
        // call never reaches this callback at all).
        CallRepository.onCallEnded = { summary, isIncoming, endedAtMillis ->
            CallEndedActivity.launch(applicationContext, summary, isIncoming, endedAtMillis)
            serviceScope.launch {
                MissedCallNotifier.notifyIfMissed(applicationContext, summary, isIncoming, endedAtMillis)
            }
        }
        createNotificationChannel()
        serviceScope.launch {
            CallRepository.activeCallUiState.collect { state ->
                if (state != null) showOngoingCallNotification(state) else cancelOngoingCallNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's hang-up action re-enters this same service via a plain
        // startService/PendingIntent — not part of the normal system-bound InCallService
        // lifecycle, but nothing prevents a regular Service action from being handled here too.
        if (intent?.action == ACTION_HANGUP) {
            CallRepository.disconnect()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(
            DIAG_TAG,
            "onCallAdded: fired, handle=${call.details?.handle}, state=${call.state}, " +
                "direction=${callDirectionLabel(call)}",
        )
        CallRepository.onCallAdded(call)

        // STATE_RINGING only ever represents the callee side of a call (outgoing calls go
        // straight to STATE_DIALING/STATE_CONNECTING). CallRepository.callState was just updated
        // above, and if MainActivity is already resumed AND the device is actually unlocked/
        // interactive, CalloraNavHost's own LaunchedEffect is already observing that StateFlow
        // and will navigate to the Incoming Call screen in-place, instantly — no Activity launch
        // needed, and firing one anyway would just trigger a visible, redundant task-to-front
        // transition on top of that.
        //
        // isResumed alone isn't a safe signal here: MainActivity.applyIncomingCallWindowFlags
        // grants setShowWhenLocked/setTurnScreenOn for the duration of a call, and per that
        // flag's documented contract the Activity is then exempt from being paused by the
        // keyguard — so isResumed can still read true on a screen that is, from the user's
        // perspective, locked (e.g. a second call arriving after an earlier one ended and the
        // phone was locked again, before MainActivity.clearIncomingCallWindowFlags on idle has a
        // chance to run). Checking the keyguard/interactive state directly closes that gap: a
        // genuinely locked or non-interactive device always goes through the full Activity
        // relaunch below, regardless of what isResumed reports.
        if (call.state == Call.STATE_RINGING) {
            if (MainActivity.isResumed && isDeviceUnlockedAndInteractive()) {
                Log.i(DIAG_TAG, "onCallAdded: incoming call ringing, app foregrounded and device unlocked — relying on in-place Compose navigation")
            } else {
                Log.i(DIAG_TAG, "onCallAdded: incoming call ringing, bringing MainActivity to front")
                launchIncomingCallUi()
            }
        }
    }

    /** True only when the device is both unlocked (no keyguard showing) and interactive (screen
     * on) — the two conditions that must both hold for [MainActivity]'s existing, already-visible
     * window to be a safe substitute for [launchIncomingCallUi]'s full relaunch. */
    private fun isDeviceUnlockedAndInteractive(): Boolean {
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: true
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: false
        return !keyguardLocked && interactive
    }

    /** Launched via the IncomingCallActivityAlias component (not MainActivity directly) so the
     * system's cold-start starting window is drawn from the alias's own dark manifest theme —
     * see the comment on that alias in AndroidManifest.xml for why MainActivity.setTheme() alone
     * can't fix the white-flash-before-incoming-call-screen issue. Only called when MainActivity
     * isn't both resumed and unlocked/interactive (see onCallAdded) — the in-place Compose
     * navigation path handles that one already-visible, already-unlocked case on its own. */
    private fun launchIncomingCallUi() {
        val intent = Intent().apply {
            component = ComponentName(this@InCallServiceImpl, "$packageName.IncomingCallActivityAlias")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_INCOMING_CALL, true)
        }
        startActivity(intent)
    }

    private fun callDirectionLabel(call: Call): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unavailable (API < 29)"
        return when (call.details?.callDirection) {
            Call.Details.DIRECTION_INCOMING -> "incoming"
            Call.Details.DIRECTION_OUTGOING -> "outgoing"
            Call.Details.DIRECTION_UNKNOWN -> "unknown"
            else -> "null"
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(DIAG_TAG, "onCallRemoved: fired, handle=${call.details?.handle}")
        CallRepository.onCallRemoved(call)
    }

    override fun onDestroy() {
        Log.i(DIAG_TAG, "onDestroy: system is unbinding/destroying InCallServiceImpl")
        CallRepository.detachInCallService(this)
        CallRepository.onCallEnded = null
        cancelOngoingCallNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Real mute/route changes — whether from this app's own Mute/Speaker buttons, a Bluetooth
     * headset connecting, or the OS itself — all funnel through here, so CallRepository.audioState
     * never drifts from what's actually happening at the telecom layer. */
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallRepository.onAudioStateChanged(audioState)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_ongoing_calls_name),
            // DEFAULT (not LOW) — this notification represents a live call, not a background
            // status update, and needs to reliably show its icon/priority on the lock screen.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notification_channel_ongoing_calls_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Builds and posts the ongoing-call notification. Loads the large icon (contact photo or a
     * rendered initials avatar) off the main thread before posting — Coil's image load is a
     * suspend call, so this whole function is suspend and is driven from the collector's
     * coroutine in [onCreate], not launched fire-and-forget. */
    private suspend fun showOngoingCallNotification(state: ActiveCallUiState) {
        val formattedNumber = state.phoneNumber?.let { formatPhoneNumberForDisplay(it) }
        val title = state.displayName ?: formattedNumber ?: getString(R.string.active_call_unknown_caller)
        val statusLabel = when (state.state) {
            CallUiState.RINGING -> getString(R.string.outgoing_status_ringing)
            CallUiState.ACTIVE -> getString(R.string.ongoing_call_notification_status_active)
            else -> getString(R.string.outgoing_status_calling)
        }
        val contentText = if (formattedNumber != null) {
            getString(R.string.ongoing_call_notification_subtitle, formattedNumber, statusLabel)
        } else {
            statusLabel
        }

        // Reused as both the notification's own tap target (body) and the "Return to call"
        // action — both must land on the exact same place, so there's no reason for two
        // separate PendingIntents doing the same thing.
        val contentIntent = buildActiveCallContentIntent()
        val hangupIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, InCallServiceImpl::class.java).setAction(ACTION_HANGUP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val largeIcon = loadLargeIcon(state.displayName, state.photoUri)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setLargeIcon(largeIcon)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                // Only a real Active call has a connect timestamp to run a live elapsed timer
                // from — Dialing/Ringing show a plain static "Calling…"/"Ringing…" label instead.
                if (state.connectTimestampMillis != null) {
                    setUsesChronometer(true)
                    setWhen(state.connectTimestampMillis)
                } else {
                    setUsesChronometer(false)
                }
            }
            .setContentIntent(contentIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification_call,
                    getString(R.string.ongoing_call_notification_return_action),
                    contentIntent,
                ).build(),
            )
            .addAction(
                // Targets InCallServiceImpl directly via ACTION_HANGUP (handled in
                // onStartCommand) — deliberately not an activity PendingIntent, so tapping this
                // disconnects the call without ever bringing Callora's UI to the foreground.
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification_call_end,
                    getString(R.string.active_call_end_call_label),
                    hangupIntent,
                ).build(),
            )
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    /** Contact photo if one resolved for this call, otherwise a colored-circle-plus-initial
     * bitmap matching the app's in-UI avatar style (see AllContactsScreen/ContactDetailScreen). */
    private suspend fun loadLargeIcon(displayName: String?, photoUri: String?): Bitmap = withContext(Dispatchers.IO) {
        val photoBitmap = photoUri?.let { loadContactPhotoBitmap(it) }
        photoBitmap ?: renderInitialsAvatar(displayName)
    }

    private suspend fun loadContactPhotoBitmap(photoUri: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(this)
                .data(photoUri)
                // Hardware bitmaps can't back a notification's large icon reliably across OEMs;
                // software is required here anyway since this is drawn straight from a Drawable.
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        } catch (e: Exception) {
            Log.w(DIAG_TAG, "loadContactPhotoBitmap: failed to load $photoUri", e)
            null
        }
    }

    private fun renderInitialsAvatar(displayName: String?): Bitmap {
        val bitmap = Bitmap.createBitmap(AVATAR_SIZE_PX, AVATAR_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = AVATAR_SIZE_PX / 2f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AVATAR_BACKGROUND_COLOR }
        canvas.drawCircle(center, center, center, circlePaint)

        val initial = displayName?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = AVATAR_SIZE_PX * 0.45f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, center, textY, textPaint)
        return bitmap
    }

    private fun cancelOngoingCallNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    /**
     * Plain `PendingIntent.getActivity` + manual NEW_TASK/CLEAR_TOP flags is enough when
     * Callora's task already exists (the flags just bring it forward and hit onNewIntent), but
     * is not reliable for constructing a fresh task from scratch when the whole app process was
     * killed while the call stayed alive at the telephony layer — that's exactly the case
     * [TaskStackBuilder] exists for: it's the documented way to get a correct, single-Activity
     * back stack out of a notification tap regardless of whether Callora is currently running.
     */
    private fun buildActiveCallContentIntent(): PendingIntent {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Read by MainActivity (cold start) / onNewIntent (already running via singleTask)
            // to navigate straight to the live call instead of landing on whatever screen the
            // app last showed.
            putExtra(MainActivity.EXTRA_OPEN_ACTIVE_CALL, true)
        }
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntent(activityIntent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        // Only null if no intents were added to the builder — never true here, since exactly
        // one always is, immediately above.
        return checkNotNull(pendingIntent)
    }
}
