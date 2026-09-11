package cz.mts.base.extensions

fun <T> MutableList<T>.swap(index1: Int, index2: Int) {
    if (index1 == index2) return
    this[index1] = this[index2].also {
        this[index2] = this[index1]
    }
}
