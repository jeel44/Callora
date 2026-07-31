package callerid.callblocker.spamcall.callerinfo.phonecall.domain.model

data class Contact(
    val name: String,
    val phoneNumber: String,
    // Computed once by ContactsRepository when this Contact is first built from the cursor row —
    // libphonenumber parsing isn't free, so row composables (e.g. AllContactsScreen's
    // AllContactRow) must read this instead of calling formatPhoneNumberForDisplay inline on
    // every recomposition/scroll.
    val formattedPhoneNumber: String,
    val photoUri: String?,
    val organization: String? = null,
    val email: String? = null,
    // Only populated by ContactsRepository.getContactByPhoneNumber (Contact Detail's one caller
    // that needs it, for the real delete-contact action) — other query paths (search/all
    // contacts/T9) don't fetch it since nothing there acts on a specific contact row.
    val contactId: Long? = null,
)
