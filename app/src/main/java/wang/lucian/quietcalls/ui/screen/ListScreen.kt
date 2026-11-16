// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import wang.lucian.quietcalls.data.entity.ListType
import wang.lucian.quietcalls.ui.screen.settings.RegionMultiPicker
import wang.lucian.quietcalls.viewmodel.ListViewModel

/** 黑白名单只管理号码与地域；分类、添加和删除都在一个页面完成。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(viewModel: ListViewModel, parentPagerState: PagerState) {
    val black by viewModel.blackList.collectAsState()
    val white by viewModel.whiteList.collectAsState()
    val context = LocalContext.current
    var region by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var regionEditor by remember { mutableStateOf<ListType?>(null) }
    val type = viewModel.currentTab
    val regionMode = region && type == ListType.BLACK
    val entries = if (type == ListType.BLACK) black else white
    val visible = entries.filter { it.locationMatch == regionMode &&
        (it.phoneNumber.contains(query.trim(), true) || it.remark.orEmpty().contains(query.trim(), true)) }
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearToast() }
    }
    val navigation = viewModel.pendingTabNavigationRequest
    LaunchedEffect(navigation?.id) {
        navigation?.let { viewModel.selectTab(it.listType); region = false; query = ""; viewModel.completeTabNavigationRequest(it.id) }
    }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = if (type == ListType.BLACK) 0 else 1) {
            listOf(ListType.BLACK, ListType.WHITE).forEach { tab ->
                Tab(selected = type == tab, onClick = { viewModel.selectTab(tab); region = false; query = "" },
                    text = { Text(if (tab == ListType.BLACK) "黑名单 · ${black.size}" else "白名单 · ${white.size}") })
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (type == ListType.BLACK) "拦截指定号码或地域。通讯录和明确号码白名单优先放行。"
                else "白名单号码不受静话的拦截规则影响；运营商侧的拦截设置独立生效。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !regionMode, onClick = { region = false; query = "" },
                    label = { Text("号码 · ${entries.count { !it.locationMatch }}") })
                if (type == ListType.BLACK) FilterChip(selected = regionMode, onClick = { region = true; query = "" },
                    label = { Text("地域 · ${entries.count { it.locationMatch }}") })
                Spacer(Modifier.weight(1f))
                FilledTonalButton(enabled = !viewModel.isSaving, onClick = {
                    if (regionMode) regionEditor = type else viewModel.openAddSheet()
                }) { Text(if (regionMode) "选择地域" else "添加号码") }
            }
            if (entries.count { it.locationMatch == regionMode } > 5 || query.isNotEmpty()) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (regionMode) "搜索地域" else "搜索号码") })
            }
            if (viewModel.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (query.isNotEmpty()) "没有匹配的条目" else if (regionMode) "还没有添加地域" else "还没有添加号码",
                        style = MaterialTheme.typography.titleMedium)
                    Text(if (regionMode) "点“选择地域”，可搜索并多选省份或城市。" else "点“添加号码”，输入完整号码或选择号段前缀。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(visible, key = { it.id }) { entry ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(
                        headlineContent = { Text(entry.phoneNumber.replace('/', '·')) },
                        supportingContent = { Text(if (entry.locationMatch) {
                            if (type == ListType.BLACK) "地域拦截" else "陌生号码地域通行"
                        } else (if (entry.isPrefix) "号段前缀" else "完整号码") + entry.remark?.let { " · $it" }.orEmpty()) },
                        leadingContent = { Icon(if (entry.locationMatch) Icons.Default.LocationOn else Icons.Default.Phone, null) },
                        trailingContent = { IconButton(enabled = !viewModel.isSaving, onClick = { viewModel.requestDelete(entry) }) {
                            Icon(Icons.Default.DeleteOutline, "移除 ${entry.phoneNumber}")
                        } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable(enabled = !viewModel.isSaving) {
                            if (entry.locationMatch) regionEditor = type else viewModel.openEditSheet(entry)
                        },
                    )
                }
            }
        }
    }
    regionEditor?.takeIf { it == ListType.BLACK }?.let { target ->
        val selected = (if (target == ListType.BLACK) black else white).filter { it.locationMatch }.map { it.phoneNumber }
        RegionMultiPicker(selected, { regionEditor = null },
            title = if (target == ListType.BLACK) "选择拦截地域" else "选择通行地域",
            explanation = if (target == ListType.BLACK) "可多选省份或城市，按号码归属地拦截。通讯录和指定号码白名单优先。"
                else "可多选省份或城市，仅豁免陌生人拦截，仍检查骚扰名单和特殊号段。",
        ) { values -> viewModel.saveRegions(target, values); regionEditor = null }
    }
    if (viewModel.showAddSheet) NumberListEditor(viewModel)
    viewModel.pendingSaveConfirmation?.let {
        AlertDialog(onDismissRequest = viewModel::cancelPendingSave, title = { Text("确认号段范围") },
            text = { Text("${it.scopeDisplayValue} 开头的号码都将${if (it.listType == ListType.BLACK) "按黑名单处理" else "优先放行"}。") },
            confirmButton = { TextButton(onClick = viewModel::confirmPendingSave) { Text("确认保存") } },
            dismissButton = { TextButton(onClick = viewModel::cancelPendingSave) { Text("取消") } })
    }
    if (viewModel.showDeleteDialog) AlertDialog(onDismissRequest = viewModel::cancelDelete,
        title = { Text("移除此条规则？") }, text = { Text("移除后，后续来电按其余拦截和通行规则处理。") },
        confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("移除") } },
        dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("取消") } })
}

@Composable
private fun NumberListEditor(viewModel: ListViewModel) {
    val editing = viewModel.editingEntry != null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(onDismissRequest = viewModel::closeAddSheet, title = { Text(if (editing) "编辑号码" else "添加号码") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!viewModel.inputIsPrefix, { viewModel.inputIsPrefix = false }, label = { Text("完整号码") })
                FilterChip(viewModel.inputIsPrefix, { viewModel.inputIsPrefix = true }, label = { Text("号段前缀") })
            }
            OutlinedTextField(viewModel.inputPhone, { viewModel.inputPhone = it }, modifier = Modifier.focusRequester(focus), singleLine = true,
                label = { Text(if (viewModel.inputIsPrefix) "号码开头，例如 400" else "完整电话号码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = viewModel.addErrorMessage != null)
            viewModel.addErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (viewModel.editingEntry?.forceBlock == true) Text("该历史规则使用强制拦截。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = viewModel::requestSave, enabled = !viewModel.isSaving && viewModel.inputPhone.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = viewModel::closeAddSheet, enabled = !viewModel.isSaving) { Text("取消") } })
}
