package callerid.callblocker.spamcall.callerinfo.phonecall.ui.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Reused across calls instead of DateTimeFormatter.ofPattern(...) inline — that allocates and
// re-parses the pattern string on every invocation. formatRelativeCallTimestamp in particular
// runs from RecentCallRow (HomeScreen's call-log list), so this is on the hot path for every
// row shown during scroll.
private val TimeOfDayFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val ShortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** "10:42 AM" if today, "Yesterday" if yesterday, otherwise a short date like "Jul 20". */
fun formatRelativeCallTimestamp(timestampMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val callDateTime = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val today = LocalDate.now(zone)
    return when (callDateTime.toLocalDate()) {
        today -> callDateTime.format(TimeOfDayFormatter)
        today.minusDays(1) -> "Yesterday"
        else -> callDateTime.format(ShortDateFormatter)
    }
}

/** Day-group header for a list grouped by calendar day, e.g. Contact Detail's Call history
 * section: "Today", "Yesterday", otherwise a short date like "Jul 18". */
fun formatRelativeDayLabel(timestampMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val callDateTime = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val today = LocalDate.now(zone)
    return when (callDateTime.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> callDateTime.format(ShortDateFormatter)
    }
}

/** Clock-time-only formatting, e.g. "10:42 AM" — for rows nested under a day-group header
 * ([formatRelativeDayLabel]) where the day itself is already shown by the group label. */
fun formatTimeOfDay(timestampMillis: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(timestampMillis).atZone(zone).format(TimeOfDayFormatter)
}

/** Numbers with no country code (bare local digits) are assumed to belong to this region. */
private const val DefaultRegion = "IN"

/**
 * Formats a raw phone number for display via libphonenumber — this is the single shared
 * formatting utility; every screen showing a phone number should call this rather than
 * hand-rolling dashes/parens/spaces. Display-only: callers must keep using the raw/E.164 form
 * for dialing, ContactsContract lookups, and call-log matching.
 *
 * A number whose detected country matches [DefaultRegion] is shown in INTERNATIONAL style so
 * the "+91" stays visible (bare local numbers get silently normalized into this same shape via
 * [PhoneNumberUtil.parse]'s defaultRegion fallback, e.g. "6351000085" -> "+91 63510 00085").
 * Anything from another region is shown in that region's own NATIONAL style instead — e.g. a US
 * number already reads as foreign, so repeating "+1" would just be noise: "+16351000085" ->
 * "(635) 100-0085".
 */
fun formatPhoneNumberForDisplay(number: String): String {
    if (number.isBlank()) return number
    val phoneUtil = PhoneNumberUtil.getInstance()
    return try {
        val parsed = phoneUtil.parse(number, DefaultRegion)
        val region = phoneUtil.getRegionCodeForNumber(parsed)
        val format = if (region == DefaultRegion) {
            PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        } else {
            PhoneNumberUtil.PhoneNumberFormat.NATIONAL
        }
        phoneUtil.format(parsed, format)
    } catch (e: NumberParseException) {
        number
    }
}
