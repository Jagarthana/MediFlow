package com.mediflow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A drawn stand-in for the counter QR code.
 *
 * The real code is generated server-side from the reservation reference, so the
 * module pattern here is derived deterministically from that same reference —
 * the picture changes when the reservation changes, which is what makes it
 * usable for layout and scanning-distance review before the backend exists.
 */
private const val MODULES = 25
private const val FINDER = 7

@Composable
fun QrCodeArt(reference: String, modifier: Modifier = Modifier) {
    val modules = remember(reference) { buildModules(reference) }
    Surface(
        modifier = modifier.semantics { contentDescription = "Counter QR code for $reference" },
        shape = RoundedCornerShape(14.dp),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / MODULES
                for (row in 0 until MODULES) {
                    for (col in 0 until MODULES) {
                        if (!modules[row][col]) continue
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(col * cell, row * cell),
                            size = Size(cell + 0.5f, cell + 0.5f)
                        )
                    }
                }
                listOf(
                    Offset(0f, 0f),
                    Offset((MODULES - FINDER) * cell, 0f),
                    Offset(0f, (MODULES - FINDER) * cell)
                ).forEach { origin ->
                    drawRect(
                        color = Color.Black,
                        topLeft = origin,
                        size = Size(FINDER * cell, FINDER * cell)
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = origin + Offset(cell, cell),
                        size = Size((FINDER - 2) * cell, (FINDER - 2) * cell)
                    )
                    drawRect(
                        color = Color.Black,
                        topLeft = origin + Offset(2 * cell, 2 * cell),
                        size = Size((FINDER - 4) * cell, (FINDER - 4) * cell)
                    )
                }
            }
        }
    }
}

/**
 * Filled grid with the finder-pattern corners left blank, so the three
 * alignment squares can be painted over a quiet area instead of colliding with
 * random modules.
 */
private fun buildModules(reference: String): List<List<Boolean>> {
    val random = Random(reference.hashCode().toLong())
    return List(MODULES) { row ->
        List(MODULES) { col ->
            val inFinder =
                (row < FINDER + 1 && col < FINDER + 1) ||
                    (row < FINDER + 1 && col > MODULES - FINDER - 2) ||
                    (row > MODULES - FINDER - 2 && col < FINDER + 1)
            !inFinder && random.nextBoolean()
        }
    }
}
