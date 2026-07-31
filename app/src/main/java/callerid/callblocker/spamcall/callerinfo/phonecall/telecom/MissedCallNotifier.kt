package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.NotificationPreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.permissions.queryPermissionsState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Channel id for the missed-call notification created in CalloraApplication.onCreate() — kept
 * here (not duplicated as a literal in both places) since this is the one file that also builds
 * notifications on it. */
const val MISSED_CALL_CHANNEL_ID = "missed_call_channel"

private const val NOTIFICATION_ID_BASE = 2000

// Matches CalloraPrimary/CalloraSpam (ui/theme/Color.kt) — Compose colors aren't reachable from
// this plain object, same rationale as InCallServiceImpl's own AVATAR_BACKGROUND_COLOR constant.
private const val NORMAL_ACCENT_COLOR = 0xFF42A5F5.toInt()
private const val SPAM_ACCENT_COLOR = 0xFFEF5350.toInt()

/**
 * Posts the real missed-call notification — the one notification category that was previously a
 * no-op stub (see NotificationPreferencesRepository/NotificationSettingsScreen's own doc
 * comments). Invoked from [CallRepository.onCallEnded] (wired up in InCallServiceImpl.onCreate),
 * the same hook CallEndedActivity uses, so it fires for every ended call — [notifyIfMissed] below
 * is what narrows that down to genuinely missed ones.
 *
 * A call CalloraCallScreeningService already rejected (blocked/spam-marked number) never reaches
 * here at all: the platform contract for CallScreeningService is that a disallowed call is never
 * handed to InCallService, so it never reaches CallRepository.onCallAdded/onCallRemoved, and
 * therefore never reaches CallRepository.onCallEnded either. No separate check is needed to
 * exclude that case.
 */
object MissedCallNotifier {

    /** [isIncoming]/[summary.durationSeconds] mirror the exact "was this call ever answered"
     * check CallEndedScreen already uses for its own "Missed call" status line (see
     * CallEndedScreen.kt) — a call that rang and was declined counts as missed here too, same as
     * everywhere else in this app; there's no separate "declined" category. */
    suspend fun notifyIfMissed(context: Context, summary: CallSummary, isIncoming: Boolean, endedAtMillis: Long) {
        if (!isIncoming || summary.durationSeconds > 0L) return
        val phoneNumber = summary.phoneNumber ?: return

        withContext(Dispatchers.IO) {
            val missedCallsEnabled = NotificationPreferencesRepository().preferences(context).first().missedCallsEnabled
            if (!missedCallsEnabled) return@withContext
            if (!queryPermissionsState(context).notificationsGranted) return@withContext

            val isSpam = SpamMarkRepository().isMarkedAsSpam(context, phoneNumber)
            val contactName = if (!isSpam) {
                ContactsRepository().getContactByPhoneNumber(context, phoneNumber)?.name
            } else {
                null
            }
            postNotification(context, phoneNumber, isSpam, contactName, endedAtMillis)
        }
    }

    private fun postNotification(
        context: Context,
        phoneNumber: String,
        isSpam: Boolean,
        contactName: String?,
        endedAtMillis: Long,
    ) {
        val notificationId = NOTIFICATION_ID_BASE + (phoneNumber.hashCode() and 0xFFFF)
        val formattedNumber = formatPhoneNumberForDisplay(phoneNumber)

        val builder = NotificationCompat.Builder(context, MISSED_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setWhen(endedAtMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, phoneNumber, contactName, notificationId))

        if (isSpam) {
            builder
                .setContentTitle(context.getString(R.string.missed_call_title_spam))
                .setContentText(formattedNumber)
                .setColor(SPAM_ACCENT_COLOR)
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification_block,
                        context.getString(R.string.incoming_block_label),
                        blockPendingIntent(context, phoneNumber, notificationId),
                    ).build(),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification_dismiss,
                        context.getString(R.string.call_ended_dismiss_content_description),
                        dismissPendingIntent(context, notificationId),
                    ).build(),
                )
        } else {
            val subtitle = if (contactName != null) {
                context.getString(R.string.missed_call_subtitle_with_name, contactName, formattedNumber)
            } else {
                formattedNumber
            }
            builder
                .setContentTitle(context.getString(R.string.call_status_missed))
                .setContentText(subtitle)
                .setColor(NORMAL_ACCENT_COLOR)
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification_call,
                        context.getString(R.string.missed_call_action_call_back),
                        callBackPendingIntent(context, phoneNumber, notificationId),
                    ).build(),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification_message,
                        context.getString(R.string.incoming_message_label),
                        messagePendingIntent(context, phoneNumber, notificationId),
                    ).build(),
                )
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /** Tapping the notification body (not an action) opens the app — the real contact_detail
     * screen if the number matched a contact (same EXTRA_OPEN_CONTACT_DETAIL deep link
     * CallEndedActivity's "Contact" action uses), otherwise just Home. */
    private fun buildContentIntent(
        context: Context,
        phoneNumber: String,
        contactName: String?,
        notificationId: Int,
    ): PendingIntent {
        val intent = if (contactName != null) {
            MainActivity.contactDetailIntent(context, phoneNumber)
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Deliberately reuses [MainActivity.placeCallIntent] — the same hand-off CallEndedActivity's
     * own "Call" action already uses — rather than firing Intent.ACTION_CALL directly. This app
     * has no ACTION_CALL/ACTION_DIAL call-placing path anywhere (real calls always go through
     * TelecomManager.placeCall from OutgoingDialingScreen, reached via CalloraNavHost's
     * attemptPlaceCall gate); firing ACTION_CALL straight from here would place a real call the
     * same way, but would leave CallRepository/the nav stack out of sync (see MainActivity's own
     * doc comment on EXTRA_PLACE_CALL_PHONE_NUMBER for why CallEndedActivity avoids it too). This
     * also means a missing CALL_PHONE permission is handled the same way it is everywhere else in
     * the app — the existing in-app prompt — rather than an ACTION_DIAL fallback. */
    private fun callBackPendingIntent(context: Context, phoneNumber: String, notificationId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId + 1,
            MainActivity.placeCallIntent(context, phoneNumber),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun messagePendingIntent(context: Context, phoneNumber: String, notificationId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber"))
        return PendingIntent.getActivity(
            context,
            notificationId + 2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun blockPendingIntent(context: Context, phoneNumber: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, MissedCallActionReceiver::class.java).apply {
            action = MissedCallActionReceiver.ACTION_BLOCK
            putExtra(MissedCallActionReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(MissedCallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun dismissPendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, MissedCallActionReceiver::class.java).apply {
            action = MissedCallActionReceiver.ACTION_DISMISS
            putExtra(MissedCallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId + 4,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
