package com.mcclabs.mook.ui.loading

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Coil'in ortak bellek/disk önbelleğini kullanır; metinleri veya gezinmeyi bekletmez.
 * URL değişince yerel görsel durumu sıfırlanır. Kilitli fotoğrafların istekleri korunur.
 */
@Composable
fun ProgressiveAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    imageUrl: String? = model as? String,
    onImageLoadChanged: (String, ImageLoadStatus) -> Unit = { _, _ -> },
) {
    if (!progressiveLoadingEnabled) {
        AsyncImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
        return
    }
    key(model, imageUrl) {
        var status by remember { mutableStateOf(if (imageUrl.isNullOrBlank()) ImageLoadStatus.Error else ImageLoadStatus.Loading) }
        val callback by rememberUpdatedState(onImageLoadChanged)
        LaunchedEffect(imageUrl, status) {
            imageUrl?.takeIf { it.isNotBlank() }?.let { callback(it, status) }
        }
        Box(modifier) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onLoading = { status = ImageLoadStatus.Loading },
                onSuccess = { status = ImageLoadStatus.Success },
                onError = { status = ImageLoadStatus.Error },
            )
            ImageLoadingOverlay(status)
        }
    }
}

/** Görsel durumu ağ bağlantısı olmadan da arayüz testlerinde doğrulanabilir. */
@Composable
fun ImageLoadingOverlay(status: ImageLoadStatus, modifier: Modifier = Modifier) {
    when (status) {
        ImageLoadStatus.Loading -> ShimmerPlaceholder(modifier.fillMaxSize().testTag("imageShimmer"))
        ImageLoadStatus.Error -> Box(modifier.fillMaxSize().testTag("imageFallback"), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Person, contentDescription = "Fotoğraf görüntülenemiyor", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        ImageLoadStatus.Success -> Unit
    }
}
