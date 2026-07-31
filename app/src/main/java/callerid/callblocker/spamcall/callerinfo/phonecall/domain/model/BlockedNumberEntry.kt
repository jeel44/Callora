package callerid.callblocker.spamcall.callerinfo.phonecall.domain.model

/**
 * [blockedTimestampMillis] is null in practice — BlockedNumberContract exposes no creation-time
 * column publicly, so there's no real timestamp to report (kept nullable rather than fabricated,
 * in case a future OEM/API surface adds one).
 */
data class BlockedNumberEntry(
    val phoneNumber: String,
    val blockedTimestampMillis: Long?,
)
