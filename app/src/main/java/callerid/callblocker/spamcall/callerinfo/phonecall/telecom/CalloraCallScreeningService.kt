package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import callerid.callblocker.spamcall.callerinfo.phonecall.ads.preloadCallEndAd
import android.os.Bundle
import android.telephony.TelephonyManager
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallDataPermissions
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamRulesPreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import android.telecom.Call
import android.telecom.CallScreeningService
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallerIdentityCache
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// spam_blocked's `source` param: why the call was rejected.
private const val SOURCE_USER_BLOCK = "user_block"
private const val SOURCE_SPAM_FLAG = "spam_flag"
private const val SOURCE_TELEMARKETER_RULE = "telemarketer_rule"
private const val SOURCE_INTERNATIONAL_RULE = "international_rule"

/**
 * Declaring this service (see AndroidManifest.xml) is what makes this app eligible for
 * RoleManager.ROLE_CALL_SCREENING — the system binds it to screen every incoming call once
 * Callora holds that role (implied by holding the default-dialer role, ROLE_DIALER, which
 * SetDefaultScreen requests and is confirmed held on-device).
 *
 * Real screening. Always rejected: numbers on the system BlockedNumberContract list
 * (BlockedNumbersRepository — written to by IncomingCallViewModel.blockCaller and Contact
 * Detail's Block action). On top of that, Spam Center's three blocking rules
 * (SpamRulesPreferencesRepository) decide the rest — see [rejectSourceByRules]. All lookups are
 * ContentResolver/DataStore/SharedPreferences reads, so the response is
 * sent asynchronously rather than blocking onScreenCall's caller thread — the platform allows a
 * CallScreeningService up to 5 seconds to call respondToCall before it assumes an allow.
 */
class CalloraCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blockedNumbersRepository = BlockedNumbersRepository()
    private val spamMarkRepository = SpamMarkRepository()

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        if (number.isNullOrBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        // The system screens a call BEFORE handing it to InCallServiceImpl.onCallAdded, so this is
        // the earliest moment the number is known — warming the contact lookup here (in parallel
        // with the block/spam checks) usually lets CallRepository answer synchronously from
        // CallerIdentityCache when the call is added, so Incoming Call paints the final identity
        // on its first frame instead of a placeholder.
        CallerIdentityCache.prewarm(applicationContext, number)
        // Earliest sign of a call on every path, default phone app or not.
        preloadCallEndAd(reason = "call screened")
        serviceScope.launch {
            val rejectSource = if (blockedNumbersRepository.isNumberBlocked(applicationContext, number)) {
                SOURCE_USER_BLOCK
            } else {
                rejectSourceByRules(number)
            }
            val shouldReject = rejectSource != null
            if (rejectSource != null) {
                // Only real blocks — every allowed call also reaches here but rejectSource is
                // null for those, so this can't fire on every screened call, only actual ones.
                AnalyticsHelper.logEvent(
                    "spam_blocked",
                    Bundle().apply { putString("source", rejectSource) },
                )
            }
            val response = CallResponse.Builder()
                .setDisallowCall(shouldReject)
                .setRejectCall(shouldReject)
                // Blocked/spam calls shouldn't surface a missed-call notification — matches how
                // the system's own call-blocking (and most real dialers) treats a rejected call.
                .setSkipNotification(shouldReject)
                .build()
            respondToCall(callDetails, response)
        }
    }

    /**
     * Spam Center's blocking rules, each only active while its toggle is on:
     *  - "Auto-block top spammers": any number this device marked as spam. There is no
     *    community database behind it — the marks are this user's own reports.
     *  - "Block telemarketers": numbers marked with the Telemarketing category, for users who
     *    turned the broader rule off but still want sales calls gone.
     *  - "Block international spam": a number from another country than this SIM's that isn't a
     *    saved contact. Needs contacts access to tell — without it nothing is blocked, since a
     *    relative abroad would otherwise be rejected too.
     * With all three off, a spam-marked number rings (shown as spam) instead of being rejected.
     *
     * Returns the first matching rule's `source` value for spam_blocked, or null to let the call
     * through.
     */
    private suspend fun rejectSourceByRules(number: String): String? {
        val context = applicationContext
        val spamMark = spamMarkRepository.getSpamMark(context, number)
        if (spamMark != null) {
            if (SpamRulesPreferencesRepository.isAutoBlockTopSpammersEnabled(context)) return SOURCE_SPAM_FLAG
            if (spamMark.category == SpamCategory.TELEMARKETING &&
                SpamRulesPreferencesRepository.isBlockTelemarketersEnabled(context)
            ) {
                return SOURCE_TELEMARKETER_RULE
            }
        }
        return if (SpamRulesPreferencesRepository.isBlockInternationalSpamEnabled(context) &&
            isUnknownInternationalNumber(number)
        ) {
            SOURCE_INTERNATIONAL_RULE
        } else {
            null
        }
    }

    private fun isUnknownInternationalNumber(number: String): Boolean {
        val context = applicationContext
        if (!CallDataPermissions.hasContacts(context)) return false
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        val homeRegion = sequenceOf(telephonyManager?.simCountryIso, telephonyManager?.networkCountryIso)
            .firstOrNull { !it.isNullOrBlank() }
            ?.uppercase()
            ?: return false
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val parsed = try {
            phoneNumberUtil.parse(number, homeRegion)
        } catch (e: NumberParseException) {
            return false
        }
        if (parsed.countryCode == phoneNumberUtil.getCountryCodeForRegion(homeRegion)) return false
        return ContactsRepository().getContactByPhoneNumber(context, number) == null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
