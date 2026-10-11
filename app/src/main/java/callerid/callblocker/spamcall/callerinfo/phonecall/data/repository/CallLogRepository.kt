package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.util.Log
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallHistoryEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallType
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallAccount
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.findAccountForCallLog
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.getCallCapableAccounts
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

private const val TAG = "CallLogRepository"

/** Window size [CallLogRepository.getCallHistoryForNumber] walks the call log in — see its kdoc. */
private const val CALL_HISTORY_SCAN_CHUNK_SIZE = 200

/**
 * One page of [getRecentCalls]. [rawRowCount] is how many rows the provider itself returned for
 * this page — separate from `entries.size` because private/unknown-number rows get filtered out
 * client-side, so a short [entries] list doesn't necessarily mean the call log has no more pages.
 * Callers should paginate using [rawRowCount] (`rawRowCount == limit` means there may be more).
 */
data class CallLogPage(
    val entries: List<RecentCallEntry>,
    val rawRowCount: Int,
)

/** Reads from the device's Call Log provider. Every call is a blocking ContentResolver query — run on Dispatchers.IO. */
class CallLogRepository(private val contactsRepository: ContactsRepository = ContactsRepository()) {

    /**
     * Fetches one page of [limit] rows starting at [offset], most recent first. Paginated at the
     * provider level (LIMIT_PARAM_KEY/OFFSET_PARAM_KEY, API 26+) rather than fetching the whole
     * call log at once — a device with ~2000 real entries would otherwise do ~2000 per-row
     * contact lookups (see [ContactsRepository.getContactByPhoneNumber]) in a single call and
     * stall the UI. Callers should keep [limit] small (a screenful) and call this again with an
     * increasing [offset] as the user scrolls.
     */
    fun getRecentCalls(context: Context, limit: Int, offset: Int): CallLogPage {
        if (!CallDataPermissions.hasCallLog(context)) return CallLogPage(entries = emptyList(), rawRowCount = 0)

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
            .appendQueryParameter(CallLog.Calls.OFFSET_PARAM_KEY, offset.toString())
            .build()

        val simAccounts = multiSimAccounts(context)
        val entries = mutableListOf<RecentCallEntry>()
        var rawRowCount = 0
        context.contentResolver.query(
            queryUri,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            rawRowCount = cursor.count
            // Logged to verify this is a genuine live paginated query, not a cached/hardcoded
            // list — rawRowCount staying at exactly `limit` across pages is expected until the
            // real end of the device's call log is reached, where it'll drop below `limit`.
            Log.d(TAG, "getRecentCalls: provider query returned $rawRowCount rows (limit=$limit, offset=$offset)")

            val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val accountComponentIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
            val accountIdIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

            while (cursor.moveToNext()) {
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                // "-1"/"-2" are the provider's sentinels for private/unknown numbers.
                if (number.isNullOrBlank() || number == "-1" || number == "-2") continue

                // Looked up fresh against the Contacts provider rather than trusting the call
                // log's own CACHED_NAME/CACHED_PHOTO_URI, which can go stale if a contact is
                // added, renamed, or re-photographed after the call happened.
                val contact = contactsRepository.getContactByPhoneNumber(context, number)
                val simAccount = cursor.simAccount(simAccounts, accountComponentIndex, accountIdIndex)

                entries += RecentCallEntry(
                    id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                    name = contact?.name,
                    phoneNumber = number,
                    // Computed once here, not inline in RecentCallRow — libphonenumber parsing on
                    // every recomposition/scroll frame is exactly the kind of per-row cost that
                    // causes list jank.
                    formattedPhoneNumber = formatPhoneNumberForDisplay(number),
                    timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                    callType = mapCallType(if (typeIndex >= 0) cursor.getInt(typeIndex) else -1),
                    photoUri = contact?.photoUri,
                    simLabel = simAccount?.label,
                    simColor = simAccount?.color,
                )
            }
        }
        Log.d(
            TAG,
            "getRecentCalls: page has ${entries.size} displayable entries out of $rawRowCount raw rows " +
                "(offset=$offset)",
        )
        return CallLogPage(entries = entries, rawRowCount = rawRowCount)
    }

    /**
     * Past calls to/from one specific number, most recent first, stopping as soon as [limit]
     * matches are found. Matching happens client-side via [PhoneNumberUtils.compare] — the same
     * loose equivalence used elsewhere in this app (see BlockedNumbersRepository) so e.g. a
     * locally-formatted call-log row still matches a contact's E.164 number — so unlike
     * [getRecentCalls] the provider can't be asked to filter by [phoneNumber] directly.
     *
     * Walked in [CALL_HISTORY_SCAN_CHUNK_SIZE]-row windows via the same
     * LIMIT_PARAM_KEY/OFFSET_PARAM_KEY pagination [getRecentCalls] uses, rather than one query
     * over the entire call log with only a client-side `entries.size < limit` stop condition in
     * the `moveToNext` loop. That client-side stop still asked the provider for a single cursor
     * capable of spanning the whole call log (200k+ rows on a real device) even though most of it
     * would often go unconsumed; each windowed query here is bounded at the provider level
     * instead. Note this bounds *each query*, not the total amount of work in the worst case: if
     * [phoneNumber]'s matches are sparse or old, this can still walk many chunks — that's inherent
     * to a client-side-filtered "find N matches" search over an unindexed column, not something
     * pagination alone fixes; what changes is that no single query is ever unbounded.
     */
    fun getCallHistoryForNumber(context: Context, phoneNumber: String, limit: Int = 20): List<CallHistoryEntry> {
        if (!CallDataPermissions.hasCallLog(context)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )

        val simAccounts = multiSimAccounts(context)
        val entries = mutableListOf<CallHistoryEntry>()
        var offset = 0
        while (entries.size < limit) {
            val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, CALL_HISTORY_SCAN_CHUNK_SIZE.toString())
                .appendQueryParameter(CallLog.Calls.OFFSET_PARAM_KEY, offset.toString())
                .build()

            var rawRowCount = 0
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                rawRowCount = cursor.count
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val accountComponentIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
                val accountIdIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext() && entries.size < limit) {
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                    if (number.isNullOrBlank() || !PhoneNumberUtils.compare(number, phoneNumber)) continue

                    val simAccount = cursor.simAccount(simAccounts, accountComponentIndex, accountIdIndex)
                    entries += CallHistoryEntry(
                        phoneNumber = number,
                        timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                        callType = mapCallType(if (typeIndex >= 0) cursor.getInt(typeIndex) else -1),
                        durationSeconds = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L,
                        simLabel = simAccount?.label,
                        simColor = simAccount?.color,
                    )
                }
            }

            // Fewer rows than the chunk size means this was the last page of the call log —
            // nothing left to scan even if entries.size hasn't reached limit yet.
            if (rawRowCount < CALL_HISTORY_SCAN_CHUNK_SIZE) break
            offset += rawRowCount
        }
        return entries
    }

    /**
     * Most recent calls whose number satisfies [matches] (e.g. Spam Center's "is this number
     * spam-marked"), up to [limit], newest first. Walks the call log in the same bounded
     * [CALL_HISTORY_SCAN_CHUNK_SIZE] windows as [getCallHistoryForNumber], but gives up after
     * [maxRowsScanned] rows: matches here are typically rare, and without that cap a few marked
     * numbers with no calls would mean scanning the entire call log on every visit. Contacts are
     * only looked up for matching rows.
     */
    fun getRecentCallsMatching(
        context: Context,
        limit: Int,
        maxRowsScanned: Int,
        matches: (phoneNumber: String) -> Boolean,
    ): List<RecentCallEntry> {
        if (!CallDataPermissions.hasCallLog(context)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val simAccounts = multiSimAccounts(context)
        val entries = mutableListOf<RecentCallEntry>()
        var offset = 0
        while (entries.size < limit && offset < maxRowsScanned) {
            val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, CALL_HISTORY_SCAN_CHUNK_SIZE.toString())
                .appendQueryParameter(CallLog.Calls.OFFSET_PARAM_KEY, offset.toString())
                .build()

            var rawRowCount = 0
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                rawRowCount = cursor.count
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val accountComponentIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
                val accountIdIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (cursor.moveToNext() && entries.size < limit) {
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                    if (number.isNullOrBlank() || number == "-1" || number == "-2" || !matches(number)) continue

                    val contact = contactsRepository.getContactByPhoneNumber(context, number)
                    val simAccount = cursor.simAccount(simAccounts, accountComponentIndex, accountIdIndex)
                    entries += RecentCallEntry(
                        id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                        name = contact?.name,
                        phoneNumber = number,
                        formattedPhoneNumber = formatPhoneNumberForDisplay(number),
                        timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                        callType = mapCallType(if (typeIndex >= 0) cursor.getInt(typeIndex) else -1),
                        photoUri = contact?.photoUri,
                        simLabel = simAccount?.label,
                        simColor = simAccount?.color,
                    )
                }
            }

            if (rawRowCount < CALL_HISTORY_SCAN_CHUNK_SIZE) break
            offset += rawRowCount
        }
        return entries
    }

    /**
     * Distinct call-log numbers containing [query]'s digits as a substring, most recent first, up
     * to [limit] — lets Search (build-2 QA #14: "Searching a number from call history ... gives
     * 'No matches'") find a number that was only ever called/received, never saved as a contact.
     * Matched the same way [ContactsRepository.getContactsMatchingDigits] matches T9 digits
     * (`filter { isDigit() }.contains(...)`) rather than [PhoneNumberUtils.compare], since a
     * partial, in-progress search query is rarely a complete, comparable number. No-op if [query]
     * has no digits at all — a name can't be matched against the call log, which doesn't reliably
     * carry caller names (see [getRecentCalls]'s own comment on CACHED_NAME going stale).
     */
    fun searchCallLogNumbers(context: Context, query: String, limit: Int = 20): List<String> {
        val queryDigits = query.filter { it.isDigit() }
        if (queryDigits.isEmpty() || !CallDataPermissions.hasCallLog(context)) return emptyList()

        val projection = arrayOf(CallLog.Calls.NUMBER)
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, CALL_HISTORY_SCAN_CHUNK_SIZE.toString())
            .build()

        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        context.contentResolver.query(
            queryUri,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            while (cursor.moveToNext() && results.size < limit) {
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (number.isNullOrBlank() || number == "-1" || number == "-2") continue
                if (!number.filter { it.isDigit() }.contains(queryDigits)) continue
                if (!seen.add(number)) continue
                results += number
            }
        }
        return results
    }

    /** Call-capable accounts to tag rows with — empty on a single-SIM device (or without
     * READ_PHONE_STATE), so rows there never carry a SIM label, matching SimAccountLabel. Fetched
     * once per query, not per row. */
    private fun multiSimAccounts(context: Context): List<CallAccount> =
        getCallCapableAccounts(context).takeIf { it.size >= 2 }.orEmpty()

    private fun Cursor.simAccount(accounts: List<CallAccount>, componentIndex: Int, idIndex: Int): CallAccount? {
        if (accounts.isEmpty() || idIndex < 0) return null
        return findAccountForCallLog(
            accounts,
            componentName = if (componentIndex >= 0) getString(componentIndex) else null,
            accountId = getString(idIndex),
        )
    }

    private fun mapCallType(type: Int): CallType = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        else -> CallType.OTHER
    }
}
