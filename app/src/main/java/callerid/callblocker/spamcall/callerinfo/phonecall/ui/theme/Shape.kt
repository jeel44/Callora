package callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Named corner-radius tokens used directly by screen code, e.g. Modifier.clip(ChipShape).
val ChipShape = RoundedCornerShape(12.dp)
val ListItemShape = RoundedCornerShape(18.dp)
val CardShape = RoundedCornerShape(24.dp)

// Wired into MaterialTheme.shapes so components that pull shapes from the theme
// (Chip, Card, ListItem, dialogs, ...) automatically pick up the same tokens.
val CalloraShapes = Shapes(
    extraSmall = ChipShape,
    small = ChipShape,
    medium = ListItemShape,
    large = CardShape,
    extraLarge = CardShape,
)
