package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val Context.languageDataStore by preferencesDataStore(name = "language_prefs")

private val SELECTED_LANGUAGE_TAG_KEY = stringPreferencesKey("selected_language_tag")

// SharedPreferences copy of the same tag, only for [LanguagePreferencesRepository.persistedTagForStartup]:
// a synchronous SharedPreferences read is a tiny XML parse, where a runBlocking DataStore read on
// the main thread cost 37-105ms of every cold start (measured on an SM-A226B debug build).
private const val STARTUP_MIRROR_PREFS = "language_prefs_startup_mirror"
private const val STARTUP_MIRROR_KEY = "selected_language_tag"

const val DEFAULT_LANGUAGE_TAG = "en"

/** One row shown in LanguageSelectScreen. [languageTag] is the BCP-47 tag passed to
 * [LocaleListCompat.forLanguageTags] and matched against `values-<tag>` resource folders. */
data class LanguageOption(val displayName: String, val languageTag: String)

/**
 * The exact language list LanguageSelectScreen shows — kept here (not duplicated) since Settings'
 * Language row subtitle also needs to turn a persisted [LanguageOption.languageTag] back into its
 * display name.
 */
val SupportedLanguages = listOf(
    LanguageOption("English", "en"),
    LanguageOption("Español", "es"),
    LanguageOption("Français", "fr"),
    LanguageOption("한국어", "ko"),
    LanguageOption("Deutsch", "de"),
    LanguageOption("Norsk", "nb"),
)

/**
 * Persists the user's selected app language. Backed by DataStore Preferences, same mechanism as
 * [NotificationPreferencesRepository]/[SpamMarkRepository]. [applyLocale] is the other half of the
 * mechanism — actually switching the app's locale via [AppCompatDelegate.setApplicationLocales],
 * the modern per-app-language API (native on API 33+, backward-compatible via AppCompat below
 * that). It's a plain function (no Context, not suspend) since it's called both right after a
 * coroutine persists the choice (LanguageSelectViewModel) and eagerly, synchronously, from
 * CalloraApplication.onCreate() before any Activity/Configuration is resolved.
 */
class LanguagePreferencesRepository {

    fun selectedLanguageTag(context: Context): Flow<String> =
        context.languageDataStore.data.map { prefs -> prefs[SELECTED_LANGUAGE_TAG_KEY] ?: DEFAULT_LANGUAGE_TAG }

    suspend fun setSelectedLanguageTag(context: Context, languageTag: String) {
        context.languageDataStore.edit { prefs -> prefs[SELECTED_LANGUAGE_TAG_KEY] = languageTag }
        withContext(Dispatchers.IO) { startupMirror(context).edit().putString(STARTUP_MIRROR_KEY, languageTag).commit() }
    }

    companion object {
        /** Blocking — the persisted tag for CalloraApplication.onCreate, which must apply it before
         * any Activity resolves its Configuration. Reads the SharedPreferences mirror; only when
         * that's missing (fresh install, or the first launch after this mirror was introduced)
         * does it fall back to one DataStore read, then backfills the mirror so later cold starts
         * skip DataStore entirely. DataStore stays the source of truth for everything else. */
        fun persistedTagForStartup(context: Context): String {
            val mirror = startupMirror(context)
            mirror.getString(STARTUP_MIRROR_KEY, null)?.let { return it }
            val tag = runBlocking { LanguagePreferencesRepository().selectedLanguageTag(context).first() }
            mirror.edit().putString(STARTUP_MIRROR_KEY, tag).apply()
            return tag
        }

        private fun startupMirror(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(STARTUP_MIRROR_PREFS, Context.MODE_PRIVATE)

        fun applyLocale(languageTag: String) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
    }
}
