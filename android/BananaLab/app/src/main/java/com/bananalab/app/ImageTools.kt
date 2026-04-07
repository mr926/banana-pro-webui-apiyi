package com.bananalab.app

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

private const val MB = 1024 * 1024
private const val BASE_MAX_BYTES = 4 * MB
private const val BASE_MAX_EDGE = 4000
private const val BASE_JPEG_QUALITY = 85
private const val REFERENCE_MAX_BYTES = 2 * MB
private val UPSTREAM_SAFE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

suspend fun createSelectedImage(
    context: Context,
    uri: Uri,
    isBaseImage: Boolean,
): SelectedImage = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val rawBytes = resolver.readBytes(uri)
    val displayName = resolver.displayName(uri) ?: "image.jpg"
    val mimeType = normalizeSourceMimeType(resolver.getType(uri), displayName, rawBytes)
    val decoded = decodeBitmap(rawBytes) ?: error("无法读取图片：$displayName")

    val processed = if (isBaseImage) {
        compressBaseIfNeeded(decoded, displayName, mimeType, rawBytes, rawBytes.size)
    } else {
        compressReferenceIfNeeded(decoded, displayName, mimeType, rawBytes, rawBytes.size)
    }

    SelectedImage(
        name = processed.name,
        mimeType = processed.mimeType,
        bytes = processed.bytes,
        sha256 = sha256Hex(processed.bytes),
        bitmapWidth = processed.width,
        bitmapHeight = processed.height,
        compressed = processed.compressed,
        preview = processed.bitmap.asImageBitmap(),
    )
}

suspend fun createSelectedImageFromBytes(
    bytes: ByteArray,
    preferredName: String,
    isBaseImage: Boolean,
): SelectedImage = withContext(Dispatchers.IO) {
    val mimeType = guessMimeType(preferredName, bytes)
    val decoded = decodeBitmap(bytes) ?: error("无法读取图片：$preferredName")
    val processed = if (isBaseImage) {
        compressBaseIfNeeded(decoded, preferredName, mimeType, bytes, bytes.size)
    } else {
        compressReferenceIfNeeded(decoded, preferredName, mimeType, bytes, bytes.size)
    }
    SelectedImage(
        name = processed.name,
        mimeType = processed.mimeType,
        bytes = processed.bytes,
        sha256 = sha256Hex(processed.bytes),
        bitmapWidth = processed.width,
        bitmapHeight = processed.height,
        compressed = processed.compressed,
        preview = processed.bitmap.asImageBitmap(),
    )
}

private data class ProcessedImage(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap,
    val compressed: Boolean,
)

private fun compressBaseIfNeeded(
    bitmap: Bitmap,
    displayName: String,
    mimeType: String,
    originalBytes: ByteArray,
    originalSize: Int,
): ProcessedImage {
    if (originalSize <= BASE_MAX_BYTES && mimeType in UPSTREAM_SAFE_MIME_TYPES) {
        return ProcessedImage(
            name = displayName,
            mimeType = mimeType.takeIf { it.startsWith("image/") } ?: "image/jpeg",
            bytes = originalBytes,
            width = bitmap.width,
            height = bitmap.height,
            bitmap = bitmap,
            compressed = false,
        )
    }

    val longEdge = maxOf(bitmap.width, bitmap.height)
    val scale = if (longEdge > BASE_MAX_EDGE) BASE_MAX_EDGE.toFloat() / longEdge else 1f
    val resized = scaleBitmap(bitmap, scale)
    return ProcessedImage(
        name = ensureJpgName(displayName),
        mimeType = "image/jpeg",
        bytes = bitmapToJpegBytes(resized, BASE_JPEG_QUALITY),
        width = resized.width,
        height = resized.height,
        bitmap = resized,
        compressed = true,
    )
}

private fun compressReferenceIfNeeded(
    bitmap: Bitmap,
    displayName: String,
    mimeType: String,
    originalBytes: ByteArray,
    originalSize: Int,
): ProcessedImage {
    if (originalSize <= REFERENCE_MAX_BYTES && mimeType in UPSTREAM_SAFE_MIME_TYPES) {
        return ProcessedImage(
            name = displayName,
            mimeType = mimeType.takeIf { it.startsWith("image/") } ?: "image/jpeg",
            bytes = originalBytes,
            width = bitmap.width,
            height = bitmap.height,
            bitmap = bitmap,
            compressed = false,
        )
    }

    val scales = listOf(1f, 0.92f, 0.84f, 0.76f, 0.68f, 0.6f)
    val qualities = listOf(85, 78, 72, 66, 60, 54, 48, 42)
    for (scale in scales) {
        val resized = scaleBitmap(bitmap, scale)
        for (quality in qualities) {
            val bytes = bitmapToJpegBytes(resized, quality)
            if (bytes.size <= REFERENCE_MAX_BYTES) {
                return ProcessedImage(
                    name = ensureJpgName(displayName),
                    mimeType = "image/jpeg",
                    bytes = bytes,
                    width = resized.width,
                    height = resized.height,
                    bitmap = resized,
                    compressed = true,
                )
            }
        }
    }

    error("参考图 ${displayName} 压缩后仍超过 2MB，请换一张图片再试。")
}

private fun scaleBitmap(bitmap: Bitmap, scale: Float): Bitmap {
    if (scale >= 0.999f) return bitmap
    val width = maxOf(1, (bitmap.width * scale).toInt())
    val height = maxOf(1, (bitmap.height * scale).toInt())
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
    return output.toByteArray()
}

private fun decodeBitmap(bytes: ByteArray): Bitmap? {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun ensureJpgName(name: String): String {
    val trimmed = name.trim().ifBlank { "image.jpg" }
    val stem = trimmed.replace(Regex("\\.[^.]+$"), "").ifBlank { "image" }
    return "$stem.jpg"
}

private fun normalizeSourceMimeType(
    providedMimeType: String?,
    name: String,
    bytes: ByteArray,
): String {
    val normalizedProvided = providedMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    return when {
        normalizedProvided.isBlank() -> guessMimeType(name, bytes)
        normalizedProvided == "application/octet-stream" -> guessMimeType(name, bytes)
        !normalizedProvided.startsWith("image/") -> guessMimeType(name, bytes)
        normalizedProvided.endsWith("/*") -> guessMimeType(name, bytes)
        else -> normalizedProvided
    }
}

private fun guessMimeType(name: String, bytes: ByteArray): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
        bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47.toByte())) -> "image/png"
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) -> "image/jpeg"
        bytes.startsWith("RIFF".encodeToByteArray()) && bytes.size >= 12 && bytes.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray()) -> "image/webp"
        else -> "image/jpeg"
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun ContentResolver.readBytes(uri: Uri): ByteArray {
    openInputStream(uri).use { input ->
        if (input == null) error("无法打开图片文件。")
        return input.readBytes()
    }
}

private fun ContentResolver.displayName(uri: Uri): String? {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }
    return uri.lastPathSegment
}
