package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme

/**
 * A row of dots marking progress through a fixed number of pages. The active dot is
 * wider and drawn in the primary color; inactive dots use the outline color.
 */
@Composable
fun PageIndicatorDots(
    pageCount: Int,
    activePage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == activePage
            val width by animateDpAsState(targetValue = if (isActive) 24.dp else 8.dp, label = "dotWidth")
            Row(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
            ) {}
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PageIndicatorDotsPreview() {
    CalloraTheme {
        PageIndicatorDots(pageCount = 3, activePage = 1)
    }
}
