package me.purainity.miuiperfecticons

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import me.purainity.miuiperfecticons.config.AppSettings
import me.purainity.miuiperfecticons.config.IconThemes
import me.purainity.miuiperfecticons.databinding.ActivityThemeSelectionBinding
import me.purainity.miuiperfecticons.databinding.ItemThemeSelectionBinding
import me.purainity.miuiperfecticons.ui.applySystemBarInsets
import me.purainity.miuiperfecticons.ui.exposeSelectionState

/** 主题选择页：点选即保存并自动返回，不提供确定按钮 */
class ThemeSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThemeSelectionBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityThemeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.toolbar.setNavigationOnClickListener { finish() }
        settings = AppSettings(this)

        IconThemes.all.forEach { theme ->
            val row = ItemThemeSelectionBinding.inflate(layoutInflater, binding.themes, true)
            row.title.text = theme.displayName
            row.subtitle.visibility = if (theme.id == "Hyper") View.VISIBLE else View.GONE
            row.radio.isChecked = theme == settings.theme
            row.root.contentDescription = if (theme.id == "Hyper") {
                getString(R.string.selection_with_description, theme.displayName, getString(R.string.hyper_theme_description))
            } else theme.displayName
            row.root.exposeSelectionState(row.radio)
            val select = {
                settings.theme = theme
                finish()
            }
            row.root.setOnClickListener { select() }
        }
    }
}
