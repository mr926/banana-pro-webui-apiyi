package com.bananalab.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.net.URLConnection

object DownloadHelper {
    fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        cookieHeader: String = "",
        mimeType: String? = null,
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("BananaLab 下载")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val guessedMime = mimeType?.takeIf { it.isNotBlank() } ?: URLConnection.guessContentTypeFromName(fileName)
        if (!guessedMime.isNullOrBlank()) {
            request.setMimeType(guessedMime)
        }

        if (cookieHeader.isNotBlank()) {
            request.addRequestHeader("Cookie", cookieHeader)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}

