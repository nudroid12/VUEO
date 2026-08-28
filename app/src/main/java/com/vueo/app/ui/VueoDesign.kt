package com.vueo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.vueo.app.core.storage.AppAccent

internal object VueoPalette {
    val Background =
        Color(0xFF080B0F)

    val Nav =
        Color(0xFF0A0E12)

    val Surface =
        Color(0xFF0F141A)

    val SurfaceElevated =
        Color(0xFF151B22)

    val SurfaceStrong =
        Color(0xFF1B232C)

    val Stroke =
        Color(0xFF26313B)

    val Muted =
        Color(0xFF98A2AD)

    val BrandLime =
        Color(0xFF8CE66A)

    private var accentState by
        mutableStateOf(
            Color(AppAccent.WHITE.argb)
        )

    val Accent: Color
        get() = accentState

    fun applyAccent(
        accent: AppAccent,
    ) {
        val next = Color(accent.argb)

        if (accentState != next) {
            accentState = next
        }
    }

    val Success =
        Color(0xFF6FE88A)

    val Warning =
        Color(0xFFFFCA6A)
}

internal fun AppAccent.composeColor(): Color =
    Color(argb)

@Composable
internal fun VueoBrandMark(
    modifier: Modifier = Modifier,
    color: Color = VueoPalette.BrandLime,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val leftRibbon = Path().apply {
            moveTo(w * .12f, h * .16f)
            lineTo(w * .32f, h * .16f)
            lineTo(w * .52f, h * .62f)
            lineTo(w * .41f, h * .84f)
            close()
        }

        val playRibbon = Path().apply {
            moveTo(w * .45f, h * .14f)
            lineTo(w * .92f, h * .50f)
            lineTo(w * .45f, h * .86f)
            lineTo(w * .58f, h * .62f)
            lineTo(w * .74f, h * .50f)
            lineTo(w * .58f, h * .38f)
            close()
        }

        drawPath(
            path = leftRibbon,
            color = color,
        )
        drawPath(
            path = playRibbon,
            color = color,
        )

    }
}
