package de.bauerapps.resimulate.views

import android.content.Context
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.widget.TextView
import de.bauerapps.resimulate.R

class ESTextView : TextView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    initialize(attrs)
  }

  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    initialize(attrs)
  }

  private fun initialize(attrs: AttributeSet) {

    val a = context.obtainStyledAttributes(attrs, R.styleable.ESTextView)

    try {
      val typeOrdinal = a.getInt(R.styleable.ESTextView_simTextViewType, -1)
      val colorId = ESColor.fromAttributeValue(typeOrdinal).getColor()
      setTextColor(ContextCompat.getColor(context, colorId))
    } finally {
      a.recycle()
    }
  }

  private var isWarning = false

  fun blinkWarning() {
    if (isWarning) return

    setTextColor(ContextCompat.getColor(context, R.color.warning))
    isWarning = true
    postDelayed({
      setTextColor(ContextCompat.getColor(context, R.color.white))
      isWarning = false
    }, 200)
  }

}