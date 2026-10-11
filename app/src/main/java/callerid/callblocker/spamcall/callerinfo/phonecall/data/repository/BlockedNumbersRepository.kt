package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import android.provider.BlockedNumberContract
import android.telephony.PhoneNumberUtils
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.BlockedNumberEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.isDialerRoleHeld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps BlockedNumberContract — the system-wide block list the default Phone/Dialer app (or
 * default SMS app, a carrier-privileged app, or the system) uses to auto-reject calls/texts.
 *
 * Gated on [isDialerRoleHeld] — the same RoleManager.ROLE_DIALER check
 * [callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.SetDefaultScreen]/
 * CalloraNavHost already treat as authoritative for default-dialer status elsewhere in this app —
 * not [BlockedNumberContract.canCurrentUserBlockNumbers]. That platform call only reflects
 * whether the *current OS user* is allowed to use the blocked-number feature at all (it reads
 * false for a secondary/guest/work-profile user); it isn't keyed to which app is calling or what
 * role that app holds, and was confirmed on-device to under-report even while Callora genuinely
 * holds ROLE_DIALER. The real access control BlockedNumberContract enforces lives on the
 * insert/delete/query calls themselves, which throw SecurityException without the role — still
 * caught below as defense-in-depth. Every function is gated on [canBlockNumbersSafely] and
 * degrades gracefully (empty/false/no-op) rather than crashing when the role isn't held.
 */
class BlockedNumbersRepository {

    suspend fun getBlockedNumbers(context: Context): List<BlockedNumberEntry> = withContext(Dispatchers.IO) {
        if (!canBlockNumbersSafely(context)) return@withContext emptyList()

        val projection = arrayOf(
            BaseColumns._ID,
            BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER,
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
        )
        val results = mutableListOf<BlockedNumberEntry>()
        try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                projection,
                null,
                null,
                "${BaseColumns._ID} ASC",
            )?.use { cursor ->
                val e164Index = cursor.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER)
                val originalIndex = cursor.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                while (cursor.moveToNext()) {
                    // COLUMN_E164_NUMBER can come back as "" (not null) when the provider
                    // couldn't normalize a number — no country code, ambiguous, etc (build-2 QA
                    // #9: a number like "5557770001" blocked without a country code showed a
                    // blank row). isNullOrBlank() catches that case too, not just a true null.
                    val e164 = if (e164Index >= 0) cursor.getString(e164Index) else null
                    val original = if (originalIndex >= 0) cursor.getString(originalIndex) else null
                    val number = e164.takeUnless { it.isNullOrBlank() }
                        ?: original.takeUnless { it.isNullOrBlank() }
                        ?: continue
                    results += BlockedNumberEntry(phoneNumber = number, blockedTimestampMillis = null)
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }
        dedupeByNumber(results)
    }

    /**
     * BlockedNumberContract doesn't enforce number uniqueness — the same number can end up
     * stored more than once (different formats inserted at different times, OEM dialer vs. this
     * app, etc). Rows are queried oldest-first, so later duplicates overwrite earlier ones here,
     * keeping the most recently blocked entry per number. Matching uses PhoneNumberUtils.compare
     * (the same loose equivalence Android's own telecom stack uses) rather than exact string
     * equality, since e.g. "+919106854193" and a locally-formatted original-number row for the
     * same number won't be byte-identical.
     */
    private fun dedupeByNumber(entries: List<BlockedNumberEntry>): List<BlockedNumberEntry> {
        val deduped = mutableListOf<BlockedNumberEntry>()
        for (entry in entries) {
            val matchIndex = deduped.indexOfFirst { PhoneNumberUtils.compare(it.phoneNumber, entry.phoneNumber) }
            if (matchIndex >= 0) {
                deduped[matchIndex] = entry
            } else {
                deduped += entry
            }
        }
        return deduped
    }

    suspend fun blockNumber(context: Context, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        if (!canBlockNumbersSafely(context)) return@withContext false

        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber)
        }
        try {
            // BlockedNumberContract allows duplicate rows for the same number (it enforces no
            // uniqueness constraint), so re-blocking an already-blocked number must be a no-op
            // rather than inserting another row for getBlockedNumbers()/dedupeByNumber() to
            // paper over later. isBlocked() does its own number-equivalence normalization.
            if (BlockedNumberContract.isBlocked(context, phoneNumber)) return@withContext true
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
        } catch (_: SecurityException) {
            false
        }
    }

    suspend fun unblockNumber(context: Context, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        if (!canBlockNumbersSafely(context)) return@withContext false

        val selection = "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ? OR " +
            "${BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER} = ?"
        try {
            val rowsDeleted = context.contentResolver.delete(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                selection,
                arrayOf(phoneNumber, phoneNumber),
            )
            rowsDeleted > 0
        } catch (_: SecurityException) {
            false
        }
    }

    suspend fun isNumberBlocked(context: Context, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        if (!canBlockNumbersSafely(context)) return@withContext false
        try {
            BlockedNumberContract.isBlocked(context, phoneNumber)
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Authoritative gate for every function above — see the class doc for why this checks
     * [isDialerRoleHeld] directly rather than [BlockedNumberContract.canCurrentUserBlockNumbers].
     * RoleManager itself can still throw on some OEM builds if the role grant and the system
     * service fall out of sync, so this degrades to "blocking unavailable" rather than crashing.
     */
    private fun canBlockNumbersSafely(context: Context): Boolean = try {
        isDialerRoleHeld(context)
    } catch (_: SecurityException) {
        false
    }
}
