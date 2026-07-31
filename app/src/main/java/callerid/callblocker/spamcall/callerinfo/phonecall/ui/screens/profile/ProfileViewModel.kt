package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val isLoaded: Boolean = false,
)

/** Reads the device's own "me" contact (ContactsContract.Profile) via [ContactsRepository.getOwnerProfile]. */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val profile = withContext(Dispatchers.IO) { contactsRepository.getOwnerProfile(context) }
            _uiState.value = ProfileUiState(
                displayName = profile.displayName,
                phoneNumber = profile.phoneNumber,
                isLoaded = true,
            )
        }
    }
}
