package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds the splash screen's timer so it survives recomposition/rotation. */
class SplashViewModel : ViewModel() {

    private val _isReadyToNavigate = MutableStateFlow(false)
    val isReadyToNavigate: StateFlow<Boolean> = _isReadyToNavigate.asStateFlow()

    init {
        viewModelScope.launch {
            delay(SPLASH_DURATION_MS)
            _isReadyToNavigate.value = true
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1800L
    }
}
