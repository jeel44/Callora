package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "PhoneAccounts"

// Alternating per-slot accent colors — only used when the system reports no usable color of its
// own for a SIM (see getCallCapableAccounts), purely as a visual distinguisher between SIMs.
private val FallbackSimSlotColors = intArrayOf(0xFF4FA3FF.toInt(), 0xFFFF8A65.toInt(), 0xFF81C784.toInt(), 0xFFBA68C8.toInt())

/** One call-capable [android.telecom.PhoneAccount] — the handle, whatever label TelecomManager
 * reports for it (carrier name / "SIM 1" depending on OEM), and the SIM's color as an ARGB int
 * (the system's own per-SIM tint where available and distinct, else a by-position fallback —
 * see [resolveSystemSimColor]). Used by the outgoing-call SIM picker (see SimPickerBottomSheet), by
 * [resolveAccount] to show which SIM an incoming/active call is on, and by CallLogRepository to
 * tag call-log rows with the SIM they were on. */
data class CallAccount(val handle: PhoneAccountHandle, val label: String, val color: Int)

/**
 * Real call-capable phone accounts (one per SIM slot on a dual/multi-SIM device) via
 * [TelecomManager.getCallCapablePhoneAccounts]. Returns an empty list on anything short of the
 * happy path — single-SIM devices, READ_PHONE_STATE not granted (already requested as part of
 * the Home screen's call-log permission bundle, but the user may have denied it), or a
 * SecurityException some OEM build throws anyway — so every caller's existing single-SIM
 * behavior (skip the picker, pass a null account handle) falls out automatically with no
 * separate branch needed.
 */
fun getCallCapableAccounts(context: Context): List<CallAccount> {
    val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
    if (!hasReadPhoneState(context)) return emptyList()
    return try {
        val subscriptions = getActiveSubscriptions(context)
        val handles = telecomManager.getCallCapablePhoneAccounts()
        val phoneAccounts = handles.map { telecomManager.getPhoneAccount(it) }
        val systemColors = handles.mapIndexed { index, handle ->
            resolveSystemSimColor(context, handle, phoneAccounts[index], subscriptions)
        }
        handles.mapIndexed { index, handle ->
            // Some OEMs (e.g. Samsung) report the same tint for every SIM — a color shared with
            // another SIM can't tell them apart, so only a unique system color is used.
            val systemColor = systemColors[index]?.takeIf { color -> systemColors.count { it == color } == 1 }
            CallAccount(
                handle = handle,
                label = phoneAccounts[index]?.label?.toString() ?: handle.id,
                color = systemColor ?: FallbackSimSlotColors[index % FallbackSimSlotColors.size],
            )
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "getCallCapableAccounts: SecurityException reading phone accounts", e)
        emptyList()
    }
}

/**
 * The user's system-wide default SIM for outgoing `tel:` calls (Settings → SIMs → "Calls"), or
 * null when it's set to "Ask every time" / unavailable. Only returned if it's one of [accounts],
 * so a stale default pointing at a removed SIM can never be used to place a call.
 */
fun getDefaultOutgoingAccount(context: Context, accounts: List<CallAccount>): CallAccount? {
    val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return null
    if (!hasReadPhoneState(context)) return null
    val defaultHandle = try {
        telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
    } catch (e: SecurityException) {
        Log.w(TAG, "getDefaultOutgoingAccount: SecurityException reading default account", e)
        null
    } ?: return null
    return accounts.firstOrNull { it.handle == defaultHandle }
}

/** Resolves a real [android.telecom.Call.Details.getAccountHandle] (see CallRepository.onCallAdded)
 * to the same account (label + color) the SIM picker shows — only on a genuinely multi-SIM
 * device. A single-SIM device always returns null here so its call screens show no SIM indicator
 * at all. */
fun resolveAccount(context: Context, handle: PhoneAccountHandle?): CallAccount? {
    if (handle == null) return null
    val accounts = getCallCapableAccounts(context)
    if (accounts.size < 2) return null
    return accounts.firstOrNull { it.handle == handle }
}

/** Matches a call-log row's raw PHONE_ACCOUNT_COMPONENT_NAME/PHONE_ACCOUNT_ID columns against
 * [accounts]. Compared by flattened component + id rather than constructing a
 * [PhoneAccountHandle] (whose equals() also compares the UserHandle the provider doesn't store). */
fun findAccountForCallLog(accounts: List<CallAccount>, componentName: String?, accountId: String?): CallAccount? {
    if (accountId.isNullOrEmpty()) return null
    val component = componentName?.let { ComponentName.unflattenFromString(it) }
    return accounts.firstOrNull { it.handle.id == accountId && (component == null || it.handle.componentName == component) }
}

private fun hasReadPhoneState(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

private fun getActiveSubscriptions(context: Context): List<SubscriptionInfo> = try {
    context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
} catch (e: SecurityException) {
    Log.w(TAG, "getActiveSubscriptions: SecurityException reading subscriptions", e)
    emptyList()
}

/**
 * The SIM's own color as Android tracks it ([SubscriptionInfo.getIconTint] — the color shown in
 * the system's SIM settings / dialer), falling back to [PhoneAccount.getHighlightColor]; null
 * when neither is set (the caller then uses a hardcoded by-position color). The handle → subscription mapping uses
 * [TelephonyManager.getSubscriptionId] on API 30+; below that, telephony's PhoneAccountHandle id
 * is the ICCID on AOSP (subscription id on some OEM builds), so both are tried.
 */
private fun resolveSystemSimColor(
    context: Context,
    handle: PhoneAccountHandle,
    account: PhoneAccount?,
    subscriptions: List<SubscriptionInfo>,
): Int? {
    val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(TelephonyManager::class.java)
            ?.getSubscriptionId(handle)
            ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    } else {
        null
    }
    val subscription = subscriptions.firstOrNull { sub ->
        if (subId != null) {
            sub.subscriptionId == subId
        } else {
            @Suppress("DEPRECATION")
            val iccId = runCatching { sub.iccId }.getOrNull()
            handle.id == sub.subscriptionId.toString() || (!iccId.isNullOrEmpty() && handle.id == iccId)
        }
    }
    subscription?.iconTint?.takeIf { it != 0 }?.let { return it or 0xFF000000.toInt() }
    return account?.highlightColor?.takeIf { it != PhoneAccount.NO_HIGHLIGHT_COLOR }?.let { it or 0xFF000000.toInt() }
}
