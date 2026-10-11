package callerid.callblocker.spamcall.callerinfo.phonecall.ui.util

/** Up to two initials ("Priya Sharma" → "PS"), or '#' when the name has no letters at all —
 * the avatar text used by Contact Info and the ongoing-call notification. */
fun initialsOf(name: String): String {
    val initials = name.split(' ')
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
    return when {
        initials.isEmpty() -> "#"
        initials.size == 1 -> initials.first().toString()
        else -> "${initials.first()}${initials.last()}"
    }
}
