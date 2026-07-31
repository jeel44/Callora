package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "PhoneAccounts"

/** One call-capable [android.telecom.PhoneAccount] — just the handle plus whatever label
 * TelecomManager reports for it (carrier name / "SIM 1" depending on OEM), used both by the
 * outgoing-call SIM picker (see SimPickerBottomSheet) and by [resolveAccountLabel] to show which
 * SIM an incoming/active call is on. */
data class CallAccount(val handle: PhoneAccountHandle, val label: String)

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
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return emptyList()
    return try {
        telecomManager.getCallCapablePhoneAccounts().map { handle ->
            val account = telecomManager.getPhoneAccount(handle)
            CallAccount(handle = handle, label = account?.label?.toString() ?: handle.id)
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "getCallCapableAccounts: SecurityException reading phone accounts", e)
        emptyList()
    }
}

/** Resolves a real [Call.Details.getAccountHandle] (see CallRepository.onCallAdded) to the same
 * human-readable label the SIM picker shows — only on a genuinely multi-SIM device. A single-SIM
 * device always returns null here so its call screens show no SIM indicator at all. */
fun resolveAccountLabel(context: Context, handle: PhoneAccountHandle?): String? {
    if (handle == null) return null
    val accounts = getCallCapableAccounts(context)
    if (accounts.size < 2) return null
    return accounts.firstOrNull { it.handle == handle }?.label
}
