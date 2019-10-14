package de.bauerapps.resimulate.simulations

import kotlin.math.abs
import kotlin.math.sin


/** Contains the CPR-Types indicating the type of the curve for which the artifacts are calculated. */
enum class CPRType {
  ECG, SPO2, ETCO2;

  fun defaultAmplitude(): Double {
    return when (this) {
      ECG -> 1.4
      SPO2 -> 1.0
      ETCO2 -> 0.8
    }
  }

  fun defaultPressPM(): Int {
    return when (this) {
      ECG -> 110
      SPO2 -> 110
      ETCO2 -> 110
    }
  }
}

/** In this class, the cardiopulmonary resuscitation-artifacts are calculated based on the curve-type. */
class CPRCalculation(private var type: CPRType) {

  private var pressRate: Int = type.defaultPressPM()
  private var amplitude: Double = type.defaultAmplitude()

  // Randomized Press Rate
  private var randPressRate: Double
  // Randomized Amplitude
  private var randAmplitude: Double
  private var time = 0.0

  /** In this function, the cardiopulmonary resuscitation-artifacts are calculated based on the
   * previously defined curve-type. */
  fun calc(timestep: Double): Double {

    time += timestep

    if (time > 60 / randPressRate) {
      randPressRate = (Math.random() - 0.5) * pressRate * 0.2 + pressRate
      randAmplitude = (Math.random() - 0.5) * amplitude * 0.4 + amplitude
      time = 0.0
    }

    return when (type) {
      CPRType.ECG -> abs(
        randAmplitude * sin(
          Math.PI * randPressRate /
              60 * ((Math.random() - 0.5) * 0.02 + time)
        )
      ) - 0.4
      CPRType.SPO2 -> abs(
        randAmplitude * sin(
          Math.PI * randPressRate /
              60 * ((Math.random() - 0.5) * 0.02 + time)
        )
      )
      CPRType.ETCO2 -> randAmplitude * sin(
        2 * Math.PI * randPressRate /
            60 * ((Math.random() - 0.5) * 0.02 + time)
      ) + randAmplitude * 1.2
    }
  }

  init {
    this.randPressRate = pressRate.toDouble()
    this.randAmplitude = amplitude
  }
}
