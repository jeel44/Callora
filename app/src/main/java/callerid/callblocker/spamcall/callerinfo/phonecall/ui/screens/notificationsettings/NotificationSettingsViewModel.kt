package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notificationsettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.NotificationPreferences
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NotificationPreferencesRepository()

    val uiState: StateFlow<NotificationPreferences> = repository.preferences(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPreferences())

    fun setMissedCallsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMissedCallsEnabled(getApplication(), enabled) }
    }

    fun setSpamAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSpamAlertsEnabled(getApplication(), enabled) }
    }

    fun setAppUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAppUpdatesEnabled(getApplication(), enabled) }
    }
}
