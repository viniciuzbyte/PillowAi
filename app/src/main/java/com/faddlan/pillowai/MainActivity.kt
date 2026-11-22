package com.faddlan.pillowai

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.faddlan.pillowai.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var store: SessionStore
    private lateinit var adapter: ChatAdapter
    private val history = ArrayList<Message>()
    private var busy = false
    private var spin: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SessionStore(this)
        if (store.email == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        lightBars()

        b.ivAvatar.circle()

        adapter = ChatAdapter(this) { scrollBottom() }
        b.rvChat.layoutManager = LinearLayoutManager(this)
        b.rvChat.adapter = adapter
        b.rvChat.itemAnimator = null
        b.rvChat.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) scrollBottom()
        }

        history.addAll(store.loadChat())
        adapter.setAll(history)
        scrollBottom()
        updateEmpty()
        updateSend()

        b.etMessage.doAfterTextChanged { updateSend() }
        b.btnSend.setOnClickListener { send() }
        b.btnNewChat.setOnClickListener { newChat() }
        b.ivAvatar.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        if (!::b.isInitialized) return
        val bmp = Avatar.bitmap(store.photoPath(), store.email, dp(128))
        b.ivAvatar.setImageBitmap(bmp)
        adapter.avatar = bmp
        spin?.cancel()
        spin = ObjectAnimator.ofFloat(b.ivSpark, "rotation", 0f, 360f).apply {
            duration = 14000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onPause() {
        spin?.cancel()
        super.onPause()
    }

    private fun scrollBottom() {
        if (adapter.itemCount > 0) b.rvChat.scrollToPosition(adapter.itemCount - 1)
    }

    private fun updateEmpty() {
        val show = history.isEmpty() && !busy
        b.emptyState.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateSend() {
        val on = !busy && b.etMessage.text.isNotBlank()
        b.btnSend.isEnabled = on
        b.btnSend.alpha = if (on) 1f else 0.35f
    }

    private fun newChat() {
        if (busy) return
        history.clear()
        store.saveChat(history)
        adapter.clear()
        updateEmpty()
    }

    private fun send() {
        val text = b.etMessage.text.toString().trim()
        if (text.isEmpty() || busy) return
        b.etMessage.text = null

        val user = Message("user", text, animate = true)
        history.add(user)
        adapter.add(user)
        adapter.showTyping()
        busy = true
        updateEmpty()
        updateSend()

        val payload = history.takeLast(30).dropWhile { it.role != "user" }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ApiClient.send(payload) } }
            adapter.hideTyping()
            result.onSuccess { reply ->
                val ai = Message("assistant", reply, animate = true)
                history.add(ai)
                adapter.add(ai)
                store.saveChat(history)
            }.onFailure { e ->
                history.remove(user)
                adapter.remove(user)
                b.etMessage.setText(text)
                b.etMessage.setSelection(text.length)
                Snackbar.make(b.root, e.message ?: "Error", Snackbar.LENGTH_LONG).show()
            }
            busy = false
            updateEmpty()
            updateSend()
        }
    }
}
