// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import wang.lucian.quietcalls.R
import wang.lucian.quietcalls.data.query.QueryBackendState
import wang.lucian.quietcalls.data.query.QueryBackendType
import wang.lucian.quietcalls.viewmodel.SettingViewModel

/** 显示与提醒分组；保留震动、通知与当前服务状态的原有门禁。 */
@Composable
fun DisplayPreferences(
    viewModel: SettingViewModel,
    backendState: QueryBackendState,
    feedbackPermissions: List<String>,
    onRequestFeedbackPermissions: (Array<String>) -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
) {
    BlockedNotificationPreference()
    val context = LocalContext.current
    val selfHostedSelected = when (backendState) {
        is QueryBackendState.Ready -> backendState.type == QueryBackendType.SELF_HOSTED
        is QueryBackendState.Blocked -> true
    }
    val onlineQueryEnabled = !viewModel.noNetworkQuery
    val feedbackEnabled = onlineQueryEnabled && !selfHostedSelected
    val feedbackSummary = when {
        !onlineQueryEnabled -> stringResource(
            R.string.setting_feedback_notification_no_network_summary
        )
        selfHostedSelected -> stringResource(
            R.string.setting_feedback_notification_self_hosted_summary
        )
        else -> stringResource(R.string.setting_feedback_notification_summary)
    }

    SettingsSection(title = stringResource(R.string.setting_group_display_and_feedback)) {
        SettingsGroup(
            rows = listOf(

            SettingsRowItem("call-state-vibration") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.callStateVibrationEnabled,
                    title = stringResource(R.string.setting_call_state_vibration),
                    summary = stringResource(R.string.setting_call_state_vibration_summary),
                    icon = Icons.Default.Vibration,
                    onCheckedChange = { enabled ->
                        when {
                            !enabled -> viewModel.updateCallStateVibrationEnabled(false)
                            ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_PHONE_STATE,
                            ) == PackageManager.PERMISSION_GRANTED ->
                                viewModel.updateCallStateVibrationEnabled(true)
                            else -> onRequestPhoneStatePermission()
                        }
                    },
                )
            },

            ),
        )
    }
}
