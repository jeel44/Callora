package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
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

private val ContentCardTopCorner = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@Composable
fun SetDefaultScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Checked once per entry to this screen, not re-derived on recomposition — if the role
    // was already granted (e.g. the app relaunched back into onboarding, or the user is
    // re-entering this route some other way), skip straight past without rendering anything.
    val roleAlreadyHeld = remember { isDialerRoleHeld(context) }
    LaunchedEffect(roleAlreadyHeld) {
        if (roleAlreadyHeld) onContinue()
    }

    // Whether the role is granted, denied, or the sheet is simply dismissed, onboarding
    // always moves forward — the user can revisit this from Settings later.
    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system role-request sheet's ActivityResult only says the picker was dismissed, not
        // that DIALER was actually granted (a user can back out or pick a different app and this
        // callback still fires) — so re-checking isDialerRoleHeld here, the same real RoleManager
        // confirmation resolveStartDestination/roleAlreadyHeld above already use, is what makes
        // this the real grant point rather than just "the request round-trip completed".
        if (isDialerRoleHeld(context)) {
            AnalyticsHelper.logEvent("set_default_dialer")
        }
        onContinue()
    }

    if (roleAlreadyHeld) return

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
            onSetDefaultClick = { requestDialerRole(context, roleRequestLauncher, onUnavailable = onContinue) },
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

/** RoleManager, and the DIALER role specifically, only exist on API 29+.
 * Internal (not private) so [callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation.CalloraNavHost]
 * can reuse this exact check when deciding the nav start destination on each launch, rather than
 * duplicating the RoleManager call. */
internal fun isDialerRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
}

/**
 * Requests the DIALER role — real call-blocking via BlockedNumberContract requires the app
 * to be the default Phone app (CALL_SCREENING role alone throws a SecurityException). Only
 * exists on API 29+, so anything below that — or a device/OEM build where the role isn't
 * offered at all — falls back to [onUnavailable] instead of leaving the button inert.
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
        onUnavailable()
    }
}
