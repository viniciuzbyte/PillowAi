package com.faddlan.pillowai

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.faddlan.pillowai.databinding.ItemAiMessageBinding
import com.faddlan.pillowai.databinding.ItemCodeBlockBinding
import com.faddlan.pillowai.databinding.ItemTypingBinding
import com.faddlan.pillowai.databinding.ItemUserMessageBinding

class ChatAdapter(
    private val context: Context,
    private val onGrow: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = ArrayList<Message>()
    private val inflater = LayoutInflater.from(context)
    private var typingItem: Message? = null

    var avatar: Bitmap? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun setAll(list: List<Message>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        typingItem = null
        notifyDataSetChanged()
    }

    fun add(message: Message) {
        items.add(message)
        notifyItemInserted(items.size - 1)
        onGrow()
    }

    fun remove(message: Message) {
        val index = items.indexOfFirst { it === message }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun showTyping() {
        val t = Message("assistant", "", typing = true)
        typingItem = t
        add(t)
    }

    fun hideTyping() {
        typingItem?.let { remove(it) }
        typingItem = null
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        val m = items[position]
        return when {
            m.typing -> 2
            m.role == "user" -> 0
            else -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            0 -> UserHolder(ItemUserMessageBinding.inflate(inflater, parent, false))
            1 -> AiHolder(ItemAiMessageBinding.inflate(inflater, parent, false))
            else -> TypingHolder(ItemTypingBinding.inflate(inflater, parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        when (holder) {
            is UserHolder -> holder.bind(m)
            is AiHolder -> holder.bind(m)
            is TypingHolder -> holder.start()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AiHolder) holder.cancel()
        if (holder is TypingHolder) holder.stop()
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is TypingHolder) holder.start()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is TypingHolder) holder.stop()
    }

    private fun copyAll(text: String) {
        context.copyToClipboard("Pillow", text)
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    inner class UserHolder(private val vb: ItemUserMessageBinding) : RecyclerView.ViewHolder(vb.root) {

        init {
            vb.ivUserAvatar.circle()
        }

        fun bind(m: Message) {
            vb.tvUser.text = m.text
            vb.ivUserAvatar.setImageBitmap(avatar)
            vb.root.setOnLongClickListener {
                copyAll(m.text)
                true
            }
            if (m.animate) {
                m.animate = false
                vb.root.alpha = 0f
                vb.root.translationY = context.dp(18).toFloat()
                vb.root.animate().alpha(1f).translationY(0f).setDuration(260).start()
            } else {
                vb.root.alpha = 1f
                vb.root.translationY = 0f
            }
        }
    }

    private class Target(val container: View, val tv: TextView, val full: CharSequence)

    inner class AiHolder(private val vb: ItemAiMessageBinding) : RecyclerView.ViewHolder(vb.root) {

        private var animator: ValueAnimator? = null
        private var current: String = ""

        init {
            vb.root.setOnLongClickListener {
                copyAll(current)
                true
            }
        }

        fun cancel() {
            animator?.cancel()
            animator = null
        }

        fun bind(m: Message) {
            cancel()
            current = m.text
            vb.blocks.removeAllViews()
            val targets = ArrayList<Target>()
            for (blk in MarkdownRenderer.parse(m.text)) {
                when (blk) {
                    is Block.Text -> {
                        val tv = inflater.inflate(R.layout.item_text_block, vb.blocks, false) as TextView
                        val full = MarkdownRenderer.render(blk.content)
                        tv.movementMethod = LinkMovementMethod.getInstance()
                        tv.text = full
                        tv.setOnLongClickListener {
                            copyAll(current)
                            true
                        }
                        vb.blocks.addView(tv)
                        targets.add(Target(tv, tv, full))
                    }
                    is Block.Code -> {
                        val code = blk
                        val cb = ItemCodeBlockBinding.inflate(inflater, vb.blocks, false)
                        cb.tvLang.text = code.lang
                        cb.tvCode.text = code.code
                        cb.btnCopy.setOnClickListener {
                            context.copyToClipboard(code.lang, code.code)
                            cb.tvCopy.text = context.getString(R.string.copied)
                            cb.ivCopy.setImageResource(R.drawable.ic_check)
                            cb.btnCopy.postDelayed({
                                cb.tvCopy.text = context.getString(R.string.copy)
                                cb.ivCopy.setImageResource(R.drawable.ic_copy)
                            }, 1600)
                        }
                        vb.blocks.addView(cb.root)
                        targets.add(Target(cb.root, cb.tvCode, code.code))
                    }
                }
            }
            if (m.animate) {
                m.animate = false
                play(targets)
            }
        }

        private fun play(targets: List<Target>) {
            val total = targets.sumOf { it.full.length }
            if (total == 0) return
            targets.forEach { it.container.visibility = View.GONE }
            val anim = ValueAnimator.ofInt(0, total)
            anim.duration = (total * 7L).coerceIn(400L, 5000L)
            anim.interpolator = LinearInterpolator()
            var tick = 0
            anim.addUpdateListener {
                var left = it.animatedValue as Int
                for (t in targets) {
                    if (left <= 0) {
                        t.container.visibility = View.GONE
                    } else {
                        t.container.visibility = View.VISIBLE
                        val n = minOf(left, t.full.length)
                        t.tv.text = if (n >= t.full.length) t.full else t.full.subSequence(0, n)
                        left -= t.full.length
                    }
                }
                tick++
                if (tick % 3 == 0) onGrow()
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    targets.forEach {
                        it.container.visibility = View.VISIBLE
                        it.tv.text = it.full
                    }
                    onGrow()
                }
            })
            animator = anim
            anim.start()
        }
    }

    inner class TypingHolder(private val vb: ItemTypingBinding) : RecyclerView.ViewHolder(vb.root) {

        private val animators = ArrayList<ObjectAnimator>()

        fun start() {
            stop()
            val lift = -context.dp(6).toFloat()
            listOf(vb.dot1, vb.dot2, vb.dot3).forEachIndexed { i, dot ->
                val a = ObjectAnimator.ofFloat(dot, "translationY", 0f, lift)
                a.duration = 420
                a.repeatMode = ValueAnimator.REVERSE
                a.repeatCount = ValueAnimator.INFINITE
                a.startDelay = i * 140L
                a.start()
                animators.add(a)
            }
        }

        fun stop() {
            animators.forEach { it.cancel() }
            animators.clear()
            vb.dot1.translationY = 0f
            vb.dot2.translationY = 0f
            vb.dot3.translationY = 0f
        }
    }
}
