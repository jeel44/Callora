package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.languageselect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.InlineNativeAdController
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.LanguagePreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "LanguageSelectViewModel"

// Rapid taps between languages only reload the ad for the selection the user settles on.
private const val AD_RELOAD_DEBOUNCE_MILLIS = 450L

// savedStateHandle carries the LANGUAGE_SELECT route's "fromSettings" nav argument (see
// CalloraNavHost) — the default viewModel() factory fills it from the back stack entry.
class LanguageSelectViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = LanguagePreferencesRepository()

    private val isFromSettings: Boolean = savedStateHandle.get<Boolean>("fromSettings") ?: false

    // Onboarding: starts null — the user must actively pick a language (Continue is disabled
    // until they do). Settings: pre-filled from the saved language in init below, so the current
    // choice is checked and Continue is enabled from the start. Held here rather than read back from DataStore: selectLanguage() applies
    // the locale immediately, which recreates the Activity, and this ViewModel (unlike the
    // composition) survives that recreation, so the pick isn't lost.
    private val _selectedLanguageTag = MutableStateFlow<String?>(null)
    val selectedLanguageTag: StateFlow<String?> = _selectedLanguageTag.asStateFlow()

    // The native ad slot (loading, single-request guard, 30s auto-refresh, no-flash swaps — see
    // InlineNativeAdController). Owned here, not in the composition, for the same recreation
    // reason: the old ad stays bound across the locale switch's Activity recreate instead of
    // being dropped and re-requested.
    val nativeAdController = InlineNativeAdController(
        context = application,
        scope = viewModelScope,
        logTag = TAG,
        isPlacementEnabled = { RemoteConfigManager.isLanguageNativeAdEnabled },
        adUnitId = { RemoteConfigManager.languageNativeAdUnitId },
    )

    private var reloadJob: Job? = null

    init {
        if (isFromSettings) {
            viewModelScope.launch {
                val savedTag = repository.selectedLanguageTag(getApplication()).first()
                // Set directly (not via selectLanguage): this is the existing choice, not a new
                // selection — no locale re-apply, no re-persist, no ad reload. Skipped if the user
                // already tapped something in the meantime.
                if (_selectedLanguageTag.value == null) _selectedLanguageTag.value = savedTag
            }
        }
        nativeAdController.start()
    }

    /**
     * Applies the locale synchronously first — AppCompatDelegate.setApplicationLocales()
     * synchronously recreates every live AppCompatActivity itself (MainActivity/CallEndedActivity
     * are both AppCompatActivity), so there's no need for callers to trigger recreate() manually —
     * then persists the choice, the actual suspend/IO part, to DataStore.
     */
    fun selectLanguage(languageTag: String) {
        if (_selectedLanguageTag.value == languageTag) return
        _selectedLanguageTag.value = languageTag
        LanguagePreferencesRepository.applyLocale(languageTag)
        viewModelScope.launch { repository.setSelectedLanguageTag(getApplication(), languageTag) }
        scheduleAdReload()
    }

    override fun onCleared() {
        reloadJob?.cancel()
        nativeAdController.clear()
    }

    // A new selection gets a new ad — debounced, so rapid taps only reload for the final pick.
    // The controller queues it if a request (e.g. a 30s refresh) is already in flight.
    private fun scheduleAdReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(AD_RELOAD_DEBOUNCE_MILLIS)
            nativeAdController.reload()
        }
    }
}
