// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import wang.lucian.quietcalls.R
import wang.lucian.quietcalls.viewmodel.SettingViewModel

/** 记录与备份分组；备份隐私边界由现有选择弹窗集中说明。 */
@Composable
fun BackupRestorePreferences(
    viewModel: SettingViewModel,
    onNavigateToMyReports: () -> Unit,
    onNavigateToLocalNumberLabels: () -> Unit,
    onRestore: () -> Unit,
) {


    SettingsSection(title = stringResource(R.string.setting_group_backup_and_restore)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("backup-data") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_backup_records),
                    summary = stringResource(R.string.setting_backup_records_summary),
                    icon = Icons.Default.Backup,
                    onClick = viewModel::openBackupOptionsSheet,
                )
            },
            SettingsRowItem("restore-data") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_restore_records),
                    summary = stringResource(R.string.setting_restore_records_summary),
                    icon = Icons.Default.RestorePage,
                    onClick = onRestore,
                )
            },
            ),
        )
    }
}
