package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

/**
 * App-wide "a contact was just deleted (or otherwise changed outside the normal paging flow)"
 * flag — same lightweight singleton-state pattern as
 * [callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState].
 *
 * AllContactsScreen's own resume effect deliberately only refreshes its Pager when the list is
 * empty or permission was just granted, to avoid a spinner flash on every trivial resume (see its
 * own comment) — which meant a contact deleted from Contact Detail stayed visible there until the
 * app restarted and the Pager was recreated from scratch (build-2 QA #11). Setting this flag on a
 * successful delete gives that resume effect a real reason to refresh, without reintroducing an
 * unconditional reload on every return visit.
 */
object ContactsChangeSignal {
    var pendingRefresh: Boolean = false
}
