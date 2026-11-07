package me.purainity.miuiperfecticons.ui

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/** 为系统文件选择器提供自定义图标目录的建议起始位置 */
object CustomDirectoryLocation {
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val CUSTOM_DIRECTORY_NAME = "MIUI完美图标自定义"

    fun recommendedUri(): Uri = DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        "primary:${Environment.DIRECTORY_DOCUMENTS}/$CUSTOM_DIRECTORY_NAME",
    )
}
