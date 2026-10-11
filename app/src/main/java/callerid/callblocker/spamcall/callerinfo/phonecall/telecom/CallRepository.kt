package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallerIdentityCache
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CallRepository"

enum class CallUiState { NONE, DIALING, RINGING, ACTIVE, DISCONNECTED }

/** [displayName] is the single, shared contact-name resolution for the entire call lifecycle —
 * looked up once per phone number and carried through ringing → answered → active → ended, so
 * every screen (Incoming Call, Outgoing Dialing, Active Call, the ongoing-call banner/notification)
 * reads the same value instead of each re-deriving it independently. Filled synchronously from
 * [CallerIdentityCache] when that was already warmed for this number (the normal case — see its
 * doc), otherwise asynchronously. [isResolvingContactName] is what lets every caller tell "still
 * resolving, null so far" apart from "resolved, genuinely no saved contact": while it's true a
 * surface must render a neutral placeholder (see CallerIdentityHeader), never the raw number —
 * otherwise the number paints first and the name replaces it a moment later (a visible flash). */
data class ActiveCallInfo(
    val phoneNumber: String?,
    val displayName: String?,
    val state: CallUiState,
    val connectTimestampMillis: Long?,
    val photoUri: String? = null,
    val isIncoming: Boolean = false,
    val isResolvingContactName: Boolean = false,
    // Which SIM/line this call is on — see resolveAccount. Only ever non-null on a genuinely
    // multi-SIM device; single-SIM devices always see null here, so callers (Incoming Call/Active
    // Call) render nothing extra for the common case.
    val simAccountLabel: String? = null,
    // That SIM's color (ARGB) — the system's own per-SIM tint where available, see
    // PhoneAccounts.getCallCapableAccounts. Non-null exactly when [simAccountLabel] is.
    val simAccountColor: Int? = null,
)

/** [displayName] is a snapshot of [ActiveCallInfo.displayName] taken at the moment the call is
 * removed — AfterCallScreen reads this once (see [lastCallSummary]) rather than the live
 * [callState], which resets moments after the call ends; without capturing it here that name
 * resolution would already be gone by the time AfterCallScreen renders. */
data class CallSummary(
    val phoneNumber: String?,
    val displayName: String?,
    val durationSeconds: Long,
    // False only when the call ended before its contact lookup settled — consumers (see
    // MissedCallNotifier) then resolve it themselves instead of trusting a null [displayName].
    val isContactResolved: Boolean = true,
)

/** The app-wide "there's an outgoing call in progress" fact — non-null for any of
 * DIALING/RINGING/ACTIVE of an OUTGOING call (never for an incoming ringing call, which already
 * has its own full-screen navigation), so the persistent notification/in-app banner can offer a
 * way back during the pre-connect phase too, not just once Active. [connectTimestampMillis] is
 * only present once truly [CallUiState.ACTIVE] — there's nothing to show a live timer for before
 * that, so callers must show a state-appropriate label (Calling…/Ringing…) instead. Mirrors
 * whatever [ActiveCallInfo] already knows — it does not do its own separate lookup. */
data class ActiveCallUiState(
    val displayName: String?,
    val phoneNumber: String?,
    val state: CallUiState,
    val connectTimestampMillis: Long?,
    val photoUri: String? = null,
    val isResolvingContactName: Boolean = false,
)

/** Real mute/speaker state, mirrored from [InCallService.getCallAudioState]/onCallAudioStateChanged
 * — not a locally-toggled UI flag, so Outgoing Dialing and Active Call always agree with each
 * other (and with whatever the system/Bluetooth/wired headset actually did) since both read the
 * same state instead of keeping their own independent booleans. */
data class CallAudioUiState(val isMuted: Boolean = false, val isSpeakerOn: Boolean = false)

/**
 * Bridges real [Call] state from [InCallServiceImpl] to the Compose UI layer. The system
 * instantiates InCallService itself (there's no way to inject a ViewModel/repository into it
 * directly), so this plain singleton is the hand-off point: the service forwards onCallAdded/
 * onCallRemoved here, and screens (Incoming Call, Outgoing Dialing, Active Call, After Call)
 * observe [callState]/[lastCallSummary] instead of running their own simulated timers or their
 * own separate contact-name lookups.
 *
 * Only tracks one call at a time — this app doesn't support multi-call/conference UI.
 */
object CallRepository {

    private var trackedCall: Call? = null

    // Captured once, from the call's state at the moment it's added — STATE_RINGING only ever
    // represents the callee side (outgoing calls go straight to STATE_DIALING/CONNECTING), so
    // this is the same signal InCallServiceImpl already uses to decide whether to surface the
    // Incoming Call UI. Needed so the app-wide "a call is ringing" navigation effect (see
    // CalloraNavHost) doesn't mistake an outgoing call's ringback for an incoming one.
    private var trackedCallIsIncoming: Boolean = false

    // Resolved once per call (in onCallAdded) from the real Call.Details.accountHandle — fixed
    // for the lifetime of a given Call, so unlike displayName/photoUri this never needs
    // re-resolving as the call transitions Dialing -> Ringing -> Active.
    private var trackedCallSimAccount: CallAccount? = null

    private val _callState = MutableStateFlow(emptyState())
    val callState: StateFlow<ActiveCallInfo> = _callState.asStateFlow()

    private val _lastCallSummary = MutableStateFlow<CallSummary?>(null)
    val lastCallSummary: StateFlow<CallSummary?> = _lastCallSummary.asStateFlow()

    /** One-shot hook, invoked at the very end of [onCallRemoved] with the same [CallSummary] just
     * published to [lastCallSummary] plus the call's direction and the wall-clock moment it
     * ended — the one call-end signal that fires for every ended call regardless of whether it
     * was ever answered (unlike the Compose nav layer's DISCONNECTED effects on ActiveCallScreen/
     * IncomingCallScreen, which only cover a call that was already showing one of those screens).
     * Set by InCallServiceImpl.onCreate() so the post-call popup Activity can be launched from a
     * real Service context; deliberately a single nullable callback, not a Flow/listener list —
     * only one thing ever needs this. Doesn't affect [lastCallSummary]/[callState] at all. */
    var onCallEnded: ((summary: CallSummary, isIncoming: Boolean, endedAtMillis: Long, suppressPopup: Boolean) -> Unit)? = null

    // Set by [rejectForSmsCompose] — the post-call popup would otherwise land on top of the SMS
    // composer that Incoming Call's "write your own" reply opens right as the call ends.
    private var suppressPostCallPopup: Boolean = false

    // Telecom's own quick-reply texts for the ringing call (Call.getCannedTextResponses), loaded
    // asynchronously by the system — only for calls with CAPABILITY_RESPOND_VIA_TEXT.
    private val _quickReplies = MutableStateFlow<List<String>>(emptyList())
    val quickReplies: StateFlow<List<String>> = _quickReplies.asStateFlow()

    private val _activeCallUiState = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCallUiState: StateFlow<ActiveCallUiState?> = _activeCallUiState.asStateFlow()

    private val _audioState = MutableStateFlow(CallAudioUiState())
    val audioState: StateFlow<CallAudioUiState> = _audioState.asStateFlow()

    // Only used for the contact-name lookup behind callState.displayName — InCallServiceImpl
    // feeds this in once (it's the one place in this app with a real Context but no ViewModel of
    // its own), since a plain singleton object otherwise has nowhere to get one.
    private var appContext: Context? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Normalized number of the lookup currently in flight, so repeated telecom state changes for
    // the same number (CONNECTING -> DIALING -> ...) don't each launch a duplicate query. Only
    // touched on the main thread, like every other write to _callState (see resolveDisplayName).
    private var inFlightLookupKey: String? = null

    // The system only ever binds one InCallServiceImpl instance at a time (see that class's own
    // doc comment) — setMuted/setAudioRoute live on InCallService itself, not on Call, so this is
    // the only handle this object has for actually changing audio routing/mute.
    private var inCallService: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            applyCallState(call, state)
        }

        override fun onCannedTextResponsesLoaded(call: Call, cannedTextResponses: MutableList<String>?) {
            if (call === trackedCall) _quickReplies.value = cannedTextResponses.orEmpty()
        }
    }

    /** Called once from InCallServiceImpl.onCreate() — every call binds that service, so this
     * is always set well before a call can reach [CallUiState.ACTIVE]. */
    fun attachContext(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Called from InCallServiceImpl.onCreate()/onDestroy() — this is what lets Outgoing
     * Dialing/Active Call's Mute/Speaker buttons reach real audio routing instead of being a
     * purely local, cosmetic toggle. */
    fun attachInCallService(service: InCallService) {
        inCallService = service
        service.callAudioState?.let { onAudioStateChanged(it) }
    }

    fun detachInCallService(service: InCallService) {
        if (inCallService === service) inCallService = null
    }

    /** Forwarded from InCallServiceImpl.onCallAudioStateChanged — the system is the source of
     * truth for mute/route (a Bluetooth headset connecting, or the OS itself changing route, must
     * be reflected here too, not just this app's own button taps). */
    fun onAudioStateChanged(state: CallAudioState) {
        _audioState.value = CallAudioUiState(
            isMuted = state.isMuted,
            isSpeakerOn = state.route == CallAudioState.ROUTE_SPEAKER,
        )
    }

    /** Real InCallService.setMuted — used by both Outgoing Dialing and Active Call's Mute button. */
    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    /** Real InCallService.setAudioRoute — used by both Outgoing Dialing and Active Call's
     * Speaker button. Falls back to the earpiece route (not a wired/Bluetooth route) when turning
     * speaker off, matching what a plain on/off speaker toggle is expected to do. */
    fun setSpeakerEnabled(enabled: Boolean) {
        inCallService?.setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
    }

    /** Real Call.playDtmfTone/stopDtmfTone — Active Call's in-call keypad. A real tone starts on
     * key-press and stops on release (not a fire-and-forget beep), so these are two calls, not one. */
    fun playDtmfTone(digit: Char) {
        trackedCall?.playDtmfTone(digit)
    }

    fun stopDtmfTone() {
        trackedCall?.stopDtmfTone()
    }

    /** Called right before placing a new outgoing call, so a previous call's leftover
     * DISCONNECTED state can't be mistaken for the new call's state. */
    fun reset() {
        trackedCall?.unregisterCallback(callback)
        trackedCall = null
        trackedCallIsIncoming = false
        trackedCallSimAccount = null
        suppressPostCallPopup = false
        _quickReplies.value = emptyList()
        _callState.value = emptyState()
        _activeCallUiState.value = null
    }

    /** Called right after [reset], before TelecomManager.placeCall — seeds [callState] with the
     * number being dialed and its identity (straight from [CallerIdentityCache] when warm, else
     * marked resolving with the lookup already started), so Outgoing Dialing's first frames —
     * which render before the system's onCallAdded arrives — never show the raw number only to
     * swap it for the name. [CallUiState] stays NONE; nothing navigates on this. Also attaches
     * [context]: InCallServiceImpl may not have been bound yet in this process. */
    fun prepareOutgoingCall(context: Context, phoneNumber: String) {
        attachContext(context)
        val cached = CallerIdentityCache.peek(phoneNumber)
        _callState.value = emptyState().copy(
            phoneNumber = phoneNumber,
            displayName = cached?.contact?.name,
            photoUri = cached?.contact?.photoUri,
            isResolvingContactName = cached == null,
        )
        if (cached == null) resolveDisplayName(phoneNumber)
    }

    fun onCallAdded(call: Call) {
        Log.d(TAG, "onCallAdded: real Call object received from InCallServiceImpl")
        trackedCall?.unregisterCallback(callback)
        trackedCall = call
        trackedCallIsIncoming = call.state == Call.STATE_RINGING ||
            call.state == Call.STATE_SIMULATED_RINGING
        // appContext is already set by this point — attachContext runs synchronously in
        // InCallServiceImpl.onCreate(), strictly before the system can ever deliver a Call here.
        trackedCallSimAccount = appContext?.let { resolveAccount(it, call.details?.accountHandle) }
        suppressPostCallPopup = false
        _quickReplies.value = call.cannedTextResponses.orEmpty()
        call.registerCallback(callback)
        applyCallState(call, call.state)
    }

    fun onCallRemoved(call: Call) {
        if (trackedCall !== call) return
        Log.d(TAG, "onCallRemoved: real Call ended")
        val info = _callState.value
        val durationSeconds = info.connectTimestampMillis
            ?.let { (System.currentTimeMillis() - it) / 1000 }
            ?: 0L
        val summary = CallSummary(
            phoneNumber = info.phoneNumber,
            displayName = info.displayName,
            durationSeconds = durationSeconds,
            isContactResolved = !info.isResolvingContactName,
        )
        _lastCallSummary.value = summary

        call.unregisterCallback(callback)
        trackedCall = null
        _quickReplies.value = emptyList()
        val suppressPopup = suppressPostCallPopup
        suppressPostCallPopup = false
        // Deliberately NOT resetting _callState here. onStateChanged already set it to
        // DISCONNECTED just before this runs, and MutableStateFlow only ever holds the latest
        // value — if this immediately overwrote it back to NONE in the same synchronous call
        // stack, a collector could miss the DISCONNECTED emission entirely (Compose coalesces
        // rapid-fire updates into one recomposition) and never navigate off the dialing/active
        // call screen. DISCONNECTED is left in place as the terminal state until [reset] is
        // called for the next outgoing call.

        onCallEnded?.invoke(summary, trackedCallIsIncoming, System.currentTimeMillis(), suppressPopup)
    }

    /** Real Call.disconnect() — used by Active Call's End Call button (and Outgoing Dialing's
     * Cancel, if a call object already exists by the time the user cancels). */
    fun disconnect() {
        trackedCall?.disconnect()
    }

    /** Real Call.answer() — used by the Incoming Call screen's Answer button. */
    fun answer() {
        trackedCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Real Call.reject() — used by the Incoming Call screen's Decline button. */
    fun reject() {
        trackedCall?.reject(false, null)
    }

    /** Whether Telecom itself can decline this call with an SMS ([rejectWithMessage]) — false
     * when the carrier/network doesn't support it or there's no number to reply to. */
    fun canRespondViaText(): Boolean =
        trackedCall?.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true

    /** Real Call.reject(true, text): Telecom declines the call and sends [text] through the
     * default SMS app's respond-via-message service — no SEND_SMS permission and no unlock needed,
     * which is why Incoming Call's quick replies and "Urgent?" go through here. */
    fun rejectWithMessage(text: String) {
        trackedCall?.reject(true, text)
    }

    /** Plain reject, minus the post-call popup — for when the caller is about to open the SMS
     * composer itself (see [suppressPostCallPopup]). */
    fun rejectForSmsCompose() {
        val call = trackedCall ?: return
        suppressPostCallPopup = true
        call.reject(false, null)
    }

    private fun applyCallState(call: Call, state: Int) {
        val number = call.details?.handle?.schemeSpecificPart
        val previous = _callState.value
        val uiState = when (state) {
            Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT -> CallUiState.DIALING
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallUiState.RINGING
            Call.STATE_ACTIVE -> CallUiState.ACTIVE
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallUiState.DISCONNECTED
            else -> previous.state
        }
        val resolvedNumber = number ?: previous.phoneNumber
        val connectTimestamp = if (uiState == CallUiState.ACTIVE) {
            previous.connectTimestampMillis ?: System.currentTimeMillis()
        } else {
            previous.connectTimestampMillis
        }
        // Same party = same number once normalized: telecom's handle can be formatted differently
        // from the number prepareOutgoingCall seeded ("+91 90000 11223" vs "+919000011223"), and a
        // plain string compare there would wipe an already-resolved name back to "resolving".
        val sameParty = resolvedNumber == previous.phoneNumber ||
            CallerIdentityCache.isSameNumber(resolvedNumber, previous.phoneNumber)
        // New party: answer synchronously from the warm cache when possible (CalloraCallScreening
        // Service/placeCall prewarm it), so the very first state the UI sees is already final.
        val cached = if (sameParty) null else resolvedNumber?.let(CallerIdentityCache::peek)

        Log.d(TAG, "applyCallState: telecom state=$state -> uiState=$uiState number=$resolvedNumber")
        _callState.value = ActiveCallInfo(
            phoneNumber = resolvedNumber,
            displayName = if (sameParty) previous.displayName else cached?.contact?.name,
            state = uiState,
            connectTimestampMillis = connectTimestamp,
            photoUri = if (sameParty) previous.photoUri else cached?.contact?.photoUri,
            isIncoming = trackedCallIsIncoming,
            isResolvingContactName = if (sameParty) {
                previous.isResolvingContactName
            } else {
                resolvedNumber != null && cached == null
            },
            simAccountLabel = trackedCallSimAccount?.label,
            simAccountColor = trackedCallSimAccount?.color,
        )
        if (_callState.value.isResolvingContactName && resolvedNumber != null) resolveDisplayName(resolvedNumber)
        updateActiveCallUiState(uiState, resolvedNumber, connectTimestamp)
    }

    /** Cache-miss path only — resolves [ActiveCallInfo.displayName] once per number, then settles
     * it back ON THE MAIN THREAD. The old version wrote _callState from Dispatchers.IO while
     * applyCallState rebuilt it from a stale `previous` on the main thread, so a telecom state
     * change landing mid-lookup could overwrite the resolved name with null and re-arm a second
     * lookup (the name then showed up late, after the number). With every write on one thread
     * there's no interleaving left. Always clears [ActiveCallInfo.isResolvingContactName], even on
     * failure, so no surface can stay stuck on its placeholder. */
    private fun resolveDisplayName(phoneNumber: String) {
        val key = CallerIdentityCache.key(phoneNumber)
        if (inFlightLookupKey == key) return
        val context = appContext
        if (context == null) {
            settleIdentity(phoneNumber, null)
            return
        }
        inFlightLookupKey = key
        repositoryScope.launch {
            val contact = try {
                CallerIdentityCache.resolve(context, phoneNumber)
            } catch (e: Exception) {
                Log.w(TAG, "resolveDisplayName: contact lookup failed", e)
                null
            }
            withContext(Dispatchers.Main) {
                if (inFlightLookupKey == key) inFlightLookupKey = null
                settleIdentity(phoneNumber, contact)
            }
        }
    }

    private fun settleIdentity(phoneNumber: String, contact: Contact?) {
        val current = _callState.value
        // A fast hang-up + new call could otherwise land a stale result on newer state.
        if (!CallerIdentityCache.isSameNumber(current.phoneNumber, phoneNumber)) return
        _callState.value = current.copy(
            displayName = contact?.name,
            photoUri = contact?.photoUri,
            isResolvingContactName = false,
        )
        val banner = _activeCallUiState.value
        if (banner != null && CallerIdentityCache.isSameNumber(banner.phoneNumber, phoneNumber)) {
            _activeCallUiState.value = banner.copy(
                displayName = contact?.name,
                photoUri = contact?.photoUri,
                isResolvingContactName = false,
            )
        }
    }

    /** Derives the app-wide "ongoing call" fact (banner/notification) from the same transition
     * [applyCallState] just computed. Covers DIALING/RINGING of an OUTGOING call — a *ringing*
     * incoming call is excluded, since that already forces its own full-screen navigation (see
     * CalloraNavHost) and doesn't need a separate "get back to it" affordance. ACTIVE is always
     * covered regardless of direction: once an incoming call is answered it's indistinguishable
     * from an outgoing one — minimizing it needs the same way back. */
    private fun updateActiveCallUiState(uiState: CallUiState, phoneNumber: String?, connectTimestamp: Long?) {
        val isOutgoingRingingOrDialing = !trackedCallIsIncoming &&
            (uiState == CallUiState.DIALING || uiState == CallUiState.RINGING)
        val shouldShow = uiState == CallUiState.ACTIVE || isOutgoingRingingOrDialing
        if (!shouldShow) {
            _activeCallUiState.value = null
            return
        }
        _activeCallUiState.value = ActiveCallUiState(
            displayName = _callState.value.displayName,
            phoneNumber = phoneNumber,
            state = uiState,
            connectTimestampMillis = if (uiState == CallUiState.ACTIVE) {
                connectTimestamp ?: System.currentTimeMillis()
            } else {
                null
            },
            photoUri = _callState.value.photoUri,
            isResolvingContactName = _callState.value.isResolvingContactName,
        )
    }

    private fun emptyState() = ActiveCallInfo(
        phoneNumber = null,
        displayName = null,
        state = CallUiState.NONE,
        connectTimestampMillis = null,
    )
}
