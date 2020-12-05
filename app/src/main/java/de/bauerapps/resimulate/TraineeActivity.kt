package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
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
import kotlinx.android.synthetic.main.activity_trainee_view.*
import kotlin.math.abs
import kotlin.math.roundToInt

class TraineeActivity : AppCompatActivity(),
  NearbyConnectionService.NCSCallback,
  NearbyConnectionService.NCSEndpointCallback,
  ESSurfaceView.Callback,
  PacerConfig.PacerCallback,
  DefiConfig.DefiConfigCallback,
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
  private var defiConfig: DefiConfig? = null
  private var alarmConfig: AlarmConfig? = null
  private var nibpConfig: NIBPConfig? = null

  private var pacerConfig: PacerConfig? = null
  private var onOffConfig: OnOffConfig? = null
  private var shockReceived = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_trainee_view)

    // Sets interface to portrait or landscape
    if (resources.getBoolean(R.bool.forceLandscape)) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    window?.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )


    initVitalSignGraphs()
    sound = Sound(this)
    defiConfig = DefiConfig(this, sound, simConfig)
    pacerConfig = PacerConfig(this, simConfig)
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

    b_back.setOnClickListener { onBackPressed() }

    val content: View = findViewById(android.R.id.content)

    content.afterMeasured {

      val availableHeight =
        this.height /*- ll_header.height*/ - ll_footer.height + ll_toggle_ui.height
      if (!resources.getBoolean(R.bool.is600dp) || availableHeight < ll_sidepanel_content.height) {

        ll_pacer_ui.visibility = View.GONE

        b_toggle_ui.setOnClickListener {

          val pacerVisible = ll_pacer_ui.visibility == View.VISIBLE

          ll_pacer_ui.visibility = if (pacerVisible) View.GONE else View.VISIBLE
          ll_defi_ui.visibility = if (pacerVisible) View.VISIBLE else View.GONE

          b_toggle_ui.text = if (pacerVisible)
            getString(R.string.show_pacer_module)
          else
            getString(R.string.show_defi_module)
        }
      } else {
        ll_toggle_ui.visibility = View.GONE
      }
      Log.i(
        TAG,
        "Available screen height: $availableHeight, scrollview content height: ${ll_sidepanel_content.height}"
      )

    }


    //countUpTimer.start()

    if (NCS.chosenEndpoint?.endpointState == NCSEndpointState.CONNECTED)
      b_connection_status.bootstrapBrand =
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
        if (isChecked) vsv_ecg.restart() else {
          vsv_ecg.clearStop()
          if (!simConfig.simState.oxyEnabled)
            tw_hr_value.text = "--"
        }
      }
      ESViewType.PLETH -> {
        if (isChecked) vsv_oxy.restart() else {
          vsv_oxy.clearStop()
          tw_spo2_value.text = "--"
          if (!simConfig.simState.ecgEnabled)
            tw_hr_value.text = "--"
        }
      }
      ESViewType.CAP -> {
        if (isChecked) vsv_cap.restart() else {
          vsv_cap.clearStop()
          tw_resp_rate_value.text = "--"
          tw_etco2_value.text = "--"
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

    fullscreenHelper = FullscreenHelper(CL_whole)
    fullscreenHelper?.delayedHide(200)
  }

  override fun onPacerUpdate(pacerState: PacerState) {
    val tempConfig = simConfig.deepCopy()
    tempConfig.simState.pacer = pacerState
    vsv_ecg.pacerEnergy = pacerState.energy
    ncService?.sendSomething(Gson().toJson(pacerState))
    simConfig = tempConfig
  }

  @SuppressLint("SetTextI18n")
  override fun onUpdateUI(type: PacerConfigType, value: Int) {
    when (type) {
      PacerConfigType.Energy -> tw_pacer_energy.text = "$value\nmA"
      PacerConfigType.Frequency -> tw_pacer_frequency.text = "$value\nbpm"
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

    vsv_oxy.performECGSync()
  }

  override fun onMeasurement(type: MeasurementType, value: Int) {
    when (type) {
      MeasurementType.ECG -> {
        if (!simConfig.simState.ecgEnabled) return
        runOnUiThread {
          tw_hr_label.text = getString(R.string.hr_bpm)
          tw_hr_value.text = if (value >= 20) "$value" else "--"

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

            tw_hr_label.text = getString(R.string.hr_bpm_oxy)
            tw_hr_value.text = if (avgHR >= 20) "$avgHR" else "--"
            alarmConfig?.testForAlarm(AlarmType.HR, avgHR)
          }

          tw_spo2_value.text = if (value != 0) "$value" else "--"
          alarmConfig?.testForAlarm(AlarmType.SPO2, value)
        }
      }
      MeasurementType.ETCO2 -> {
        if (!simConfig.simState.capEnabled) return
        runOnUiThread {

          tw_etco2_value.text = if (value != 0) "$value" else "--"
          alarmConfig?.testForAlarm(AlarmType.ETCO2, value)
        }
      }
      MeasurementType.RESP_RATE -> {
        if (!simConfig.simState.capEnabled) return
        runOnUiThread {
          tw_resp_rate_value.text = if (value != 0) "$value" else "--"
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
          vsv_ecg.drawSyncPeak = true
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
        tw_bp_sys_dia_value.text = "$sysText/$diaText"
        tw_bp_avg_value.text = "($avgText)"

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
    vsv_ecg.simulationStarted = false
    vsv_oxy.simulationStarted = false
    vsv_cap.simulationStarted = false
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
    vsv_ecg.simulationStarted = true
    vsv_oxy.simulationStarted = true
    vsv_cap.simulationStarted = true
    setVitalSignView(ESViewType.ECG, simConfig.simState.ecgEnabled)
    setVitalSignView(ESViewType.PLETH, simConfig.simState.oxyEnabled)
    setVitalSignView(ESViewType.CAP, simConfig.simState.capEnabled)
    vsv_ecg.needsGraphReset = true
    vsv_oxy.needsGraphReset = true
    vsv_cap.needsGraphReset = true
  }

  @SuppressLint("SetTextI18n")
  override fun updateTimer(tick: Long) {
    tw_bp_repeat.text = "(${tick.formatTimeMMSS})"
  }

  override fun updateTimer(alarmConfig: AlarmConfig, ms: Long) {
    b_mute.bootstrapText = BootstrapText.Builder(this, false)
      .addFontAwesomeIcon(FontAwesome.FA_BELL_SLASH)
      .addText(ms.formatTimeMMSS).build()
  }

  private fun initVitalSignGraphs() {

    vsv_ecg.callback = this
    vsv_oxy.callback = this
    vsv_cap.callback = this
    ecgCalculation.currentHR = simConfig.vitalSigns.ecg.hr
    oxyCalculation.currentNIBP = NIBP(simConfig.vitalSigns.nibp.sys, simConfig.vitalSigns.nibp.dia)

    vsv_ecg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0)
    vsv_oxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0)
    vsv_cap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0)
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
        Handler().postDelayed({
          ncService?.sendSomething(Gson().toJson(simConfig.simState.pacer))
        }, 500)
        color = R.color.success
      }
      NCSEndpointState.DISCONNECTED, NCSEndpointState.UNKNOWN -> {
        val isSearching = NCS.ncsState == NCSState.SEARCHING
        color = if (isSearching) R.color.warning else R.color.danger
      }
    }

    b_connection_status.bootstrapBrand = ESBrandStyle(R.color.colorPrimaryDark, color)
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
    vsv_ecg.resume()
    vsv_oxy.resume()
    vsv_cap.resume()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.delayedHide(100)
    super.onResume()
  }

  override fun onPause() {
    vsv_ecg.pause()
    vsv_oxy.pause()
    vsv_cap.pause()
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }
}

