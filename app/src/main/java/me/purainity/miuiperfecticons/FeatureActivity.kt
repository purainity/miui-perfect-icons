package me.purainity.miuiperfecticons

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import me.purainity.miuiperfecticons.config.AppSettings
import me.purainity.miuiperfecticons.databinding.ActivityFeatureBinding
import me.purainity.miuiperfecticons.databinding.ItemNavigationBinding
import me.purainity.miuiperfecticons.databinding.ItemOpenSourceProjectBinding
import me.purainity.miuiperfecticons.databinding.ItemSwitchSettingBinding
import me.purainity.miuiperfecticons.databinding.ViewFeatureGroupBinding
import me.purainity.miuiperfecticons.databinding.ViewAboutHeaderBinding
import me.purainity.miuiperfecticons.engine.DocumentStorage
import me.purainity.miuiperfecticons.engine.GitHubRepository
import me.purainity.miuiperfecticons.engine.OriginalChangelog
import me.purainity.miuiperfecticons.engine.OriginalChangelogRepository
import me.purainity.miuiperfecticons.tasks.TaskLogDialog
import me.purainity.miuiperfecticons.tasks.TaskRuntime
import me.purainity.miuiperfecticons.tasks.TaskSnapshot
import me.purainity.miuiperfecticons.tasks.TaskService
import me.purainity.miuiperfecticons.ui.CustomDirectoryAccess
import me.purainity.miuiperfecticons.ui.CustomDirectoryLocation
import me.purainity.miuiperfecticons.ui.OpenSourceProjects
import me.purainity.miuiperfecticons.ui.applySystemBarInsets
import me.purainity.miuiperfecticons.ui.exposeSelectionState
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/** 按功能构建独立的二级页面，避免将耗时的仓库和文件操作放在主线程 */
class FeatureActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeatureBinding
    private lateinit var settings: AppSettings
    private val io = Executors.newSingleThreadExecutor()
    private fun repository() = GitHubRepository(filesDir)
    private var pendingTask: String? = null
    private var hasResumed = false
    private var selectedApps = mutableSetOf<String>()
    private var loadingUpdates = false
    private var hasValidUpdateCache = false
    private var updatePlaceholder: TextView? = null
    private var updateList: RecyclerView? = null
    private var updateAdapter: UpdateLogAdapter? = null
    private var resourceSummary: TextView? = null
    private var downloadButton: MaterialButton? = null
    private var updateButton: MaterialButton? = null
    private var updateStatus: TextView? = null
    private var resourceTaskRunning = false
    private val resourceTaskListener: (TaskSnapshot) -> Unit = { state ->
        if (state.running) resourceTaskRunning = true
        else if (resourceTaskRunning) {
            resourceTaskRunning = false
            // 资源同步完成后隐藏检查更新结果，重新读取图标库状态
            if (state.kind == TaskService.SYNC && state.stage == getString(R.string.task_stage_completed)) {
                updateButton?.visibility = View.GONE
                updateStatus?.visibility = View.GONE
            }
            updateResourceSummary()
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingTask?.let(::startTaskNow)
        pendingTask = null
    }
    private val customDirectoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                io.execute {
                    try {
                        DocumentStorage(this).ensureCustomFolders(uri)
                        val old = settings.customDirectory
                        settings.customDirectory = uri
                        settings.customEnabled = true
                        if (old != null && old != uri && old != settings.outputDirectory) releaseTree(old)
                        runOnUiThread { if (isActive()) showCustom() }
                    } catch (error: Exception) {
                        runOnUiThread { message(error.message ?: getString(R.string.custom_directory_unusable)); if (isActive()) showCustom() }
                    }
                }
            } catch (_: SecurityException) { message(getString(R.string.custom_directory_write_failed)); showCustom() }
        } else showCustom()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(includeIme = intent.getStringExtra(EXTRA_PAGE) == APPS)
        binding.toolbar.setNavigationOnClickListener { finish() }
        settings = AppSettings(this)
        pendingTask = savedInstanceState?.getString("pendingTask")
        importTarget = savedInstanceState?.getString("importTarget") ?: "静态图标"
        selectedApps = savedInstanceState?.getStringArrayList("selectedApps")?.toMutableSet() ?: settings.disabledPackages.toMutableSet()
        binding.swipe.isEnabled = false
        when (intent.getStringExtra(EXTRA_PAGE)) {
            CUSTOM -> showCustom()
            DYNAMIC -> showDynamic()
            APPS -> showAppSelection()
            UPDATES -> showUpdates()
            ABOUT -> showAbout()
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingTask", pendingTask)
        outState.putString("importTarget", importTarget)
        outState.putStringArrayList("selectedApps", ArrayList(selectedApps))
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (intent.getStringExtra(EXTRA_PAGE) == UPDATES) TaskRuntime.observe(resourceTaskListener)
    }

    override fun onStop() {
        if (intent.getStringExtra(EXTRA_PAGE) == UPDATES) TaskRuntime.remove(resourceTaskListener)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    /** 重置标题、菜单与内容区，构建功能页前调用 */
    private fun clear(title: String) {
        binding.toolbar.title = title
        binding.toolbar.menu.clear()
        binding.content.removeAllViews()
    }

    /** 使用同一套分组标题，保持各功能页信息层级一致 */
    private fun heading(title: String, configureHeading: (TextView) -> Unit = {}) {
        TextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
            text = title
            setPadding(dp(4), dp(20), dp(4), dp(8))
            ViewCompat.setAccessibilityHeading(this, true)
            configureHeading(this)
            binding.content.addView(this)
        }
    }

    /** 分组小标题 + 卡片容器 */
    private fun group(title: String, configureHeading: (TextView) -> Unit = {}): LinearLayout {
        heading(title, configureHeading)
        return untitledGroup()
    }

    /** 无标题分组卡，返回卡内列表容器 */
    private fun untitledGroup(topMargin: Int = 0): LinearLayout {
        val group = ViewFeatureGroupBinding.inflate(layoutInflater, binding.content, false)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(topMargin)
        binding.content.addView(group.root, params)
        return group.items
    }

    /** 卡片内的正文或小节标题 */
    private fun paragraph(parent: LinearLayout, value: String, title: Boolean = false): TextView = TextView(this).apply {
        setTextAppearance(if (title) com.google.android.material.R.style.TextAppearance_Material3_TitleMedium else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        if (!title) setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        text = value
        val top = if (title || parent.isEmpty()) 16 else 4
        setPadding(dp(16), dp(top), dp(16), dp(if (title) 0 else 12))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    /** 卡片内的列表行，可带副标题与尾部图标 */
    private fun action(parent: LinearLayout, icon: Int?, title: String, subtitle: String? = null, external: Boolean = false, onClick: () -> Unit) {
        val row = ItemNavigationBinding.inflate(layoutInflater, parent, true)
        if (icon == null) row.icon.visibility = View.GONE else row.icon.setImageResource(icon)
        row.title.text = title
        row.subtitle.text = subtitle
        row.subtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        row.trailingIcon.setImageResource(if (external) R.drawable.ic_external else R.drawable.ic_chevron)
        row.root.isFocusable = true
        row.root.setOnClickListener { onClick() }
    }

    /** 卡片内的整宽按钮 */
    private fun filledButton(parent: LinearLayout, label: String, onClick: () -> Unit): MaterialButton = MaterialButton(this).apply {
        text = label
        setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(12)) }
        parent.addView(this, params)
    }

    private fun showCustom() {
        clear(getString(R.string.custom_icons))
        val control = group(getString(R.string.custom_group_title))
        val switchRow = ItemSwitchSettingBinding.inflate(layoutInflater, control, true)
        val switch = switchRow.toggle
        switchRow.root.contentDescription = getString(
            R.string.selection_with_description, getString(R.string.custom_icons_toggle), getString(R.string.custom_icons_summary),
        )
        switchRow.root.exposeSelectionState(switch)
        switchRow.root.setOnClickListener { switch.isChecked = !switch.isChecked }
        switch.isChecked = settings.customEnabled
        switch.setOnCheckedChangeListener { _, checked ->
            if (checked) enableCustomIcons(switchRow)
            else { settings.customEnabled = false; showCustom() }
        }
        if (!settings.customEnabled) return

        val uri = settings.customDirectory
        val folder = group(getString(R.string.custom_group_directory))
        paragraph(folder, getString(R.string.custom_rule_static_title), true)
        paragraph(folder, getString(R.string.custom_rule_static_body))
        paragraph(folder, getString(R.string.custom_rule_dynamic_title), true)
        paragraph(folder, getString(R.string.custom_rule_dynamic_body))
        paragraph(folder, getString(R.string.custom_rule_addon_title), true)
        paragraph(folder, getString(R.string.custom_rule_addon_body))
        val path = uri?.let {
            try { DocumentsContract.getTreeDocumentId(it) } catch (_: IllegalArgumentException) { it.lastPathSegment }
        } ?: getString(R.string.custom_directory_none)
        action(folder, R.drawable.ic_folder, getString(R.string.custom_directory_row), path) { chooseCustomDirectory() }

        val imports = group(getString(R.string.custom_group_import))
        action(imports, R.drawable.ic_add_photo_alternate, getString(R.string.import_static_files), getString(R.string.import_static_files_sub)) { importTarget = "静态图标"; importFiles.launch(arrayOf("image/png")) }
        action(imports, R.drawable.ic_create_new_folder, getString(R.string.import_static_directory), getString(R.string.import_static_directory_sub)) { importTarget = "静态图标"; importDirectory.launch(null) }
        action(imports, R.drawable.ic_animation, getString(R.string.import_dynamic_directory), getString(R.string.import_dynamic_directory_sub)) { importTarget = "动态图标"; importDirectory.launch(null) }
        action(imports, R.drawable.ic_archive, getString(R.string.import_addon_files)) { importTarget = "高级附加"; importFiles.launch(arrayOf("*/*")) }

        val archive = group(getString(R.string.custom_group_archive))
        action(archive, R.drawable.ic_download, getString(R.string.archive_download), getString(R.string.archive_download_sub)) { requestTask(TaskService.ARCHIVE) }
        action(archive, R.drawable.ic_inventory_2, getString(R.string.archive_view), external = true) { open("https://github.com/pzcn/Perfect-Icons-Completion-Project/tree/main/addition/Icons_Archiving") }
    }

    /** 重新启用时优先验证并复用上次保存的持久授权 */
    private fun enableCustomIcons(switchRow: ItemSwitchSettingBinding) {
        val previous = settings.customDirectory
        val grants = contentResolver.persistedUriPermissions.map {
            CustomDirectoryAccess.Grant(it.uri.toString(), it.isReadPermission, it.isWritePermission)
        }
        if (!CustomDirectoryAccess.canReuse(previous?.toString(), grants)) {
            chooseCustomDirectory()
            return
        }
        switchRow.root.isEnabled = false
        switchRow.toggle.isEnabled = false
        io.execute {
            try {
                DocumentStorage(this).ensureCustomFolders(previous!!)
                runOnUiThread {
                    if (isActive()) {
                        settings.customEnabled = true
                        showCustom()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isActive()) {
                        showCustom()
                        message(error.message ?: getString(R.string.custom_directory_previous_unusable))
                        chooseCustomDirectory()
                    }
                }
            }
        }
    }

    private fun chooseCustomDirectory() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_custom_directory_title)
            .setMessage(R.string.choose_custom_directory_message)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> showCustom() }
            .setOnCancelListener { showCustom() }
            .setPositiveButton(R.string.choose_custom_directory_action) { _, _ ->
                try { customDirectoryPicker.launch(CustomDirectoryLocation.recommendedUri()) }
                catch (_: ActivityNotFoundException) { message(getString(R.string.directory_picker_unavailable)); showCustom() }
            }
            .show()
    }

    private var importTarget = "静态图标"
    // 选择器返回后目标分类立即复位，避免下次误用上一次的选择
    private val importFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = importTarget
        importTarget = "静态图标"
        if (uris.isNotEmpty()) confirmImport(target) { importSelection(target, uris, false) }
    }
    private val importDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = importTarget
        importTarget = "静态图标"
        if (uri != null) confirmImport(target) { importSelection(target, listOf(uri), true) }
    }

    private fun confirmImport(target: String, action: () -> Unit) {
        confirm(getString(R.string.import_confirm, target), action)
    }

    private fun importSelection(target: String, uris: List<Uri>, tree: Boolean) {
        val destination = settings.customDirectory ?: return message(getString(R.string.custom_directory_required))
        io.execute {
            try {
                DocumentStorage(this) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException(getString(R.string.import_cancelled))
                }.importDocuments(destination, target, uris, tree)
                runOnUiThread { message(getString(R.string.import_done)) }
            } catch (error: Exception) {
                runOnUiThread { message(error.message ?: getString(R.string.import_failed)) }
            }
        }
    }

    private fun releaseTree(uri: Uri) {
        // 后台任务可能仍在读取原自定义目录
        if (TaskRuntime.current().running) return
        val grant = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try { contentResolver.releasePersistableUriPermission(uri, flags) } catch (_: SecurityException) { }
    }

    private fun showDynamic() {
        clear(getString(R.string.disable_dynamic_icons))
        val status = when {
            settings.disableAllDynamic -> getString(R.string.dynamic_status_all)
            settings.disabledPackages.isEmpty() -> getString(R.string.dynamic_status_empty)
            else -> settings.disabledPackages.sorted().joinToString("\n")
        }
        paragraph(group(getString(R.string.dynamic_group_status)), status)
        val selected = group(getString(R.string.dynamic_group_some))
        paragraph(selected, getString(R.string.dynamic_warning))
        action(selected, R.drawable.ic_app_registration, getString(R.string.dynamic_choose_apps), getString(R.string.dynamic_choose_apps_sub)) {
            startActivity(Intent(this, FeatureActivity::class.java).putExtra(EXTRA_PAGE, APPS))
        }
        val all = group(getString(R.string.dynamic_group_all))
        action(all, R.drawable.ic_select_all, getString(R.string.dynamic_disable_all), getString(R.string.dynamic_disable_all_sub)) {
            confirm(getString(R.string.dynamic_disable_all_confirm)) { settings.disableAllDynamic = true; settings.disabledPackages = emptySet(); showDynamic() }
        }
        action(all, R.drawable.ic_remove_selection, getString(R.string.dynamic_clear_list), getString(R.string.dynamic_clear_list_sub)) {
            confirm(getString(R.string.dynamic_clear_list_confirm)) { settings.disableAllDynamic = false; settings.disabledPackages = emptySet(); showDynamic() }
        }
        paragraph(group(getString(R.string.dynamic_group_notes)), getString(R.string.dynamic_notes))
    }

    // 唯一确认操作始终显示，并同时展示已选数量
    @SuppressLint("AlwaysShowAction")
    private fun showAppSelection() {
        clear(getString(R.string.apps_title))
        binding.swipe.visibility = View.GONE
        binding.appSelection.root.visibility = View.VISIBLE
        val views = binding.appSelection
        // TopAppBar 右侧确定项实时显示已选数量，点击才落盘
        val count = binding.toolbar.menu.add("")
        count.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        fun updateCount() { count.title = getString(R.string.apps_confirm_count, selectedApps.size) }
        updateCount()
        count.setOnMenuItemClickListener {
            settings.disabledPackages = selectedApps
            settings.disableAllDynamic = false
            finish()
            true
        }
        val adapter = AppSelectionAdapter(packageManager, selectedApps, ::updateCount)
        views.apps.layoutManager = LinearLayoutManager(this)
        views.apps.adapter = adapter
        var loading = true
        var loadError: String? = null
        fun updateAppState() {
            views.loading.visibility = if (loading) View.VISIBLE else View.GONE
            views.empty.visibility = if (!loading && (loadError != null || adapter.itemCount == 0)) View.VISIBLE else View.GONE
            views.empty.text = loadError ?: getString(
                if (views.search.text.isNullOrBlank()) R.string.no_visible_apps else R.string.no_matching_apps,
            )
        }
        fun filter(value: String) {
            adapter.filter(value)
            views.clearSearch.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            updateAppState()
        }
        views.clearSearch.setOnClickListener { views.search.text?.clear() }
        views.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) { }
        })
        io.execute {
            try {
                val pm = packageManager
                @Suppress("DEPRECATION")
                val apps = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).map { app ->
                    AppSelectionAdapter.App(app.packageName, pm.getApplicationLabel(app).toString(), app)
                }.sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.label })
                runOnUiThread {
                    if (!isActive()) return@runOnUiThread
                    loading = false
                    adapter.setApps(apps)
                    filter(views.search.text?.toString().orEmpty())
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isActive()) {
                        loading = false
                        loadError = error.message ?: getString(R.string.apps_load_failed)
                        updateAppState()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized && hasResumed) {
            when (intent.getStringExtra(EXTRA_PAGE)) {
                DYNAMIC -> showDynamic()
                UPDATES -> updateResourceSummary()
            }
        }
        hasResumed = true
    }

    private fun showAbout() {
        clear(getString(R.string.about))
        val identity = untitledGroup()
        val header = ViewAboutHeaderBinding.inflate(layoutInflater, identity, true)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0)
        header.appVersion.text = getString(R.string.about_version, version.versionName, version.longVersionCode)
        val project = untitledGroup(topMargin = 16)
        action(project, R.drawable.ic_code, getString(R.string.about_project_github), "https://github.com/purainity/miui-perfect-icons", external = true) { open("https://github.com/purainity/miui-perfect-icons") }
        action(project, R.drawable.ic_person, getString(R.string.about_author), "https://github.com/purainity", external = true) { open("https://github.com/purainity") }
        // 同作者的主题扩展模块，提供主题破解等增强功能
        action(project, R.drawable.ic_extension, getString(R.string.about_theme_extension), getString(R.string.about_theme_extension_sub), external = true) { open("https://github.com/purainity/miui-theme-extension") }
        val thanks = group(getString(R.string.thanks_group))
        paragraph(thanks, getString(R.string.thanks_team_title), true)
        paragraph(thanks, getString(R.string.thanks_team))
        paragraph(thanks, getString(R.string.thanks_drawers_title), true)
        paragraph(thanks, getString(R.string.thanks_drawers))
        paragraph(thanks, getString(R.string.thanks_license_title), true)
        paragraph(thanks, getString(R.string.thanks_license))
        paragraph(thanks, getString(R.string.thanks_special_title), true)
        paragraph(thanks, getString(R.string.thanks_special))
        paragraph(thanks, getString(R.string.thanks_donors_title), true)
        paragraph(thanks, getString(R.string.thanks_donors))
        val licenses = group(getString(R.string.licenses_group))
        OpenSourceProjects.all.forEach { project ->
            val row = ItemOpenSourceProjectBinding.inflate(layoutInflater, licenses, true)
            row.title.text = project.name
            row.projectMeta.text = getString(R.string.licenses_project_meta, project.developer, project.license)
            row.projectUrl.text = project.url
            row.root.isFocusable = true
            row.root.setOnClickListener { open(project.url) }
        }
    }

    private fun showUpdates() {
        clear(getString(R.string.update_title))
        binding.swipe.isEnabled = true
        binding.swipe.setColorSchemeColors(MaterialColors.getColor(binding.swipe, androidx.appcompat.R.attr.colorPrimary))
        binding.swipe.setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(binding.swipe, com.google.android.material.R.attr.colorSurfaceContainerHigh),
        )
        binding.swipe.setOnRefreshListener { loadUpdates() }
        val resources = group(getString(R.string.updates_group_status))
        resourceSummary = paragraph(resources, getString(R.string.updates_status_loading))
        downloadButton = filledButton(resources, getString(R.string.updates_download)) {
            confirm(getString(R.string.updates_download_confirm)) { requestTask(TaskService.SYNC) }
        }
        downloadButton?.visibility = View.GONE
        lateinit var status: TextView
        lateinit var update: MaterialButton
        lateinit var check: MaterialButton
        check = filledButton(resources, getString(R.string.updates_check)) {
            check.isEnabled = false
            update.visibility = View.GONE
            status.visibility = View.VISIBLE
            status.text = getString(R.string.updates_check_running)
            io.execute {
                try {
                    val latest = repository().latestCommit()
                    val old = repository().cachedCommit()
                    runOnUiThread {
                        if (!isActive()) return@runOnUiThread
                        getSharedPreferences("content", MODE_PRIVATE).edit { putBoolean("resources_have_update", old != latest) }
                        // 首行合并结论，后两行给出新旧版本；无前缀与缩进
                        status.text = getString(
                            R.string.updates_check_result,
                            getString(if (old == latest) R.string.updates_already_latest else R.string.updates_has_new),
                            old ?: getString(R.string.updates_version_none), latest,
                        )
                        update.visibility = if (old != null && old != latest) View.VISIBLE else View.GONE
                        check.isEnabled = true
                    }
                } catch (error: Exception) {
                    runOnUiThread { if (isActive()) { status.text = error.message ?: getString(R.string.updates_check_failed); check.isEnabled = true } }
                }
            }
        }
        status = paragraph(resources, "").apply { visibility = View.GONE }
        update = filledButton(resources, getString(R.string.updates_update_resources)) { requestTask(TaskService.SYNC) }.apply { visibility = View.GONE }
        updateStatus = status
        updateButton = update
        updateResourceSummary()
        val notifications = group(getString(R.string.updates_group_notifications))
        action(notifications, R.drawable.ic_wechat, getString(R.string.updates_subscribe), getString(R.string.updates_subscribe_sub), external = true) { open("https://iconsx.pedroz.eu.org/push.html") }
        heading(getString(R.string.updates_heading)) { heading ->
            // 保留下拉手势，同时为不便使用手势的用户提供无障碍刷新动作
            ViewCompat.addAccessibilityAction(heading, getString(R.string.refresh_updates)) { _, _ ->
                loadUpdates()
                true
            }
        }
        updatePlaceholder = paragraph(binding.content, getString(R.string.updates_list_loading))
        attachUpdateList()
        renderCachedUpdates()
        loadUpdates()
    }

    private fun updateResourceSummary() {
        // 下载任务可能已更新索引，重新读取以避免显示旧版本
        io.execute {
            val repository = repository()
            val iconCount = repository.iconCount()
            val count = iconCount?.toString() ?: "--"
            val sha = repository.cachedCommit()?.take(7) ?: "--"
            val date = repository.cachedDate().takeIf { it > 0 }?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) } ?: "--"
            runOnUiThread {
                if (isActive()) {
                    resourceSummary?.text = getString(
                        R.string.updates_summary, count, sha,
                        date.ifEmpty { getString(R.string.updates_summary_none) },
                    )
                    downloadButton?.visibility = if (iconCount == null) View.VISIBLE else View.GONE
                }
            }
        }
    }

    /** 将顶部操作区作为列表首项，使长日志可以复用视图并在滚动时分页 */
    private fun attachUpdateList() {
        binding.featureScroll.removeView(binding.content)
        binding.featureScroll.visibility = View.GONE
        binding.content.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        binding.content.setPadding(dp(16), dp(8), dp(16), dp(8))
        val list = layoutInflater.inflate(R.layout.view_update_list, binding.refreshContent, false) as RecyclerView
        val manager = LinearLayoutManager(this)
        val adapter = UpdateLogAdapter(binding.content, ::open)
        list.layoutManager = manager
        list.itemAnimator = null
        list.adapter = adapter
        list.addOnScrollListener(UpdateLogPagingListener(manager, adapter))
        binding.refreshContent.addView(list)
        binding.swipe.setOnChildScrollUpCallback { _, _ -> list.canScrollVertically(-1) }
        updateList = list
        updateAdapter = adapter
    }

    private fun renderCachedUpdates() {
        io.execute {
            val entries = OriginalChangelogRepository(filesDir).cached() ?: return@execute
            getSharedPreferences("content", MODE_PRIVATE).edit { putString("updates_latest_time", entries.first().date) }
            runOnUiThread {
                if (isActive()) {
                    hasValidUpdateCache = true
                    showUpdates(entries)
                }
            }
        }
    }

    /** 进入页面自动获取原版日志，下拉刷新失败时保留已缓存的内容 */
    private fun loadUpdates() {
        if (loadingUpdates) return
        loadingUpdates = true
        binding.swipe.isRefreshing = true
        io.execute {
            try {
                val entries = OriginalChangelogRepository(filesDir).refresh()
                getSharedPreferences("content", MODE_PRIVATE).edit { putString("updates_latest_time", entries.first().date) }
                runOnUiThread {
                    if (isActive()) {
                        loadingUpdates = false
                        binding.swipe.isRefreshing = false
                        hasValidUpdateCache = true
                        showUpdates(entries)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isActive()) {
                        loadingUpdates = false
                        binding.swipe.isRefreshing = false
                        if (!hasValidUpdateCache) updatePlaceholder?.text = getString(R.string.updates_list_failed)
                        message(error.message ?: getString(R.string.updates_refresh_failed))
                    }
                }
            }
        }
    }

    private fun showUpdates(entries: List<OriginalChangelog.Entry>) {
        updatePlaceholder?.visibility = View.GONE
        updateAdapter?.replace(entries)
        updateList?.post {
            val list = updateList ?: return@post
            val adapter = updateAdapter ?: return@post
            val manager = list.layoutManager as? LinearLayoutManager ?: return@post
            if (manager.findLastVisibleItemPosition() >= adapter.itemCount - 3) adapter.loadNextPage()
        }
    }

    private fun requestTask(kind: String) {
        if (TaskRuntime.current().running) { showTaskLog(); return }
        if (TaskRuntime.isCacheClearing()) { message(getString(R.string.clear_cache_in_progress)); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingTask = kind
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startTaskNow(kind)
    }
    private fun startTaskNow(kind: String) { TaskService.start(this, kind); showTaskLog() }
    private fun showTaskLog() { if (supportFragmentManager.findFragmentByTag("task-log") == null) TaskLogDialog().show(supportFragmentManager, "task-log") }
    private fun confirm(message: String, action: () -> Unit) { MaterialAlertDialogBuilder(this).setMessage(message).setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.btn_ok) { _, _ -> action() }.show() }
    private fun open(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: ActivityNotFoundException) { message(getString(R.string.open_link_failed_simple)) } }
    private fun message(value: String) { if (isActive()) Snackbar.make(binding.root, value, Snackbar.LENGTH_LONG).show() }
    private fun isActive() = !isFinishing && !isDestroyed
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAGE = "page"
        const val CUSTOM = "custom"
        const val DYNAMIC = "dynamic"
        const val APPS = "apps"
        const val UPDATES = "updates"
        const val ABOUT = "about"
    }
}
