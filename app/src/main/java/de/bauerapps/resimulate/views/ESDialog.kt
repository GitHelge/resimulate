package de.bauerapps.resimulate.views

import android.app.Dialog
import android.content.Context
import android.view.WindowManager
import de.bauerapps.resimulate.helper.FullscreenHelper

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
    window?.decorView?.systemUiVisibility = FullscreenHelper.defaultSystemVisibility()

    // Set the dialog to focusable again.
    window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    if (!backgroundDim)
      window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
  }
}