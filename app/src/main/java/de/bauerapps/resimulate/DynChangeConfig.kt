package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.databinding.ChangeDurationDialogBinding
import de.bauerapps.resimulate.views.ESDialog

@SuppressLint("SetTextI18n", "InflateParams")
class DynChangeConfig(context: AppCompatActivity, simConfig: SimConfig) {

  interface Callback {
    fun updateSimConfig(simConfig: SimConfig)
  }

  private var tempConfig = simConfig.deepCopy()

  private var dynChangeDialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView = ChangeDurationDialogBinding.inflate(context.layoutInflater)

  private var slider: SeekBar = dialogView.sbChangeDuration.apply {
    max = 119
  }

  var callback: Callback? = null

  init {

    dialogView.apply {
      bCheck.setOnClickListener {

        tempConfig.simState.changeDuration = slider.progress + 1

        callback?.updateSimConfig(tempConfig)

        dynChangeDialog.dismiss()
      }

      bCancel.setOnClickListener { dynChangeDialog.dismiss() }
    }

    slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
        dialogView.twDynChangeValue.text = "${p1 + 1}s"
      }

      override fun onStartTrackingTouch(p0: SeekBar?) {}
      override fun onStopTrackingTouch(p0: SeekBar?) {}
    })

    dynChangeDialog.setContentView(dialogView.root)
  }

  fun openDialog(simConfig: SimConfig) {
    tempConfig = simConfig.deepCopy()

    slider.progress = tempConfig.simState.changeDuration - 1

    dialogView.twDynChangeValue.text = "${slider.progress + 1}s"

    dynChangeDialog.show()
  }

}