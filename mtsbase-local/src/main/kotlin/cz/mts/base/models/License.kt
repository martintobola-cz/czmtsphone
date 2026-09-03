package cz.mts.base.models

import androidx.compose.runtime.Immutable

@Immutable
data class License(val id: Long, val titleId: Int, val textId: Int, val urlId: Int)
