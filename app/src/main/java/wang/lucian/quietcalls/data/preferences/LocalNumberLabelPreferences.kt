// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级本地号码标签显示开关。
 *
 * 新安装默认关闭；键只声明在此处，不在 SettingViewModel 中重复定义。
 */
class LocalNumberLabelPreferences(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_SHOW_LOCAL_NUMBER_LABELS = "show_local_number_labels"
    }

    private val _enabled = MutableStateFlow(
        false
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_LOCAL_NUMBER_LABELS, false) }
        _enabled.value = false
    }
}
