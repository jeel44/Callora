package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.contactdetail

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsChangeSignal
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallHistoryEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamMark
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactDetailUiState(
    val phoneNumber: String = "",
    val contact: Contact? = null,
    val isBlocked: Boolean = false,
    val callHistory: List<CallHistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val showReportSheet: Boolean = false,
    val showOverflowMenu: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val spamMark: SpamMark? = null,
    val snackbarMessage: String? = null,
)

class ContactDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()
    private val blockedNumbersRepository = BlockedNumbersRepository()
    private val callLogRepository = CallLogRepository()
    private val spamMarkRepository = SpamMarkRepository()

    private val phoneNumber: String = Uri.decode(
        checkNotNull(savedStateHandle.get<String>("phoneNumber")) { "contact_detail requires a phoneNumber nav argument" },
    )

    private val _uiState = MutableStateFlow(ContactDetailUiState(phoneNumber = phoneNumber))
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val contact = withContext(Dispatchers.IO) {
                contactsRepository.getContactByPhoneNumber(context, phoneNumber, includeDetails = true)
            }
            val isBlocked = withContext(Dispatchers.IO) {
                blockedNumbersRepository.isNumberBlocked(context, phoneNumber)
            }
            val callHistory = withContext(Dispatchers.IO) {
                callLogRepository.getCallHistoryForNumber(context, phoneNumber, limit = 20)
            }
            val spamMark = withContext(Dispatchers.IO) {
                spamMarkRepository.getSpamMark(context, phoneNumber)
            }
            _uiState.value = _uiState.value.copy(
                contact = contact,
                isBlocked = isBlocked,
                callHistory = callHistory,
                spamMark = spamMark,
                isLoading = false,
            )
        }
    }

    /** Re-checked on resume — blocking happens on the separate block_confirm screen, so the
     * blocked state here can change without this screen having done anything itself. */
    fun refreshBlockedState() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val isBlocked = withContext(Dispatchers.IO) {
                blockedNumbersRepository.isNumberBlocked(context, phoneNumber)
            }
            _uiState.value = _uiState.value.copy(isBlocked = isBlocked)
        }
    }

    /** Unblocking, unlike blocking, needs no confirmation step — called directly from the action button. */
    fun unblockNumber() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val unblocked = withContext(Dispatchers.IO) {
                blockedNumbersRepository.unblockNumber(context, phoneNumber)
            }
            if (unblocked) {
                _uiState.value = _uiState.value.copy(isBlocked = false)
            }
        }
    }

    fun onReportClicked() {
        _uiState.value = _uiState.value.copy(showReportSheet = true)
    }

    fun onReportSheetDismissed() {
        _uiState.value = _uiState.value.copy(showReportSheet = false)
    }

    fun onReportCategorySelected(category: SpamCategory) {
        _uiState.value = _uiState.value.copy(showReportSheet = false)
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { spamMarkRepository.markAsSpam(context, phoneNumber, category) }
            val spamMark = withContext(Dispatchers.IO) { spamMarkRepository.getSpamMark(context, phoneNumber) }
            _uiState.value = _uiState.value.copy(
                spamMark = spamMark,
                snackbarMessage = context.getString(R.string.contact_detail_snackbar_marked_spam),
            )
        }
    }

    fun onUnmarkSpamClicked() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { spamMarkRepository.unmarkAsSpam(context, phoneNumber) }
            _uiState.value = _uiState.value.copy(
                spamMark = null,
                snackbarMessage = context.getString(R.string.contact_detail_snackbar_removed_spam),
            )
        }
    }

    /** One-tap overflow-menu toggle — unlike [onReportClicked]'s flow, this skips the category
     * picker entirely, so a fresh mark always uses [SpamCategory.OTHER]. Available for both saved
     * and unsaved numbers since spam-marking only needs the phone number. */
    fun onToggleSpamMarkClicked() {
        val currentlyMarked = _uiState.value.spamMark != null
        _uiState.value = _uiState.value.copy(showOverflowMenu = false)
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (currentlyMarked) {
                withContext(Dispatchers.IO) { spamMarkRepository.unmarkAsSpam(context, phoneNumber) }
                _uiState.value = _uiState.value.copy(
                    spamMark = null,
                    snackbarMessage = context.getString(R.string.contact_detail_snackbar_removed_spam),
                )
            } else {
                withContext(Dispatchers.IO) { spamMarkRepository.markAsSpam(context, phoneNumber, SpamCategory.OTHER) }
                val spamMark = withContext(Dispatchers.IO) { spamMarkRepository.getSpamMark(context, phoneNumber) }
                _uiState.value = _uiState.value.copy(
                    spamMark = spamMark,
                    snackbarMessage = context.getString(R.string.contact_detail_snackbar_marked_spam),
                )
            }
        }
    }

    fun onWhatsAppUnavailable() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            snackbarMessage = context.getString(R.string.contact_detail_whatsapp_not_installed),
        )
    }

    fun onVideoCallUnavailable() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            snackbarMessage = context.getString(R.string.contact_detail_video_not_supported),
        )
    }

    fun snackbarMessageShown() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun onOverflowMenuOpened() {
        _uiState.value = _uiState.value.copy(showOverflowMenu = true)
    }

    fun onOverflowMenuDismissed() {
        _uiState.value = _uiState.value.copy(showOverflowMenu = false)
    }

    fun onCopyNameClicked() {
        val name = _uiState.value.contact?.name ?: return
        val context = getApplication<Application>()
        copyToClipboard(context.getString(R.string.contact_detail_clip_label_name), name)
        _uiState.value = _uiState.value.copy(showOverflowMenu = false)
        showCopiedConfirmation(R.string.contact_detail_snackbar_name_copied)
    }

    fun onCopyNumberClicked() {
        val context = getApplication<Application>()
        copyToClipboard(context.getString(R.string.contact_detail_clip_label_number), phoneNumber)
        _uiState.value = _uiState.value.copy(showOverflowMenu = false)
        showCopiedConfirmation(R.string.contact_detail_snackbar_number_copied)
    }

    /** Long-press on the number under the name (Google Phone behaviour). */
    fun onNumberLongPressed() {
        val context = getApplication<Application>()
        copyToClipboard(context.getString(R.string.contact_detail_clip_label_number), phoneNumber)
        showCopiedConfirmation(R.string.contact_detail_snackbar_number_copied)
    }

    fun onCopyContactClicked() {
        val name = _uiState.value.contact?.name
        val formattedNumber = formatPhoneNumberForDisplay(phoneNumber)
        val text = if (name != null) "$name, $formattedNumber" else formattedNumber
        val context = getApplication<Application>()
        copyToClipboard(context.getString(R.string.contact_detail_clip_label_contact), text)
        _uiState.value = _uiState.value.copy(showOverflowMenu = false)
        showCopiedConfirmation(R.string.contact_detail_snackbar_contact_copied)
    }

    private fun copyToClipboard(label: String, text: String) {
        val context = getApplication<Application>()
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Android 13+ already shows its own system clipboard confirmation after every copy, so the
     * app's snackbar is only shown below that — otherwise the two would double up.
     */
    private fun showCopiedConfirmation(@StringRes messageRes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val message = getApplication<Application>().getString(messageRes)
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    /** Opens the confirmation dialog — deletion never happens directly off the menu tap. */
    fun onDeleteClicked() {
        _uiState.value = _uiState.value.copy(showOverflowMenu = false, showDeleteConfirmDialog = true)
    }

    fun onDeleteCancelled() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmDialog = false)
    }

    /**
     * Real deletion against the system Contacts provider via [ContactsRepository.deleteContact] —
     * the same provider [init] already reads this contact from. Requires WRITE_CONTACTS, which
     * the screen checks/requests before calling this (see ContactDetailScreen's permission
     * launcher) — this function assumes that's already been granted by the time it's called.
     */
    fun onDeleteConfirmed() {
        val contactId = _uiState.value.contact?.contactId ?: return
        _uiState.value = _uiState.value.copy(isDeleting = true)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val deleted = withContext(Dispatchers.IO) { contactsRepository.deleteContact(context, contactId) }
            if (deleted) ContactsChangeSignal.pendingRefresh = true
            _uiState.value = if (deleted) {
                _uiState.value.copy(showDeleteConfirmDialog = false, isDeleting = false, deleted = true)
            } else {
                _uiState.value.copy(
                    showDeleteConfirmDialog = false,
                    isDeleting = false,
                    snackbarMessage = context.getString(R.string.contact_detail_snackbar_delete_failed),
                )
            }
        }
    }
}
