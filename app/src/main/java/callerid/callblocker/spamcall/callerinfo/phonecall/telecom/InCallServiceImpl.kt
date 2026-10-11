package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.core.app.Person
import androidx.core.app.TaskStackBuilder
import androidx.core.graphics.drawable.IconCompat
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.AfterCallPreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended.CallEndedActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.initialsOf
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DIAG_TAG = "DIAG_InCallService"
private const val NOTIFICATION_CHANNEL_ID = "active_call_channel"
private const val NOTIFICATION_ID = 1001
private const val ACTION_HANGUP = "callerid.callblocker.spamcall.callerinfo.phonecall.action.HANGUP_FROM_NOTIFICATION"
private const val ACTION_TOGGLE_MUTE = "callerid.callblocker.spamcall.callerinfo.phonecall.action.TOGGLE_MUTE_FROM_NOTIFICATION"

// Compose colors aren't reachable from this plain Service, so the fallback avatar hardcodes the
// same values Contact Info's avatar uses (ui/theme/Color.kt): CalloraPrimary initials on the
// Light/DarkAccentContainer circle, picked by the system night mode the notification shade follows.
private const val AVATAR_INITIALS_COLOR = 0xFF42A5F5.toInt()
private const val AVATAR_BACKGROUND_LIGHT = 0xFFE3EFFD.toInt()
private const val AVATAR_BACKGROUND_DARK = 0xFF1E2A3A.toInt()
private const val AVATAR_SIZE_PX = 192

// The dialer's fixed call-green (see DialpadScreen) — the notification's accent colour.
private const val CALL_GREEN_LIGHT = 0xFF2DA84B.toInt()
private const val CALL_GREEN_DARK = 0xFF34C063.toInt()

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

    // Settings' "After call screen" toggle, mirrored here so onCallEnded can decide synchronously
    // (the system may unbind this service right after the last call is removed, so a suspending
    // DataStore read there could lose the race). Defaults on until the first emission lands.
    @Volatile
    private var afterCallScreenEnabled = true

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
        serviceScope.launch {
            AfterCallPreferencesRepository().afterCallScreenEnabled(applicationContext).collect {
                afterCallScreenEnabled = it
            }
        }
        CallRepository.onCallEnded = { summary, isIncoming, endedAtMillis, suppressPopup ->
            if (!suppressPopup && afterCallScreenEnabled) CallEndedActivity.launch(applicationContext, summary, isIncoming, endedAtMillis)
            serviceScope.launch {
                MissedCallNotifier.notifyIfMissed(applicationContext, summary, isIncoming, endedAtMillis)
            }
        }
        createNotificationChannel()
        serviceScope.launch {
            // Driven by the full call state, not CallRepository.activeCallUiState: that one (which
            // also drives the in-app banner) only ever covers outgoing calls, so an answered
            // incoming call used to get no notification at all. Combined with audioState so the
            // Mute action's label follows the real mic state.
            combine(CallRepository.callState, CallRepository.audioState) { call, audio -> call to audio.isMuted }
                .collect { (call, isMuted) ->
                    // Holds the first post until the contact lookup settles (CallRepository always
                    // re-emits once it does) — posting with the raw number and then re-notifying
                    // with the name is the same number -> name flash, just in the notification shade.
                    when {
                        !call.showsOngoingNotification() -> cancelOngoingCallNotification()
                        call.isResolvingContactName -> Unit
                        else -> showOngoingCallNotification(call, isMuted)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's hang-up action re-enters this same service via a plain
        // startService/PendingIntent — not part of the normal system-bound InCallService
        // lifecycle, but nothing prevents a regular Service action from being handled here too.
        when (intent?.action) {
            ACTION_HANGUP -> CallRepository.disconnect()
            // Same CallRepository.setMuted the Active Call screen's Mute button uses.
            ACTION_TOGGLE_MUTE -> CallRepository.setMuted(!CallRepository.audioState.value.isMuted)
        }
        // Being started by an action must not keep this service alive once Telecom unbinds it
        // after the call — clearing the started state leaves its lifetime to the binding alone.
        stopSelf(startId)
        return START_NOT_STICKY
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

    /** Outgoing calls get the notification from dialing onwards (a way back to the call before it
     * connects); incoming calls only once answered — while ringing they have their own full-screen UI. */
    private fun ActiveCallInfo.showsOngoingNotification(): Boolean = when (state) {
        CallUiState.ACTIVE -> true
        CallUiState.DIALING, CallUiState.RINGING -> !isIncoming
        else -> false
    }

    private var isForegroundForCall = false

    /** Builds and posts the ongoing-call notification — CallStyle on Android 12+, a plain
     * notification with the same content below that. Loads the person icon (contact photo or a
     * rendered initials avatar) off the main thread before posting — Coil's image load is a
     * suspend call, so this whole function is suspend and is driven from the collector's
     * coroutine in [onCreate], not launched fire-and-forget. */
    private suspend fun showOngoingCallNotification(call: ActiveCallInfo, isMuted: Boolean) {
        val formattedNumber = call.phoneNumber?.let { formatPhoneNumberForDisplay(it) }
        val title = call.displayName ?: formattedNumber ?: getString(R.string.active_call_unknown_caller)
        // Same "Phone" label Contact Info uses — the app doesn't know the number's real type.
        val statusText = when (call.state) {
            CallUiState.ACTIVE -> getString(R.string.contact_detail_number_label)
            CallUiState.RINGING -> getString(R.string.outgoing_status_ringing)
            else -> getString(R.string.outgoing_status_calling)
        }
        // Which SIM the call is on — only non-null on a multi-SIM device (see resolveAccount),
        // the same label Incoming/Active Call show.
        val contentText = listOfNotNull(statusText, call.simAccountLabel).joinToString(" · ")

        val contentIntent = buildActiveCallContentIntent()
        val hangupIntent = serviceActionIntent(ACTION_HANGUP)
        val avatar = loadAvatar(call.displayName, call.photoUri)
        val muteAction = NotificationCompat.Action.Builder(
            if (isMuted) R.drawable.ic_notification_mic else R.drawable.ic_notification_mic_off,
            getString(if (isMuted) R.string.call_unmute_label else R.string.call_mute_label),
            serviceActionIntent(ACTION_TOGGLE_MUTE),
        ).build()

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setColor(if (isNightMode()) CALL_GREEN_DARK else CALL_GREEN_LIGHT)
            .setContentTitle(title)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                // Only a real Active call has a connect timestamp to run a live elapsed timer
                // from — Dialing/Ringing show a plain static "Calling…"/"Ringing…" label instead.
                if (call.connectTimestampMillis != null) {
                    setUsesChronometer(true)
                    setWhen(call.connectTimestampMillis)
                } else {
                    setUsesChronometer(false)
                }
            }
            .setContentIntent(contentIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            postAsCallStyle(builder, title, avatar, hangupIntent, muteAction)
        ) {
            return
        }

        // Below Android 12, or if the system refused to make this a foreground service (which
        // CallStyle requires): the same content as a plain notification. Hang-up is added as a
        // regular action here — CallStyle supplies its own red one.
        val plain = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setColor(if (isNightMode()) CALL_GREEN_DARK else CALL_GREEN_LIGHT)
            .setContentTitle(title)
            .setContentText(contentText)
            .setLargeIcon(avatar)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(call.connectTimestampMillis != null)
            .apply { call.connectTimestampMillis?.let { setWhen(it) } }
            .setContentIntent(contentIntent)
            .addAction(muteAction)
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
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, plain)
    }

    /** Android 12+ only accepts a CallStyle notification from a foreground service (or one with a
     * full-screen intent, which would pop the call UI over the lock screen on every post), so
     * this service goes foreground with it for the duration of the call. Returns false if the
     * system refuses the foreground start, so the caller can fall back to a plain notification. */
    private fun postAsCallStyle(
        builder: NotificationCompat.Builder,
        title: String,
        avatar: Bitmap,
        hangupIntent: PendingIntent,
        muteAction: NotificationCompat.Action,
    ): Boolean {
        val person = Person.Builder()
            .setName(title)
            .setIcon(IconCompat.createWithBitmap(avatar))
            .setImportant(true)
            .build()
        val notification = builder
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangupIntent))
            .addAction(muteAction)
            .build()
        return try {
            if (isForegroundForCall) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                isForegroundForCall = true
            }
            true
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException/SecurityException, or the CallStyle
            // rejection itself — never worth crashing the in-call service over.
            Log.w(DIAG_TAG, "postAsCallStyle: falling back to a plain notification", e)
            false
        }
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun serviceActionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, InCallServiceImpl::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Contact photo if one resolved for this call, otherwise an initials avatar matching Contact
     * Info's (see ContactDetailScreen's IdentityBlock). */
    private suspend fun loadAvatar(displayName: String?, photoUri: String?): Bitmap = withContext(Dispatchers.IO) {
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

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isNightMode()) AVATAR_BACKGROUND_DARK else AVATAR_BACKGROUND_LIGHT
        }
        canvas.drawCircle(center, center, center, circlePaint)

        val initials = displayName?.let(::initialsOf) ?: "#"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AVATAR_INITIALS_COLOR
            textSize = AVATAR_SIZE_PX * if (initials.length > 1) 0.38f else 0.45f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initials, center, textY, textPaint)
        return bitmap
    }

    private fun cancelOngoingCallNotification() {
        if (isForegroundForCall) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundForCall = false
        }
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
