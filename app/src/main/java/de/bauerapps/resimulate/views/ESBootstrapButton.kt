package de.bauerapps.resimulate.views

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import de.bauerapps.resimulate.helper.ESBrand
import de.bauerapps.resimulate.helper.ESBrandStyle
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.helper.sized
import com.beardedhen.androidbootstrap.BootstrapButton
import com.beardedhen.androidbootstrap.api.defaults.DefaultBootstrapSize
import com.beardedhen.androidbootstrap.utils.DimenUtils


class ESBootstrapButton : BootstrapButton {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    initialise(attrs)
  }

  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
    context,
    attrs,
    defStyle
  ) {
    initialise(attrs)
  }

  private var brand = ESBrandStyle()
  private var baselineHoriPadding: Float = DimenUtils.pixelsFromDpResource(
    context,
    R.dimen.custom_button_hori_padding
  )
  private var baselineVertPadding: Float = DimenUtils.pixelsFromDpResource(
    context,
    R.dimen.custom_button_vert_padding
  )
  private var baselineFontSize: Float = DimenUtils.pixelsFromSpResource(
    context,
    R.dimen.bootstrap_button_default_font_size
  )
  private var customTextcolor: Int? = null
  private var isDarkGray: Boolean? = null
  private var hasWhiteBackground: Boolean? = null
  private var hasBlueishWhiteBackground: Boolean? = null
  private var useXLText: Boolean? = null

  @SuppressLint("CustomViewStyleable")
  private fun initialise(attrs: AttributeSet) {
    val a = context.obtainStyledAttributes(attrs, R.styleable.ESBootstrapButton)
    val b = context.obtainStyledAttributes(attrs, R.styleable.BootstrapButton)
    try {
      isDarkGray = a.getBoolean(R.styleable.ESBootstrapButton_isDarkGray, false)
      hasWhiteBackground = a.getBoolean(R.styleable.ESBootstrapButton_hasWhiteBackground, false)
      hasBlueishWhiteBackground =
        a.getBoolean(R.styleable.ESBootstrapButton_hasBlueishWhiteBackground, false)
      useXLText = a.getBoolean(R.styleable.ESBootstrapButton_useXLText, false)
      customTextcolor = a.getResourceId(R.styleable.ESBootstrapButton_useCustomTextColor, -1)


      val sizeOrdinal = b.getInt(R.styleable.BootstrapButton_bootstrapSize, -1)
      val typeOrdinal = a.getInt(R.styleable.ESBootstrapButton_esBrand, -1)

      val tempBrand = ESBrand.fromAttributeValue(typeOrdinal)
      if (tempBrand == ESBrand.REGULAR) {
        bootstrapBrand = brand
      } else {
        bootstrapBrand = tempBrand
      }
      /*else if (tempBrand == ESBrand.DANGER) {
          bootstrapBrand = ESBrandStyle(R.color.danger, R.color.white)
      }*/

      isSoundEffectsEnabled = false

      bootstrapSize = if (sizeOrdinal != -1) {
        DefaultBootstrapSize.fromAttributeValue(sizeOrdinal).scaleFactor()
      } else {
        1.8f.sized
      }
    } finally {
      a.recycle()
      b.recycle()
    }

    if (isDarkGray == true) {
      brand.color = R.color.colorPrimaryDark
      brand.textColor = R.color.white
      bootstrapBrand = brand
    }

    if (hasWhiteBackground == true) {
      brand.color = R.color.white
      brand.textColor = R.color.black
      bootstrapBrand = brand
    }

    if (hasBlueishWhiteBackground == true) {
      brand.color = R.color.blueish_white
      brand.textColor = R.color.black
      bootstrapBrand = brand
    }

    updateBootstrapState()
  }

  override fun updateBootstrapState() {
    super.updateBootstrapState()

    customTextcolor?.let {
      if (it != -1) setTextColor(ContextCompat.getColor(context, it))
    }

    if (useXLText == true && bootstrapSize != DefaultBootstrapSize.MD.scaleFactor()) {
      textSize = baselineFontSize * DefaultBootstrapSize.XL.scaleFactor()
    } else if (useXLText == true) {
      //TODO: Fix this workaround for md:
      textSize = baselineFontSize * DefaultBootstrapSize.LG.scaleFactor()
    }

    val vert = (baselineVertPadding * bootstrapSize).toInt()
    val hori = (baselineHoriPadding * bootstrapSize).toInt()
    setPadding(hori, vert, hori, vert)
  }

  fun setActiveText(isActive: Boolean) {
    if (!isActive) {
      setDeactivated()
      return
    }
    brand.color = R.color.white
    brand.textColor = R.color.success
    bootstrapBrand = brand
  }

  private fun setDeactivated() {
    brand.textColor = R.color.black
    brand.color = R.color.white
    bootstrapBrand = brand
  }

  fun setActiveBackground(isActive: Boolean) {
    if (!isActive) {
      setDeactivated()
      return
    }
    brand.color = R.color.success
    brand.textColor = R.color.white
    bootstrapBrand = brand
  }

  val isWarning get() = brand.color == R.color.warning
  val isActive get() = brand.color == R.color.success

  fun setWarningBackground(isWarning: Boolean) {
    if (!isWarning) {
      setDeactivated()
      return
    }
    brand.color = R.color.warning
    brand.textColor = R.color.white
    bootstrapBrand = brand
  }

}