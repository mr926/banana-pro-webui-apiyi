package com.bananalab.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiHttpException(
    val code: Int,
    override val message: String,
) : IOException(message)

class BananaLabApi(
    serverUrl: String,
) {
    private val cookieJar = SessionCookieJar()
    private val imageCache = object : LruCache<String, ImageBitmap>(12) {}

    @Volatile
    private var baseUrl: HttpUrl = normalizeServerUrl(serverUrl)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(480, TimeUnit.SECONDS)
        .writeTimeout(480, TimeUnit.SECONDS)
        .build()

    fun updateServerUrl(serverUrl: String) {
        baseUrl = normalizeServerUrl(serverUrl)
        clearSession()
    }

    fun clearSession() {
        cookieJar.clear()
    }

    fun exportSessionCookies(): String = cookieJar.serialize()

    fun restoreSessionCookies(serializedCookies: String) {
        cookieJar.restore(serializedCookies, baseUrl)
    }

    fun cookieHeaderFor(url: String): String {
        val httpUrl = resolveUrl(url) ?: return ""
        return cookieJar.cookiesFor(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
    }

    suspend fun authStatus(): AuthStatus = withContext(Dispatchers.IO) {
        val json = getJson("api/auth/status")
        AuthStatus(
            authenticated = json.optBoolean("authenticated", false),
            passwordEnabled = json.optBoolean("passwordEnabled", false),
        )
    }

    suspend fun login(password: String): AuthStatus = withContext(Dispatchers.IO) {
        val body = JSONObject().put("password", password).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = requestFor("api/auth/login").post(body).build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            AuthStatus(
                authenticated = json.optBoolean("authenticated", true),
                passwordEnabled = json.optBoolean("passwordEnabled", true),
            )
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val request = requestFor("api/auth/logout").post(FormBody.Builder().build()).build()
        client.newCall(request).execute().use { response ->
            response.throwIfNeeded()
        }
        clearSession()
    }

    suspend fun fetchApiPlatforms(): ApiPlatformConfig = withContext(Dispatchers.IO) {
        val json = getJson("api/image-platforms")
        ApiPlatformConfig(
            items = json.optJSONArray("items")?.toApiPlatformList().orEmpty(),
            defaultPlatformId = json.optString("defaultPlatformId").orEmpty(),
            defaultImageModel = json.optString("defaultImageModel").orEmpty(),
        )
    }

    suspend fun fetchHistory(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        val json = getJson("api/history")
        json.optJSONArray("items")?.toHistoryList().orEmpty()
    }

    suspend fun deleteHistory(id: String) = withContext(Dispatchers.IO) {
        val request = requestFor("api/history/$id").delete().build()
        client.newCall(request).execute().use { response ->
            response.throwIfNeeded()
        }
    }

    suspend fun fetchDownloadTargets(ids: List<String>): List<DownloadTarget> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("ids", JSONArray(ids))
        val request = requestFor("api/history/download-links")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            json.optJSONArray("items")?.let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val url = item.optString("url").orEmpty()
                        if (url.isBlank()) continue
                        add(
                            DownloadTarget(
                                id = item.optString("id").orEmpty(),
                                url = url,
                                downloadName = item.optString("downloadName").ifBlank { "banana-pro-image" },
                                source = item.optString("source").ifBlank { "local" },
                            ),
                        )
                    }
                }
            }.orEmpty()
        }
    }

    suspend fun fetchPromptLibrary(): PromptLibraryState = withContext(Dispatchers.IO) {
        val json = getJson("api/prompt-library")
        val content = json.optString("content").orEmpty()
        val items = json.optJSONArray("items")?.toStringList().orEmpty()
        PromptLibraryState(content = content, items = items)
    }

    suspend fun savePromptLibrary(content: String): PromptLibraryState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("content", content)
        val request = requestFor("api/prompt-library")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            PromptLibraryState(
                content = json.optString("content").orEmpty(),
                items = json.optJSONArray("items")?.toStringList().orEmpty(),
            )
        }
    }

    suspend fun fetchPersonas(): List<PersonaSummary> = withContext(Dispatchers.IO) {
        val json = getJson("api/prompt-personas")
        json.optJSONArray("items")?.toPersonaSummaryList().orEmpty()
    }

    suspend fun fetchPersona(personaId: String): PersonaDetail = withContext(Dispatchers.IO) {
        val json = getJson("api/prompt-personas/${encodePath(personaId)}")
        json.toPersonaDetail()
    }

    suspend fun createPersona(payload: PersonaDetail): PersonaDetail = withContext(Dispatchers.IO) {
        val request = requestFor("api/prompt-personas")
            .post(payload.toJson().toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            json.optJSONObject("item")?.toPersonaDetail()
                ?: error("服务端没有返回人设内容")
        }
    }

    suspend fun updatePersona(personaId: String, payload: PersonaDetail): PersonaDetail = withContext(Dispatchers.IO) {
        val request = requestFor("api/prompt-personas/${encodePath(personaId)}")
            .put(payload.toJson().toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            json.optJSONObject("item")?.toPersonaDetail()
                ?: error("服务端没有返回人设内容")
        }
    }

    suspend fun deletePersona(personaId: String) = withContext(Dispatchers.IO) {
        val request = requestFor("api/prompt-personas/${encodePath(personaId)}").delete().build()
        client.newCall(request).execute().use { response ->
            response.throwIfNeeded()
        }
    }

    suspend fun optimizePrompt(prompt: String, personaId: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("prompt", prompt)
            .put("personaId", personaId)
        val request = requestFor("api/optimize-prompt")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            json
        }
    }

    suspend fun generate(
        apiPlatformId: String,
        imageModel: String,
        prompt: String,
        sourcePrompt: String,
        promptMode: String,
        aspectRatio: String,
        imageSize: String,
        enableSearch: Boolean,
        baseImage: SelectedImage,
        referenceImages: List<SelectedImage>,
    ): HistoryEntry = withContext(Dispatchers.IO) {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        multipart.addFormDataPart("apiPlatformId", apiPlatformId)
        multipart.addFormDataPart("imageModel", imageModel)
        multipart.addFormDataPart("prompt", prompt)
        multipart.addFormDataPart("sourcePrompt", sourcePrompt)
        multipart.addFormDataPart("promptMode", promptMode)
        multipart.addFormDataPart("aspectRatio", aspectRatio)
        multipart.addFormDataPart("imageSize", imageSize)
        multipart.addFormDataPart("enableSearch", enableSearch.toString())
        multipart.addFormDataPart(
            "baseImage",
            baseImage.name,
            baseImage.bytes.toRequestBody(baseImage.mimeType.toMediaType()),
        )
        referenceImages.forEachIndexed { index, image ->
            multipart.addFormDataPart(
                "referenceImages",
                image.name.ifBlank { "reference-${index + 1}.jpg" },
                image.bytes.toRequestBody(image.mimeType.toMediaType()),
            )
        }
        val request = requestFor("api/generate").post(multipart.build()).build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            json.toHistoryEntry()
        }
    }

    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(resolveUrl(url) ?: error("无效的下载地址：$url"))
        val cookieHeader = cookieHeaderFor(url)
        if (cookieHeader.isNotBlank()) {
            requestBuilder.header("Cookie", cookieHeader)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                error("下载失败：HTTP ${response.code} ${text.take(120)}")
            }
            response.body?.bytes() ?: error("下载失败：空响应")
        }
    }

    suspend fun loadBitmap(url: String): ImageBitmap = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        imageCache.get(normalized)?.let { return@withContext it }
        val bytes = downloadBytes(normalized)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("无法解码图片")
        val image = bitmap.asImageBitmap()
        imageCache.put(normalized, image)
        image
    }

    suspend fun resolveDownloadTargets(ids: List<String>): List<DownloadTarget> = fetchDownloadTargets(ids)

    suspend fun requestHistoryEntryImage(entry: HistoryEntry): ByteArray = downloadBytes(preferredImageUrl(entry))

    fun preferredImageUrl(entry: HistoryEntry): String = (entry.ossImageUrl ?: entry.imageUrl).trim()

    fun preferredThumbUrl(entry: HistoryEntry): String = (entry.ossThumbUrl ?: entry.thumbUrl).trim()

    fun downloadName(entry: HistoryEntry): String = entry.downloadName.ifBlank { "banana-pro-image" }

    fun absoluteUrl(urlOrPath: String): String {
        return resolveUrl(urlOrPath)?.toString() ?: urlOrPath.trim()
    }

    fun baseUrlString(): String = baseUrl.toString().trimEnd('/')

    fun cookieHeaderForCurrentServer(): String = cookieHeaderFor(baseUrlString())

    private fun requestFor(path: String): Request.Builder {
        val url = resolveUrl(path) ?: error("无效的服务器地址：${baseUrl}")
        return Request.Builder().url(url)
    }

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = requestFor(path).get().build()
        client.newCall(request).execute().use { response ->
            val json = response.toJsonObject()
            checkResponse(response, json)
            json
        }
    }

    private fun resolveUrl(urlOrPath: String): HttpUrl? {
        val trimmed = urlOrPath.trim()
        if (trimmed.isBlank()) return null
        val parsed = trimmed.toHttpUrlOrNull()
        if (parsed != null) return parsed
        return baseUrl.resolve(trimmed.removePrefix("/"))
    }

    private fun checkResponse(response: okhttp3.Response, json: JSONObject) {
        if (response.isSuccessful) return
        val error = json.optString("error").ifBlank { "请求失败：HTTP ${response.code}" }
        val details = json.optString("details").ifBlank { "" }
        throw ApiHttpException(
            code = response.code,
            message = if (details.isBlank()) error else "$error $details",
        )
    }

    private fun okhttp3.Response.toJsonObject(): JSONObject {
        val text = body?.string().orEmpty()
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun okhttp3.Response.throwIfNeeded() {
        if (isSuccessful) return
        val text = body?.string().orEmpty()
        throw ApiHttpException(
            code = code,
            message = text.ifBlank { "请求失败：HTTP $code" },
        )
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            val value = optString(i).trim()
            if (value.isNotBlank()) add(value)
        }
    }

    private fun JSONArray.toHistoryList(): List<HistoryEntry> = buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            add(item.toHistoryEntry())
        }
    }

    private fun JSONArray.toApiPlatformList(): List<ApiPlatformOption> = buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val models = item.optJSONArray("models")?.toStringList().orEmpty()
            val id = item.optString("id").orEmpty().trim()
            if (id.isBlank() || models.isEmpty()) continue
            add(
                ApiPlatformOption(
                    id = id,
                    name = item.optString("name").orEmpty().ifBlank { "未命名平台" },
                    models = models,
                    defaultModel = item.optString("defaultModel").orEmpty(),
                ),
            )
        }
    }

    private fun JSONArray.toPersonaSummaryList(): List<PersonaSummary> = buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            add(
                PersonaSummary(
                    id = item.optString("id").orEmpty(),
                    name = item.optString("name").orEmpty(),
                    summary = item.optString("summary").orEmpty(),
                    filename = item.optString("filename").orEmpty(),
                ),
            )
        }
    }

    private fun JSONObject.toHistoryEntry(): HistoryEntry {
        return HistoryEntry(
            id = optString("id").orEmpty(),
            createdAt = optString("createdAt").orEmpty(),
            prompt = optString("prompt").orEmpty(),
            sourcePrompt = optString("sourcePrompt").orEmpty(),
            promptMode = optString("promptMode").orEmpty(),
            aspectRatio = optString("aspectRatio").orEmpty(),
            imageSize = optString("imageSize").orEmpty(),
            enableSearch = optBoolean("enableSearch", false),
            baseImageName = optString("baseImageName").orEmpty(),
            referenceCount = optInt("referenceCount", 0),
            imageUrl = optString("imageUrl").orEmpty(),
            thumbUrl = optString("thumbUrl").orEmpty(),
            downloadName = optString("downloadName").orEmpty(),
            message = optString("message").takeIf { it.isNotBlank() },
            apiPlatformId = optString("apiPlatformId").orEmpty(),
            apiPlatformName = optString("apiPlatformName").orEmpty(),
            imageModel = optString("imageModel").orEmpty(),
            ossImageUrl = optString("ossImageUrl").takeIf { it.isNotBlank() },
            ossThumbUrl = optString("ossThumbUrl").takeIf { it.isNotBlank() },
            ossImageKey = optString("ossImageKey").takeIf { it.isNotBlank() },
            ossThumbKey = optString("ossThumbKey").takeIf { it.isNotBlank() },
            ossMetadataXmlUrl = optString("ossMetadataXmlUrl").takeIf { it.isNotBlank() },
            ossMetadataXmlKey = optString("ossMetadataXmlKey").takeIf { it.isNotBlank() },
            ossUploadError = optString("ossUploadError").takeIf { it.isNotBlank() },
        )
    }

    private fun JSONObject.toPersonaDetail(): PersonaDetail {
        return PersonaDetail(
            id = optString("id").orEmpty(),
            name = optString("name").orEmpty(),
            summary = optString("summary").orEmpty(),
            content = optString("content").orEmpty(),
            filename = optString("filename").orEmpty(),
        )
    }

    private fun PersonaDetail.toJson(): JSONObject {
        return JSONObject()
            .put("filename", filename)
            .put("name", name)
            .put("summary", summary)
            .put("content", content)
    }

    private fun normalizeServerUrl(serverUrl: String): HttpUrl {
        var normalized = serverUrl.trim()
        if (normalized.isBlank()) {
            normalized = AppPreferences.DEFAULT_SERVER_URL
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }
        return normalized.toHttpUrlOrNull() ?: error("无效的服务器地址：$serverUrl")
    }

    private fun encodePath(value: String): String = Uri.encode(value)

    private class SessionCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { existing ->
                cookies.any { it.name == existing.name && it.domain == existing.domain && it.path == existing.path }
            }
            this.cookies.addAll(cookies)
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }

        @Synchronized
        fun clear() {
            cookies.clear()
        }

        @Synchronized
        fun cookiesFor(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }

        @Synchronized
        fun serialize(): String {
            if (cookies.isEmpty()) return ""
            return JSONArray(cookies.map { it.toString() }).toString()
        }

        @Synchronized
        fun restore(serializedCookies: String, url: HttpUrl) {
            cookies.clear()
            val trimmed = serializedCookies.trim()
            if (trimmed.isBlank()) return
            val array = runCatching { JSONArray(trimmed) }.getOrNull() ?: return
            for (index in 0 until array.length()) {
                val cookieString = array.optString(index).trim()
                if (cookieString.isBlank()) continue
                val cookie = Cookie.parse(url, cookieString) ?: continue
                cookies.add(cookie)
            }
        }
    }
}
