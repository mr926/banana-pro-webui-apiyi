import CoreGraphics
import Foundation
import UIKit
import UniformTypeIdentifiers

struct AuthStatus: Codable {
    let authenticated: Bool
    let passwordEnabled: Bool
}

struct HistoryEntry: Codable, Identifiable, Hashable {
    let id: String
    let createdAt: String
    let prompt: String
    let sourcePrompt: String
    let promptMode: String
    let aspectRatio: String
    let imageSize: String
    let enableSearch: Bool
    let baseImageName: String
    let referenceCount: Int
    let imageUrl: String
    let thumbUrl: String
    let downloadName: String
    let message: String?
    let ossImageUrl: String?
    let ossThumbUrl: String?
    let ossImageKey: String?
    let ossThumbKey: String?
    let ossMetadataXmlUrl: String?
    let ossMetadataXmlKey: String?
    let ossUploadError: String?

    var preferredImageURL: String { (ossImageUrl ?? imageUrl).trimmingCharacters(in: .whitespacesAndNewlines) }
    var preferredThumbURL: String { (ossThumbUrl ?? thumbUrl).trimmingCharacters(in: .whitespacesAndNewlines) }
    var createdDate: Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: createdAt)
    }
}

struct DownloadTarget: Codable, Identifiable, Hashable {
    let id: String
    let url: String
    let downloadName: String
    let source: String
}

struct PromptLibraryState: Codable {
    var content: String
    var items: [String]

    init(content: String = "", items: [String] = []) {
        self.content = content
        self.items = items
    }
}

struct PersonaSummary: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let summary: String
    let filename: String
}

struct PersonaDetail: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let summary: String
    let content: String
    let filename: String
}

struct PersonaDraft: Equatable {
    var id: String = ""
    var filename: String = ""
    var name: String = ""
    var summary: String = ""
    var content: String = ""
    var isNew: Bool = true

    static let empty = PersonaDraft()
}

struct UploadImage: Identifiable {
    let id = UUID()
    let filename: String
    let mimeType: String
    let data: Data
    let pixelSize: CGSize
    let compressed: Bool
    let previewImage: UIImage

    var fileExtension: String {
        if let ext = UTType(mimeType: mimeType)?.preferredFilenameExtension {
            return ext
        }
        return (filename as NSString).pathExtension
    }

    var readableSize: String {
        ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file)
    }
}

struct BannerMessage: Identifiable, Equatable {
    enum Kind: Equatable {
        case success
        case error
        case info
    }

    let id = UUID()
    let kind: Kind
    let title: String
    let message: String

    static func success(_ message: String, title: String = "完成") -> BannerMessage {
        BannerMessage(kind: .success, title: title, message: message)
    }

    static func error(_ message: String, title: String = "出错了") -> BannerMessage {
        BannerMessage(kind: .error, title: title, message: message)
    }

    static func info(_ message: String, title: String = "提示") -> BannerMessage {
        BannerMessage(kind: .info, title: title, message: message)
    }
}

enum AppPhase: Equatable {
    case bootstrapping
    case loginRequired
    case ready
}

enum AppTab: String, CaseIterable, Identifiable {
    case generate
    case history
    case prompts
    case personas
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .generate: return "生成"
        case .history: return "历史"
        case .prompts: return "提示词"
        case .personas: return "人设"
        case .settings: return "设置"
        }
    }

    var systemImage: String {
        switch self {
        case .generate: return "sparkles"
        case .history: return "square.stack.3d.up"
        case .prompts: return "text.justify.left"
        case .personas: return "person.crop.square"
        case .settings: return "gearshape"
        }
    }
}

enum PromptMode: String, Codable, CaseIterable, Identifiable {
    case `default`
    case optimized

    var id: String { rawValue }

    var title: String {
        switch self {
        case .default: return "默认"
        case .optimized: return "优化"
        }
    }
}

struct GenerationRequestSnapshot: Equatable {
    var prompt: String
    var sourcePrompt: String
    var promptMode: PromptMode
    var aspectRatio: String
    var imageSize: String
    var enableSearch: Bool
}

enum AppError: LocalizedError, Equatable {
    case missingBaseImage
    case missingPrompt
    case invalidServerURL
    case invalidResponse
    case unauthorized
    case message(String)

    var errorDescription: String? {
        switch self {
        case .missingBaseImage:
            return "请先选择基础图。"
        case .missingPrompt:
            return "请输入提示词。"
        case .invalidServerURL:
            return "服务器地址不正确。"
        case .invalidResponse:
            return "服务返回的数据格式不正确。"
        case .unauthorized:
            return "登录已失效，请重新登录。"
        case .message(let text):
            return text
        }
    }
}
