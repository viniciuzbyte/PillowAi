package com.faddlan.pillowai

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

fun AppCompatActivity.lightBars() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

fun ImageView.circle() {
    scaleType = ImageView.ScaleType.CENTER_CROP
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
    clipToOutline = true
}

fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun View.shake() {
    ObjectAnimator.ofFloat(this, "translationX", 0f, -18f, 18f, -12f, 12f, -6f, 6f, 0f)
        .setDuration(380)
        .start()
}
