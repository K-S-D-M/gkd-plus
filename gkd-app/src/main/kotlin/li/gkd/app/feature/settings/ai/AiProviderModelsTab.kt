package li.gkd.app.feature.settings.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import li.gkd.app.MainViewModel
import li.gkd.app.data.settings.AiConfig
import li.gkd.app.data.settings.AiModel
import li.gkd.app.ui.component.GkCheckbox
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.defaultIconTint
import li.gkd.app.util.AiRuleGenerator
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.launchLogged

@Composable
fun AiProviderModelsTab(provider: AiConfig) {
    val mainVm = MainViewModel.requireCurrent()
    val scope = rememberCoroutineScope()

    var fetching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AiModel?>(null) }
    var creating by remember { mutableStateOf(false) }
    var selectionMode by remember(provider.id) { mutableStateOf(false) }
    var selected by remember(provider.id) { mutableStateOf(setOf<String>()) }

    val keyword = query.trim()
    val filtered = remember(provider.models, keyword) {
        if (keyword.isEmpty()) {
            provider.models
        } else {
            provider.models.filter {
                it.displayName.contains(keyword, true) || it.modelId.contains(keyword, true)
            }
        }
    }

    fun saveModels(models: List<AiModel>) {
        AiProviders.mutate(provider.id) { it.copy(models = models) }
    }

    fun setModelId(modelId: String) {
        AiProviders.mutate(provider.id) { it.copy(model = modelId) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "actions") {
                AiSection(title = "模型管理") {
                    AiBasicRow(
                        title = if (fetching) "拉取中…" else "从远端拉取模型",
                        summary = "读取 ${provider.apiUrl.ifBlank { "（未填写地址）" }}/models",
                        startAction = { AiRowIcon(imageVector = GkIcons.ArrowDownward) },
                        onClick = throttle {
                            if (!provider.usable) {
                                toast("请先在「配置」页填写地址、Key 并保存")
                                return@throttle
                            }
                            fetching = true
                            message = null
                            scope.launchLogged {
                                AiRuleGenerator.fetchModels(provider)
                                    .onSuccess { remote ->
                                        val (merged, added) = mergeAiModels(provider.models, remote)
                                        saveModels(merged)
                                        message = if (remote.isEmpty()) {
                                            "接口未返回模型列表"
                                        } else {
                                            "拉取成功：远端 ${remote.size} 个，新增 $added，共 ${merged.size} 个"
                                        }
                                    }.onFailure { e ->
                                        message = "拉取失败：${e.message}"
                                    }
                                fetching = false
                            }
                        },
                    )
                    AiRowDivider(hasLeading = false)
                    AiBasicRow(
                        title = "添加自定义模型",
                        summary = "手动填写 Model ID，用于接口不开放模型列表的服务商",
                        startAction = { AiRowIcon(imageVector = GkIcons.Add) },
                        onClick = throttle {
                            creating = true
                            editing = AiModel(modelId = "", displayName = "")
                        },
                    )
                    AiRowDivider(hasLeading = false)
                    AiBasicRow(
                        title = if (selectionMode) "退出多选" else "批量管理模型",
                        summary = "多选后可一次删除；退出多选可用左侧关闭按钮",
                        startAction = { AiRowIcon(imageVector = GkIcons.Check) },
                        onClick = throttle {
                            selectionMode = !selectionMode
                            selected = emptySet()
                        },
                    )
                    message?.let { text ->
                        AiRowDivider(hasLeading = false)
                        AiHint(
                            text = text,
                            error = text.startsWith("拉取失败"),
                        )
                    }
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("搜索模型") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }

            val listTitle = if (keyword.isEmpty()) {
                "已保存 ${provider.models.size} 个模型"
            } else {
                "匹配 ${filtered.size} / ${provider.models.size} 个模型"
            }

            if (filtered.isEmpty()) {
                item(key = "models_empty") {
                    AiSection(title = listTitle) {
                        AiHint(
                            text = if (provider.models.isEmpty()) {
                                "还没有模型，先「从远端拉取模型」或手动添加"
                            } else {
                                "没有匹配的模型"
                            },
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
            } else {
                val visible = if (selectionMode) provider.models else filtered
                item(key = "models") {
                    AiSection(title = listTitle) {
                        visible.forEachIndexed { index, model ->
                            if (index > 0) {
                                AiRowDivider(hasLeading = false)
                            }
                            ModelRow(
                                provider = provider,
                                model = model,
                                selectionMode = selectionMode,
                                checked = model.modelId in selected,
                                onToggleChecked = {
                                    selected = if (model.modelId in selected) {
                                        selected - model.modelId
                                    } else {
                                        selected + model.modelId
                                    }
                                },
                                onEdit = {
                                    creating = false
                                    editing = model
                                },
                                onSetCurrent = { setModelId(model.modelId) },
                            )
                        }
                    }
                }
            }

            item(key = "bottom") {
                Spacer(modifier = Modifier.height(if (selectionMode) 88.dp else 24.dp))
            }
        }

        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GkSizedIconButton(
                    size = 36.dp,
                    iconSize = 19.dp,
                    onClickLabel = "退出多选",
                    onClick = throttle {
                        selectionMode = false
                        selected = emptySet()
                    },
                    imageVector = GkIcons.Close,
                    contentDescription = "退出多选",
                )
                Text(
                    text = "已选 ${selected.size} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = !fetching,
                    onClick = throttle {
                        selected = if (selected.size == provider.models.size) {
                            emptySet()
                        } else {
                            provider.models.mapTo(mutableSetOf()) { it.modelId }
                        }
                    },
                ) {
                    Text(if (selected.size == provider.models.size) "取消全选" else "全选")
                }
                TextButton(
                    enabled = selected.isNotEmpty() && !fetching,
                    onClick = throttle {
                        scope.launch {
                            if (!mainVm.dialogRequests.confirm(
                                    title = "删除模型",
                                    text = "确定删除选中的 ${selected.size} 个模型？",
                                    confirmText = "删除",
                                    error = true,
                                )
                            ) return@launch
                            saveModels(provider.models.filterNot { it.modelId in selected })
                            toast("已删除 ${selected.size} 个模型")
                            selected = emptySet()
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    editing?.let { model ->
        ModelEditDialog(
            model = model,
            isNew = creating,
            onDismiss = { editing = null },
            onSubmit = { saved ->
                val ids = provider.models.map { it.modelId }.filter { it != model.modelId }
                if (saved.modelId in ids) {
                    toast("模型 ${saved.modelId} 已存在")
                    return@ModelEditDialog
                }
                val rest = provider.models.filterNot { it.modelId == model.modelId }
                val models = if (creating) rest + saved else rest.replaceFirst(saved)
                saveModels(models)
                editing = null
                message = if (creating) "已添加 ${saved.displayName}" else "已更新 ${saved.displayName}"
            },
            onDelete = if (creating) null else {
                {
                    saveModels(provider.models.filterNot { it.modelId == model.modelId })
                    editing = null
                    message = "已删除 ${model.displayName}"
                }
            },
        )
    }
}

@Composable
private fun ModelRow(
    provider: AiConfig,
    model: AiModel,
    selectionMode: Boolean,
    checked: Boolean,
    onToggleChecked: () -> Unit,
    onEdit: () -> Unit,
    onSetCurrent: () -> Unit,
) {
    val isCurrent = provider.model == model.modelId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = if (selectionMode) onToggleChecked else onSetCurrent,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName.ifBlank { model.modelId },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.modelId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                AiTagChip(text = "上下文 ${formatTokens(model.contextWindow)}")
                if (model.reasoning == true) {
                    AiTagChip(text = "支持思考")
                }
                if (isCurrent) {
                    AiTagChip(text = "当前", emphasized = true)
                }
            }
        }
        if (selectionMode) {
            val toggle = throttle(fn = onToggleChecked)
            GkCheckbox(
                checked = checked,
                key = model.modelId,
                onCheckedChange = { toggle() },
            )
        } else {
            GkSizedIconButton(
                size = 36.dp,
                iconSize = 19.dp,
                onClickLabel = "编辑模型参数",
                onClick = throttle(fn = onEdit),
                imageVector = GkIcons.Edit,
                contentDescription = "编辑",
            )
            GkSizedIconButton(
                size = 36.dp,
                iconSize = 19.dp,
                onClickLabel = if (isCurrent) "当前模型" else "设为当前模型",
                onClick = throttle(fn = onSetCurrent),
                imageVector = GkIcons.Check,
                contentDescription = "设为当前",
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else defaultIconTint(),
            )
        }
    }
}

@Composable
private fun ModelEditDialog(
    model: AiModel,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AiModel) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var displayName by remember(model.modelId, isNew) { mutableStateOf(model.displayName) }
    var modelId by remember(model.modelId, isNew) { mutableStateOf(model.modelId) }
    var contextWindow by remember(model.modelId, isNew) {
        mutableStateOf(model.contextWindow.takeIf { it > 0 }?.toString().orEmpty())
    }
    var reasoning by remember(model.modelId, isNew) { mutableStateOf(model.reasoning == true) }
    val contextError = contextWindow.takeIf { it.isNotBlank() && it.toIntOrNull() == null }
    val canSave = displayName.isNotBlank() && modelId.isNotBlank() && contextError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加模型" else "编辑模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("显示名称") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = contextWindow,
                    onValueChange = { contextWindow = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("上下文长度（tokens，可留空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                contextError?.let {
                    AiHint(text = "上下文长度需为正整数", error = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                GkTextSwitch(
                    title = "支持思考",
                    subtitle = "仅用于列表标记，生成时仍按服务商参数请求",
                    checked = reasoning,
                    onCheckedChange = { reasoning = it },
                )
                onDelete?.let { delete ->
                    Text(
                        text = "删除该模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable(onClick = throttle(fn = delete))
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = throttle(fn = onDismiss)) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = throttle {
                    onSubmit(
                        AiModel(
                            modelId = modelId.trim(),
                            displayName = displayName.trim(),
                            contextWindow = contextWindow.trim().toIntOrNull() ?: 0,
                            reasoning = reasoning,
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
    )
}

/** 保持原有顺序替换模型；模型不在列表里时追加（远端拉取的编辑场景）。 */
private fun List<AiModel>.replaceFirst(model: AiModel): List<AiModel> {
    val index = indexOfFirst { it.modelId == model.modelId }
    return if (index < 0) this + model else toMutableList().also { it[index] = model }
}
