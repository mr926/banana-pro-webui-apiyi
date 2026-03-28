import Foundation
import UIKit

final class BananaLabAPI: @unchecked Sendable {
    private let sessionStore: SessionStore
    private let urlSession: URLSession
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    private var baseURL: URL

    init(baseURL: String, sessionStore: SessionStore) throws {
        self.sessionStore = sessionStore
        self.baseURL = try BananaLabAPI.normalizeServerURL(baseURL)

        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 180
        configuration.timeoutIntervalForResource = 360
        configuration.requestCachePolicy = .useProtocolCachePolicy
        configuration.urlCache = URLCache(
            memoryCapacity: 32 * 1024 * 1024,
            diskCapacity: 128 * 1024 * 1024,
            diskPath: "BananaLabURLCache"
        )
        self.urlSession = URLSession(configuration: configuration)
        decoder.keyDecodingStrategy = .useDefaultKeys
        encoder.keyEncodingStrategy = .useDefaultKeys
    }

    func updateServerURL(_ value: String) throws {
        baseURL = try BananaLabAPI.normalizeServerURL(value)
    }

    func authStatus() async throws -> AuthStatus {
        try await request("api/auth/status", method: "GET", authenticated: true, as: AuthStatus.self)
    }

    func login(password: String) async throws -> AuthStatus {
        struct LoginBody: Encodable { let password: String }
        var request = try makeRequest(path: "api/auth/login")
        request.httpMethod = "POST"
        request.httpBody = try encoder.encode(LoginBody(password: password))
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")

        let (data, response) = try await urlSession.data(for: request)
        let status = try decodeResponse(AuthStatus.self, data: data, response: response)

        if let http = response as? HTTPURLResponse,
           let cookie = http.value(forHTTPHeaderField: "Set-Cookie"),
           let token = BananaLabAPI.extractSessionToken(from: cookie) {
            await sessionStore.saveSession(token: token)
        }
        return status
    }

    func logout() async {
        do {
            var request = try makeRequest(path: "api/auth/logout")
            request.httpMethod = "POST"
            _ = try await urlSession.data(for: request)
        } catch {
            // Ignore logout failures; local session is cleared below.
        }
        await sessionStore.clearSession()
    }

    func fetchHistory() async throws -> [HistoryEntry] {
        struct Payload: Decodable { let items: [HistoryEntry] }
        return try await request("api/history", method: "GET", authenticated: true, as: Payload.self).items
    }

    func deleteHistory(id: String) async throws {
        _ = try await request("api/history/\(id.urlEncodedPathComponent())", method: "DELETE", authenticated: true, as: EmptyResponse.self)
    }

    func fetchDownloadTargets(ids: [String]) async throws -> [DownloadTarget] {
        struct Payload: Decodable { let items: [DownloadTarget] }
        let body = try encoder.encode(["ids": ids])
        return try await request(
            "api/history/download-links",
            method: "POST",
            authenticated: true,
            body: body,
            contentType: "application/json; charset=utf-8",
            as: Payload.self
        ).items
    }

    func fetchPromptLibrary() async throws -> PromptLibraryState {
        struct Payload: Decodable {
            let items: [String]
            let content: String
        }
        let payload = try await request("api/prompt-library", method: "GET", authenticated: true, as: Payload.self)
        return PromptLibraryState(content: payload.content, items: payload.items)
    }

    func savePromptLibrary(_ content: String) async throws -> PromptLibraryState {
        struct Body: Encodable { let content: String }
        struct Payload: Decodable {
            let items: [String]
            let content: String
        }
        let body = try encoder.encode(Body(content: content))
        let payload = try await request(
            "api/prompt-library",
            method: "POST",
            authenticated: true,
            body: body,
            contentType: "application/json; charset=utf-8",
            as: Payload.self
        )
        return PromptLibraryState(content: payload.content, items: payload.items)
    }

    func fetchPersonas() async throws -> [PersonaSummary] {
        struct Payload: Decodable { let items: [PersonaSummary] }
        return try await request("api/prompt-personas", method: "GET", authenticated: true, as: Payload.self).items
    }

    func fetchPersona(id: String) async throws -> PersonaDetail {
        try await request("api/prompt-personas/\(id.urlEncodedPathComponent())", method: "GET", authenticated: true, as: PersonaDetail.self)
    }

    func savePersona(_ draft: PersonaDraft) async throws -> PersonaDetail {
        struct Body: Encodable {
            let id: String
            let filename: String
            let name: String
            let summary: String
            let content: String
        }
        let body = try encoder.encode(Body(id: draft.id, filename: draft.filename, name: draft.name, summary: draft.summary, content: draft.content))
        let response: PersonaSaveResponse = try await request(
            draft.isNew ? "api/prompt-personas" : "api/prompt-personas/\(draft.id.urlEncodedPathComponent())",
            method: draft.isNew ? "POST" : "PUT",
            authenticated: true,
            body: body,
            contentType: "application/json; charset=utf-8",
            as: PersonaSaveResponse.self
        )
        return response.item
    }

    func deletePersona(id: String) async throws {
        _ = try await request("api/prompt-personas/\(id.urlEncodedPathComponent())", method: "DELETE", authenticated: true, as: EmptyResponse.self)
    }

    func optimizePrompt(prompt: String, personaId: String) async throws -> OptimizePromptResponse {
        struct Body: Encodable { let prompt: String; let personaId: String }
        let body = try encoder.encode(Body(prompt: prompt, personaId: personaId))
        return try await request(
            "api/optimize-prompt",
            method: "POST",
            authenticated: true,
            body: body,
            contentType: "application/json; charset=utf-8",
            as: OptimizePromptResponse.self
        )
    }

    func generate(
        prompt: String,
        sourcePrompt: String,
        promptMode: PromptMode,
        aspectRatio: String,
        imageSize: String,
        enableSearch: Bool,
        baseImage: UploadImage,
        referenceImages: [UploadImage]
    ) async throws -> HistoryEntry {
        var form = MultipartFormDataBuilder()
        form.append(field: "prompt", value: prompt)
        form.append(field: "sourcePrompt", value: sourcePrompt)
        form.append(field: "promptMode", value: promptMode.rawValue)
        form.append(field: "aspectRatio", value: aspectRatio)
        form.append(field: "imageSize", value: imageSize)
        form.append(field: "enableSearch", value: enableSearch ? "true" : "false")
        form.append(
            file: "baseImage",
            filename: baseImage.filename,
            mimeType: baseImage.mimeType,
            data: baseImage.data
        )
        for (index, item) in referenceImages.enumerated() {
            form.append(
                file: "referenceImages",
                filename: item.filename.isEmpty ? "reference-\(index + 1).jpg" : item.filename,
                mimeType: item.mimeType,
                data: item.data
            )
        }

        let (body, contentType) = form.finalize()
        return try await request(
            "api/generate",
            method: "POST",
            authenticated: true,
            body: body,
            contentType: contentType,
            as: HistoryEntry.self
        )
    }

    func downloadBytes(from urlString: String) async throws -> Data {
        let request = try await makeExternalRequest(urlString: urlString)
        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse, 200...299 ~= http.statusCode else {
            throw AppError.message("下载失败")
        }
        return data
    }

    func preferredImageURL(for entry: HistoryEntry) -> String { entry.preferredImageURL }
    func preferredThumbURL(for entry: HistoryEntry) -> String { entry.preferredThumbURL }
    func downloadName(for entry: HistoryEntry) -> String { entry.downloadName.isEmpty ? "banana-pro-image" : entry.downloadName }

    func currentServerURL() -> String {
        baseURL.absoluteString
    }

    func currentSessionCookie() async -> String? {
        await sessionStore.cookieHeader()
    }

    private func request<T: Decodable>(
        _ path: String,
        method: String,
        authenticated: Bool,
        body: Data? = nil,
        contentType: String? = nil,
        as type: T.Type
    ) async throws -> T {
        var request = try makeRequest(path: path)
        request.httpMethod = method
        request.httpBody = body
        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        if authenticated, let cookie = await sessionStore.cookieHeader() {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }
        let (data, response) = try await urlSession.data(for: request)
        return try decodeResponse(T.self, data: data, response: response)
    }

    private func decodeResponse<T: Decodable>(_ type: T.Type, data: Data, response: URLResponse) throws -> T {
        guard let http = response as? HTTPURLResponse else {
            throw AppError.invalidResponse
        }
        if http.statusCode == 401 {
            throw AppError.unauthorized
        }
        guard 200...299 ~= http.statusCode else {
            if let errorResponse = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let error = errorResponse["error"] as? String {
                throw AppError.message(error)
            }
            throw AppError.message("请求失败：HTTP \(http.statusCode)")
        }
        if T.self == EmptyResponse.self {
            return EmptyResponse() as! T
        }
        return try decoder.decode(T.self, from: data)
    }

    private func makeRequest(path: String) throws -> URLRequest {
        guard let url = urlForPath(path) else {
            throw AppError.invalidServerURL
        }
        var request = URLRequest(url: url)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func makeExternalRequest(urlString: String) async throws -> URLRequest {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = resolvedURL(from: trimmed) else { throw AppError.message("无效的下载地址") }
        var request = URLRequest(url: url)
        if let cookie = await sessionStore.cookieHeader() {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }
        return request
    }

    private static func normalizeServerURL(_ value: String) throws -> URL {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw AppError.invalidServerURL }
        let normalized = trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") ? trimmed : "http://\(trimmed)"
        guard let url = URL(string: normalized) else { throw AppError.invalidServerURL }
        return url
    }

    private static func extractSessionToken(from cookieHeader: String) -> String? {
        let parts = cookieHeader.split(separator: ";")
        for part in parts {
            let pair = part.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard pair.count == 2 else { continue }
            let key = pair[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let value = pair[1].trimmingCharacters(in: .whitespacesAndNewlines)
            if key == "banana_ui_session", !value.isEmpty {
                return value
            }
        }
        return nil
    }

    private func urlForPath(_ path: String) -> URL? {
        let base = baseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let trimmedPath = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return URL(string: "\(base)/\(trimmedPath)")
    }

    private func resolvedURL(from value: String) -> URL? {
        if let absolute = URL(string: value), absolute.scheme != nil {
            return absolute
        }
        let base = baseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let path = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !path.isEmpty else { return nil }
        let normalized = path.hasPrefix("/") ? String(path.dropFirst()) : path
        return URL(string: "\(base)/\(normalized)")
    }
}

struct EmptyResponse: Codable {}

struct PersonaSaveResponse: Decodable {
    let item: PersonaDetail
}

struct OptimizePromptResponse: Decodable {
    let prompt: String
    let model: String?
    let personaId: String?
    let personaName: String?
}

private extension String {
    func urlEncodedPathComponent() -> String {
        addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? self
    }
}
