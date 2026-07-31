package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider-backed, LIMIT/OFFSET-paginated source for the All Contacts screen — mirrors
 * [CallLogRepository.getRecentCalls]'s already-correct pattern of never materializing more than
 * one page's worth of rows in memory, instead of loading every contact on the device into memory
 * at once and sorting it there.
 *
 * [query] selects which repository method backs each page: blank uses
 * [ContactsRepository.getContactsPage] (unfiltered, structurally deduped via the Contacts table),
 * non-blank uses [ContactsRepository.searchContactsPage] (name/number filter against the Phone
 * table) — see their kdoc for why they're different queries rather than one parameterized one.
 * A new [ContactsPagingSource] is created (via [androidx.paging.Pager]'s `pagingSourceFactory`)
 * each time the search query changes, since Paging sources are single-query/immutable by design.
 */
class ContactsPagingSource(
    private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val query: String,
) : PagingSource<Int, Contact>() {

    override fun getRefreshKey(state: PagingState<Int, Contact>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Contact> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        return try {
            val page = withContext(Dispatchers.IO) {
                if (query.isBlank()) {
                    contactsRepository.getContactsPage(context, limit = limit, offset = offset)
                } else {
                    contactsRepository.searchContactsPage(context, query = query, limit = limit, offset = offset)
                }
            }
            LoadResult.Page(
                data = page.contacts,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                // rawRowCount, not page.contacts.size — a page can be provider-full but shorter
                // client-side (see ContactsPage kdoc), and using contacts.size here would end
                // pagination early even though more rows exist past this offset.
                nextKey = if (page.rawRowCount < limit) null else offset + page.rawRowCount,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
