package com.faddlan.pillowai

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan

sealed class Block {
    class Text(val content: String) : Block()
    class Code(val lang: String, val code: String) : Block()
}

object MarkdownRenderer {

    private const val F = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    private val ACCENT = 0xFFC2603F.toInt()
    private val BORDER = 0xFFE3E0D6.toInt()
    private val CODE_BG = 0xFFEFEDE4.toInt()

    private val fenceRx = Regex("```([\\w+#.-]*)[ \\t]*\\r?\\n([\\s\\S]*?)(?:\\r?\\n```|\\z)")
    private val htmlRx = Regex("^\\s*(<!doctype\\s+html|<html[\\s>])", RegexOption.IGNORE_CASE)
    private val tableRx = Regex("^\\s*\\|.*\\|\\s*$")
    private val headingRx = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val ruleRx = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val bulletRx = Regex("^(\\s*)[-*+\u2022]\\s+(.*)$")
    private val quoteRx = Regex("^>\\s?(.*)$")
    private val inlineRx = Regex(
        "`([^`\\n]+)`" +
            "|\\*\\*(.+?)\\*\\*" +
            "|~~(.+?)~~" +
            "|\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)" +
            "|(?<![*\\w])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![*\\w])" +
            "|(?<![_\\w])_(?!\\s)([^_\\n]+?)(?<!\\s)_(?![_\\w])"
    )

    private val headingSizes = floatArrayOf(1.45f, 1.28f, 1.15f, 1.08f, 1.0f, 1.0f)

    fun parse(src: String): List<Block> {
        val out = ArrayList<Block>()
        var last = 0
        for (m in fenceRx.findAll(src)) {
            if (m.range.first > last) addText(out, src.substring(last, m.range.first))
            val lang = m.groupValues[1].ifEmpty { "code" }
            out.add(Block.Code(lang, m.groupValues[2]))
            last = m.range.last + 1
        }
        if (last < src.length) addText(out, src.substring(last))
        return out
    }

    private fun addText(out: MutableList<Block>, chunk: String) {
        val trimmed = chunk.trim('\n', '\r')
        if (trimmed.isBlank()) return
        if (htmlRx.containsMatchIn(trimmed)) {
            out.add(Block.Code("html", trimmed))
            return
        }
        val text = StringBuilder()
        val table = StringBuilder()
        fun flushText() {
            val t = text.toString().trim('\n')
            if (t.isNotBlank()) out.add(Block.Text(t))
            text.setLength(0)
        }
        fun flushTable() {
            if (table.isNotEmpty()) out.add(Block.Code("table", table.toString().trimEnd('\n')))
            table.setLength(0)
        }
        for (line in trimmed.split("\n")) {
            if (tableRx.matches(line)) {
                flushText()
                table.append(line).append('\n')
            } else {
                flushTable()
                text.append(line).append('\n')
            }
        }
        flushText()
        flushTable()
    }

    fun render(text: String): CharSequence {
        val out = SpannableStringBuilder()
        val lines = text.replace("\r", "").split("\n")
        for ((i, raw) in lines.withIndex()) {
            val start = out.length
            var paragraph: Any? = null
            val heading = headingRx.matchEntire(raw)
            val bullet = bulletRx.matchEntire(raw)
            val quote = quoteRx.matchEntire(raw)
            if (heading != null) {
                out.append(inline(heading.groupValues[2]))
                val end = out.length
                out.setSpan(StyleSpan(Typeface.BOLD), start, end, F)
                out.setSpan(RelativeSizeSpan(headingSizes[heading.groupValues[1].length - 1]), start, end, F)
            } else if (ruleRx.matches(raw)) {
                out.append("\u2500".repeat(20))
                out.setSpan(ForegroundColorSpan(BORDER), start, out.length, F)
            } else if (bullet != null) {
                out.append(inline(bullet.groupValues[2]))
                paragraph = BulletSpan(28, ACCENT)
            } else if (quote != null) {
                out.append(inline(quote.groupValues[1]))
                out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, F)
                paragraph = QuoteSpan(ACCENT)
            } else {
                out.append(inline(raw))
            }
            if (i < lines.size - 1) out.append('\n')
            if (paragraph != null) out.setSpan(paragraph, start, out.length, F)
        }
        return out
    }

    private fun inline(src: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        var last = 0
        for (m in inlineRx.findAll(src)) {
            sb.append(src, last, m.range.first)
            val code = m.groups[1]?.value
            val bold = m.groups[2]?.value
            val strike = m.groups[3]?.value
            val label = m.groups[4]?.value
            val url = m.groups[5]?.value
            val italic = m.groups[6]?.value ?: m.groups[7]?.value
            val start = sb.length
            when {
                code != null -> {
                    sb.append(code)
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, F)
                    sb.setSpan(BackgroundColorSpan(CODE_BG), start, sb.length, F)
                    sb.setSpan(RelativeSizeSpan(0.92f), start, sb.length, F)
                }
                bold != null -> {
                    sb.append(inline(bold))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, F)
                }
                strike != null -> {
                    sb.append(inline(strike))
                    sb.setSpan(StrikethroughSpan(), start, sb.length, F)
                }
                label != null && url != null -> {
                    sb.append(inline(label))
                    sb.setSpan(URLSpan(url), start, sb.length, F)
                }
                italic != null -> {
                    sb.append(inline(italic))
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, F)
                }
            }
            last = m.range.last + 1
        }
        sb.append(src, last, src.length)
        return sb
    }
}
