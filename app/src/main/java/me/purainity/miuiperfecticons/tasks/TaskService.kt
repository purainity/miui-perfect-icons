package me.purainity.miuiperfecticons.tasks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import me.purainity.miuiperfecticons.MainActivity
import me.purainity.miuiperfecticons.R
import me.purainity.miuiperfecticons.config.AppSettings
import me.purainity.miuiperfecticons.engine.ArchivePacker
import me.purainity.miuiperfecticons.engine.AssemblyOptions
import me.purainity.miuiperfecticons.engine.DocumentStorage
import me.purainity.miuiperfecticons.engine.GitHubRepository
import me.purainity.miuiperfecticons.engine.PackageAssembler
import me.purainity.miuiperfecticons.engine.PackageFormat
import me.purainity.miuiperfecticons.engine.RateLimitException
import me.purainity.miuiperfecticons.engine.RepositoryRules
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** 承载资源下载与产物装配的前台服务，任务状态经 TaskRuntime 分发到界面 */
class TaskService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var destroyed = false
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.task_channel), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            TaskRuntime.cancelled.set(true)
            TaskRuntime.status(getString(R.string.task_log_cancelling))
            return START_NOT_STICKY
        }
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: run { stopSelf(); return START_NOT_STICKY }
        if (kind !in setOf(MTZ, MODULE, SYNC, ARCHIVE)) { stopSelf(); return START_NOT_STICKY }
        if (!TaskRuntime.begin(kind, getString(R.string.task_stage_preparing))) {
            if (!TaskRuntime.current().running) {
                startForeground(NOTIFICATION_ID, notification(getString(R.string.task_stage_preparing), 0, 0))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        val notification = notification(getString(R.string.task_stage_preparing), 0, 0)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val useCache = intent.getBooleanExtra(EXTRA_USE_CACHE, false)
        executor.execute {
            // 任务体抛出的异常在此统一转为失败通知与界面状态
            var output: DocumentStorage.SavedFile? = null
            var failure: String? = null
            var cancelled = false
            var canUseCache = false
            try {
                output = runTask(kind, useCache)
            } catch (_: InterruptedException) {
                cancelled = true
                TaskRuntime.status(getString(R.string.task_log_cancelled))
            } catch (error: Exception) {
                failure = error.message ?: getString(R.string.task_failed_generic)
                TaskRuntime.status(failure)
                if (error is RateLimitException && kind in setOf(MTZ, MODULE)) {
                    try {
                        canUseCache = GitHubRepository(filesDir, TaskRuntime::ensureActive)
                            .hasCompleteSnapshot(AppSettings(this).theme.id)
                    } catch (_: InterruptedException) {
                        cancelled = true
                        failure = null
                        TaskRuntime.status(getString(R.string.task_log_cancelled))
                    }
                }
            }
            val result = output
            val message = failure
            val isCancelled = cancelled
            val cached = canUseCache
            android.os.Handler(mainLooper).post {
                if (!destroyed) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (!isCancelled) {
                        try { notifications.notify(NOTIFICATION_ID, finishedNotification(kind, result, message)) } catch (_: SecurityException) { }
                    }
                    stopSelf()
                }
                TaskRuntime.update {
                    it.copy(running = false, stage = when {
                        isCancelled -> getString(R.string.task_stage_cancelled)
                        message == null -> getString(R.string.task_stage_completed)
                        else -> getString(R.string.task_stage_failed)
                    },
                        resultUri = result?.uri, resultMime = result?.mime ?: "application/octet-stream", resultLabel = result?.label,
                        error = message, canUseCache = cached)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun runTask(kind: String, useCache: Boolean): DocumentStorage.SavedFile? {
        val settings = AppSettings(this)
        val storage = DocumentStorage(this, TaskRuntime::ensureActive)
        val theme = settings.theme
        val newMask = settings.useNewMask
        val disableAll = settings.disableAllDynamic
        val disabledPackages = settings.disabledPackages
        val customEnabled = settings.customEnabled
        val customDirectory = settings.customDirectory
        val outputTree = if (kind == ARCHIVE) settings.customDirectory else settings.outputDirectory
        if ((kind == MTZ || kind == MODULE || kind == ARCHIVE) && outputTree != null && !storage.hasWriteAccess(outputTree)) {
            throw IOException(getString(R.string.task_error_output_directory))
        }
        if (kind == ARCHIVE && outputTree == null) throw IOException(getString(R.string.task_error_custom_directory_required))
        if ((kind == MTZ || kind == MODULE) && customEnabled && customDirectory == null) {
            throw IOException(getString(R.string.task_error_custom_directory_invalid))
        }
        var lastProgressUpdate = 0L
        val progressLock = Any()
        val repo = GitHubRepository(filesDir, TaskRuntime::ensureActive, TaskRuntime::status) { done, total ->
            synchronized(progressLock) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (done != total && done != 0L && now - lastProgressUpdate < 250) return@synchronized
                lastProgressUpdate = now
            TaskRuntime.update { it.copy(stage = getString(R.string.task_stage_downloading), completedBytes = done, totalBytes = total) }
            try { notifications.notify(NOTIFICATION_ID, notification(getString(R.string.task_notify_downloading), done, total)) } catch (_: SecurityException) { }
            }
        }
        val archiveEntries = if (kind == ARCHIVE) {
            val cached = repo.cachedArchiveEntries()
            if (cached != null) {
                TaskRuntime.status(getString(R.string.task_log_archive_cache_used))
                cached
            } else {
                if (useCache) throw IOException(getString(R.string.task_error_archive_cache_incomplete))
                repo.synchronize(theme.id, archiveOnly = true)
                repo.cachedArchiveEntries() ?: throw IOException(getString(R.string.task_error_archive_verify_failed))
            }
        } else {
            if (useCache) {
                if (!repo.hasCompleteSnapshot(theme.id)) throw IOException(getString(R.string.task_error_cache_incomplete))
                TaskRuntime.status(getString(R.string.task_log_cache_used))
            } else repo.synchronize(theme.id)
            null
        }
        if (!useCache && kind != ARCHIVE) getSharedPreferences("content", MODE_PRIVATE).edit { putBoolean("resources_have_update", false) }
        // 仅同步资源不产出文件
        if (kind == SYNC) {
            TaskRuntime.status(getString(R.string.task_log_sync_done))
            return null
        }
        val work = File(cacheDir, "task-${UUID.randomUUID()}")
        val result = File(cacheDir, "result-${UUID.randomUUID()}.zip")
        try {
            TaskRuntime.update { it.copy(stage = getString(R.string.task_stage_assembling), completedBytes = 0, totalBytes = 0) }
            val title = when (kind) {
                MODULE -> getString(R.string.task_notify_module)
                MTZ -> getString(R.string.task_notify_mtz)
                else -> getString(R.string.task_notify_archive)
            }
            try { notifications.notify(NOTIFICATION_ID, notification(title, 0, 0)) } catch (_: SecurityException) { }
            val date = Date()
            val timestamp = SimpleDateFormat("MMddHHmm", Locale.ROOT).format(date)
            val name: String
            val mime: String
            if (kind == ARCHIVE) {
                val archive = File(repo.files, "addition/Icons_Archiving")
                if (!archive.isDirectory) throw IOException(getString(R.string.task_error_archive_missing))
                TaskRuntime.status(getString(R.string.task_log_archive_packing))
                ArchivePacker(TaskRuntime::ensureActive).pack(
                    archive, archiveEntries.orEmpty().map { it.path.removePrefix(RepositoryRules.ARCHIVE_PREFIX) }, result,
                )
                name = getString(R.string.archive_filename)
                mime = "application/zip"
            } else {
                val custom = if (customEnabled) {
                    val uri = customDirectory ?: throw IOException(getString(R.string.task_error_custom_directory_invalid))
                    File(cacheDir, "custom-${UUID.randomUUID()}").also {
                        try { storage.snapshotCustom(uri, it) } catch (error: Throwable) { it.deleteRecursively(); throw error }
                    }
                } else null
                try {
                    val format = if (kind == MTZ) PackageFormat.MTZ else PackageFormat.MODULE
                    val options = AssemblyOptions(theme, newMask, disableAll, disabledPackages, custom, format, date)
                    // 遮罩模板优先取仓库缓存文件，仓库中的 V1 文件名与 assets 模板不同；缓存缺失时回退内置模板
                    val maskLoader: (String) -> ByteArray = { name ->
                        val repoName = if (name == "transform_config1.xml") "transform_config.xml" else name
                        val repoFile = File(repo.files, "addition/$repoName")
                        if (repoFile.isFile) repoFile.readBytes()
                        else assets.open(name).use { input -> input.readBytes() }
                    }
                    PackageAssembler(maskLoader, TaskRuntime::ensureActive, TaskRuntime::status)
                        .assemble(repo.files, work, result, options)
                } finally {
                    custom?.deleteRecursively()
                }
                name = if (kind == MTZ) "${theme.assemblyName}完美图标补全-$timestamp.mtz" else "${theme.assemblyName}模块-$timestamp.zip"
                mime = if (kind == MTZ) "application/octet-stream" else "application/zip"
            }
            TaskRuntime.ensureActive()
            TaskRuntime.status(getString(R.string.task_stage_saving))
            val saved = storage.save(result, name, mime, outputTree)
            val savedMessage = getString(
                when (kind) {
                    MTZ -> R.string.task_log_saved_mtz
                    MODULE -> R.string.task_log_saved_module
                    else -> R.string.task_log_saved_archive
                }, saved.label,
            )
            // MTZ 的破解警示与保存结果同屏多行显示
            TaskRuntime.status(if (kind == MTZ) "$savedMessage\n${getString(R.string.task_log_mtz_warning)}" else savedMessage)
            return saved
        } finally {
            work.deleteRecursively()
            result.delete()
        }
    }

    /** 进行中任务的进度通知，带取消动作 */
    private fun notification(title: String, done: Long, total: Long) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(if (total > 0) "${done / 1048576} / ${total / 1048576} MB" else getString(R.string.task_notify_background))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(mainPendingIntent())
        .setProgress(100, if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0, total <= 0)
        .addAction(0, getString(R.string.btn_cancel), PendingIntent.getService(this, 2, Intent(this, TaskService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    /** 完成通知：成功时点击打开产物，失败时提供重试 */
    private fun finishedNotification(kind: String, output: DocumentStorage.SavedFile?, failure: String?): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (failure == null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(getString(if (failure == null) R.string.task_notify_done else R.string.task_notify_failed))
            .setContentText(output?.label ?: failure ?: getString(R.string.task_notify_sync_done))
            .setAutoCancel(true)
            .setContentIntent(if (output == null) mainPendingIntent() else PendingIntent.getActivity(
                this, 3, Intent(Intent.ACTION_VIEW).setDataAndType(output.uri, output.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
        if (failure != null) {
            val retry = Intent(this, TaskService::class.java).putExtra(EXTRA_KIND, kind)
            val pending = PendingIntent.getForegroundService(this, 4, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, getString(R.string.btn_retry), pending)
        }
        return builder.build()
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onTimeout(startId: Int, fgsType: Int) {
        TaskRuntime.cancelled.set(true)
        TaskRuntime.status(getString(R.string.task_timeout))
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        if (TaskRuntime.current().running) TaskRuntime.cancelled.set(true)
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val MTZ = "mtz"
        const val MODULE = "module"
        const val SYNC = "sync"
        const val ARCHIVE = "archive"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_USE_CACHE = "use_cache"
        private const val ACTION_CANCEL = "cancel"
        private const val CHANNEL = "generation"
        private const val NOTIFICATION_ID = 10

        fun start(context: Context, kind: String, useCache: Boolean = false) {
            ContextCompat.startForegroundService(context, Intent(context, TaskService::class.java).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_USE_CACHE, useCache))
        }
        fun cancel() { TaskRuntime.cancelled.set(true) }
    }
}
