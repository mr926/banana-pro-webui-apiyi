package com.bananalab.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import java.io.File
import okio.Path.Companion.toPath

class BananaLabApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        GenerationNotificationManager.ensureChannel(this)
        SingletonImageLoader.setSafe(this)
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "banana_lab_image_cache").absolutePath.toPath())
                    .build()
            }
            .build()
    }
}
