package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.config.VitalSignConfig
import de.bauerapps.resimulate.databinding.DefiPathologyConfigDialogBinding
import de.bauerapps.resimulate.helper.VSConfigType
import de.bauerapps.resimulate.views.ESDialog

@SuppressLint("InflateParams")
class PostShockConfig(val context: AppCompatActivity, simConfig: SimConfig) {

  interface Callback {
    fun updateSimConfig(simConfig: SimConfig)
  }

  private var dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView = DefiPathologyConfigDialogBinding.inflate(context.layoutInflater)


  var callback: Callback? = null
  private var tempConfig = simConfig.deepCopy()

  /** Post-Shock Vital Sign Configs */
  private val psVSConfigs = mutableMapOf<VSConfigType, VitalSignConfig>()

  fun openDialog(simConfig: SimConfig) {
    tempConfig = simConfig.deepCopy()

    dialogView.apply {

      initPostShockConfigs(this.root)

      val psVitalSigns = tempConfig.simState.defi.vitalSigns
      setBoundsByPathology(psVSConfigs, psVitalSigns.pathology)

      psVSConfigs[VSConfigType.HR]?.update(psVitalSigns.ecg.hr)
      psVSConfigs[VSConfigType.SPO2]?.update(psVitalSigns.oxy.spo2)
      psVSConfigs[VSConfigType.ETCO2]?.update(psVitalSigns.cap.etco2)
      psVSConfigs[VSConfigType.RESP_RATE]?.update(psVitalSigns.cap.respRate)
      psVSConfigs[VSConfigType.SYS]?.update(psVitalSigns.nibp.sys)
      psVSConfigs[VSConfigType.DIA]?.update(psVitalSigns.nibp.dia)


      bCheck.setOnClickListener {
        updatePostShockConfig()
        callback?.updateSimConfig(tempConfig)

        dialog.dismiss()
      }

      bCancel.setOnClickListener { dialog.dismiss() }
    }

    dialog.apply {
      setContentView(dialogView.root)
      show()
    }
  }

  private fun updatePostShockConfig() {
    val psVitalSigns = tempConfig.simState.defi.vitalSigns.deepCopy()

    psVitalSigns.ecg.hr = psVSConfigs[VSConfigType.HR]!!.value
    psVitalSigns.oxy.spo2 = psVSConfigs[VSConfigType.SPO2]!!.value
    psVitalSigns.cap.etco2 = psVSConfigs[VSConfigType.ETCO2]!!.value
    psVitalSigns.cap.respRate = psVSConfigs[VSConfigType.RESP_RATE]!!.value
    psVitalSigns.nibp.sys = psVSConfigs[VSConfigType.SYS]!!.value
    psVitalSigns.nibp.dia = psVSConfigs[VSConfigType.DIA]!!.value

    tempConfig.simState.defi.vitalSigns = psVitalSigns
  }

  private fun initPostShockConfigs(psView: View) {
    psView.apply {
      dialogView.apply {
        psVSConfigs[VSConfigType.HR] = VitalSignConfig(
          60,
          bHrUp, bHrDown, twHrConfigLabel
        )
        psVSConfigs[VSConfigType.SPO2] = VitalSignConfig(
          97,
          bSpo2Up, bSpo2Down, twSpo2ConfigLabel
        )
        psVSConfigs[VSConfigType.ETCO2] = VitalSignConfig(
          35,
          bEtco2Up, bEtco2Down, twEtco2ConfigLabel
        )
        psVSConfigs[VSConfigType.RESP_RATE] = VitalSignConfig(
          12,
          bRespRateUp, bRespRateDown, twRespRateConfigLabel
        )
        psVSConfigs[VSConfigType.SYS] = VitalSignConfig(
          120,
          bSysUp, bSysDown, twSysConfigLabel
        )
        psVSConfigs[VSConfigType.DIA] = VitalSignConfig(
          80,
          bDiaUp, bDiaDown, twDiaConfigLabel
        )
      }
    }
  }

  private fun setBoundsByPathology(
    vsConfigs: Map<VSConfigType, VitalSignConfig>,
    pathology: Pathology
  ) {
    val vsBounds = pathology.getSpecificBounds()
    vsBounds.forEach { vsConfigs[it.key]?.updateBounds(it.value.first, it.value.second) }
  }
}