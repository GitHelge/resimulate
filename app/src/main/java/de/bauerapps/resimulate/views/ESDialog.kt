package de.bauerapps.resimulate.views

import android.app.Dialog
import android.content.Context
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ESDialog(
  context: Context, themeResId: Int,
  private val backgroundDim: Boolean = true
) : Dialog(context, themeResId) {

  override fun show() {
    // Set the dialog to not focusable.
    window?.setFlags(
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    )

    // Show the dialog with NavBar hidden.
    super.show()

    //Set the dialog to immersive
    window?.let {
      WindowCompat.setDecorFitsSystemWindows(it, false)
      WindowInsetsControllerCompat(it, it.decorView).apply {
        hide(WindowInsetsCompat.Type.statusBars())
        hide(WindowInsetsCompat.Type.navigationBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    }



    // Set the dialog to focusable again.
    window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    if (!backgroundDim)
      window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
  }
}