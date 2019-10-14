package de.bauerapps.resimulate.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.bauerapps.resimulate.helper.ESBrandStyle
import com.beardedhen.androidbootstrap.BootstrapText
import com.beardedhen.androidbootstrap.api.attributes.BootstrapBrand

class ESDropdown : CustomBootstrapDropDown, CustomBootstrapDropDown.OnDropDownItemClickListener {

  private var onDismissListener: ((View) -> Unit)? = null

  companion object {
    val brand = ESBrandStyle()
  }

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    initialise()
  }

  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    initialise()
  }

  private fun initialise() {
    setOnDropDownItemClickListener(this)

    setTextColor(bootstrapButtonText(context, brand))
  }

  override fun getBootstrapBrand(): BootstrapBrand {
    return brand
  }

  override fun onItemClick(parent: ViewGroup?, v: View?, id: Int) {
    bootstrapText =
      BootstrapText.Builder(context, false).addText((v as TextView).text.toString()).build()
    setTextColor(bootstrapButtonText(context, brand))
  }

  private fun bootstrapButtonText(context: Context, brand: BootstrapBrand): ColorStateList {

    val defaultColor = brand.defaultTextColor(context)
    val activeColor = brand.activeTextColor(context)
    val disabledColor = brand.disabledTextColor(context)

    return ColorStateList(getStateList(), getColorList(defaultColor, activeColor, disabledColor))
  }

  override fun onDismiss() {

    onDismissListener?.invoke(this)
    super.onDismiss()
  }

  fun setOnDismissListener(listener: (View) -> Unit) {
    this.onDismissListener = listener
  }

  private fun getColorList(defaultColor: Int, activeColor: Int, disabledColor: Int): IntArray {
    return intArrayOf(
      activeColor,
      activeColor,
      activeColor,
      activeColor,
      activeColor,
      activeColor,
      disabledColor,
      defaultColor
    )
  }

  private fun getStateList(): Array<IntArray> {
    return arrayOf(

      intArrayOf(android.R.attr.state_hovered),
      intArrayOf(android.R.attr.state_activated),
      intArrayOf(android.R.attr.state_focused),
      intArrayOf(android.R.attr.state_selected),
      intArrayOf(android.R.attr.state_pressed),
      intArrayOf(android.R.attr.state_hovered),
      intArrayOf(-android.R.attr.state_enabled),
      intArrayOf()
    )
  }
}