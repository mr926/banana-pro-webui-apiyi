import Foundation
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

@MainActor
final class BananaLabViewModel: ObservableObject {
    @Published var phase: AppPhase = .bootstrapping
    @Published var serverURL: String = ""
    @Published var authStatus = AuthStatus(authenticated: false, passwordEnabled: true)
    @Published var loginPassword: String = ""
    @Published var loginInProgress = false
    @Published var banner: BannerMessage?
    @Published var activeTab: AppTab = .generate

    @Published var promptText: String = ""
    @Published var promptMode: PromptMode = .default
    @Published var optimizedPrompt: String = ""
    @Published var optimizingPrompt = false
    @Published var selectedPersonaID: String = ""

    @Published var promptLibrary = PromptLibraryState()
    @Published var promptLibraryDraft: String = ""

    @Published var personas: [PersonaSummary] = []
    @Published var personaDraft = PersonaDraft.empty
    @Published var showingPersonaEditor = false

    @Published var baseImage: UploadImage?
    @Published var referenceImages: [UploadImage] = []
    @Published var aspectRatio: String = "auto"
    @Published var imageSize: String = "4K"
    @Published var enableSearch = false

    @Published var generationInProgress = false
    @Published var generationRetryAvailable = false
    @Published var currentResult: HistoryEntry?
    @Published var lastGenerationMessage: String = ""

    @Published var history: [HistoryEntry] = []
    @Published var historySelection: Set<String> = []

    @Published var shareSheetURLs: [URL] = []
    @Published var showingShareSheet = false

    @Published var settingsEditedServerURL: String = ""

    private let sessionStore = SessionStore.shared
    private var api: BananaLabAPI?
    private var lastGenerationBundle: GenerationBundle?
    private var bootstrapTask: Task<Void, Never>?

    init() {
        bootstrapTask = Task { await bootstrap() }
    }

    deinit {
        bootstrapTask?.cancel()
    }

    func bootstrap() async {
        phase = .bootstrapping
        serverURL = await sessionStore.storedServerURL()
        settingsEditedServerURL = serverURL
        do {
            api = try BananaLabAPI(baseURL: serverURL, sessionStore: sessionStore)
        } catch {
            banner = .error(AppError.invalidServerURL.localizedDescription)
            phase = .loginRequired
            return
        }

        await refreshAuthStatus()
    }

    func refreshAuthStatus() async {
        guard let api else { return }
        do {
            let status = try await api.authStatus()
            authStatus = status
            if status.authenticated || !status.passwordEnabled {
                phase = .ready
                loginPassword = ""
                await loadWorkspace()
                await NotificationCoordinator.shared.requestAuthorizationIfNeeded()
            } else {
                phase = .loginRequired
            }
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
            phase = .loginRequired
        }
    }

    func login() async {
        guard let api else { return }
        let password = loginPassword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !password.isEmpty else {
            banner = .error("请输入登录密码。")
            return
        }
        loginInProgress = true
        defer { loginInProgress = false }
        do {
            let status = try await api.login(password: password)
            authStatus = status
            loginPassword = ""
            phase = .ready
            banner = .success("登录成功")
            await NotificationCoordinator.shared.requestAuthorizationIfNeeded()
            await loadWorkspace()
        } catch AppError.unauthorized {
            authStatus = AuthStatus(authenticated: false, passwordEnabled: true)
            banner = .error("密码错误，请重试。")
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func logout() async {
        await api?.logout()
        await sessionStore.clearSession()
        phase = .loginRequired
        authStatus = AuthStatus(authenticated: false, passwordEnabled: authStatus.passwordEnabled)
        loginPassword = ""
        promptLibrary = PromptLibraryState()
        promptLibraryDraft = ""
        personas = []
        selectedPersonaID = ""
        baseImage = nil
        referenceImages = []
        currentResult = nil
        history = []
        historySelection = []
        optimizedPrompt = ""
        generationRetryAvailable = false
        lastGenerationBundle = nil
        banner = .info("已退出登录")
    }

    func updateServerURLAndReconnect(_ value: String) async {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            banner = .error("服务器地址不能为空。")
            return
        }

        do {
            await sessionStore.clearSession()
            await sessionStore.saveServerURL(trimmed)
            serverURL = trimmed
            settingsEditedServerURL = trimmed
            api = try BananaLabAPI(baseURL: trimmed, sessionStore: sessionStore)
            authStatus = AuthStatus(authenticated: false, passwordEnabled: true)
            phase = .loginRequired
            promptLibrary = PromptLibraryState()
            promptLibraryDraft = ""
            personas = []
            selectedPersonaID = ""
            baseImage = nil
            referenceImages = []
            currentResult = nil
            history = []
            historySelection = []
            optimizedPrompt = ""
            generationRetryAvailable = false
            lastGenerationBundle = nil
            banner = .info("服务器地址已更新")
            await refreshAuthStatus()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func reloadWorkspace() async {
        await refreshAuthStatus()
    }

    func loadWorkspace() async {
        await loadPromptLibrary()
        await loadPersonas()
        await loadHistory()
        if selectedPersonaID.isEmpty {
            selectedPersonaID = personas.first?.id ?? ""
        }
    }

    func loadPromptLibrary() async {
        guard let api else { return }
        do {
            promptLibrary = try await api.fetchPromptLibrary()
            promptLibraryDraft = promptLibrary.content
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func savePromptLibrary() async {
        guard let api else { return }
        do {
            promptLibrary = try await api.savePromptLibrary(promptLibraryDraft)
            banner = .success("提示词列表已保存")
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func insertPromptLibraryItem(_ item: String) {
        let trimmed = item.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if promptText.isEmpty {
            promptText = trimmed
        } else {
            promptText += "\n" + trimmed
        }
    }

    func loadPersonas() async {
        guard let api else { return }
        do {
            personas = try await api.fetchPersonas()
            if selectedPersonaID.isEmpty {
                selectedPersonaID = personas.first?.id ?? ""
            }
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func startNewPersona() {
        personaDraft = .empty
        showingPersonaEditor = true
    }

    func editPersona(id: String) async {
        guard let api else { return }
        do {
            let persona = try await api.fetchPersona(id: id)
            personaDraft = PersonaDraft(
                id: persona.id,
                filename: persona.filename,
                name: persona.name,
                summary: persona.summary,
                content: persona.content,
                isNew: false
            )
            showingPersonaEditor = true
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func savePersonaDraft() async {
        guard let api else { return }
        let draft = personaDraft
        guard !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            banner = .error("请输入人设名称。")
            return
        }
        do {
            let saved = try await api.savePersona(draft)
            if let index = personas.firstIndex(where: { $0.id == saved.id }) {
                personas[index] = PersonaSummary(id: saved.id, name: saved.name, summary: saved.summary, filename: saved.filename)
            } else {
                personas.insert(PersonaSummary(id: saved.id, name: saved.name, summary: saved.summary, filename: saved.filename), at: 0)
            }
            selectedPersonaID = saved.id
            showingPersonaEditor = false
            banner = .success("人设已保存")
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func deletePersona(id: String) async {
        guard let api else { return }
        do {
            try await api.deletePersona(id: id)
            personas.removeAll { $0.id == id }
            if selectedPersonaID == id {
                selectedPersonaID = personas.first?.id ?? ""
            }
            banner = .info("人设已删除")
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func selectBaseImage(from item: PhotosPickerItem?) {
        guard let item else { return }
        Task { @MainActor in
            do {
                baseImage = try await ImagePreparation.loadUploadImage(from: item, role: .base)
                banner = .success("基础图已添加", title: "图片已选中")
            } catch {
                banner = .error(error.localizedDescription)
            }
        }
    }

    func setReferenceImages(from items: [PhotosPickerItem]) {
        Task { @MainActor in
            do {
                let uploads: [UploadImage] = try await withThrowingTaskGroup(of: UploadImage.self) { group in
                    for item in items.prefix(6) {
                        group.addTask {
                            try await ImagePreparation.loadUploadImage(from: item, role: .reference)
                        }
                    }
                    var collected: [UploadImage] = []
                    for try await image in group {
                        collected.append(image)
                    }
                    return collected
                }
                referenceImages = Array(uploads.prefix(6))
                banner = .success("已添加 \(referenceImages.count) 张参考图")
            } catch {
                banner = .error(error.localizedDescription)
            }
        }
    }

    func removeReferenceImage(id: UUID) {
        referenceImages.removeAll { $0.id == id }
    }

    func clearImages() {
        baseImage = nil
        referenceImages = []
    }

    func toggleHistorySelection(_ id: String) {
        if historySelection.contains(id) {
            historySelection.remove(id)
        } else {
            historySelection.insert(id)
        }
    }

    func clearHistorySelection() {
        historySelection.removeAll()
    }

    func loadHistory() async {
        guard let api else { return }
        do {
            history = try await api.fetchHistory()
            if currentResult == nil {
                currentResult = history.first
            }
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func deleteHistory(id: String) async {
        guard let api else { return }
        do {
            try await api.deleteHistory(id: id)
            history.removeAll { $0.id == id }
            historySelection.remove(id)
            if currentResult?.id == id {
                currentResult = history.first
            }
            banner = .info("历史记录已删除")
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func fetchSelectedDownloadURLs() async {
        guard let api else { return }
        let ids = Array(historySelection)
        guard !ids.isEmpty else {
            banner = .error("请先选择要下载的图片。")
            return
        }
        do {
            let targets = try await api.fetchDownloadTargets(ids: ids)
            let urls = try await downloadTargets(targets)
            shareSheetURLs = urls
            showingShareSheet = true
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func downloadSingleHistory(_ entry: HistoryEntry) async {
        guard let api else { return }
        do {
            let targets = try await api.fetchDownloadTargets(ids: [entry.id])
            let urls = try await downloadTargets(targets)
            shareSheetURLs = urls
            showingShareSheet = true
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func importHistoryEntry(_ entry: HistoryEntry, asBase: Bool) async {
        guard let api else { return }
        do {
            let data = try await api.downloadBytes(from: api.preferredImageURL(for: entry))
            let filename = sanitizedDownloadName(entry.downloadName)
            let mimeType = inferredMimeType(from: filename, fallbackURL: api.preferredImageURL(for: entry))
            let role: ImageRole = asBase ? .base : .reference
            let upload = try ImagePreparation.prepareUploadImage(
                data: data,
                filename: filename,
                mimeType: mimeType,
                role: role
            )
            if asBase {
                baseImage = upload
                activeTab = .generate
                banner = .success("已导入为基础图")
            } else {
                if referenceImages.count >= 6 {
                    referenceImages.removeFirst()
                }
                referenceImages.append(upload)
                activeTab = .generate
                banner = .success("已导入为参考图")
            }
        } catch AppError.unauthorized {
            await handleUnauthorized()
        } catch {
            banner = .error(error.localizedDescription)
        }
    }

    func optimizePromptIfNeeded() async -> String? {
        guard let api else { return nil }
        let sourcePrompt = promptText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !sourcePrompt.isEmpty else {
            banner = .error(AppError.missingPrompt.localizedDescription)
            return nil
        }

        let personaID = selectedPersonaID.isEmpty ? personas.first?.id ?? "" : selectedPersonaID
        guard !personaID.isEmpty else {
            banner = .error("请先选择一个优化人设。")
            return nil
        }

        optimizingPrompt = true
        defer { optimizingPrompt = false }

        do {
            let response = try await api.optimizePrompt(prompt: sourcePrompt, personaId: personaID)
            optimizedPrompt = response.prompt
            return response.prompt
        } catch AppError.unauthorized {
            await handleUnauthorized()
            return nil
        } catch {
            banner = .error(error.localizedDescription)
            return nil
        }
    }

    func generate() async {
        guard let api else { return }
        guard let baseImage else {
            banner = .error(AppError.missingBaseImage.localizedDescription)
            return
        }

        let sourcePrompt = promptText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !sourcePrompt.isEmpty else {
            banner = .error(AppError.missingPrompt.localizedDescription)
            return
        }

        let personaID = selectedPersonaID.isEmpty ? personas.first?.id ?? "" : selectedPersonaID
        let finalPrompt: String
        if promptMode == .optimized {
            guard !personaID.isEmpty else {
                banner = .error("请先选择一个优化人设。")
                return
            }
            guard let optimized = await optimizePromptIfNeeded() else { return }
            finalPrompt = optimized
        } else {
            finalPrompt = sourcePrompt
        }

        let bundle = GenerationBundle(
            snapshot: GenerationRequestSnapshot(
                prompt: finalPrompt,
                sourcePrompt: sourcePrompt,
                promptMode: promptMode,
                aspectRatio: aspectRatio,
                imageSize: imageSize,
                enableSearch: enableSearch
            ),
            baseImage: baseImage,
            referenceImages: referenceImages
        )
        lastGenerationBundle = bundle
        generationInProgress = true
        generationRetryAvailable = false
        defer { generationInProgress = false }

        do {
            let result = try await api.generate(
                prompt: bundle.snapshot.prompt,
                sourcePrompt: bundle.snapshot.sourcePrompt,
                promptMode: bundle.snapshot.promptMode,
                aspectRatio: bundle.snapshot.aspectRatio,
                imageSize: bundle.snapshot.imageSize,
                enableSearch: bundle.snapshot.enableSearch,
                baseImage: bundle.baseImage,
                referenceImages: bundle.referenceImages
            )
            currentResult = result
            history.insert(result, at: 0)
            lastGenerationMessage = result.message ?? "生成完成"
            generationRetryAvailable = false
            banner = .success("生成完成")
            await NotificationCoordinator.shared.postGenerationNotification(
                success: true,
                title: "生成成功",
                message: result.message ?? "图片已生成，可以返回应用查看。"
            )
        } catch AppError.unauthorized {
            await handleUnauthorized()
            generationRetryAvailable = true
            await NotificationCoordinator.shared.postGenerationNotification(
                success: false,
                title: "生成失败",
                message: "登录已失效，请重新登录后再试。"
            )
        } catch {
            generationRetryAvailable = true
            banner = .error(error.localizedDescription)
            lastGenerationMessage = error.localizedDescription
            await NotificationCoordinator.shared.postGenerationNotification(
                success: false,
                title: "生成失败",
                message: error.localizedDescription
            )
        }
    }

    func retryLastGeneration() async {
        guard let bundle = lastGenerationBundle else {
            banner = .error("没有可重试的生成任务。")
            return
        }
        baseImage = bundle.baseImage
        referenceImages = bundle.referenceImages
        promptText = bundle.snapshot.sourcePrompt
        promptMode = bundle.snapshot.promptMode
        aspectRatio = bundle.snapshot.aspectRatio
        imageSize = bundle.snapshot.imageSize
        enableSearch = bundle.snapshot.enableSearch
        await generate()
    }

    func setPromptFromHistory(_ entry: HistoryEntry, asBase: Bool) {
        if asBase {
            promptText = entry.sourcePrompt
            promptMode = .default
        } else {
            if promptText.isEmpty {
                promptText = entry.prompt
            } else {
                promptText += "\n" + entry.prompt
            }
        }
        activeTab = .generate
    }

    func copyPromptText() -> String {
        currentResult?.prompt ?? promptText
    }

    private func handleUnauthorized() async {
        await sessionStore.clearSession()
        phase = .loginRequired
        authStatus = AuthStatus(authenticated: false, passwordEnabled: true)
        loginPassword = ""
        banner = .error("登录已失效，请重新登录。")
    }

    private func downloadTargets(_ targets: [DownloadTarget]) async throws -> [URL] {
        guard let api else { return [] }
        var urls: [URL] = []
        for target in targets {
            let data = try await api.downloadBytes(from: target.url)
            let fileName = sanitizedDownloadName(target.downloadName)
            let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try data.write(to: fileURL, options: .atomic)
            urls.append(fileURL)
        }
        return urls
    }

    private func sanitizedDownloadName(_ name: String) -> String {
        let cleaned = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.isEmpty {
            return "banana-lab-image.jpg"
        }
        let allowed = CharacterSet.alphanumerics.union(.init(charactersIn: "-_ .()"))
        let filtered = String(cleaned.unicodeScalars.filter { allowed.contains($0) })
        return filtered.isEmpty ? "banana-lab-image.jpg" : filtered
    }

    private func inferredMimeType(from filename: String, fallbackURL: String) -> String {
        let extensionName = (filename as NSString).pathExtension.lowercased()
        if let mime = UTType(filenameExtension: extensionName)?.preferredMIMEType {
            return mime
        }
        let fallbackExtension = URL(string: fallbackURL)?.pathExtension.lowercased() ?? ""
        if let mime = UTType(filenameExtension: fallbackExtension)?.preferredMIMEType {
            return mime
        }
        return "image/jpeg"
    }
}

private struct GenerationBundle {
    let snapshot: GenerationRequestSnapshot
    let baseImage: UploadImage
    let referenceImages: [UploadImage]
}
