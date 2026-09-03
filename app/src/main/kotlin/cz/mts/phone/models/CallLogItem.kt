package cz.mts.phone.models

import java.time.Instant
import java.time.ZoneId

sealed class CallLogItem {

    data class Date(
        val timestamp: Long,
        val dayCode: String,
    ) : CallLogItem()

    fun getItemId(): Int = when (this) {

        is Date -> {
            val epochDay = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()
                .toInt()

            -(epochDay + 1)
        }

        is RecentCall -> id
    }

    fun getDayCodeX(): String = when (this) {
        is Date -> dayCode
        is RecentCall -> ""
    }
}