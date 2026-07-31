package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Shared just-in-time permission bottom sheet: plays [lottieAsset] (an asset under
 * app/src/main/assets, same convention as the onboarding carousel's art) once with no loop, then
 * offers a primary button and a secondary "Not now" (dismiss only, via [onNotNow]).
 *
 * Always shown on first visit to its screen — regardless of current grant state, since e.g.
 * RoleManager's dialer-role prompt can silently auto-grant Contacts/Call Log/Phone on some
 * devices, and the popup should still get its one-time appearance even then (once-per-install
 * gating lives in the caller, via [callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository]).
 * [permissions] is re-checked live at render time to decide the primary button: if every listed
 * permission is already granted, it reads "Got it" and just dismisses (same as [onNotNow] — there
 * is nothing left to request); otherwise it reads "Allow access" and fires the real runtime
 * permission request via [onAllowAccess]. The Lottie/heading/subtext never change between the two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionPreviewBottomSheet(
    lottieAsset: String,
    heading: String,
    subtext: String,
    permissions: Array<String>,
    onAllowAccess: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val alreadyGranted = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    // skipPartiallyExpanded so the sheet always opens straight to its full (Expanded) height
    // instead of resting at a half-screen PartiallyExpanded anchor — see PermissionPreviewBottomSheet
    // investigation: without this, show() rests at PartiallyExpanded whenever the content is taller
    // than half the screen, hiding the buttons until the user drags the sheet up.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onNotNow,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Fixed total height, capped so the sheet always fits on-screen with no scrolling: a
        // bounded slot for the Lottie (ContentScale.Fit keeps it from ever growing past that slot,
        // regardless of the composition's native aspect ratio), a flexible slot that centers the
        // heading/subtext, and a fixed slot for the buttons — pinned visible at the bottom.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val composition by rememberLottieComposition(LottieCompositionSpec.Asset(lottieAsset))
            val progress by animateLottieCompositionAsState(composition = composition, iterations = 1)
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.onSurfaceSoft,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = if (alreadyGranted) onNotNow else onAllowAccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = if (alreadyGranted) stringResource(R.string.permission_got_it) else stringResource(R.string.permission_allow_access),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.permission_not_now), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
