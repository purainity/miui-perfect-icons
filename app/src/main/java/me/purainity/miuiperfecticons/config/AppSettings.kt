package me.purainity.miuiperfecticons.config

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** 以 SharedPreferences 持久化的全部用户配置 */
class AppSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme: IconTheme
        get() = IconThemes.find(preferences.getString(KEY_THEME, null))
        set(value) {
            require(IconThemes.all.contains(value))
            preferences.edit { putString(KEY_THEME, value.id) }
        }

    var outputDirectory: Uri?
        get() = preferences.getString(KEY_OUTPUT_DIRECTORY, null)?.let(Uri::parse)
        set(value) {
            preferences.edit { putString(KEY_OUTPUT_DIRECTORY, value?.toString()) }
        }

    var customDirectory: Uri?
        get() = preferences.getString(KEY_CUSTOM_DIRECTORY, null)?.let(Uri::parse)
        set(value) { preferences.edit { putString(KEY_CUSTOM_DIRECTORY, value?.toString()) } }

    var customEnabled: Boolean
        get() = preferences.getBoolean(KEY_CUSTOM_ENABLED, false)
        set(value) { preferences.edit { putBoolean(KEY_CUSTOM_ENABLED, value) } }

    var disableAllDynamic: Boolean
        get() = preferences.getBoolean(KEY_DISABLE_ALL, false)
        set(value) { preferences.edit { putBoolean(KEY_DISABLE_ALL, value) } }

    var disabledPackages: Set<String>
        get() = preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        set(value) { preferences.edit { putStringSet(KEY_DISABLED_PACKAGES, value.toSet()) } }

    /** 反射读取 ro.miui.ui.version.code，读取失败或非数字时返回 null */
    val systemVersion: String? by lazy {
        try {
            // 保留现有 MIUI 版本识别；私有 API 不可用时仍沿用默认值与手动开关
            @SuppressLint("PrivateApi")
            val properties = Class.forName("android.os.SystemProperties")
            (properties.getMethod("get", String::class.java).invoke(null, "ro.miui.ui.version.code") as? String)
                ?.takeIf { it.toIntOrNull() != null }
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    var useNewMask: Boolean
        get() = preferences.getBoolean(KEY_NEW_MASK, MaskDefaults.useNewMask(systemVersion))
        set(value) {
            preferences.edit { putBoolean(KEY_NEW_MASK, value) }
        }

    private companion object {
        const val KEY_CUSTOM_DIRECTORY = "custom_directory"
        const val KEY_CUSTOM_ENABLED = "custom_enabled"
        const val KEY_DISABLE_ALL = "disable_all_dynamic"
        const val KEY_DISABLED_PACKAGES = "disabled_packages"
        const val KEY_THEME = "theme_id"
        const val KEY_NEW_MASK = "use_new_mask"
        const val KEY_OUTPUT_DIRECTORY = "output_directory"
    }
}
