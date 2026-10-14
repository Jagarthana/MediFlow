package com.mediflow.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mediflow.app.data.AppGraph
import java.net.URI
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val MAX_EDGE_PX = 1800

/** A phone photo decoded at full size is far more than a review pane needs, and six of
 *  them at once is enough to matter on a low-RAM device. */
private val slipCache = object : LruCache<String, Bitmap>(6) {}

/**
 * The slip the patient actually photographed, read from Storage at the path the reservation
 * carries. Nothing here is invented: no path, no image — the pharmacist sees the same absence
 * the patient recorded.
 */
@Composable
fun SlipImage(
    path: String?,
    modifier: Modifier = Modifier,
    label: String = "Prescription slip"
) {
    var bitmap by remember(path) { mutableStateOf(path?.let { slipCache.get(it) }) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val key = path ?: return@LaunchedEffect
        if (bitmap != null) return@LaunchedEffect
        runCatching { loadSlip(key) }
            .onSuccess { if (isActive) { slipCache.put(key, it); bitmap = it } }
            .onFailure { if (isActive) failed = true }
    }

    val shown = bitmap
    Box(
        modifier = modifier.semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        when {
            shown != null -> Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            else -> Text(
                text = when {
                    path == null -> "No slip was attached to this reservation."
                    failed -> "Could not load the slip from storage."
                    else -> "Loading the slip…"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private suspend fun loadSlip(path: String): Bitmap {
    val url = AppGraph.slips.downloadUrl(path).getOrThrow()
    val bytes = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
    return decodeCapped(bytes) ?: throw IllegalStateException("The stored file is not a readable image.")
}

private fun decodeCapped(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / (sample * 2) >= MAX_EDGE_PX) sample *= 2
    return BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )
}
