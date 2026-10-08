package li.gkd.app.feature.settings.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.MainViewModel
import li.gkd.app.data.settings.AiConfig
import li.gkd.app.data.settings.AiEndpointMode
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.LabeledField
import li.gkd.app.ui.component.TextSearchListDialog
import li.gkd.app.util.AiProtocolOption
import li.gkd.app.util.AiRuleGenerator
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.findOption
import li.gkd.app.util.launchLogged

@Composable
fun AiProviderDetailPage(route: AiProviderDetailRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val scope = rememberCoroutineScope()
    val store by storeFlow.collectAsStateWithLifecycle()
    var createdId by remember { mutableStateOf<String?>(null) }

    val providerId = route.id ?: createdId
    val provider = providerId?.let { id -> store.aiProviders.firstOrNull { it.id == id } }

    if (providerId != null && provider == null) {
        MissingProviderPage(onBack = { mainVm.popPage() })
        return
    }

    val isNew = provider == null
    var tab by remember { mutableIntStateOf(0) }
    var draft by remember(providerId) {
        mutableStateOf(provider?.let(AiProviderDraft::of) ?: AiProviderDraft.new(route.protocol))
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
                },
                title = { Text(text = if (isNew) "新建服务商" else draft.name.ifBlank { "服务商" }) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            if (!isNew) {
                TabRow(
                    selectedTabIndex = tab,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("配置", "模型").forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (isNew || tab == 0) {
                    AiProviderConfigTab(
                        provider = provider ?: AiConfig(),
                        draft = draft,
                        isNew = isNew,
                        scope = scope,
                        onDraftChange = { draft = it },
                        onCreated = { createdId = it },
                        onRemoved = { mainVm.popPage() },
                    )
                } else {
                    AiProviderModelsTab(provider)
                }
            }
        }
    }
}

@Composable
private fun MissingProviderPage(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = onBack,
                    )
                },
                title = { Text(text = "服务商不存在") },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "该服务商已被移除",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiProviderConfigTab(
    provider: AiConfig,
    draft: AiProviderDraft,
    isNew: Boolean,
    scope: CoroutineScope,
    onDraftChange: (AiProviderDraft) -> Unit,
    onCreated: (String) -> Unit,
    onRemoved: () -> Unit,
) {
    val mainVm = MainViewModel.requireCurrent()
    var apiKeyVisible by remember { mutableStateOf(false) }
    var headersExpanded by remember { mutableStateOf(false) }
    var showEndpointDlg by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val protocolOption = AiProtocolOption.objects.findOption(draft.protocol)
    val baseUrl = draft.apiUrl.ifBlank { protocolOption.placeholder }

    fun buildDraftProvider(): AiConfig = draft.toProvider(provider)

    fun update(transform: (AiProviderDraft) -> AiProviderDraft) {
        onDraftChange(transform(draft))
        testResult = null
    }

    if (showEndpointDlg) {
        TextSearchListDialog(
            onDismiss = { showEndpointDlg = false },
            title = "端点模式",
            selectedText = AiEndpointMode.labels[draft.endpointMode],
            textList = listOf(AiEndpointMode.CHAT, AiEndpointMode.RESPONSES).map { mode ->
                AiEndpointMode.labels[mode]!! to { update { it.copy(endpointMode = mode) } }
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "connection") {
            AiSection(title = "连接配置") {
                Column(modifier = Modifier.padding(16.dp)) {
                    FieldLabel("名称")
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { v -> update { it.copy(name = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如 DeepSeek 官方") },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FieldLabel("Base URL")
                    OutlinedTextField(
                        value = draft.apiUrl,
                        onValueChange = { v -> update { it.copy(apiUrl = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(protocolOption.placeholder) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FieldLabel("API Key")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft.apiKey,
                            onValueChange = { v -> update { it.copy(apiKey = v) } },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("请输入 API Key") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (apiKeyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        GkSizedIconButton(
                            size = 36.dp,
                            iconSize = 19.dp,
                            onClickLabel = if (apiKeyVisible) "隐藏 API Key" else "显示 API Key",
                            onClick = { apiKeyVisible = !apiKeyVisible },
                            imageVector = if (apiKeyVisible) GkIcons.ToggleOff else GkIcons.ToggleOn,
                            contentDescription = "显示/隐藏 API Key",
                        )
                    }
                    if (draft.isAnthropic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FieldLabel("anthropic-version")
                        OutlinedTextField(
                            value = draft.anthropicVersion,
                            onValueChange = { v -> update { it.copy(anthropicVersion = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(AiConfig.DEFAULT_ANTHROPIC_VERSION) },
                            singleLine = true,
                        )
                    }
                }
                if (!draft.isAnthropic) {
                    AiRowDivider(hasLeading = false)
                    AiPickerRow(
                        title = "端点模式",
                        value = AiEndpointMode.labels[draft.endpointMode] ?: draft.endpointMode,
                        summary = if (draft.endpointMode == AiEndpointMode.RESPONSES) {
                            "调用 /responses，只带 temperature 与 max_output_tokens"
                        } else {
                            "调用 /chat/completions，支持 top_p 与 system 消息"
                        },
                        onClick = { showEndpointDlg = true },
                    )
                }
                AiRowDivider(hasLeading = false)
                AiBasicRow(
                    title = if (testing) "测试连接中…" else "测试连接",
                    summary = testResult ?: "读取 $baseUrl/models，并把返回的模型合并进模型列表",
                    onClick = throttle {
                        val error = draft.validationError()
                        if (error != null) {
                            testResult = "校验未通过：$error"
                            return@throttle
                        }
                        testing = true
                        testResult = null
                        val config = buildDraftProvider()
                        scope.launchLogged {
                            AiRuleGenerator.fetchModels(config)
                                .onSuccess { remote ->
                                    val (merged, added) = mergeAiModels(config.models, remote)
                                    if (!isNew) {
                                        AiProviders.mutate(provider.id) { it.copy(models = merged) }
                                    }
                                    testResult = if (remote.isEmpty()) {
                                        "接口未返回模型列表"
                                    } else {
                                        "连接成功：远端 ${remote.size} 个，新增 $added"
                                    }
                                }.onFailure { e ->
                                    testResult = "连接失败：${e.message}"
                                }
                            testing = false
                        }
                    },
                )
            }
        }

        item(key = "headers") {
            AiSection(title = "自定义请求头") {
                val rotation by animateFloatAsState(if (headersExpanded) 180f else 0f)
                AiBasicRow(
                    title = if (draft.headers.isEmpty()) "未设置" else "已设置 ${draft.headers.size} 项",
                    summary = "会先于认证头写入，因此 Authorization / x-api-key 仍以 API Key 为准。",
                    onClick = throttle { headersExpanded = !headersExpanded },
                    endActions = {
                        GkIcon(
                            imageVector = GkIcons.ExpandMore,
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                if (headersExpanded) {
                    draft.headers.forEach { header ->
                        AiRowDivider(hasLeading = false)
                        HeaderRow(
                            header = header,
                            onNameChange = { name ->
                                update {
                                    it.copy(
                                        headers = it.headers.map { h ->
                                            if (h.key == header.key) h.copy(name = name) else h
                                        },
                                    )
                                }
                            },
                            onValueChange = { value ->
                                update {
                                    it.copy(
                                        headers = it.headers.map { h ->
                                            if (h.key == header.key) h.copy(value = value) else h
                                        },
                                    )
                                }
                            },
                            onRemove = {
                                update { it.copy(headers = it.headers.filterNot { h -> h.key == header.key }) }
                            },
                        )
                    }
                    AiRowDivider(hasLeading = false)
                    AiBasicRow(
                        title = "添加请求头",
                        startAction = { AiRowIcon(imageVector = GkIcons.Add) },
                        endActions = { GkIcon(imageVector = GkIcons.Add) },
                        onClick = throttle { update { it.copy(headers = it.headers + AiHeaderDraft()) } },
                    )
                }
            }
        }

        item(key = "preferences") {
            AiSection(title = "偏好与提示词") {
                GkTextSwitch(
                    title = "启用此服务商",
                    subtitle = "停用后不参与快照自动生成",
                    checked = draft.enabled,
                    onCheckedChange = { v -> update { it.copy(enabled = v) } },
                )
                AiRowDivider(hasLeading = false)
                Column(modifier = Modifier.padding(16.dp)) {
                    FieldLabel("系统提示词")
                    OutlinedTextField(
                        value = draft.systemPrompt,
                        onValueChange = { v -> update { it.copy(systemPrompt = v) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = { Text("追加在内置提示词之前") },
                    )
                    AiHint(
                        text = "留空则只使用内置的 gkd-rule-generator-prompt.md。",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item(key = "params") {
            AiSection(title = "生成参数") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LabeledField(
                        label = "Temperature",
                        value = draft.temperature,
                        onValueChange = { v -> update { it.copy(temperature = v) } },
                        placeholder = "0 ~ 2，越大输出越随机",
                        keyboardType = KeyboardType.Decimal,
                    )
                    LabeledField(
                        label = "Top P",
                        value = draft.topP,
                        onValueChange = { v -> update { it.copy(topP = v) } },
                        placeholder = "0 ~ 1，通常与 Temperature 二选一",
                        keyboardType = KeyboardType.Decimal,
                    )
                    LabeledField(
                        label = "Max Tokens",
                        value = draft.maxTokens,
                        onValueChange = { v -> update { it.copy(maxTokens = v) } },
                        placeholder = "1 ~ ${AiProviderDraft.MAX_TOKENS_LIMIT}，规则 JSON 建议 4096",
                        keyboardType = KeyboardType.Number,
                    )
                    AiHint(
                        text = "快照规则输出是结构化 JSON，Temperature 建议保持 0，可减少选择器漂移。",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        item(key = "actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = throttle {
                        val error = draft.validationError()
                        if (error != null) {
                            status = "保存失败：$error"
                            return@throttle
                        }
                        val config = buildDraftProvider()
                        if (isNew) {
                            onCreated(AiProviders.add(config))
                            status = "已创建，切到「模型」页签拉取模型"
                            toast("已创建服务商 ${config.name}")
                        } else {
                            AiProviders.save(config)
                            status = if (config.enabled) {
                                "已保存"
                            } else {
                                "已保存，但该服务商处于停用状态"
                            }
                            toast("AI 配置已保存")
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            testing -> "处理中…"
                            isNew -> "创建服务商"
                            else -> "保存配置"
                        }
                    )
                }
                status?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("保存失败")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (!isNew) {
            item(key = "danger") {
                AiSection(title = "危险操作") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !testing) {
                                scope.launch {
                                    if (!mainVm.dialogRequests.confirm(
                                            title = "移除服务商",
                                            text = "确定移除「${provider.name.ifBlank { "未命名" }}」？它的模型列表会一并删除。",
                                            confirmText = "移除",
                                            error = true,
                                        )
                                    ) return@launch
                                    AiProviders.remove(provider.id)
                                    toast("已移除 ${provider.name.ifBlank { "未命名" }}")
                                    onRemoved()
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "移除服务商",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item(key = "bottom") {
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun HeaderRow(
    header: AiHeaderDraft,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = header.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Header 名") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = header.value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("值") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        GkSizedIconButton(
            size = 36.dp,
            iconSize = 18.dp,
            onClickLabel = "删除请求头",
            onClick = throttle(fn = onRemove),
            imageVector = GkIcons.Delete,
            contentDescription = "删除",
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
