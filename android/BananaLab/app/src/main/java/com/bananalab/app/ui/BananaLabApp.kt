package com.bananalab.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import com.bananalab.app.AppTab
import com.bananalab.app.AppUiState
import com.bananalab.app.BananaLabViewModel
import com.bananalab.app.DownloadTarget
import com.bananalab.app.HistoryEntry
import com.bananalab.app.PersonaEditorState
import com.bananalab.app.PromptMode
import com.bananalab.app.SelectedImage
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun BananaLabApp(
    viewModel: BananaLabViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var imageViewerUrl by remember { mutableStateOf<String?>(null) }
    var promptLibraryDraft by remember { mutableStateOf(state.promptLibrary.content) }
    var promptLibraryLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(state.promptLibrary.content) {
        if (!promptLibraryLoaded || promptLibraryDraft.isBlank()) {
            promptLibraryDraft = state.promptLibrary.content
            promptLibraryLoaded = true
        }
    }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val message = when {
            state.errorMessage.isNotBlank() -> state.errorMessage
            state.statusMessage.isNotBlank() -> state.statusMessage
            else -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    val basePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setBaseImage(uri)
        }
    }

    val referencePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addReferenceImages(uris)
        }
    }

    val personaFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().decodeToString()
                    } ?: ""
                }.getOrDefault("")
                val name = runCatching { resolveDocumentName(context, uri) }.getOrDefault("persona.md")
                if (text.isNotBlank()) {
                    viewModel.importPersonaMd(text, name)
                }
            }
        }
    }

    val locked = state.passwordEnabled && !state.authenticated

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        ) {
            if (locked) {
                LoginScreen(
                    state = state,
                    onServerUrlChange = viewModel::setServerUrl,
                    onApplyServerUrl = viewModel::applyServerUrl,
                    onPasswordChange = viewModel::setLoginPassword,
                    onLogin = viewModel::login,
                )
            } else {
                AppShell(
                    state = state,
                    promptLibraryDraft = promptLibraryDraft,
                    onTabChange = viewModel::setActiveTab,
                    onBasePicker = {
                        basePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onReferencePicker = {
                        referencePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onPromptChange = viewModel::setPromptText,
                    onPromptModeChange = viewModel::setPromptMode,
                    onAspectRatioChange = viewModel::setAspectRatio,
                    onImageSizeChange = viewModel::setImageSize,
                    onEnableSearchChange = viewModel::setEnableSearch,
                    onGenerate = viewModel::generate,
                    onRetryGeneration = viewModel::retryGeneration,
                    onOptimizePrompt = viewModel::optimizePrompt,
                    onSavePromptLibrary = { viewModel.savePromptLibrary(promptLibraryDraft) },
                    onPromptLibraryDraftChange = { promptLibraryDraft = it },
                    onSelectPromptLibraryItem = viewModel::selectPromptLibraryItem,
                    onRefreshHistory = viewModel::loadHistory,
                    onDeleteHistory = viewModel::deleteHistory,
                    onToggleHistorySelection = viewModel::toggleHistorySelection,
                    onSelectAllHistory = viewModel::selectAllHistory,
                    onClearHistorySelection = viewModel::clearHistorySelection,
                    onDownloadSelectedHistory = viewModel::downloadSelectedHistory,
                    onDownloadHistoryItem = viewModel::downloadHistoryItem,
                    onSendHistoryToBase = viewModel::sendHistoryToBase,
                    onSendHistoryToReference = viewModel::sendHistoryToReference,
                    onDeletePersona = viewModel::deletePersona,
                    onNewPersona = viewModel::newPersona,
                    onSavePersona = viewModel::savePersona,
                    onPersonaSelect = viewModel::setPersonaFromList,
                    onPersonaEditorFilename = viewModel::updatePersonaEditorFilename,
                    onPersonaEditorName = viewModel::updatePersonaEditorName,
                    onPersonaEditorSummary = viewModel::updatePersonaEditorSummary,
                    onPersonaEditorContent = viewModel::updatePersonaEditorContent,
                    onPersonaImport = {
                        personaFilePicker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
                    },
                    onLogout = viewModel::logout,
                    onServerUrlChange = viewModel::setServerUrl,
                    onApplyServerUrl = viewModel::applyServerUrl,
                    onLoginPasswordChange = viewModel::setLoginPassword,
                    onLogin = viewModel::login,
                    onCopyText = { text -> copyToClipboard(clipboard, text) },
                    onImageViewer = { imageViewerUrl = it },
                    onClearBaseImage = viewModel::clearBaseImage,
                    onClearReferenceImages = viewModel::clearReferenceImages,
                    onInsertPrompt = viewModel::insertPrompt,
                    onUseCurrentPrompt = viewModel::useCurrentResultPrompt,
                    onSendCurrentResultToBase = viewModel::sendCurrentResultToBase,
                    onSendCurrentResultToReference = viewModel::sendCurrentResultToReference,
                    onDownloadCurrentResult = viewModel::downloadCurrentResult,
                    onRemoveReferenceImage = viewModel::removeReferenceImage,
                    onLoadRemoteBitmap = viewModel::loadRemoteBitmap,
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
            )

            imageViewerUrl?.let { url ->
                ImageViewerDialog(
                    url = url,
                    onDismiss = { imageViewerUrl = null },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    onServerUrlChange: (String) -> Unit,
    onApplyServerUrl: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "BananaLab",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "请输入服务器地址和访问密码后进入工作台。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器地址") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onApplyServerUrl) {
                        Text("连接服务器")
                    }
                    TextButton(onClick = onLogin) {
                        Text("使用当前密码登录")
                    }
                }
                OutlinedTextField(
                    value = state.loginPassword,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("访问密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loginInProgress,
                ) {
                    if (state.loginInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("进入 BananaLab")
                }
                if (state.errorMessage.isNotBlank()) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppShell(
    state: AppUiState,
    promptLibraryDraft: String,
    onTabChange: (AppTab) -> Unit,
    onBasePicker: () -> Unit,
    onReferencePicker: () -> Unit,
    onPromptChange: (String) -> Unit,
    onPromptModeChange: (PromptMode) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onImageSizeChange: (String) -> Unit,
    onEnableSearchChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onRetryGeneration: () -> Unit,
    onOptimizePrompt: () -> Unit,
    onSavePromptLibrary: () -> Unit,
    onPromptLibraryDraftChange: (String) -> Unit,
    onSelectPromptLibraryItem: (String) -> Unit,
    onRefreshHistory: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onToggleHistorySelection: (String) -> Unit,
    onSelectAllHistory: () -> Unit,
    onClearHistorySelection: () -> Unit,
    onDownloadSelectedHistory: () -> Unit,
    onDownloadHistoryItem: (HistoryEntry) -> Unit,
    onSendHistoryToBase: (HistoryEntry) -> Unit,
    onSendHistoryToReference: (HistoryEntry) -> Unit,
    onDeletePersona: () -> Unit,
    onNewPersona: () -> Unit,
    onSavePersona: () -> Unit,
    onPersonaSelect: (String) -> Unit,
    onPersonaEditorFilename: (String) -> Unit,
    onPersonaEditorName: (String) -> Unit,
    onPersonaEditorSummary: (String) -> Unit,
    onPersonaEditorContent: (String) -> Unit,
    onPersonaImport: () -> Unit,
    onLogout: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onApplyServerUrl: () -> Unit,
    onLoginPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onCopyText: (String) -> Unit,
    onImageViewer: (String) -> Unit,
    onClearBaseImage: () -> Unit,
    onClearReferenceImages: () -> Unit,
    onInsertPrompt: (String) -> Unit,
    onUseCurrentPrompt: () -> Unit,
    onSendCurrentResultToBase: () -> Unit,
    onSendCurrentResultToReference: () -> Unit,
    onDownloadCurrentResult: () -> Unit,
    onRemoveReferenceImage: (Int) -> Unit,
    onLoadRemoteBitmap: suspend (String) -> ImageBitmap,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BananaLab", fontWeight = FontWeight.Black)
                        Text(
                            text = state.serverUrl.removePrefix("http://").removePrefix("https://").trimEnd('/'),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    BadgeContainer(
                        label = if (state.authenticated) "已登录" else "未登录",
                        icon = if (state.authenticated) Icons.Default.CheckCircle else Icons.Default.Lock,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            if (state.activeTab == AppTab.Generate) {
                GenerationFab(
                    state = state,
                    onGenerate = onGenerate,
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 0.dp,
            ) {
                BottomNavItem(
                    selected = state.activeTab == AppTab.Generate,
                    label = "生成",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { onTabChange(AppTab.Generate) },
                )
                BottomNavItem(
                    selected = state.activeTab == AppTab.History,
                    label = "历史",
                    icon = Icons.Default.History,
                    onClick = { onTabChange(AppTab.History) },
                )
                BottomNavItem(
                    selected = state.activeTab == AppTab.Prompts,
                    label = "提示词",
                    icon = Icons.Default.TextSnippet,
                    onClick = { onTabChange(AppTab.Prompts) },
                )
                BottomNavItem(
                    selected = state.activeTab == AppTab.Settings,
                    label = "设置",
                    icon = Icons.Default.Settings,
                    onClick = { onTabChange(AppTab.Settings) },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.activeTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab-transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { tab ->
            when (tab) {
                AppTab.Generate -> GenerateScreen(
                    state = state,
                    onBasePicker = onBasePicker,
                    onReferencePicker = onReferencePicker,
                    onPromptChange = onPromptChange,
                    onPromptModeChange = onPromptModeChange,
                    onAspectRatioChange = onAspectRatioChange,
                    onImageSizeChange = onImageSizeChange,
                    onEnableSearchChange = onEnableSearchChange,
                    onGenerate = onGenerate,
                    onRetryGeneration = onRetryGeneration,
                    onOptimizePrompt = onOptimizePrompt,
                    onPersonaSelect = onPersonaSelect,
                    onSelectPromptLibraryItem = onSelectPromptLibraryItem,
                    onClearBaseImage = onClearBaseImage,
                    onClearReferenceImages = onClearReferenceImages,
                    onRemoveReferenceImage = onRemoveReferenceImage,
                    onCopyText = onCopyText,
                    onImageViewer = onImageViewer,
                    onInsertPrompt = onInsertPrompt,
                    onUseCurrentPrompt = onUseCurrentPrompt,
                    onSendCurrentResultToBase = onSendCurrentResultToBase,
                    onSendCurrentResultToReference = onSendCurrentResultToReference,
                    onDownloadCurrentResult = onDownloadCurrentResult,
                    onLoadRemoteBitmap = onLoadRemoteBitmap,
                )
                AppTab.History -> HistoryScreen(
                    state = state,
                    onRefresh = onRefreshHistory,
                    onToggleSelection = onToggleHistorySelection,
                    onSelectAll = onSelectAllHistory,
                    onClearSelection = onClearHistorySelection,
                    onDownloadSelected = onDownloadSelectedHistory,
                    onDownloadItem = onDownloadHistoryItem,
                    onDeleteItem = onDeleteHistory,
                    onCopyText = onCopyText,
                    onSendToBase = onSendHistoryToBase,
                    onSendToReference = onSendHistoryToReference,
                    onImageViewer = onImageViewer,
                    onLoadRemoteBitmap = onLoadRemoteBitmap,
                )
                AppTab.Prompts -> PromptToolsScreen(
                    state = state,
                    promptLibraryDraft = promptLibraryDraft,
                    onPromptLibraryDraftChange = onPromptLibraryDraftChange,
                    onSavePromptLibrary = onSavePromptLibrary,
                    onPersonaSelect = onPersonaSelect,
                    onPersonaEditorFilename = onPersonaEditorFilename,
                    onPersonaEditorName = onPersonaEditorName,
                    onPersonaEditorSummary = onPersonaEditorSummary,
                    onPersonaEditorContent = onPersonaEditorContent,
                    onNewPersona = onNewPersona,
                    onSavePersona = onSavePersona,
                    onDeletePersona = onDeletePersona,
                    onPersonaImport = onPersonaImport,
                )
                AppTab.Settings -> SettingsScreen(
                    state = state,
                    onServerUrlChange = onServerUrlChange,
                    onApplyServerUrl = onApplyServerUrl,
                    onLoginPasswordChange = onLoginPasswordChange,
                    onLogin = onLogin,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
private fun GenerateScreen(
    state: AppUiState,
    onBasePicker: () -> Unit,
    onReferencePicker: () -> Unit,
    onPromptChange: (String) -> Unit,
    onPromptModeChange: (PromptMode) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onImageSizeChange: (String) -> Unit,
    onEnableSearchChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onRetryGeneration: () -> Unit,
    onOptimizePrompt: () -> Unit,
    onPersonaSelect: (String) -> Unit,
    onSelectPromptLibraryItem: (String) -> Unit,
    onClearBaseImage: () -> Unit,
    onClearReferenceImages: () -> Unit,
    onRemoveReferenceImage: (Int) -> Unit,
    onCopyText: (String) -> Unit,
    onImageViewer: (String) -> Unit,
    onInsertPrompt: (String) -> Unit,
    onUseCurrentPrompt: () -> Unit,
    onSendCurrentResultToBase: () -> Unit,
    onSendCurrentResultToReference: () -> Unit,
    onDownloadCurrentResult: () -> Unit,
    onLoadRemoteBitmap: suspend (String) -> ImageBitmap,
) {
    val basePromptExpanded = rememberSaveable { mutableStateOf(false) }
    val imageSettingsExpanded = rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "基础结构图",
                subtitle = "必填，决定主体构图与空间关系。",
                titleStyle = MaterialTheme.typography.titleSmall,
                subtitleStyle = MaterialTheme.typography.bodySmall,
            ) {
                BaseImagePanel(
                    image = state.baseImage,
                    onAdd = onBasePicker,
                    onRemove = onClearBaseImage,
                )
            }
        }
        item {
            SectionCard(
                title = "提示词编辑器",
                subtitle = "把这里当作编辑器，不是表单。",
                titleStyle = MaterialTheme.typography.titleSmall,
                subtitleStyle = MaterialTheme.typography.bodySmall,
            ) {
                PromptEditor(
                    prompt = state.promptText,
                    onPromptChange = onPromptChange,
                    onClear = { onPromptChange("") },
                )
                Spacer(Modifier.height(8.dp))
                GenerationInlineState(
                    state = state,
                    onRetryGeneration = onRetryGeneration,
                )
            }
        }
        item {
            SectionCard(
                title = "风格参考",
                subtitle = "横向滑动查看，单张占位更紧凑。",
                titleStyle = MaterialTheme.typography.titleSmall,
                subtitleStyle = MaterialTheme.typography.bodySmall,
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ReferenceAddCard(onClick = onReferencePicker)
                    }
                    if (state.referenceImages.isEmpty()) {
                        item {
                            EmptyHint(
                                text = "还没有参考图。",
                                modifier = Modifier.size(width = 180.dp, height = 92.dp),
                                fillWidth = false,
                            )
                        }
                    } else {
                        items(state.referenceImages.size) { index ->
                            ReferenceThumbCard(
                                image = state.referenceImages[index],
                                index = index,
                                onRemove = { onRemoveReferenceImage(index) },
                            )
                        }
                    }
                }
                if (state.referenceImages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CompactActionRow {
                        CompactTextButton(
                            text = "添加",
                            onClick = onReferencePicker,
                            icon = Icons.Default.Add,
                        )
                        CompactTextButton(
                            text = "清空",
                            onClick = onClearReferenceImages,
                        )
                    }
                }
            }
        }
        item {
            CollapsibleWorkspaceSection(
                title = "基础提示词",
                subtitle = if (state.promptMode == PromptMode.Optimized) {
                    "已开启 AI 翻译优化。"
                } else {
                    "默认折叠，放置次级提示词和优化工具。"
                },
                expanded = basePromptExpanded.value,
                onToggle = { basePromptExpanded.value = !basePromptExpanded.value },
            ) {
                PromptModeToggle(
                    selected = state.promptMode,
                    onSelected = onPromptModeChange,
                )
                if (state.promptLibrary.items.isNotEmpty()) {
                    Text(
                        text = "快速插入",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScrollChipRow {
                        state.promptLibrary.items.forEach { item ->
                            AssistChip(
                                onClick = { onSelectPromptLibraryItem(item) },
                                label = { Text(item, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.Input, contentDescription = null) },
                            )
                        }
                    }
                }
                if (state.promptMode == PromptMode.Optimized) {
                    PersonaOptimizerPanel(
                        state = state,
                        onOptimizePrompt = onOptimizePrompt,
                        onInsertPrompt = onInsertPrompt,
                        onPersonaSelect = onPersonaSelect,
                    )
                }
            }
        }
        item {
            CollapsibleWorkspaceSection(
                title = "图片设置",
                subtitle = "低优先级，默认收起。",
                expanded = imageSettingsExpanded.value,
                onToggle = { imageSettingsExpanded.value = !imageSettingsExpanded.value },
            ) {
                Text(
                    text = "图片比例",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScrollChipRow {
                    AspectRatioOptions().forEach { option ->
                        FilterChip(
                            selected = state.aspectRatio == option.value,
                            onClick = { onAspectRatioChange(option.value) },
                            modifier = Modifier.heightIn(min = 32.dp),
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Text(
                    text = "生成大小",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow {
                    listOf("1K", "2K", "4K").forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = state.imageSize == size,
                            onClick = { onImageSizeChange(size) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Text(size, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "网络搜索增强",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        )
                        Text(
                            text = "仅在需要补充公开信息时启用。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        )
                    }
                    Switch(
                        checked = state.enableSearch,
                        onCheckedChange = onEnableSearchChange,
                    )
                }
            }
        }
        item {
            ResultCard(
                entry = state.currentResult,
                onCopy = onCopyText,
                onView = onImageViewer,
                onDownload = onDownloadCurrentResult,
                onSendToBase = onSendCurrentResultToBase,
                onSendToReference = onSendCurrentResultToReference,
                onLoadRemoteBitmap = onLoadRemoteBitmap,
            )
        }
    }
}

@Composable
private fun BaseImagePanel(
    image: SelectedImage?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    if (image == null) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clickable(onClick = onAdd),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("选择基础图", fontWeight = FontWeight.SemiBold)
                    Text(
                        "从相册选择主约束图",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedCard(
                modifier = Modifier
                    .size(104.dp)
                    .clickable(onClick = onAdd),
                shape = RoundedCornerShape(12.dp),
            ) {
                androidx.compose.foundation.Image(
                    bitmap = image.preview,
                    contentDescription = image.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(image.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = "${image.bitmapWidth} × ${image.bitmapHeight} · ${if (image.compressed) "已压缩" else "原始"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactActionRow {
                    CompactTextButton(text = "更换", onClick = onAdd)
                    CompactTextButton(text = "移除", onClick = onRemove)
                }
            }
        }
    }
}

@Composable
private fun GenerationInlineState(
    state: AppUiState,
    onRetryGeneration: () -> Unit,
) {
    GenerationStatusBanner(
        state = state,
        onRetryGeneration = onRetryGeneration,
    )
}

@Composable
private fun GenerationFab(
    state: AppUiState,
    onGenerate: () -> Unit,
) {
    val ready = generationReady(state)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.95f else 1f, label = "generate-fab-scale")

    ExtendedFloatingActionButton(
        text = {
            Text(
                text = when {
                    state.generationInProgress -> "生成中"
                    ready -> "生成"
                    else -> "缺少条件"
                },
            )
        },
        icon = {
            if (state.generationInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
            }
        },
        onClick = { if (ready) onGenerate() },
        modifier = Modifier.scale(scale),
        interactionSource = interactionSource,
        expanded = true,
        containerColor = when {
            state.generationInProgress -> MaterialTheme.colorScheme.primary
            ready -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            state.generationInProgress -> MaterialTheme.colorScheme.onPrimary
            ready -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun GenerationStatusBanner(
    state: AppUiState,
    onRetryGeneration: () -> Unit,
) {
    when {
        state.generationInProgress -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "正在生成",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = state.generationMessage.ifBlank { "请稍候。" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "运行中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        state.generationRetryAvailable && state.errorMessage.isNotBlank() -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "生成失败",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        )
                    }
                    CompactTextButton(
                        text = "重试",
                        onClick = onRetryGeneration,
                        enabled = true,
                    )
                }
            }
        }
        state.currentResult != null -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最新结果已就位",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = state.statusMessage.ifBlank { "可以继续编辑提示词或下载图片。" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptEditor(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 142.dp),
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 6,
                maxLines = 10,
                decorationBox = { innerTextField ->
                    Box {
                        if (prompt.isBlank()) {
                            Text(
                                text = "输入主体、风格、镜头、光线、材质和约束…",
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (prompt.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "清空提示词")
                }
            }
        }
    }
}

@Composable
private fun CollapsibleWorkspaceSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.animateContentSize(animationSpec = tween(150)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactActionRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CompactTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReferenceAddCard(
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .size(92.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun generationReady(state: AppUiState): Boolean {
    if (state.generationInProgress) return false
    if (state.baseImage == null) return false
    return when (state.promptMode) {
        PromptMode.Default -> state.promptText.isNotBlank()
        PromptMode.Optimized -> state.optimizedPrompt.isNotBlank() || state.promptText.isNotBlank()
    }
}

@Composable
private fun HistoryScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDownloadItem: (HistoryEntry) -> Unit,
    onDeleteItem: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onSendToBase: (HistoryEntry) -> Unit,
    onSendToReference: (HistoryEntry) -> Unit,
    onImageViewer: (String) -> Unit,
    onLoadRemoteBitmap: suspend (String) -> ImageBitmap,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "最近历史", subtitle = "可批量选择、下载、复制提示词，也能回流到基础图或参考图。") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("刷新")
                    }
                    OutlinedButton(onClick = onSelectAll) { Text("全选") }
                    OutlinedButton(onClick = onClearSelection) { Text("清空") }
                    Button(
                        onClick = onDownloadSelected,
                        enabled = state.historySelection.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("批量下载")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "已选择 ${state.historySelection.size} / ${state.history.size} 张",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.history.isEmpty()) {
            item {
                EmptyHint("还没有历史记录，先去生成一张吧。")
            }
        } else {
            items(state.history, key = { it.id }) { entry ->
                HistoryCard(
                    entry = entry,
                    selected = state.historySelection.contains(entry.id),
                    onToggleSelection = { onToggleSelection(entry.id) },
                    onDownload = { onDownloadItem(entry) },
                    onCopy = { onCopyText(entry.prompt) },
                    onSendToBase = { onSendToBase(entry) },
                    onSendToReference = { onSendToReference(entry) },
                    onDelete = { onDeleteItem(entry.id) },
                    onView = { onImageViewer(preferredImageUrl(entry)) },
                    onLoadRemoteBitmap = onLoadRemoteBitmap,
                )
            }
        }
    }
}

@Composable
private fun PromptToolsScreen(
    state: AppUiState,
    promptLibraryDraft: String,
    onPromptLibraryDraftChange: (String) -> Unit,
    onSavePromptLibrary: () -> Unit,
    onPersonaSelect: (String) -> Unit,
    onPersonaEditorFilename: (String) -> Unit,
    onPersonaEditorName: (String) -> Unit,
    onPersonaEditorSummary: (String) -> Unit,
    onPersonaEditorContent: (String) -> Unit,
    onNewPersona: () -> Unit,
    onSavePersona: () -> Unit,
    onDeletePersona: () -> Unit,
    onPersonaImport: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "提示词库", subtitle = "一行一个条目，保存后会同步到服务器。") {
                OutlinedTextField(
                    value = promptLibraryDraft,
                    onValueChange = onPromptLibraryDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 20,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSavePromptLibrary) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("保存提示词库")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.promptLibrary.items.isNotEmpty()) {
                    ScrollChipRow {
                        state.promptLibrary.items.forEach { item ->
                            AssistChip(onClick = {}, label = { Text(item) })
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "人设管理", subtitle = "导入、编辑、保存和删除提示词优化人格。") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onNewPersona) {
                        Text("新建人格")
                    }
                    OutlinedButton(onClick = onPersonaImport) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导入 .md")
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (state.personas.isNotEmpty()) {
                    Text("可用人设", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.personas, key = { it.id }) { persona ->
                            OutlinedCard(
                                modifier = Modifier.widthIn(min = 180.dp).clickable {
                                    onPersonaSelect(persona.id)
                                },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (persona.id == state.selectedPersonaId) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(persona.name, fontWeight = FontWeight.SemiBold)
                                    Text(persona.summary, style = MaterialTheme.typography.bodySmall)
                                    Text(persona.filename, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                PersonaEditor(
                    editor = state.personaEditor,
                    onFilenameChange = onPersonaEditorFilename,
                    onNameChange = onPersonaEditorName,
                    onSummaryChange = onPersonaEditorSummary,
                    onContentChange = onPersonaEditorContent,
                    onSave = onSavePersona,
                    onDelete = onDeletePersona,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppUiState,
    onServerUrlChange: (String) -> Unit,
    onApplyServerUrl: () -> Unit,
    onLoginPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "服务器", subtitle = "可随时切换到新的后端地址。") {
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器地址") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onApplyServerUrl) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("保存并重连")
                }
            }
        }
        item {
            SectionCard(title = "登录", subtitle = "与服务器使用同一个密码。") {
                if (state.authenticated) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前已登录", fontWeight = FontWeight.SemiBold)
                            Text("你可以继续使用全部功能。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = onLogout) {
                            Text("退出登录")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = state.loginPassword,
                        onValueChange = onLoginPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("访问密码") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onLogin) {
                        Text("登录")
                    }
                }
            }
        }
        item {
            SectionCard(title = "关于", subtitle = "BananaLab 原生 Android 版。") {
                Text("功能覆盖生成、历史、提示词库、人设管理、提示词优化和批量下载。")
                Spacer(Modifier.height(8.dp))
                Text("如果你想，我后面还可以继续做图标、启动页和更强的本地缓存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = titleStyle, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = subtitleStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun ImageSlot(
    image: SelectedImage?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    emptyTitle: String,
    emptySubtitle: String,
) {
    if (image == null) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable(onClick = onAdd),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(emptyTitle, fontWeight = FontWeight.SemiBold)
                    Text(emptySubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ImagePreviewCard(
                bitmap = image.preview,
                title = image.name,
                subtitle = buildString {
                    append("${image.bitmapWidth} × ${image.bitmapHeight}")
                    append(" · ")
                    append(if (image.compressed) "已压缩" else "原始")
                },
                onClick = onAdd,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAdd) {
                    Text("更换")
                }
                OutlinedButton(onClick = onRemove) {
                    Text("移除")
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewCard(
    bitmap: ImageBitmap,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReferenceThumbCard(
    image: SelectedImage,
    index: Int,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .size(92.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Box {
            androidx.compose.foundation.Image(
                bitmap = image.preview,
                contentDescription = image.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("参 ${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.White)
            }
        }
    }
}

@Composable
private fun PersonaOptimizerPanel(
    state: AppUiState,
    onOptimizePrompt: () -> Unit,
    onInsertPrompt: (String) -> Unit,
    onPersonaSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "提示词优化人设",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
        )
        ScrollChipRow {
            state.personas.forEach { persona ->
                FilterChip(
                    selected = persona.id == state.selectedPersonaId,
                    onClick = { onPersonaSelect(persona.id) },
                    modifier = Modifier.heightIn(min = 32.dp),
                    label = { Text(persona.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        if (state.personaEditor.summary.isNotBlank()) {
            Text(
                text = state.personaEditor.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        CompactTextButton(
            text = "提示词优化",
            onClick = onOptimizePrompt,
            enabled = !state.optimizingPrompt,
        )
        if (state.optimizeMessage.isNotBlank()) {
            Text(
                text = state.optimizeMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (state.optimizedPrompt.isNotBlank()) {
            OutlinedTextField(
                value = state.optimizedPrompt,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("优化后的英文提示词") },
                minLines = 3,
                maxLines = 6,
                readOnly = true,
            )
            CompactActionRow {
                CompactTextButton(text = "插入", onClick = { onInsertPrompt(state.optimizedPrompt) })
            }
        }
    }
}

@Composable
private fun PromptModeToggle(
    selected: PromptMode,
    onSelected: (PromptMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = selected == PromptMode.Default,
            onClick = { onSelected(PromptMode.Default) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            modifier = Modifier.heightIn(min = 32.dp),
        ) {
            Text("直接输入", style = MaterialTheme.typography.labelSmall)
        }
        SegmentedButton(
            selected = selected == PromptMode.Optimized,
            onClick = { onSelected(PromptMode.Optimized) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            modifier = Modifier.heightIn(min = 32.dp),
        ) {
            Text("AI 翻译优化", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onSendToBase: () -> Unit,
    onSendToReference: () -> Unit,
    onDelete: () -> Unit,
    onView: () -> Unit,
    onLoadRemoteBitmap: suspend (String) -> ImageBitmap,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.createdAt, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${entry.aspectRatio} · ${entry.imageSize} · ${if (entry.promptMode == "optimized") "AI 翻译优化" else "直接输入"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (entry.ossUploadError?.isNotBlank() == true) {
                    Badge { Text("OSS") }
                }
            }
            RemoteThumb(
                url = preferredThumbUrl(entry).ifBlank { preferredImageUrl(entry) },
                contentDescription = entry.prompt,
                onClick = onView,
                onLongClick = onDownload,
                height = 96.dp,
            )
            Text(
                text = entry.prompt.ifBlank { "未填写提示词" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            FlowButtons {
                CompactTextButton(text = "查看", onClick = onView)
                CompactTextButton(text = "下载", onClick = onDownload, icon = Icons.Default.Download)
                CompactTextButton(text = "复制", onClick = onCopy, icon = Icons.Default.ContentCopy)
                CompactTextButton(text = "送基础图", onClick = onSendToBase)
                CompactTextButton(text = "送参考图", onClick = onSendToReference)
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("删除", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    entry: HistoryEntry?,
    onCopy: (String) -> Unit,
    onView: (String) -> Unit,
    onDownload: () -> Unit,
    onSendToBase: () -> Unit,
    onSendToReference: () -> Unit,
    onLoadRemoteBitmap: suspend (String) -> ImageBitmap,
) {
    SectionCard(title = "当前结果", subtitle = "生成完成后显示在这里。") {
        if (entry == null) {
            EmptyHint("还没有结果。")
        } else {
            RemoteThumb(
                url = preferredImageUrl(entry),
                contentDescription = entry.prompt,
                onClick = { onView(preferredImageUrl(entry)) },
                onLongClick = onDownload,
                height = 280.dp,
            )
            Text(entry.prompt.ifBlank { "未填写提示词" }, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            CompactActionRow {
                CompactTextButton(text = "复制", onClick = { onCopy(entry.prompt) }, icon = Icons.Default.ContentCopy)
                CompactTextButton(text = "下载", onClick = onDownload, icon = Icons.Default.Download)
                CompactTextButton(text = "送基础图", onClick = onSendToBase)
                CompactTextButton(text = "送参考图", onClick = onSendToReference)
            }
        }
    }
}

@Composable
private fun PersonaEditor(
    editor: PersonaEditorState,
    onFilenameChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = editor.filename,
            onValueChange = onFilenameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("文件名") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("人格名称") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.summary,
            onValueChange = onSummaryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("一句简介") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.content,
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("人格正文 / system prompt") },
            minLines = 6,
            maxLines = 16,
        )
        CompactActionRow {
            CompactTextButton(text = "保存", onClick = onSave)
            CompactTextButton(text = "删除", onClick = onDelete, enabled = editor.id.isNotBlank())
        }
    }
}

@Composable
private fun RemoteThumb(
    url: String,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    height: androidx.compose.ui.unit.Dp = 220.dp,
) {
    val context = LocalContext.current
    val loaded = remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (loaded.value) 1f else 0f, label = "remote-thumb-alpha")
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .build()
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            },
            success = {
                LaunchedEffect(url) {
                    loaded.value = true
                }
                SubcomposeAsyncImageContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = alpha),
                )
            },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun RemoteImageDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val loaded = remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (loaded.value) 1f else 0f, label = "remote-dialog-alpha")
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .build()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("图片预览") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = { CircularProgressIndicator(modifier = Modifier.size(20.dp)) },
                    success = {
                        LaunchedEffect(url) {
                            loaded.value = true
                        }
                        SubcomposeAsyncImageContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = alpha),
                        )
                    },
                    error = {
                        Text("加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        },
    )
}

@Composable
private fun ImageViewerDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    RemoteImageDialog(url = url, onDismiss = onDismiss)
}

@Composable
private fun RowScope.BottomNavItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BadgeContainer(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun ScrollChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun FlowButtons(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    OutlinedCard(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class RatioOption(val label: String, val value: String)

private fun AspectRatioOptions(): List<RatioOption> = listOf(
    RatioOption("自动", "auto"),
    RatioOption("1:1", "1:1"),
    RatioOption("4:3", "4:3"),
    RatioOption("3:4", "3:4"),
    RatioOption("16:9", "16:9"),
    RatioOption("9:16", "9:16"),
    RatioOption("5:4", "5:4"),
    RatioOption("4:5", "4:5"),
)

private fun preferredImageUrl(entry: HistoryEntry): String = (entry.ossImageUrl ?: entry.imageUrl).trim()

private fun preferredThumbUrl(entry: HistoryEntry): String = (entry.ossThumbUrl ?: entry.thumbUrl).trim()

private fun copyToClipboard(clipboard: androidx.compose.ui.platform.ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
}

private fun resolveDocumentName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else "persona.md"
    } ?: "persona.md"
}
