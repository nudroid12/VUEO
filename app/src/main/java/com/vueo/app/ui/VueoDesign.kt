package com.vueo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.vueo.app.core.storage.AppAccent

internal object VueoPalette {
    val Background = Color(0xFF070A0D)
    val Nav = Color(0xFF0A0D11)
    val Surface = Color(0xFF101419)
    val SurfaceElevated = Color(0xFF161B20)
    val SurfaceStrong = Color(0xFF1D2329)
    val Stroke = Color(0xFF2A323A)
    val Muted = Color(0xFFA2AAB3)

    // VUEO brand identity. This never follows the user's interface accent.
    val BrandLime = Color(0xFF8CE66A)

    private var accentState by mutableStateOf(
        Color(AppAccent.WHITE.argb)
    )

    val Accent: Color
        get() = accentState

    fun applyAccent(accent: AppAccent) {
        val next = Color(accent.argb)
        if (accentState != next) {
            accentState = next
        }
    }

    val Success = Color(0xFF72DF87)
    val Warning = Color(0xFFFFB84D)
    val Error = Color(0xFFFF6767)
}

internal fun AppAccent.composeColor(): Color = Color(argb)

@Composable
internal fun VueoBrandMark(
    modifier: Modifier = Modifier,
    color: Color = VueoPalette.BrandLime,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Approved v0.9.6 mark: a clean cinematic V made from two ribbons.
        val left = Path().apply {
            moveTo(w * .08f, h * .15f)
            lineTo(w * .39f, h * .15f)
            lineTo(w * .57f, h * .52f)
            lineTo(w * .36f, h * .88f)
            close()
        }

        val right = Path().apply {
            moveTo(w * .43f, h * .15f)
            lineTo(w * .91f, h * .15f)
            lineTo(w * .60f, h * .58f)
            close()
        }

        drawPath(left, color)
        drawPath(right, color)
    }
}

@Composable
internal fun VueoBrandLockup(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        VueoBrandMark(
            modifier = Modifier.size(
                if (compact) 28.dp else 34.dp
            )
        )
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        Text(
            "VUEO",
            color = VueoPalette.BrandLime,
            fontWeight = FontWeight.Black,
            fontSize = if (compact) 19.sp else 24.sp,
            letterSpacing = if (compact) 2.6.sp else 3.6.sp,
        )
    }
}
