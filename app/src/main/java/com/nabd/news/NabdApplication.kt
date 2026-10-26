package com.nabd.news

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class NabdApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Network().client.newBuilder().followRedirects(true).followSslRedirects(false).build())
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.18).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("article-images")).maxSizeBytes(96L * 1024 * 1024).build() }
        .crossfade(180).build()
}
