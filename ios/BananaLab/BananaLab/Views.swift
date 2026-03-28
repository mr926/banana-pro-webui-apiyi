import PhotosUI
import SwiftUI
import UIKit

struct RootView: View {
    @EnvironmentObject private var model: BananaLabViewModel

    var body: some View {
        ZStack {
            background
            content
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if let banner = model.banner {
                BannerView(message: banner)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 4)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: banner.id) {
                        try? await Task.sleep(nanoseconds: 2_600_000_000)
                        if model.banner?.id == banner.id {
                            withAnimation(.easeOut(duration: 0.2)) {
                                model.banner = nil
                            }
                        }
                    }
            }
        }
        .sheet(isPresented: $model.showingPersonaEditor) {
            PersonaEditorSheet()
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $model.showingShareSheet) {
            ActivityView(items: model.shareSheetURLs)
        }
    }

    private var background: some View {
        LinearGradient(
            colors: [
                Color(uiColor: .systemGroupedBackground),
                Color(uiColor: .secondarySystemGroupedBackground),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .bootstrapping:
            BootstrapView()
        case .loginRequired:
            LoginView()
        case .ready:
            AdaptiveShellView()
        }
    }
}

struct BootstrapView: View {
    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "sparkles")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(.secondary)
            Text("BananaLab")
                .font(.largeTitle.weight(.semibold))
            ProgressView()
            Text("正在连接服务器")
                .font(.callout)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .padding()
    }
}

struct LoginView: View {
    @EnvironmentObject private var model: BananaLabViewModel
    @FocusState private var loginFocused: Bool
    @State private var editingServerURL: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                section
            }
            .padding(20)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .onAppear {
            editingServerURL = model.settingsEditedServerURL.isEmpty ? model.serverURL : model.settingsEditedServerURL
            loginFocused = model.authStatus.passwordEnabled
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("BananaLab")
                .font(.largeTitle.weight(.semibold))
            Text("连接到你的图生图服务器")
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }

    private var section: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 16) {
                Label("服务器设置", systemImage: "server.rack")
                    .font(.headline)
                TextField("服务器地址", text: $editingServerURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)

                Button {
                    Task { await model.updateServerURLAndReconnect(editingServerURL) }
                } label: {
                    Label("保存并重连", systemImage: "arrow.clockwise")
                }
                .buttonStyle(PrimaryButtonStyle())

                Divider()

                if model.authStatus.passwordEnabled {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("登录密码")
                            .font(.headline)
                        SecureField("输入服务器密码", text: $model.loginPassword)
                            .textFieldStyle(.roundedBorder)
                            .focused($loginFocused)
                        Button {
                            Task { await model.login() }
                        } label: {
                            if model.loginInProgress {
                                ProgressView().frame(maxWidth: .infinity)
                            } else {
                                Text("登录")
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(model.loginInProgress)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("当前服务器未启用密码。")
                            .foregroundStyle(.secondary)
                        Button {
                            Task { await model.reloadWorkspace() }
                        } label: {
                            Text("继续进入")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    }
                }
            }
            .padding(18)
        }
    }
}

struct AdaptiveShellView: View {
    @EnvironmentObject private var model: BananaLabViewModel
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                splitShell
            } else {
                tabShell
            }
        }
    }

    private var splitShell: some View {
        NavigationSplitView {
            List {
                ForEach(AppTab.allCases) { tab in
                    sidebarRow(for: tab)
                }
            }
            .navigationTitle("BananaLab")
            .listStyle(.sidebar)
        } detail: {
            NavigationStack {
                tabContent(model.activeTab)
                    .navigationTitle(model.activeTab.title)
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    private func sidebarRow(for tab: AppTab) -> some View {
        let isSelected = model.activeTab == tab
        return Button {
            model.activeTab = tab
        } label: {
            Label {
                Text(tab.title)
            } icon: {
                Image(systemName: tab.systemImage)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .foregroundStyle(isSelected ? Color.accentColor : .primary)
        .listRowBackground(isSelected ? Color.accentColor.opacity(0.12) : Color.clear)
    }

    private var tabShell: some View {
        TabView(selection: $model.activeTab) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack {
                    tabContent(tab)
                        .navigationTitle(tab.title)
                        .navigationBarTitleDisplayMode(.inline)
                }
                .tabItem { Label(tab.title, systemImage: tab.systemImage) }
                .tag(tab)
            }
        }
    }

    @ViewBuilder
    private func tabContent(_ tab: AppTab) -> some View {
        switch tab {
        case .generate:
            GenerateView()
        case .history:
            HistoryView()
        case .prompts:
            PromptLibraryView()
        case .personas:
            PersonasView()
        case .settings:
            SettingsView()
        }
    }
}

struct GenerateView: View {
    @EnvironmentObject private var model: BananaLabViewModel
    @State private var basePickerItem: PhotosPickerItem?
    @State private var referencePickerItems: [PhotosPickerItem] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                promptSection
                referenceSection
                baseImageSection
                ratioSizeSection
                resultSection
            }
            .padding(16)
            .frame(maxWidth: 900, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .safeAreaInset(edge: .bottom) {
            generateBar
        }
        .onChange(of: basePickerItem?.itemIdentifier ?? "") { _, newValue in
            guard !newValue.isEmpty else { return }
            model.selectBaseImage(from: basePickerItem)
        }
        .onChange(of: referencePickerItems.map { $0.itemIdentifier ?? "" }) { _, newValue in
            guard !newValue.isEmpty else { return }
            model.setReferenceImages(from: referencePickerItems)
        }
    }

    private var promptSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Base Prompt")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text("提示词")
                            .font(.headline)
                    }
                    Spacer()
                    Picker("模式", selection: $model.promptMode) {
                        ForEach(PromptMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    .frame(maxWidth: 220)
                }

                TextEditor(text: $model.promptText)
                    .font(.callout)
                    .frame(minHeight: 150)
                    .padding(8)
                    .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                HStack(spacing: 10) {
                    Menu {
                        Button("插入当前结果提示词") {
                            if let prompt = model.currentResult?.prompt {
                                model.insertPromptLibraryItem(prompt)
                            }
                        }
                        Divider()
                        ForEach(model.promptLibrary.items, id: \.self) { item in
                            Button(item) { model.insertPromptLibraryItem(item) }
                        }
                    } label: {
                        Label("插入提示词", systemImage: "plus")
                    }
                    .buttonStyle(SecondaryButtonStyle())

                    Button("清空") {
                        model.promptText = ""
                        model.optimizedPrompt = ""
                    }
                    .buttonStyle(SecondaryButtonStyle())

                    if model.promptMode == .optimized {
                        Button {
                            Task { _ = await model.optimizePromptIfNeeded() }
                        } label: {
                            if model.optimizingPrompt {
                                ProgressView().frame(maxWidth: .infinity)
                            } else {
                                Text("优化提示词")
                            }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(model.optimizingPrompt)
                    }
                }

                if !model.optimizedPrompt.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("优化结果")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(model.optimizedPrompt)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .padding(10)
                            .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
            }
            .padding(18)
        }
    }

    private var referenceSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Style Reference Images")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text("风格参考图")
                            .font(.headline)
                    }
                    Spacer()
                    Text("\(model.referenceImages.count) / 6")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                PhotosPicker(selection: $referencePickerItems, maxSelectionCount: 6, matching: .images) {
                    Label("选择参考图", systemImage: "photo.on.rectangle.angled")
                }
                .buttonStyle(SecondaryButtonStyle())

                if model.referenceImages.isEmpty {
                    EmptySectionHint(text: "选择最多 6 张风格参考图。")
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(model.referenceImages) { item in
                                VStack(alignment: .leading, spacing: 8) {
                                    ZStack(alignment: .topTrailing) {
                                        Image(uiImage: item.previewImage)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 110, height: 110)
                                            .clipped()
                                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                        Button {
                                            model.removeReferenceImage(id: item.id)
                                        } label: {
                                            Image(systemName: "xmark.circle.fill")
                                                .font(.title3)
                                                .symbolRenderingMode(.hierarchical)
                                                .foregroundStyle(.white, .black.opacity(0.45))
                                        }
                                        .padding(6)
                                    }
                                    Text(item.filename)
                                        .font(.caption2)
                                        .lineLimit(1)
                                        .frame(width: 110, alignment: .leading)
                                }
                            }
                        }
                    }
                }
            }
            .padding(18)
        }
    }

    private var baseImageSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Base Image")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text("基础图")
                            .font(.headline)
                    }
                    Spacer()
                    if model.baseImage != nil {
                        Button("移除") { model.baseImage = nil }
                            .buttonStyle(SecondaryButtonStyle())
                    }
                }

                PhotosPicker(selection: $basePickerItem, matching: .images) {
                    Label("选择基础图", systemImage: "photo")
                }
                .buttonStyle(SecondaryButtonStyle())

                if let baseImage = model.baseImage {
                    HStack(alignment: .top, spacing: 14) {
                        Image(uiImage: baseImage.previewImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 132, height: 132)
                            .clipped()
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                        VStack(alignment: .leading, spacing: 8) {
                            Text(baseImage.filename)
                                .font(.subheadline.weight(.semibold))
                            Text(baseImage.readableSize)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(baseImage.compressed ? "已自动压缩" : "原图")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                } else {
                    EmptySectionHint(text: "上传主结构图，其他图片会围绕它进行生成。")
                }
            }
            .padding(18)
        }
    }

    private var ratioSizeSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Image Ratio & Size")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text("比例与尺寸")
                        .font(.headline)
                }

                Picker("比例", selection: $model.aspectRatio) {
                    ForEach(["auto", "1:1", "4:3", "3:4", "16:9", "9:16", "3:2", "2:3", "21:9"], id: \.self) { item in
                        Text(item.uppercased() == "AUTO" ? "自动" : item).tag(item)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()

                Picker("尺寸", selection: $model.imageSize) {
                    ForEach(["1K", "2K", "4K"], id: \.self) { item in
                        Text(item).tag(item)
                    }
                }
                .pickerStyle(.segmented)

                Toggle("开启搜索增强", isOn: $model.enableSearch)
                    .font(.subheadline)
            }
            .padding(18)
        }
    }

    private var resultSection: some View {
        Group {
            if let result = model.currentResult {
                SectionCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Current Result")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                Text("当前结果")
                                    .font(.headline)
                            }
                            Spacer()
                            Text(result.createdAt)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        CachedRemoteImageView(url: result.preferredImageURL)
                            .frame(maxWidth: .infinity)
                            .frame(height: 320)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

                        Text(result.message ?? "生成成功")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(18)
                }
            }
        }
    }

    private var generateBar: some View {
        VStack(spacing: 10) {
            Divider()
            HStack(spacing: 12) {
                if model.generationRetryAvailable {
                    Button {
                        Task { await model.retryLastGeneration() }
                    } label: {
                        Label("重试", systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }

                Button {
                    Task { await model.generate() }
                } label: {
                    if model.generationInProgress {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Label("开始生成", systemImage: "sparkles")
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(model.generationInProgress || model.baseImage == nil)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
        .background(.ultraThinMaterial)
    }
}

struct HistoryView: View {
    @EnvironmentObject private var model: BananaLabViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                content
            }
            .padding(16)
            .frame(maxWidth: 1100, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .refreshable {
            await model.loadHistory()
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("History Album")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text("历史相册")
                    .font(.largeTitle.weight(.semibold))
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 8) {
                Text("\(model.historySelection.count) / \(model.history.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    Button("批量下载") {
                        Task { await model.fetchSelectedDownloadURLs() }
                    }
                    .buttonStyle(SecondaryButtonStyle())
                    .disabled(model.historySelection.isEmpty)

                    Button("清空选择") { model.clearHistorySelection() }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(model.historySelection.isEmpty)
                }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.history.isEmpty {
            SectionCard {
                EmptySectionHint(text: "登录后会显示历史生成结果。")
                    .frame(maxWidth: .infinity)
                    .padding(24)
            }
        } else {
            LazyVGrid(columns: gridColumns, spacing: 14) {
                ForEach(model.history) { entry in
                    HistoryCard(entry: entry)
                }
            }
        }
    }

    private var gridColumns: [GridItem] {
        [GridItem(.adaptive(minimum: 240, maximum: 360), spacing: 14)]
    }
}

struct HistoryCard: View {
    @EnvironmentObject private var model: BananaLabViewModel
    let entry: HistoryEntry

    var body: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                ZStack(alignment: .topTrailing) {
                    CachedRemoteImageView(url: entry.preferredThumbURL)
                        .frame(height: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                    Button {
                        model.toggleHistorySelection(entry.id)
                    } label: {
                        Image(systemName: model.historySelection.contains(entry.id) ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .symbolRenderingMode(.hierarchical)
                            .padding(10)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.createdAt)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(entry.prompt)
                        .font(.subheadline)
                        .lineLimit(3)
                        .foregroundStyle(.primary)
                }

                HStack(spacing: 8) {
                    Pill(text: entry.aspectRatio)
                    Pill(text: entry.imageSize)
                    if entry.ossImageUrl != nil {
                        Pill(text: "OSS")
                    }
                }

                HStack(spacing: 8) {
                    Button("设为基础图") {
                        Task { await model.importHistoryEntry(entry, asBase: true) }
                    }
                    .buttonStyle(SecondaryButtonStyle())

                    Button("设为参考图") {
                        Task { await model.importHistoryEntry(entry, asBase: false) }
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }

                HStack(spacing: 8) {
                    Button("下载") {
                        Task { await model.downloadSingleHistory(entry) }
                    }
                    .buttonStyle(SecondaryButtonStyle())

                    Button("删除") {
                        Task { await model.deleteHistory(id: entry.id) }
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }
            }
            .padding(16)
        }
    }
}

struct PromptLibraryView: View {
    @EnvironmentObject private var model: BananaLabViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                editor
            }
            .padding(16)
            .frame(maxWidth: 920, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Prompt Library")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text("提示词列表")
                    .font(.largeTitle.weight(.semibold))
            }
            Spacer()
            Button("保存") {
                Task { await model.savePromptLibrary() }
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }

    private var editor: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 14) {
                TextEditor(text: $model.promptLibraryDraft)
                    .font(.callout)
                    .frame(minHeight: 220)
                    .padding(8)
                    .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                if !model.promptLibrary.items.isEmpty {
                    FlowLayout(spacing: 8) {
                        ForEach(model.promptLibrary.items, id: \.self) { item in
                            Button(item) {
                                model.insertPromptLibraryItem(item)
                                model.activeTab = .generate
                            }
                            .buttonStyle(TagButtonStyle())
                        }
                    }
                }
            }
            .padding(18)
        }
    }
}

struct PersonasView: View {
    @EnvironmentObject private var model: BananaLabViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                selector
                list
            }
            .padding(16)
            .frame(maxWidth: 960, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Prompt Personas")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text("优化人设")
                    .font(.largeTitle.weight(.semibold))
            }
            Spacer()
            Button("新建") { model.startNewPersona() }
                .buttonStyle(PrimaryButtonStyle())
        }
    }

    private var selector: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("当前生成人设")
                    .font(.headline)
                Picker("人设", selection: $model.selectedPersonaID) {
                    Text("默认").tag("")
                    ForEach(model.personas) { persona in
                        Text(persona.name).tag(persona.id)
                    }
                }
                .pickerStyle(.menu)
            }
            .padding(18)
        }
    }

    private var list: some View {
        Group {
            if model.personas.isEmpty {
                SectionCard {
                    EmptySectionHint(text: "还没有人设，点击右上角新建一个。")
                        .frame(maxWidth: .infinity)
                        .padding(24)
                }
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(model.personas) { persona in
                        SectionCard {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(persona.name)
                                            .font(.headline)
                                        Text(persona.summary)
                                            .font(.subheadline)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if model.selectedPersonaID == persona.id {
                                        Pill(text: "当前")
                                    }
                                }
                                Text(persona.filename)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)

                                HStack(spacing: 8) {
                                    Button("编辑") {
                                        Task { await model.editPersona(id: persona.id) }
                                    }
                                    .buttonStyle(SecondaryButtonStyle())

                                    Button("删除") {
                                        Task { await model.deletePersona(id: persona.id) }
                                    }
                                    .buttonStyle(SecondaryButtonStyle())
                                }
                            }
                            .padding(16)
                        }
                    }
                }
            }
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: BananaLabViewModel
    @State private var localServerURL: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                serverSection
                sessionSection
                aboutSection
            }
            .padding(16)
            .frame(maxWidth: 820, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .onAppear {
            localServerURL = model.settingsEditedServerURL.isEmpty ? model.serverURL : model.settingsEditedServerURL
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Settings")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text("设置")
                .font(.largeTitle.weight(.semibold))
        }
    }

    private var serverSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("服务器地址")
                    .font(.headline)
                TextField("http://127.0.0.1:8787", text: $localServerURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                HStack(spacing: 8) {
                    Button("保存并重连") {
                        Task { await model.updateServerURLAndReconnect(localServerURL) }
                    }
                    .buttonStyle(PrimaryButtonStyle())

                    Button("刷新") {
                        Task { await model.reloadWorkspace() }
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }
            }
            .padding(18)
        }
    }

    private var sessionSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("登录状态")
                    .font(.headline)
                Text(model.authStatus.authenticated ? "已登录" : "未登录")
                    .foregroundStyle(.secondary)
                if model.authStatus.passwordEnabled {
                    Text("服务器启用了密码，登录状态会安全保存 24 小时。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Text("当前服务器未启用密码。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Button("退出登录") {
                    Task { await model.logout() }
                }
                .buttonStyle(SecondaryButtonStyle())
            }
            .padding(18)
        }
    }

    private var aboutSection: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("BananaLab")
                    .font(.headline)
                Text("iOS 原生客户端，支持 iPhone 和 iPad。")
                    .foregroundStyle(.secondary)
                Text("Version \(Bundle.main.appVersion)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(18)
        }
    }
}

struct PersonaEditorSheet: View {
    @EnvironmentObject private var model: BananaLabViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("基本信息") {
                    TextField("名称", text: $model.personaDraft.name)
                    TextField("简介", text: $model.personaDraft.summary, axis: .vertical)
                        .lineLimit(2...3)
                }

                Section("正文") {
                    TextEditor(text: $model.personaDraft.content)
                        .frame(minHeight: 240)
                }

                Section("文件名") {
                    TextField("persona.md", text: $model.personaDraft.filename)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
            .navigationTitle(model.personaDraft.isNew ? "新建人设" : "编辑人设")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        model.showingPersonaEditor = false
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        Task {
                            await model.savePersonaDraft()
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

struct BannerView: View {
    let message: BannerMessage

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbolName)
                .font(.headline)
                .foregroundStyle(foregroundColor)
                .padding(.top, 1)

            VStack(alignment: .leading, spacing: 2) {
                Text(message.title)
                    .font(.subheadline.weight(.semibold))
                Text(message.message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .background(backgroundColor, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(borderColor, lineWidth: 1)
        }
    }

    private var symbolName: String {
        switch message.kind {
        case .success: return "checkmark.circle.fill"
        case .error: return "exclamationmark.triangle.fill"
        case .info: return "info.circle.fill"
        }
    }

    private var foregroundColor: Color {
        switch message.kind {
        case .success: return .green
        case .error: return .red
        case .info: return .blue
        }
    }

    private var backgroundColor: Color {
        switch message.kind {
        case .success: return Color.green.opacity(0.10)
        case .error: return Color.red.opacity(0.10)
        case .info: return Color.blue.opacity(0.10)
        }
    }

    private var borderColor: Color {
        switch message.kind {
        case .success: return Color.green.opacity(0.18)
        case .error: return Color.red.opacity(0.18)
        case .info: return Color.blue.opacity(0.18)
        }
    }
}

struct SectionCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color.black.opacity(0.06), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.03), radius: 10, y: 3)
    }
}

struct EmptySectionHint: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.callout)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct CachedRemoteImageView: View {
    let url: String
    @State private var image: UIImage?
    @State private var loading = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else if loading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(uiColor: .secondarySystemGroupedBackground))
            } else {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemGroupedBackground))
                    .overlay {
                        Image(systemName: "photo")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                    }
            }
        }
        .task(id: url) {
            guard !url.isEmpty else {
                image = nil
                return
            }
            loading = true
            defer { loading = false }
            do {
                image = try await RemoteImageCache.shared.image(for: url)
            } catch {
                image = nil
            }
        }
    }
}

struct ActivityView: UIViewControllerRepresentable {
    let items: [URL]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct Pill: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.secondary)
            .padding(.vertical, 6)
            .padding(.horizontal, 8)
            .background(Color(uiColor: .tertiarySystemGroupedBackground), in: Capsule())
    }
}

struct TagButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.caption.weight(.semibold))
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
            .background(configuration.isPressed ? Color.black.opacity(0.08) : Color(uiColor: .tertiarySystemGroupedBackground), in: Capsule())
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .padding(.vertical, 10)
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
            .background(configuration.isPressed ? Color.black.opacity(0.08) : Color(uiColor: .tertiarySystemGroupedBackground), in: Capsule())
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.vertical, 12)
            .padding(.horizontal, 18)
            .frame(minHeight: 44)
            .background(configuration.isPressed ? Color.accentColor.opacity(0.82) : Color.accentColor, in: Capsule())
    }
}

struct FlowLayout<Content: View>: View {
    let spacing: CGFloat
    let content: Content

    init(spacing: CGFloat, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            content
        }
    }
}

private extension Bundle {
    var appVersion: String {
        let version = object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(version) (\(build))"
    }
}
