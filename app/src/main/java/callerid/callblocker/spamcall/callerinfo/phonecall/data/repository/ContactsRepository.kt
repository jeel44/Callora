package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

/** One page of [ContactsRepository.getContactsPage]/[ContactsRepository.searchContactsPage].
 * [rawRowCount] is how many provider rows this page actually consumed — separate from
 * `contacts.size` because a row can be dropped client-side (a failed primary-number lookup, or a
 * dedup hit in the search path) without meaning the page was provider-short; same rationale as
 * [CallLogPage.rawRowCount]. [ContactsPagingSource] uses it to compute the next offset. */
data class ContactsPage(
    val contacts: List<Contact>,
    val rawRowCount: Int,
)

/** One entry of [ContactsRepository]'s T9-matching cache — see [ContactsRepository.loadT9Index]. */
private data class T9IndexEntry(val name: String, val phoneNumber: String)

/** Reads from the device's Contacts provider. Every call is a blocking ContentResolver query — run on Dispatchers.IO. */
class ContactsRepository {

    // Lightweight (name, number)-only cache backing getContactsMatchingDigits — see its kdoc.
    // @Volatile since ContentObserver.onChange fires on a Handler/main thread while cache reads
    // happen on whatever dispatcher the caller used (Dialpad calls this from Dispatchers.IO), so
    // plain-var visibility across those threads isn't otherwise guaranteed.
    @Volatile private var t9Cache: List<T9IndexEntry>? = null

    @Volatile private var t9Observer: ContentObserver? = null

    companion object {
        // Standard phone-keypad T9 letter-to-digit mapping.
        private val t9DigitByLetter: Map<Char, Char> = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
        )

        private fun nameToT9Digits(name: String): String =
            buildString { name.lowercase().forEach { char -> t9DigitByLetter[char]?.let(::append) } }
    }

    /**
     * [includeDetails] additionally queries organization/email — used only by Contact Detail,
     * which is the one caller that shows them. Left off by default so callers that just need
     * name/photo for a list row (recent calls, search results) don't pay for the extra queries.
     */
    fun getContactByPhoneNumber(context: Context, phoneNumber: String, includeDetails: Boolean = false): Contact? {
        if (!CallDataPermissions.hasContacts(context)) return null

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
        )

        context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                if (name != null) {
                    val contactId = if (idIndex >= 0) cursor.getLong(idIndex) else null
                    return Contact(
                        name = name,
                        phoneNumber = phoneNumber,
                        formattedPhoneNumber = formatPhoneNumberForDisplay(phoneNumber),
                        photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null,
                        organization = if (includeDetails) contactId?.let { queryOrganization(context, it) } else null,
                        email = if (includeDetails) contactId?.let { queryEmail(context, it) } else null,
                        contactId = contactId,
                    )
                }
            }
        }
        return null
    }

    private fun queryOrganization(context: Context, contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
        return context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
                    (if (index >= 0) cursor.getString(index) else null)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
    }

    private fun queryEmail(context: Context, contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
        return context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    (if (index >= 0) cursor.getString(index) else null)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
    }

    fun searchContacts(context: Context, query: String, limit: Int = 20): List<Contact> {
        if (!CallDataPermissions.hasContacts(context) || query.isBlank()) return emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val likeArg = "%$query%"
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        val results = mutableListOf<Contact>()
        val seenNumbers = mutableSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(likeArg, likeArg),
            sortOrder,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext() && results.size < limit) {
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (number.isNullOrBlank() || !seenNumbers.add(number)) continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                results += Contact(
                    name = name ?: number,
                    phoneNumber = number,
                    formattedPhoneNumber = formatPhoneNumberForDisplay(number),
                    photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null,
                )
            }
        }
        return results
    }

    /**
     * One page of the unfiltered, alphabetically-sorted contact list — used by
     * [ContactsPagingSource] for the All Contacts screen instead of loading every contact on the
     * device into memory at once.
     *
     * Paged via `LIMIT`/`OFFSET` appended directly to [sortOrder]: unlike [CallLog.Calls],
     * [ContactsContract] has no officially documented `OFFSET` query parameter (only
     * [ContactsContract.LIMIT_PARAM_KEY], with no offset counterpart), so there is no equivalent
     * to [CallLogRepository.getRecentCalls]'s `appendQueryParameter` approach. Appending
     * `"LIMIT n OFFSET m"` straight into the sort-order string is the standard, widely-used
     * technique against the Contacts Provider's SQLite-backed implementation (the sort-order
     * string is passed close to verbatim into the underlying query builder).
     *
     * Sorted server-side with `COLLATE LOCALIZED` — the Contacts Provider's own locale-aware
     * SQLite collation (the same one the system Contacts app itself sorts by) — so no in-memory
     * `Collator` sort over the full list is needed; each page already arrives in final order.
     *
     * Queries [ContactsContract.Contacts] (one row per aggregated contact) rather than
     * [ContactsContract.CommonDataKinds.Phone] (one row per phone number) specifically so pages
     * are inherently deduped at the data-model level: a contact with several numbers still
     * contributes exactly one row here, so unlike a full-list load, no `seenContactIds`
     * bookkeeping — which can't work correctly across page boundaries anyway — is needed. The
     * tradeoff is that the primary phone number per contact
     * isn't on this table, so [queryPrimaryPhoneNumber] does one small extra query per row — the
     * same bounded-per-page-lookup tradeoff [CallLogRepository.getRecentCalls] already makes for
     * its own per-row contact-name lookups.
     */
    fun getContactsPage(context: Context, limit: Int, offset: Int): ContactsPage {
        if (!CallDataPermissions.hasContacts(context)) return ContactsPage(emptyList(), 0)

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI,
        )
        val selection = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1"
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE LOCALIZED ASC LIMIT $limit OFFSET $offset"

        val results = mutableListOf<Contact>()
        var rawRowCount = 0
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (cursor.moveToNext()) {
                rawRowCount++
                val contactId = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                // Counted in rawRowCount either way, so a contact whose number lookup fails
                // (rare — e.g. HAS_PHONE_NUMBER stale relative to the Data table) doesn't corrupt
                // ContactsPagingSource's next-offset math the way silently dropping it without
                // counting would.
                val number = queryPrimaryPhoneNumber(context, contactId) ?: continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                results += Contact(
                    name = name ?: number,
                    phoneNumber = number,
                    formattedPhoneNumber = formatPhoneNumberForDisplay(number),
                    photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null,
                    contactId = contactId,
                )
            }
        }
        return ContactsPage(results, rawRowCount)
    }

    /** The (super-)primary phone number for [contactId] — one small extra query per row in
     * [getContactsPage], since NUMBER isn't a column on the [ContactsContract.Contacts] table. */
    private fun queryPrimaryPhoneNumber(context: Context, contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC"
        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(contactId.toString()),
            sortOrder,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                (if (index >= 0) cursor.getString(index) else null)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    /**
     * One page of search results (name or number contains [query]), paged the same
     * `LIMIT`/`OFFSET`-in-[sortOrder] way as [getContactsPage] — used by [ContactsPagingSource]
     * once the All Contacts screen's search field is non-blank.
     *
     * Unlike [getContactsPage] this queries [ContactsContract.CommonDataKinds.Phone] directly
     * (one row per phone number, not per contact) since `NUMBER` isn't a column on the Contacts
     * table — matching by number requires the Phone table. Deduped only *within* a page (a
     * `seenContactIds` set scoped to this call, same as [searchContacts]); a contact with two-plus
     * matching numbers whose rows straddle a page boundary could in principle still appear twice
     * across pages. That's an accepted, narrow tradeoff: it only affects the actively-filtered
     * search path (result sets here are small by nature — the user has already typed a filter),
     * not [getContactsPage]'s unfiltered path, which is the one that actually matters at
     * 100k-contact scale.
     */
    fun searchContactsPage(context: Context, query: String, limit: Int, offset: Int): ContactsPage {
        if (!CallDataPermissions.hasContacts(context) || query.isBlank()) return ContactsPage(emptyList(), 0)

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val likeArg = "%$query%"
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC " +
            "LIMIT $limit OFFSET $offset"

        val results = mutableListOf<Contact>()
        val seenContactIds = mutableSetOf<Long>()
        var rawRowCount = 0
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(likeArg, likeArg),
            sortOrder,
        )?.use { cursor ->
            val contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext()) {
                rawRowCount++
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (number.isNullOrBlank()) continue
                val contactId = if (contactIdIndex >= 0) cursor.getLong(contactIdIndex) else null
                if (contactId != null && !seenContactIds.add(contactId)) continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                results += Contact(
                    name = name ?: number,
                    phoneNumber = number,
                    formattedPhoneNumber = formatPhoneNumberForDisplay(number),
                    photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null,
                    contactId = contactId,
                )
            }
        }
        return ContactsPage(results, rawRowCount)
    }

    /**
     * Builds the A-Z fast-scroll bar's letter -> approximate item-index map for the *unfiltered*
     * All Contacts list, without loading full [Contact] rows for the whole device.
     *
     * [ContactsContract]'s [android.content.ContentResolver.query] interface has no server-side
     * `GROUP BY`/`DISTINCT` (a `ContentProvider` only exposes structured query() calls, not raw
     * SQL, so there's no way to ask it directly for "first row-index per starting letter" the way
     * a real `SELECT DISTINCT first_letter, MIN(position) ... GROUP BY first_letter` could
     * against a database you control). The lightweight alternative taken here: a single streaming
     * cursor pass over just the `DISPLAY_NAME` column — same filter (`HAS_PHONE_NUMBER = 1`) and
     * same `COLLATE LOCALIZED ASC` sort as [getContactsPage], so the row ordinal counted here
     * lines up exactly with the item index [ContactsPagingSource] hands to `LazyPagingItems`. No
     * photo/number lookups, no [Contact] object construction, no in-memory sort — still O(n) in
     * cursor rows, but each row costs one string comparison, and this only runs once per list
     * load/permission-grant (not per keystroke, not per scroll frame), off the main thread, and
     * never materializes a `List<Contact>`.
     */
    fun getAlphabetIndex(context: Context): Map<String, Int> {
        if (!CallDataPermissions.hasContacts(context)) return emptyMap()

        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        val selection = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1"
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE LOCALIZED ASC"

        val indices = mutableMapOf<String, Int>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            var position = 0
            while (cursor.moveToNext()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val letter = name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
                indices.getOrPut(letter) { position }
                position++
            }
        }
        return indices
    }

    /**
     * T9-style reverse lookup for the Dialpad's live "as you type" match list — matches contacts
     * whose phone number contains [digits] as a substring, or whose name maps (via the standard
     * phone-keypad letter-to-digit mapping, e.g. 6=MNO) to a digit string containing [digits].
     *
     * Filters against [t9Cache], a lightweight `(name, number)`-only index (see [loadT9Index]) —
     * built once per [ContactsRepository] instance (in practice, once per Dialpad session, since
     * [callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad.DialpadViewModel]
     * owns one instance for its lifetime) and reused across every keystroke, rather than the
     * previous behavior of re-querying and re-sorting the full contact list from the provider on
     * every call. [ensureT9CacheReady] registers a [ContentObserver] on the Contacts URI the
     * first time this is called, so an actual contact add/edit/delete invalidates the cache
     * instead of it silently going stale for the rest of the session — see
     * [callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad.DialpadViewModel.onCleared]
     * for where that observer gets torn down via [disposeT9Cache].
     *
     * Returns full [Contact] objects (with `photoUri = null`) only for the up-to-[limit] matches
     * actually surfaced, not for the whole cache — [MatchingContactRow] never reads `photoUri`,
     * so there's no lookup to make it worth paying for here.
     */
    fun getContactsMatchingDigits(context: Context, digits: String, limit: Int = 20): List<Contact> {
        val normalizedDigits = digits.filter { it.isDigit() }
        if (normalizedDigits.isEmpty() || !CallDataPermissions.hasContacts(context)) return emptyList()

        ensureT9CacheReady(context)
        val cache = t9Cache ?: return emptyList()

        return cache.asSequence()
            .filter { entry ->
                entry.phoneNumber.filter { it.isDigit() }.contains(normalizedDigits) ||
                    nameToT9Digits(entry.name).contains(normalizedDigits)
            }
            .take(limit)
            .map { entry ->
                Contact(
                    name = entry.name,
                    phoneNumber = entry.phoneNumber,
                    formattedPhoneNumber = formatPhoneNumberForDisplay(entry.phoneNumber),
                    photoUri = null,
                )
            }
            .toList()
    }

    /** Populates [t9Cache] on first use and registers the invalidating [ContentObserver] if not
     * already registered. Synchronized since [ContentObserver.onChange] can race a concurrent
     * call to [getContactsMatchingDigits] on another thread. */
    @Synchronized
    private fun ensureT9CacheReady(context: Context) {
        if (t9Cache == null) {
            t9Cache = loadT9Index(context)
        }
        if (t9Observer == null) {
            val appContext = context.applicationContext
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    // Invalidate only — rebuilt lazily on the next getContactsMatchingDigits
                    // call rather than eagerly here, so a burst of provider change notifications
                    // (e.g. a sync importing many contacts at once) doesn't trigger a rebuild per
                    // notification.
                    t9Cache = null
                }
            }
            appContext.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            t9Observer = observer
        }
    }

    /** Unregisters the [ContentObserver] [ensureT9CacheReady] registers — call from the owning
     * ViewModel's `onCleared()` so the observer doesn't outlive the screen that needed it. */
    fun disposeT9Cache(context: Context) {
        t9Observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        t9Observer = null
        t9Cache = null
    }

    /** The lightweight `(name, number)` pairs [getContactsMatchingDigits] filters against — just
     * the two columns T9 matching actually needs (see [MatchingContactRow]'s usage), not full
     * [Contact] rows with photo URIs or per-contact dedup lookups. Still one pass over every
     * contact's phone rows, but far cheaper per row than the old full [Contact]-object load this
     * replaced, and — unlike that old load — built once and cached rather than repeated on every
     * keystroke. Sorted by `COLLATE LOCALIZED` so multiple matches surface in the same
     * alphabetical order the rest of the app uses, consistent with pre-cache behavior. */
    private fun loadT9Index(context: Context): List<T9IndexEntry> {
        if (!CallDataPermissions.hasContacts(context)) return emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"

        val results = mutableListOf<T9IndexEntry>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (number.isNullOrBlank()) continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                results += T9IndexEntry(name = name ?: number, phoneNumber = number)
            }
        }
        return results
    }

    /**
     * Resolves the phone number behind a URI returned by an `ACTION_PICK` on
     * [ContactsContract.CommonDataKinds.Phone.CONTENT_URI] (used by Active Call's Add Call picker)
     * — that URI already points directly at a phone row, so this is a single NUMBER-column query,
     * not a second contact lookup.
     */
    fun resolvePickedPhoneNumber(context: Context, phoneRowUri: Uri): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        return context.contentResolver.query(phoneRowUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                (if (index >= 0) cursor.getString(index) else null)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    /**
     * Real deletion against the system Contacts provider (the same source every other query in
     * this class reads from) — not a local/soft removal. Requires WRITE_CONTACTS, a separate
     * runtime permission from READ_CONTACTS (checked by the caller before invoking this); deleting
     * the aggregate [ContactsContract.Contacts] row cascades to its raw contacts/data rows, which
     * is the documented way to remove a contact entirely rather than just one data row.
     */
    fun deleteContact(context: Context, contactId: Long): Boolean {
        return try {
            val rowsDeleted = context.contentResolver.delete(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                null,
                null,
            )
            rowsDeleted > 0
        } catch (_: SecurityException) {
            false
        }
    }

    fun getOwnerDisplayName(context: Context): String? {
        if (!CallDataPermissions.hasContacts(context)) return null

        val projection = arrayOf(ContactsContract.Profile.DISPLAY_NAME)
        return context.contentResolver.query(
            ContactsContract.Profile.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.Profile.DISPLAY_NAME)
                (if (nameIndex >= 0) cursor.getString(nameIndex) else null)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    /**
     * The device owner's own "me" contact card (ContactsContract.Profile) — both name and phone
     * number, for the dedicated Profile screen. Distinct from [getOwnerDisplayName]/Settings'
     * TelephonyManager-based number lookup (used only for the Settings row's compact preview):
     * this reads the actual profile contact a user may have filled in via Contacts/Settings,
     * which is commonly empty — both fields land as null in that case rather than throwing.
     */
    fun getOwnerProfile(context: Context): OwnerProfile {
        if (!CallDataPermissions.hasContacts(context)) return OwnerProfile(null, null)

        val displayName = getOwnerDisplayName(context)

        // Profile phone numbers live in the same Data table as regular contacts, scoped to the
        // profile's own raw contact via the profile/data content directory.
        val profileDataUri = Uri.withAppendedPath(
            ContactsContract.Profile.CONTENT_URI,
            ContactsContract.Contacts.Data.CONTENT_DIRECTORY,
        )
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        val phoneNumber = context.contentResolver.query(
            profileDataUri,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                (if (index >= 0) cursor.getString(index) else null)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

        return OwnerProfile(displayName, phoneNumber)
    }
}

/** Result of [ContactsRepository.getOwnerProfile] — both fields null means no profile is set on this device. */
data class OwnerProfile(val displayName: String?, val phoneNumber: String?)
