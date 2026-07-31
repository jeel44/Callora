package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Snapshot of every runtime permission Callora cares about, re-derived fresh from
 * ContextCompat on each call — never cached. Used by [callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notificationsettings.NotificationSettingsScreen]
 * to gate its just-in-time notifications permission popup; [SetDefaultScreen]'s dialer-role
 * check is the analogous per-permission check for that flow. */
internal data class PermissionsUiState(
    val contactsGranted: Boolean,
    val phoneGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    fun allRequiredGranted(): Boolean = contactsGranted && phoneGranted && notificationsGranted
}

internal fun queryPermissionsState(context: Context): PermissionsUiState {
    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        isGranted(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

    return PermissionsUiState(
        contactsGranted = isGranted(Manifest.permission.READ_CONTACTS),
        phoneGranted = isGranted(Manifest.permission.READ_PHONE_STATE) &&
            isGranted(Manifest.permission.READ_CALL_LOG) &&
            isGranted(Manifest.permission.CALL_PHONE),
        notificationsGranted = notificationsGranted,
    )
}
