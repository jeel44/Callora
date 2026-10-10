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

// Visual separators people type or paste ("+91 98765-43210", "(022) 1234 5678") — ignored when
// deciding what a number is, never part of the number itself.
private val VisualSeparators = Regex("[\\s\\-().]")

// Only ordinary subscriber numbers get +91 added. Toll-free (1800), premium, shared-cost etc. are
// dialed domestically as-is, and prefixing them can make them undialable.
private val AutoPrefixNumberTypes = setOf(
    PhoneNumberUtil.PhoneNumberType.MOBILE,
    PhoneNumberUtil.PhoneNumberType.FIXED_LINE,
    PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE,
)

/**
 * The number to actually dial / match for what the user typed or pasted — the single place the
 * app decides whether a number gets +91, mirroring Google's Phone app:
 *  - starts with "+" (any country code) or "00" (international dialing prefix): returned exactly
 *    as given — never prefixed, never rewritten;
 *  - a complete Indian national number (e.g. "98765 43210", "09876543210", or "919876543210"
 *    typed without the "+"): returned as E.164, "+919876543210";
 *  - "091" + a 10-digit number: the leading 0 dropped, "+91..." — never a stacked "+91091...";
 *  - anything else (partial input, short codes like 112, toll-free, MMI/USSD codes like *#06#):
 *    returned unchanged.
 * Validity and number type come from libphonenumber's India metadata, not prefix guessing.
 */
fun normalizeDialedNumber(input: String): String {
    val compact = input.replace(VisualSeparators, "")
    if (compact.isEmpty() || compact.startsWith("+") || compact.startsWith("00")) return input
    if (!compact.all { it.isDigit() }) return input

    // "091XXXXXXXXXX" is +91 written with a 0 in place of the "+"; libphonenumber would otherwise
    // read that 0 as the domestic trunk prefix and the "91" as the start of the national number.
    val candidate = if (compact.length == 13 && compact.startsWith("091")) "+" + compact.drop(1) else compact
    val phoneUtil = PhoneNumberUtil.getInstance()
    val parsed = try {
        phoneUtil.parse(candidate, DefaultRegion)
    } catch (e: NumberParseException) {
        return input
    }
    val isIndianSubscriberNumber = phoneUtil.getRegionCodeForNumber(parsed) == DefaultRegion &&
        phoneUtil.isValidNumber(parsed) &&
        phoneUtil.getNumberType(parsed) in AutoPrefixNumberTypes
    return if (isIndianSubscriberNumber) phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164) else input
}

/**
 * Formats a phone number for display via libphonenumber — this is the single shared formatting
 * utility; every screen showing a phone number should call this rather than hand-rolling
 * dashes/parens/spaces. Display-only: callers must keep using the raw form (or
 * [normalizeDialedNumber]'s) for dialing, ContactsContract lookups, and call-log matching.
 *
 * Follows [normalizeDialedNumber]'s rules, so a number only ever shows "+91" when it really is an
 * Indian number: bare Indian numbers gain it ("6351000085" -> "+91 63510 00085"), numbers that
 * already carry a country code keep exactly that code, just spaced out ("+14155550100" ->
 * "+1 415-555-0100"), and anything [normalizeDialedNumber] leaves alone without a "+" (00-prefixed,
 * short codes, partial input) is shown exactly as given.
 */
fun formatPhoneNumberForDisplay(number: String): String {
    if (number.isBlank()) return number
    val compact = normalizeDialedNumber(number).replace(VisualSeparators, "")
    if (!compact.startsWith("+")) return number
    val phoneUtil = PhoneNumberUtil.getInstance()
    return try {
        phoneUtil.format(phoneUtil.parse(compact, DefaultRegion), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        number
    }
}

/**
 * What the dialpad's number field shows for [typed]: the +91 form once [normalizeDialedNumber]
 * recognizes a complete Indian number, otherwise exactly what was typed — including any "+" or
 * "00" number, which the dialpad never reformats.
 */
fun formatDialpadInput(typed: String): String {
    val dialed = normalizeDialedNumber(typed)
    return if (dialed != typed && dialed.startsWith("+")) formatPhoneNumberForDisplay(dialed) else typed
}
