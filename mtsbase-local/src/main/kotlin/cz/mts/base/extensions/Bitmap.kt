package cz.mts.base.extensions

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

fun Bitmap.getByteArray(): ByteArray {
    var baos: ByteArrayOutputStream? = null
    try {
        baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 88, baos)
        return baos.toByteArray()
    } finally {
        baos?.close()
    }
}
