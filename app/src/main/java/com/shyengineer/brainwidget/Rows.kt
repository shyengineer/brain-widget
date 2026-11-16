package com.shyengineer.brainwidget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 위젯과 앱이 같이 쓰는 '한 줄' 데이터 */
data class Row(
    val kind: Kind,
    val badge: String = "",
    val title: String = "",
    val sub: String = "",
    val tone: Tone = Tone.NORMAL,
) {
    enum class Kind { HEADER, ITEM }
    enum class Tone { NORMAL, DIM, NOW, WARN }
}

object Rows {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("M/d")

    fun dayLabel(d: LocalDate): String =
        "${d.format(md)} (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

    fun classesFor(slots: List<ClassSlot>, day: DayOfWeek) = slots.filter { it.day == day }

    fun dueBadge(due: LocalDate?, today: LocalDate): Pair<String, Row.Tone> {
        if (due == null) return "" to Row.Tone.NORMAL
        val diff = ChronoUnit.DAYS.between(today, due)
        return when {
            diff < 0 -> "${due.format(md)} 지남" to Row.Tone.WARN
            diff == 0L -> "오늘" to Row.Tone.NOW
            diff == 1L -> "내일" to Row.Tone.NORMAL
            else -> "D-$diff" to Row.Tone.NORMAL
        }
    }

    /** 위젯 목록: 오늘(또는 내일) 수업 + 일주일 안 할 일 */
    fun widgetRows(cache: Cache, now: LocalDateTime = LocalDateTime.now()): List<Row> {
        val today = now.toLocalDate()
        val out = ArrayList<Row>()
        if (cache.error.isNotEmpty()) out.add(Row(Row.Kind.ITEM, "⚠", cache.error, tone = Row.Tone.WARN))

        val slots = cache.timetable()
        val todays = classesFor(slots, today.dayOfWeek)
        val remaining = todays.filter { it.end.isAfter(now.toLocalTime()) }
        out.add(Row(Row.Kind.HEADER, title = "오늘 수업 · ${dayLabel(today)}"))
        if (todays.isEmpty()) out.add(Row(Row.Kind.ITEM, title = "수업 없음", tone = Row.Tone.DIM))
        for (c in todays) {
            val t = now.toLocalTime()
            val tone = when {
                !c.end.isAfter(t) -> Row.Tone.DIM
                !c.start.isAfter(t) -> Row.Tone.NOW
                else -> Row.Tone.NORMAL
            }
            out.add(Row(Row.Kind.ITEM, c.start.format(hm), c.name, c.place, tone))
        }
        if (remaining.isEmpty() && slots.isNotEmpty()) {
            // 오늘 수업이 다 끝났으면 다음 수업 있는 날을 보여 준다
            for (i in 1..7) {
                val d = today.plusDays(i.toLong())
                val next = classesFor(slots, d.dayOfWeek)
                if (next.isNotEmpty()) {
                    out.add(Row(Row.Kind.HEADER, title = "다음 수업 · ${dayLabel(d)}"))
                    for (c in next) out.add(Row(Row.Kind.ITEM, c.start.format(hm), c.name, c.place))
                    break
                }
            }
        }

        val tasks = cache.visibleTasks(today).filter { it.due == null || !it.due.isAfter(today.plusDays(7)) }
        out.add(Row(Row.Kind.HEADER, title = "할 일"))
        if (tasks.isEmpty()) out.add(Row(Row.Kind.ITEM, title = "일주일 안에 할 일 없음", tone = Row.Tone.DIM))
        for (t in tasks) {
            val (badge, tone) = dueBadge(t.due, today)
            out.add(Row(Row.Kind.ITEM, badge, t.text, tone = tone))
        }
        return out
    }

    /** 앱 화면의 할 일 목록 (기간 제한 없음) */
    fun taskRows(cache: Cache, today: LocalDate = LocalDate.now()): List<Row> =
        cache.visibleTasks(today).map { t ->
            val (badge, tone) = dueBadge(t.due, today)
            Row(Row.Kind.ITEM, badge, t.text, t.path.substringAfterLast('/').removeSuffix(".md"), tone)
        }
}
