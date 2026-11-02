package cz.mts.phone.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.extensions.baseConfig
import cz.mts.phone.R
import java.io.File


object AvatarSource {

    enum class Kind(val defaultRes: Int, val fileName: String) {
        FAKE(R.drawable.fakeavatar, "custom_fakeavatar.png"),
        BLOCK(R.drawable.blockavatar, "custom_blockavatar.png")
    }

    private const val DIR_NAME = "my_avatars"
    private const val MAX_SIZE = 512

    private fun file(ctx: Context, kind: Kind) =
        File(File(ctx.applicationContext.filesDir, DIR_NAME), kind.fileName)

    private fun decodeCustom(ctx: Context, kind: Kind): Bitmap? =
        try {
            BitmapFactory.decodeFile(file(ctx, kind).path)
        } catch (e: Exception) {
            null
        }

    private fun drawableToBitmap(d: Drawable): Bitmap {
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 1
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }

    // ─────────────────────────────────────────────────────────────────────
    // Stav
    // ─────────────────────────────────────────────────────────────────────

    /** Existuje uložený vlastní soubor (bez ohledu na to, zda je zvolen)? */
    fun hasCustomFile(ctx: Context, kind: Kind): Boolean =
        file(ctx, kind).let { it.isFile && it.length() > 0 }


    private fun isChosen(ctx: Context, kind: Kind): Boolean = ctx.baseConfig.let {
        when (kind) {
            Kind.FAKE -> it.useCustomFakeAvatar
            Kind.BLOCK -> it.useCustomBlockAvatar
        }
    }

    fun isCustomActive(ctx: Context, kind: Kind): Boolean =
        hasCustomFile(ctx, kind) && isChosen(ctx, kind)

    fun setUseCustom(ctx: Context, kind: Kind, use: Boolean) {
        ctx.baseConfig.apply {
            when (kind) {
                Kind.FAKE -> useCustomFakeAvatar = use
                Kind.BLOCK -> useCustomBlockAvatar = use
            }
        }
    }
    // ─────────────────────────────────────────────────────────────────────
    // Výstupy pro ostatní třídy (respektují aktuální volbu)
    // ─────────────────────────────────────────────────────────────────────

    /** Pro ImageView.setImageDrawable, placeholderImage apod. */
    fun drawable(ctx: Context, kind: Kind): Drawable {
        if (isCustomActive(ctx, kind)) {
            decodeCustom(ctx, kind)?.let { return BitmapDrawable(ctx.resources, it) }
        }
        return defaultDrawable(ctx, kind)
    }

    /** Náhrada za fakeAvatar(ctx, R.drawable.xxx). */
    fun bitmap(ctx: Context, kind: Kind): Bitmap {
        if (isCustomActive(ctx, kind)) {
            decodeCustom(ctx, kind)?.let { return it }
        }
        val d = ContextCompat.getDrawable(ctx, kind.defaultRes)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return drawableToBitmap(d)
    }

    /**
     * Pro RecentsHelper – vrací kompletní URI včetně ?t=.
     * U vlastního souboru je přidán i parametr v=lastModified, aby se po změně
     * obrázku neukazoval starý z cache (Glide apod.).
     */
    fun uriString(ctx: Context, kind: Kind, stamp: Long): String {
        return if (isCustomActive(ctx, kind)) {
            val f = file(ctx, kind)
            Uri.fromFile(f).buildUpon()
                .appendQueryParameter("t", stamp.toString())
                .appendQueryParameter("v", f.lastModified().toString())
                .build()
                .toString()
        } else {
            "android.resource://${ctx.packageName}/${kind.defaultRes}?t=$stamp"
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pro obrazovku nastavení (obě varianty zvlášť, bez ohledu na volbu)
    // ─────────────────────────────────────────────────────────────────────

    fun defaultDrawable(ctx: Context, kind: Kind): Drawable =
        ContextCompat.getDrawable(ctx, kind.defaultRes)!!

    fun customDrawable(ctx: Context, kind: Kind): Drawable? =
        if (!hasCustomFile(ctx, kind)) null
        else decodeCustom(ctx, kind)?.let { BitmapDrawable(ctx.resources, it) }

    // ─────────────────────────────────────────────────────────────────────
    // Ukládání / mazání
    // ─────────────────────────────────────────────────────────────────────

    /** Uloží vybraný obrázek jako kulaté PNG (čtvercový výřez ze středu, max 512 px). Vrací true při úspěchu. */
    fun saveCustom(ctx: Context, kind: Kind, source: Uri): Boolean {
        return try {
            val resolver = ctx.contentResolver

            // 1) zjisti rozměry
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIZE * 2 || bounds.outHeight / sample > MAX_SIZE * 2) {
                sample *= 2
            }

            // 2) načti zmenšené
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false

            // 3) čtverec ze středu obrázku (kruh musí vycházet ze čtverce)
            val side = minOf(decoded.width, decoded.height)
            val x = (decoded.width - side) / 2
            val y = (decoded.height - side) / 2
            var square = Bitmap.createBitmap(decoded, x, y, side, side)
            if (side > MAX_SIZE) {
                square = Bitmap.createScaledBitmap(square, MAX_SIZE, MAX_SIZE, true)
            }

            // 4) kulatý výřez, rohy průhledné (stejně jako výchozí avatar)
            val circular = SimpleContactsHelper(ctx).getCircularBitmap(square)

            // 5) ulož jako PNG (zachová průhlednost)
            val target = file(ctx, kind).also { it.parentFile?.mkdirs() }
            target.outputStream().use { circular.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Smaže vlastní obrázek a vrátí se k výchozímu. */
    fun resetToDefault(ctx: Context, kind: Kind) {
        file(ctx, kind).delete()
        setUseCustom(ctx, kind, false)
    }
}
