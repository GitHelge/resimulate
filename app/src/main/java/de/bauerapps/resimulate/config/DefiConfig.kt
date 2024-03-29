package de.bauerapps.resimulate.config

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import de.bauerapps.resimulate.*
import de.bauerapps.resimulate.databinding.ActivitySingleModeBinding
import de.bauerapps.resimulate.databinding.DefiChargeDialogBinding
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESDialog
import kotlin.math.roundToLong

enum class DefiState {
  Idle, Charging, Warning, Pending, Shock
}

class SingleModeActivityDefiConfig(val context: SingleModeActivity, private val sound: Sound?, var simConfig: SimConfig) {
  interface DefiConfigCallback {
    //fun updateLabel(energy: Int)
    //fun updateUI(state: DefiState)
    fun requestSound(type: SoundType)

    fun onCharge(tempDefi: Defi)
    fun onShock(tempConfig: SimConfig, shockSucceeded: Boolean)
  }

  private class DefiChargeBounds(val max: Int, val intermediate: Int, val min: Int)

  /** This variable stores the currently chosen charge Value. */
  var energy = 150

  /** Stores max, intermediate and min values for the defi charge.  */
  private val defiChargeBounds = DefiChargeBounds(400, 50, 5)

  /** This variable contains the automatic defibrillator discharge timer. */
  private var defiDischargeHandler = Handler(Looper.getMainLooper())

  /** Indicates, if the ECG peaks are marked by triangles for the synchronization of the
   * defibrillation. */
  var isSynchronized = false
    private set

  /** Indicates, if a defi shock is pending this is the case, when the shock-Button
   * is pressed and the next R-wave is still to be detected. */
  var isShockPending = false

  private var defiState = DefiState.Idle
  var defiConfigCallback: DefiConfigCallback? = null
  private var defiChargeDialog: ESDialog? = null

  init {
    context.binding.bDefiCharge.setOnClickListener { changeState(DefiState.Charging) }
    context.binding.bDefiShock.setOnClickListener { changeState(DefiState.Shock) }
    context.binding.bDefiEnergyUp.setOnClickListener { energyUp() }
    context.binding.bDefiEnergyDown.setOnClickListener { energyDown() }
    context.binding.bDefiSync.setOnClickListener {
      toggleSync()
      (it as ESBootstrapButton).setActiveBackground(isSynchronized)
    }
  }

  /** Used to reduce the defi charge energy and to update the corresponding Label.
   * @see energyUp */
  private fun energyDown() {
    if (energy - 5 >= defiChargeBounds.min) {
      energy -= if (energy - 50 >= defiChargeBounds.intermediate) 50 else 5

      updateLabel(energy)
    }
  }

  /** Used to increase the defi charge energy and to update the corresponding Label.
   * @see energyDown */
  private fun energyUp() {
    if (energy + 5 <= defiChargeBounds.max) {
      energy += if (energy >= defiChargeBounds.intermediate) 50 else 5

      updateLabel(energy)
    }
  }

  private fun charge() {
    val tempDefi = simConfig.simState.defi.deepCopy()
    tempDefi.energy = energy
    defiConfigCallback?.onCharge(tempDefi)
  }

  fun changeState(newState: DefiState) {
    if (newState == defiState) return

    when (newState) {
      DefiState.Idle -> {
        isShockPending = false
        context.binding.bDefiCharge.text = context.getString(R.string.charge)
      }
      DefiState.Charging -> {
        if (defiState == DefiState.Pending) {
          // Discharge on second click on Charge
          defiDischargeHandler.removeCallbacks(dischargeRunnable)
          discharge()
          return
        } else {
          defiConfigCallback?.requestSound(SoundType.Charging)
          charge()
        }
      }
      DefiState.Warning -> {
        defiConfigCallback?.requestSound(SoundType.Warning)
        defiDischargeHandler.postDelayed(dischargeRunnable, 30000)
      }
      DefiState.Pending -> {
        context.binding.bDefiCharge.text = context.getString(R.string.discharge)
      }
      DefiState.Shock -> {
        defiShock()
      }
    }
    defiState = newState
    updateUI(newState)
  }

  private var dischargeRunnable = Runnable { discharge() }
  private fun discharge() {
    changeState(DefiState.Idle)
  }

  /** Used to synchronously or asynchronously provide a shock to the patient.
   * This is depended on, if the synchronization is active (indicated by [isSynchronized]).
   * If a sync Shock is expected, the shock is put on hold, until the next peak is found in
   * the ECG. */
  private fun defiShock() {
    if (isSynchronized) {
      isShockPending = true
    } else {
      performShock()
    }
  }

  private fun performShock() {
    defiDischargeHandler.removeCallbacks(dischargeRunnable)
    defiConfigCallback?.requestSound(SoundType.Shock)
    context.binding.bDefiCharge.text = context.getString(R.string.charge)

    val tempConfig = simConfig.deepCopy()
    tempConfig.vitalSigns = simConfig.simState.defi.vitalSigns

    defiConfigCallback?.onShock(
      tempConfig,
      energy >= simConfig.simState.defi.energyThreshold
    )
  }

  /** Used to perform a shock after waiting for the R-peak. */
  fun syncShock() {
    isShockPending = false
    Handler(Looper.getMainLooper()).post { performShock() }
  }

  /** Used to deactivate the defi Syncronization, if no R-Peak can be detected. */
  fun deactivateSync() {
    isSynchronized = false
    context.binding.bDefiSync.setActiveBackground(false)
  }

  /** Used to toggle the UI and the flag for the visible ecg-peaks. */
  private fun toggleSync() {
    isSynchronized = !isSynchronized
  }


  @SuppressLint("SetTextI18n")
  private fun updateLabel(energy: Int) {
    context.binding.twDefiEnergy.text = "$energy\nJ"
  }

  private fun updateUI(state: DefiState) {
    when (state) {
      DefiState.Charging -> openDefiChargeDialog()
      DefiState.Warning -> context.binding.bDefiShock.isEnabled = true
      DefiState.Shock, DefiState.Idle -> context.binding.bDefiShock.isEnabled = false
      else -> return
    }
  }

  @SuppressLint("InflateParams")
  private fun openDefiChargeDialog() {
    defiChargeDialog = ESDialog(context, R.style.NoAnimDialog)

    val defiChargeDialogView = DefiChargeDialogBinding.inflate(context.layoutInflater)

    val chargeDuration = try {
      sound?.chargeSoundDuration ?: 2000
    } catch(e: IllegalStateException) {
      e.printStackTrace()
      2000
    }

    defiChargeDialogView.pbDefiCharge.apply {
      updateAnimationDuration = ((chargeDuration) * 0.9).roundToLong()
      progress = 100

      setOnProgressAnimationEndedListener { defiChargeDialog?.dismiss() }
    }

    defiChargeDialog?.apply {
      setContentView(defiChargeDialogView.root)
      //setOnDismissListener { fullscreenHelper?.delayedHide(0) }
      show()
    }
  }
}

class TraineeActivityDefiConfig(val context: TraineeActivity, private val sound: Sound?, var simConfig: SimConfig) {

  interface DefiConfigCallback {
    //fun updateLabel(energy: Int)
    //fun updateUI(state: DefiState)
    fun requestSound(type: SoundType)

    fun onCharge(tempDefi: Defi)
    fun onShock(tempConfig: SimConfig, shockSucceeded: Boolean)
  }

  private class DefiChargeBounds(val max: Int, val intermediate: Int, val min: Int)

  /** This variable stores the currently chosen charge Value. */
  var energy = 150

  /** Stores max, intermediate and min values for the defi charge.  */
  private val defiChargeBounds = DefiChargeBounds(400, 50, 5)

  /** This variable contains the automatic defibrillator discharge timer. */
  private var defiDischargeHandler = Handler(Looper.getMainLooper())

  /** Indicates, if the ECG peaks are marked by triangles for the synchronization of the
   * defibrillation. */
  var isSynchronized = false
    private set

  /** Indicates, if a defi shock is pending this is the case, when the shock-Button
   * is pressed and the next R-wave is still to be detected. */
  var isShockPending = false

  private var defiState = DefiState.Idle
  var defiConfigCallback: DefiConfigCallback? = null
  private var defiChargeDialog: ESDialog? = null

  init {
    context.binding.bDefiCharge.setOnClickListener { changeState(DefiState.Charging) }
    context.binding.bDefiShock.setOnClickListener { changeState(DefiState.Shock) }
    context.binding.bDefiEnergyUp.setOnClickListener { energyUp() }
    context.binding.bDefiEnergyDown.setOnClickListener { energyDown() }
    context.binding.bDefiSync.setOnClickListener {
      toggleSync()
      (it as ESBootstrapButton).setActiveBackground(isSynchronized)
    }
  }

  /** Used to reduce the defi charge energy and to update the corresponding Label.
   * @see energyUp */
  private fun energyDown() {
    if (energy - 5 >= defiChargeBounds.min) {
      energy -= if (energy - 50 >= defiChargeBounds.intermediate) 50 else 5

      updateLabel(energy)
    }
  }

  /** Used to increase the defi charge energy and to update the corresponding Label.
   * @see energyDown */
  private fun energyUp() {
    if (energy + 5 <= defiChargeBounds.max) {
      energy += if (energy >= defiChargeBounds.intermediate) 50 else 5

      updateLabel(energy)
    }
  }

  private fun charge() {
    val tempDefi = simConfig.simState.defi.deepCopy()
    tempDefi.energy = energy
    defiConfigCallback?.onCharge(tempDefi)
  }

  fun changeState(newState: DefiState) {
    if (newState == defiState) return

    when (newState) {
      DefiState.Idle -> {
        isShockPending = false
        context.binding.bDefiCharge.text = context.getString(R.string.charge)
      }
      DefiState.Charging -> {
        if (defiState == DefiState.Pending) {
          // Discharge on second click on Charge
          defiDischargeHandler.removeCallbacks(dischargeRunnable)
          discharge()
          return
        } else {
          defiConfigCallback?.requestSound(SoundType.Charging)
          charge()
        }
      }
      DefiState.Warning -> {
        defiConfigCallback?.requestSound(SoundType.Warning)
        defiDischargeHandler.postDelayed(dischargeRunnable, 30000)
      }
      DefiState.Pending -> {
        context.binding.bDefiCharge.text = context.getString(R.string.discharge)
      }
      DefiState.Shock -> {
        defiShock()
      }
    }
    defiState = newState
    updateUI(newState)
  }

  private var dischargeRunnable = Runnable { discharge() }
  private fun discharge() {
    changeState(DefiState.Idle)
  }

  /** Used to synchronously or asynchronously provide a shock to the patient.
   * This is depended on, if the synchronization is active (indicated by [isSynchronized]).
   * If a sync Shock is expected, the shock is put on hold, until the next peak is found in
   * the ECG. */
  private fun defiShock() {
    if (isSynchronized) {
      isShockPending = true
    } else {
      performShock()
    }
  }

  private fun performShock() {
    defiDischargeHandler.removeCallbacks(dischargeRunnable)
    defiConfigCallback?.requestSound(SoundType.Shock)
    context.binding.bDefiCharge.text = context.getString(R.string.charge)

    val tempConfig = simConfig.deepCopy()
    tempConfig.vitalSigns = simConfig.simState.defi.vitalSigns

    defiConfigCallback?.onShock(
      tempConfig,
      energy >= simConfig.simState.defi.energyThreshold
    )
  }

  /** Used to perform a shock after waiting for the R-peak. */
  fun syncShock() {
    isShockPending = false
    Handler(Looper.getMainLooper()).post { performShock() }
  }

  /** Used to deactivate the defi Syncronization, if no R-Peak can be detected. */
  fun deactivateSync() {
    isSynchronized = false
    context.binding.bDefiSync.setActiveBackground(false)
  }

  /** Used to toggle the UI and the flag for the visible ecg-peaks. */
  private fun toggleSync() {
    isSynchronized = !isSynchronized
  }


  @SuppressLint("SetTextI18n")
  private fun updateLabel(energy: Int) {
    context.binding.twDefiEnergy.text = "$energy\nJ"
  }

  private fun updateUI(state: DefiState) {
    when (state) {
      DefiState.Charging -> openDefiChargeDialog()
      DefiState.Warning -> context.binding.bDefiShock.isEnabled = true
      DefiState.Shock, DefiState.Idle -> context.binding.bDefiShock.isEnabled = false
      else -> return
    }
  }

  @SuppressLint("InflateParams")
  private fun openDefiChargeDialog() {
    defiChargeDialog = ESDialog(context, R.style.NoAnimDialog)

    val defiChargeDialogView = DefiChargeDialogBinding.inflate(context.layoutInflater)

    val chargeDuration = try {
      sound?.chargeSoundDuration ?: 2000
    } catch(e: IllegalStateException) {
      e.printStackTrace()
      2000
    }

    defiChargeDialogView.pbDefiCharge.apply {
      updateAnimationDuration = ((chargeDuration) * 0.9).roundToLong()
      progress = 100

      setOnProgressAnimationEndedListener { defiChargeDialog?.dismiss() }
    }

    defiChargeDialog?.apply {
      setContentView(defiChargeDialogView.root)
      //setOnDismissListener { fullscreenHelper?.delayedHide(0) }
      show()
    }
  }
}