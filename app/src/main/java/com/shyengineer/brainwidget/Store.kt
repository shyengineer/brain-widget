package com.shyengineer.brainwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** 설정값 (저장소, 토큰 등) */
class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var owner: String
        get() = sp.getString("owner", "shyengineer")!!
        set(v) = sp.edit().putString("owner", v.trim()).apply()
    var repo: String
        get() = sp.getString("repo", "second-brain")!!
        set(v) = sp.edit().putString("repo", v.trim()).apply()
    var branch: String
        get() = sp.getString("branch", "main")!!
        set(v) = sp.edit().putString("branch", v.trim()).apply()
    var token: String
        get() = sp.getString("token", "")!!
        set(v) = sp.edit().putString("token", v.trim()).apply()
    /** 비워 두면 가장 최근 학기의 시간표.md 를 자동으로 찾는다 */
    var timetablePath: String
        get() = sp.getString("timetablePath", "")!!
        set(v) = sp.edit().putString("timetablePath", v.trim()).apply()
}

/**
 * 동기화 결과 캐시. 노트마다 (sha, 할 일 목록)을 저장해서
 * 다음 동기화 때는 바뀐 노트만 내려받는다.
 */
class Cache(private val file: File) {
    class Entry(val sha: String, val tasks: List<Task>)

    val files = HashMap<String, Entry>()
    var timetablePath = ""
    var timetableText = ""
    var syncedAt = 0L
    var error = ""

    companion object {
        private val lock = Any()
        fun of(ctx: Context) = Cache(File(ctx.filesDir, "cache.json")).also { it.load() }
    }

    fun load() = synchronized(lock) {
        files.clear()
        if (!file.exists()) return@synchronized
        runCatching {
            val o = JSONObject(file.readText())
            val fs = o.optJSONObject("files") ?: JSONObject()
            for (path in fs.keys()) {
                val e = fs.getJSONObject(path)
                val arr = e.optJSONArray("tasks") ?: JSONArray()
                val tasks = (0 until arr.length()).map { i ->
                    val t = arr.getJSONObject(i)
                    val d = t.optString("d", "")
                    Task(t.getString("t"), if (d.isEmpty()) null else LocalDate.parse(d), path)
                }
                files[path] = Entry(e.getString("sha"), tasks)
            }
            timetablePath = o.optString("ttPath", "")
            timetableText = o.optString("ttText", "")
            syncedAt = o.optLong("syncedAt", 0L)
            error = o.optString("error", "")
        }
    }

    fun save() = synchronized(lock) {
        val fs = JSONObject()
        for ((path, e) in files) {
            val arr = JSONArray()
            for (t in e.tasks) arr.put(JSONObject().put("t", t.text).put("d", t.due?.toString() ?: ""))
            fs.put(path, JSONObject().put("sha", e.sha).put("tasks", arr))
        }
        val o = JSONObject()
            .put("files", fs)
            .put("ttPath", timetablePath)
            .put("ttText", timetableText)
            .put("syncedAt", syncedAt)
            .put("error", error)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    fun timetable(): List<ClassSlot> = Parser.timetable(timetableText)

    /**
     * 화면에 보여 줄 할 일:
     * - 마감일이 있는 것: 30일 전 ~ 앞으로 (지난 건 '밀림'으로 표시)
     * - 마감일이 없는 것: 최근 7일 데일리 노트와 이번 주 주간 노트에 있는 것만
     */
    fun visibleTasks(today: LocalDate = LocalDate.now()): List<Task> {
        val recentDaily = (0..7).map { "04_Life/00_Diary/${today.minusDays(it.toLong())}.md" }.toSet()
        val iso = today.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR).toString() + "-W" +
            today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString().padStart(2, '0')
        val weekly = "04_Life/02_Weekly note/$iso.md"
        val out = ArrayList<Task>()
        for ((path, e) in files) {
            for (t in e.tasks) {
                if (t.due != null) {
                    if (!t.due.isBefore(today.minusDays(30))) out.add(t)
                } else if (path in recentDaily || path == weekly) {
                    out.add(t)
                }
            }
        }
        return out.distinctBy { it.text to it.due }
            .sortedWith(compareBy<Task>({ it.due == null }, { it.due }, { it.path }))
    }
}
