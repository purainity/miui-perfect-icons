package me.purainity.miuiperfecticons

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.text.format.Formatter
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import me.purainity.miuiperfecticons.config.AppSettings
import me.purainity.miuiperfecticons.databinding.ActivityMainBinding
import me.purainity.miuiperfecticons.databinding.ItemNavigationBinding
import me.purainity.miuiperfecticons.engine.GitHubRepository
import me.purainity.miuiperfecticons.engine.OriginalChangelog
import me.purainity.miuiperfecticons.ui.applySystemBarInsets
import me.purainity.miuiperfecticons.ui.exposeSelectionState
import me.purainity.miuiperfecticons.tasks.TaskLogDialog
import me.purainity.miuiperfecticons.tasks.TaskRuntime
import me.purainity.miuiperfecticons.tasks.TaskService
import me.purainity.miuiperfecticons.tasks.TaskSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** 应用首页：公告、更新入口、配置卡、生成按钮与文档外链 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private lateinit var themeRow: ItemNavigationBinding
    private lateinit var outputRow: ItemNavigationBinding
    private lateinit var updateRow: ItemNavigationBinding
    private lateinit var customRow: ItemNavigationBinding
    private lateinit var dynamicRow: ItemNavigationBinding
    private lateinit var cacheRow: ItemNavigationBinding
    private val io = Executors.newSingleThreadExecutor()
    // 任务结果由任务面板与完成通知展示，首页不再弹出 Snackbar
    private val taskListener: (TaskSnapshot) -> Unit = { state ->
        binding.viewTask.visibility = if (state.running) View.VISIBLE else View.GONE
    }

    private var pendingTask: String? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingTask?.let { startTask(it) }
        pendingTask = null
    }

    private val outputDirectoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                // 必须先获得持久写入授权，再更新保存的目录
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val previousDirectory = settings.outputDirectory
                settings.outputDirectory = uri
                if (previousDirectory != uri) releaseOutputPermission(previousDirectory)
                updateOutputDirectory()
            } catch (_: SecurityException) {
                showMessage(R.string.output_directory_permission_failed)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        settings = AppSettings(this)

        updateRow = addRow(
            binding.updateRows,
            R.drawable.ic_history,
            R.string.update_title,
            getString(R.string.update_subtitle),
        ) { openFeature(FeatureActivity.UPDATES) }
        themeRow = addRow(binding.configurationRows, R.drawable.ic_palette, R.string.choose_theme) {
            startActivity(Intent(this, ThemeSelectionActivity::class.java))
        }
        customRow = addRow(
            binding.configurationRows,
            R.drawable.ic_tune,
            R.string.custom_icons,
        ) { openFeature(FeatureActivity.CUSTOM) }
        dynamicRow = addRow(
            binding.configurationRows,
            R.drawable.ic_animation,
            R.string.disable_dynamic_icons,
        ) { openFeature(FeatureActivity.DYNAMIC) }
        outputRow = addRow(binding.configurationRows, R.drawable.ic_folder, R.string.output_directory) {
            chooseOutputDirectory()
        }
        binding.maskSwitch.isChecked = settings.useNewMask
        binding.maskSetting.exposeSelectionState(binding.maskSwitch)
        binding.maskSetting.setOnClickListener { binding.maskSwitch.isChecked = !binding.maskSwitch.isChecked }
        binding.maskSwitch.setOnCheckedChangeListener { _, checked -> settings.useNewMask = checked }
        binding.maskSystemUnknown.visibility = if (settings.systemVersion == null) View.VISIBLE else View.GONE
        binding.maskSetting.contentDescription = listOfNotNull(
            getString(R.string.new_mask), getString(R.string.new_mask_description),
            getString(R.string.new_mask_system_unknown).takeIf { settings.systemVersion == null },
        ).joinToString("，")
        addDocumentationRows()
        binding.generateMtz.setOnClickListener { confirmGeneration(TaskService.MTZ) }
        binding.generateModule.setOnClickListener { confirmGeneration(TaskService.MODULE) }
        binding.viewTask.setOnClickListener { showTaskLog() }
        pendingTask = savedInstanceState?.getString("pendingTask")
    }

    override fun onResume() {
        super.onResume()
        // 主题行直接显示当前显示名，不加前缀
        themeRow.subtitle.text = settings.theme.displayName
        themeRow.subtitle.visibility = View.VISIBLE
        updateOutputDirectory()
        customRow.subtitle.text = getString(if (settings.customEnabled) R.string.state_enabled else R.string.state_disabled)
        customRow.subtitle.visibility = View.VISIBLE
        dynamicRow.subtitle.text = when {
            settings.disableAllDynamic -> getString(R.string.dynamic_disabled_all)
            settings.disabledPackages.isEmpty() -> getString(R.string.state_disabled)
            else -> getString(R.string.dynamic_selected_apps, settings.disabledPackages.size)
        }
        dynamicRow.subtitle.visibility = View.VISIBLE
        val content = getSharedPreferences("content", MODE_PRIVATE)
        val latest = content.getString("updates_latest_time", null)?.let { OriginalChangelog.latestDate(it) }
        updateRow.subtitle.text = if (latest == null) getString(R.string.update_check_hint) else
            getString(R.string.update_latest, latest) +
                if (content.getBoolean("resources_have_update", false)) getString(R.string.update_has_update_suffix) else ""
        refreshCacheSize()
        refreshAnnouncement()
    }

    override fun onStart() {
        super.onStart()
        TaskRuntime.observe(taskListener)
    }

    override fun onStop() {
        TaskRuntime.remove(taskListener)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingTask", pendingTask)
        super.onSaveInstanceState(outState)
    }

    private fun openFeature(page: String) {
        startActivity(Intent(this, FeatureActivity::class.java).putExtra(FeatureActivity.EXTRA_PAGE, page))
    }

    /** 后台统计缓存大小，统计范围含索引与快照，不含同目录的更新日志缓存 */
    private fun refreshCacheSize() {
        io.execute {
            val size = GitHubRepository.cacheSize(filesDir)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    cacheRow.subtitle.text = getString(R.string.cache_size_current, Formatter.formatFileSize(this, size))
                    cacheRow.subtitle.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun clearCache() {
        if (TaskRuntime.current().running || TaskRuntime.isCacheClearing()) {
            Snackbar.make(binding.root, getString(R.string.clear_cache_blocked_by_task), Snackbar.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_cache_title)
            .setMessage(R.string.clear_cache_message)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.clear_cache_action) { _, _ ->
                if (!TaskRuntime.beginCacheClear()) {
                    Snackbar.make(binding.root, getString(R.string.clear_cache_blocked_by_task), Snackbar.LENGTH_LONG).show()
                } else {
                    Thread({
                        val success = try {
                            GitHubRepository.clear(filesDir)
                        } finally {
                            TaskRuntime.endCacheClear()
                        }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                Snackbar.make(
                                    binding.root,
                                    getString(if (success) R.string.clear_cache_done else R.string.clear_cache_failed),
                                    Snackbar.LENGTH_LONG,
                                ).show()
                                refreshCacheSize()
                            }
                        }
                    }, "clear-icon-cache").start()
                }
            }.show()
    }

    private fun refreshAnnouncement() {
        val preferences = getSharedPreferences("content", MODE_PRIVATE)
        fun show(value: String?) {
            binding.announcementGroup.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.announcement.text = value.orEmpty()
        }
        // 先显示上次缓存，再异步拉取最新公告；拉取失败时保留原内容
        show(preferences.getString("announcement", null))
        io.execute {
            try {
                val url = URL("https://miuiicons-generic.pkg.coding.net/icons/files/announce3000.txt?version=latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                val value = try {
                    if (connection.responseCode != 200) throw java.io.IOException("公告请求失败")
                    connection.inputStream.bufferedReader().use { reader ->
                        // 公告最多读取 64 KB，防止异常超大响应耗尽内存
                        val text = StringBuilder()
                        val buffer = CharArray(4096)
                        while (text.length < 65536) {
                            val count = reader.read(buffer, 0, minOf(buffer.size, 65536 - text.length))
                            if (count < 0) break
                            text.appendRange(buffer, 0, count)
                        }
                        text.toString().trim()
                    }
                } finally { connection.disconnect() }
                if (value.isNotBlank()) {
                    preferences.edit { putString("announcement", value) }
                    runOnUiThread { if (!isFinishing && !isDestroyed) show(value) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun confirmGeneration(kind: String) {
        if (TaskRuntime.isCacheClearing()) {
            Snackbar.make(binding.root, getString(R.string.clear_cache_in_progress), Snackbar.LENGTH_SHORT).show()
            return
        }
        if (TaskRuntime.current().running) {
            showTaskLog()
            return
        }
        val message = if (kind == TaskService.MTZ) getString(R.string.confirm_mtz_message) else getString(R.string.confirm_module_message)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(if (kind == TaskService.MTZ) R.string.generate_mtz else R.string.generate_module_title))
            .setMessage("$message\n\n${getString(R.string.confirm_generation_note)}")
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_continue) { _, _ -> requestTask(kind) }
            .show()
    }

    private fun requestTask(kind: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingTask = kind
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startTask(kind)
    }

    private fun startTask(kind: String) {
        if (!TaskRuntime.current().running) TaskService.start(this, kind)
        showTaskLog()
    }

    private fun showTaskLog() {
        if (supportFragmentManager.findFragmentByTag("task-log") == null) {
            TaskLogDialog().show(supportFragmentManager, "task-log")
        }
    }

    private fun chooseOutputDirectory() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.output_directory)
            .setItems(arrayOf(getString(R.string.choose_output_directory), getString(R.string.restore_default_directory))) { _, which ->
                when (which) {
                    0 -> try {
                        outputDirectoryPicker.launch(settings.outputDirectory)
                    } catch (_: ActivityNotFoundException) {
                        showMessage(R.string.directory_picker_unavailable)
                    }
                    1 -> {
                        val previousDirectory = settings.outputDirectory
                        settings.outputDirectory = null
                        releaseOutputPermission(previousDirectory)
                        updateOutputDirectory()
                    }
                }
            }
            .show()
    }

    private fun releaseOutputPermission(uri: Uri?) {
        // 后台任务可能仍在使用原目录授权，不能在任务结束前撤销
        if (uri == null || uri == settings.customDirectory || TaskRuntime.current().running) return
        val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try {
            contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // 授权可能已被系统撤销，撤销失败可忽略
        }
    }

    private fun updateOutputDirectory() {
        val uri = settings.outputDirectory
        val directory = when {
            uri == null -> getString(R.string.output_directory_default)
            contentResolver.persistedUriPermissions.none { it.uri == uri && it.isWritePermission } ->
                getString(R.string.output_directory_invalid)
            else -> getString(R.string.output_directory_value, treeName(uri))
        }
        outputRow.subtitle.text = directory
        outputRow.subtitle.visibility = View.VISIBLE
    }

    private fun treeName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (_: IllegalArgumentException) {
        uri.lastPathSegment ?: uri.authority.orEmpty()
    }

    /** 文档卡 12 条外链；卡片底部另附清理缓存与关于两行 */
    private fun addDocumentationRows() {
        val links = listOf(
            R.string.doc_preview to "https://icons-demo.pedroz.eu.org/",
            R.string.doc_intro to "https://www.yuque.com/pedeoz/icons/crit9tn6d1334zt0",
            R.string.doc_miui_guide to "https://www.yuque.com/pedeoz/icons/er8ige3ldvc7wn1n",
            R.string.doc_custom_guide to "https://www.yuque.com/pedeoz/icons/bg668dqpli7brqgi",
            R.string.doc_adaptation to "https://www.yuque.com/pedeoz/icons/ao7yvw37irgdghr6",
            R.string.doc_release to "https://www.yuque.com/pedeoz/icons/xpqqtku1lkacpal4",
            R.string.doc_icons_github to "https://github.com/pzcn/Perfect-Icons-Completion-Project",
            R.string.doc_app_github to "https://github.com/pzcn/MIUI-Icons-APP",
            R.string.doc_telegram_channel to "https://t.me/miuiicons",
            R.string.doc_telegram_group to "https://t.me/miui_icons_dev",
            R.string.doc_qq to "https://qm.qq.com/cgi-bin/qm/qr?k=_92-RGlaiz3Xm-9i8O3USqTSg29EYg44",
            R.string.doc_privacy to "https://iconsx.pedroz.eu.org/privacy.html",
        )
        links.forEach { (title, url) ->
            val icon = when (title) {
                R.string.doc_preview -> R.drawable.ic_preview
                R.string.doc_intro, R.string.doc_miui_guide, R.string.doc_custom_guide,
                R.string.doc_adaptation, R.string.doc_release -> R.drawable.ic_docs
                R.string.doc_icons_github, R.string.doc_app_github -> R.drawable.ic_github
                R.string.doc_telegram_channel, R.string.doc_telegram_group -> R.drawable.ic_telegram
                R.string.doc_qq -> R.drawable.ic_qq
                R.string.doc_privacy -> R.drawable.ic_privacy
                else -> R.drawable.ic_link
            }
            addRow(binding.documentationRows, icon, title, external = true) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    showMessage(R.string.open_link_failed)
                }
            }
        }
        cacheRow = addRow(binding.documentationRows, R.drawable.ic_delete_sweep, R.string.clear_cache) { clearCache() }
        addRow(binding.documentationRows, R.drawable.ic_info, R.string.about) { openFeature(FeatureActivity.ABOUT) }
    }

    /** 通用列表行：图标 + 标题 + 当前值（副标题）+ 尾部图标（导航或外链） */
    private fun addRow(
        parent: LinearLayout,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        subtitle: String? = null,
        external: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): ItemNavigationBinding {
        val row = ItemNavigationBinding.inflate(layoutInflater, parent, true)
        row.icon.setImageResource(icon)
        row.title.setText(title)
        row.subtitle.text = subtitle
        row.subtitle.visibility = if (subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (onClick == null) {
            row.trailingIcon.visibility = View.GONE
        } else {
            row.trailingIcon.setImageResource(if (external) R.drawable.ic_external else R.drawable.ic_chevron)
            row.root.isFocusable = true
            row.root.setOnClickListener { onClick() }
        }
        return row
    }

    private fun showMessage(@StringRes message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
