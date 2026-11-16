package com.shyengineer.brainwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 에브리타임 같은 주간 시간표 격자 */
class TimetableView(ctx: Context) : View(ctx) {
    var slots: List<ClassSlot> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }

    private val dp = resources.displayMetrics.density
    private val headerH = 28 * dp
    private val timeW = 26 * dp
    private val hourH = 56 * dp

    private val palette = intArrayOf(
        0xFF4E9A5B.toInt(), 0xFFE36D46.toInt(), 0xFFE9C03E.toInt(), 0xFF3DB8E6.toInt(),
        0xFF7A5545.toInt(), 0xFF2F80C2.toInt(), 0xFFC0392B.toInt(), 0xFF8E24AA.toInt(),
        0xFF00897B.toInt(), 0xFF5C6BC0.toInt(),
    )
    private val grid = Paint().apply { color = Color.parseColor("#E6E6E6"); strokeWidth = 1 * dp }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575"); textSize = 12 * dp; textAlign = Paint.Align.CENTER
    }
    private val todayLabel = Paint(label).apply { color = Color.parseColor("#1565C0"); typeface = Typeface.DEFAULT_BOLD }
    private val todayFill = Paint().apply { color = Color.parseColor("#F2F7FD") }
    private val block = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nameText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 11.5f * dp; typeface = Typeface.DEFAULT_BOLD
    }
    private val subText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); textSize = 10 * dp }
    private val nowLine = Paint().apply { color = Color.parseColor("#E53935"); strokeWidth = 2 * dp }

    private fun days(): List<DayOfWeek> {
        val weekend = slots.any { it.day == DayOfWeek.SATURDAY || it.day == DayOfWeek.SUNDAY }
        return if (weekend) DayOfWeek.values().toList() else DayOfWeek.values().take(5)
    }

    private fun hourRange(): IntRange {
        if (slots.isEmpty()) return 9..18
        val first = slots.minOf { it.start.hour }
        val last = slots.maxOf { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour }
        return minOf(first, 9)..maxOf(last, 18)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val r = hourRange()
        val h = headerH + (r.last - r.first) * hourH + 1 * dp
        setMeasuredDimension(w, h.toInt())
    }

    private fun colorFor(name: String): Int {
        val key = name.replace(Regex("""\s*\(.*\)$"""), "")
        return palette[Math.floorMod(key.hashCode(), palette.size)]
    }

    private fun y(t: LocalTime, first: Int) = headerH + ((t.hour - first) + t.minute / 60f) * hourH

    override fun onDraw(c: Canvas) {
        val ds = days()
        val r = hourRange()
        val colW = (width - timeW) / ds.size
        val today = LocalDate.now().dayOfWeek
        val todayIdx = ds.indexOf(today)

        if (todayIdx >= 0) c.drawRect(timeW + todayIdx * colW, 0f, timeW + (todayIdx + 1) * colW, height.toFloat(), todayFill)

        // 요일 머리
        val names = listOf("월", "화", "수", "목", "금", "토", "일")
        for ((i, d) in ds.withIndex()) {
            val p = if (i == todayIdx) todayLabel else label
            c.drawText(names[d.value - 1], timeW + i * colW + colW / 2, headerH * 0.68f, p)
        }
        // 가로줄 + 시간
        for (h in r) {
            val yy = headerH + (h - r.first) * hourH
            c.drawLine(0f, yy, width.toFloat(), yy, grid)
            if (h < r.last) {
                val shown = if (h > 12) h - 12 else h
                c.drawText(shown.toString(), timeW / 2, yy + 14 * dp, label)
            }
        }
        // 세로줄
        for (i in 0..ds.size) {
            val xx = timeW + i * colW
            c.drawLine(xx, headerH, xx, height.toFloat(), grid)
        }
        // 수업 블록
        val pad = 1.5f * dp
        for (s in slots) {
            val i = ds.indexOf(s.day)
            if (i < 0) continue
            val rect = RectF(timeW + i * colW + pad, y(s.start, r.first) + pad, timeW + (i + 1) * colW - pad, y(s.end, r.first) - pad)
            block.color = colorFor(s.name)
            c.drawRoundRect(rect, 4 * dp, 4 * dp, block)

            val innerW = (rect.width() - 6 * dp).toInt().coerceAtLeast(1)
            val text = s.name
            val name = StaticLayout.Builder.obtain(text, 0, text.length, nameText, innerW)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            c.save()
            c.translate(rect.left + 3 * dp, rect.top + 3 * dp)
            c.clipRect(0f, 0f, innerW.toFloat(), rect.height() - 6 * dp)
            name.draw(c)
            if (s.place.isNotEmpty()) {
                c.translate(0f, name.height.toFloat() + 1 * dp)
                val sub = StaticLayout.Builder.obtain(s.place, 0, s.place.length, subText, innerW)
                    .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
                sub.draw(c)
            }
            c.restore()
        }
        // 지금 시각 선
        val now = LocalTime.now()
        if (todayIdx >= 0 && now.hour >= r.first && now.hour < r.last) {
            val yy = y(now, r.first)
            c.drawLine(timeW + todayIdx * colW, yy, timeW + (todayIdx + 1) * colW, yy, nowLine)
        }
    }
}
