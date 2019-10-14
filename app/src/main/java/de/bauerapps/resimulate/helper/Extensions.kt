package de.bauerapps.resimulate.helper

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.text.SimpleDateFormat
import java.util.*


fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View {
  return LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}

val Int.dp: Int
  get() = (this / Resources.getSystem().displayMetrics.density).toInt()

val Int.px: Int
  get() = (this * Resources.getSystem().displayMetrics.density).toInt()


val Long.formatTime: String
  get() {
    val hours = this / (1000 * 60 * 60) % 24
    val minutes = this / (60 * 1000)
    val seconds = this / 1000 % 60
    return String.format("%d:%02d:%02d", hours, minutes, seconds)
  }


val Long.toDate: String
  get() {
    val date = Date(this)
    val format = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    return format.format(date)
  }

val Long.formatTimeMMSS: String
  get() {
    val minutes = this / (60 * 1000)
    val seconds = this / 1000 % 60
    return String.format("%d:%02d", minutes, seconds)
  }

val Float.sized: Float
  get() {

    var screenLayout = Resources.getSystem().configuration.screenLayout
    screenLayout = screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK

    return when (screenLayout) {
      Configuration.SCREENLAYOUT_SIZE_SMALL -> this * 0.6f
      Configuration.SCREENLAYOUT_SIZE_NORMAL -> this * 0.7f
      Configuration.SCREENLAYOUT_SIZE_LARGE -> this * 0.8f
      Configuration.SCREENLAYOUT_SIZE_XLARGE -> this * 1.0f
      else -> this * 1.0f
    }

  }