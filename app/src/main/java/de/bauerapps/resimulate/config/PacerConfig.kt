package de.bauerapps.resimulate.config

import android.app.Activity
import de.bauerapps.resimulate.PacerState
import de.bauerapps.resimulate.SimConfig
import de.bauerapps.resimulate.SingleModeActivity
import de.bauerapps.resimulate.TraineeActivity
import de.bauerapps.resimulate.views.ESBootstrapButton

enum class PacerConfigType { Energy, Frequency }
class TraineePacerConfig(val context: TraineeActivity, var simConfig: SimConfig) {

  interface PacerCallback {
    fun onPacerUpdate(pacerState: PacerState)
    fun onUpdateUI(type: PacerConfigType, value: Int)
  }

  /** Stores the current frequency level. */
  var frequency = 60
  var pacerCallback: PacerCallback? = null

  private inner class Bounds(val max: Int, val min: Int)

  /** Stores the bounds of the Energy. */
  private val energyBounds = Bounds(150, 0)

  /** Stores the bounds of the Frequency. */
  private val frequencyBounds = Bounds(200, 50)


  /** Stores the current energy level. */
  private var energy = 0


  /** Stores a counting value to draw the pacer peaks at the specified [frequency]. */
  private var pacedDeltaX = 0.0

  /** Stores a flag, if the pacer is currently active. */
  private var isEnabled = false

  /** Checks and returns if the currently chosen energy is higher or equal to the
  threshold at which the pacer starts to overtake the internal heartrate. */
  private val isEnergyThresholdReached get() = energy >= simConfig.simState.pacer.energyThreshold

  init {
    context.binding.bPacerEnergyUp.setOnClickListener { energyUp() }
    context.binding.bPacerEnergyDown.setOnClickListener { energyDown() }
    context.binding.bPacerFrequencyUp.setOnClickListener { frequencyUp() }
    context.binding.bPacerFrequencyDown.setOnClickListener { frequencyDown() }
    context.binding.bPacer.setOnClickListener {
      togglePacing()
      (it as ESBootstrapButton).setActiveBackground(isEnabled)
    }
  }


  /** Used to updated the pacer endpointState in the database. This is necessary to also show pacer
  peaks on the TrainerActivity. */
  private fun updatePacerDB() {
    val pacerState = simConfig.simState.pacer.deepCopy()
    pacerState.energy = energy
    pacerState.frequency = frequency
    pacerState.isEnabled = isEnabled
    pacerCallback?.onPacerUpdate(pacerState)
  }

  /** Toggles the pacing and issues an UI update request. */
  private fun togglePacing() {
    isEnabled = !isEnabled
    updatePacerDB()
  }

  /** Increases the energy of the Pacer. */
  private fun energyUp() {
    if (energy + 10 <= energyBounds.max) {
      energy += 10
      pacerCallback?.onUpdateUI(PacerConfigType.Energy, energy)
      updatePacerDB()
    }
  }

  /** Decreases the energy of the Pacer. */
  private fun energyDown() {
    if (energy - 10 >= energyBounds.min) {
      energy -= 10
      pacerCallback?.onUpdateUI(PacerConfigType.Energy, energy)
      updatePacerDB()
    }
  }

  /** Increases the frequency of the Pacer. */
  private fun frequencyUp() {
    if (frequency + 10 <= frequencyBounds.max) {
      frequency += 10
      pacerCallback?.onUpdateUI(PacerConfigType.Frequency, frequency)
      updatePacerDB()
    }
  }

  /** Decreases the frequency of the Pacer. */
  private fun frequencyDown() {
    if (frequency - 10 >= frequencyBounds.min) {
      frequency -= 10
      pacerCallback?.onUpdateUI(PacerConfigType.Frequency, frequency)
      updatePacerDB()
    }
  }

  fun drawPacerPeak(timestep: Double) {
    if (isEnabled) {
      pacedDeltaX += timestep
      if (pacedDeltaX >= 60.0 / frequency) {
        pacedDeltaX = 0.0
        if (!isEnergyThresholdReached || simConfig.vitalSigns.ecg.hr >= frequency) {
          context.binding.vsvEcg.pacerEnergy = energy
          context.binding.vsvEcg.drawPacerPeak = true
        }
      }
    }
  }

  fun forceDrawPacerPeak() {
    context.binding.vsvEcg.pacerEnergy = energy
    context.binding.vsvEcg.drawPacerPeak = true
  }
}

class SingleModePacerConfig(val context: SingleModeActivity, var simConfig: SimConfig) {

  interface PacerCallback {
    fun onPacerUpdate(pacerState: PacerState)
    fun onUpdateUI(type: PacerConfigType, value: Int)
  }

  /** Stores the current frequency level. */
  var frequency = 60
  var pacerCallback: PacerCallback? = null

  private inner class Bounds(val max: Int, val min: Int)

  /** Stores the bounds of the Energy. */
  private val energyBounds = Bounds(150, 0)

  /** Stores the bounds of the Frequency. */
  private val frequencyBounds = Bounds(200, 50)


  /** Stores the current energy level. */
  private var energy = 0


  /** Stores a counting value to draw the pacer peaks at the specified [frequency]. */
  private var pacedDeltaX = 0.0

  /** Stores a flag, if the pacer is currently active. */
  private var isEnabled = false

  /** Checks and returns if the currently chosen energy is higher or equal to the
  threshold at which the pacer starts to overtake the internal heartrate. */
  private val isEnergyThresholdReached get() = energy >= simConfig.simState.pacer.energyThreshold

  init {
    context.binding.bPacerEnergyUp.setOnClickListener { energyUp() }
    context.binding.bPacerEnergyDown.setOnClickListener { energyDown() }
    context.binding.bPacerFrequencyUp.setOnClickListener { frequencyUp() }
    context.binding.bPacerFrequencyDown.setOnClickListener { frequencyDown() }
    context.binding.bPacer.setOnClickListener {
      togglePacing()
      (it as ESBootstrapButton).setActiveBackground(isEnabled)
    }
  }


  /** Used to updated the pacer endpointState in the database. This is necessary to also show pacer
  peaks on the TrainerActivity. */
  private fun updatePacerDB() {
    val pacerState = simConfig.simState.pacer.deepCopy()
    pacerState.energy = energy
    pacerState.frequency = frequency
    pacerState.isEnabled = isEnabled
    pacerCallback?.onPacerUpdate(pacerState)
  }

  /** Toggles the pacing and issues an UI update request. */
  private fun togglePacing() {
    isEnabled = !isEnabled
    updatePacerDB()
  }

  /** Increases the energy of the Pacer. */
  private fun energyUp() {
    if (energy + 10 <= energyBounds.max) {
      energy += 10
      pacerCallback?.onUpdateUI(PacerConfigType.Energy, energy)
      updatePacerDB()
    }
  }

  /** Decreases the energy of the Pacer. */
  private fun energyDown() {
    if (energy - 10 >= energyBounds.min) {
      energy -= 10
      pacerCallback?.onUpdateUI(PacerConfigType.Energy, energy)
      updatePacerDB()
    }
  }

  /** Increases the frequency of the Pacer. */
  private fun frequencyUp() {
    if (frequency + 10 <= frequencyBounds.max) {
      frequency += 10
      pacerCallback?.onUpdateUI(PacerConfigType.Frequency, frequency)
      updatePacerDB()
    }
  }

  /** Decreases the frequency of the Pacer. */
  private fun frequencyDown() {
    if (frequency - 10 >= frequencyBounds.min) {
      frequency -= 10
      pacerCallback?.onUpdateUI(PacerConfigType.Frequency, frequency)
      updatePacerDB()
    }
  }

  fun drawPacerPeak(timestep: Double) {
    if (isEnabled) {
      pacedDeltaX += timestep
      if (pacedDeltaX >= 60.0 / frequency) {
        pacedDeltaX = 0.0
        if (!isEnergyThresholdReached || simConfig.vitalSigns.ecg.hr >= frequency) {
          context.binding.vsvEcg.pacerEnergy = energy
          context.binding.vsvEcg.drawPacerPeak = true
        }
      }
    }
  }

  fun forceDrawPacerPeak() {
    context.binding.vsvEcg.pacerEnergy = energy
    context.binding.vsvEcg.drawPacerPeak = true
  }
}