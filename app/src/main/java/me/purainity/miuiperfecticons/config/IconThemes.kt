package me.purainity.miuiperfecticons.config

/** 图标仓库中的主题标识与界面显示名称 */
data class IconTheme(val id: String, val displayName: String) {
    val assemblyName: String get() = "${displayName}主题"
}

object IconThemes {
    val all = listOf(
        IconTheme("default", "MIUI经典"),
        IconTheme("Hyper", "HyperOS"),
        IconTheme("explore", "MIUI探·界"),
        IconTheme("coloros12", "ColorOS12"),
        IconTheme("coloros13", "ColorOS13"),
        IconTheme("coloros14", "ColorOS14"),
        IconTheme("flyme9", "Flyme9"),
        IconTheme("flyme10", "Flyme10"),
        IconTheme("iOS", "iOS"),
        IconTheme("lrone", "Lrone"),
        IconTheme("luck7", "Luck7"),
        IconTheme("perfectcurve", "完美曲线"),
        IconTheme("Aquamarine", "鸿蒙-润泽"),
        IconTheme("AmethystLake", "EMUI-紫晶湖"),
        IconTheme("GoldenBeach", "EMUI-碧海金沙"),
        IconTheme("LightWings", "EMUI-光之翼"),
        IconTheme("Nebulae", "EMUI-宇宙之眼"),
        IconTheme("StarrySky", "EMUI-星夜彩绘"),
        IconTheme("Reconstruction", "EMUI-未来之翼"),
    )

    val default: IconTheme = all.first()

    // 未保存或无效的 ID 回退默认主题
    fun find(id: String?): IconTheme = all.firstOrNull { it.id == id } ?: default
}
