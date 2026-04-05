package com.bananalab.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class GenerationRequest(
    val apiPlatformId: String,
    val imageModel: String,
    val prompt: String,
    val sourcePrompt: String,
    val promptMode: PromptMode,
    val aspectRatio: String,
    val imageSize: String,
    val enableSearch: Boolean,
    val baseImage: SelectedImage,
    val referenceImages: List<SelectedImage>,
)

private sealed interface GenerationPipelineResult {
    data class Success(
        val entry: HistoryEntry,
        val recoveredFromHistory: Boolean,
    ) : GenerationPipelineResult

    data class Failure(
        val message: String,
        val sessionExpired: Boolean,
    ) : GenerationPipelineResult
}

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
            selectedApiPlatformId = prefs.selectedApiPlatformId,
            selectedImageModel = prefs.selectedImageModel,
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
            prefs.selectedApiPlatformId = ""
            prefs.selectedImageModel = ""
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
                    apiPlatforms = emptyList(),
                    apiPlatformsLoaded = false,
                    selectedApiPlatformId = "",
                    selectedImageModel = "",
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
                    apiPlatforms = emptyList(),
                    apiPlatformsLoaded = false,
                    selectedApiPlatformId = "",
                    selectedImageModel = "",
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

    fun loadApiPlatforms() {
        viewModelScope.launch {
            runCatching { api.fetchApiPlatforms() }
                .onSuccess { config ->
                    val preferredPlatformId = _state.value.selectedApiPlatformId.ifBlank { prefs.selectedApiPlatformId }
                    val preferredModel = _state.value.selectedImageModel.ifBlank { prefs.selectedImageModel }
                    applyApiPlatformSelection(
                        platforms = config.items,
                        preferredPlatformId = preferredPlatformId,
                        preferredModel = preferredModel,
                        fallbackPlatformId = config.defaultPlatformId,
                        fallbackModel = config.defaultImageModel,
                    )
                }
                .onFailure { error ->
                    _state.update { it.copy(apiPlatformsLoaded = true) }
                    handleRequestFailure(error, "API 平台配置加载失败。")
                }
        }
    }

    fun setSelectedApiPlatform(platformId: String) {
        val current = _state.value
        val platform = resolveSelectedPlatform(
            platforms = current.apiPlatforms,
            preferredPlatformId = platformId,
            fallbackPlatformId = current.selectedApiPlatformId,
        ) ?: return
        val selectedModel = resolveSelectedImageModel(
            platform = platform,
            preferredModel = current.selectedImageModel,
        )
        persistApiPlatformSelection(platform.id, selectedModel)
        _state.update {
            it.copy(
                selectedApiPlatformId = platform.id,
                selectedImageModel = selectedModel,
                errorMessage = "",
            )
        }
    }

    fun setSelectedImageModel(model: String) {
        val current = _state.value
        val platform = current.apiPlatforms.firstOrNull { it.id == current.selectedApiPlatformId } ?: return
        val selectedModel = resolveSelectedImageModel(
            platform = platform,
            preferredModel = model,
            fallbackModel = current.selectedImageModel,
        )
        persistApiPlatformSelection(platform.id, selectedModel)
        _state.update {
            it.copy(
                selectedImageModel = selectedModel,
                errorMessage = "",
            )
        }
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
        setActiveTab(AppTab.Result)
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
        val apiPlatform = current.apiPlatforms.firstOrNull { it.id == current.selectedApiPlatformId }
        if (apiPlatform == null) {
            _state.update { it.copy(errorMessage = "请先选择 API 平台。") }
            return null
        }
        val imageModel = current.selectedImageModel.trim()
        if (imageModel.isBlank()) {
            _state.update { it.copy(errorMessage = "请先选择生成模型。") }
            return null
        }
        if (imageModel !in apiPlatform.models) {
            _state.update { it.copy(errorMessage = "当前模型不属于所选 API 平台，请重新选择。") }
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
            apiPlatformId = apiPlatform.id,
            imageModel = imageModel,
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
            val previousTopHistoryId = _state.value.history.firstOrNull()?.id
            val appContext = getApplication<Application>()
            GenerationForegroundService.start(appContext)
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

            try {
                when (val result = runGenerationPipeline(request, previousTopHistoryId)) {
                    is GenerationPipelineResult.Success -> {
                        lastGenerationRequest = request
                        setActiveTab(AppTab.Result)
                        _state.update {
                            it.copy(
                                generationInProgress = false,
                                generationMessage = "",
                                currentResult = result.entry,
                                generationRetryAvailable = false,
                                statusMessage = if (result.recoveredFromHistory) {
                                    "生成已完成，结果已从历史恢复。"
                                } else {
                                    "生成成功。"
                                },
                                errorMessage = "",
                            )
                        }
                        if (result.recoveredFromHistory) {
                            GenerationNotificationManager.showSuccess(appContext, "图片已生成完成，结果已自动恢复。")
                        } else {
                            GenerationNotificationManager.showSuccess(appContext, "图片已生成完成。")
                            loadHistory(selectEntryId = result.entry.id)
                        }
                    }
                    is GenerationPipelineResult.Failure -> {
                        _state.update {
                            it.copy(
                                generationInProgress = false,
                                generationMessage = "",
                                generationRetryAvailable = true,
                                statusMessage = "",
                                errorMessage = result.message,
                            )
                        }
                        if (result.sessionExpired) {
                            handleSessionExpired(result.message)
                        }
                        GenerationNotificationManager.showFailure(appContext, result.message)
                    }
                }
            } finally {
                generationProgressJob?.cancel()
                generationProgressJob = null
                GenerationForegroundService.stop(appContext)
            }
        }
    }

    private suspend fun runGenerationPipeline(
        request: GenerationRequest,
        previousTopHistoryId: String?,
    ): GenerationPipelineResult {
        val generatedEntry = runCatching {
            api.generate(
                apiPlatformId = request.apiPlatformId,
                imageModel = request.imageModel,
                prompt = request.prompt,
                sourcePrompt = request.sourcePrompt,
                promptMode = request.promptMode.name.lowercase(),
                aspectRatio = request.aspectRatio,
                imageSize = request.imageSize,
                enableSearch = request.enableSearch,
                baseImage = request.baseImage,
                referenceImages = request.referenceImages,
            )
        }.getOrElse { error ->
            if (error is ApiHttpException && error.code == 401) {
                return GenerationPipelineResult.Failure(
                    message = "登录已过期，请重新登录。",
                    sessionExpired = true,
                )
            }

            val connectionAbort = error.isRecoverableConnectionAbort()
            val recoveredEntry = recoverGeneratedEntry(
                request = request,
                previousTopHistoryId = previousTopHistoryId,
                aggressive = connectionAbort,
            )
            if (recoveredEntry != null) {
                return GenerationPipelineResult.Success(
                    entry = recoveredEntry,
                    recoveredFromHistory = true,
                )
            }

            return GenerationPipelineResult.Failure(
                message = if (connectionAbort) {
                    "网络中断，暂未确认结果。可稍后在历史中查看，或点击重试。"
                } else {
                    error.message ?: "生成失败。"
                },
                sessionExpired = false,
            )
        }

        return GenerationPipelineResult.Success(
            entry = generatedEntry,
            recoveredFromHistory = false,
        )
    }

    private suspend fun recoverGeneratedEntry(
        request: GenerationRequest,
        previousTopHistoryId: String?,
        aggressive: Boolean,
    ): HistoryEntry? {
        val totalAttempts = if (aggressive) 12 else 4
        val intervalMs = if (aggressive) 1500L else 1200L
        repeat(totalAttempts) { attempt ->
            val items = runCatching { api.fetchHistory() }.getOrNull()
            if (items != null) {
                val recovered = findRecoveredEntry(items, request, previousTopHistoryId)
                if (recovered != null) {
                    _state.update {
                        val selection = it.historySelection.filterTo(mutableSetOf()) { selected ->
                            items.any { item -> item.id == selected }
                        }
                        it.copy(
                            history = items,
                            historySelection = selection,
                            currentResult = recovered,
                        )
                    }
                    return recovered
                }
            }
            if (attempt < totalAttempts - 1) {
                delay(intervalMs)
            }
        }
        return null
    }

    private fun findRecoveredEntry(
        items: List<HistoryEntry>,
        request: GenerationRequest,
        previousTopHistoryId: String?,
    ): HistoryEntry? {
        val strict = items.firstOrNull { entry -> entry.matchesGenerationRequestStrict(request) }
        if (strict != null) return strict
        val relaxed = items.firstOrNull { entry -> entry.matchesGenerationRequestRelaxed(request) }
        if (relaxed != null) return relaxed

        val newest = items.firstOrNull() ?: return null
        if (previousTopHistoryId != null && newest.id != previousTopHistoryId && newest.matchesGenerationSettings(request)) {
            return newest
        }
        return null
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
                apiPlatforms = emptyList(),
                apiPlatformsLoaded = false,
                selectedApiPlatformId = "",
                selectedImageModel = "",
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
            setActiveTab(AppTab.Generate)
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
        loadApiPlatforms()
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

    private fun HistoryEntry.matchesGenerationRequest(request: GenerationRequest): Boolean {
        val normalizedRequestPrompt = request.prompt.trim()
        val normalizedRequestSourcePrompt = request.sourcePrompt.trim()
        val normalizedEntryPrompt = prompt.trim()
        val normalizedEntrySourcePrompt = sourcePrompt.trim()
        val promptMatches = normalizedEntryPrompt == normalizedRequestPrompt
        val sourceMatches = normalizedRequestSourcePrompt.isNotBlank() && normalizedEntrySourcePrompt == normalizedRequestSourcePrompt
        val promptModeMatches = promptMode.equals(request.promptMode.name.lowercase(), ignoreCase = true)
        val aspectRatioMatches = request.aspectRatio.equals("auto", ignoreCase = true) || aspectRatio == request.aspectRatio
        val imageSizeMatches = imageSize == request.imageSize
        val searchMatches = enableSearch == request.enableSearch
        val referenceCountMatches = referenceCount == request.referenceImages.size
        val platformMatches = apiPlatformId.isBlank() || apiPlatformId == request.apiPlatformId
        val imageModelMatches = imageModel.isBlank() || imageModel == request.imageModel
        return (promptMatches || sourceMatches) &&
            promptModeMatches &&
            aspectRatioMatches &&
            imageSizeMatches &&
            searchMatches &&
            referenceCountMatches &&
            platformMatches &&
            imageModelMatches
    }

    private fun HistoryEntry.matchesGenerationRequestStrict(request: GenerationRequest): Boolean {
        if (!matchesGenerationRequest(request)) return false
        return baseImageName.normalizedFileName() == request.baseImage.name.normalizedFileName()
    }

    private fun HistoryEntry.matchesGenerationRequestRelaxed(request: GenerationRequest): Boolean {
        return matchesGenerationRequest(request)
    }

    private fun HistoryEntry.matchesGenerationSettings(request: GenerationRequest): Boolean {
        val promptModeMatches = promptMode.equals(request.promptMode.name.lowercase(), ignoreCase = true)
        val aspectRatioMatches = request.aspectRatio.equals("auto", ignoreCase = true) || aspectRatio == request.aspectRatio
        val imageSizeMatches = imageSize == request.imageSize
        val searchMatches = enableSearch == request.enableSearch
        val referenceCountMatches = referenceCount == request.referenceImages.size
        val platformMatches = apiPlatformId.isBlank() || apiPlatformId == request.apiPlatformId
        val imageModelMatches = imageModel.isBlank() || imageModel == request.imageModel
        return promptModeMatches &&
            aspectRatioMatches &&
            imageSizeMatches &&
            searchMatches &&
            referenceCountMatches &&
            platformMatches &&
            imageModelMatches
    }

    private fun applyApiPlatformSelection(
        platforms: List<ApiPlatformOption>,
        preferredPlatformId: String,
        preferredModel: String,
        fallbackPlatformId: String = "",
        fallbackModel: String = "",
    ) {
        val platform = resolveSelectedPlatform(platforms, preferredPlatformId, fallbackPlatformId)
        val selectedPlatformId = platform?.id.orEmpty()
        val selectedModel = resolveSelectedImageModel(platform, preferredModel, fallbackModel)
        persistApiPlatformSelection(selectedPlatformId, selectedModel)
        _state.update {
            it.copy(
                apiPlatforms = platforms,
                apiPlatformsLoaded = true,
                selectedApiPlatformId = selectedPlatformId,
                selectedImageModel = selectedModel,
                errorMessage = "",
            )
        }
    }

    private fun resolveSelectedPlatform(
        platforms: List<ApiPlatformOption>,
        preferredPlatformId: String,
        fallbackPlatformId: String = "",
    ): ApiPlatformOption? {
        return platforms.firstOrNull { it.id == preferredPlatformId } ?:
            platforms.firstOrNull { it.id == fallbackPlatformId } ?:
            platforms.firstOrNull()
    }

    private fun resolveSelectedImageModel(
        platform: ApiPlatformOption?,
        preferredModel: String,
        fallbackModel: String = "",
    ): String {
        if (platform == null) return ""
        return when {
            preferredModel.isNotBlank() && preferredModel in platform.models -> preferredModel
            fallbackModel.isNotBlank() && fallbackModel in platform.models -> fallbackModel
            platform.defaultModel.isNotBlank() && platform.defaultModel in platform.models -> platform.defaultModel
            else -> platform.models.firstOrNull().orEmpty()
        }
    }

    private fun persistApiPlatformSelection(platformId: String, imageModel: String) {
        prefs.selectedApiPlatformId = platformId
        prefs.selectedImageModel = imageModel
    }

    private fun Throwable.isRecoverableConnectionAbort(): Boolean {
        if (this !is IOException) return false
        val text = message.orEmpty().lowercase()
        if (text.contains("software caused connection abort")) return true
        if (text.contains("connection reset")) return true
        if (text.contains("broken pipe")) return true
        if (text.contains("timeout")) return true
        return false
    }

    private fun String.normalizedFileName(): String {
        return trim().lowercase()
            .substringAfterLast('/')
            .substringAfterLast('\\')
    }
}
