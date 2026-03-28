import Foundation
import PhotosUI
import UniformTypeIdentifiers
import UIKit
import SwiftUI

enum ImageRole {
    case base
    case reference
}

enum ImagePreparation {
    static func loadUploadImage(
        from item: PhotosPickerItem,
        role: ImageRole
    ) async throws -> UploadImage {
        guard let data = try await item.loadTransferable(type: Data.self) else {
            throw AppError.message("无法读取所选图片")
        }

        let preferredExtension = item.supportedContentTypes.first?.preferredFilenameExtension
        let mimeType = item.supportedContentTypes.first?.preferredMIMEType ?? "image/jpeg"
        let filename = "photo.\(preferredExtension ?? "jpg")"
        return try prepareUploadImage(
            data: data,
            filename: filename,
            mimeType: mimeType,
            role: role
        )
    }

    static func prepareUploadImage(
        data: Data,
        filename: String,
        mimeType: String,
        role: ImageRole
    ) throws -> UploadImage {
        guard let sourceImage = UIImage(data: data) else {
            throw AppError.message("无法解析图片")
        }

        let originalSize = sourceImage.size
        let pixelSize = CGSize(width: sourceImage.size.width * sourceImage.scale, height: sourceImage.size.height * sourceImage.scale)
        let limitBytes = role == .base ? 4_000_000 : 2_000_000
        let maxEdge: CGFloat = role == .base ? 4000 : 3000

        if data.count <= limitBytes, max(originalSize.width, originalSize.height) <= maxEdge {
            return UploadImage(
                filename: filename,
                mimeType: mimeType,
                data: data,
                pixelSize: pixelSize,
                compressed: false,
                previewImage: sourceImage
            )
        }

        let targetData = try compress(image: sourceImage, maxEdge: maxEdge, targetBytes: limitBytes)
        guard let preview = UIImage(data: targetData) else {
            throw AppError.message("图片压缩失败")
        }
        return UploadImage(
            filename: filename.hasSuffix(".jpg") || filename.hasSuffix(".jpeg") ? filename : (filename as NSString).deletingPathExtension + ".jpg",
            mimeType: "image/jpeg",
            data: targetData,
            pixelSize: CGSize(width: preview.size.width * preview.scale, height: preview.size.height * preview.scale),
            compressed: true,
            previewImage: preview
        )
    }

    private static func compress(image: UIImage, maxEdge: CGFloat, targetBytes: Int) throws -> Data {
        let resized = resizedImage(image: image, maxEdge: maxEdge) ?? image
        var quality: CGFloat = 0.85
        var output = resized.jpegData(compressionQuality: quality) ?? Data()

        while output.count > targetBytes && quality > 0.45 {
            quality -= 0.1
            output = resized.jpegData(compressionQuality: quality) ?? Data()
        }

        guard !output.isEmpty else {
            throw AppError.message("压缩结果为空")
        }
        return output
    }

    private static func resizedImage(image: UIImage, maxEdge: CGFloat) -> UIImage? {
        let size = image.size
        let longestEdge = max(size.width, size.height)
        guard longestEdge > maxEdge else { return nil }

        let scale = maxEdge / longestEdge
        let targetSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }
}
