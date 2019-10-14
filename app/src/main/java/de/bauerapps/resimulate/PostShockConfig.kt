package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.config.VitalSignConfig
import de.bauerapps.resimulate.helper.VSConfigType
import de.bauerapps.resimulate.views.ESDialog
import kotlinx.android.synthetic.main.defi_pathology_config_dialog.view.*

@SuppressLint("InflateParams")
class PostShockConfig(val context: AppCompatActivity, simConfig: SimConfig) {

  interface Callback {
    fun updateSimConfig(simConfig: SimConfig)
  }

  private var dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView: View = LayoutInflater.from(context)
    .inflate(R.layout.defi_pathology_config_dialog, null)


  var callback: Callback? = null
  private var tempConfig = simConfig.deepCopy()

  /** Post-Shock Vital Sign Configs */
  private val psVSConfigs = mutableMapOf<VSConfigType, VitalSignConfig>()

  fun openDialog(simConfig: SimConfig) {
    tempConfig = simConfig.deepCopy()

    dialogView.apply {

      initPostShockConfigs(this)

      val psVitalSigns = tempConfig.simState.defi.vitalSigns
      setBoundsByPathology(psVSConfigs, psVitalSigns.pathology)

      psVSConfigs[VSConfigType.HR]?.update(psVitalSigns.ecg.hr)
      psVSConfigs[VSConfigType.SPO2]?.update(psVitalSigns.oxy.spo2)
      psVSConfigs[VSConfigType.ETCO2]?.update(psVitalSigns.cap.etco2)
      psVSConfigs[VSConfigType.RESP_RATE]?.update(psVitalSigns.cap.respRate)
      psVSConfigs[VSConfigType.SYS]?.update(psVitalSigns.nibp.sys)
      psVSConfigs[VSConfigType.DIA]?.update(psVitalSigns.nibp.dia)


      b_check.setOnClickListener {
        updatePostShockConfig()
        callback?.updateSimConfig(tempConfig)

        dialog.dismiss()
      }

      b_cancel.setOnClickListener { dialog.dismiss() }
    }

    dialog.apply {
      setContentView(dialogView)
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
      psVSConfigs[VSConfigType.HR] = VitalSignConfig(
        60,
        b_hr_up, b_hr_down, tw_hr_config_label
      )
      psVSConfigs[VSConfigType.SPO2] = VitalSignConfig(
        97,
        b_spo2_up, b_spo2_down, tw_spo2_config_label
      )
      psVSConfigs[VSConfigType.ETCO2] = VitalSignConfig(
        35,
        b_etco2_up, b_etco2_down, tw_etco2_config_label
      )
      psVSConfigs[VSConfigType.RESP_RATE] = VitalSignConfig(
        12,
        b_resp_rate_up, b_resp_rate_down, tw_resp_rate_config_label
      )
      psVSConfigs[VSConfigType.SYS] = VitalSignConfig(
        120,
        b_sys_up, b_sys_down, tw_sys_config_label
      )
      psVSConfigs[VSConfigType.DIA] = VitalSignConfig(
        80,
        b_dia_up, b_dia_down, tw_dia_config_label
      )
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