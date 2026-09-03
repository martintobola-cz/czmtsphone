package cz.mts.phone.helpers


object RecentsQueryLimits {

    private const val QUERY_RECENT_FAST_FIRST_LIMIT = 5
    private const val QUERY_RECENT_CHUNK_LIMIT = 33
    private var bFullRefreshIsNeeded = false
    private var bIsPreview = false

    public var recentcount = 0
    fun getFastLimit() : Int { return QUERY_RECENT_FAST_FIRST_LIMIT}
    fun getChunkLimit() : Int { return QUERY_RECENT_CHUNK_LIMIT}

    fun getRefreshState() : Boolean { return bFullRefreshIsNeeded}
    fun setRefreshState(bState : Boolean) { bFullRefreshIsNeeded = bState}

    fun getPreviewState() : Boolean { return bIsPreview}
    fun setPreviewState(bState : Boolean) { bIsPreview = bState}
}
