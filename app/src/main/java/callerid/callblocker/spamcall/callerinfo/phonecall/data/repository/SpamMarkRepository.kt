package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import android.telephony.PhoneNumberUtils
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamMark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.spamMarkDataStore by preferencesDataStore(name = "spam_mark_prefs")

private const val CATEGORY_PREFIX = "category_"
private const val MARKED_AT_PREFIX = "markedAt_"

/** Best-effort E.164 normalization so the same number always keys the same local row,
 * regardless of how it was formatted at the call site. Falls back to the raw number if the
 * platform can't parse it, rather than failing the mark outright. */
private fun normalizeToE164(number: String): String {
    val countryIso = Locale.getDefault().country.ifBlank { "US" }
    return PhoneNumberUtils.formatNumberToE164(number, countryIso) ?: number
}

/**
 * Purely local, on-device spam marking — no network call, no shared/community data, and
 * therefore no connectivity-related failure states. Backed by DataStore Preferences (the same
 * mechanism this app already uses elsewhere) rather than Room: there's no existing Room/KSP
 * setup in this project, and one device's own marks is a small enough data set that a
 * preferences file is the simpler fit.
 */
class SpamMarkRepository {

    suspend fun markAsSpam(context: Context, phoneNumber: String, category: SpamCategory) {
        val normalized = normalizeToE164(phoneNumber)
        context.spamMarkDataStore.edit { prefs ->
            prefs[stringPreferencesKey(CATEGORY_PREFIX + normalized)] = category.name
            prefs[longPreferencesKey(MARKED_AT_PREFIX + normalized)] = System.currentTimeMillis()
        }
    }

    suspend fun unmarkAsSpam(context: Context, phoneNumber: String) {
        val normalized = normalizeToE164(phoneNumber)
        context.spamMarkDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(CATEGORY_PREFIX + normalized))
            prefs.remove(longPreferencesKey(MARKED_AT_PREFIX + normalized))
        }
    }

    suspend fun isMarkedAsSpam(context: Context, phoneNumber: String): Boolean =
        getSpamMark(context, phoneNumber) != null

    suspend fun getSpamMark(context: Context, phoneNumber: String): SpamMark? {
        val normalized = normalizeToE164(phoneNumber)
        val prefs = context.spamMarkDataStore.data.first()
        val categoryName = prefs[stringPreferencesKey(CATEGORY_PREFIX + normalized)] ?: return null
        val markedAt = prefs[longPreferencesKey(MARKED_AT_PREFIX + normalized)] ?: return null
        val category = SpamCategory.entries.firstOrNull { it.name == categoryName } ?: return null
        return SpamMark(normalized, category, markedAt)
    }

    suspend fun getAllSpamMarks(context: Context): List<SpamMark> {
        val prefs = context.spamMarkDataStore.data.first()
        return prefs.asMap().keys
            .filter { it.name.startsWith(CATEGORY_PREFIX) }
            .mapNotNull { key ->
                val phoneNumber = key.name.removePrefix(CATEGORY_PREFIX)
                val categoryName = prefs[stringPreferencesKey(CATEGORY_PREFIX + phoneNumber)]
                val markedAt = prefs[longPreferencesKey(MARKED_AT_PREFIX + phoneNumber)]
                val category = categoryName?.let { name -> SpamCategory.entries.firstOrNull { it.name == name } }
                if (category != null && markedAt != null) SpamMark(phoneNumber, category, markedAt) else null
            }
    }

    /**
     * Reactive companion to [isMarkedAsSpam], for screens (Home/Recents) that need to react live
     * to a mark/unmark made anywhere else in the app rather than only re-checking once at load
     * time. [Context.spamMarkDataStore] is a single Flow backed by one on-disk file shared by
     * every [SpamMarkRepository] instance's reads *and* writes (same pattern as
     * [NotificationPreferencesRepository.preferences]) — so this emits on every [markAsSpam]/
     * [unmarkAsSpam] call anywhere, including from a completely different screen's ViewModel.
     * Emits the raw normalized keys; pair with [normalize] to test a specific number the same
     * way [isMarkedAsSpam] does internally.
     */
    fun spamMarkedNumbersFlow(context: Context): Flow<Set<String>> =
        context.spamMarkDataStore.data.map { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(CATEGORY_PREFIX) }
                .mapTo(mutableSetOf()) { it.name.removePrefix(CATEGORY_PREFIX) }
        }

    /** The same normalization [isMarkedAsSpam]/[markAsSpam] use internally, exposed so callers
     * matching against [spamMarkedNumbersFlow]'s raw keys stay consistent with it instead of
     * re-deriving their own normalization. */
    fun normalize(phoneNumber: String): String = normalizeToE164(phoneNumber)
}
