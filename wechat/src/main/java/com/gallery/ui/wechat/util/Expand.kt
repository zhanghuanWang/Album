package com.gallery.ui.wechat.util

import android.annotation.SuppressLint
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

@SuppressLint("SimpleDateFormat")
private val formatter = SimpleDateFormat("yyyy/MM")

internal fun Long.formatTimeVideo(): String {
    if (toInt() == 0) {
        return "--:--"
    }
    val format: String = String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(this),
            TimeUnit.MILLISECONDS.toSeconds(this) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(this)))
    if (!format.startsWith("0")) {
        return format
    }
    return format.substring(1)
}

internal fun Long.formatTime(): String {
    if (toInt() == 0) {
        return "--/--"
    }
    return formatter.format(this * 1000)
}

internal fun RotateAnimation.doOnAnimationEnd(action: (animation: Animation) -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationRepeat(animation: Animation) {
        }

        override fun onAnimationEnd(animation: Animation) {
            action.invoke(animation)
        }

        override fun onAnimationStart(animation: Animation) {
        }
    })
}

internal fun Long.toFileSize(): String {
    val df = DecimalFormat()
    return when {
        this < 1024 -> {
            df.format(this) + " B"
        }
        this < 1048576 -> {
            df.format(this / 1024) + " KB"
        }
        this < 1073741824 -> {
            df.format(this / 1048576) + " MB"
        }
        else -> {
            df.format(this / 1073741824) + " GB"
        }
    }
}
