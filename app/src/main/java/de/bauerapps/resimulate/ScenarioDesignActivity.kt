package de.bauerapps.resimulate

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import de.bauerapps.resimulate.adapters.ECGParam
import de.bauerapps.resimulate.adapters.ECGParamAdapter
import de.bauerapps.resimulate.databinding.ScenarioDesignActivityBinding
import de.bauerapps.resimulate.helper.FullscreenHelper
import de.bauerapps.resimulate.helper.VSConfigType
import de.bauerapps.resimulate.simulations.*
import de.bauerapps.resimulate.views.ESColor
import de.bauerapps.resimulate.views.ESSurfaceView
import de.bauerapps.resimulate.views.ESViewType
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

  private lateinit var binding: ScenarioDesignActivityBinding;

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ScenarioDesignActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)

    ecgParamAdapter = ECGParamAdapter()
    ecgParamAdapter?.callback = this
    ecgCalculation.callback = this

    scenarioSaveDialog = ScenarioSaveDialog(this)
    scenarioSaveDialog?.callback = this

    dynChangeConfig = DynChangeConfig(this, tempConfig)
    dynChangeConfig?.callback = this

    fullscreenHelper = FullscreenHelper(binding.llWhole)
    fullscreenHelper?.hide()

    // Sets interface to portrait or landscape
    if (resources.getBoolean(R.bool.forceLandscape)) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.insetsController?.hide(WindowInsets.Type.statusBars())
    } else {
      window.setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN
      )
    }


    //initVitalSignView()
    initGraphs()
    initParamList()
    initPathologyDropdown()

    simConfig.simState.changeDuration = 20
    
    binding.apply {
      bZeroIndicator.setOnClickListener {
        val active = vsgEcg.hasZeroIndicator
        bZeroIndicator.setActiveBackground(!active)
        vsgEcg.hasZeroIndicator = !active
      }

      bOk.setOnClickListener {
        if (bOk.isWarning) {
          simConfig = tempConfig
          bOk.setWarningBackground(false)
        }
      }

      bEcgToggle.setOnClickListener {
        val isActive = bEcgToggle.isActive
        //setVitalSignView(ESViewType.ECG, !isActive)
        ecgParamAdapter?.updateParams(mapOf("ecg" to !isActive))
        bEcgToggle.setActiveBackground(!isActive)
      }
      bOxyToggle.setOnClickListener {
        val isActive = bOxyToggle.isActive
        //setVitalSignView(ESViewType.PLETH, !isActive)
        ecgParamAdapter?.updateParams(mapOf("oxy" to !isActive))
        bOxyToggle.setActiveBackground(!isActive)
      }
      bCapToggle.setOnClickListener {
        val isActive = bCapToggle.isActive
        //setVitalSignView(ESViewType.CAP, !isActive)
        ecgParamAdapter?.updateParams(mapOf("cap" to !isActive))
        bCapToggle.setActiveBackground(!isActive)
      }

      bSaveScenario.setOnClickListener {
        scenarioSaveDialog?.openDialog(simConfig.vitalSigns)
      }

      bBack.setOnClickListener {
        onBackPressed()
      }

      bDynchangeConfig.setOnClickListener {
        dynChangeConfig?.openDialog(simConfig)
      }

      bEcgToggle.setActiveBackground(true)
    }
    
    setVitalSignView(ESViewType.ECG, true)
    setVitalSignView(ESViewType.PLETH, true)
    setVitalSignView(ESViewType.CAP, true)
  }


  private fun onUpdateSimConfig(newValue: SimConfig) {

    ecgCalculation.pendingSimConfig = newValue
    capCalculation.simConfig = newValue
    oxyCalculation.simConfig = newValue
  }

  private fun setVitalSignView(type: ESViewType, isChecked: Boolean) {

    when (type) {
      ESViewType.ECG -> {
        binding.vsgEcg.isToggledOn = isChecked
        if (isChecked) binding.vsgEcg.restart() else binding.vsgEcg.clearStop()
      }
      ESViewType.PLETH -> {
        binding.vsgOxy.isToggledOn = isChecked
        if (isChecked) binding.vsgOxy.restart() else binding.vsgOxy.clearStop()
      }
      ESViewType.CAP -> {
        binding.vsgCap.isToggledOn = isChecked
        if (isChecked) binding.vsgCap.restart() else binding.vsgCap.clearStop()
      }
    }
  }

  private fun initGraphs() {

    binding.vsgEcg.callback = this
    binding.vsgOxy.callback = this
    binding.vsgCap.callback = this
    ecgCalculation.currentHR = simConfig.vitalSigns.ecg.hr
    oxyCalculation.currentNIBP = NIBP(simConfig.vitalSigns.nibp.sys, simConfig.vitalSigns.nibp.dia)

    binding.vsgEcg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0, true)
    binding.vsgOxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0, true)
    binding.vsgCap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0, true)
  }

  override fun drawZeroIndicator() {
    binding.vsgEcg.drawZeroIndicator = true
  }

  private fun initParamList() {

    binding.rvParameters.layoutManager = LinearLayoutManager(this)
    /*
    * rv_parameters.layoutManager = GridLayoutManager(
        this,
        if (resources.getBoolean(R.bool.is600dp)) 3 else 2
    )
    * */

    binding.rvParameters.adapter = ecgParamAdapter
  }

  private fun initPathologyDropdown() {

    binding.ddPathologies.text = PType.SinusRhythm.pname

    binding.ddPathologies.setOnDropDownItemClickListener { _, v, _ ->

      val tempConfig = simConfig.deepCopy()
      val pathology = Pathology((v as TextView).text.toString())
      val defaultVS = DefaultVitalSigns.fromPathology(pathology)
      tempConfig.vitalSigns = defaultVS
      binding.ddPathologies.text = pathology.name

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
      binding.bOk.setWarningBackground(true)
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
    binding.bOk.setWarningBackground(true)
  }

  override fun updateSimConfig(simConfig: SimConfig) {
    this.simConfig.simState.changeDuration = simConfig.simState.changeDuration
  }

  override fun reloadDropdown(downloadPathology: DownloadPathology?) {
    binding.ddPathologies.updateDropdownData()
  }

  override fun pullValue(type: ESViewType, timestep: Double): Double {

    return when (type) {
      ESViewType.ECG -> ecgCalculation.calc(timestep)
      ESViewType.PLETH -> oxyCalculation.calc(timestep)
      ESViewType.CAP -> capCalculation.calc(timestep)
    }
  }

  override fun requestSync() {
    //if (!bOxyToggle.isActive) return
    binding.vsgOxy.performECGSync()
  }

  override fun onPause() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }

  override fun onResume() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.hide()
    super.onResume()
  }

}