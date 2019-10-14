package de.bauerapps.resimulate.helper

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import de.bauerapps.resimulate.R
import com.beardedhen.androidbootstrap.api.attributes.BootstrapBrand
import com.beardedhen.androidbootstrap.utils.ColorUtils.*


class ESBrandStyle(
  private var color: Int = R.color.white,
  var textColor: Int = R.color.black
) : BootstrapBrand {

  override fun defaultFill(context: Context?): Int {
    return resolveColor(color, context)
  }

  override fun defaultTextColor(context: Context?): Int {
    return resolveColor(textColor, context)
  }

  override fun activeTextColor(context: Context?): Int {
    return resolveColor(textColor, context)
  }

  override fun disabledEdge(context: Context): Int {
    return ContextCompat.getColor(
      context,
      R.color.bootstrap_gray_light
    )//increaseOpacity(context, R.color.bootstrap_gray_light, DISABLED_ALPHA_FILL)
  }

  fun setColor(color: Int) {
    this.color = color
  }

  @SuppressLint("ResourceAsColor")
  override fun getColor(): Int {
    return color
  }

  override fun activeFill(context: Context?): Int {
    return decreaseRgbChannels(context, color, ACTIVE_OPACITY_FACTOR_FILL)
  }

  override fun defaultEdge(context: Context?): Int {
    return decreaseRgbChannels(context, color, ACTIVE_OPACITY_FACTOR_EDGE)
  }

  override fun disabledTextColor(context: Context?): Int {
    return resolveColor(textColor, context)
  }

  override fun disabledFill(context: Context): Int {
    return ContextCompat.getColor(
      context,
      R.color.bootstrap_gray_light
    )//increaseOpacity(context, R.color.bootstrap_gray_light, DISABLED_ALPHA_FILL)
  }

  override fun activeEdge(context: Context?): Int {
    return decreaseRgbChannels(
      context,
      color,
      ACTIVE_OPACITY_FACTOR_FILL + ACTIVE_OPACITY_FACTOR_EDGE
    )
  }

}