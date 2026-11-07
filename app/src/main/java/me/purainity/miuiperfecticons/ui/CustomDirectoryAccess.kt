package me.purainity.miuiperfecticons.ui

/** 判断保存的目录是否仍拥有完整的持久读写授权 */
object CustomDirectoryAccess {
    data class Grant(val uri: String, val canRead: Boolean, val canWrite: Boolean)

    fun canReuse(directory: String?, grants: Iterable<Grant>): Boolean =
        directory != null && grants.any { it.uri == directory && it.canRead && it.canWrite }
}
