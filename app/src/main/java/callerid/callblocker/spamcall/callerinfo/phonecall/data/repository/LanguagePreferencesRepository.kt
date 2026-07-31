package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.languageDataStore by preferencesDataStore(name = "language_prefs")

private val SELECTED_LANGUAGE_TAG_KEY = stringPreferencesKey("selected_language_tag")

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
    }

    companion object {
        fun applyLocale(languageTag: String) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
    }
}
