package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import de.bauerapps.resimulate.config.VitalSignConfig
import de.bauerapps.resimulate.helper.VSConfigType
import de.bauerapps.resimulate.views.ESDialog
import kotlinx.android.synthetic.main.single_mode_console_dialog.view.*
import kotlin.math.roundToInt


@SuppressLint("InflateParams")
class SingleModeConsole(context: SingleModeActivity, simConfig: SimConfig) :
  VitalSignConfig.Callback,
  PostShockConfig.Callback,
  DynChangeConfig.Callback {


  interface Callback {
    fun setFullscreen()
    fun updateSimConfig(simConfig: SimConfig)
  }

  private var dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView: View = LayoutInflater.from(context)
    .inflate(R.layout.single_mode_console_dialog, null)

  private var vsConfigs = mutableMapOf<VSConfigType, VitalSignConfig>()
  private var dynChangeConfig: DynChangeConfig
  private var psConfig: PostShockConfig
  private var tempConfig = simConfig.deepCopy()

  var callback: Callback? = null

  init {
    initSingleModeConsole(dialogView)
    dynChangeConfig = DynChangeConfig(context, tempConfig)
    dynChangeConfig.callback = this
    psConfig = PostShockConfig(context, tempConfig)
    psConfig.callback = this
  }

  fun openDialog(simConfig: SimConfig) {
    tempConfig = simConfig.deepCopy()
    updateDialog()


    dialog.apply {

      setContentView(dialogView)
      val width = (context.resources.displayMetrics.widthPixels * 0.90).roundToInt()
      dialog.window?.setLayout(width, LinearLayout.LayoutParams.MATCH_PARENT)
      show()
    }

  }

  private fun updateDialog() {

    val simState = tempConfig.simState
    val vitalSigns = tempConfig.vitalSigns

    dialogView.apply {
      esd_pathology.text = vitalSigns.pathology.name
      esd_defi_pathology.text = simState.defi.vitalSigns.pathology.name
      setBoundsByPathology(vsConfigs, vitalSigns.pathology)



      b_ecg_toggle.setActiveBackground(simState.ecgEnabled)
      b_oxy_toggle.setActiveBackground(simState.oxyEnabled)
      b_cap_toggle.setActiveBackground(simState.capEnabled)
      b_nibp_toggle.setActiveBackground(simState.nibpEnabled)
      b_cpr.setActiveBackground(simState.hasCPR)
      b_copd.setActiveBackground(simState.hasCOPD)
    }

    vsConfigs[VSConfigType.HR]?.update(vitalSigns.ecg.hr)
    vsConfigs[VSConfigType.PACER_THRES]?.update(simState.pacer.energyThreshold)
    vsConfigs[VSConfigType.SPO2]?.update(vitalSigns.oxy.spo2)
    vsConfigs[VSConfigType.ETCO2]?.update(vitalSigns.cap.etco2)
    vsConfigs[VSConfigType.RESP_RATE]?.update(vitalSigns.cap.respRate)
    vsConfigs[VSConfigType.SYS]?.update(vitalSigns.nibp.sys)
    vsConfigs[VSConfigType.DIA]?.update(vitalSigns.nibp.dia)
    vsConfigs[VSConfigType.SHOCK_THRES]?.update(simState.defi.energyThreshold)
  }

  private fun setBoundsByPathology(
    vsConfigs: Map<VSConfigType, VitalSignConfig>,
    pathology: Pathology
  ) {
    val vsBounds = pathology.getSpecificBounds()
    vsBounds.forEach { vsConfigs[it.key]?.updateBounds(it.value.first, it.value.second) }
  }

  private fun updateVitalSignConfigs(pathology: Pathology, vs: VitalSigns) {
    dialogView.esd_pathology.text = pathology.name

    setBoundsByPathology(vsConfigs, pathology)
    vsConfigs[VSConfigType.HR]?.update(vs.ecg.hr)
    vsConfigs[VSConfigType.SPO2]?.update(vs.oxy.spo2)
    vsConfigs[VSConfigType.ETCO2]?.update(vs.cap.etco2)
    vsConfigs[VSConfigType.RESP_RATE]?.update(vs.cap.respRate)
    vsConfigs[VSConfigType.SYS]?.update(vs.nibp.sys)
    vsConfigs[VSConfigType.DIA]?.update(vs.nibp.dia)
  }

  private fun initSingleModeConsole(view: View) {

    view.apply {

      vsConfigs[VSConfigType.HR] = VitalSignConfig(
        60,
        b_hr_up, b_hr_down, tw_hr_config_label
      )
      vsConfigs[VSConfigType.PACER_THRES] = VitalSignConfig(
        20,
        b_pacer_thres_up, b_pacer_thres_down, tw_pacer_thres_config_label, 10
      )
      vsConfigs[VSConfigType.SPO2] = VitalSignConfig(
        97,
        b_spo2_up, b_spo2_down, tw_spo2_config_label
      )
      vsConfigs[VSConfigType.ETCO2] = VitalSignConfig(
        35,
        b_etco2_up, b_etco2_down, tw_etco2_config_label
      )
      vsConfigs[VSConfigType.RESP_RATE] = VitalSignConfig(
        12,
        b_resp_rate_up, b_resp_rate_down, tw_resp_rate_config_label
      )
      vsConfigs[VSConfigType.SYS] = VitalSignConfig(
        120,
        b_sys_up, b_sys_down, tw_sys_config_label
      )
      vsConfigs[VSConfigType.DIA] = VitalSignConfig(
        80,
        b_dia_up, b_dia_down, tw_dia_config_label
      )
      vsConfigs[VSConfigType.SHOCK_THRES] = VitalSignConfig(
        150,
        b_defi_energy_thres_up, b_defi_energy_thres_down, tw_defi_energy_value, 10
      )

      vsConfigs.forEach { it.value.callback = this@SingleModeConsole }

      esd_pathology.text = tempConfig.vitalSigns.pathology.name
      esd_defi_pathology.text = tempConfig.simState.defi.vitalSigns.pathology.name
      setBoundsByPathology(vsConfigs, tempConfig.vitalSigns.pathology)

      val simState = tempConfig.simState
      val vitalSigns = tempConfig.vitalSigns

      b_ecg_toggle.setActiveBackground(simState.ecgEnabled)
      b_oxy_toggle.setActiveBackground(simState.oxyEnabled)
      b_cap_toggle.setActiveBackground(simState.capEnabled)
      b_nibp_toggle.setActiveBackground(simState.nibpEnabled)
      b_cpr.setActiveBackground(simState.hasCPR)
      b_copd.setActiveBackground(simState.hasCOPD)

      b_ecg_toggle.setOnClickListener {
        tempConfig.simState.ecgEnabled = !b_ecg_toggle.isActive
        b_ecg_toggle.setActiveBackground(!b_ecg_toggle.isActive)
      }

      b_oxy_toggle.setOnClickListener {
        tempConfig.simState.oxyEnabled = !b_oxy_toggle.isActive
        b_oxy_toggle.setActiveBackground(!b_oxy_toggle.isActive)
      }

      b_cap_toggle.setOnClickListener {
        tempConfig.simState.capEnabled = !b_cap_toggle.isActive
        b_cap_toggle.setActiveBackground(!b_cap_toggle.isActive)
      }

      b_nibp_toggle.setOnClickListener {
        tempConfig.simState.nibpEnabled = !b_nibp_toggle.isActive
        b_nibp_toggle.setActiveBackground(!b_nibp_toggle.isActive)
      }

      b_copd.setOnClickListener {
        tempConfig.simState.hasCOPD = !b_copd.isActive
        b_copd.setActiveBackground(!b_copd.isActive)
      }

      b_cpr.setOnClickListener {
        tempConfig.simState.hasCPR = !b_cpr.isActive
        b_cpr.setActiveBackground(!b_cpr.isActive)
      }

      vsConfigs[VSConfigType.HR]?.update(vitalSigns.ecg.hr)
      vsConfigs[VSConfigType.PACER_THRES]?.update(simState.pacer.energyThreshold)
      vsConfigs[VSConfigType.SPO2]?.update(vitalSigns.oxy.spo2)
      vsConfigs[VSConfigType.ETCO2]?.update(vitalSigns.cap.etco2)
      vsConfigs[VSConfigType.RESP_RATE]?.update(vitalSigns.cap.respRate)
      vsConfigs[VSConfigType.SYS]?.update(vitalSigns.nibp.sys)
      vsConfigs[VSConfigType.DIA]?.update(vitalSigns.nibp.dia)
      vsConfigs[VSConfigType.SHOCK_THRES]?.update(simState.defi.energyThreshold)

      esd_pathology.setOnDropDownItemClickListener { _, v, _ ->
        val pathology = Pathology((v as TextView).text.toString())
        val defaultVS = DefaultVitalSigns.fromPathology(pathology)
        updateVitalSignConfigs(pathology, defaultVS)
        //notifyRequireConfigPush()
        //TODO: Update

        callback?.setFullscreen()
      }

      esd_defi_pathology.setOnDropDownItemClickListener { _, v, _ ->
        val pathology = Pathology((v as TextView).text.toString())
        val defaultVS = DefaultVitalSigns.fromPathology(pathology)
        esd_defi_pathology.text = pathology.name

        tempConfig.simState.defi.vitalSigns = defaultVS.deepCopy()
        //ncService?.sendSomething(Gson().toJson(simConfig.simState.defi))

        callback?.setFullscreen()
      }

      esd_pathology.setOnDismissListener {
        callback?.setFullscreen()
      }

      b_dynchange_config.setOnClickListener {
        dynChangeConfig.openDialog(tempConfig)
      }

      b_ps_pathology_config.setOnClickListener {
        psConfig.openDialog(tempConfig)
      }

      b_check.setOnClickListener {
        performConfigUpdate()
        dialog.dismiss()
      }


      b_cancel.setOnClickListener {
        dialog.dismiss()
      }
    }
  }

  private fun performConfigUpdate() {

    val pathology = dialogView.esd_pathology.text.toString()

    tempConfig.apply {
      if (pathology != vitalSigns.pathology.name)
        vitalSigns = DefaultVitalSigns.fromPathology(Pathology(pathology))

      vitalSigns.ecg.hr = vsConfigs[VSConfigType.HR]!!.value
      vitalSigns.oxy.spo2 = vsConfigs[VSConfigType.SPO2]!!.value
      vitalSigns.cap.etco2 = vsConfigs[VSConfigType.ETCO2]!!.value
      vitalSigns.cap.respRate = vsConfigs[VSConfigType.RESP_RATE]!!.value
      vitalSigns.nibp.sys = vsConfigs[VSConfigType.SYS]!!.value
      vitalSigns.nibp.dia = vsConfigs[VSConfigType.DIA]!!.value
      simState.pacer.energyThreshold = vsConfigs[VSConfigType.PACER_THRES]!!.value
      simState.defi.energyThreshold = vsConfigs[VSConfigType.SHOCK_THRES]!!.value
    }

    callback?.updateSimConfig(tempConfig)
  }

  override fun updateSimConfig(simConfig: SimConfig) {
    tempConfig = simConfig
    callback?.updateSimConfig(tempConfig)
  }

  override fun wasUpdated() {
    Handler(Looper.getMainLooper()).post {
      // TODO: Update:
    }
  }

}