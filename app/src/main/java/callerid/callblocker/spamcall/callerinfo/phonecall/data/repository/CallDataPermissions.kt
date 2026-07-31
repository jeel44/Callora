package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Central place to check the runtime permissions the contacts/call-log repositories need. */
object CallDataPermissions {
    fun hasContacts(context: Context): Boolean = isGranted(context, Manifest.permission.READ_CONTACTS)

    // A separate dangerous permission from READ_CONTACTS — only needed for Contact Detail's real
    // delete-contact action, so it's requested just-in-time there rather than during onboarding.
    fun hasWriteContacts(context: Context): Boolean = isGranted(context, Manifest.permission.WRITE_CONTACTS)

    fun hasCallLog(context: Context): Boolean = isGranted(context, Manifest.permission.READ_CALL_LOG)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
