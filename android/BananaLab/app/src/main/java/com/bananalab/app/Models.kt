package com.bananalab.app

data class AuthStatus(
    val authenticated: Boolean,
    val passwordEnabled: Boolean,
)

data class HistoryEntry(
    val id: String,
    val createdAt: String,
    val prompt: String,
    val sourcePrompt: String,
    val promptMode: String,
    val aspectRatio: String,
    val imageSize: String,
    val enableSearch: Boolean,
    val baseImageName: String,
    val referenceCount: Int,
    val imageUrl: String,
    val thumbUrl: String,
    val downloadName: String,
    val message: String?,
    val ossImageUrl: String? = null,
    val ossThumbUrl: String? = null,
    val ossImageKey: String? = null,
    val ossThumbKey: String? = null,
    val ossMetadataXmlUrl: String? = null,
    val ossMetadataXmlKey: String? = null,
    val ossUploadError: String? = null,
)

data class PromptLibraryState(
    val content: String = "",
    val items: List<String> = emptyList(),
)

data class PersonaEditorState(
    val id: String = "",
    val filename: String = "",
    val name: String = "",
    val summary: String = "",
    val content: String = "",
    val isNew: Boolean = false,
)

data class PersonaSummary(
    val id: String,
    val name: String,
    val summary: String,
    val filename: String,
)

data class PersonaDetail(
    val id: String,
    val name: String,
    val summary: String,
    val content: String,
    val filename: String,
)

data class SelectedImage(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val compressed: Boolean,
    val preview: androidx.compose.ui.graphics.ImageBitmap,
)

data class DownloadTarget(
    val id: String,
    val url: String,
    val downloadName: String,
    val source: String,
)

data class GenerateResult(
    val entry: HistoryEntry,
    val downloadTarget: DownloadTarget? = null,
)

enum class AppTab {
    Generate,
    Result,
    History,
    Settings,
}

enum class PromptMode {
    Default,
    Optimized,
}

data class AppUiState(
    val serverUrl: String = PrefsDefaults.serverUrl,
    val authenticated: Boolean = false,
    val passwordEnabled: Boolean = false,
    val loginPassword: String = "",
    val loginInProgress: Boolean = false,
    val activeTab: AppTab = AppTab.Generate,
    val promptText: String = "",
    val promptMode: PromptMode = PromptMode.Default,
    val optimizedPrompt: String = "",
    val optimizingPrompt: Boolean = false,
    val optimizeMessage: String = "",
    val promptLibrary: PromptLibraryState = PromptLibraryState(),
    val personas: List<PersonaSummary> = emptyList(),
    val selectedPersonaId: String = "",
    val personaEditor: PersonaEditorState = PersonaEditorState(),
    val baseImage: SelectedImage? = null,
    val referenceImages: List<SelectedImage> = emptyList(),
    val aspectRatio: String = "auto",
    val imageSize: String = "4K",
    val enableSearch: Boolean = false,
    val generationInProgress: Boolean = false,
    val generationMessage: String = "",
    val generationRetryAvailable: Boolean = false,
    val currentResult: HistoryEntry? = null,
    val history: List<HistoryEntry> = emptyList(),
    val historySelection: Set<String> = emptySet(),
    val statusMessage: String = "",
    val errorMessage: String = "",
    val pendingDownloadCount: Int = 0,
)

object PrefsDefaults {
    const val serverUrl: String = AppPreferences.DEFAULT_SERVER_URL
}
