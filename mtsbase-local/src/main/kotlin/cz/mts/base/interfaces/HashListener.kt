package cz.mts.base.interfaces

interface HashListener {
    fun receivedHash(hash: String, type: Int)
}
