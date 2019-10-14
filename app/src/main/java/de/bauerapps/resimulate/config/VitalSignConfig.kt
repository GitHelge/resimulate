package de.bauerapps.resimulate.config

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESTextView
import java.util.*
import kotlin.math.exp
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class VitalSignConfig(
  var value: Int,
  var b_up: ESBootstrapButton,
  var b_down: ESBootstrapButton,
  private var tw_value: ESTextView,
  private val changeInterval: Int? = null
) : View.OnClickListener, View.OnTouchListener {

  interface Callback {
    /** Implement to receive a notification, if the up or down buttons were clicked. */
    fun wasUpdated()
  }

  private var changeValueTimer = Timer()
  private var max: Int = -1
  private var min: Int = -1

  var callback: Callback? = null

  init {
    b_up.setOnClickListener(this)
    b_up.setOnTouchListener(this)
    b_down.setOnClickListener(this)
    b_down.setOnTouchListener(this)
  }

  override fun onClick(p0: View?) {
    require(this.max != -1) { "Forgot to call updateBounds? - max bound as ${this.max} found." }
    when (p0) {
      b_up -> increase(changeInterval ?: 1, isMainThread = true)
      b_down -> decrease(changeInterval ?: 1, isMainThread = true)
    }
  }

  private fun increase(value: Int, isMainThread: Boolean = false) {
    if (this.value < max) {
      var temp = this.value
      temp += value

      if (temp > max) return

      callback?.wasUpdated()
      update(temp, isMainThread)
    }
  }

  private fun decrease(value: Int, isMainThread: Boolean = false) {
    if (this.value > min) {
      var temp = this.value
      temp -= value

      if (temp < min) return

      callback?.wasUpdated()
      update(temp, isMainThread)
    }
  }

  private fun scheduleForQuickChange(v: View?) {

    var count = 0
    changeValueTimer.scheduleAtFixedRate(object : TimerTask() {
      override fun run() {
        count++
        when (v) {
          b_up -> increase(changeInterval ?: exp((count) / 20.0).roundToInt())
          b_down -> decrease(changeInterval ?: exp((count) / 20.0).roundToInt())
        }
      }
    }, 500, 70)
  }

  private fun stopQuickChange() {
    changeValueTimer.cancel()
    changeValueTimer.purge()
    changeValueTimer = Timer()
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouch(v: View?, event: MotionEvent?): Boolean {

    when (event?.action) {
      MotionEvent.ACTION_DOWN -> {
        scheduleForQuickChange(v)
        // Do something
        return false
      }
      MotionEvent.ACTION_UP -> {
        stopQuickChange()
        // No longer down
        return false
      }
      MotionEvent.ACTION_CANCEL -> {
        stopQuickChange()
        return false
      }
      else -> return false
    }
  }

  fun update(value: Int, isMainThread: Boolean = true) {

    fun updateValue() {
      this.value = value
      tw_value.text = value.toString()
    }

    if (isMainThread) {
      updateValue()
    } else {
      Handler(Looper.getMainLooper()).post { updateValue() }
    }
  }

  fun updateBounds(min: Int, max: Int) {
    this.min = min
    this.max = max

    if (value < min) value = min
    if (value > max) value = max

    update(value)
  }
}