package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.config.*
import de.bauerapps.resimulate.helper.*
import de.bauerapps.resimulate.simulations.*
import de.bauerapps.resimulate.views.ESColor
import de.bauerapps.resimulate.views.ESSurfaceView
import de.bauerapps.resimulate.views.ESViewType
import com.beardedhen.androidbootstrap.BootstrapText
import com.beardedhen.androidbootstrap.font.FontAwesome
import de.bauerapps.resimulate.databinding.ActivitySingleModeBinding
import kotlin.math.abs
import kotlin.math.roundToInt

class SingleModeActivity : AppCompatActivity(),
  ESSurfaceView.Callback,
  SingleModePacerConfig.PacerCallback,
  SingleModeActivityDefiConfig.DefiConfigCallback,
  Sound.SoundCallback,
  Measurement.MeasurementCallback,
  ECGCalculation.Callback,
  NIBPConfig.NIBPConfigCallback, AlarmConfig.AlarmConfigCallback, OnOffConfig.Callback,
  SingleModeConsole.Callback {


  companion object {
    const val TAG = "SingleModeActivity"
  }

  private var fullscreenHelper: FullscreenHelper? = null

  private var simConfig = SimConfig()
    set(value) {
      onUpdateSimConfig(simConfig, value)
      field = value
    }
  private var ecgCalculation = ECGCalculation(simConfig, CPRCalculation(CPRType.ECG))
  private var oxyCalculation =
    OxyCalculation(simConfig, CPRCalculation(CPRType.SPO2), ecgCalculation)
  private var capCalculation = CAPCalculation(simConfig, CPRCalculation(CPRType.ETCO2))

  private val ecgMeasurement = ECGMeasurement()
  private val oxyMeasurement = SpO2Measurement(simConfig)
  private val capMeasurement = ETCO2Measurement()

  private var sound: Sound? = null
  private var defiConfig: SingleModeActivityDefiConfig? = null
  private var alarmConfig: AlarmConfig? = null
  private var nibpConfig: NIBPConfig? = null

  private var pacerConfig: SingleModePacerConfig? = null
  private var onOffConfig: OnOffConfig? = null

  private var singleModeConsole: SingleModeConsole? = null


  private var shockReceived = false

  lateinit var binding: ActivitySingleModeBinding;

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivitySingleModeBinding.inflate(layoutInflater)
    setContentView(binding.root)

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


    initVitalSignGraphs()
    sound = Sound(this)
    defiConfig = SingleModeActivityDefiConfig(this, sound, simConfig)
    pacerConfig = SingleModePacerConfig(this, simConfig)
    onOffConfig = OnOffConfig(this)
    alarmConfig = AlarmConfig(this, sound)
    nibpConfig = NIBPConfig(this)
    singleModeConsole = SingleModeConsole(this, simConfig)

    onOffConfig?.callback = this
    singleModeConsole?.callback = this
    pacerConfig?.pacerCallback = this
    defiConfig?.defiConfigCallback = this
    ecgCalculation.callback = this
    sound?.soundCallback = this
    alarmConfig?.callback = this
    ecgMeasurement.measurementCallback = this
    oxyMeasurement.measurementCallback = this
    capMeasurement.measurementCallback = this
    nibpConfig?.callback = this

    binding.bBack.setOnClickListener { onBackPressed() }

    val content: View = findViewById(android.R.id.content)

    binding.apply {
      content.afterMeasured {
        val availableHeight = this.height - llFooter.height + llToggleUi.height
        if (!resources.getBoolean(R.bool.is600dp) || availableHeight < llSidepanelContent.height) {

          llPacerUi.visibility = View.GONE

          bToggleUi.setOnClickListener {

            val pacerVisible = llPacerUi.visibility == View.VISIBLE

            llPacerUi.visibility = if (pacerVisible) View.GONE else View.VISIBLE
            llDefiUi.visibility = if (pacerVisible) View.VISIBLE else View.GONE

            bToggleUi.text = if (pacerVisible)
              getString(R.string.show_pacer_module)
            else
              getString(R.string.show_defi_module)
          }
        } else {
          llToggleUi.visibility = View.GONE
        }
        Log.i(
          TAG,
          "Available screen height: $availableHeight, scrollview content height: ${llSidepanelContent.height}"
        )
      }
    }

    binding.bTrainerConsole.setOnClickListener {
      singleModeConsole?.openDialog(simConfig)
    }
  }

  private fun onUpdateSimConfig(oldValue: SimConfig, newValue: SimConfig) {

    ecgCalculation.forceChange = shockReceived ||
        (newValue.simState.ecgEnabled && !oldValue.simState.ecgEnabled)

    oxyCalculation.forceChange = shockReceived ||
        (newValue.simState.oxyEnabled && !oldValue.simState.oxyEnabled)

    capCalculation.forceChange = shockReceived ||
        (newValue.simState.capEnabled && !oldValue.simState.capEnabled)

    if (shockReceived || ecgCalculation.forceChange) {
      ecgCalculation.simConfig = newValue
    } else
      ecgCalculation.pendingSimConfig = newValue

    oxyCalculation.simConfig = newValue
    capCalculation.simConfig = newValue


    defiConfig?.simConfig = newValue
    pacerConfig?.simConfig = newValue
    oxyMeasurement.simConfig = newValue

    setVitalSignView(ESViewType.ECG, newValue.simState.ecgEnabled)
    setVitalSignView(ESViewType.PLETH, newValue.simState.oxyEnabled)
    setVitalSignView(ESViewType.CAP, newValue.simState.capEnabled)
    setNIBPActive(newValue.simState.nibpEnabled)

    alarmConfig?.updateAlarmConfig(newValue.simState)
  }

  private fun setVitalSignView(type: ESViewType, isChecked: Boolean) {
    binding.apply { 
      when (type) {
        ESViewType.ECG -> {
          if (isChecked) vsvEcg.restart() else {
            vsvEcg.clearStop()
            if (!simConfig.simState.oxyEnabled)
              twHrValue.text = "--"
          }
        }
        ESViewType.PLETH -> {
          if (isChecked) vsvOxy.restart() else {
            vsvOxy.clearStop()
            twSpo2Value.text = "--"
            if (!simConfig.simState.ecgEnabled)
              twHrValue.text = "--"
          }
        }
        ESViewType.CAP -> {
          if (isChecked) vsvCap.restart() else {
            vsvCap.clearStop()
            twRespRateValue.text = "--"
            twEtco2Value.text = "--"
          }
        }
      }
    }
  }

  private fun setNIBPActive(isChecked: Boolean) {
    // TODO: Show disconnection warning?
    nibpConfig?.isEnabled = isChecked
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)

    fullscreenHelper = FullscreenHelper(binding.CLWhole)
    fullscreenHelper?.hide()
  }

  override fun onPacerUpdate(pacerState: PacerState) {
    val tempConfig = simConfig.deepCopy()
    tempConfig.simState.pacer = pacerState
    binding.vsvEcg.pacerEnergy = pacerState.energy
    //singleModeConsole?.simConfig = simConfig.deepCopy()
    simConfig = tempConfig
  }

  @SuppressLint("SetTextI18n")
  override fun onUpdateUI(type: PacerConfigType, value: Int) {
    when (type) {
      PacerConfigType.Energy -> binding.twPacerEnergy.text = "$value\nmA"
      PacerConfigType.Frequency -> binding.twPacerFrequency.text = "$value\nbpm"
    }
  }

  override fun drawPacerPeak() {
    pacerConfig?.forceDrawPacerPeak()
  }

  override fun pullValue(type: ESViewType, timestep: Double): Double {

    when (type) {
      ESViewType.ECG -> {

        pacerConfig?.drawPacerPeak(timestep)

        val ecgValue = ecgCalculation.calc(timestep)
        ecgMeasurement.addECGValue(ecgValue, timestep)
        return ecgValue
      }
      ESViewType.PLETH -> {
        // If HR is deactivated, HR changes should still be possible.
        if (!simConfig.simState.ecgEnabled) {
          // Just calculate for dynamic HR change.
          ecgCalculation.calc(timestep)
        }

        val spo2Value = oxyCalculation.calc(timestep)
        oxyMeasurement.addSpO2Value(spo2Value, simConfig.vitalSigns.nibp, timestep)
        return spo2Value
      }
      ESViewType.CAP -> {

        val etco2Value = capCalculation.calc(timestep)
        capMeasurement.addETCO2Value(etco2Value, timestep)
        return etco2Value
      }
    }
  }

  override fun requestSync() {
    if (!simConfig.simState.oxyEnabled) return

    binding.vsvOxy.performECGSync()
  }

  override fun onMeasurement(type: MeasurementType, value: Int) {
    binding.apply {
      when (type) {
        MeasurementType.ECG -> {
          if (!simConfig.simState.ecgEnabled) return
          runOnUiThread {
            twHrLabel.text = getString(R.string.hr_bpm)
            twHrValue.text = if (value >= 20) "$value" else "--"

            alarmConfig?.testForAlarm(AlarmType.HR, value)
            if (ecgMeasurement.isOverMaxIdleTime())
              defiConfig?.deactivateSync()
          }
        }
        MeasurementType.PLETH -> {
          if (!simConfig.simState.oxyEnabled) return
          runOnUiThread {
            if (!simConfig.simState.ecgEnabled) {
              val avgHR = oxyMeasurement.getAverageHeartrateFromSPO2()

              twHrLabel.text = getString(R.string.hr_bpm_oxy)
              twHrValue.text = if (avgHR >= 20) "$avgHR" else "--"
              alarmConfig?.testForAlarm(AlarmType.HR, avgHR)
            }

            twSpo2Value.text = if (value != 0) "$value" else "--"
            alarmConfig?.testForAlarm(AlarmType.SPO2, value)
          }
        }
        MeasurementType.ETCO2 -> {
          if (!simConfig.simState.capEnabled) return
          runOnUiThread {

            twEtco2Value.text = if (value != 0) "$value" else "--"
            alarmConfig?.testForAlarm(AlarmType.ETCO2, value)
          }
        }
        MeasurementType.RESP_RATE -> {
          if (!simConfig.simState.capEnabled) return
          runOnUiThread {
            twRespRateValue.text = if (value != 0) "$value" else "--"
            alarmConfig?.testForAlarm(AlarmType.RESP_RATE, value)
          }
        }
      }
    }
  }

  override fun onPeak(type: MeasurementType) {
    when (type) {
      MeasurementType.ECG -> {
        if (oxyMeasurement.isOverMaxIdleTime() || (abs(
            oxyMeasurement.globalIdleTimeCounter -
                ecgMeasurement.globalIdleTimeCounter
          ) >= oxyMeasurement.maxIdleTime)
        ) {
          sound?.playSound(SoundType.ECGPeak)
        }

        if (defiConfig?.isSynchronized == true) {
          binding.vsvEcg.drawSyncPeak = true
          if (defiConfig?.isShockPending == true)
            defiConfig?.syncShock()
        }
      }
      MeasurementType.PLETH -> {
        sound?.updateSPO2(oxyMeasurement.maxValue)
        sound?.playSound(SoundType.Spo2Peak)
        ecgMeasurement.globalIdleTimeCounter = oxyMeasurement.globalIdleTimeCounter
      }
      MeasurementType.ETCO2 -> TODO()
      MeasurementType.RESP_RATE -> TODO()
    }
  }

  override fun onShock(tempConfig: SimConfig, shockSucceeded: Boolean) {

    if (shockSucceeded) {
      shockReceived = true
      simConfig = tempConfig
      ecgCalculation.drawShock = true
      shockReceived = false
      //singleModeConsole?.simConfig = simConfig.deepCopy()
    } else {
      // Shock not succeeded. Still draw shock artifact
      ecgCalculation.drawShock = true
    }

  }

  override fun onCharge(tempDefi: Defi) {
    simConfig.simState.defi = tempDefi
    //singleModeConsole?.simConfig = simConfig.deepCopy()
  }

  @SuppressLint("SetTextI18n")
  override fun onFinish(type: SoundType) {
    when (type) {
      SoundType.Charging -> defiConfig?.changeState(DefiState.Warning)
      SoundType.Warning -> defiConfig?.changeState(DefiState.Pending)
      SoundType.Shock -> defiConfig?.changeState(DefiState.Idle)
      SoundType.NIBP -> {
        nibpConfig?.audioFinished()

        val sysVal = simConfig.vitalSigns.nibp.sys
        val diaVal = simConfig.vitalSigns.nibp.dia

        // MAD = diastolic pressure + 1/2 * (systolic pressure - diastolic pressure)
        val sysText = if (sysVal > 20) "$sysVal" else "--"
        val diaText = if (diaVal > 20) "$diaVal" else "--"
        val avgText =
          if (sysVal > 20 && diaVal > 20) "${(diaVal + 0.5 * (sysVal - diaVal)).roundToInt()}" else "--"
        binding.twBpSysDiaValue.text = "$sysText/$diaText"
        binding.twBpAvgValue.text = "($avgText)"

        alarmConfig?.testForAlarm(AlarmType.SYS, sysVal)
        alarmConfig?.testForAlarm(AlarmType.DIA, diaVal)
      }
      else -> return
    }
  }

  override fun requestSound(type: SoundType) {
    sound?.playSound(type)
  }

  override fun shutdownDevice() {
    alarmConfig?.deactivateAllAlarms()
    binding.apply {
      vsvEcg.simulationStarted = false
      vsvOxy.simulationStarted = false
      vsvCap.simulationStarted = false
    }
    sound?.clearAllSounds()
  }

  override fun stopSimulation() {

    /*ncService?.updateState(NCSState.IDLE)
    ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)

    if (bound == true) {
        unbindService(mConnection)
        bound = false
    }*/

    super.onBackPressed()
  }

  override fun startDevice() {
    sound?.createAllSounds()
    nibpConfig?.audioFinished()
    nibpConfig?.resetRepeatedMeasurement()

    binding.apply {
      vsvEcg.simulationStarted = true
      vsvOxy.simulationStarted = true
      vsvCap.simulationStarted = true
      setVitalSignView(ESViewType.ECG, simConfig.simState.ecgEnabled)
      setVitalSignView(ESViewType.PLETH, simConfig.simState.oxyEnabled)
      setVitalSignView(ESViewType.CAP, simConfig.simState.capEnabled)
      vsvEcg.needsGraphReset = true
      vsvOxy.needsGraphReset = true
      vsvCap.needsGraphReset = true
    }
  }

  @SuppressLint("SetTextI18n")
  override fun updateTimer(tick: Long) {
    binding.twBpRepeat.text = "(${tick.formatTimeMMSS})"
  }

  override fun updateTimer(alarmConfig: AlarmConfig, ms: Long) {
    binding.bMute.bootstrapText = BootstrapText.Builder(this, false)
      .addFontAwesomeIcon(FontAwesome.FA_BELL_SLASH)
      .addText(ms.formatTimeMMSS).build()
  }

  private fun initVitalSignGraphs() {

    binding.vsvEcg.callback = this
    binding.vsvOxy.callback = this
    binding.vsvCap.callback = this
    ecgCalculation.currentHR = simConfig.vitalSigns.ecg.hr
    oxyCalculation.currentNIBP = NIBP(simConfig.vitalSigns.nibp.sys, simConfig.vitalSigns.nibp.dia)
    binding.apply {
      vsvEcg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0)
      vsvOxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0)
      vsvCap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0)
    }
  }

  override fun updateAlarms() {
    alarmConfig?.testCurrentForAlarm()
  }

  override fun setFullscreen() {
    fullscreenHelper?.hide()
  }

  override fun updateSimConfig(simConfig: SimConfig) {
    this.simConfig = simConfig
  }

  override fun onStop() {
    super.onStop()

    alarmConfig?.onStop()
    sound?.clearAllSounds()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    onOffConfig?.openStopDialog(true)
  }

  override fun onResume() {
    binding.vsvEcg.resume()
    binding.vsvOxy.resume()
    binding.vsvCap.resume()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.hide()
    super.onResume()
  }

  override fun onPause() {
    binding.vsvEcg.pause()
    binding.vsvOxy.pause()
    binding.vsvCap.pause()
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }
}

