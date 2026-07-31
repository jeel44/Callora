package callerid.callblocker.spamcall.callerinfo.phonecall.domain.model

/**
 * [firestoreKey] values must exactly match allowedCategories() in firestore.rules —
 * the rules reject any category key outside that list.
 */
enum class SpamCategory(val firestoreKey: String) {
    TELEMARKETING("telemarketing"),
    SCAM("scam"),
    ROBOCALL("robocall"),
    FRAUD("fraud"),
    OTHER("other"),
}
