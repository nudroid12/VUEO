package com.vueo.app.ui

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import java.util.function.Consumer

private const val PIP_ACTION_TOGGLE =
    "com.vueo.app.action.PIP_TOGGLE_PLAYBACK"
private const val PIP_ACTION_NEXT =
    "com.vueo.app.action.PIP_NEXT_EPISODE"
private const val PIP_REQUEST_TOGGLE = 701
private const val PIP_REQUEST_NEXT = 702

@Composable
internal fun VueoPictureInPictureEffect(
    activity: Activity?,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    onTogglePlayback: () -> Unit,
    onNextEpisode: () -> Unit,
    onModeChanged: (Boolean) -> Unit,
) {
    val latestTogglePlayback =
        rememberUpdatedState(onTogglePlayback)
    val latestNextEpisode =
        rememberUpdatedState(onNextEpisode)
    val latestModeChanged =
        rememberUpdatedState(onModeChanged)

    DisposableEffect(activity) {
        if (
            activity == null ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        ) {
            return@DisposableEffect onDispose { }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    PIP_ACTION_TOGGLE ->
                        latestTogglePlayback.value.invoke()
                    PIP_ACTION_NEXT -> {
                        if (activity.isInPictureInPictureMode) {
                            runCatching {
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        activity.javaClass,
                                    ).addFlags(
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                )
                            }
                        }
                        latestNextEpisode.value.invoke()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(PIP_ACTION_TOGGLE)
            addAction(PIP_ACTION_NEXT)
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val componentActivity = activity as? ComponentActivity
        val modeListener =
            if (
                componentActivity != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ) {
                Consumer<PictureInPictureModeChangedInfo> { info ->
                    latestModeChanged.value.invoke(
                        info.isInPictureInPictureMode
                    )
                }
            } else {
                null
            }
        modeListener?.let {
            componentActivity
                ?.addOnPictureInPictureModeChangedListener(it)
        }

        onDispose {
            runCatching {
                activity.unregisterReceiver(receiver)
            }
            modeListener?.let {
                componentActivity
                    ?.removeOnPictureInPictureModeChangedListener(it)
            }
            if (!activity.isInPictureInPictureMode) {
                setVueoPictureInPictureParams(
                    activity = activity,
                    isPlaying = false,
                    hasNextEpisode = false,
                    autoEnterEnabled = false,
                )
            }
        }
    }

    LaunchedEffect(
        activity,
        isPlaying,
        hasNextEpisode,
    ) {
        if (
            activity != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            setVueoPictureInPictureParams(
                activity = activity,
                isPlaying = isPlaying,
                hasNextEpisode = hasNextEpisode,
                autoEnterEnabled = isPlaying,
            )
        }
    }
}

internal fun enterVueoPictureInPicture(
    activity: Activity?,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
): Boolean {
    if (
        activity == null ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        !activity.packageManager.hasSystemFeature(
            PackageManager.FEATURE_PICTURE_IN_PICTURE
        ) ||
        activity.isFinishing ||
        activity.isDestroyed ||
        activity.isInPictureInPictureMode
    ) {
        return false
    }

    return runCatching {
        activity.enterPictureInPictureMode(
            buildVueoPictureInPictureParams(
                activity = activity,
                isPlaying = isPlaying,
                hasNextEpisode = hasNextEpisode,
                autoEnterEnabled = isPlaying,
            )
        )
    }.getOrDefault(false)
}

private fun setVueoPictureInPictureParams(
    activity: Activity,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    autoEnterEnabled: Boolean,
) {
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        !activity.packageManager.hasSystemFeature(
            PackageManager.FEATURE_PICTURE_IN_PICTURE
        ) ||
        activity.isFinishing ||
        activity.isDestroyed
    ) {
        return
    }

    runCatching {
        activity.setPictureInPictureParams(
            buildVueoPictureInPictureParams(
                activity = activity,
                isPlaying = isPlaying,
                hasNextEpisode = hasNextEpisode,
                autoEnterEnabled = autoEnterEnabled,
            )
        )
    }
}

private fun buildVueoPictureInPictureParams(
    activity: Activity,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    autoEnterEnabled: Boolean,
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(
            buildVueoPictureInPictureActions(
                activity = activity,
                isPlaying = isPlaying,
                hasNextEpisode = hasNextEpisode,
            )
        )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder
            .setAutoEnterEnabled(autoEnterEnabled)
            .setSeamlessResizeEnabled(true)
    }
    return builder.build()
}

private fun buildVueoPictureInPictureActions(
    activity: Activity,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
): List<RemoteAction> = buildList {
    val toggleTitle = if (isPlaying) "Pause" else "Play"
    add(
        RemoteAction(
            Icon.createWithResource(
                activity,
                if (isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                },
            ),
            toggleTitle,
            toggleTitle,
            pipPendingIntent(
                activity = activity,
                action = PIP_ACTION_TOGGLE,
                requestCode = PIP_REQUEST_TOGGLE,
            ),
        )
    )
    if (hasNextEpisode) {
        add(
            RemoteAction(
                Icon.createWithResource(
                    activity,
                    android.R.drawable.ic_media_next,
                ),
                "Next episode",
                "Play next episode",
                pipPendingIntent(
                    activity = activity,
                    action = PIP_ACTION_NEXT,
                    requestCode = PIP_REQUEST_NEXT,
                ),
            )
        )
    }
}

private fun pipPendingIntent(
    activity: Activity,
    action: String,
    requestCode: Int,
): PendingIntent = PendingIntent.getBroadcast(
    activity,
    requestCode,
    Intent(action).setPackage(activity.packageName),
    PendingIntent.FLAG_UPDATE_CURRENT or
        PendingIntent.FLAG_IMMUTABLE,
)
