package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import android.util.Log
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallType
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "RecentCallsSnapshot"
private const val FILE_NAME = "home_recent_calls_snapshot.json"

/** Last-known Home data. [nextOffset]/[hasMoreCalls] are the paging cursor that goes with
 * [recentCalls], so a restored list can keep paging from where it left off. */
data class RecentCallsSnapshot(
    val ownerName: String?,
    val recentCalls: List<RecentCallEntry>,
    val nextOffset: Int,
    val hasMoreCalls: Boolean,
)

/**
 * Keeps Home's last-known data alive beyond HomeViewModel itself, which only lives as long as
 * Home's NavBackStackEntry — i.e. it dies whenever MainActivity is destroyed (Back out of Home,
 * the system reclaiming a backgrounded Activity) and with the process. Two tiers:
 *  - [memory]: the full loaded list, process-wide, read synchronously — a new HomeViewModel in a
 *    still-running process paints this on its very first frame.
 *  - a small file in noBackupFilesDir holding only the first page, for a start after process
 *    death. Same data the system call log already holds on this device, kept app-private and
 *    excluded from backup; deleted when call-log permission is gone (see [clear]). Read once
 *    per process by [preload], started from CalloraApplication.onCreate so it's normally
 *    finished (and promoted into [memory]) long before Home composes.
 */
object RecentCallsSnapshotStore {

    @Volatile var memory: RecentCallsSnapshot? = null
        private set

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var preloadJob: Deferred<RecentCallsSnapshot?>? = null

    fun update(snapshot: RecentCallsSnapshot) {
        memory = snapshot
    }

    /** Starts the one disk read this process needs, in the background, and promotes its result
     * into [memory] so HomeViewModel can pick it up synchronously. Skipped without call-log
     * permission (nothing may be shown then, and [clear] will delete the file). */
    fun preload(context: Context) {
        if (memory != null || preloadJob != null) return
        val appContext = context.applicationContext
        if (!CallDataPermissions.hasCallLog(appContext)) return
        preloadJob = preloadScope.async {
            readFromDisk(appContext)?.takeIf { it.recentCalls.isNotEmpty() }?.also { snapshot ->
                if (memory == null) memory = snapshot
            }
        }
    }

    /** For HomeViewModel when [memory] was still empty at creation: waits for [preload] if it's
     * still in flight rather than reading the file a second time; falls back to a direct read if
     * preload never ran (e.g. call-log permission was granted after process start). */
    suspend fun awaitDiskSnapshot(context: Context): RecentCallsSnapshot? =
        preloadJob?.await() ?: readFromDisk(context)

    /** Blocking — call from Dispatchers.IO. */
    fun readFromDisk(context: Context): RecentCallsSnapshot? = try {
        val file = file(context)
        if (!file.exists()) null else parse(JSONObject(file.readText()))
    } catch (e: Exception) {
        Log.w(TAG, "readFromDisk: unreadable snapshot, ignoring", e)
        null
    }

    /** Blocking — call from Dispatchers.IO. Persists only the first [pageSize] rows: a disk
     * restore is display-only until HomeViewModel's silent first-page reload replaces it (paging
     * stays blocked until then), so its cursor is never actually paged from. */
    @Synchronized
    fun writeToDisk(context: Context, snapshot: RecentCallsSnapshot, pageSize: Int) {
        try {
            val json = JSONObject().apply {
                put("ownerName", snapshot.ownerName ?: JSONObject.NULL)
                put("recentCalls", JSONArray().apply { snapshot.recentCalls.take(pageSize).forEach { put(toJson(it)) } })
            }
            val target = file(context)
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "writeToDisk: failed", e)
        }
    }

    fun clear(context: Context) {
        memory = null
        preloadJob = null
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)

    private fun toJson(entry: RecentCallEntry) = JSONObject().apply {
        put("id", entry.id)
        put("name", entry.name ?: JSONObject.NULL)
        put("phoneNumber", entry.phoneNumber)
        put("formattedPhoneNumber", entry.formattedPhoneNumber)
        put("timestamp", entry.timestamp)
        put("callType", entry.callType.name)
        put("photoUri", entry.photoUri ?: JSONObject.NULL)
        put("isSpam", entry.isSpam)
    }

    private fun parse(json: JSONObject): RecentCallsSnapshot {
        val array = json.getJSONArray("recentCalls")
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            RecentCallEntry(
                id = item.getLong("id"),
                name = item.optStringOrNull("name"),
                phoneNumber = item.getString("phoneNumber"),
                formattedPhoneNumber = item.getString("formattedPhoneNumber"),
                timestamp = item.getLong("timestamp"),
                callType = runCatching { CallType.valueOf(item.getString("callType")) }.getOrDefault(CallType.OTHER),
                photoUri = item.optStringOrNull("photoUri"),
                isSpam = item.optBoolean("isSpam", false),
            )
        }
        return RecentCallsSnapshot(
            ownerName = json.optStringOrNull("ownerName"),
            recentCalls = entries,
            nextOffset = 0,
            hasMoreCalls = true,
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key)
}
