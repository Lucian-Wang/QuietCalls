package wang.lucian.quietcalls.service

import android.content.Context
import wang.lucian.quietcalls.R
import wang.lucian.quietcalls.util.queryFailureSummaryRes
import wang.lucian.quietcalls.data.entity.ResultType
import wang.lucian.quietcalls.data.remote.PhoneLocationInfo

data class IncomingCallOverlayContent(
    val phoneNumber: String,
    val locationText: String,
    val localLabelText: String?,
    val sourceLabelText: String?,
    val communityLabelText: String? = null,
    val communityEpoch: Long = -1,
    val communityReceivedAtElapsedMs: Long = -1,
)

object IncomingCallOverlayFormatter {
    fun formatLocation(
        context: Context,
        info: PhoneLocationInfo?,
        resultType: ResultType,
    ): String {
        val failureText = resultType.queryFailureSummaryRes()
        if (failureText != null) return context.getString(failureText)

        val region = listOfNotNull(
            info?.province?.trim()?.takeIf { it.isNotEmpty() },
            info?.city?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")

        return region.ifEmpty { context.getString(R.string.overlay_unknown_location) }
    }

    /** 分别整理本地标签与数据源标签，trim 后为空则丢弃，不拼接。 */
    fun buildContent(
        phoneNumber: String,
        locationText: String,
        localLabel: String?,
        sourceLabel: String?,
        communityLabel: String? = null,
    ): IncomingCallOverlayContent {
        return IncomingCallOverlayContent(
            phoneNumber = phoneNumber.trim().ifEmpty { phoneNumber },
            locationText = locationText.trim().ifEmpty { locationText },
            localLabelText = localLabel?.trim()?.takeIf { it.isNotEmpty() },
            sourceLabelText = sourceLabel?.trim()?.takeIf { it.isNotEmpty() },
            communityLabelText = communityLabel,

        )
    }
}
