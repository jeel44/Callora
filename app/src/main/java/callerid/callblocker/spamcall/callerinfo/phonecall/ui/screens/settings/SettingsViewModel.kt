package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.settings

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.AfterCallPreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.DEFAULT_LANGUAGE_TAG
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.LanguagePreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val ownerName: String? = null,
    val ownerPhoneNumber: String? = null,
)

private val defaultLanguageDisplayName: String =
    SupportedLanguages.first { it.languageTag == DEFAULT_LANGUAGE_TAG }.displayName

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()
    private val languagePreferencesRepository = LanguagePreferencesRepository()
    private val spamMarkRepository = SpamMarkRepository()
    private val afterCallPreferencesRepository = AfterCallPreferencesRepository()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Continuously observed (not a one-shot init read like uiState above) since the Language row
    // must reflect a change made on LanguageSelectScreen immediately on returning to this screen —
    // navigating back reuses this same ViewModel instance rather than recreating it.
    val languageDisplayName: StateFlow<String> = languagePreferencesRepository.selectedLanguageTag(application)
        .map { tag -> SupportedLanguages.find { it.languageTag == tag }?.displayName ?: defaultLanguageDisplayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultLanguageDisplayName)

    // Spam Center row's badge: how many numbers this device has marked as spam — the same
    // SpamMarkRepository data Spam Center builds its spam-call list from. Observed live so a
    // mark/unmark made in Spam Center shows up on returning here.
    val spamMarkedCount: StateFlow<Int> = spamMarkRepository.spamMarkedNumbersFlow(application)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val afterCallScreenEnabled: StateFlow<Boolean> = afterCallPreferencesRepository.afterCallScreenEnabled(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAfterCallScreenEnabled(enabled: Boolean) {
        viewModelScope.launch {
            afterCallPreferencesRepository.setAfterCallScreenEnabled(getApplication(), enabled)
        }
    }

    init {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val ownerName = withContext(Dispatchers.IO) { contactsRepository.getOwnerDisplayName(context) }
            val ownerPhoneNumber = withContext(Dispatchers.IO) { getDevicePhoneNumber(context) }
            _uiState.value = SettingsUiState(ownerName = ownerName, ownerPhoneNumber = ownerPhoneNumber)
        }
    }

    /**
     * TelephonyManager.line1Number is unreliable by design — carriers frequently withhold it
     * even when READ_PHONE_NUMBERS is granted. That permission (not READ_PHONE_STATE) is what's
     * actually required on API 26+; it's requested together with READ_PHONE_STATE in Home's
     * call-log permission bundle. Treat any missing permission or blank result as "unavailable"
     * so the UI can omit the number instead of showing something fake.
     */
    private fun getDevicePhoneNumber(context: Context): String? {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return null
        return try {
            telephonyManager.line1Number?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        }
    }
}
