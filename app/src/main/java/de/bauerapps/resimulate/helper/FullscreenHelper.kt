package de.bauerapps.resimulate.helper

import android.os.Handler
import android.view.View

class FullscreenHelper(private val topLevelView: View) {

  private val mHideHandler = Handler()
  private val mHidePart2Runnable = Runnable {
    topLevelView.systemUiVisibility = defaultSystemVisibility()
  }
  private val mHideRunnable = Runnable { hide() }

  private fun hide() {
    mHideHandler.postDelayed(mHidePart2Runnable, Const.UI_ANIMATION_DELAY.toLong())
  }

  /**
   * Schedules a call to hide() in [delayMillis], canceling any
   * previously scheduled calls.
   */
  fun delayedHide(delayMillis: Int) {
    mHideHandler.removeCallbacks(mHideRunnable)
    mHideHandler.postDelayed(mHideRunnable, delayMillis.toLong())
  }

  companion object {

    fun defaultSystemVisibility(): Int {
      return View.SYSTEM_UI_FLAG_LOW_PROFILE or
          View.SYSTEM_UI_FLAG_FULLSCREEN or
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
          View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
  }
}