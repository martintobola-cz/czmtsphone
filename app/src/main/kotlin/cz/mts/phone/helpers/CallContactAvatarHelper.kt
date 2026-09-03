package cz.mts.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.phone.models.CallContact

class CallContactAvatarHelper(private val context: Context) {
    @SuppressLint("NewApi")
    fun getCallContactAvatar(callContact: CallContact?, circle: Boolean = true): Bitmap? {
        if (callContact?.photoUri.isNullOrEmpty()) return null

        val photoUri = Uri.parse(callContact!!.photoUri)
        return try {
            val bitmap: Bitmap? = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    // API 28+ – ImageDecoder funguje i pro Contacts URI
                    try {
                        val source = ImageDecoder.createSource(context.contentResolver, photoUri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true // aby šel dál zpracovat
                        }
                    } catch (_: Exception) {
                        decodeViaStream(photoUri) // fallback
                    }
                }
                else -> decodeViaStream(photoUri)
            }
            bitmap?.let {
                if (circle) SimpleContactsHelper(context).getCircularBitmap(it) else it
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeViaStream(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

}
