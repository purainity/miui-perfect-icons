package me.purainity.miuiperfecticons.config

import me.purainity.miuiperfecticons.engine.GitHubEndpoint

/** 常见镜像候选：仅作为界面预填选项，APP 默认直连，不内置任何对镜像可用性的保证
 *  形态分三类：API 与文件均可用（双源，另含主站 Atom 通道）、仅文件（API 走官方直连）、jsDelivr 引用形态 */
data class MirrorPreset(
    val id: String,
    val label: String,
    val description: String,
    val apiBase: String?,
    val rawBase: String?,
    val siteBase: String? = null,
)

object MirrorPresets {
    private const val API = GitHubEndpoint.API_DEFAULT
    private const val RAW = GitHubEndpoint.RAW_DEFAULT
    private const val SITE = GitHubEndpoint.SITE_DEFAULT

    /** 自定义项：实际基址取自用户输入的两个前缀字段，留空的部分走官方直连 */
    val custom = MirrorPreset("custom", "自定义", "填写一个或两个地址前缀", null, null)

    // 双源镜像在前（含主站 Atom 通道），仅文件镜像与 jsDelivr 系在后
    val all = listOf(
        MirrorPreset("gh-proxy", "gh-proxy.com", "API 与文件下载共用前缀", "https://gh-proxy.com/$API", "https://gh-proxy.com/$RAW", "https://gh-proxy.com/$SITE"),
        MirrorPreset("cnxiaobai", "github.cnxiaobai.com", "API 与文件下载共用前缀", "https://github.cnxiaobai.com/$API", "https://github.cnxiaobai.com/$RAW", "https://github.cnxiaobai.com/$SITE"),
        MirrorPreset("noki", "gh.noki.icu", "API 与文件下载共用前缀", "https://gh.noki.icu/$API", "https://gh.noki.icu/$RAW", "https://gh.noki.icu/$SITE"),
        MirrorPreset("chenc", "github.chenc.dev", "API 与文件下载共用前缀", "https://github.chenc.dev/$API", "https://github.chenc.dev/$RAW", "https://github.chenc.dev/$SITE"),
        MirrorPreset("keleyaa", "ghp.keleyaa.com", "API 与文件下载共用前缀", "https://ghp.keleyaa.com/$API", "https://ghp.keleyaa.com/$RAW", "https://ghp.keleyaa.com/$SITE"),
        MirrorPreset("nxnow", "gh.nxnow.top", "API 与文件下载共用前缀", "https://gh.nxnow.top/$API", "https://gh.nxnow.top/$RAW", "https://gh.nxnow.top/$SITE"),
        MirrorPreset("six-github", "6github", "API 与文件下载（域名替换形态）", "https://api.6github.com/repos/pzcn/Perfect-Icons-Completion-Project", "https://raw.6githubusercontent.com/pzcn/Perfect-Icons-Completion-Project", "https://6github.com/pzcn/Perfect-Icons-Completion-Project"),
        MirrorPreset("ghproxy-net", "ghproxy.net", "仅文件下载，API 走官方直连", null, "https://ghproxy.net/$RAW"),
        MirrorPreset("ghfast", "ghfast.top", "仅文件下载，API 走官方直连", null, "https://ghfast.top/$RAW"),
        MirrorPreset("ednovas", "github.ednovas.xyz", "仅文件下载，API 走官方直连", null, "https://github.ednovas.xyz/$RAW"),
        MirrorPreset("skybyte", "ghproxy.mirror.skybyte.me", "仅文件下载，API 走官方直连", null, "https://ghproxy.mirror.skybyte.me/$RAW"),
        MirrorPreset("tianrld", "github.tianrld.top", "仅文件下载，API 走官方直连", null, "https://github.tianrld.top/$RAW"),
        MirrorPreset("one-ms", "github.1ms.xx.kg", "仅文件下载，API 走官方直连", null, "https://github.1ms.xx.kg/$RAW"),
        MirrorPreset("mlmle", "github.mlmle.cn", "仅文件下载，API 走官方直连", null, "https://github.mlmle.cn/$RAW"),
        MirrorPreset("jsdelivr", "jsDelivr CDN", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://cdn.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdelivr-fastly", "jsDelivr（Fastly）", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://fastly.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdelivr-gcore", "jsDelivr（Gcore）", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://gcore.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdelivr-testingcf", "jsDelivr（Cloudflare）", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://testingcf.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdelivr-quantil", "jsDelivr（Quantil）", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://quantil.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdelivr-originfastly", "jsDelivr（Origin Fastly）", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://originfastly.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsd-onmicrosoft", "jsd.onmicrosoft.cn", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://jsd.onmicrosoft.cn/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdmirror", "cdn.jsdmirror.com", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://cdn.jsdmirror.com/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdmirror-cn", "cdn.jsdmirror.cn", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://cdn.jsdmirror.cn/gh/pzcn/Perfect-Icons-Completion-Project@"),
        MirrorPreset("jsdelivr-5210521", "jsdelivr.5210521.xyz", "仅文件下载（jsDelivr 引用形态），API 走官方直连", null, "https://jsdelivr.5210521.xyz/gh/pzcn/Perfect-Icons-Completion-Project@"),
        custom,
    )

    val default: MirrorPreset = all.first()

    // 未保存或无效的 ID 回退默认镜像
    fun find(id: String?): MirrorPreset = all.firstOrNull { it.id == id } ?: default
}
