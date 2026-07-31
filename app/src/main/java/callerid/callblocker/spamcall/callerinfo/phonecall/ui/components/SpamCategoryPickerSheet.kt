package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

@Composable
private fun SpamCategory.displayLabel(): String = when (this) {
    SpamCategory.TELEMARKETING -> stringResource(R.string.spam_category_telemarketing)
    SpamCategory.SCAM -> stringResource(R.string.spam_category_scam)
    SpamCategory.ROBOCALL -> stringResource(R.string.spam_category_robocall)
    SpamCategory.FRAUD -> stringResource(R.string.spam_category_fraud)
    SpamCategory.OTHER -> stringResource(R.string.spam_category_other)
}

/**
 * Shared category picker for both Report-spam entry points (Contact Detail's unknown-number
 * variant, After-Call) so the two callers only need to react to [onCategorySelected] the same
 * way: mark via SpamMarkRepository, then dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamCategoryPickerSheet(
    onDismissRequest: () -> Unit,
    onCategorySelected: (SpamCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.spam_category_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SpamCategory.values().forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.spam,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = category.displayLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
