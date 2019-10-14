package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.material.snackbar.Snackbar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import de.bauerapps.resimulate.config.VitalSignConfig
import de.bauerapps.resimulate.helper.*
import de.bauerapps.resimulate.simulations.*
import de.bauerapps.resimulate.threads.NCS
import de.bauerapps.resimulate.threads.NCSEndpointState
import de.bauerapps.resimulate.threads.NCSState
import de.bauerapps.resimulate.threads.NearbyConnectionService
import de.bauerapps.resimulate.views.ESColor
import de.bauerapps.resimulate.views.ESDialog
import de.bauerapps.resimulate.views.ESSurfaceView
import de.bauerapps.resimulate.views.ESViewType
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_trainer_view.*
import kotlinx.android.synthetic.main.stop_sim_dialog.view.*

class TrainerActivity : AppCompatActivity(),
  NearbyConnectionService.NCSCallback,
  NearbyConnectionService.NCSEndpointCallback,
  ECGCalculation.Callback,
  ESSurfaceView.Callback,
  View.OnClickListener,
  VitalSignConfig.Callback,
  DynChangeConfig.Callback,
  PostShockConfig.Callback {

  companion object {
    const val TAG = "TrainerActivity"
  }

  private var ncService: NearbyConnectionService? = null
  private var fullscreenHelper: FullscreenHelper? = null
  private var bound: Boolean? = null
  private var needsReconnection: Boolean? = null
  private var stopSimulationDialog: ESDialog? = null

  private var dynChangeConfig: DynChangeConfig? = null
  private var psConfig: PostShockConfig? = null

  private var vsConfigs = mutableMapOf<VSConfigType, VitalSignConfig>()

  /** Stores a counting value to draw the pacer peaks at the specified pacer frequency. */
  private var pacedDeltaX = 0.0
  private var simConfig = SimConfig()
    set(value) {
      val tempConfig = simConfig.deepCopy()
      onUpdateSimConfig(tempConfig, value)
      field = value
    }
  private var ecgCalculation = ECGCalculation(simConfig, CPRCalculation(CPRType.ECG))
  private var oxyCalculation =
    OxyCalculation(simConfig, CPRCalculation(CPRType.SPO2), ecgCalculation)
  private var capCalculation = CAPCalculation(simConfig, CPRCalculation(CPRType.ETCO2))
  private var shockReceived = false

  private fun onUpdateSimConfig(oldValue: SimConfig, newValue: SimConfig) {

    ecgCalculation.forceChange = shockReceived ||
        (!newValue.simState.ecgEnabled)

    oxyCalculation.forceChange = shockReceived ||
        (!newValue.simState.oxyEnabled)

    capCalculation.forceChange = shockReceived ||
        (!newValue.simState.capEnabled)

    if (shockReceived || ecgCalculation.forceChange) {
      ecgCalculation.simConfig = newValue
    } else
      ecgCalculation.pendingSimConfig = newValue

    capCalculation.simConfig = newValue
    oxyCalculation.simConfig = newValue

    val newPathology = newValue.vitalSigns.pathology

    if (newValue.vitalSigns != oldValue.vitalSigns) {
      if (oldValue.vitalSigns.pathology != newPathology) {
        updateVitalSignConfigs(newPathology, newValue.vitalSigns)
      }

      Log.i(TAG, "SimConfig updated. Current Pathology: ${newPathology.name}")
    }

    shockReceived = false
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_trainer_view)

    // Sets interface to portrait or landscape
    if (resources.getBoolean(R.bool.forceLandscape))
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    window?.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )

    initVitalSignGraphs()

    b_back.setOnClickListener(this)
    b_ok.setOnClickListener(this)

    dynChangeConfig = DynChangeConfig(this, simConfig)
    dynChangeConfig?.callback = this
    b_dynchange_config.setOnClickListener(this)

    psConfig = PostShockConfig(this, simConfig)
    psConfig?.callback = this
    b_ps_pathology_config.setOnClickListener(this)

    initUI()

    ecgCalculation.callback = this

    // Initially send the current config:
    Handler().postDelayed({
      ncService?.sendSomething(Gson().toJson(simConfig))
    }, 500)
  }

  private fun initUI() {

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
      b_defi_energy_thres_up, b_defi_energy_thres_down, tw_defi_energy_value, 50
    )

    vsConfigs.forEach { it.value.callback = this }

    setBoundsByPathology(vsConfigs, simConfig.vitalSigns.pathology)

    val simState = simConfig.simState
    val vitalSigns = simConfig.vitalSigns

    setVitalSignView(ESViewType.ECG, simState.ecgEnabled)
    b_ecg_toggle.setActiveBackground(simState.ecgEnabled)

    setVitalSignView(ESViewType.PLETH, simState.oxyEnabled)
    b_oxy_toggle.setActiveBackground(simState.oxyEnabled)

    setVitalSignView(ESViewType.CAP, simState.capEnabled)
    b_cap_toggle.setActiveBackground(simState.capEnabled)

    b_nibp_toggle.setActiveBackground(simState.nibpEnabled)

    b_ecg_toggle.setOnClickListener {
      val ecgEnabled = simConfig.simState.ecgEnabled
      simConfig.simState.ecgEnabled = !ecgEnabled
      setVitalSignView(ESViewType.ECG, !ecgEnabled)
      b_ecg_toggle.setActiveBackground(!ecgEnabled)
    }

    b_oxy_toggle.setOnClickListener {
      val oxyEnabled = simConfig.simState.oxyEnabled
      simConfig.simState.oxyEnabled = !oxyEnabled
      setVitalSignView(ESViewType.PLETH, !oxyEnabled)
      b_oxy_toggle.setActiveBackground(!oxyEnabled)
    }

    b_cap_toggle.setOnClickListener {
      val capEnabled = simConfig.simState.capEnabled
      simConfig.simState.capEnabled = !capEnabled
      setVitalSignView(ESViewType.CAP, !capEnabled)
      b_cap_toggle.setActiveBackground(!capEnabled)
    }

    b_nibp_toggle.setOnClickListener {
      simConfig.simState.nibpEnabled = !b_nibp_toggle.isActive
      ncService?.sendSomething(Gson().toJson(simConfig))
      b_nibp_toggle.setActiveBackground(!b_nibp_toggle.isActive)
    }

    b_copd.setOnClickListener {
      simConfig.simState.hasCOPD = !b_copd.isActive
      ncService?.sendSomething(Gson().toJson(simConfig))
      b_copd.setActiveBackground(!b_copd.isActive)
    }

    b_cpr.setOnClickListener {
      simConfig.simState.hasCPR = !b_cpr.isActive
      ncService?.sendSomething(Gson().toJson(simConfig))
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
      notifyRequireConfigPush()

      fullscreenHelper?.delayedHide(0)
    }

    esd_defi_pathology.setOnDropDownItemClickListener { _, v, _ ->
      val pathology = Pathology((v as TextView).text.toString())
      val defaultVS = DefaultVitalSigns.fromPathology(pathology)
      esd_defi_pathology.text = pathology.name

      simConfig.simState.defi.vitalSigns = defaultVS.deepCopy()
      notifyRequireConfigPush()
      //ncService?.sendSomething(Gson().toJson(simConfig.simState.defi))

      fullscreenHelper?.delayedHide(0)
    }

    esd_pathology.setOnDismissListener { fullscreenHelper?.delayedHide(0) }

    if (NCS.chosenEndpoint?.endpointState == NCSEndpointState.CONNECTED)
      b_connection_status.bootstrapBrand =
        ESBrandStyle(R.color.bootstrap_gray_dark, R.color.success)
  }

  private fun notifyRequireConfigPush() {
    if (!b_ok.isWarning)
      b_ok.setWarningBackground(true)
  }

  private fun updateVitalSignConfigs(pathology: Pathology, vs: VitalSigns) {
    esd_pathology.text = pathology.name

    setBoundsByPathology(vsConfigs, pathology)
    vsConfigs[VSConfigType.HR]?.update(vs.ecg.hr)
    vsConfigs[VSConfigType.SPO2]?.update(vs.oxy.spo2)
    vsConfigs[VSConfigType.ETCO2]?.update(vs.cap.etco2)
    vsConfigs[VSConfigType.RESP_RATE]?.update(vs.cap.respRate)
    vsConfigs[VSConfigType.SYS]?.update(vs.nibp.sys)
    vsConfigs[VSConfigType.DIA]?.update(vs.nibp.dia)
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

    ncService?.sendSomething(Gson().toJson(simConfig))
  }

  override fun onClick(v: View?) {
    when (v) {
      b_back -> onBackPressed()
      b_ok -> performConfigUpdateAndSend()
      b_dynchange_config -> dynChangeConfig?.openDialog(simConfig)
      b_ps_pathology_config -> psConfig?.openDialog(simConfig)
    }
  }

  private fun setBoundsByPathology(
    vsConfigs: Map<VSConfigType, VitalSignConfig>,
    pathology: Pathology
  ) {
    val vsBounds = pathology.getSpecificBounds()
    vsBounds.forEach { vsConfigs[it.key]?.updateBounds(it.value.first, it.value.second) }
  }

  private fun performConfigUpdateAndSend() {

    b_ok.setWarningBackground(false)

    val tempConfig: SimConfig = simConfig.deepCopy()
    val pathology = esd_pathology.text.toString()

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

    simConfig = tempConfig

    ncService?.sendSomething(Gson().toJson(simConfig))
  }


  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)

    fullscreenHelper = FullscreenHelper(CL_whole)
    fullscreenHelper?.delayedHide(200)
  }

  private fun initVitalSignGraphs() {

    vsg_ecg.callback = this
    vsg_oxy.callback = this
    vsg_cap.callback = this
    ecgCalculation.currentHR = simConfig.vitalSigns.ecg.hr
    oxyCalculation.currentNIBP = NIBP(simConfig.vitalSigns.nibp.sys, simConfig.vitalSigns.nibp.dia)

    vsg_ecg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0, true)
    vsg_oxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0, true)
    vsg_cap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0, true)
  }

  override fun pullValue(type: ESViewType, timestep: Double): Double {

    when (type) {
      ESViewType.ECG -> {

        val pacer = simConfig.simState.pacer
        val isThresholdReached = pacer.energy >= pacer.energyThreshold

        if (pacer.isEnabled) {
          pacedDeltaX += timestep
          if (pacedDeltaX >= 60.0 / pacer.frequency) {
            pacedDeltaX = 0.0
            if (!isThresholdReached || simConfig.vitalSigns.ecg.hr >= pacer.frequency) {

              vsg_ecg.pacerEnergy = pacer.energy
              vsg_ecg.drawPacerPeak = true
            }
          }
        }

        return ecgCalculation.calc(timestep)
      }
      ESViewType.PLETH -> {
        // If HR is deactivated, HR changes should still be possible.
        if (!simConfig.simState.ecgEnabled) {
          // Just calculate for dynamic HR change.
          ecgCalculation.calc(timestep)
        }

        return oxyCalculation.calc(timestep)
      }
      ESViewType.CAP -> {
        return capCalculation.calc(timestep)
      }
    }
  }

  override fun requestSync() {
    if (!simConfig.simState.oxyEnabled) return
    vsg_oxy.performECGSync()
  }

  override fun drawPacerPeak() {

    vsg_ecg.pacerEnergy = simConfig.simState.pacer.energy
    vsg_ecg.drawPacerPeak = true
  }

  override fun wasUpdated() {

    Handler(Looper.getMainLooper()).post { notifyRequireConfigPush() }
  }


  // CONNECTION RELATED METHODS:

  private var mConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
      Log.i(TAG, "NCS connected")
      ncService = (service as NearbyConnectionService.LocalBinder).ncService
      ncService?.ncsCallback = this@TrainerActivity
      ncService?.ncsEndpointCallback = this@TrainerActivity
      bound = true

      tw_connection_indicator.text = NCSEndpointState.getDesc(NCS.chosenEndpoint)
    }

    override fun onServiceDisconnected(className: ComponentName) {
      Log.i(TAG, "NCS disconnected")
      ncService = null
      bound = false
    }
  }

  override fun receive(payload: ByteArray) {

    val tempConfig = simConfig.deepCopy()

    val message = payload.toString(Charsets.UTF_8)
    Log.i(TAG, "Message Received: $message")

    if (message == Const.SHOCK_ID) {

      tempConfig.vitalSigns = simConfig.simState.defi.vitalSigns
      shockReceived = true
      simConfig = tempConfig
      ecgCalculation.drawShock = true
      return
    }

    when {
      message.contains(ParseNames.DefiParams.name) -> {
        val newDefiParams = Gson().fromJson(message, DefiParams::class.java)
        tempConfig.simState.defi.energy = newDefiParams.energy
        tempConfig.simState.defi.energyThreshold = newDefiParams.energyThreshold
        simConfig = tempConfig
      }
      message.contains(ParseNames.PacerStateClass.name) -> {
        val newPacerState = Gson().fromJson(message, PacerState::class.java)
        tempConfig.simState.pacer = newPacerState
        simConfig = tempConfig
      }
      else -> return
    }
  }

  @SuppressLint("SetTextI18n")
  override fun onEndpointStateUpdate(state: NCSEndpointState) {

    var appendText = ""
    val color: Int
    when (state) {
      NCSEndpointState.FOUND -> {
        when {
          NCS.lastFoundEndpoint == NCS.chosenEndpoint -> {
            ncService?.requestConnection(NCS.chosenEndpoint?.id)
            color = R.color.warning
          }
          NCS.chosenEndpoint?.endpointState != NCSEndpointState.CONNECTED -> {
            tw_connection_indicator.text = getString(R.string.no_endpoint_restart_necessary)
            color = R.color.danger
          }
          else -> return
        }
      }
      NCSEndpointState.LOST, NCSEndpointState.WAITING -> color = R.color.warning
      NCSEndpointState.CONNECTED -> {
        Handler().postDelayed({
          ncService?.sendSomething(Gson().toJson(simConfig))
        }, 500)
        color = R.color.success
      }
      NCSEndpointState.DISCONNECTED, NCSEndpointState.UNKNOWN -> {
        val isSearching = NCS.ncsState == NCSState.SEARCHING
        appendText = if (isSearching) " Reconnecting..." else ""
        color = if (isSearching) R.color.warning else R.color.danger
      }
    }

    b_connection_status.bootstrapBrand = ESBrandStyle(R.color.colorPrimaryDark, color)
    tw_connection_indicator.text = NCSEndpointState.getDesc(NCS.chosenEndpoint) + appendText
  }

  override fun onStateUpdate() {

    if (NCS.ncsState == NCSState.CONNECTED || NCS.ncsState == NCSState.DISCONNECTED) return

    val isActive = NCS.ncsState == NCSState.SEARCHING

    tw_connection_indicator.text =
      if (isActive) "Searching for Trainee..." else "Searching stopped."
  }

  override fun updateSimConfig(simConfig: SimConfig) {
    this.simConfig = simConfig
  }

  override fun error(title: String, description: String) {
    Snackbar.make(findViewById(android.R.id.content), description, Snackbar.LENGTH_SHORT).show()
  }

  override fun onStart() {
    super.onStart()

    bindService(
      Intent(this, NearbyConnectionService::class.java), mConnection,
      Context.BIND_AUTO_CREATE
    )

    if (needsReconnection == true) ncService?.updateState(NCSState.SEARCHING)
  }

  override fun onStop() {
    super.onStop()

    if (bound == true) {
      unbindService(mConnection)
      bound = false

      needsReconnection = true
      ncService?.updateState(NCSState.IDLE)
      ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)
    }

  }

  @SuppressLint("InflateParams")
  private fun openStopDialog() {
    stopSimulationDialog = ESDialog(this, R.style.NoAnimDialog)
    val stopSimulationDialogView = LayoutInflater.from(this)
      .inflate(R.layout.stop_sim_dialog, null)

    stopSimulationDialogView.apply {
      b_check.setOnClickListener {

        ncService?.sendSomething(Const.SIM_STOPPED)
        ncService?.updateState(NCSState.IDLE)
        ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)

        if (bound == true) {
          unbindService(mConnection)
          bound = false
        }
        stopSimulationDialog?.dismiss()
        super.onBackPressed()
      }
      b_cancel.setOnClickListener {
        stopSimulationDialog?.dismiss()
      }
    }

    stopSimulationDialog?.apply {
      setContentView(stopSimulationDialogView)
      //setOnDismissListener { callback?.hideStatusBar() }
      show()
    }
  }

  override fun onBackPressed() {
    openStopDialog()
  }

  override fun onResume() {
    fullscreenHelper?.delayedHide(200)
    vsg_ecg.resume()
    vsg_oxy.resume()
    vsg_cap.resume()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.delayedHide(100)
    super.onResume()
  }

  override fun onPause() {
    vsg_ecg.pause()
    vsg_oxy.pause()
    vsg_cap.pause()
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }
}
