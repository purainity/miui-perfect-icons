package me.purainity.miuiperfecticons.config

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import me.purainity.miuiperfecticons.engine.GitHubEndpoint

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

    /** GitHub 访问令牌：明文保存，界面引导使用无权限令牌（仅公开数据只读）以控制泄露风险 */
    var githubToken: String?
        get() = preferences.getString(KEY_GITHUB_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) {
            preferences.edit { putString(KEY_GITHUB_TOKEN, value?.trim()?.takeIf { it.isNotEmpty() }) }
        }

    /** 镜像功能默认关闭，开启后仅影响 GitHub 请求的地址基址 */
    var mirrorEnabled: Boolean
        get() = preferences.getBoolean(KEY_MIRROR_ENABLED, false)
        set(value) {
            preferences.edit { putBoolean(KEY_MIRROR_ENABLED, value) }
        }

    var mirrorPreset: String
        get() = preferences.getString(KEY_MIRROR_PRESET, null) ?: MirrorPresets.default.id
        set(value) {
            require(MirrorPresets.all.any { it.id == value })
            preferences.edit { putString(KEY_MIRROR_PRESET, value) }
        }

    /** 自定义镜像的两个地址前缀，留空或非法的部分回退官方直连 */
    var mirrorCustomApi: String?
        get() = preferences.getString(KEY_MIRROR_CUSTOM_API, null)
        set(value) { preferences.edit { putString(KEY_MIRROR_CUSTOM_API, value?.trim()) } }

    var mirrorCustomRaw: String?
        get() = preferences.getString(KEY_MIRROR_CUSTOM_RAW, null)
        set(value) { preferences.edit { putString(KEY_MIRROR_CUSTOM_RAW, value?.trim()) } }

    /** 组装当前生效的 GitHub 访问配置：镜像开启时替换基址，令牌仅在官方域名请求中附带 */
    fun githubEndpoint(): GitHubEndpoint {
        val token = githubToken
        if (!mirrorEnabled) return GitHubEndpoint(token)
        val preset = MirrorPresets.find(mirrorPreset)
        val apiBase = when {
            preset.id == MirrorPresets.custom.id -> normalized(mirrorCustomApi) ?: GitHubEndpoint.API_DEFAULT
            preset.apiBase != null -> preset.apiBase
            else -> GitHubEndpoint.API_DEFAULT
        }
        val rawBase = when {
            preset.id == MirrorPresets.custom.id -> normalized(mirrorCustomRaw) ?: GitHubEndpoint.RAW_DEFAULT
            preset.rawBase != null -> preset.rawBase
            else -> GitHubEndpoint.RAW_DEFAULT
        }
        val siteBase = preset.siteBase ?: GitHubEndpoint.SITE_DEFAULT
        return GitHubEndpoint(token, apiBase, rawBase, siteBase)
    }

    private fun normalized(value: String?): String? = value?.let(GitHubEndpoint::normalize)

    private companion object {
        const val KEY_CUSTOM_DIRECTORY = "custom_directory"
        const val KEY_CUSTOM_ENABLED = "custom_enabled"
        const val KEY_DISABLE_ALL = "disable_all_dynamic"
        const val KEY_DISABLED_PACKAGES = "disabled_packages"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_MIRROR_CUSTOM_API = "mirror_custom_api"
        const val KEY_MIRROR_CUSTOM_RAW = "mirror_custom_raw"
        const val KEY_MIRROR_ENABLED = "mirror_enabled"
        const val KEY_MIRROR_PRESET = "mirror_preset"
        const val KEY_THEME = "theme_id"
        const val KEY_NEW_MASK = "use_new_mask"
        const val KEY_OUTPUT_DIRECTORY = "output_directory"
    }
}
