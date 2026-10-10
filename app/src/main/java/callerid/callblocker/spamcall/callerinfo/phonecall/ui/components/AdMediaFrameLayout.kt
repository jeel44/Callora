package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Holds the large native template's MediaView (see native_ad_large.xml). Asks for a 16:9 box at
 * the given width, but accepts less when its LinearLayout parent has to shrink it (it carries
 * layout_weight, so on overflow LinearLayout takes the shortfall out of the media rather than
 * clipping the body/CTA below it) — never below [MIN_MEDIA_HEIGHT_DP], AdMob's minimum MediaView
 * size.
 */
class AdMediaFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val minHeightPx = (MIN_MEDIA_HEIGHT_DP * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val preferred = max(width * 9 / 16, minHeightPx)
        val available = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> max(available, minHeightPx)
            MeasureSpec.AT_MOST -> max(min(preferred, available), minHeightPx)
            else -> preferred
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    companion object {
        const val MIN_MEDIA_HEIGHT_DP = 120
    }
}
