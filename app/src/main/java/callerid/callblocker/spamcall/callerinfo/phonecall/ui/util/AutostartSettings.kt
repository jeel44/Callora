package callerid.callblocker.spamcall.callerinfo.phonecall.ui.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * OEM Android skins that keep an app from being started in the background (by a broadcast, at
 * boot, or after being swiped away) until the user allows "autostart" for it in the OEM's own
 * security app. Without it, call screening and the call-ended screen silently stop working on
 * these phones once the app isn't open. There is no API to read whether it's allowed, nor a
 * standard screen for it — only each OEM's own settings Activity, which moves between OS
 * versions, hence several candidates per brand, tried in order.
 */
private val AutostartSettingsByManufacturer: Map<String, List<ComponentName>> = run {
    val xiaomi = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )
    val oppo = listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
    )
    val vivo = listOf(
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
    )
    val huawei = listOf(
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )
    val onePlus = listOf(
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    ) + oppo
    mapOf(
        "xiaomi" to xiaomi,
        "redmi" to xiaomi,
        "poco" to xiaomi,
        "oppo" to oppo,
        "realme" to oppo,
        "oneplus" to onePlus,
        "vivo" to vivo,
        "iqoo" to vivo,
        "huawei" to huawei,
        "honor" to huawei + listOf(
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
        "asus" to listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
        ),
        "letv" to listOf(
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        ),
        "tecno" to listOf(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
        "infinix" to listOf(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
    )
}

/** Whether this phone's maker is one that gates background starts behind an autostart setting. */
fun deviceNeedsAutostartPermission(): Boolean =
    Build.MANUFACTURER.lowercase() in AutostartSettingsByManufacturer

/**
 * Opens this OEM's autostart screen. Just tries each candidate rather than resolving first —
 * these packages aren't visible to package queries without listing every one in the manifest. If
 * none exists on this OS version, falls back to this app's own system App info page, from where
 * the setting is reachable on every one of these skins.
 */
fun Context.openAutostartSettings() {
    for (component in AutostartSettingsByManufacturer[Build.MANUFACTURER.lowercase()].orEmpty()) {
        try {
            startExternalActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Not this OS version's screen — try the next.
        } catch (e: SecurityException) {
            // Exists but isn't exported on this OS version — try the next.
        }
    }
    try {
        startExternalActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        // Nothing to open on this device.
    }
}
