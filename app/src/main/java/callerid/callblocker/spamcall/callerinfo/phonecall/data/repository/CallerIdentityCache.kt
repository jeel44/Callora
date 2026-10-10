package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val MAX_ENTRIES = 256

/**
 * Process-wide, number-keyed memo of [ContactsRepository.getContactByPhoneNumber] results —
 * including negative ("not a saved contact") results — so every call surface (Incoming, Outgoing
 * Dialing, Active Call, the ongoing-call notification, the missed-call notification) can answer
 * "who is this?" synchronously, on the very first frame, instead of rendering the raw number and
 * swapping in the name once an async ContentResolver query lands.
 *
 * Deliberately NOT a full in-memory index of every contact: PhoneLookup.CONTENT_FILTER_URI is
 * served from the provider's own phone_lookup table (indexed on the normalized/reversed number),
 * so one lookup stays a few milliseconds even on a 100K-contact device — what made the UI flash
 * was the async hop between "number known" and "name known", not the query cost. Loading 100K
 * rows up front would cost seconds and megabytes and still be cold right after process start.
 * Instead this is warmed at the earliest moment a number is known, ahead of any UI:
 * CalloraCallScreeningService.onScreenCall for incoming calls (runs before InCallService ever
 * sees the call) and OutgoingDialingViewModel.placeCall for outgoing ones.
 *
 * Invalidated wholesale on any Contacts provider change, so an edited/added/deleted contact is
 * never served stale.
 */
object CallerIdentityCache {

    /** [contact] null means "resolved: not a saved contact" — distinct from a [peek] miss (null
     * [Entry]), which means "not resolved yet". */
    data class Entry(val contact: Contact?)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contactsRepository = ContactsRepository()

    @Volatile private var observerRegistered = false

    /** Canonical key for comparing two renderings of the same number ("+91 90000 11223" vs
     * "+919000011223") — also what CallRepository uses to decide "is this still the same call
     * party", so formatting differences between a nav argument and Call.Details.handle can't
     * trigger a spurious re-resolve. */
    fun key(phoneNumber: String): String =
        PhoneNumberUtils.normalizeNumber(phoneNumber).ifEmpty { phoneNumber }

    fun isSameNumber(a: String?, b: String?): Boolean =
        a != null && b != null && key(a) == key(b)

    /** Synchronous, main-thread-safe: never touches the ContentResolver. */
    fun peek(phoneNumber: String): Entry? = synchronized(entries) { entries[key(phoneNumber)] }

    /** Blocking — call from Dispatchers.IO. Returns the cached result if present. Results are only
     * memoized while READ_CONTACTS is held, so a later permission grant isn't masked by a cached
     * "no contact" from before it. */
    fun resolve(context: Context, phoneNumber: String): Contact? {
        peek(phoneNumber)?.let { return it.contact }
        val appContext = context.applicationContext
        if (!CallDataPermissions.hasContacts(appContext)) return null
        registerObserverIfNeeded(appContext)
        val contact = contactsRepository.getContactByPhoneNumber(appContext, phoneNumber)
        synchronized(entries) { entries[key(phoneNumber)] = Entry(contact) }
        return contact
    }

    /** Fire-and-forget [resolve], used to warm the cache before any UI needs the answer. */
    fun prewarm(context: Context, phoneNumber: String) {
        if (peek(phoneNumber) != null) return
        val appContext = context.applicationContext
        scope.launch { runCatching { resolve(appContext, phoneNumber) } }
    }

    private fun registerObserverIfNeeded(context: Context) {
        if (observerRegistered) return
        synchronized(this) {
            if (observerRegistered) return
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        synchronized(entries) { entries.clear() }
                    }
                },
            )
            observerRegistered = true
        }
    }
}
