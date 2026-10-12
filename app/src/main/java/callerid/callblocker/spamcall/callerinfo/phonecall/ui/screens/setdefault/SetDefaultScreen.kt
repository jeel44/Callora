package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SetupPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraAccent
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraPrimary
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.DarkOnSurfaceSoft
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultCardBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultHeroBottom
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultHeroGlow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultHeroTop
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultInk
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultMutedGray
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultRowBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultRowBorder
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultSpamAvatarEnd
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.SetDefaultSpamAvatarStart
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Fraction of screen height claimed by the dark hero vs. the light content card. They sum to
// more than 1f on purpose — the card is bottom-aligned on top of the hero, so the overlap
// (here, 6%) is what produces the card's shadow-over-dark-background seam.
private const val HeroHeightFraction = 0.56f
private const val ContentCardHeightFraction = 0.50f

// A role dialog the system suppressed still takes a second or two to report back (measured
// ~1.3s on an emulator); anything answered faster than this wasn't really read by the user.
private const val ROLE_DIALOG_SUPPRESSED_MILLIS = 3_000L

// See keepPermissionsLauncher in SetDefaultScreen.
private val PermissionsToKeepWithoutRole = listOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_CALL_LOG,
)

private val ContentCardTopCorner = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * The mandatory gate in front of the whole app: shown straight after Splash on first run, and on
 * every later launch for as long as the DIALER role isn't held (see CalloraNavHost's
 * startDestinationFor). There is deliberately no skip path — [onContinue] only ever fires once
 * the role is really held (or can't be offered on this device at all).
 *
 * Two steps, in this order:
 *  1. DIALER role — required.
 *  2. CALL_SCREENING role — requested once, right after step 1. Holding it separately means
 *     CalloraCallScreeningService keeps screening calls even if the user later hands the default
 *     phone app back to another dialer. Optional: declining it still continues into the app.
 *  3. "Display over other apps" — requested once, right after step 2. It is what lets
 *     PostCallPhoneStateReceiver open the call-ended screen from the background when this app is
 *     no longer the default phone app. Optional as well.
 */
@Composable
fun SetDefaultScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dialerRequiredMessage = stringResource(R.string.setdefault_required_message)

    // Steps 2 and 3's results are never gated on: granted or declined, the app is usable.
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(context)) {
            AnalyticsHelper.logEvent("set_overlay_permission")
        }
        onContinue()
    }

    val continueAfterCallScreeningRole = {
        requestOverlayPermission(context, overlayPermissionLauncher, onNotNeeded = onContinue)
    }

    val callScreeningRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (isCallScreeningRoleHeld(context)) {
            AnalyticsHelper.logEvent("set_call_screening_role")
        }
        continueAfterCallScreeningRole()
    }

    val continueAfterDialerRole = {
        requestCallScreeningRole(context, callScreeningRequestLauncher, onNotNeeded = continueAfterCallScreeningRole)
    }

    // Moves on to steps 2/3 the first time the dialer role is seen held, whichever way that is
    // noticed: already held on entry, the role dialog's result, or coming back to this screen
    // from system Settings (see the fallback below).
    var proceeded by remember { mutableStateOf(false) }
    val proceedIfDialerRoleHeld = {
        if (!proceeded && isDialerRoleHeld(context)) {
            proceeded = true
            continueAfterDialerRole()
        }
    }

    // Checked once per entry to this screen, not re-derived on recomposition. Already default
    // and nothing left to ask (e.g. relaunched mid-onboarding): skip straight past without
    // rendering anything. Already default but steps 2/3 never requested (an install that became
    // default before they existed): go straight to them.
    val dialerRoleHeldOnEntry = remember { isDialerRoleHeld(context) }
    val followUpPendingOnEntry = remember { hasPendingSetupRequests(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) proceedIfDialerRoleHeld()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var roleRequestedAtMillis by remember { mutableStateOf(0L) }
    var roleRefusals by remember { mutableStateOf(0) }
    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system role-request sheet's ActivityResult only says the picker was dismissed, not
        // that DIALER was actually granted (a user can back out or pick a different app and this
        // callback still fires) — so re-checking isDialerRoleHeld here, the same real RoleManager
        // confirmation resolveStartDestination uses, is what makes this the real grant point
        // rather than just "the request round-trip completed". Not granted: stay on this screen.
        if (isDialerRoleHeld(context)) {
            AnalyticsHelper.logEvent("set_default_dialer")
            proceedIfDialerRoleHeld()
        } else {
            Toast.makeText(context, dialerRequiredMessage, Toast.LENGTH_LONG).show()
            // Came back without the user having had time to answer: the system refuses to show
            // the dialog any more ("Don't ask again" was ticked earlier). The only way left to
            // become default is the system's own Default apps page — without sending the user
            // there this mandatory screen would be a dead end. A second refusal goes there too,
            // in case the timing guess misses on a slow device.
            roleRefusals++
            if (roleRefusals >= 2 ||
                SystemClock.elapsedRealtime() - roleRequestedAtMillis < ROLE_DIALOG_SUPPRESSED_MILLIS
            ) {
                openDefaultAppsSettings(context)
            }
        }
    }

    val launchDialerRoleRequest = {
        roleRequestedAtMillis = SystemClock.elapsedRealtime()
        // A device that can't offer the role at all (no telephony) can't use a phone app either,
        // so it stays on this screen like any other refusal.
        requestDialerRole(
            context,
            roleRequestLauncher,
            onUnavailable = { Toast.makeText(context, dialerRequiredMessage, Toast.LENGTH_LONG).show() },
        )
    }

    // Asked BEFORE the role, on purpose. The dialer role grants these permissions by itself, but
    // a permission that only the role granted is taken away again the moment the user picks
    // another default phone app — and with them goes everything the not-default call-ended
    // screen needs (CallMonitorService can't hear calls, the call log can't be read). Granted by
    // the user first, they are the user's own grant and survive losing the role. Allowed or
    // denied, the role request follows either way.
    val keepPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { launchDialerRoleRequest() }

    if (dialerRoleHeldOnEntry && !followUpPendingOnEntry) return

    // No BackHandler on purpose: as the start destination, Back exits the app (the role is
    // required to get any further); reached from Blocked List, Back just returns there.

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SetDefaultCardBackground),
    ) {
        SetDefaultHero(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(HeroHeightFraction)
                .align(Alignment.TopStart),
        )

        SetDefaultContentCard(
            onSetDefaultClick = {
                if (isDialerRoleHeld(context)) {
                    proceedIfDialerRoleHeld()
                } else {
                    val missingPermissions = PermissionsToKeepWithoutRole.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missingPermissions.isEmpty()) {
                        launchDialerRoleRequest()
                    } else {
                        keepPermissionsLauncher.launch(missingPermissions.toTypedArray())
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(ContentCardHeightFraction)
                .align(Alignment.BottomStart),
        )
    }
}

/** Dark gradient hero showing two static mock call rows — a live preview of what the call
 * log looks like once Callora is the default dialer. */
@Composable
private fun SetDefaultHero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RectangleShape)
            .drawWithCache {
                // 160deg diagonal (CSS-style angle, clockwise from straight up).
                val angle = Math.toRadians(160.0)
                val dx = sin(angle).toFloat()
                val dy = -cos(angle).toFloat()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val halfDiagonal = hypot(size.width, size.height) / 2f
                val brush = Brush.linearGradient(
                    colors = listOf(SetDefaultHeroTop, SetDefaultHeroBottom),
                    start = Offset(cx - dx * halfDiagonal, cy - dy * halfDiagonal),
                    end = Offset(cx + dx * halfDiagonal, cy + dy * halfDiagonal),
                )
                onDrawBehind { drawRect(brush) }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-70).dp)
                .size(300.dp)
                .background(
                    brush = Brush.radialGradient(colors = listOf(SetDefaultHeroGlow, Color.Transparent)),
                    shape = CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            MockCallRow(
                avatar = { InitialsAvatar(initials = "RS") },
                title = stringResource(R.string.setdefault_mock_name),
                titleColor = Color.White,
                subtitle = "+91 98765 43210",
                badgeText = stringResource(R.string.setdefault_mock_badge_idd),
                badgeColor = MaterialTheme.extendedColors.success,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MockCallRow(
                avatar = { SpamAvatar() },
                title = stringResource(R.string.setdefault_mock_spam_title),
                titleColor = MaterialTheme.extendedColors.spam,
                subtitle = "+91 70000 12345",
                badgeText = stringResource(R.string.setdefault_mock_badge_blocked),
                badgeColor = MaterialTheme.extendedColors.spam,
            )
        }
    }
}

/** Static mock call-list row, styled after the app's real call rows (see
 * `HomeScreen.kt::RecentCallRow`) but non-interactive preview content. */
@Composable
private fun MockCallRow(
    avatar: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SetDefaultRowBackground)
            .border(width = 1.dp, color = SetDefaultRowBorder, shape = RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = DarkOnSurfaceSoft,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        MockBadge(text = badgeText, tintColor = badgeColor)
    }
}

@Composable
private fun InitialsAvatar(initials: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(CalloraPrimary, CalloraAccent))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun SpamAvatar() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(SetDefaultSpamAvatarStart, SetDefaultSpamAvatarEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MockBadge(text: String, tintColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tintColor.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tintColor,
        )
    }
}

/** Light content card overlapping the dark hero, holding the pitch copy, feature list, and
 * the CTA that actually requests the DIALER role. */
@Composable
private fun SetDefaultContentCard(onSetDefaultClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = ContentCardTopCorner)
            .clip(ContentCardTopCorner)
            .background(SetDefaultCardBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.setdefault_card_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, letterSpacing = (-0.3).sp),
            fontWeight = FontWeight.Bold,
            color = SetDefaultInk,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.setdefault_card_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
            color = SetDefaultMutedGray,
        )

        Spacer(modifier = Modifier.height(20.dp))

        FeatureRow(text = stringResource(R.string.setdefault_feature_realtime_id))
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow(text = stringResource(R.string.setdefault_feature_spam_blocked))
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow(text = stringResource(R.string.setdefault_feature_keep_contacts))

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSetDefaultClick,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SetDefaultInk,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(vertical = 17.dp),
        ) {
            Text(
                text = stringResource(R.string.blocked_list_set_as_default),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(CalloraAccent),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
            fontWeight = FontWeight.Bold,
            color = SetDefaultInk,
        )
    }
}

/** Whether this app is the default phone app: the DIALER role on API 29+, TelecomManager's
 * default dialer package below that (RoleManager doesn't exist there).
 * Internal (not private) so [callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation.CalloraNavHost]
 * can reuse this exact check when deciding the nav start destination on each launch, rather than
 * duplicating the RoleManager call. */
internal fun isDialerRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return false
        return telecomManager.defaultDialerPackage == context.packageName
    }
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
}

/**
 * Requests the DIALER role — real call-blocking via BlockedNumberContract requires the app
 * to be the default Phone app (CALL_SCREENING role alone throws a SecurityException). API 26-28
 * has no RoleManager and goes through TelecomManager's change-default-dialer dialog instead.
 * A device/OEM build where the role isn't offered at all (e.g. no telephony) reports
 * [onUnavailable] instead of leaving the button inert.
 */
private fun requestDialerRole(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    onUnavailable: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        ) {
            launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            onUnavailable()
        }
    } else {
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        if (intent.resolveActivity(context.packageManager) != null) {
            launcher.launch(intent)
        } else {
            onUnavailable()
        }
    }
}

/** The system's own "Default apps" page — see SetDefaultScreen's suppressed-dialog fallback. */
private fun openDefaultAppsSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        // No such page on this device — nothing further to offer.
    }
}

/** The CALL_SCREENING role only exists on API 29+. */
internal fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

/**
 * True while the one-time CALL_SCREENING request is still owed: the role exists on this device,
 * isn't held, and this install has never been asked (see [SetupPromptRepository]).
 * Internal so CalloraNavHost's start-destination check can route an already-default install
 * through this screen once to get asked.
 */
internal fun shouldRequestCallScreeningRole(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
        !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) &&
        !SetupPromptRepository.hasCallScreeningRoleBeenRequested(context)
}

/** Launches the system CALL_SCREENING role dialog if [shouldRequestCallScreeningRole], recording
 * that it was asked; otherwise [onNotNeeded]. */
private fun requestCallScreeningRole(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    onNotNeeded: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && shouldRequestCallScreeningRole(context)) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        SetupPromptRepository.markCallScreeningRoleRequested(context)
        launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    } else {
        onNotNeeded()
    }
}

/** True while the one-time "display over other apps" request is still owed: not granted, and
 * this install has never been asked. */
internal fun shouldRequestOverlayPermission(context: Context): Boolean =
    !Settings.canDrawOverlays(context) && !SetupPromptRepository.hasOverlayPermissionBeenRequested(context)

/** Whether Set Default still has an optional follow-up request to make — what sends an
 * already-default install through this screen once (see CalloraNavHost's startDestinationFor). */
internal fun hasPendingSetupRequests(context: Context): Boolean =
    shouldRequestCallScreeningRole(context) || shouldRequestOverlayPermission(context)

/** Opens the system "display over other apps" page for this app if [shouldRequestOverlayPermission],
 * recording that it was asked; otherwise [onNotNeeded]. It's a Settings page rather than a
 * dialog, so a toast says why the user just landed there. */
private fun requestOverlayPermission(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    onNotNeeded: () -> Unit,
) {
    if (!shouldRequestOverlayPermission(context)) {
        onNotNeeded()
        return
    }
    SetupPromptRepository.markOverlayPermissionRequested(context)
    try {
        launcher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        )
        Toast.makeText(context, R.string.setdefault_overlay_permission_message, Toast.LENGTH_LONG).show()
    } catch (e: ActivityNotFoundException) {
        onNotNeeded()
    }
}
