package cz.mts.base.extensions

import android.content.Context
import cz.mts.base.helpers.photoExtensions
import cz.mts.base.helpers.videoExtensions
import java.io.File

fun File.isGif() = absolutePath.endsWith(".gif", true)
fun File.isVideoFast() = videoExtensions.any { absolutePath.endsWith(it, true) }
fun File.isImageFast() = photoExtensions.any { absolutePath.endsWith(it, true) }
fun File.isPortrait() = absolutePath.isPortrait()

fun File.getProperSize(countHiddenItems: Boolean): Long {
    return if (isDirectory) {
        getDirectorySize(this, countHiddenItems)
    } else {
        length()
    }
}

private fun getDirectorySize(dir: File, countHiddenItems: Boolean): Long {
    var size = 0L
    if (dir.exists()) {
        val files = dir.listFiles()
        if (files != null) {
            for (i in files.indices) {
                if (files[i].isDirectory) {
                    size += getDirectorySize(files[i], countHiddenItems)
                } else if (!files[i].name.startsWith('.') && !dir.name.startsWith('.') || countHiddenItems) {
                    size += files[i].length()
                }
            }
        }
    }
    return size
}

fun File.getDirectChildrenCount(context: Context, countHiddenItems: Boolean): Int {
    val fileCount = if (context.isRestrictedSAFOnlyRoot(path)) {
        context.getAndroidSAFDirectChildrenCount(
            path,
            countHiddenItems
        )
    } else {
        listFiles()?.filter {
            if (countHiddenItems) {
                true
            } else {
                !it.name.startsWith('.')
            }
        }?.size ?: 0
    }

    return fileCount
}
