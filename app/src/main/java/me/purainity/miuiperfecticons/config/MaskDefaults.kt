package me.purainity.miuiperfecticons.config

/** 无法识别系统版本时使用旧版遮罩 */
object MaskDefaults {
    fun useNewMask(version: String?): Boolean = version?.toIntOrNull()?.let { it > 14 } ?: false
}
