package de.bauerapps.resimulate.helper

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import de.bauerapps.resimulate.R
import com.beardedhen.androidbootstrap.api.attributes.BootstrapBrand
import com.beardedhen.androidbootstrap.utils.ColorUtils.*

enum class ESBrand : BootstrapBrand {

  PRIMARY(R.color.primary),
  SUCCESS(R.color.success),
  INFO(R.color.info),
  WARNING(R.color.warning),
  DANGER(R.color.danger),
  SECONDARY(
    R.color.bootstrap_brand_secondary_fill,
    R.color.bootstrap_brand_secondary_text
  ),
  REGULAR(R.color.bootstrap_gray_light);

  private val textColor: Int
  private val color: Int

  constructor(color: Int) {
    this.color = color
    this.textColor = android.R.color.white
  }

  constructor(color: Int, textColor: Int) {
    this.color = color
    this.textColor = textColor
  }

  @ColorInt
  override fun defaultFill(context: Context): Int {
    return resolveColor(color, context)
  }

  @ColorInt
  override fun defaultEdge(context: Context): Int {
    return decreaseRgbChannels(context, color, ACTIVE_OPACITY_FACTOR_EDGE)
  }

  @ColorInt
  override fun activeFill(context: Context): Int {
    return decreaseRgbChannels(context, color, ACTIVE_OPACITY_FACTOR_FILL)
  }

  @ColorInt
  override fun activeEdge(context: Context): Int {
    return decreaseRgbChannels(
      context,
      color,
      ACTIVE_OPACITY_FACTOR_FILL + ACTIVE_OPACITY_FACTOR_EDGE
    )
  }

  @ColorInt
  override fun disabledFill(context: Context): Int {
    return ContextCompat.getColor(
      context,
      R.color.bootstrap_gray_light
    )//increaseOpacity(context, color, DISABLED_ALPHA_FILL)
  }

  @ColorInt
  override fun disabledEdge(context: Context): Int {
    return ContextCompat.getColor(
      context,
      R.color.bootstrap_gray_light
    )//increaseOpacity(context, color, DISABLED_ALPHA_FILL - DISABLED_ALPHA_EDGE)
  }

  @ColorInt
  override fun defaultTextColor(context: Context): Int {
    return resolveColor(textColor, context)
  }

  @ColorInt
  override fun activeTextColor(context: Context): Int {
    return resolveColor(textColor, context)
  }

  @ColorInt
  override fun disabledTextColor(context: Context): Int {
    return resolveColor(textColor, context)
  }

  @ColorInt
  override fun getColor(): Int {
    return color
  }

  companion object {

    fun fromAttributeValue(attrValue: Int): ESBrand {
      return when (attrValue) {
        0 -> PRIMARY
        1 -> SUCCESS
        2 -> INFO
        3 -> WARNING
        4 -> DANGER
        5 -> REGULAR
        6 -> SECONDARY
        else -> REGULAR
      }
    }
  }


}