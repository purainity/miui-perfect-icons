package me.purainity.miuiperfecticons.engine

/** GitHub 访问配置：访问令牌与可选镜像基址
 *  令牌仅发送至官方 api.github.com 与 raw.githubusercontent.com，镜像属于第三方一律不附带凭据
 *  三个基址均含仓库路径，语义对称：apiBase 拼接 API 端点；siteBase 拼接主站端点（版本检查的 Atom feed）；
 *  rawBase 以 @ 结尾表示 jsDelivr 引用形态（{base}{commit}/{path}），否则为 {base}/{commit}/{path} */
class GitHubEndpoint(
    val token: String? = null,
    val apiBase: String = API_DEFAULT,
    val rawBase: String = RAW_DEFAULT,
    val siteBase: String = SITE_DEFAULT,
) {
    fun apiUrl(path: String): String = "$apiBase/$path"

    fun siteUrl(path: String): String = "$siteBase/$path"

    /** 版本检查的零限额通道：主站 Atom feed，条目链接含最新 commit SHA */
    fun atomUrl(): String = siteUrl("commits/main.atom")

    fun rawUrl(commit: String, encodedPath: String): String =
        if (rawBase.endsWith(AT)) "$rawBase$commit/$encodedPath" else "$rawBase/$commit/$encodedPath"

    /** 仅当请求官方域名时返回令牌，镜像不转发任何凭据（主站域一律不带令牌） */
    fun authorize(url: String): String? =
        token?.takeIf { url.startsWith("$API_HOST/") || url.startsWith("$RAW_HOST/") }

    /** 镜像地址跟随服务端重定向，官方地址维持不重定向语义 */
    val followRedirects: Boolean get() = apiBase != API_DEFAULT || rawBase != RAW_DEFAULT || siteBase != SITE_DEFAULT

    fun followRedirectsFor(url: String): Boolean = followRedirects &&
        listOf(API_HOST, RAW_HOST, SITE_HOST).none { url.startsWith("$it/") }

    companion object {
        const val API_DEFAULT = "https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project"
        const val RAW_DEFAULT = "https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project"
        const val SITE_DEFAULT = "https://github.com/pzcn/Perfect-Icons-Completion-Project"
        private const val API_HOST = "https://api.github.com"
        private const val RAW_HOST = "https://raw.githubusercontent.com"
        private const val SITE_HOST = "https://github.com"
        private const val AT = "@"

        /** 规范化用户输入的基址：去首尾空白与尾部斜杠、要求 http(s) 开头且不含空白，非法时返回 null */
        fun normalize(value: String): String? {
            val trimmed = value.trim().trimEnd('/')
            if (!trimmed.matches(Regex("https?://\\S+"))) return null
            return trimmed
        }
    }
}
