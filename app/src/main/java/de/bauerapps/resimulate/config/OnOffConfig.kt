package de.bauerapps.resimulate.config

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.databinding.DefiChargeDialogBinding
import de.bauerapps.resimulate.databinding.StopSimDialogBinding
import de.bauerapps.resimulate.helper.ESBrandStyle
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESDialog

class OnOffConfig(private val context: AppCompatActivity) {

  interface Callback {
    //fun hideStatusBar() {}
    fun shutdownDevice()

    fun stopSimulation()
    fun startDevice()
  }

  private var uiElements = mutableListOf<View>()

  private var startDeviceDialog: ESDialog? = null
  private var stopDeviceDialog: ESDialog? = null
  private var bOnOffButton: ESBootstrapButton? = null

  private var isSimulationOn = false
  var callback: Callback? = null

  init {
    uiElements.add(context.findViewById(R.id.ll_graph_label_column))
    uiElements.add(context.findViewById(R.id.ll_graph_column))
    uiElements.add(context.findViewById(R.id.ll_measurement_column))
    uiElements.add(context.findViewById(R.id.sv_sidepanel))
    uiElements.add(context.findViewById(R.id.ll_time))
    uiElements.add(context.findViewById(R.id.ll_volume))
    uiElements.add(context.findViewById(R.id.ll_nibp_btns))
    uiElements.add(context.findViewById(R.id.ll_misc))

    uiElements.forEach { it.visibility = View.INVISIBLE }

    bOnOffButton = context.findViewById(R.id.b_OnOff)
    bOnOffButton?.setActiveText(true)
    bOnOffButton?.setOnClickListener {
      if (isSimulationOn) openStopDialog() else openStartDialog()
    }
  }

  @SuppressLint("InflateParams")
  private fun openStartDialog() {
    startDeviceDialog = ESDialog(context, R.style.NoAnimDialog)
    val startDeviceDialogView = DefiChargeDialogBinding.inflate(context.layoutInflater)

    startDeviceDialogView.twHeader.text = context.getString(R.string.starting_up_device)
    startDeviceDialogView.pbDefiCharge.apply {
      updateAnimationDuration = 5000
      bootstrapBrand = ESBrandStyle(R.color.white, R.color.black)
      progress = 100

      setOnProgressAnimationEndedListener {

        toggleUI()
        isSimulationOn = true
        bOnOffButton?.setActiveText(false)
        bOnOffButton?.setActiveBackground(true)
        startDeviceDialog?.dismiss()
        callback?.startDevice()
      }

      setOnProgressAnimationUpdateListener { percent ->
        if (percent == 50) {
          startDeviceDialogView.twHeader.text = context.getString(R.string.self_testing)
        }
      }

    }

    startDeviceDialog?.apply {
      setContentView(startDeviceDialogView.root)
      //setOnDismissListener { callback?.hideStatusBar() }
      show()
    }
  }

  @SuppressLint("InflateParams")
  fun openStopDialog(stopSimulation: Boolean = false) {
    stopDeviceDialog = ESDialog(context, R.style.NoAnimDialog)
    val stopDeviceDialogView = StopSimDialogBinding.inflate(context.layoutInflater)

    stopDeviceDialogView.apply {
      bCheck.setOnClickListener {

        if (stopSimulation) {
          callback?.stopSimulation()
        } else {
          callback?.shutdownDevice()
          toggleUI()
          isSimulationOn = false
          bOnOffButton?.setActiveBackground(false)
          bOnOffButton?.setActiveText(true)
          stopDeviceDialog?.dismiss()
        }
      }
      bCancel.setOnClickListener {
        stopDeviceDialog?.dismiss()
      }
    }

    stopDeviceDialog?.apply {
      setContentView(stopDeviceDialogView.root)
      //setOnDismissListener { callback?.hideStatusBar() }
      show()
    }
  }

  private fun toggleUI() {

    if (isSimulationOn) {
      uiElements.forEach { it.visibility = View.INVISIBLE }
    } else {
      uiElements.forEach { it.visibility = View.VISIBLE }
    }

  }

}