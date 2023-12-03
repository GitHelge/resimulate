package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import de.bauerapps.resimulate.config.VitalSignConfig
import de.bauerapps.resimulate.databinding.SingleModeConsoleDialogBinding
import de.bauerapps.resimulate.helper.VSConfigType
import de.bauerapps.resimulate.views.ESDialog
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

  private var dialogView = SingleModeConsoleDialogBinding.inflate(context.layoutInflater)

  private var vsConfigs = mutableMapOf<VSConfigType, VitalSignConfig>()
  private var dynChangeConfig: DynChangeConfig
  private var psConfig: PostShockConfig
  private var tempConfig = simConfig.deepCopy()

  var callback: Callback? = null

  init {
    initSingleModeConsole(dialogView.root)
    dynChangeConfig = DynChangeConfig(context, tempConfig)
    dynChangeConfig.callback = this
    psConfig = PostShockConfig(context, tempConfig)
    psConfig.callback = this
  }

  fun openDialog(simConfig: SimConfig) {
    tempConfig = simConfig.deepCopy()
    updateDialog()


    dialog.apply {

      setContentView(dialogView.root)
      val width = (context.resources.displayMetrics.widthPixels * 0.90).roundToInt()
      dialog.window?.setLayout(width, LinearLayout.LayoutParams.MATCH_PARENT)
      show()
    }

  }

  private fun updateDialog() {

    val simState = tempConfig.simState
    val vitalSigns = tempConfig.vitalSigns

    dialogView.apply {
      esdPathology.text = vitalSigns.pathology.name
      esdDefiPathology.text = simState.defi.vitalSigns.pathology.name
      setBoundsByPathology(vsConfigs, vitalSigns.pathology)



      bEcgToggle.setActiveBackground(simState.ecgEnabled)
      bOxyToggle.setActiveBackground(simState.oxyEnabled)
      bCapToggle.setActiveBackground(simState.capEnabled)
      bNibpToggle.setActiveBackground(simState.nibpEnabled)
      bCpr.setActiveBackground(simState.hasCPR)
      bCopd.setActiveBackground(simState.hasCOPD)
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
    dialogView.esdPathology.text = pathology.name

    setBoundsByPathology(vsConfigs, pathology)
    vsConfigs[VSConfigType.HR]?.update(vs.ecg.hr)
    vsConfigs[VSConfigType.SPO2]?.update(vs.oxy.spo2)
    vsConfigs[VSConfigType.ETCO2]?.update(vs.cap.etco2)
    vsConfigs[VSConfigType.RESP_RATE]?.update(vs.cap.respRate)
    vsConfigs[VSConfigType.SYS]?.update(vs.nibp.sys)
    vsConfigs[VSConfigType.DIA]?.update(vs.nibp.dia)
  }

  private fun initSingleModeConsole(view: View) {
    dialogView.apply { 
      view.apply {
  
        vsConfigs[VSConfigType.HR] = VitalSignConfig(
          60,
          bHrUp, bHrDown, twHrConfigLabel
        )
        vsConfigs[VSConfigType.PACER_THRES] = VitalSignConfig(
          20,
          bPacerThresUp, bPacerThresDown, twPacerThresConfigLabel, 10
        )
        vsConfigs[VSConfigType.SPO2] = VitalSignConfig(
          97,
          bSpo2Up, bSpo2Down, twSpo2ConfigLabel
        )
        vsConfigs[VSConfigType.ETCO2] = VitalSignConfig(
          35,
          bEtco2Up, bEtco2Down, twEtco2ConfigLabel
        )
        vsConfigs[VSConfigType.RESP_RATE] = VitalSignConfig(
          12,
          bRespRateUp, bRespRateDown, twRespRateConfigLabel
        )
        vsConfigs[VSConfigType.SYS] = VitalSignConfig(
          120,
          bSysUp, bSysDown, twSysConfigLabel
        )
        vsConfigs[VSConfigType.DIA] = VitalSignConfig(
          80,
          bDiaUp, bDiaDown, twDiaConfigLabel
        )
        vsConfigs[VSConfigType.SHOCK_THRES] = VitalSignConfig(
          150,
          bDefiEnergyThresUp, bDefiEnergyThresDown, twDefiEnergyValue, 10
        )
  
        vsConfigs.forEach { it.value.callback = this@SingleModeConsole }
  
        esdPathology.text = tempConfig.vitalSigns.pathology.name
        esdDefiPathology.text = tempConfig.simState.defi.vitalSigns.pathology.name
        setBoundsByPathology(vsConfigs, tempConfig.vitalSigns.pathology)
  
        val simState = tempConfig.simState
        val vitalSigns = tempConfig.vitalSigns
  
        bEcgToggle.setActiveBackground(simState.ecgEnabled)
        bOxyToggle.setActiveBackground(simState.oxyEnabled)
        bCapToggle.setActiveBackground(simState.capEnabled)
        bNibpToggle.setActiveBackground(simState.nibpEnabled)
        bCpr.setActiveBackground(simState.hasCPR)
        bCopd.setActiveBackground(simState.hasCOPD)
  
        bEcgToggle.setOnClickListener {
          tempConfig.simState.ecgEnabled = !bEcgToggle.isActive
          bEcgToggle.setActiveBackground(!bEcgToggle.isActive)
        }
  
        bOxyToggle.setOnClickListener {
          tempConfig.simState.oxyEnabled = !bOxyToggle.isActive
          bOxyToggle.setActiveBackground(!bOxyToggle.isActive)
        }
  
        bCapToggle.setOnClickListener {
          tempConfig.simState.capEnabled = !bCapToggle.isActive
          bCapToggle.setActiveBackground(!bCapToggle.isActive)
        }
  
        bNibpToggle.setOnClickListener {
          tempConfig.simState.nibpEnabled = !bNibpToggle.isActive
          bNibpToggle.setActiveBackground(!bNibpToggle.isActive)
        }
  
        bCopd.setOnClickListener {
          tempConfig.simState.hasCOPD = !bCopd.isActive
          bCopd.setActiveBackground(!bCopd.isActive)
        }
  
        bCpr.setOnClickListener {
          tempConfig.simState.hasCPR = !bCpr.isActive
          bCpr.setActiveBackground(!bCpr.isActive)
        }
  
        vsConfigs[VSConfigType.HR]?.update(vitalSigns.ecg.hr)
        vsConfigs[VSConfigType.PACER_THRES]?.update(simState.pacer.energyThreshold)
        vsConfigs[VSConfigType.SPO2]?.update(vitalSigns.oxy.spo2)
        vsConfigs[VSConfigType.ETCO2]?.update(vitalSigns.cap.etco2)
        vsConfigs[VSConfigType.RESP_RATE]?.update(vitalSigns.cap.respRate)
        vsConfigs[VSConfigType.SYS]?.update(vitalSigns.nibp.sys)
        vsConfigs[VSConfigType.DIA]?.update(vitalSigns.nibp.dia)
        vsConfigs[VSConfigType.SHOCK_THRES]?.update(simState.defi.energyThreshold)
  
        esdPathology.setOnDropDownItemClickListener { _, v, _ ->
          val pathology = Pathology((v as TextView).text.toString())
          val defaultVS = DefaultVitalSigns.fromPathology(pathology)
          updateVitalSignConfigs(pathology, defaultVS)
          //notifyRequireConfigPush()
          //TODO: Update
  
          callback?.setFullscreen()
        }
  
        esdDefiPathology.setOnDropDownItemClickListener { _, v, _ ->
          val pathology = Pathology((v as TextView).text.toString())
          val defaultVS = DefaultVitalSigns.fromPathology(pathology)
          esdDefiPathology.text = pathology.name
  
          tempConfig.simState.defi.vitalSigns = defaultVS.deepCopy()
          //ncService?.sendSomething(Gson().toJson(simConfig.simState.defi))
  
          callback?.setFullscreen()
        }
  
        esdPathology.setOnDismissListener {
          callback?.setFullscreen()
        }
  
        dialogView.bDynchangeConfig.setOnClickListener {
          dynChangeConfig.openDialog(tempConfig)
        }

        dialogView.bPsPathologyConfig.setOnClickListener {
          psConfig.openDialog(tempConfig)
        }
  
        bCheck.setOnClickListener {
          performConfigUpdate()
          dialog.dismiss()
        }


        dialogView.bCancel.setOnClickListener {
          dialog.dismiss()
        }
      } 
    }
  }

  private fun performConfigUpdate() {

    val pathology = dialogView.esdPathology.text.toString()

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