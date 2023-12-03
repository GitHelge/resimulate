package de.bauerapps.resimulate.helper

import android.app.Activity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class FullscreenHelper(private val topLevelView: View) {
  fun hide() {

    (topLevelView.context as? Activity)?.window?.let {
      WindowCompat.setDecorFitsSystemWindows(it, false)
      WindowInsetsControllerCompat(it, it.decorView).apply {
        hide(WindowInsetsCompat.Type.statusBars())
        hide(WindowInsetsCompat.Type.navigationBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    }
  }
}