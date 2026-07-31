package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.permissionPromptDataStore by preferencesDataStore(name = "permission_prompt_prefs")

/** Which just-in-time permission bottom sheet (see PermissionPreviewBottomSheet) a
 * [PermissionPromptRepository] flag tracks. */
enum class PermissionPromptKind(val prefsKey: String) {
    CONTACTS("contacts_prompt_shown"),
    CALL_LOG("call_log_prompt_shown"),
    NOTIFICATIONS("notifications_prompt_shown"),
    CALL_PHONE("call_phone_prompt_shown"),
}

/**
 * Tracks whether each contextual permission popup has ever been shown, same DataStore-backed
 * once-ever mechanism as [OnboardingRepository]. [markShown] is called the moment the user
 * responds either way (Allow access or Not now) — once set, the popup never reappears for that
 * permission again on this install, regardless of whether it ends up granted.
 */
class PermissionPromptRepository {

    suspend fun hasBeenShown(context: Context, kind: PermissionPromptKind): Boolean =
        context.permissionPromptDataStore.data.first()[booleanPreferencesKey(kind.prefsKey)] ?: false

    suspend fun markShown(context: Context, kind: PermissionPromptKind) {
        context.permissionPromptDataStore.edit { prefs -> prefs[booleanPreferencesKey(kind.prefsKey)] = true }
    }
}
