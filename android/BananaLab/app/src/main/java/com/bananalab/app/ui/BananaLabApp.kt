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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalConfiguration
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

private object UiTokens {
    val s1 = 8.dp
    val s2 = 16.dp
    val s3 = 24.dp
    val r12 = RoundedCornerShape(12.dp)
    val r16 = RoundedCornerShape(16.dp)
    val r20 = RoundedCornerShape(20.dp)
}

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
                            MaterialTheme.colorScheme.surface,
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
                    onApiPlatformChange = viewModel::setSelectedApiPlatform,
                    onImageModelChange = viewModel::setSelectedImageModel,
                    onRefreshApiPlatforms = viewModel::loadApiPlatforms,
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
                    .padding(start = UiTokens.s2, end = UiTokens.s2, bottom = 88.dp),
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
            .padding(UiTokens.s3),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
            shape = UiTokens.r20,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(UiTokens.s3),
                verticalArrangement = Arrangement.spacedBy(UiTokens.s2),
            ) {
                Text(
                    text = "BananaLab",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "请输入服务器地址和访问密码后进入工作台。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器地址") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    singleLine = true,
                    shape = UiTokens.r12,
                    colors = appTextFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
                    FilledTonalButton(onClick = onApplyServerUrl) {
                        Text("连接服务器")
                    }
                    TextButton(onClick = onLogin) {
                        Text("使用当前密码登录")
                    }
                }
                TextField(
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
                    shape = UiTokens.r12,
                    colors = appTextFieldColors(),
                )
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loginInProgress,
                    shape = UiTokens.r16,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    if (state.loginInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(UiTokens.s1))
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
    onApiPlatformChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onRefreshApiPlatforms: () -> Unit,
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
                        Text("BananaLab", style = MaterialTheme.typography.titleMedium)
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                tonalElevation = 0.dp,
            ) {
                BottomNavItem(selected = state.activeTab == AppTab.Generate, label = "生成", icon = Icons.Default.AutoAwesome, onClick = { onTabChange(AppTab.Generate) })
                BottomNavItem(selected = state.activeTab == AppTab.Result, label = "结果", icon = Icons.Default.Image, onClick = { onTabChange(AppTab.Result) })
                BottomNavItem(selected = state.activeTab == AppTab.History, label = "历史", icon = Icons.Default.History, onClick = { onTabChange(AppTab.History) })
                BottomNavItem(selected = state.activeTab == AppTab.Settings, label = "设置", icon = Icons.Default.Settings, onClick = { onTabChange(AppTab.Settings) })
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
                    onApiPlatformChange = onApiPlatformChange,
                    onImageModelChange = onImageModelChange,
                    onRefreshApiPlatforms = onRefreshApiPlatforms,
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
                AppTab.Result -> ResultScreen(
                    state = state,
                    onRetryGeneration = onRetryGeneration,
                    onCopyText = onCopyText,
                    onImageViewer = onImageViewer,
                    onUseCurrentPrompt = onUseCurrentPrompt,
                    onSendCurrentResultToBase = onSendCurrentResultToBase,
                    onSendCurrentResultToReference = onSendCurrentResultToReference,
                    onDownloadCurrentResult = onDownloadCurrentResult,
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
                AppTab.Settings -> SettingsScreen(
                    state = state,
                    onServerUrlChange = onServerUrlChange,
                    onApplyServerUrl = onApplyServerUrl,
                    onLoginPasswordChange = onLoginPasswordChange,
                    onLogin = onLogin,
                    onLogout = onLogout,
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
    onApiPlatformChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onRefreshApiPlatforms: () -> Unit,
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
        contentPadding = PaddingValues(start = UiTokens.s2, top = UiTokens.s2, end = UiTokens.s2, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(UiTokens.s2),
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
                subtitle = "和其他模块保持同宽，图片可横向快速补充。",
                titleStyle = MaterialTheme.typography.titleSmall,
                subtitleStyle = MaterialTheme.typography.bodySmall,
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            ReferenceAddCard(onClick = onReferencePicker)
                        }
                        if (state.referenceImages.isEmpty()) {
                            item {
                                EmptyHint(
                                    text = "还没有参考图。",
                                    modifier = Modifier.size(width = maxWidth - 8.dp, height = 92.dp),
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
                ApiPlatformSelectorPanel(
                    state = state,
                    onApiPlatformChange = onApiPlatformChange,
                    onImageModelChange = onImageModelChange,
                    onRefreshApiPlatforms = onRefreshApiPlatforms,
                )
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
    }
}

@Composable
private fun ApiPlatformSelectorPanel(
    state: AppUiState,
    onApiPlatformChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onRefreshApiPlatforms: () -> Unit,
) {
    val selectedPlatform = state.apiPlatforms.firstOrNull { it.id == state.selectedApiPlatformId }

    Text(
        text = "API 平台",
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CompactActionRow {
        CompactTextButton(
            text = "刷新平台",
            onClick = onRefreshApiPlatforms,
            icon = Icons.Default.Refresh,
        )
    }

    when {
        !state.apiPlatformsLoaded -> {
            Text(
                text = "正在读取服务器平台配置...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        state.apiPlatforms.isEmpty() -> {
            EmptyHint("当前服务器还没有可用的 API 平台配置，请先检查 `data/api-platforms.xml`。")
        }

        else -> {
            ScrollChipRow {
                state.apiPlatforms.forEach { platform ->
                    FilterChip(
                        selected = platform.id == state.selectedApiPlatformId,
                        onClick = { onApiPlatformChange(platform.id) },
                        modifier = Modifier.heightIn(min = 32.dp),
                        label = { Text(platform.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Text(
                text = buildString {
                    append(selectedPlatform?.name ?: "未选择平台")
                    if (selectedPlatform != null) {
                        append(" · ${selectedPlatform.models.size} 个可选模型")
                    }
                    append(" · 自动绑定对应接口地址与密钥")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            )
        }
    }

    Text(
        text = "生成模型",
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (selectedPlatform == null) {
        Text(
            text = "请先选择一个 API 平台。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    } else {
        ScrollChipRow {
            selectedPlatform.models.forEach { model ->
                FilterChip(
                    selected = model == state.selectedImageModel,
                    onClick = { onImageModelChange(model) },
                    modifier = Modifier.heightIn(min = 32.dp),
                    label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Text(
            text = if (state.selectedImageModel.isBlank()) {
                "当前平台还没有可用模型。"
            } else {
                "当前模型：${state.selectedImageModel}"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        )
    }
}

@Composable
private fun ResultScreen(
    state: AppUiState,
    onRetryGeneration: () -> Unit,
    onCopyText: (String) -> Unit,
    onImageViewer: (String) -> Unit,
    onUseCurrentPrompt: () -> Unit,
    onSendCurrentResultToBase: () -> Unit,
    onSendCurrentResultToReference: () -> Unit,
    onDownloadCurrentResult: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = UiTokens.s2, top = UiTokens.s2, end = UiTokens.s2, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(UiTokens.s2),
    ) {
        item {
            GenerationStatusBanner(
                state = state,
                onRetryGeneration = onRetryGeneration,
            )
        }
        item {
            ResultCard(
                entry = state.currentResult,
                onCopy = onCopyText,
                onView = onImageViewer,
                onUsePrompt = onUseCurrentPrompt,
                onDownload = onDownloadCurrentResult,
                onSendToBase = onSendCurrentResultToBase,
                onSendToReference = onSendCurrentResultToReference,
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clickable(onClick = onAdd),
            shape = UiTokens.r12,
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
            Card(
                modifier = Modifier
                    .size(104.dp)
                    .clickable(onClick = onAdd),
                shape = UiTokens.r12,
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
                    state.apiPlatforms.none { it.id == state.selectedApiPlatformId } || state.selectedImageModel.isBlank() -> "先选平台"
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
                shape = UiTokens.r12,
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
                shape = UiTokens.r12,
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
                shape = UiTokens.r12,
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
        shape = UiTokens.r16,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Row(
            modifier = Modifier.padding(UiTokens.s2),
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
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(150)),
        shape = UiTokens.r16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(UiTokens.s2), verticalArrangement = Arrangement.spacedBy(UiTokens.s1)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(UiTokens.s1)) {
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
        modifier = Modifier.horizontalScroll(rememberScrollState()),
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
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = UiTokens.s2, vertical = UiTokens.s1),
        modifier = Modifier.heightIn(min = 40.dp),
        shape = UiTokens.r12,
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
    Card(
        modifier = Modifier
            .size(92.dp)
            .clickable(onClick = onClick),
        shape = UiTokens.r12,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
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
    if (state.apiPlatforms.none { it.id == state.selectedApiPlatformId }) return false
    if (state.selectedApiPlatformId.isBlank() || state.selectedImageModel.isBlank()) return false
    return when (state.promptMode) {
        PromptMode.Default -> state.promptText.isNotBlank()
        PromptMode.Optimized -> state.optimizedPrompt.isNotBlank() || state.promptText.isNotBlank()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val columns = historyGridColumns(LocalConfiguration.current.screenWidthDp)
    var actionEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = UiTokens.s2, top = UiTokens.s2, end = UiTokens.s2, bottom = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(UiTokens.s2),
        ) {
            item(span = { GridItemSpan(columns) }) {
                SectionCard(title = "历史相册", subtitle = "按相册方式查看结果；点图查看，长按或勾选可批量处理。") {
                    ScrollChipRow {
                        FilledTonalButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("刷新")
                        }
                        FilledTonalButton(onClick = onSelectAll) { Text("全选") }
                        FilledTonalButton(onClick = onClearSelection) { Text("清空") }
                        Button(
                            onClick = onDownloadSelected,
                            enabled = state.historySelection.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("批量下载")
                        }
                    }
                    Text(
                        text = "已选择 ${state.historySelection.size} / ${state.history.size} 张",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.history.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    EmptyHint("还没有历史记录，先去生成一张吧。")
                }
            } else {
                items(state.history, key = { it.id }) { entry ->
                    HistoryAlbumCard(
                        entry = entry,
                        selected = state.historySelection.contains(entry.id),
                        onToggleSelection = { onToggleSelection(entry.id) },
                        onView = { onImageViewer(preferredImageUrl(entry)) },
                        onMore = { actionEntry = entry },
                    )
                }
            }
        }

        actionEntry?.let { entry ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { actionEntry = null },
                sheetState = sheetState,
            ) {
                HistoryActionSheet(
                    entry = entry,
                    onView = {
                        onImageViewer(preferredImageUrl(entry))
                        actionEntry = null
                    },
                    onDownload = {
                        onDownloadItem(entry)
                        actionEntry = null
                    },
                    onCopy = {
                        onCopyText(entry.prompt)
                        actionEntry = null
                    },
                    onSendToBase = {
                        onSendToBase(entry)
                        actionEntry = null
                    },
                    onSendToReference = {
                        onSendToReference(entry)
                        actionEntry = null
                    },
                    onDelete = {
                        onDeleteItem(entry.id)
                        actionEntry = null
                    },
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
        contentPadding = PaddingValues(UiTokens.s2),
        verticalArrangement = Arrangement.spacedBy(UiTokens.s2),
    ) {
        item {
            SectionCard(title = "提示词库", subtitle = "一行一个条目，保存后会同步到服务器。") {
                TextField(
                    value = promptLibraryDraft,
                    onValueChange = onPromptLibraryDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 20,
                shape = UiTokens.r12,
                colors = appTextFieldColors(),
                )
                Spacer(Modifier.height(UiTokens.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
                    FilledTonalButton(onClick = onNewPersona) {
                        Text("新建人格")
                    }
                    FilledTonalButton(onClick = onPersonaImport) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导入 .md")
                    }
                }
                Spacer(Modifier.height(UiTokens.s2))
                if (state.personas.isNotEmpty()) {
                    Text("可用人设", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
                        items(state.personas, key = { it.id }) { persona ->
                            Card(
                                modifier = Modifier.widthIn(min = 180.dp).clickable {
                                    onPersonaSelect(persona.id)
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (persona.id == state.selectedPersonaId) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                            ) {
                                Column(modifier = Modifier.padding(UiTokens.s2), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        contentPadding = PaddingValues(UiTokens.s2),
        verticalArrangement = Arrangement.spacedBy(UiTokens.s2),
    ) {
        item {
            SectionCard(title = "服务器", subtitle = "可随时切换到新的后端地址。") {
                TextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器地址") },
                    singleLine = true,
                shape = UiTokens.r12,
                colors = appTextFieldColors(),
                )
                Spacer(Modifier.height(UiTokens.s2))
                Button(
                    onClick = onApplyServerUrl,
                    shape = UiTokens.r16,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
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
                        FilledTonalButton(onClick = onLogout, shape = UiTokens.r12) {
                            Text("退出登录")
                        }
                    }
                } else {
                    TextField(
                        value = state.loginPassword,
                        onValueChange = onLoginPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("访问密码") },
                        singleLine = true,
                    shape = UiTokens.r12,
                    colors = appTextFieldColors(),
                    )
                    Spacer(Modifier.height(UiTokens.s2))
                    Button(
                        onClick = onLogin,
                        shape = UiTokens.r16,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    ) {
                        Text("登录")
                    }
                }
            }
        }
        item {
            SectionCard(title = "提示词库", subtitle = "原来的提示词页已并入设置；一行一个条目。") {
                TextField(
                    value = promptLibraryDraft,
                    onValueChange = onPromptLibraryDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 20,
                shape = UiTokens.r12,
                colors = appTextFieldColors(),
                )
                Button(onClick = onSavePromptLibrary) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("保存提示词库")
                }
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
            SectionCard(title = "提示词优化人设", subtitle = "导入、编辑、保存和删除提示词优化人格。") {
                Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
                    FilledTonalButton(onClick = onNewPersona) {
                        Text("新建人格")
                    }
                    FilledTonalButton(onClick = onPersonaImport) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导入 .md")
                    }
                }
                if (state.personas.isNotEmpty()) {
                    Text("可用人设", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
                        items(state.personas, key = { it.id }) { persona ->
                            Card(
                                modifier = Modifier
                                    .widthIn(min = 180.dp)
                                    .clickable { onPersonaSelect(persona.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (persona.id == state.selectedPersonaId) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                            ) {
                                Column(modifier = Modifier.padding(UiTokens.s2), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(persona.name, fontWeight = FontWeight.SemiBold)
                                    Text(persona.summary, style = MaterialTheme.typography.bodySmall)
                                    Text(persona.filename, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
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
        item {
            SectionCard(title = "关于", subtitle = "BananaLab 原生 Android 版。") {
                Text("功能覆盖生成、结果、历史、服务器连接、提示词库、人设管理、提示词优化和批量下载。")
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        shape = UiTokens.r20,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(UiTokens.s2), verticalArrangement = Arrangement.spacedBy(UiTokens.s1)) {
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable(onClick = onAdd),
            shape = UiTokens.r16,
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
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.s2)) {
                FilledTonalButton(onClick = onAdd) {
                    Text("更换")
                }
                FilledTonalButton(onClick = onRemove) {
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clickable(onClick = onClick),
        shape = UiTokens.r12,
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
    Card(
        modifier = Modifier
            .size(92.dp),
        shape = UiTokens.r12,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
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
                    .clip(UiTokens.r12)
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
            TextField(
                value = state.optimizedPrompt,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("优化后的英文提示词") },
                minLines = 3,
                maxLines = 6,
                readOnly = true,
            shape = UiTokens.r12,
            colors = appTextFieldColors(),
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
private fun HistoryAlbumCard(
    entry: HistoryEntry,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onView: () -> Unit,
    onMore: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = UiTokens.r16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteGridThumb(
                url = preferredThumbUrl(entry).ifBlank { preferredImageUrl(entry) },
                contentDescription = entry.prompt,
                onClick = onView,
                onLongClick = onToggleSelection,
            )
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection() },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = entry.createdAt,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                        Text(
                            text = historyMetaText(entry),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = onMore,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                }
                Text(
                    text = entry.prompt.ifBlank { "未填写提示词" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun HistoryActionSheet(
    entry: HistoryEntry,
    onView: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onSendToBase: () -> Unit,
    onSendToReference: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = entry.createdAt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = entry.prompt.ifBlank { "未填写提示词" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onView, modifier = Modifier.fillMaxWidth()) {
            Text("查看大图")
        }
        FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("下载")
        }
        FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
            Text("复制提示词")
        }
        FilledTonalButton(onClick = onSendToBase, modifier = Modifier.fillMaxWidth()) {
            Text("送到基础图")
        }
        FilledTonalButton(onClick = onSendToReference, modifier = Modifier.fillMaxWidth()) {
            Text("送到参考图")
        }
        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text("删除")
        }
    }
}

@Composable
private fun RemoteGridThumb(
    url: String,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val loaded = remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (loaded.value) 1f else 0f, label = "remote-grid-thumb-alpha")
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .build()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
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
private fun ResultCard(
    entry: HistoryEntry?,
    onCopy: (String) -> Unit,
    onView: (String) -> Unit,
    onUsePrompt: () -> Unit,
    onDownload: () -> Unit,
    onSendToBase: () -> Unit,
    onSendToReference: () -> Unit,
) {
    SectionCard(title = "当前结果", subtitle = "生成完成后会稳定显示在这里。") {
        if (entry == null) {
            EmptyHint("还没有结果。")
        } else {
            RemoteThumb(
                url = preferredImageUrl(entry),
                contentDescription = entry.prompt,
                onClick = { onView(preferredImageUrl(entry)) },
                onLongClick = onDownload,
                height = 320.dp,
            )
            Text(
                text = historyMetaText(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.prompt.ifBlank { "未填写提示词" }, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            CompactActionRow {
                CompactTextButton(text = "复制", onClick = { onCopy(entry.prompt) }, icon = Icons.Default.ContentCopy)
                CompactTextButton(text = "下载", onClick = onDownload, icon = Icons.Default.Download)
                CompactTextButton(text = "带回提示词", onClick = onUsePrompt)
                CompactTextButton(text = "送基础图", onClick = onSendToBase)
                CompactTextButton(text = "送参考图", onClick = onSendToReference)
            }
        }
    }
}

private fun historyGridColumns(screenWidthDp: Int): Int = when {
    screenWidthDp >= 1100 -> 6
    screenWidthDp >= 900 -> 5
    screenWidthDp >= 720 -> 4
    else -> 3
}

private fun historyMetaText(entry: HistoryEntry): String {
    return listOfNotNull(
        entry.aspectRatio.takeIf { it.isNotBlank() },
        entry.imageSize.takeIf { it.isNotBlank() },
        entry.apiPlatformName.takeIf { it.isNotBlank() },
        entry.imageModel.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
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
        TextField(
            value = editor.filename,
            onValueChange = onFilenameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("文件名") },
            singleLine = true,
        shape = UiTokens.r12,
        colors = appTextFieldColors(),
        )
        TextField(
            value = editor.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("人格名称") },
            singleLine = true,
        shape = UiTokens.r12,
        colors = appTextFieldColors(),
        )
        TextField(
            value = editor.summary,
            onValueChange = onSummaryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("一句简介") },
            singleLine = true,
        shape = UiTokens.r12,
        colors = appTextFieldColors(),
        )
        TextField(
            value = editor.content,
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("人格正文 / system prompt") },
            minLines = 6,
            maxLines = 16,
        shape = UiTokens.r12,
        colors = appTextFieldColors(),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = UiTokens.r12,
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
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        alwaysShowLabel = true,
    )
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
    Card(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = UiTokens.r16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(UiTokens.s2), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun appTextFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
)

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
