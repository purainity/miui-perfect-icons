package me.purainity.miuiperfecticons.ui

import android.view.View
import android.widget.CompoundButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/** 将列表行与行内选择控件合并为一个无障碍目标，读屏时呈现为可勾选项 */
fun View.exposeSelectionState(control: CompoundButton) {
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = control.accessibilityClassName
            info.isCheckable = true
            info.isChecked = control.isChecked
        }
    })
}
