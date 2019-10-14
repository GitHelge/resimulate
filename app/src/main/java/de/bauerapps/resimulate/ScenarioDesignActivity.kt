package de.bauerapps.resimulate

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import de.bauerapps.resimulate.adapters.ECGParam
import de.bauerapps.resimulate.adapters.ECGParamAdapter
import de.bauerapps.resimulate.helper.FullscreenHelper
import de.bauerapps.resimulate.helper.VSConfigType
import de.bauerapps.resimulate.simulations.*
import de.bauerapps.resimulate.views.ESColor
import de.bauerapps.resimulate.views.ESSurfaceView
import de.bauerapps.resimulate.views.ESViewType
import kotlinx.android.synthetic.main.scenario_design_activity.*
import kotlin.math.roundToInt

class ScenarioDesignActivity : AppCompatActivity(),
  ECGParamAdapter.Callback,
  ESSurfaceView.Callback,
  ECGCalculation.Callback,
  ScenarioSaveDialog.Callback,
  DynChangeConfig.Callback {

  private var ecgParamAdapter: ECGParamAdapter? = null
  private var scenarioSaveDialog: ScenarioSaveDialog? = null
  private var dynChangeConfig: DynChangeConfig? = null
  private var fullscreenHelper: FullscreenHelper? = null

  private var simConfig = SimConfig()
    set(value) {
      onUpdateSimConfig(value.deepCopy())
      field = value
    }

  private var tempConfig = simConfig.deepCopy()

  private var ecgCalculation = ECGCalculation(simConfig, CPRCalculation(CPRType.ECG))
  private var oxyCalculation =
    OxyCalculation(simConfig, CPRCalculation(CPRType.SPO2), ecgCalculation)
  private var capCalculation = CAPCalculation(simConfig, CPRCalculation(CPRType.ETCO2))


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.scenario_design_activity)

    ecgParamAdapter = ECGParamAdapter()
    ecgParamAdapter?.callback = this
    ecgCalculation.callback = this

    scenarioSaveDialog = ScenarioSaveDialog(this)
    scenarioSaveDialog?.callback = this

    dynChangeConfig = DynChangeConfig(this, tempConfig)
    dynChangeConfig?.callback = this

    fullscreenHelper = FullscreenHelper(ll_whole)
    fullscreenHelper?.delayedHide(100)

    // Sets interface to portrait or landscape
    if (resources.getBoolean(R.bool.forceLandscape)) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    window?.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )


    //initVitalSignView()
    initGraphs()
    initParamList()
    initPathologyDropdown()

    simConfig.simState.changeDuration = 20

    b_zero_indicator.setOnClickListener {
      val active = vsg_ecg.hasZeroIndicator
      b_zero_indicator.setActiveBackground(!active)
      vsg_ecg.hasZeroIndicator = !active
    }

    b_ok.setOnClickListener {
      if (b_ok.isWarning) {
        simConfig = tempConfig
        b_ok.setWarningBackground(false)
      }
    }

    b_ecg_toggle.setOnClickListener {
      val isActive = b_ecg_toggle.isActive
      //setVitalSignView(ESViewType.ECG, !isActive)
      ecgParamAdapter?.updateParams(mapOf("ecg" to !isActive))
      b_ecg_toggle.setActiveBackground(!isActive)
    }
    b_oxy_toggle.setOnClickListener {
      val isActive = b_oxy_toggle.isActive
      //setVitalSignView(ESViewType.PLETH, !isActive)
      ecgParamAdapter?.updateParams(mapOf("oxy" to !isActive))
      b_oxy_toggle.setActiveBackground(!isActive)
    }
    b_cap_toggle.setOnClickListener {
      val isActive = b_cap_toggle.isActive
      //setVitalSignView(ESViewType.CAP, !isActive)
      ecgParamAdapter?.updateParams(mapOf("cap" to !isActive))
      b_cap_toggle.setActiveBackground(!isActive)
    }

    b_save_scenario.setOnClickListener {
      scenarioSaveDialog?.openDialog(simConfig.vitalSigns)
    }

    b_back.setOnClickListener {
      onBackPressed()
    }

    b_dynchange_config.setOnClickListener {
      dynChangeConfig?.openDialog(simConfig)
    }

    b_ecg_toggle.setActiveBackground(true)
    setVitalSignView(ESViewType.ECG, true)
    setVitalSignView(ESViewType.PLETH, true)
    setVitalSignView(ESViewType.CAP, true)

    /*fullscreenHelper = FullscreenHelper(ll_whole)
    if (currentFocus != null) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        fullscreenHelper?.delayedHide(0)
    }*/
  }


  private fun onUpdateSimConfig(newValue: SimConfig) {

    ecgCalculation.pendingSimConfig = newValue
    capCalculation.simConfig = newValue
    oxyCalculation.simConfig = newValue
  }

  private fun setVitalSignView(type: ESViewType, isChecked: Boolean) {

    when (type) {
      ESViewType.ECG -> {
        vsg_ecg.isToggledOn = isChecked
        if (isChecked) vsg_ecg.restart() else vsg_ecg.clearStop()
      }
      ESViewType.PLETH -> {
        vsg_oxy.isToggledOn = isChecked
        if (isChecked) vsg_oxy.restart() else vsg_oxy.clearStop()
      }
      ESViewType.CAP -> {
        vsg_cap.isToggledOn = isChecked
        if (isChecked) vsg_cap.restart() else vsg_cap.clearStop()
      }
    }
  }

  private fun initGraphs() {

    vsg_ecg.callback = this
    vsg_oxy.callback = this
    vsg_cap.callback = this
    ecgCalculation.currentHR = simConfig.vitalSigns.ecg.hr
    oxyCalculation.currentNIBP = NIBP(simConfig.vitalSigns.nibp.sys, simConfig.vitalSigns.nibp.dia)

    vsg_ecg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0, true)
    vsg_oxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0, true)
    vsg_cap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0, true)
  }

  override fun drawZeroIndicator() {
    vsg_ecg.drawZeroIndicator = true
  }

  private fun initParamList() {

    rv_parameters.layoutManager = LinearLayoutManager(this)
    /*
    * rv_parameters.layoutManager = GridLayoutManager(
        this,
        if (resources.getBoolean(R.bool.is600dp)) 3 else 2
    )
    * */

    rv_parameters.adapter = ecgParamAdapter
  }

  private fun initPathologyDropdown() {

    dd_pathologies.text = PType.SinusRhythm.pname

    dd_pathologies.setOnDropDownItemClickListener { _, v, _ ->

      val tempConfig = simConfig.deepCopy()
      val pathology = Pathology((v as TextView).text.toString())
      val defaultVS = DefaultVitalSigns.fromPathology(pathology)
      tempConfig.vitalSigns = defaultVS
      dd_pathologies.text = pathology.name

      val bounds = pathology.getSpecificBounds()

      val ecg = defaultVS.ecg

      ecgParamAdapter?.ecgParams?.forEach { i ->

        when (i) {
          ECGParam.HR -> {
            i.min = bounds.getValue(VSConfigType.HR).first.toDouble()
            i.max = bounds.getValue(VSConfigType.HR).second.toDouble()
            i.value = ecg.hr.toDouble()
          }
          ECGParam.PWaveAmp -> i.value = ecg.pWave.amp
          ECGParam.PWaveDuration -> i.value = ecg.pWave.duration
          ECGParam.PWaveStartTime -> i.value = ecg.pWave.startTime
          ECGParam.QWaveDuration -> i.value = ecg.qWave.duration
          ECGParam.QWaveStartTime -> i.value = ecg.qWave.startTime
          ECGParam.QWaveAmp -> i.value = ecg.qWave.amp
          ECGParam.QRSDuration -> i.value = ecg.qrs.duration
          ECGParam.QRSStartTime -> i.value = ecg.qrs.startTime
          ECGParam.QRSWaveAmp -> i.value = ecg.qrs.amp
          /*ECGParam.QRSAmplitudeOffset -> i.value = ecg.qrs.amplitudeOffset*/
          ECGParam.SWaveDuration -> i.value = ecg.sWave.duration
          ECGParam.SWaveStartTime -> i.value = ecg.sWave.startTime
          ECGParam.SWaveAmp -> i.value = ecg.sWave.amp
          ECGParam.TWaveDuration -> i.value = ecg.tWave.duration
          ECGParam.TWaveStartTime -> i.value = ecg.tWave.startTime
          ECGParam.TWaveAmp -> i.value = ecg.tWave.amp
          ECGParam.STWaveSeparationDuration -> i.value = ecg.stWaveSeparationDuration
          ECGParam.UWaveAmp -> i.value = ecg.uWave.amp
          ECGParam.UWaveDuration -> i.value = ecg.uWave.duration
          ECGParam.UWaveStartTime -> i.value = ecg.uWave.startTime
          ECGParam.XValOffset -> i.value = ecg.xValOffset
          ECGParam.EcgNoise -> i.value = ecg.noise
          ECGParam.OxyNoise -> i.value = defaultVS.oxy.noise
          ECGParam.CapNoise -> i.value = defaultVS.cap.noise
          ECGParam.SPO2 -> {
            i.min = bounds.getValue(VSConfigType.SPO2).first.toDouble()
            i.max = bounds.getValue(VSConfigType.SPO2).second.toDouble()
            i.value = defaultVS.oxy.spo2.toDouble()
          }
          ECGParam.ETCO2 -> {
            i.min = bounds.getValue(VSConfigType.ETCO2).first.toDouble()
            i.max = bounds.getValue(VSConfigType.ETCO2).second.toDouble()
            i.value = defaultVS.cap.etco2.toDouble()
          }
          ECGParam.RESPRATE -> {
            i.min = bounds.getValue(VSConfigType.RESP_RATE).first.toDouble()
            i.max = bounds.getValue(VSConfigType.RESP_RATE).second.toDouble()
            i.value = defaultVS.cap.respRate.toDouble()
          }
          ECGParam.NIBP_SYS -> {
            i.min = bounds.getValue(VSConfigType.SYS).first.toDouble()
            i.max = bounds.getValue(VSConfigType.SYS).second.toDouble()
            i.value = defaultVS.nibp.sys.toDouble()
          }
          ECGParam.NIBP_DIA -> {
            i.min = bounds.getValue(VSConfigType.DIA).first.toDouble()
            i.max = bounds.getValue(VSConfigType.DIA).second.toDouble()
            i.value = defaultVS.nibp.dia.toDouble()
          }
        }
      }

      ecgParamAdapter?.notifyDataSetChanged()
      this.tempConfig = tempConfig
      b_ok.setWarningBackground(true)
    }
  }

  override fun onProgressChanged(ecgParam: ECGParam, value: Int) {

    val tempConfig: SimConfig = simConfig.deepCopy()

    val vs = tempConfig.vitalSigns
    val ecg = tempConfig.vitalSigns.ecg

    when (ecgParam) {
      ECGParam.HR -> ecg.hr = ecgParam.value.roundToInt()
      ECGParam.PWaveAmp -> ecg.pWave.amp = ecgParam.value
      ECGParam.PWaveDuration -> ecg.pWave.duration = ecgParam.value
      ECGParam.PWaveStartTime -> ecg.pWave.startTime = ecgParam.value

      ECGParam.QWaveDuration -> ecg.qWave.duration = ecgParam.value
      ECGParam.QWaveStartTime -> ecg.qWave.startTime = ecgParam.value
      ECGParam.QWaveAmp -> ecg.qWave.amp = ecgParam.value

      ECGParam.QRSDuration -> ecg.qrs.duration = ecgParam.value
      ECGParam.QRSStartTime -> ecg.qrs.startTime = ecgParam.value
      ECGParam.QRSWaveAmp -> ecg.qrs.amp = ecgParam.value
      /*ECGParam.QRSAmplitudeOffset -> ecg.qrs.amplitudeOffset = ecgParam.value*/

      ECGParam.SWaveDuration -> ecg.sWave.duration = ecgParam.value
      ECGParam.SWaveStartTime -> ecg.sWave.startTime = ecgParam.value
      ECGParam.SWaveAmp -> ecg.sWave.amp = ecgParam.value

      ECGParam.TWaveDuration -> ecg.tWave.duration = ecgParam.value
      ECGParam.TWaveStartTime -> ecg.tWave.startTime = ecgParam.value
      ECGParam.TWaveAmp -> ecg.tWave.amp = ecgParam.value

      ECGParam.STWaveSeparationDuration -> ecg.stWaveSeparationDuration = ecgParam.value

      ECGParam.UWaveAmp -> ecg.uWave.amp = ecgParam.value
      ECGParam.UWaveDuration -> ecg.uWave.duration = ecgParam.value
      ECGParam.UWaveStartTime -> ecg.uWave.startTime = ecgParam.value

      ECGParam.XValOffset -> ecg.xValOffset = ecgParam.value
      ECGParam.EcgNoise -> ecg.noise = ecgParam.value
      ECGParam.OxyNoise -> vs.oxy.noise = ecgParam.value
      ECGParam.CapNoise -> vs.cap.noise = ecgParam.value
      ECGParam.SPO2 -> tempConfig.vitalSigns.oxy.spo2 = ecgParam.value.roundToInt()
      ECGParam.ETCO2 -> tempConfig.vitalSigns.cap.etco2 = ecgParam.value.roundToInt()
      ECGParam.RESPRATE -> tempConfig.vitalSigns.cap.respRate = ecgParam.value.roundToInt()
      ECGParam.NIBP_SYS -> tempConfig.vitalSigns.nibp.sys = ecgParam.value.roundToInt()
      ECGParam.NIBP_DIA -> tempConfig.vitalSigns.nibp.dia = ecgParam.value.roundToInt()
    }

    this.tempConfig = tempConfig
    b_ok.setWarningBackground(true)
  }

  override fun updateSimConfig(simConfig: SimConfig) {
    this.simConfig.simState.changeDuration = simConfig.simState.changeDuration
  }

  override fun reloadDropdown(downloadPathology: DownloadPathology?) {
    dd_pathologies.updateDropdownData()
  }

  override fun pullValue(type: ESViewType, timestep: Double): Double {

    return when (type) {
      ESViewType.ECG -> ecgCalculation.calc(timestep)
      ESViewType.PLETH -> oxyCalculation.calc(timestep)
      ESViewType.CAP -> capCalculation.calc(timestep)
    }
  }

  override fun requestSync() {
    //if (!b_oxy_toggle.isActive) return
    vsg_oxy.performECGSync()
  }

  override fun onPause() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }

  override fun onResume() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.delayedHide(100)
    super.onResume()
  }

}