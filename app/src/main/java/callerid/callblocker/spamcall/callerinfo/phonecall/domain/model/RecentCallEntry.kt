package callerid.callblocker.spamcall.callerinfo.phonecall.domain.model

data class RecentCallEntry(
    // The call log provider's own row _ID — a genuinely stable, unique identifier (unlike
    // phoneNumber+timestamp, which could theoretically collide) for LazyColumn's items() key in
    // HomeScreen, so scrolling/recomposition can track each row across list rebuilds instead of
    // falling back to positional identity.
    val id: Long,
    val name: String?,
    val phoneNumber: String,
    // Computed once by CallLogRepository when this entry is first built from the cursor row —
    // libphonenumber parsing isn't free, so this must never be recomputed inline in a row
    // composable on every recomposition/scroll. Survives HomeViewModel's annotateSpam() .copy()
    // calls unchanged since phoneNumber itself never changes for an existing entry.
    val formattedPhoneNumber: String,
    val timestamp: Long,
    val callType: CallType,
    val photoUri: String?,
    // Populated by HomeViewModel via the existing SpamMarkRepository, not by CallLogRepository
    // itself — the call log and spam-marking are separate local data sources.
    val isSpam: Boolean = false,
    // Which SIM the call was on (label + ARGB color, see PhoneAccounts.findAccountForCallLog) —
    // only ever set on a multi-SIM device, so single-SIM rows show no SIM indicator at all.
    val simLabel: String? = null,
    val simColor: Int? = null,
)
