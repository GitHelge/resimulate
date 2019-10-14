package de.bauerapps.resimulate.helper

import de.bauerapps.resimulate.views.ESViewType
import kotlin.math.exp

class BaselineDrift(val type: ESViewType) {

  private var baselineDriftPeriod = 3.0
  private var baselineDriftSimTime = 0.0
  private var baselineDriftTarget = 0.0
  private var baselineDriftLastReached = 0.0

  private val targetFactor: Double =
    when (type) {
      ESViewType.ECG -> 0.5
      ESViewType.PLETH -> 10.0
      ESViewType.CAP -> 3.0
    }


  fun calc(timestep: Double): Double {

    if (baselineDriftSimTime >= baselineDriftPeriod) {
      baselineDriftSimTime = 0.0
      // Produces values between 3 and 7
      baselineDriftPeriod = (3.0 + 2.0 * (Math.random() - 0.5))
      baselineDriftLastReached = baselineDriftTarget
      baselineDriftTarget = (Math.random() - 0.5) * targetFactor
    }

    val x0 = baselineDriftPeriod / 2.0
    /* k is the steepness of the curve. The adaptibility through the
        dependency on x0 is chosen to make k more adaptable to different
        speeds and heights of changes.*/
    val k = 3.0 / x0
    val x = baselineDriftSimTime

    val L = baselineDriftTarget - baselineDriftLastReached
    val baseline = baselineDriftLastReached + L / (1 + (exp(-k * (x - x0))))

    baselineDriftSimTime += timestep
    return baseline
  }

}