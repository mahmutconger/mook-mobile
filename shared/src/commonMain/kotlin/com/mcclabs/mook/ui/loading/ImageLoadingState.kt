package com.mcclabs.mook.ui.loading

import com.mcclabs.mook.getPlatform

/** Ortak ekranlarda yeni yükleme deneyimi yalnızca Android için etkinleştirilir. */
val progressiveLoadingEnabled: Boolean
    get() = getPlatform().name.startsWith("Android")

enum class ImageLoadStatus { Loading, Success, Error }

enum class ContentLoadingPhase { Loading, ImagesLoading, Success, Error }

/**
 * Görsel baytları arayüzde Coil tarafından yüklenir; ViewModel yalnızca sonuçları tutar.
 * Görünmeyen satırlar ve galeri sayfaları önceden indirilmez, görünür olunca yüklenir.
 * Hatalı görseller ayrı tutulur; sonsuz parıltı yerine yer tutucu gösterilir.
 */
data class ImageLoadingState(val statuses: Map<String, ImageLoadStatus> = emptyMap()) {
    val imagesLoading: Boolean get() = statuses.values.any { it == ImageLoadStatus.Loading }
    val failedUrls: Set<String> get() = statuses.filterValues { it == ImageLoadStatus.Error }.keys

    fun reconcile(urls: Iterable<String?>): ImageLoadingState {
        if (!progressiveLoadingEnabled) return this
        val keys = urls.filterNotNull().filter { it.isNotBlank() }.toSet()
        return ImageLoadingState(keys.associateWith { statuses[it] ?: ImageLoadStatus.Loading })
    }

    fun onResult(url: String, status: ImageLoadStatus): ImageLoadingState {
        if (url !in statuses) return this
        // Aynı URL başka bir bileşende de kullanılıyorsa başarı durumu geriye düşmez.
        if (statuses[url] == ImageLoadStatus.Success) return this
        return copy(statuses = statuses + (url to status))
    }

    fun phase(isLoading: Boolean, hasError: Boolean): ContentLoadingPhase = when {
        isLoading -> ContentLoadingPhase.Loading
        hasError -> ContentLoadingPhase.Error
        imagesLoading -> ContentLoadingPhase.ImagesLoading
        else -> ContentLoadingPhase.Success
    }
}
