package com.bananalab.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class GenerationRequest(
    val prompt: String,
    val sourcePrompt: String,
    val promptMode: PromptMode,
    val aspectRatio: String,
    val imageSize: String,
    val enableSearch: Boolean,
    val baseImage: SelectedImage,
    val referenceImages: List<SelectedImage>,
)

class BananaLabViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val api = BananaLabApi(prefs.serverUrl)
    private val authSessionStore = AuthSessionStore(application)

    private val _state = MutableStateFlow(
        AppUiState(
            serverUrl = prefs.serverUrl,
            promptText = prefs.promptText,
            aspectRatio = prefs.aspectRatio,
            imageSize = prefs.imageSize,
            enableSearch = prefs.enableSearch,
            promptMode = runCatching { PromptMode.valueOf(prefs.promptMode.replaceFirstChar { it.uppercase() }) }.getOrElse { PromptMode.Default },
            selectedPersonaId = prefs.selectedPersonaId,
            activeTab = runCatching { AppTab.valueOf(prefs.selectedTab) }.getOrElse { AppTab.Generate },
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var generationProgressJob: Job? = null
    private var optimizeProgressJob: Job? = null
    private var lastGenerationRequest: GenerationRequest? = null

    init {
        viewModelScope.launch {
            restorePersistedSession()
            refreshAllInternal()
        }
    }

    fun setActiveTab(tab: AppTab) {
        prefs.selectedTab = tab.name
        _state.update { it.copy(activeTab = tab) }
    }

    fun setServerUrl(value: String) {
        _state.update { it.copy(serverUrl = value, errorMessage = "") }
    }

    fun applyServerUrl() {
        val normalized = normalizeServerUrl(_state.value.serverUrl)
        viewModelScope.launch {
            authSessionStore.clear()
            prefs.serverUrl = normalized
            api.updateServerUrl(normalized)
            _state.update {
                it.copy(
                    serverUrl = normalized,
                    authenticated = false,
                    passwordEnabled = false,
                    loginPassword = "",
                    currentResult = null,
                    history = emptyList(),
                    historySelection = emptySet(),
                    personas = emptyList(),
                    selectedPersonaId = "",
                    personaEditor = PersonaEditorState(),
                    promptLibrary = PromptLibraryState(),
                    optimizedPrompt = "",
                    generationRetryAvailable = false,
                    statusMessage = "服务器地址已更新，正在重新连接...",
                    errorMessage = "",
                )
            }
            refreshAllInternal()
        }
    }

    fun setLoginPassword(value: String) {
        _state.update { it.copy(loginPassword = value, errorMessage = "") }
    }

    fun login() {
        val password = _state.value.loginPassword.trim()
        if (password.isBlank()) {
            _state.update { it.copy(errorMessage = "请输入密码。") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loginInProgress = true, errorMessage = "", statusMessage = "正在登录...") }
            runCatching { api.login(password) }
                .onSuccess { result ->
                    val sessionJson = api.exportSessionCookies()
                    if (sessionJson.isNotBlank()) {
                        authSessionStore.save(
                            serverUrl = _state.value.serverUrl,
                            cookiesJson = sessionJson,
                        )
                    }
                    _state.update {
                        it.copy(
                            authenticated = result.authenticated,
                            passwordEnabled = result.passwordEnabled,
                            loginPassword = "",
                            loginInProgress = false,
                            statusMessage = "登录成功。",
                            errorMessage = "",
                            generationRetryAvailable = false,
                        )
                    }
                    loadProtectedData()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loginInProgress = false,
                            errorMessage = error.message ?: "登录失败。",
                            statusMessage = "",
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            authSessionStore.clear()
            _state.update {
                it.copy(
                    authenticated = false,
                    passwordEnabled = true,
                    loginPassword = "",
                    history = emptyList(),
                    historySelection = emptySet(),
                    personas = emptyList(),
                    selectedPersonaId = "",
                    personaEditor = PersonaEditorState(),
                    promptLibrary = PromptLibraryState(),
                    currentResult = null,
                    generationRetryAvailable = false,
                    statusMessage = "已退出登录。",
                    errorMessage = "",
                )
            }
        }
    }

    fun setPromptText(value: String) {
        prefs.promptText = value
        _state.update { it.copy(promptText = value, errorMessage = "") }
    }

    fun setPromptMode(mode: PromptMode) {
        prefs.promptMode = mode.name.lowercase()
        _state.update { it.copy(promptMode = mode, errorMessage = "") }
    }

    fun setAspectRatio(value: String) {
        prefs.aspectRatio = value
        _state.update { it.copy(aspectRatio = value, errorMessage = "") }
    }

    fun setImageSize(value: String) {
        prefs.imageSize = value
        _state.update { it.copy(imageSize = value, errorMessage = "") }
    }

    fun setEnableSearch(value: Boolean) {
        prefs.enableSearch = value
        _state.update { it.copy(enableSearch = value, errorMessage = "") }
    }

    fun setSelectedPersonaId(value: String) {
        prefs.selectedPersonaId = value
        _state.update { it.copy(selectedPersonaId = value) }
    }

    fun insertPrompt(text: String) {
        val current = _state.value.promptText.trim()
        val next = if (current.isBlank()) text.trim() else "$current\n${text.trim()}"
        setPromptText(next)
    }

    fun setBaseImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                createSelectedImage(getApplication(), uri, isBaseImage = true)
            }.onSuccess { image ->
                _state.update { it.copy(baseImage = image, statusMessage = imageStatus(image, "基础图")) }
            }.onFailure { error ->
                _state.update { it.copy(errorMessage = error.message ?: "基础图读取失败。") }
            }
        }
    }

    fun addReferenceImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val currentCount = _state.value.referenceImages.size
            val slots = (6 - currentCount).coerceAtLeast(0)
            val accepted = uris.take(slots)
            if (accepted.isEmpty()) {
                _state.update { it.copy(errorMessage = "参考图最多 6 张。") }
                return@launch
            }

            val images = mutableListOf<SelectedImage>()
            for (uri in accepted) {
                val image = runCatching {
                    createSelectedImage(getApplication(), uri, isBaseImage = false)
                }.getOrElse { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "参考图读取失败。") }
                    continue
                }
                images += image
            }
            if (images.isNotEmpty()) {
                _state.update {
                    it.copy(
                        referenceImages = it.referenceImages + images,
                        statusMessage = "${images.size} 张参考图已添加。",
                        errorMessage = "",
                    )
                }
            }
        }
    }

    fun removeReferenceImage(index: Int) {
        _state.update {
            if (index !in it.referenceImages.indices) return@update it
            it.copy(referenceImages = it.referenceImages.toMutableList().apply { removeAt(index) })
        }
    }

    fun clearBaseImage() {
        _state.update { it.copy(baseImage = null) }
    }

    fun clearReferenceImages() {
        _state.update { it.copy(referenceImages = emptyList()) }
    }

    fun loadPromptLibrary() {
        viewModelScope.launch {
            runCatching { api.fetchPromptLibrary() }
                .onSuccess { library ->
                    _state.update { it.copy(promptLibrary = library, errorMessage = "") }
                }
                .onFailure { error -> handleRequestFailure(error, "提示词库加载失败。") }
        }
    }

    fun savePromptLibrary(content: String) {
        viewModelScope.launch {
            runCatching { api.savePromptLibrary(content) }
                .onSuccess { library ->
                    _state.update {
                        it.copy(promptLibrary = library, statusMessage = "提示词库已保存。")
                    }
                }
                .onFailure { error -> handleRequestFailure(error, "保存提示词库失败。") }
        }
    }

    fun loadPersonas() {
        viewModelScope.launch {
            runCatching { api.fetchPersonas() }
                .onSuccess { personas ->
                    val selected = personas.firstOrNull { it.id == _state.value.selectedPersonaId }?.id ?: personas.firstOrNull()?.id.orEmpty()
                    _state.update {
                        it.copy(
                            personas = personas,
                            selectedPersonaId = selected,
                            errorMessage = "",
                        )
                    }
                    if (selected.isNotBlank()) {
                        loadPersonaDetail(selected)
                    } else {
                        _state.update { it.copy(personaEditor = PersonaEditorState()) }
                    }
                }
                .onFailure { error -> handleRequestFailure(error, "人设加载失败。") }
        }
    }

    fun loadPersonaDetail(personaId: String) {
        if (personaId.isBlank()) {
            _state.update { it.copy(personaEditor = PersonaEditorState()) }
            return
        }
        viewModelScope.launch {
            runCatching { api.fetchPersona(personaId) }
                .onSuccess { persona ->
                    _state.update {
                        it.copy(
                            selectedPersonaId = persona.id,
                            personaEditor = PersonaEditorState(
                                id = persona.id,
                                filename = persona.filename,
                                name = persona.name,
                                summary = persona.summary,
                                content = persona.content,
                                isNew = false,
                            ),
                            errorMessage = "",
                        )
                    }
                    prefs.selectedPersonaId = persona.id
                }
                .onFailure { error -> handleRequestFailure(error, "加载人设详情失败。") }
        }
    }

    fun newPersona() {
        _state.update {
            it.copy(
                personaEditor = PersonaEditorState(
                    id = "",
                    filename = "persona.md",
                    name = "新建人格",
                    summary = "请填写一句人格简介",
                    content = "你是一个擅长将中文图像需求转写为高质量英文提示词的助手。",
                    isNew = true,
                ),
            )
        }
    }

    fun setPersonaEditor(value: PersonaEditorState) {
        _state.update { it.copy(personaEditor = value) }
    }

    fun savePersona() {
        val editor = _state.value.personaEditor
        if (editor.name.isBlank() || editor.summary.isBlank() || editor.content.isBlank()) {
            _state.update { it.copy(errorMessage = "人设名称、简介和内容都不能为空。") }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (editor.isNew || editor.id.isBlank()) {
                    api.createPersona(editor.toPersonaDetail())
                } else {
                    api.updatePersona(editor.id, editor.toPersonaDetail())
                }
            }.onSuccess { persona ->
                _state.update {
                    it.copy(
                        personaEditor = PersonaEditorState(
                            id = persona.id,
                            filename = persona.filename,
                            name = persona.name,
                            summary = persona.summary,
                            content = persona.content,
                            isNew = false,
                        ),
                        selectedPersonaId = persona.id,
                        statusMessage = "人设已保存。",
                        errorMessage = "",
                    )
                }
                prefs.selectedPersonaId = persona.id
                loadPersonas()
            }.onFailure { error -> handleRequestFailure(error, "保存人设失败。") }
        }
    }

    fun deletePersona() {
        val editor = _state.value.personaEditor
        if (editor.id.isBlank()) {
            _state.update { it.copy(errorMessage = "请选择一个人设再删除。") }
            return
        }
        viewModelScope.launch {
            runCatching { api.deletePersona(editor.id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            personaEditor = PersonaEditorState(),
                            selectedPersonaId = "",
                            statusMessage = "人设已删除。",
                        )
                    }
                    loadPersonas()
                }
                .onFailure { error -> handleRequestFailure(error, "删除人设失败。") }
        }
    }

    fun importPersonaMd(content: String, fallbackName: String = "persona.md") {
        val lines = content.lines()
        if (lines.size < 3) {
            _state.update { it.copy(errorMessage = "人设文件格式不正确，至少需要名称、简介和正文三部分。") }
            return
        }
        val name = lines[0].trim()
        val summary = lines[1].trim()
        val body = lines.drop(2).joinToString("\n").trim()
        if (name.isBlank() || summary.isBlank() || body.isBlank()) {
            _state.update { it.copy(errorMessage = "人设文件内容不能为空。") }
            return
        }
        _state.update {
            it.copy(
                personaEditor = PersonaEditorState(
                    id = "",
                    filename = fallbackName,
                    name = name,
                    summary = summary,
                    content = body,
                    isNew = true,
                ),
                statusMessage = "已导入人格文件，可直接保存。",
                errorMessage = "",
            )
        }
    }

    fun optimizePrompt() {
        val prompt = _state.value.promptText.trim()
        if (prompt.isBlank()) {
            _state.update { it.copy(errorMessage = "请先输入要优化的提示词。") }
            return
        }
        if (_state.value.personas.isEmpty()) {
            _state.update { it.copy(errorMessage = "未找到可用的人设文件，请先在提示词页添加 .md 文件。") }
            return
        }
        val personaId = _state.value.selectedPersonaId.ifBlank { _state.value.personas.firstOrNull()?.id.orEmpty() }
        if (personaId.isBlank()) {
            _state.update { it.copy(errorMessage = "请先选择一个转译人设。") }
            return
        }
        viewModelScope.launch {
            optimizeProgressJob?.cancel()
            _state.update { it.copy(optimizingPrompt = true, optimizeMessage = "正在发送提示词...") }
            optimizeProgressJob = launch {
                val messages = listOf("正在发送提示词...", "模型正在翻译优化...", "正在整理英文提示词...", "即将完成...")
                for (message in messages) {
                    _state.update { it.copy(optimizeMessage = message) }
                    delay(1200)
                }
            }
            runCatching { api.optimizePrompt(prompt, personaId) }
                .onSuccess { json ->
                    val optimized = extractOptimizedPrompt(json)
                    val personaName = json.optString("personaName").orEmpty()
                    _state.update {
                        it.copy(
                            optimizedPrompt = optimized,
                            optimizingPrompt = false,
                            optimizeMessage = "提示词优化完成，当前使用人设：${personaName.ifBlank { "未命名人设" }}。",
                            errorMessage = "",
                        )
                    }
                }
                .onFailure { error ->
                    if (error is ApiHttpException && error.code == 401) {
                        handleSessionExpired("登录已过期，请重新登录。")
                    } else {
                        _state.update {
                            it.copy(
                                optimizingPrompt = false,
                                optimizeMessage = "",
                                errorMessage = error.message ?: "提示词优化失败。",
                            )
                        }
                    }
                }
            optimizeProgressJob?.cancel()
            optimizeProgressJob = null
        }
    }

    fun generate() {
        val request = buildGenerationRequest() ?: return
        lastGenerationRequest = request
        executeGeneration(request)
    }

    fun retryGeneration() {
        val request = lastGenerationRequest ?: return
        executeGeneration(request)
    }

    private fun buildGenerationRequest(): GenerationRequest? {
        val current = _state.value
        val baseImage = current.baseImage
        if (baseImage == null) {
            _state.update { it.copy(errorMessage = "请先选择基础结构图。") }
            return null
        }
        val promptMode = current.promptMode
        val finalPrompt = when (promptMode) {
            PromptMode.Default -> current.promptText.trim()
            PromptMode.Optimized -> current.optimizedPrompt.trim().ifBlank { current.promptText.trim() }
        }
        if (promptMode == PromptMode.Optimized && current.optimizedPrompt.isBlank()) {
            _state.update { it.copy(errorMessage = "请先完成提示词优化。") }
            return null
        }
        if (finalPrompt.isBlank()) {
            _state.update { it.copy(errorMessage = "请先输入提示词。") }
            return null
        }
        return GenerationRequest(
            prompt = finalPrompt,
            sourcePrompt = current.promptText,
            promptMode = promptMode,
            aspectRatio = current.aspectRatio,
            imageSize = current.imageSize,
            enableSearch = current.enableSearch,
            baseImage = baseImage,
            referenceImages = current.referenceImages,
        )
    }

    private fun executeGeneration(request: GenerationRequest) {
        viewModelScope.launch {
            generationProgressJob?.cancel()
            _state.update {
                it.copy(
                    generationInProgress = true,
                    generationMessage = "正在上传素材...",
                    generationRetryAvailable = false,
                    errorMessage = "",
                )
            }
            generationProgressJob = launch {
                val steps = listOf(
                    "正在上传素材...",
                    "正在整理提示词...",
                    "Banana Pro 正在理解基础图...",
                    "正在融合风格参考...",
                    "正在渲染细节...",
                    "正在整理结果...",
                )
                for (message in steps) {
                    _state.update { it.copy(generationMessage = message) }
                    delay(1400)
                }
            }

            runCatching {
                api.generate(
                    prompt = request.prompt,
                    sourcePrompt = request.sourcePrompt,
                    promptMode = request.promptMode.name.lowercase(),
                    aspectRatio = request.aspectRatio,
                    imageSize = request.imageSize,
                    enableSearch = request.enableSearch,
                    baseImage = request.baseImage,
                    referenceImages = request.referenceImages,
                )
            }.onSuccess { entry ->
                lastGenerationRequest = request
                _state.update {
                    it.copy(
                        generationInProgress = false,
                        generationMessage = "",
                        currentResult = entry,
                        generationRetryAvailable = false,
                        statusMessage = "生成成功。",
                        errorMessage = "",
                    )
                }
                GenerationNotificationManager.showSuccess(getApplication(), "图片已生成完成。")
                loadHistory(selectEntryId = entry.id)
            }.onFailure { error ->
                val message = when (error) {
                    is ApiHttpException -> if (error.code == 401) {
                        "登录已过期，请重新登录。"
                    } else {
                        error.message
                    }
                    else -> error.message ?: "生成失败。"
                }
                _state.update {
                    it.copy(
                        generationInProgress = false,
                        generationMessage = "",
                        generationRetryAvailable = true,
                        statusMessage = "",
                        errorMessage = message,
                    )
                }
                if (error is ApiHttpException && error.code == 401) {
                    handleSessionExpired(message)
                }
                GenerationNotificationManager.showFailure(getApplication(), message)
            }
            generationProgressJob?.cancel()
            generationProgressJob = null
        }
    }

    fun loadHistory(selectEntryId: String? = null) {
        viewModelScope.launch {
            runCatching { api.fetchHistory() }
                .onSuccess { items ->
                    val selection = _state.value.historySelection.filterTo(mutableSetOf()) { selected ->
                        items.any { it.id == selected }
                    }
                    val current = selectEntryId?.let { id -> items.firstOrNull { it.id == id } } ?: _state.value.currentResult
                    _state.update {
                        it.copy(
                            history = items,
                            historySelection = selection,
                            currentResult = current ?: items.firstOrNull(),
                            errorMessage = "",
                        )
                    }
                }
                .onFailure { error -> handleRequestFailure(error, "历史记录加载失败。") }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshAllInternal()
        }
    }

    fun refreshProtectedData() {
        if (_state.value.authenticated || !_state.value.passwordEnabled) {
            loadProtectedData()
        }
    }

    private suspend fun restorePersistedSession() {
        val persisted = authSessionStore.load()
        if (persisted == null) {
            return
        }
        if (persisted.serverUrl != _state.value.serverUrl || persisted.isExpired()) {
            authSessionStore.clear()
            api.clearSession()
            return
        }
        api.restoreSessionCookies(persisted.cookiesJson)
        _state.update {
            it.copy(
                authenticated = true,
                passwordEnabled = true,
                statusMessage = "已恢复 24 小时登录状态。",
                errorMessage = "",
            )
        }
    }

    private suspend fun refreshAllInternal() {
        val hadRestoredSession = _state.value.authenticated
        runCatching { api.authStatus() }
            .onSuccess { auth ->
                if (auth.authenticated || !auth.passwordEnabled) {
                    _state.update {
                        it.copy(
                            authenticated = auth.authenticated || !auth.passwordEnabled,
                            passwordEnabled = auth.passwordEnabled,
                            errorMessage = "",
                        )
                    }
                    if (auth.authenticated || !auth.passwordEnabled) {
                        if (!auth.passwordEnabled) {
                            authSessionStore.clear()
                        }
                        loadProtectedData()
                    }
                    return@onSuccess
                }

                authSessionStore.clear()
                api.clearSession()
                clearProtectedState()
                _state.update {
                    it.copy(
                        authenticated = false,
                        passwordEnabled = auth.passwordEnabled,
                        loginPassword = "",
                        statusMessage = "",
                        errorMessage = "",
                    )
                }
            }
            .onFailure { error ->
                if (error is ApiHttpException && error.code == 401) {
                    handleSessionExpired("登录已过期，请重新登录。")
                    return@onFailure
                }
                _state.update {
                    it.copy(
                        errorMessage = error.message ?: "无法连接服务器。",
                        statusMessage = if (hadRestoredSession) "会话已恢复，正在等待服务器响应。" else it.statusMessage,
                    )
                }
            }
    }

    private fun clearProtectedState() {
        _state.update {
            it.copy(
                currentResult = null,
                history = emptyList(),
                historySelection = emptySet(),
                personas = emptyList(),
                selectedPersonaId = "",
                personaEditor = PersonaEditorState(),
                promptLibrary = PromptLibraryState(),
                optimizedPrompt = "",
                optimizingPrompt = false,
                optimizeMessage = "",
                generationInProgress = false,
                generationMessage = "",
                generationRetryAvailable = false,
                pendingDownloadCount = 0,
            )
        }
    }

    private fun handleSessionExpired(message: String) {
        viewModelScope.launch {
            authSessionStore.clear()
            api.clearSession()
            clearProtectedState()
            _state.update {
                it.copy(
                    authenticated = false,
                    passwordEnabled = true,
                    loginPassword = "",
                    loginInProgress = false,
                    statusMessage = "",
                    errorMessage = message,
                )
            }
        }
    }

    private fun handleRequestFailure(error: Throwable, fallbackMessage: String) {
        if (error is ApiHttpException && error.code == 401) {
            handleSessionExpired("登录已过期，请重新登录。")
            return
        }
        _state.update { it.copy(errorMessage = error.message ?: fallbackMessage) }
    }

    fun toggleHistorySelection(id: String) {
        _state.update {
            val next = it.historySelection.toMutableSet()
            if (!next.add(id)) next.remove(id)
            it.copy(historySelection = next)
        }
    }

    fun selectAllHistory() {
        _state.update {
            it.copy(historySelection = it.history.mapTo(mutableSetOf()) { entry -> entry.id })
        }
    }

    fun clearHistorySelection() {
        _state.update { it.copy(historySelection = emptySet()) }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteHistory(id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            historySelection = it.historySelection - id,
                            statusMessage = "历史记录已删除。",
                            errorMessage = "",
                        )
                    }
                    loadHistory()
                }
                .onFailure { error -> handleRequestFailure(error, "删除历史失败。") }
        }
    }

    fun downloadHistoryItem(entry: HistoryEntry) {
        viewModelScope.launch {
            runCatching {
                val directUrl = api.preferredImageUrl(entry).ifBlank { api.preferredThumbUrl(entry) }
                if (directUrl.isBlank()) error("未找到可用下载地址。")
                val absoluteUrl = api.absoluteUrl(directUrl)
                DownloadHelper.enqueue(
                    context = getApplication(),
                    url = absoluteUrl,
                    fileName = api.downloadName(entry),
                    mimeType = null,
                )
            }.onSuccess {
                _state.update { it.copy(statusMessage = "已加入下载队列。") }
            }.onFailure { error -> handleRequestFailure(error, "下载失败。") }
        }
    }

    fun downloadCurrentResult() {
        val current = _state.value.currentResult ?: return
        downloadHistoryItem(current)
    }

    fun downloadSelectedHistory() {
        val ids = _state.value.historySelection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(pendingDownloadCount = ids.size, statusMessage = "正在准备下载...") }
            val selectedEntries = _state.value.history.filter { it.id in ids }
            runCatching {
                val targetCount = selectedEntries.size
                if (targetCount == 0) {
                    error("选中的图片没有可用下载地址。")
                }
                for (entry in selectedEntries) {
                    val directUrl = api.preferredImageUrl(entry).ifBlank { api.preferredThumbUrl(entry) }
                    if (directUrl.isBlank()) continue
                    val absoluteUrl = api.absoluteUrl(directUrl)
                    DownloadHelper.enqueue(
                        context = getApplication(),
                        url = absoluteUrl,
                        fileName = api.downloadName(entry),
                        mimeType = null,
                    )
                    delay(120)
                }
                targetCount
            }.onSuccess { count ->
                _state.update {
                    it.copy(
                        pendingDownloadCount = 0,
                        statusMessage = "批量下载已加入队列：$count 张。",
                        errorMessage = "",
                    )
                }
            }.onFailure { error ->
                if (error is ApiHttpException && error.code == 401) {
                    handleSessionExpired("登录已过期，请重新登录。")
                } else {
                    _state.update { it.copy(pendingDownloadCount = 0, errorMessage = error.message ?: "批量下载失败。") }
                }
            }
        }
    }

    fun copyPromptSourceToCurrentResult() {
        val prompt = _state.value.currentResult?.prompt.orEmpty()
        if (prompt.isBlank()) {
            _state.update { it.copy(errorMessage = "当前结果没有可复制的提示词。") }
        }
    }

    fun sendHistoryToBase(entry: HistoryEntry) {
        viewModelScope.launch {
            runCatching {
                val bytes = api.downloadBytes(api.preferredImageUrl(entry))
                createSelectedImageFromBytes(bytes, entry.downloadName.ifBlank { "base-image.jpg" }, isBaseImage = true)
            }.onSuccess { image ->
                _state.update { it.copy(baseImage = image, statusMessage = "已发送到基础结构图。") }
            }.onFailure { error -> handleRequestFailure(error, "发送到基础图失败。") }
        }
    }

    fun sendHistoryToReference(entry: HistoryEntry) {
        viewModelScope.launch {
            runCatching {
                val bytes = api.downloadBytes(api.preferredImageUrl(entry))
                createSelectedImageFromBytes(bytes, entry.downloadName.ifBlank { "reference-image.jpg" }, isBaseImage = false)
            }.onSuccess { image ->
                _state.update {
                    if (it.referenceImages.size >= 6) {
                        it.copy(errorMessage = "参考图最多 6 张。")
                    } else {
                        it.copy(
                            referenceImages = it.referenceImages + image,
                            statusMessage = "已发送到风格参考图。",
                            errorMessage = "",
                        )
                    }
                }
            }.onFailure { error -> handleRequestFailure(error, "发送到参考图失败。") }
        }
    }

    fun sendCurrentResultToBase() {
        val entry = _state.value.currentResult ?: return
        sendHistoryToBase(entry)
    }

    fun sendCurrentResultToReference() {
        val entry = _state.value.currentResult ?: return
        sendHistoryToReference(entry)
    }

    suspend fun loadRemoteBitmap(url: String): ImageBitmap {
        return api.loadBitmap(url)
    }

    fun refreshPromptData() {
        if (_state.value.authenticated || !_state.value.passwordEnabled) {
            loadPromptLibrary()
            loadPersonas()
        }
    }

    fun clearMessages() {
        _state.update { it.copy(statusMessage = "", errorMessage = "", optimizeMessage = "", generationMessage = "") }
    }

    fun setPersonaFromList(personaId: String) {
        setSelectedPersonaId(personaId)
        loadPersonaDetail(personaId)
    }

    fun updatePersonaEditorFilename(value: String) {
        _state.update { it.copy(personaEditor = it.personaEditor.copy(filename = value)) }
    }

    fun updatePersonaEditorName(value: String) {
        _state.update { it.copy(personaEditor = it.personaEditor.copy(name = value)) }
    }

    fun updatePersonaEditorSummary(value: String) {
        _state.update { it.copy(personaEditor = it.personaEditor.copy(summary = value)) }
    }

    fun updatePersonaEditorContent(value: String) {
        _state.update { it.copy(personaEditor = it.personaEditor.copy(content = value)) }
    }

    fun selectPromptLibraryItem(value: String) {
        insertPrompt(value)
    }

    fun useCurrentResultPrompt() {
        val prompt = _state.value.currentResult?.prompt.orEmpty()
        if (prompt.isNotBlank()) {
            setPromptText(prompt)
        }
    }

    fun applyStatusMessage(message: String) {
        _state.update { it.copy(statusMessage = message, errorMessage = "") }
    }

    fun applyErrorMessage(message: String) {
        _state.update { it.copy(errorMessage = message, statusMessage = "") }
    }

    fun togglePromptMode() {
        setPromptMode(if (_state.value.promptMode == PromptMode.Default) PromptMode.Optimized else PromptMode.Default)
    }

    private fun loadProtectedData() {
        loadPromptLibrary()
        loadPersonas()
        loadHistory()
    }

    private fun imageStatus(image: SelectedImage, label: String): String {
        return buildString {
            append("${label}已选择")
            if (image.compressed) append("，已自动压缩")
            append("。")
        }
    }

    private fun normalizeServerUrl(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) value = AppPreferences.DEFAULT_SERVER_URL
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        if (!value.endsWith("/")) value += "/"
        return value
    }

    private fun extractOptimizedPrompt(json: JSONObject): String {
        return when {
            json.optString("prompt").isNotBlank() -> json.optString("prompt")
            json.optString("optimizedPrompt").isNotBlank() -> json.optString("optimizedPrompt")
            else -> ""
        }
    }

    private fun PersonaEditorState.toPersonaDetail(): PersonaDetail {
        return PersonaDetail(
            id = id,
            name = name.trim(),
            summary = summary.trim(),
            content = content.trim(),
            filename = filename.trim().ifBlank { "persona.md" },
        )
    }
}
