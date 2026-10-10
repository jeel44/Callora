package callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors — identical in light and dark theme.
val CalloraPrimary = Color(0xFF42A5F5)
val CalloraAccent = Color(0xFF2196F3)
val CalloraSpam = Color(0xFFEF5350)
val CalloraSuccess = Color(0xFF4CAF50)
val CalloraWarning = Color(0xFFFFB300)

// Text drawn directly on top of the primary/accent gradient (splash, filled buttons).
val OnCalloraPrimary = Color(0xFFFFFFFF)

// Light theme surfaces & ink.
val LightCanvas = Color(0xFFE7EDF3)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF3F6FA)
val LightOnSurface = Color(0xFF152238)
val LightOnSurfaceSoft = Color(0xFF5B6B84)
val LightOnSurfaceFaint = Color(0xFF93A2B8)
val LightOutline = Color(0xFFDCE4EE)

// Dark theme surfaces & ink.
val DarkCanvas = Color(0xFF0E1622)
val DarkSurface = Color(0xFF161F2E)
val DarkSurfaceVariant = Color(0xFF1D2838)
val DarkOnSurface = Color(0xFFEAF0F8)
val DarkOnSurfaceSoft = Color(0xFF9AABC4)
val DarkOnSurfaceFaint = Color(0xFF5C6C86)
val DarkOutline = Color(0xFF2A374A)

// Flat call-log list (Home, Spam Center): direction arrows, row dividers, muted secondary ink.
val LightCallOutgoing = Color(0xFF4CAF50)
val LightCallIncoming = Color(0xFF6B7280)
val LightCallMissed = Color(0xFFEF5350)
val LightListDivider = Color(0xFFF0F1F3)
val LightMutedText = Color(0xFF8A919B)
val DarkCallOutgoing = Color(0xFF66BB6A)
val DarkCallIncoming = Color(0xFF9AA3AD)
val DarkCallMissed = Color(0xFFFF6B65)
val DarkListDivider = Color(0xFF22262B)
val DarkMutedText = Color(0xFF9AA3AD)

// "Set as default" screen — dark hero call-preview tokens. Values specific to this screen's
// mock UI (not reused elsewhere), so they live here rather than overloading the semantic tokens
// above.
val SetDefaultHeroTop = Color(0xFF14243D)
val SetDefaultHeroBottom = Color(0xFF0F1B2E)
val SetDefaultHeroGlow = Color(0x4742A5F5)
val SetDefaultRowBackground = Color(0xFF182B47)
val SetDefaultRowBorder = Color(0xFF24395C)
val SetDefaultCardBackground = Color(0xFFEEF2F7)
val SetDefaultInk = Color(0xFF101828)
val SetDefaultMutedGray = Color(0xFF475467)
val SetDefaultSpamAvatarStart = Color(0xFFF87171)
val SetDefaultSpamAvatarEnd = Color(0xFFEF4444)
