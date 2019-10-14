package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.views.ESDialog
import kotlinx.android.synthetic.main.change_duration_dialog.view.*

@SuppressLint("SetTextI18n", "InflateParams")
class DynChangeConfig(context: AppCompatActivity, simConfig: SimConfig) {

  interface Callback {
    fun updateSimConfig(simConfig: SimConfig)
  }

  private var tempConfig = simConfig.deepCopy()

  private var dynChangeDialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView: View = LayoutInflater.from(context)
    .inflate(R.layout.change_duration_dialog, null)

  private var slider: SeekBar

  var callback: Callback? = null

  init {
    slider = dialogView.sb_change_duration.apply {
      max = 119
    }

    dialogView.apply {
      b_check.setOnClickListener {

        tempConfig.simState.changeDuration = slider.progress + 1

        callback?.updateSimConfig(tempConfig)

        dynChangeDialog.dismiss()
      }

      b_cancel.setOnClickListener { dynChangeDialog.dismiss() }
    }

    slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
        dialogView.tw_dyn_change_value.text = "${p1 + 1}s"
      }

      override fun onStartTrackingTouch(p0: SeekBar?) {}
      override fun onStopTrackingTouch(p0: SeekBar?) {}
    })

    dynChangeDialog.setContentView(dialogView)
  }

  fun openDialog(simConfig: SimConfig) {
    tempConfig = simConfig.deepCopy()

    slider.progress = tempConfig.simState.changeDuration - 1

    dialogView.tw_dyn_change_value.text = "${slider.progress + 1}s"

    dynChangeDialog.show()
  }

}