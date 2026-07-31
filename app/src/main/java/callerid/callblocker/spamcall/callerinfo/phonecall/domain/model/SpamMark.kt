package callerid.callblocker.spamcall.callerinfo.phonecall.domain.model

/** This device's own local "I marked this number as spam" record — never synced or shared. */
data class SpamMark(
    val phoneNumber: String,
    val category: SpamCategory,
    val markedAt: Long,
)
