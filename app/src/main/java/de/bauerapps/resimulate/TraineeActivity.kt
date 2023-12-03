package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import cn.pedant.SweetAlert.SweetAlertDialog
import de.bauerapps.resimulate.config.*
import de.bauerapps.resimulate.helper.*
import de.bauerapps.resimulate.simulations.*
import de.bauerapps.resimulate.threads.NCS
import de.bauerapps.resimulate.threads.NCSEndpointState
import de.bauerapps.resimulate.threads.NCSState
import de.bauerapps.resimulate.threads.NearbyConnectionService
import de.bauerapps.resimulate.views.ESColor
import de.bauerapps.resimulate.views.ESSurfaceView
import de.bauerapps.resimulate.views.ESViewType
import com.beardedhen.androidbootstrap.BootstrapText
import com.beardedhen.androidbootstrap.font.FontAwesome
import com.google.gson.Gson
import de.bauerapps.resimulate.databinding.ActivityTraineeViewBinding
import kotlin.math.abs
import kotlin.math.roundToInt

class TraineeActivity : AppCompatActivity(),
  NearbyConnectionService.NCSCallback,
  NearbyConnectionService.NCSEndpointCallback,
  ESSurfaceView.Callback,
  TraineePacerConfig.PacerCallback,
  TraineeActivityDefiConfig.DefiConfigCallback,
  Sound.SoundCallback,
  Measurement.MeasurementCallback,
  ECGCalculation.Callback,
  NIBPConfig.NIBPConfigCallback, AlarmConfig.AlarmConfigCallback, OnOffConfig.Callback {


  companion object {
    const val TAG = "TraineeActivity"
  }

  private var ncService: NearbyConnectionService? = null
  private var fullscreenHelper: FullscreenHelper? = null
  private var bound: Boolean? = null

  private var needsReconnection: Boolean? = null

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
  private var defiConfig: TraineeActivityDefiConfig? = null
  private var alarmConfig: AlarmConfig? = null
  private var nibpConfig: NIBPConfig? = null

  private var pacerConfig: TraineePacerConfig? = null
  private var onOffConfig: OnOffConfig? = null
  private var shockReceived = false

  lateinit var binding: ActivityTraineeViewBinding;

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityTraineeViewBinding.inflate(layoutInflater)
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
    defiConfig = TraineeActivityDefiConfig(this, sound, simConfig)
    pacerConfig = TraineePacerConfig(this, simConfig)
    onOffConfig = OnOffConfig(this)
    alarmConfig = AlarmConfig(this, sound)
    nibpConfig = NIBPConfig(this)

    onOffConfig?.callback = this
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

    content.afterMeasured {

      val availableHeight =
        this.height /*- ll_header.height*/ - binding.llFooter.height + binding.llToggleUi.height
      if (!resources.getBoolean(R.bool.is600dp) || availableHeight < binding.llSidepanelContent.height) {

        binding.llPacerUi.visibility = View.GONE

        binding.bToggleUi.setOnClickListener {

          val pacerVisible = binding.llPacerUi.visibility == View.VISIBLE

          binding.llPacerUi.visibility = if (pacerVisible) View.GONE else View.VISIBLE
          binding.llDefiUi.visibility = if (pacerVisible) View.VISIBLE else View.GONE

          binding.bToggleUi.text = if (pacerVisible)
            getString(R.string.show_pacer_module)
          else
            getString(R.string.show_defi_module)
        }
      } else {
        binding.llToggleUi.visibility = View.GONE
      }
      Log.i(
        TAG,
        "Available screen height: $availableHeight, scrollview content height: ${binding.llSidepanelContent.height}"
      )

    }


    //countUpTimer.start()

    if (NCS.chosenEndpoint?.endpointState == NCSEndpointState.CONNECTED)
      binding.bConnectionStatus.bootstrapBrand =
        ESBrandStyle(R.color.bootstrap_gray_dark, R.color.success)

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

    when (type) {
      ESViewType.ECG -> {
        if (isChecked) binding.vsvEcg.restart() else {
          binding.vsvEcg.clearStop()
          if (!simConfig.simState.oxyEnabled)
            binding.twHrValue.text = "--"
        }
      }
      ESViewType.PLETH -> {
        if (isChecked) binding.vsvOxy.restart() else {
          binding.vsvOxy.clearStop()
          binding.twSpo2Value.text = "--"
          if (!simConfig.simState.ecgEnabled)
            binding.twHrValue.text = "--"
        }
      }
      ESViewType.CAP -> {
        if (isChecked) binding.vsvCap.restart() else {
          binding.vsvCap.clearStop()
          binding.twRespRateValue.text = "--"
          binding.twEtco2Value.text = "--"
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
    ncService?.sendSomething(Gson().toJson(pacerState))
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
    when (type) {
      MeasurementType.ECG -> {
        if (!simConfig.simState.ecgEnabled) return
        runOnUiThread {
          binding.twHrLabel.text = getString(R.string.hr_bpm)
          binding.twHrValue.text = if (value >= 20) "$value" else "--"

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

            binding.twHrLabel.text = getString(R.string.hr_bpm_oxy)
            binding.twHrValue.text = if (avgHR >= 20) "$avgHR" else "--"
            alarmConfig?.testForAlarm(AlarmType.HR, avgHR)
          }

          binding.twSpo2Value.text = if (value != 0) "$value" else "--"
          alarmConfig?.testForAlarm(AlarmType.SPO2, value)
        }
      }
      MeasurementType.ETCO2 -> {
        if (!simConfig.simState.capEnabled) return
        runOnUiThread {

          binding.twEtco2Value.text = if (value != 0) "$value" else "--"
          alarmConfig?.testForAlarm(AlarmType.ETCO2, value)
        }
      }
      MeasurementType.RESP_RATE -> {
        if (!simConfig.simState.capEnabled) return
        runOnUiThread {
          binding.twRespRateValue.text = if (value != 0) "$value" else "--"
          alarmConfig?.testForAlarm(AlarmType.RESP_RATE, value)
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
      ncService?.sendSomething(Const.SHOCK_ID)

      Log.i(TAG, "onShock: ${Const.SHOCK_ID} send: ${ncService != null}")
    } else {
      // Shock not succeeded. Still draw shock artifact
      ecgCalculation.drawShock = true
    }

  }

  override fun onCharge(tempDefi: Defi) {
    simConfig.simState.defi = tempDefi
    val defiParams = DefiParams(tempDefi.energy, tempDefi.energyThreshold)
    ncService?.sendSomething(Gson().toJson(defiParams))

    Log.i(TAG, "onCharge: $defiParams send: ${ncService != null}")
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
    binding.vsvEcg.simulationStarted = false
    binding.vsvOxy.simulationStarted = false
    binding.vsvCap.simulationStarted = false
    sound?.clearAllSounds()
  }

  override fun updateAlarms() {
    // Update the Alarms because the config was changed
    alarmConfig?.testCurrentForAlarm()
  }

  override fun stopSimulation() {

    ncService?.updateState(NCSState.IDLE)
    ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)

    if (bound == true) {
      unbindService(mConnection)
      bound = false
    }

    super.onBackPressed()
  }

  override fun startDevice() {
    sound?.createAllSounds()
    nibpConfig?.audioFinished()
    nibpConfig?.resetRepeatedMeasurement()
    binding.vsvEcg.simulationStarted = true
    binding.vsvOxy.simulationStarted = true
    binding.vsvCap.simulationStarted = true
    setVitalSignView(ESViewType.ECG, simConfig.simState.ecgEnabled)
    setVitalSignView(ESViewType.PLETH, simConfig.simState.oxyEnabled)
    setVitalSignView(ESViewType.CAP, simConfig.simState.capEnabled)
    binding.vsvEcg.needsGraphReset = true
    binding.vsvOxy.needsGraphReset = true
    binding.vsvCap.needsGraphReset = true
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

    binding.vsvEcg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0)
    binding.vsvOxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0)
    binding.vsvCap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0)
  }

  private var mConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
      Log.i(TAG, "NCS connected")
      ncService = (service as NearbyConnectionService.LocalBinder).ncService
      ncService?.ncsCallback = this@TraineeActivity
      ncService?.ncsEndpointCallback = this@TraineeActivity
      bound = true
    }

    override fun onServiceDisconnected(className: ComponentName) {
      Log.i(TAG, "NCS disconnected")
      ncService = null
      bound = false
    }
  }

  override fun receive(payload: ByteArray) {
    val receivedString = payload.toString(Charsets.UTF_8)

    val message = payload.toString(Charsets.UTF_8)

    if (message == Const.SIM_STOPPED) {
      SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
        .setTitleText(getString(R.string.sim_ended_header))
        .setContentText(getString(R.string.sim_ended_desc))
        .setConfirmText(ESApplication.getString(R.string.ok))
        .setConfirmClickListener {
          it.dismissWithAnimation()
          stopSimulation()
        }
        .show()
      return
    }

    val tempConfig = simConfig.deepCopy()

    when {
      message.contains(ParseNames.SimConfigClass.name) -> {
        val newSimConfig = Gson().fromJson(receivedString, SimConfig::class.java)
        Log.i(TAG, "Received new SimConfig.")
        simConfig = newSimConfig
      }
      message.contains(ParseNames.DefiClass.name) -> {
        val newDefi = Gson().fromJson(message, Defi::class.java)
        Log.i(TAG, "Received new PostShock-Config.")
        tempConfig.simState.defi = newDefi
        simConfig = tempConfig
      }

    }
  }

  override fun onEndpointStateUpdate(state: NCSEndpointState) {

    val color: Int
    when (state) {
      NCSEndpointState.FOUND -> {
        ncService?.requestConnection(NCS.chosenEndpoint?.id)
        color = R.color.warning
      }
      NCSEndpointState.LOST, NCSEndpointState.WAITING -> color = R.color.warning
      NCSEndpointState.CONNECTED -> {
        Handler(Looper.getMainLooper()).postDelayed({
          ncService?.sendSomething(Gson().toJson(simConfig.simState.pacer))
        }, 500)
        color = R.color.success
      }
      NCSEndpointState.DISCONNECTED, NCSEndpointState.UNKNOWN -> {
        val isSearching = NCS.ncsState == NCSState.SEARCHING
        color = if (isSearching) R.color.warning else R.color.danger
      }
    }

    binding.bConnectionStatus.bootstrapBrand = ESBrandStyle(R.color.colorPrimaryDark, color)
  }

  override fun error(title: String, description: String) {
    Snackbar.make(findViewById(android.R.id.content), description, Snackbar.LENGTH_SHORT).show()
  }

  override fun onStop() {
    super.onStop()

    if (bound == true) {

      unbindService(mConnection)
      Log.i(TAG, "Unbound Service")
      bound = false

      needsReconnection = true
      ncService?.updateState(NCSState.IDLE)
      ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)
    }

    alarmConfig?.onStop()
    sound?.clearAllSounds()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    onOffConfig?.openStopDialog(true)
  }

  override fun onStart() {
    super.onStart()

    bindService(
      Intent(this, NearbyConnectionService::class.java), mConnection,
      Context.BIND_AUTO_CREATE
    )

    // TODO: How long should the reconnection phase be?
    if (needsReconnection == true) {
      ncService?.updateState(NCSState.SEARCHING)
      sound?.createAllSounds()
    }
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

