package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context

private const val SPAM_RULES_PREFS = "spam_rules_prefs"
private const val AUTO_BLOCK_TOP_SPAMMERS_KEY = "auto_block_top_spammers"
private const val BLOCK_TELEMARKETERS_KEY = "block_telemarketers"
private const val BLOCK_INTERNATIONAL_SPAM_KEY = "block_international_spam"

/**
 * Persists Spam Center's 3 blocking-rule toggles. Plain SharedPreferences, same reasoning as
 * [ThemePreferencesRepository] — small, read-at-screen-open booleans, no need for DataStore's
 * async machinery. Defaults match what SpamCenterScreen originally hardcoded as in-memory
 * `remember` state (auto-block and telemarketers on, international off) for a fresh install.
 *
 * Read by CalloraCallScreeningService on every screened call — see its shouldRejectByRules for
 * what each rule actually blocks. No community spam database exists: "top spammers" are the
 * numbers this user marked as spam themselves.
 */
object SpamRulesPreferencesRepository {

    private fun prefs(context: Context) = context.getSharedPreferences(SPAM_RULES_PREFS, Context.MODE_PRIVATE)

    fun isAutoBlockTopSpammersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(AUTO_BLOCK_TOP_SPAMMERS_KEY, true)

    fun setAutoBlockTopSpammersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AUTO_BLOCK_TOP_SPAMMERS_KEY, enabled).apply()
    }

    fun isBlockTelemarketersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(BLOCK_TELEMARKETERS_KEY, true)

    fun setBlockTelemarketersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BLOCK_TELEMARKETERS_KEY, enabled).apply()
    }

    fun isBlockInternationalSpamEnabled(context: Context): Boolean =
        prefs(context).getBoolean(BLOCK_INTERNATIONAL_SPAM_KEY, false)

    fun setBlockInternationalSpamEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BLOCK_INTERNATIONAL_SPAM_KEY, enabled).apply()
    }
}
