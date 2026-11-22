package com.faddlan.pillowai

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.faddlan.pillowai.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private lateinit var store: SessionStore
    private var floatAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SessionStore(this)
        if (store.email != null) {
            goMain()
            return
        }
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        lightBars()

        b.btnGo.setOnClickListener { submit() }
        b.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else false
        }

        playEntrance()
    }

    override fun onDestroy() {
        floatAnim?.cancel()
        super.onDestroy()
    }

    private fun playEntrance() {
        val rise = listOf<View>(b.logoRow, b.tvTagline, b.formBox, b.tvTerms)
        rise.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 36f
            v.animate().alpha(1f).translationY(0f).setStartDelay(120L + i * 110L).setDuration(520).start()
        }
        b.ivHero.alpha = 0f
        b.ivHero.scaleX = 0.88f
        b.ivHero.scaleY = 0.88f
        b.ivHero.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(80).setDuration(620)
            .withEndAction {
                floatAnim = ObjectAnimator.ofFloat(b.ivHero, "translationY", 0f, -16f).apply {
                    duration = 1900
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
            .start()
    }

    private fun submit() {
        val mail = b.etEmail.text.toString().trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            b.tvError.text = getString(R.string.email_invalid)
            b.tvError.visibility = View.VISIBLE
            b.formBox.shake()
            return
        }
        store.email = mail
        store.registerDevice()
        goMain()
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
