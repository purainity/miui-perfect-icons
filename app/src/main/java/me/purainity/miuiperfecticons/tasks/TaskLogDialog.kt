package me.purainity.miuiperfecticons.tasks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import me.purainity.miuiperfecticons.R
import me.purainity.miuiperfecticons.databinding.DialogTaskLogBinding
import java.util.Locale

/** 生成过程的任务面板（BottomSheet）：标题 + 状态文本 + 进度条，单行双按钮随状态切换 */
class TaskLogDialog : BottomSheetDialogFragment() {
    private var viewBinding: DialogTaskLogBinding? = null
    private val listener: (TaskSnapshot) -> Unit = { render(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = DialogTaskLogBinding.inflate(inflater, container, false)
        viewBinding = binding
        binding.buttonPrimary.setOnClickListener {
            val state = TaskRuntime.current()
            when {
                state.running -> {
                    TaskService.cancel()
                    binding.buttonPrimary.isEnabled = false
                }
                state.error != null -> TaskService.start(requireContext(), state.kind)
                state.resultUri != null -> try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(state.resultUri, state.resultMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: ActivityNotFoundException) {
                    Snackbar.make(binding.root, getString(R.string.task_open_failed), Snackbar.LENGTH_LONG).show()
                }
            }
        }
        binding.buttonSecondary.setOnClickListener {
            if (!TaskRuntime.current().running && TaskRuntime.current().canUseCache) {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.task_rate_limit_message)
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.task_use_cache) { _, _ ->
                        TaskService.start(requireContext(), TaskRuntime.current().kind, true)
                    }
                    .show()
            } else dismiss()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        TaskRuntime.observe(listener)
    }

    override fun onStop() {
        TaskRuntime.remove(listener)
        super.onStop()
    }

    override fun onDestroyView() {
        viewBinding = null
        super.onDestroyView()
    }

    private fun render(state: TaskSnapshot) {
        val binding = viewBinding ?: return
        binding.title.text = when (state.kind) {
            TaskService.MTZ -> getString(R.string.task_dialog_mtz)
            TaskService.MODULE -> getString(R.string.task_dialog_module)
            TaskService.ARCHIVE -> getString(R.string.task_dialog_archive)
            TaskService.SYNC -> getString(R.string.task_dialog_sync)
            else -> ""
        }
        binding.title.visibility = if (binding.title.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.statusText.text = state.statusText.ifBlank { state.stage }
        binding.progress.visibility = if (state.running) View.VISIBLE else View.GONE
        binding.progress.isIndeterminate = state.totalBytes <= 0
        if (state.totalBytes > 0) binding.progress.setProgressCompat((state.completedBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100), true)
        binding.progressText.text = if (state.totalBytes > 0) String.format(Locale.getDefault(), "%.1f / %.1f MB", state.completedBytes / 1048576.0, state.totalBytes / 1048576.0) else ""
        binding.progressText.visibility = if (state.totalBytes > 0) View.VISIBLE else View.GONE
        if (state.running) {
            binding.buttonSecondary.setText(R.string.task_background)
            binding.buttonPrimary.setText(R.string.task_cancel)
            binding.buttonPrimary.visibility = View.VISIBLE
            binding.buttonPrimary.isEnabled = !TaskRuntime.cancelled.get()
        } else {
            binding.buttonSecondary.setText(if (state.canUseCache) R.string.task_use_cache else R.string.task_close)
            binding.buttonPrimary.text = when {
                state.error != null -> getString(R.string.task_retry)
                state.resultUri != null -> getString(R.string.task_open)
                else -> ""
            }
            binding.buttonPrimary.visibility = if (binding.buttonPrimary.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
}
