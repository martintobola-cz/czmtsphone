package cz.mts.base.extensions

import android.database.Cursor

fun Cursor.getStringValue(key: String) = getString(getColumnIndexOrThrow(key))

fun Cursor.getStringValueOrNull(key: String): String? {
    return if (isNull(getColumnIndexOrThrow(key))) null else getString(getColumnIndexOrThrow(key))
}

fun Cursor.getIntValue(key: String) = getInt(getColumnIndexOrThrow(key))

fun Cursor.getIntValueOrNull(key: String): Int? {
    return if (isNull(getColumnIndexOrThrow(key))) null else getInt(getColumnIndexOrThrow(key))
}

fun Cursor.getLongValue(key: String) = getLong(getColumnIndexOrThrow(key))
