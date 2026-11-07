package me.purainity.miuiperfecticons

import android.content.Intent
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.materialswitch.MaterialSwitch
import me.purainity.miuiperfecticons.config.AppSettings
import me.purainity.miuiperfecticons.config.IconThemes
import me.purainity.miuiperfecticons.ui.CustomDirectoryLocation
import me.purainity.miuiperfecticons.ui.OpenSourceProjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeaturePagesTest {
    private fun feature(page: String): Intent = Intent(
        ApplicationProvider.getApplicationContext(), FeatureActivity::class.java,
    ).putExtra(FeatureActivity.EXTRA_PAGE, page)

    @Test
    fun updatesUsePullToRefreshWithoutExtraButton() {
        ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.UPDATES)).use { scenario ->
            var checked = false
            repeat(50) {
                if (!checked) {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity { activity ->
                        val swipe = activity.findViewById<SwipeRefreshLayout>(R.id.swipe)
                        val heading = textViews(swipe).firstOrNull { it.text.toString() == "更新日志" }
                        val list = activity.findViewById<RecyclerView>(R.id.updates_list)
                        val header = list.findViewHolderForAdapterPosition(0)?.itemView
                        val firstHeading = header?.let { view -> textViews(view).firstOrNull { it.text.toString() == "图标库状态" } }
                        val summary = header?.let { view -> texts(view).firstOrNull { it.contains("本地图标库同步日期：") } }
                        if (heading != null && header != null && summary != null && list.width > 0 && list.height > 0 && firstHeading?.getGlobalVisibleRect(android.graphics.Rect()) == true) {
                            assertTrue(swipe.isEnabled)
                            assertTrue(list.isShown)
                            assertTrue(header.width > 0 && header.height > 0)
                            assertEquals(View.GONE, activity.findViewById<View>(R.id.featureScroll).visibility)
                            assertEquals(activity.findViewById(R.id.refreshContent), list.parent)
                            assertFalse(texts(swipe).contains("刷新更新日志"))
                            assertTrue(texts(swipe).contains("通过微信订阅后，有新版本会推送通知"))
                            val summaryLines = summary.lines()
                            assertEquals(3, summaryLines.size)
                            assertTrue(summaryLines[1].startsWith("本地图标库版本："))
                            assertTrue(summaryLines[2].startsWith("本地图标库同步日期："))
                            val notificationTitle = textViews(header).first { it.text.toString() == "订阅新版本通知" }
                            val notificationRow = notificationTitle.parent.parent as View
                            val icon = notificationRow.findViewById<android.widget.ImageView>(R.id.icon)
                            val trailing = notificationRow.findViewById<android.widget.ImageView>(R.id.trailingIcon)
                            assertTrue(icon.drawable.constantState != trailing.drawable.constantState)
                            val info = heading.createAccessibilityNodeInfo()
                            assertTrue(info.isHeading)
                            assertTrue(info.actionList.any { it.label == "刷新更新日志" })
                            checked = true
                        }
                    }
                    if (!checked) Thread.sleep(50)
                }
            }
            assertTrue("更新日志标题未显示", checked)
        }
    }

    @Test
    fun appSelectionSurvivesRecreationWithoutSavingUntilConfirmed() {
        val settings = AppSettings(ApplicationProvider.getApplicationContext())
        val oldPackages = settings.disabledPackages
        val oldDisableAll = settings.disableAllDynamic
        settings.disabledPackages = emptySet()
        settings.disableAllDynamic = false
        try {
            ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.APPS)).use { scenario ->
                var selected = false
                repeat(100) {
                    if (!selected) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                        scenario.onActivity { activity ->
                            val list = activity.findViewById<RecyclerView>(R.id.apps)
                            val holder = list.findViewHolderForAdapterPosition(0) as? AppSelectionAdapter.Holder
                            if (holder != null) {
                                assertEquals(View.GONE, activity.findViewById<View>(R.id.loading).visibility)
                                holder.itemView.performClick()
                                selected = true
                            }
                        }
                        if (!selected) Thread.sleep(50)
                    }
                }
                assertTrue("应用列表未加载", selected)
                scenario.onActivity { activity -> assertEquals("确定（1）", activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).menu.getItem(0).title.toString()) }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals("确定（1）", activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).menu.getItem(0).title.toString())
                    activity.onBackPressedDispatcher.onBackPressed()
                }
                assertTrue(settings.disabledPackages.isEmpty())
            }
        } finally {
            settings.disabledPackages = oldPackages
            settings.disableAllDynamic = oldDisableAll
        }
    }

    @Test
    fun appSelectionFiltersClearsAndConfirmsAfterRecreation() {
        val settings = AppSettings(ApplicationProvider.getApplicationContext())
        val oldPackages = settings.disabledPackages
        val oldDisableAll = settings.disableAllDynamic
        settings.disabledPackages = emptySet()
        settings.disableAllDynamic = true
        try {
            ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.APPS)).use { scenario ->
                var chosenPackage: String? = null
                repeat(100) {
                    if (chosenPackage == null) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                        scenario.onActivity { activity ->
                            val list = activity.findViewById<RecyclerView>(R.id.apps)
                            val holder = list.findViewHolderForAdapterPosition(0) as? AppSelectionAdapter.Holder
                            if (holder != null) {
                                assertEquals(list.width, holder.itemView.width)
                                chosenPackage = holder.binding.subtitle.text.toString()
                                val before = holder.itemView.createAccessibilityNodeInfo()
                                assertTrue(before.isCheckable)
                                assertFalse(before.isChecked)
                                holder.itemView.performClick()
                                assertTrue(holder.binding.checkbox.isChecked)
                                assertTrue(holder.itemView.createAccessibilityNodeInfo().isChecked)
                                val search = activity.findViewById<EditText>(R.id.search)
                                search.setText("no_matching_app_21f339f9")
                                assertEquals(0, list.adapter?.itemCount)
                                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty).visibility)
                                val clear = activity.findViewById<View>(R.id.clearSearch)
                                assertEquals(View.VISIBLE, clear.visibility)
                                clear.performClick()
                                assertEquals("", search.text.toString())
                                assertEquals(View.GONE, clear.visibility)
                                search.setText(chosenPackage)
                            }
                        }
                        if (chosenPackage == null) Thread.sleep(50)
                    }
                }
                assertNotNull("应用列表未加载", chosenPackage)
                assertTrue(settings.disabledPackages.isEmpty())
                scenario.recreate()
                var restored = false
                repeat(100) {
                    if (!restored) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                        scenario.onActivity { activity ->
                            val search = activity.findViewById<EditText>(R.id.search)
                            val list = activity.findViewById<RecyclerView>(R.id.apps)
                            if (search.text.toString() == chosenPackage && activity.findViewById<View>(R.id.loading).visibility == View.GONE) {
                                val holder = (0 until (list.adapter?.itemCount ?: 0))
                                    .mapNotNull { list.findViewHolderForAdapterPosition(it) as? AppSelectionAdapter.Holder }
                                    .firstOrNull { it.binding.subtitle.text.toString() == chosenPackage }
                                if (holder != null) {
                                    assertTrue(holder.binding.checkbox.isChecked)
                                    assertEquals("确定（1）", activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).menu.getItem(0).title.toString())
                                    restored = true
                                }
                            }
                        }
                        if (!restored) Thread.sleep(50)
                    }
                }
                assertTrue("搜索条件或选中项未恢复", restored)
                assertTrue(settings.disabledPackages.isEmpty())
                scenario.onActivity { activity ->
                    val menu = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).menu
                    assertTrue(menu.performIdentifierAction(menu.getItem(0).itemId, 0))
                }
                assertEquals(setOf(chosenPackage), settings.disabledPackages)
                assertFalse(settings.disableAllDynamic)
            }
        } finally {
            settings.disabledPackages = oldPackages
            settings.disableAllDynamic = oldDisableAll
        }
    }

    @Test
    fun groupCardsUseZeroElevation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val cards = cards(activity.findViewById(android.R.id.content)).filter { it.isShown }
                assertTrue(cards.isNotEmpty())
                cards.forEach {
                    assertEquals(0f, it.cardElevation, 0f)
                    assertEquals(com.google.android.material.color.MaterialColors.getColor(it, com.google.android.material.R.attr.colorSurfaceContainer), it.cardBackgroundColor.defaultColor)
                    assertEquals(0, it.strokeWidth)
                }
            }
        }
        ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.DYNAMIC)).use { scenario ->
            scenario.onActivity { activity ->
                val cards = cards(activity.findViewById(android.R.id.content)).filter { it.isShown }
                assertTrue(cards.isNotEmpty())
                cards.forEach {
                    assertEquals(0f, it.cardElevation, 0f)
                    assertEquals(com.google.android.material.color.MaterialColors.getColor(it, com.google.android.material.R.attr.colorSurfaceContainer), it.cardBackgroundColor.defaultColor)
                    assertEquals(0, it.strokeWidth)
                }
            }
        }
    }

    @Test
    fun selectingThemeSavesImmediately() {
        val settings = AppSettings(ApplicationProvider.getApplicationContext())
        val original = settings.theme
        try {
            settings.theme = IconThemes.default
            val intent = Intent(ApplicationProvider.getApplicationContext(), ThemeSelectionActivity::class.java)
            ActivityScenario.launch<ThemeSelectionActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    val themes = activity.findViewById<ViewGroup>(R.id.themes)
                    assertEquals(IconThemes.all.size, themes.childCount)
                    val card = themes.parent as com.google.android.material.card.MaterialCardView
                    assertTrue(card.clipToOutline)
                    assertFalse(card.preventCornerOverlap)
                    assertEquals(0f, card.cardElevation, 0f)
                    assertFalse(texts(activity.findViewById(android.R.id.content)).any { it.startsWith("当前选择") })
                    val selected = themes.getChildAt(0).createAccessibilityNodeInfo()
                    val other = themes.getChildAt(1).createAccessibilityNodeInfo()
                    assertTrue(selected.isCheckable)
                    assertTrue(selected.isChecked)
                    assertTrue(other.isCheckable)
                    assertFalse(other.isChecked)
                    themes.getChildAt(1).performClick()
                }
                assertEquals(IconThemes.all[1], settings.theme)
            }
        } finally {
            settings.theme = original
        }
    }

    @Test
    fun disablingCustomIconsKeepsDirectoryAndMissingGrantRequestsSelection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = AppSettings(context)
        val oldDirectory = settings.customDirectory
        val oldEnabled = settings.customEnabled
        val previous = android.net.Uri.parse("content://example.documents/tree/previous")
        try {
            settings.customDirectory = previous
            settings.customEnabled = true
            ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.CUSTOM)).use { scenario ->
                scenario.onActivity { activity ->
                    val content = activity.findViewById<ViewGroup>(R.id.content)
                    assertFalse(texts(content).contains("使用帮助"))
                    assertFalse(texts(content).contains("自定义图标使用指南"))
                    assertFalse(texts(content).contains("完美图标适配指南"))
                    content.findViewById<MaterialSwitch>(R.id.toggle).isChecked = false
                    assertFalse(settings.customEnabled)
                    assertEquals(previous, settings.customDirectory)
                    content.findViewById<MaterialSwitch>(R.id.toggle).isChecked = true
                    assertFalse(settings.customEnabled)
                }
                androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
                    androidx.test.espresso.matcher.ViewMatchers.withText("选择自定义图标目录"),
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed(),
                )).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
                    androidx.test.espresso.matcher.ViewMatchers.withText("取消"),
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed(),
                )).perform(androidx.test.espresso.action.ViewActions.click())
                assertFalse(settings.customEnabled)
                assertEquals(previous, settings.customDirectory)
            }
        } finally {
            settings.customDirectory = oldDirectory
            settings.customEnabled = oldEnabled
        }
    }

    @Test
    fun customDirectoryPickerStartsAtRecommendedDirectory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val recommended = CustomDirectoryLocation.recommendedUri()
        assertEquals("com.android.externalstorage.documents", recommended.authority)
        assertEquals("primary:Documents/MIUI完美图标自定义", DocumentsContract.getDocumentId(recommended))
        val intent = ActivityResultContracts.OpenDocumentTree().createIntent(context, recommended)
        @Suppress("DEPRECATION")
        val initial = intent.getParcelableExtra<android.net.Uri>(DocumentsContract.EXTRA_INITIAL_URI)
        assertEquals(recommended, initial)
    }

    @Test
    fun appSelectionShowsMaterialLoadingIndicatorBeforeLoading() {
        ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.APPS)).use { scenario ->
            scenario.onActivity { activity ->
                val view = activity.layoutInflater.inflate(R.layout.view_app_selection, null)
                val indicator = view.findViewById<com.google.android.material.loadingindicator.LoadingIndicator>(R.id.loadingIndicator)
                assertTrue(indicator.indicatorSize > 0)
                assertEquals(com.google.android.material.color.MaterialColors.getColor(indicator, androidx.appcompat.R.attr.colorPrimary),
                    indicator.indicatorColor.single())
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.loading).visibility)
                assertEquals(View.GONE, view.findViewById<View>(R.id.empty).visibility)
                assertFalse(texts(view).contains("系统允许查看的应用"))
            }
        }
    }

    @Test
    fun taskProgressHasAnAccessibleLabelAndFocusArea() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = activity.layoutInflater.inflate(R.layout.dialog_task_log, null)
                val indicator = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress)
                assertEquals("任务进度", indicator.contentDescription.toString())
                assertTrue(indicator.paddingTop > 0)
                assertTrue(indicator.paddingBottom > 0)
                assertFalse(texts(view).contains("执行日志"))
                assertNotNull(view.findViewById<TextView>(R.id.statusText))
                assertNotNull(view.findViewById(R.id.buttonSecondary))
                assertNotNull(view.findViewById(R.id.buttonPrimary))
            }
        }
    }

    @Test
    fun mainRowsUseRequestedMaterialSymbols() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                listOf(
                    R.string.doc_intro to R.drawable.ic_docs,
                    R.string.doc_miui_guide to R.drawable.ic_docs,
                    R.string.doc_custom_guide to R.drawable.ic_docs,
                    R.string.doc_adaptation to R.drawable.ic_docs,
                    R.string.doc_release to R.drawable.ic_docs,
                    R.string.doc_preview to R.drawable.ic_preview,
                    R.string.clear_cache to R.drawable.ic_delete_sweep,
                    R.string.disable_dynamic_icons to R.drawable.ic_animation,
                    R.string.custom_icons to R.drawable.ic_tune,
                ).forEach { (title, icon) -> assertRowIcon(activity, content, title, icon) }
            }
        }
    }

    @Test
    fun dynamicRowsUseRequestedMaterialSymbols() {
        ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.DYNAMIC)).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(R.id.content)
                listOf(
                    R.string.dynamic_choose_apps to R.drawable.ic_app_registration,
                    R.string.dynamic_disable_all to R.drawable.ic_select_all,
                    R.string.dynamic_clear_list to R.drawable.ic_remove_selection,
                ).forEach { (title, icon) -> assertRowIcon(activity, content, title, icon) }
            }
        }
    }

    @Test
    fun customRowsUseRequestedMaterialSymbols() {
        val settings = AppSettings(ApplicationProvider.getApplicationContext())
        val oldEnabled = settings.customEnabled
        settings.customEnabled = true
        try {
            ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.CUSTOM)).use { scenario ->
                scenario.onActivity { activity ->
                    val content = activity.findViewById<ViewGroup>(R.id.content)
                    listOf(
                        R.string.import_static_files to R.drawable.ic_add_photo_alternate,
                        R.string.import_static_directory to R.drawable.ic_create_new_folder,
                        R.string.import_dynamic_directory to R.drawable.ic_animation,
                        R.string.import_addon_files to R.drawable.ic_archive,
                        R.string.archive_view to R.drawable.ic_inventory_2,
                    ).forEach { (title, icon) -> assertRowIcon(activity, content, title, icon) }
                }
            }
        } finally {
            settings.customEnabled = oldEnabled
        }
    }

    @Test
    fun updateRowsRetainDetailsAndUseIncrementalPages() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val entries = me.purainity.miuiperfecticons.engine.OriginalChangelog.parse(
                    "<group><text><title>2026.10.24 14:08</title><desc>新增图标\n\n更新详情\nby 作者</desc>" +
                        "<slice link=\"https://github.com/example/repo/commit/abc\" /></text></group>",
                )
                val entry = entries.single()
                assertEquals("新增图标\n\n更新详情", entry.message)
                assertEquals("作者", entry.author)
                val adapter = UpdateLogAdapter(View(activity)) { }
                val singleLine = me.purainity.miuiperfecticons.engine.OriginalChangelog.Entry("2026.10.24 14:08", "单行更新", null, null)
                adapter.replace(listOf(entry, singleLine) + List(58) { entry })
                assertEquals(26, adapter.itemCount)
                val parent = android.widget.FrameLayout(activity)
                val holder = adapter.createViewHolder(parent, adapter.getItemViewType(1))
                adapter.bindViewHolder(holder, 1)
                assertEquals("新增图标", holder.itemView.findViewById<TextView>(R.id.message).text.toString())
                val details = holder.itemView.findViewById<TextView>(R.id.details)
                assertEquals("更新详情", details.text.toString())
                assertEquals(View.VISIBLE, details.visibility)
                adapter.bindViewHolder(holder, 2)
                assertEquals("单行更新", holder.itemView.findViewById<TextView>(R.id.message).text.toString())
                assertEquals(View.GONE, details.visibility)
                assertTrue(adapter.loadNextPage())
                assertEquals(51, adapter.itemCount)
                assertTrue(adapter.loadNextPage())
                assertEquals(61, adapter.itemCount)
                assertFalse(adapter.loadNextPage())
            }
        }
    }

    @Test
    fun updateListAutomaticallyLoadsMoreWhenScrolledNearTheEnd() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            lateinit var logAdapter: UpdateLogAdapter
            scenario.onActivity { activity ->
                val entry = me.purainity.miuiperfecticons.engine.OriginalChangelog.Entry(
                    "2026.10.24 14:08", "更新内容", null, null,
                )
                val manager = androidx.recyclerview.widget.LinearLayoutManager(activity)
                logAdapter = UpdateLogAdapter(View(activity)) { }.apply { replace(List(60) { entry }) }
                list = RecyclerView(activity).apply {
                    layoutManager = manager
                    this.adapter = logAdapter
                    addOnScrollListener(UpdateLogPagingListener(manager, logAdapter))
                }
                activity.setContentView(list)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { list.scrollToPosition(25) }
            var loaded = false
            repeat(50) {
                if (!loaded) {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity { loaded = logAdapter.itemCount >= 51 }
                    if (!loaded) Thread.sleep(50)
                }
            }
            assertTrue("滚动到末尾后未加载下一批日志", loaded)
        }
    }

    @Test
    fun aboutShowsProjectLicensesAtBottomWithExternalLinks() {
        ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.ABOUT)).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(R.id.content)
                assertFalse(texts(content).contains("应用信息"))
                assertFalse(texts(content).contains("本项目"))
                val appInfo = content.getChildAt(0)
                @Suppress("DEPRECATION")
                val version = activity.packageManager.getPackageInfo(activity.packageName, 0)
                assertTrue(texts(appInfo).contains("版本 ${version.versionName}（${version.longVersionCode}）"))
                assertFalse(texts(appInfo).any { it.startsWith("本客户端基于 GPL-3.0") || it.contains("Android 机器人") })
                assertFalse(texts(content).contains("开源信息"))
                assertFalse(texts(content).contains("查看原项目"))
                assertFalse(texts(content).contains("查看图标资源仓库"))
                val projectItems = content.getChildAt(1).findViewById<ViewGroup>(R.id.items)
                assertEquals(2, projectItems.childCount)
                assertTrue(texts(projectItems).contains("本项目 GitHub"))
                assertTrue(texts(projectItems).contains("作者：轻雨 Purainity"))
                assertTrue(texts(projectItems).contains("https://github.com/purainity/miui-perfect-icons"))
                assertTrue(texts(projectItems).contains("https://github.com/purainity"))
                assertTrue(texts(content).indexOf("本项目 GitHub") < texts(content).indexOf("原项目致谢"))
                val heading = textViews(content).first { it.text.toString() == "开源许可" }
                val headingIndex = (0 until content.childCount).first { content.getChildAt(it) == heading }
                assertEquals(content.childCount - 2, headingIndex)
                val card = content.getChildAt(headingIndex + 1)
                val items = card.findViewById<ViewGroup>(R.id.items)
                assertEquals(OpenSourceProjects.all.size, items.childCount)
                OpenSourceProjects.all.forEachIndexed { index, project ->
                    val row = items.getChildAt(index)
                    assertEquals("${project.name} - ${project.developer}", row.findViewById<TextView>(R.id.title).text.toString())
                    assertEquals(project.url, row.findViewById<TextView>(R.id.projectUrl).text.toString())
                    assertEquals(project.license, row.findViewById<TextView>(R.id.license).text.toString())
                    val url = row.findViewById<TextView>(R.id.projectUrl)
                    assertTrue(url.maxLines > 1)
                    assertEquals(null, url.ellipsize)
                    assertEquals(View.VISIBLE, row.findViewById<View>(R.id.trailingIcon).visibility)
                    assertTrue(row.isClickable && row.isFocusable)
                }
            }
        }
    }

    @Test
    fun aboutHeaderIconMatchesTextHeightAndLicenseTextWraps() {
        ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.ABOUT)).use { scenario ->
            scenario.onActivity { activity ->
                val width = (240 * activity.resources.displayMetrics.density).toInt()
                fun measure(view: View) {
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    view.layout(0, 0, width, view.measuredHeight)
                }
                val header = activity.layoutInflater.inflate(R.layout.view_about_header, null)
                header.findViewById<TextView>(R.id.appVersion).text = "版本 1.0（1）"
                measure(header)
                val icon = header.findViewById<View>(R.id.appIcon)
                val text = header.findViewById<View>(R.id.nameAndVersion)
                assertTrue(text.height > 0)
                assertEquals(text.top, icon.top)
                assertEquals(text.bottom, icon.bottom)
                assertEquals(icon.height, icon.width)

                val row = activity.layoutInflater.inflate(R.layout.item_open_source_project, null)
                val title = row.findViewById<TextView>(R.id.title)
                val url = row.findViewById<TextView>(R.id.projectUrl)
                title.text = "AndroidX Very Long Project Name - Developer With A Long Name"
                url.text = "https://github.com/Remix-Design/RemixIcon/tree/v4.6.0"
                measure(row)
                assertTrue(title.layout.lineCount > 1)
                assertEquals(null, title.ellipsize)
                assertTrue(url.layout.lineCount > 1)
                assertEquals(null, url.ellipsize)
            }
        }
    }

    @Test
    fun announcementHeadingIsOutsideCardAndSwitchesUsePrimaryTrack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.layoutInflater.inflate(R.layout.activity_main, null)
                val announcement = root.findViewById<ViewGroup>(R.id.announcementGroup)
                assertEquals(View.GONE, announcement.visibility)
                assertEquals("公告", (announcement.getChildAt(0) as TextView).text.toString())
                assertEquals(announcement.getChildAt(1), root.findViewById(R.id.announcementCard))
                val primary = com.google.android.material.color.MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
                assertEquals(primary, (announcement.getChildAt(0) as TextView).currentTextColor)
                val controls: List<MaterialSwitch> = listOf(
                    root.findViewById(R.id.maskSwitch),
                    activity.layoutInflater.inflate(R.layout.item_switch_setting, null)
                        .findViewById(R.id.toggle),
                )
                val selectionControls = listOf(
                    activity.layoutInflater.inflate(R.layout.item_theme_selection, null)
                        .findViewById<com.google.android.material.radiobutton.MaterialRadioButton>(R.id.radio),
                    activity.layoutInflater.inflate(R.layout.item_app_selection, null)
                        .findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkbox),
                )
                val selected = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                selectionControls.forEach { control ->
                    val tint = androidx.core.widget.CompoundButtonCompat.getButtonTintList(control)
                        ?: throw AssertionError("选择控件未设置主题配色")
                    assertEquals(primary, tint.getColorForState(selected, 0))
                    assertFalse(control.isClickable)
                    assertFalse(control.isFocusable)
                }
                controls.forEach { control ->
                    val track = control.trackTintList ?: throw AssertionError("开关轨道未设置颜色")
                    val thumb = control.thumbTintList ?: throw AssertionError("开关滑块未设置颜色")
                    assertEquals(primary, track.getColorForState(selected, 0))
                    assertEquals(com.google.android.material.color.MaterialColors.getColor(control, com.google.android.material.R.attr.colorOnPrimary),
                        thumb.getColorForState(selected, 0))
                    val disabled = intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked)
                    assertTrue(track.getColorForState(disabled, 0) != primary)
                }
            }
        }
    }

    @Test
    fun headingsUseThemePrimaryAndHomeShowsCurrentSelection() {
        val settings = AppSettings(ApplicationProvider.getApplicationContext())
        val original = settings.theme
        try {
            settings.theme = IconThemes.find("explore")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    val primary = com.google.android.material.color.MaterialColors.getColor(content, androidx.appcompat.R.attr.colorPrimary)
                    listOf("更新", "配置", "文档").forEach { heading ->
                        assertEquals(primary, textViews(content).first { it.text.toString() == heading }.currentTextColor)
                    }
                    assertTrue(texts(content).contains("MIUI探·界"))
                    assertTrue(texts(content).contains("生成 Magisk 模块 ZIP"))
                }
            }
            ActivityScenario.launch<FeatureActivity>(feature(FeatureActivity.DYNAMIC)).use { scenario ->
                scenario.onActivity { activity ->
                    val content = activity.findViewById<ViewGroup>(R.id.content)
                    val primary = com.google.android.material.color.MaterialColors.getColor(content, androidx.appcompat.R.attr.colorPrimary)
                    assertEquals(primary, textViews(content).first { it.text.toString() == "当前禁用列表" }.currentTextColor)
                    assertTrue(texts(content).any { it.contains("列表内会显示全部 app") && it.contains("再次强调，按需选择，请勿全选！") })
                }
            }
        } finally {
            settings.theme = original
        }
    }

    @Test
    fun appSelectionRequestsVisibilityOfInstalledPackages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.QUERY_ALL_PACKAGES))
    }

    @Test
    fun launcherUsesNewAppName() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("MIUI 完美图标计划", context.applicationInfo.loadLabel(context.packageManager).toString())
    }

    private fun assertRowIcon(activity: android.app.Activity, content: View, title: Int, icon: Int) {
        val titleView = textViews(content).single {
            it.id == R.id.title && it.text.toString() == activity.getString(title)
        }
        val row = titleView.parent.parent as View
        val actual = row.findViewById<android.widget.ImageView>(R.id.icon).drawable
        val expected = requireNotNull(activity.getDrawable(icon))

        fun render(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
            val copy = requireNotNull(drawable.constantState)
                .newDrawable(activity.resources, activity.theme).mutate()
            copy.setTint(android.graphics.Color.BLACK)
            val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, bitmap.width, bitmap.height)
            copy.draw(android.graphics.Canvas(bitmap))
            return bitmap
        }
        assertTrue("入口图标不正确：${activity.getString(title)}", render(expected).sameAs(render(actual)))
    }

    private fun cards(view: View): List<com.google.android.material.card.MaterialCardView> {
        val result = mutableListOf<com.google.android.material.card.MaterialCardView>()
        if (view is com.google.android.material.card.MaterialCardView) result.add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) result.addAll(cards(view.getChildAt(index)))
        return result
    }

    private fun textViews(view: View): List<TextView> {
        val result = mutableListOf<TextView>()
        if (view is TextView) result.add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) result.addAll(textViews(view.getChildAt(index)))
        return result
    }

    private fun texts(view: View): List<String> = textViews(view).map { it.text.toString() }
}
