package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.languageselect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.DEFAULT_LANGUAGE_TAG
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.LanguagePreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LanguageSelectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LanguagePreferencesRepository()

    val selectedLanguageTag: StateFlow<String> = repository.selectedLanguageTag(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_LANGUAGE_TAG)

    /**
     * Applies the locale synchronously first — AppCompatDelegate.setApplicationLocales()
     * synchronously recreates every live AppCompatActivity itself (MainActivity/CallEndedActivity
     * are both AppCompatActivity), so there's no need for callers to trigger recreate() manually —
     * then persists the choice, the actual suspend/IO part, to DataStore.
     */
    fun selectLanguage(languageTag: String) {
        LanguagePreferencesRepository.applyLocale(languageTag)
        viewModelScope.launch { repository.setSelectedLanguageTag(getApplication(), languageTag) }
    }
}
