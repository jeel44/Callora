package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context

private const val CALL_NOTES_PREFS = "call_notes_prefs"

/**
 * The note a user keeps about a number, written from the call-ended screen. One note per number
 * (keyed by its normalized form, same normalization as [SpamMarkRepository]), local to this
 * device like spam marks. Plain SharedPreferences — a handful of short strings. Blocking reads:
 * call from Dispatchers.IO.
 */
class CallNotesRepository(private val spamMarkRepository: SpamMarkRepository = SpamMarkRepository()) {

    private fun prefs(context: Context) = context.getSharedPreferences(CALL_NOTES_PREFS, Context.MODE_PRIVATE)

    fun getNote(context: Context, phoneNumber: String): String =
        prefs(context).getString(spamMarkRepository.normalize(phoneNumber), null).orEmpty()

    /** A blank [note] removes the saved one. */
    fun setNote(context: Context, phoneNumber: String, note: String) {
        val key = spamMarkRepository.normalize(phoneNumber)
        val trimmed = note.trim()
        prefs(context).edit().apply {
            if (trimmed.isEmpty()) remove(key) else putString(key, trimmed)
        }.apply()
    }
}
