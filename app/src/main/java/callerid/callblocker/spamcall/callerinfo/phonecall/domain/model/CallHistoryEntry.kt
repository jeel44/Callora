package callerid.callblocker.spamcall.callerinfo.phonecall.domain.model

/** A single past call to/from one specific number — shown on Contact Detail's Call history section. */
data class CallHistoryEntry(
    val phoneNumber: String,
    val timestamp: Long,
    val callType: CallType,
    val durationSeconds: Long,
    // Same as RecentCallEntry.simLabel/simColor — null unless on a multi-SIM device.
    val simLabel: String? = null,
    val simColor: Int? = null,
)
