package com.shyengineer.brainwidget

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var table: TimetableView
    private lateinit var taskBox: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(px(12), px(12), px(12), px(24))
        }

        // 위쪽 막대: 제목 + 동기화 + 설정
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(TextView(this).apply {
            text = getString(R.string.app_name); textSize = 22f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B1B1F"))
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply { text = "동기화"; setOnClickListener { sync() } })
        bar.addView(Button(this).apply { text = "설정"; setOnClickListener { openSettings() } })
        root.addView(bar)

        status = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#757575")) }
        root.addView(status)

        root.addView(section("시간표"))
        table = TimetableView(this)
        root.addView(table, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(section("할 일"))
        taskBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(taskBox)

        setContentView(ScrollView(this).apply { addView(root) })

        Sync.schedulePeriodic(this)
        if (Settings(this).token.isEmpty()) openSettings()
    }

    override fun onResume() {
        super.onResume()
        render()
        val cache = Cache.of(this)
        if (Settings(this).token.isNotEmpty() && System.currentTimeMillis() - cache.syncedAt > 10 * 60 * 1000) sync()
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun section(title: String) = TextView(this).apply {
        text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#1B1B1F")); setPadding(0, px(18), 0, px(8))
    }

    private fun sync() {
        status.text = "동기화 중… (처음엔 노트 수에 따라 1분쯤 걸려요)"
        Sync.runAsync(this) { runOnUiThread { render() } }
    }

    private fun render() {
        val cache = Cache.of(this)
        val time = if (cache.syncedAt == 0L) "아직 동기화 안 함"
        else "마지막 동기화 " + SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(cache.syncedAt))
        status.text = if (Sync.running) "동기화 중…" else listOf(time, cache.error).filter { it.isNotEmpty() }.joinToString("\n")
        status.setTextColor(if (cache.error.isNotEmpty()) Color.parseColor("#C62828") else Color.parseColor("#757575"))

        table.slots = cache.timetable()

        taskBox.removeAllViews()
        val rows = Rows.taskRows(cache, LocalDate.now())
        if (rows.isEmpty()) taskBox.addView(TextView(this).apply {
            text = "표시할 할 일이 없어요"; setTextColor(Color.parseColor("#9E9E9E"))
        })
        for (r in rows) taskBox.addView(taskRow(r))
    }

    private fun taskRow(r: Row): LinearLayout {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, px(6), 0, px(6))
        }
        val badgeColor = when (r.tone) {
            Row.Tone.WARN -> "#C62828"; Row.Tone.NOW -> "#1565C0"; else -> "#616161"
        }
        line.addView(TextView(this).apply {
            text = r.badge; textSize = 12f; setTextColor(Color.parseColor(badgeColor))
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(px(72), WRAP_CONTENT))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = r.title; textSize = 15f; setTextColor(Color.parseColor("#1B1B1F")) })
        if (r.sub.isNotEmpty()) col.addView(TextView(this).apply { text = r.sub; textSize = 11f; setTextColor(Color.parseColor("#9E9E9E")) })
        line.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return line
    }

    private fun openSettings() {
        val s = Settings(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), 0) }
        fun field(hint: String, value: String, secret: Boolean = false) = EditText(this).apply {
            this.hint = hint; setText(value); isSingleLine = true
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(TextView(this@MainActivity).apply { text = hint; textSize = 12f; setPadding(0, px(8), 0, 0) })
            box.addView(this)
        }
        val token = field("GitHub 토큰 (읽기 전용)", s.token, secret = true)
        val owner = field("저장소 주인", s.owner)
        val repo = field("저장소 이름", s.repo)
        val branch = field("브랜치", s.branch)
        val tt = field("시간표 노트 경로 (비우면 자동)", s.timetablePath)

        AlertDialog.Builder(this)
            .setTitle("설정")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("저장") { _, _ ->
                s.token = token.text.toString(); s.owner = owner.text.toString()
                s.repo = repo.text.toString(); s.branch = branch.text.toString()
                s.timetablePath = tt.text.toString()
                sync()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
