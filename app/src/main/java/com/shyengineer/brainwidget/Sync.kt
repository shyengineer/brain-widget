package com.shyengineer.brainwidget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.Future

class GitHubError(val code: Int, msg: String) : Exception(msg)

/** GitHub 저장소(볼트)에서 노트를 읽어 캐시를 갱신한다. */
object Sync {
    private val SKIP = listOf("99template/", ".obsidian/", "Resource/", ".git")
    @Volatile var running = false
        private set

    private fun get(s: Settings, path: String): JSONObject {
        val c = URL("https://api.github.com/repos/${s.owner}/${s.repo}/$path").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (s.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${s.token}")
        try {
            val code = c.responseCode
            if (code != 200) {
                val why = when (code) {
                    401 -> "토큰이 올바르지 않아요 (401). 설정에서 토큰을 확인해 주세요."
                    403 -> "접근이 거부됐어요 (403). 토큰 권한이나 요청 한도를 확인해 주세요."
                    404 -> "저장소나 브랜치를 찾을 수 없어요 (404). 설정의 저장소 이름과 토큰 권한을 확인해 주세요."
                    else -> "GitHub 오류 ($code)"
                }
                throw GitHubError(code, why)
            }
            return JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).readText())
        } finally {
            c.disconnect()
        }
    }

    /** 실제 동기화. 백그라운드 스레드에서 호출할 것. 성공하면 true. */
    fun run(ctx: Context): Boolean {
        if (running) return false
        running = true
        val s = Settings(ctx)
        val cache = Cache.of(ctx)
        val pool = Executors.newFixedThreadPool(6)
        try {
            val branch = URLEncoder.encode(s.branch, "UTF-8")
            val tree = get(s, "git/trees/$branch?recursive=1").getJSONArray("tree")
            val wanted = HashMap<String, String>() // path -> sha
            for (i in 0 until tree.length()) {
                val it = tree.getJSONObject(i)
                val path = it.getString("path")
                if (it.getString("type") != "blob" || !path.endsWith(".md")) continue
                if (SKIP.any { p -> path.startsWith(p) }) continue
                wanted[path] = it.getString("sha")
            }

            // 시간표 노트 경로: 설정값, 없으면 가장 최근 학기의 "시간표.md"
            val ttPath = s.timetablePath.ifEmpty {
                wanted.keys.filter { it.endsWith("/시간표.md") || it == "시간표.md" }.maxOrNull() ?: ""
            }

            val jobs = ArrayList<Pair<String, Future<String>>>()
            for ((path, sha) in wanted) {
                val old = cache.files[path]
                val needText = path == ttPath && (cache.timetablePath != ttPath || old?.sha != sha)
                if (old?.sha == sha && !needText) continue
                jobs.add(path to pool.submit<String> {
                    val b = get(s, "git/blobs/$sha")
                    val bytes = Base64.decode(b.getString("content"), Base64.DEFAULT)
                    String(bytes, Charsets.UTF_8)
                })
            }
            for ((path, f) in jobs) {
                val text = f.get()
                cache.files[path] = Cache.Entry(wanted[path]!!, Parser.tasks(path, text))
                if (path == ttPath) { cache.timetablePath = ttPath; cache.timetableText = text }
            }
            cache.files.keys.retainAll(wanted.keys)
            if (ttPath.isEmpty()) { cache.timetablePath = ""; cache.timetableText = "" }
            cache.syncedAt = System.currentTimeMillis()
            cache.error = ""
            cache.save()
            return true
        } catch (e: Exception) {
            cache.error = when (e) {
                is GitHubError -> e.message ?: "GitHub 오류"
                is java.util.concurrent.ExecutionException -> (e.cause as? GitHubError)?.message ?: "네트워크 오류: ${e.cause?.message}"
                else -> "네트워크 오류: ${e.message}"
            }
            cache.save()
            return false
        } finally {
            pool.shutdownNow()
            running = false
            BrainWidget.refreshAll(ctx)
        }
    }

    /** 다른 스레드에서 동기화하고 끝나면 done 을 호출 (done 은 그 스레드에서 실행됨) */
    fun runAsync(ctx: Context, done: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        Thread { val ok = run(app); done?.invoke(ok) }.start()
    }

    private const val PERIODIC_ID = 1
    private const val NOW_ID = 2

    /** 1시간마다 (네트워크 있을 때) 자동 동기화 */
    fun schedulePeriodic(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(PERIODIC_ID) != null) return
        js.schedule(
            JobInfo.Builder(PERIODIC_ID, ComponentName(ctx, SyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L)
                .setPersisted(true)
                .build()
        )
    }

    /** 위젯 새로고침 버튼: 지금 바로 동기화 */
    fun scheduleNow(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        js.schedule(
            JobInfo.Builder(NOW_ID, ComponentName(ctx, SyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
                .build()
        )
    }
}

class SyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            Sync.run(applicationContext)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Sync.schedulePeriodic(context)
    }
}
