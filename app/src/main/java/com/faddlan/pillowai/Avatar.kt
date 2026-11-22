package com.faddlan.pillowai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object Avatar {

    fun bitmap(path: String?, email: String?, px: Int): Bitmap {
        if (path != null) {
            val decoded = BitmapFactory.decodeFile(path)
            if (decoded != null) return Bitmap.createScaledBitmap(decoded, px, px, true)
        }
        return initial(email, px)
    }

    private fun initial(email: String?, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFD97757.toInt()
        canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
        paint.color = Color.WHITE
        paint.textSize = px * 0.46f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.SERIF
        val letter = (email?.firstOrNull { it.isLetterOrDigit() } ?: 'P').uppercaseChar().toString()
        val y = px / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, px / 2f, y, paint)
        return bmp
    }

    fun saveFromUri(context: Context, uri: Uri, dest: File): Boolean {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1600 && bounds.outHeight / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val degrees = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val rotated = if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            } else src
            val side = minOf(rotated.width, rotated.height)
            val square = Bitmap.createBitmap(rotated, (rotated.width - side) / 2, (rotated.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(square, 512, 512, true)
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
