package callerid.callblocker.spamcall.callerinfo.phonecall

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log

private const val TAG = "StartupTiming"

/**
 * Cold-start milestones, logged as milliseconds since the process was forked
 * ([Process.getStartUptimeMillis]) so `adb logcat -s StartupTiming` gives a comparable timeline on
 * every launch. [section] also emits a Trace section so the same steps show up in Perfetto.
 */
object StartupTimer {

    fun mark(label: String) {
        Log.i(TAG, "+${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}ms $label")
    }

    inline fun <T> section(label: String, block: () -> T): T {
        val start = SystemClock.uptimeMillis()
        Trace.beginSection(label)
        try {
            return block()
        } finally {
            Trace.endSection()
            logDuration(label, SystemClock.uptimeMillis() - start)
        }
    }

    fun logDuration(label: String, millis: Long) {
        Log.i(TAG, "  $label took ${millis}ms")
    }
}
