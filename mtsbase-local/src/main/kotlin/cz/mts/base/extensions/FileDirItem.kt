package cz.mts.base.extensions

import android.content.Context
import cz.mts.base.models.FileDirItem

fun FileDirItem.isRecycleBinPath(context: Context): Boolean {
    return path.startsWith(context.recycleBinPath)
}
