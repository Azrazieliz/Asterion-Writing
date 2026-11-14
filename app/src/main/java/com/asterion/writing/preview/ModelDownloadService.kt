package com.asterion.writing.preview

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit user-selected model download. Resumes verified byte prefixes using HTTP Range. */
class ModelDownloadService : Service() {
    companion object {
        const val ACTION_START = "com.asterion.writing.DOWNLOAD"
        const val ACTION_CANCEL = "com.asterion.writing.CANCEL_DOWNLOAD"
        const val KEY_STATUS = "download_status"
        const val KEY_PROGRESS = "download_percent"
        const val KEY_ACTIVE = "download_active"
        private const val CHANNEL = "model_downloads"
        private const val NOTIFY_ID = 5102
        private val cancel = AtomicBoolean(false)
        fun modelsDir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }
        fun modelName(f: ModelFile): String = f.repo.replace('/', '_') + "__" + f.path.substringAfterLast('/')
        fun start(context: Context, f: ModelFile) {
            val i = Intent(context, ModelDownloadService::class.java).setAction(ACTION_START)
                .putExtra("repo", f.repo).putExtra("path", f.path).putExtra("bytes", f.bytes).putExtra("sha", f.sha256)
            context.startForegroundService(i)
        }
        fun cancel(context: Context) = context.startService(Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL))
    }
    private var worker: Thread? = null
    private val prefs get() = getSharedPreferences("writer", MODE_PRIVATE)
    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); val m=getSystemService(NotificationManager::class.java); m.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancel.set(true); return START_NOT_STICKY }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (worker?.isAlive == true) { status("Another model download is running", true); return START_NOT_STICKY }
        cancel.set(false)
        val repo = intent.getStringExtra("repo") ?: return START_NOT_STICKY
        val path = intent.getStringExtra("path") ?: return START_NOT_STICKY
        val f = ModelFile(repo, path, intent.getLongExtra("bytes", 0), intent.getStringExtra("sha"))
        val notif = notification("Preparing ${f.path.substringAfterLast('/')}", 0)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFY_ID, notif)
        worker = Thread({ try { download(f) } catch (t: Exception) { status("Failed: ${t.message}", false) }
            finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) } }, "HuggingFace-Download").apply { start() }
        return START_REDELIVER_INTENT
    }
    private fun notification(text: String, progress: Int): Notification {
        val cancelIntent = PendingIntent.getService(this, 2, Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Asterion Writing · Hugging Face").setContentText(text)
            .setProgress(100, progress.coerceIn(0, 100), progress < 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent).build()
    }
    private fun status(message: String, active: Boolean, percent: Int = 0) {
        prefs.edit().putString(KEY_STATUS, message).putBoolean(KEY_ACTIVE, active).putInt(KEY_PROGRESS, percent).apply()
        if (active) getSystemService(NotificationManager::class.java).notify(NOTIFY_ID, notification(message, percent))
    }
    private fun download(f: ModelFile) {
        require(HuggingFaceCatalog.normalizeRepo(f.repo) == f.repo)
        require(f.path.matches(Regex("[A-Za-z0-9_./-]+\\.gguf", RegexOption.IGNORE_CASE)) && !f.path.contains(".."))
        require(f.bytes > 4096) { "Hugging Face did not provide a valid file size" }
        val target = File(modelsDir(this), modelName(f))
        val partial = File(target.absolutePath + ".part")
        if (target.isFile && target.length() == f.bytes) { status("Already downloaded: ${target.name}", false, 100); return }
        if (partial.length() > f.bytes) partial.delete()
        val remaining = f.bytes - partial.length()
        require(StatFs(target.parentFile!!.path).availableBytes > remaining + 256L * 1024L * 1024L) { "Insufficient local space. Free storage before downloading." }
        var from = partial.length()
        val escaped = f.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val url = "https://huggingface.co/${f.repo}/resolve/main/$escaped?download=true"
        status("Downloading ${target.name}", true, (from * 100L / f.bytes).toInt())
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000; conn.readTimeout = 45000; conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "AsterionWriting/0.1")
        if (from > 0L) conn.setRequestProperty("Range", "bytes=$from-")
        try {
            val code = conn.responseCode
            if (code == 200 && from > 0L) { partial.delete(); from = 0L }
            else require(code == 200 || (code == 206 && from > 0)) { "HTTP $code; cannot resume model" }
            FileOutputStream(partial, from > 0L).use { out ->
                conn.inputStream.use { input ->
                    val bytes = ByteArray(1024 * 1024)
                    var total = from; var lastPct = -1
                    while (true) {
                        if (cancel.get()) { status("Paused: ${target.name}", false, (total * 100 / f.bytes).toInt()); return }
                        val n = input.read(bytes)
                        if (n == -1) break
                        out.write(bytes, 0, n); total += n
                        val pct = (total * 100L / f.bytes).toInt().coerceIn(0, 100)
                        if (pct != lastPct) { lastPct = pct; status("${target.name}: $pct%", true, pct) }
                    }
                    out.fd.sync()
                }
            }
        } finally { conn.disconnect() }
        require(partial.length() == f.bytes) { "Incomplete transfer: ${partial.length()} of ${f.bytes} bytes (resume available)" }
        require(partial.inputStream().use { it.readNBytes(4) }.contentEquals(byteArrayOf(71, 71, 85, 70))) { "Downloaded file is not GGUF" }
        val sha = hash(partial)
        if (f.sha256 != null) require(f.sha256.equals(sha, ignoreCase = true)) { "SHA-256 mismatch; refusing to install" }
        require(partial.renameTo(target)) { "Cannot finalize verified model" }
        prefs.edit().putString("model_sha_${target.name}", sha).putString("model_repo_${target.name}", f.repo).putString("model_path_${target.name}", f.path).apply()
        status("Ready: ${target.name}" + if (f.sha256 == null) " (local checksum, remote hash unavailable)" else " (SHA-256 verified)", false, 100)
    }
    private fun hash(file: File): String {
        val dig = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { i -> val b=ByteArray(1024*1024); while (true) { val n=i.read(b); if (n<0) break; dig.update(b,0,n) } }
        return dig.digest().joinToString("") { "%02x".format(it) }
    }
}
