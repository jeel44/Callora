package callerid.callblocker.spamcall.callerinfo.phonecall.ui.util

import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad.contactMatchDigits
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the dialpad shows, dials and matches contacts on, for each kind of typed number. */
class DialedNumberNormalizationTest {

    private fun assertDialpad(typed: String, shown: String, dialed: String, matched: String) {
        assertEquals("shown for $typed", shown, formatDialpadInput(typed))
        assertEquals("dialed for $typed", dialed, normalizeDialedNumber(typed))
        assertEquals("matched for $typed", matched, contactMatchDigits(typed))
    }

    @Test
    fun plainIndianMobile_getsPlus91() =
        assertDialpad("9876543210", shown = "+91 98765 43210", dialed = "+919876543210", matched = "9876543210")

    @Test
    fun alreadyPlus91_isLeftAsTyped_notDoubled() =
        assertDialpad("+919876543210", shown = "+919876543210", dialed = "+919876543210", matched = "9876543210")

    @Test
    fun usNumber_isNeverGivenPlus91() =
        assertDialpad("+14155550100", shown = "+14155550100", dialed = "+14155550100", matched = "14155550100")

    @Test
    fun doubleZeroInternationalPrefix_isLeftAsTyped() =
        assertDialpad("00919876543210", shown = "00919876543210", dialed = "00919876543210", matched = "00919876543210")

    @Test
    fun zero91Prefix_becomesSinglePlus91() =
        assertDialpad("0919876543210", shown = "+91 98765 43210", dialed = "+919876543210", matched = "9876543210")

    @Test
    fun trunkZeroMobile_becomesPlus91() =
        assertDialpad("09876543210", shown = "+91 98765 43210", dialed = "+919876543210", matched = "9876543210")

    @Test
    fun partialInput_isShownAndDialedAsTyped() =
        assertDialpad("98765", shown = "98765", dialed = "98765", matched = "98765")

    @Test
    fun mmiCodesAndShortCodes_areUntouched() {
        assertEquals("*#06#", normalizeDialedNumber("*#06#"))
        assertEquals("112", normalizeDialedNumber("112"))
        assertEquals("18001234567", normalizeDialedNumber("18001234567"))
    }

    @Test
    fun sharedDisplayFormatter_keepsForeignCountryCode_andNeverStacksPlus91() {
        assertEquals("+1 415-555-0100", formatPhoneNumberForDisplay("+14155550100"))
        assertEquals("+91 98765 43210", formatPhoneNumberForDisplay("9876543210"))
        assertEquals("+91 98765 43210", formatPhoneNumberForDisplay("+91 98765 43210"))
        assertEquals("+91 98765 43210", formatPhoneNumberForDisplay("0919876543210"))
        assertEquals("00919876543210", formatPhoneNumberForDisplay("00919876543210"))
    }
}
