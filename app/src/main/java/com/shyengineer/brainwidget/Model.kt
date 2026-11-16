package com.shyengineer.brainwidget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Task(val text: String, val due: LocalDate?, val path: String)

data class ClassSlot(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val name: String,
    val professor: String,
    val place: String,
)

object Parser {
    /** 이 제목 아래의 체크박스는 할 일이 아니라 습관 체크라서 뺀다 */
    private val SKIP_HEADINGS = listOf("운동")
    private val openTask =Regex("""^\s*[-*+] \[ \] (.*)$""")
    private val dueRe = Regex("""📅\s*(\d{4}-\d{2}-\d{2})""")
    private val cleanups = listOf(
        Regex("""[📅⏳🛫✅➕❌]\s*\d{4}-\d{2}-\d{2}"""),
        Regex("""🔁[^📅⏳🛫✅➕⏫🔼🔽🔺]*"""),
        Regex("""[⏫🔼🔽🔺]"""),
        Regex("""\[([^\]]*)]\([^)]*\)"""),  // 마크다운 링크는 글자만 남김
        Regex("""\[\[(?:[^\]|]*\|)?([^\]]*)]]"""), // 위키링크도 글자만
    )

    /** 노트 한 개에서 안 끝난 할 일을 뽑는다. 코드 블록 안은 무시. */
    fun tasks(path: String, text: String): List<Task> {
        val out = ArrayList<Task>()
        var inCode = false
        var skipSection = false
        for (raw in text.lines()) {
            if (raw.trimStart().startsWith("```")) { inCode = !inCode; continue }
            if (inCode) continue
            if (raw.startsWith("#")) { skipSection = SKIP_HEADINGS.any { raw.contains(it) }; continue }
            if (skipSection) continue
            val m = openTask.find(raw) ?: continue
            val body = m.groupValues[1]
            val due = dueRe.find(body)?.groupValues?.get(1)?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }
            var t = body
            cleanups.forEachIndexed { i, re ->
                t = if (i >= 3) t.replace(re) { it.groupValues[1] } else t.replace(re, "")
            }
            t = t.replace(Regex("""\s+"""), " ").trim()
            if (t.isEmpty()) continue
            out.add(Task(t, due, path))
        }
        return out
    }

    private val days = mapOf(
        "월" to DayOfWeek.MONDAY, "화" to DayOfWeek.TUESDAY, "수" to DayOfWeek.WEDNESDAY,
        "목" to DayOfWeek.THURSDAY, "금" to DayOfWeek.FRIDAY, "토" to DayOfWeek.SATURDAY,
        "일" to DayOfWeek.SUNDAY,
    )

    private fun time(s: String): LocalTime? = runCatching {
        val (h, m) = s.trim().split(":").map { it.trim().toInt() }
        LocalTime.of(h, m)
    }.getOrNull()

    /** | 요일 | 시작 | 끝 | 과목 | 교수 | 장소 | 표를 읽는다. */
    fun timetable(text: String): List<ClassSlot> {
        val out = ArrayList<ClassSlot>()
        for (line in text.lines()) {
            val l = line.trim()
            if (!l.startsWith("|")) continue
            val cells = l.trim('|').split("|").map { it.trim() }
            if (cells.size < 4) continue
            val day = days[cells[0].take(1)] ?: continue
            val start = time(cells[1]) ?: continue
            val end = time(cells[2]) ?: continue
            out.add(ClassSlot(day, start, end, cells[3], cells.getOrElse(4) { "" }, cells.getOrElse(5) { "" }))
        }
        return out.sortedWith(compareBy({ it.day }, { it.start }))
    }
}
