import CryptoKit
import Foundation
import UIKit

actor RemoteImageCache {
    static let shared = RemoteImageCache()

    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private let urlSession: URLSession

    init() {
        let base = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
        cacheDirectory = base.appendingPathComponent("BananaLabImageCache", isDirectory: true)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)

        let configuration = URLSessionConfiguration.default
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.urlCache = URLCache(memoryCapacity: 32 * 1024 * 1024, diskCapacity: 256 * 1024 * 1024, diskPath: "BananaLabRemoteImages")
        urlSession = URLSession(configuration: configuration)
    }

    func image(for urlString: String) async throws -> UIImage {
        let normalized = normalizedURLString(urlString)
        if normalized.isEmpty {
            throw AppError.message("图片地址为空")
        }

        if let cached = memoryCache.object(forKey: normalized as NSString) {
            return cached
        }

        let fileURL = cacheFileURL(for: normalized)
        if let data = try? Data(contentsOf: fileURL), let image = UIImage(data: data) {
            memoryCache.setObject(image, forKey: normalized as NSString)
            return image
        }

        let data = try await downloadData(urlString: normalized)
        try? data.write(to: fileURL, options: .atomic)
        guard let image = UIImage(data: data) else {
            throw AppError.message("无法解析图片")
        }
        memoryCache.setObject(image, forKey: normalized as NSString)
        return image
    }

    func downloadData(urlString: String) async throws -> Data {
        guard let url = resolvedURL(from: urlString) else {
            throw AppError.message("无效的图片地址")
        }
        var request = URLRequest(url: url)
        if let cookie = await SessionStore.shared.cookieHeader() {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }
        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse, 200...299 ~= http.statusCode else {
            throw AppError.message("图片加载失败")
        }
        return data
    }

    func clear() {
        memoryCache.removeAllObjects()
        try? fileManager.removeItem(at: cacheDirectory)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
    }

    private func cacheFileURL(for urlString: String) -> URL {
        let digest = SHA256.hash(data: Data(urlString.utf8))
        let filename = digest.compactMap { String(format: "%02x", $0) }.joined()
        return cacheDirectory.appendingPathComponent(filename)
    }

    private func normalizedURLString(_ value: String) -> String {
        resolvedURL(from: value)?.absoluteString ?? value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func resolvedURL(from value: String) -> URL? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if let absolute = URL(string: trimmed), absolute.scheme != nil {
            return absolute
        }
        let baseURL = UserDefaults.standard.string(forKey: "banana-lab.server-url") ?? "http://127.0.0.1:8787"
        let base = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let path = trimmed.hasPrefix("/") ? String(trimmed.dropFirst()) : trimmed
        guard !path.isEmpty else { return nil }
        return URL(string: "\(base)/\(path)")
    }
}
