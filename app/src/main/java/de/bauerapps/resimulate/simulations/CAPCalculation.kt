package de.bauerapps.resimulate.simulations

import de.bauerapps.resimulate.RespRatio
import de.bauerapps.resimulate.SimConfig
import de.bauerapps.resimulate.helper.BaselineDrift
import de.bauerapps.resimulate.helper.isZero
import de.bauerapps.resimulate.views.ESViewType
import kotlin.math.exp
import kotlin.math.roundToInt

class CAPCalculation(
  simConfig: SimConfig,
  private var cpr: CPRCalculation
) {

  private class RecentETCO2(var old: Double, var new: Double)

  /** This counter contains the current point in time. */
  private var simTime = 0.0

  /** Contains the old respiratory-rate used in the dynamic change process. */
  private var oldRR: Int? = null

  /** Contains the old etco2-value used in the dynamic change process. */
  private var oldETCO2: Int? = null

  /** Contains the time since last respiratory-rate config change. */
  private var timeSinceRRChange = 0.0

  /** Contains the time since last etco2-value config change. */
  private var timeSinceETCO2Change = 0.0

  /** Contains the duration since last dynamic respiratory-rate adaption using the logistic
   * function. Used to adapt the frequency only at specific points. */
  private var timeSinceIntermediateRRChange = 0.0

  /** Contains the duration since last dynamic etco2 adaption using the logistic
   * function. Used to adapt the frequency only at specific points. */
  private var timeSinceIntermediateETCO2Change = 0.0

  /** Contains the current respiratory-rate. */
  private var currentRR: Int = -1

  /** Contains the old "currentRR" after a config change. It is used to react to changes
   * to the <currentRR> while an old change is still in process. */
  private var latestReachedRR: Int = -1

  /** Stores the randomized respiratory-rate to give more realistic values. */
  private var randomizedRR: Int = -1

  /** Contains the current etco2-value. */
  private var currentETCO2: Int = 0

  /** Contains the old "currentETCO2" after a config change. It is used to react to changes
   * to the [currentETCO2] while an old change is still in process. */
  private var latestReachedETCO2: Int = -1

  /** Stores the randomized etco2-value to give more realistic values. */
  private var randomizedETCO2: Int = -1

  /** Contains the two most recent y-values used to find the correct point for the
   * dynamic change. */
  private var recentETCO2CurveValue = RecentETCO2(0.0, 0.0)

  /** Locally saves the current flag if the patient has COPD. It is saved locally so that
   * changes are only performed at fixed points in time. */
  private var hasCOPD = false

  /** Locally saves the current resp ratio so that changes are only performed at fixed points
   * in time. */
  private var respRatio = RespRatio.Normal

  private var timestep = 0.0

  private val baselineDrift = BaselineDrift(ESViewType.CAP)

  /** Calculates and returns the gradient between two values. */
  private fun getGradient(yNew: Double, yOld: Double): Double {
    return (yNew - yOld) / timestep
  }

  var forceChange = false

  var simConfig = simConfig
    set(value) {

      if (forceChange) {
        oldETCO2 = value.vitalSigns.cap.etco2
        oldRR = value.vitalSigns.cap.respRate
        currentRR = value.vitalSigns.cap.respRate
        currentETCO2 = value.vitalSigns.cap.etco2
      }

      timeSinceRRChange = 0.0
      timeSinceETCO2Change = 0.0
      field = value
      forceChange = false
    }

  /** Calculates the dynamic changing values for the etco2 max value. */
  private fun calculateCurrentETCO2(newETCO2: Int): Int {
    if (timeSinceETCO2Change.isZero() && currentETCO2 != -1) {
      // Aha, change was performed:
      latestReachedETCO2 = currentETCO2

      // Blocks the etco2 from performing changes when the capnogramm is near the baseline.
      if (getGradient(recentETCO2CurveValue.new, recentETCO2CurveValue.old) <= -0.1) {
        return currentETCO2
      } else if (recentETCO2CurveValue.new > 0.1 && recentETCO2CurveValue.old > 0.1) {
        return currentETCO2
      }

      // A rising part is reached... perform changes NOW.
    }

    timeSinceETCO2Change += timestep

    val tempETCO2: Int = when {
      latestReachedETCO2 != -1 -> latestReachedETCO2
      currentETCO2 != -1 -> currentETCO2
      else -> newETCO2
    }

    /* The latest reached etco2 has the highest priority.
        It gives the ability for continuous changes of the rate even if multiple changes are
        performed in quick succession. Search (Strg+F) "latestReachedRR" for further
        information. */

    val valDiff = newETCO2 - tempETCO2


    val cd = simConfig.simState.changeDuration
    /* t0 of the logistic function defines the duration to reach the mid point, with the
    steepest slope. */
    val t0 = cd / 2.0

    /* k is the steepness of the curve. The adaptibility through the dependency on t0 is
    chosen to make k more adaptable to different speeds and heights of changes.*/
    val k = 3.0 / t0
    val t = timeSinceETCO2Change

    /* Based on the characteristic of the logistic function, the end-value (max OR min)
    is approximately reached at 2*t0. */
    if (timeSinceETCO2Change > 2 * t0 || newETCO2 == tempETCO2 || tempETCO2 <= 4) {
      timeSinceIntermediateETCO2Change = 0.0
      return newETCO2
    }

    timeSinceIntermediateETCO2Change += timestep

    if (timeSinceIntermediateETCO2Change > 60.0 / currentETCO2) {
      timeSinceIntermediateETCO2Change = 0.0
      // With the logistic function, the current respiratory-rate is calculated.
      currentETCO2 = (tempETCO2 + valDiff / (1 + (exp(-k * (t - t0))))).roundToInt()
    }

    return currentETCO2
  }

  /** This function is used to calculated the current rr-value. It is used for the dynamic
   * change. */
  private fun calculateCurrentRR(newRR: Int): Int {

    if (timeSinceRRChange.isZero() && currentRR != -1) {
      // Aha, change was performed:
      // When a change is performed in the config, the latest reached resp-rate is updated.
      latestReachedRR = currentRR

      // Blocks the etco2 from performing changes when the capnogram is near the baseline.
      if (getGradient(recentETCO2CurveValue.new, recentETCO2CurveValue.old) <= -0.1) {
        return currentRR
      } else if (recentETCO2CurveValue.new > 0.1 && recentETCO2CurveValue.old > 0.1) {
        return currentRR
      }

      // A rising part is reached... perform changes NOW.
    }

    timeSinceRRChange += timestep

    val tempRR: Int = when {
      latestReachedRR != -1 -> latestReachedRR
      currentRR != -1 -> currentRR
      else -> newRR
    }

    /* The latest reached resp-rate has the highest priority.
        It gives the ability for continuous changes of the rate even if multiple changes are
        performed in quick succession. E.g. changing from 15 -> 20 and changing back to 12
        before the 20 was reached. Therefore, the 17 or 18 is used as a new "latestReachedRR"
        to perform further changes from there. */

    val valDiff = newRR - tempRR

    val cd = simConfig.simState.changeDuration
    /* t0 of the logistic function defines the duration to reach the mid point, with the
    steepest slope. */
    val t0 = cd / 2.0

    /* k is the steepness of the curve. The adaptibility through the dependency on t0 is
    chosen to make k more adaptable to different speeds and heights of changes.*/
    val k = 3.0 / t0
    val t = timeSinceRRChange

    /* Based on the characteristic of the logistic function, the end-value (max OR min)
    is approximately reached at 2*t0. */
    if (timeSinceRRChange > 2 * t0 || newRR == tempRR || tempRR <= 2) {
      timeSinceIntermediateRRChange = 0.0
      return newRR
    }

    timeSinceIntermediateRRChange += timestep

    if (timeSinceIntermediateRRChange > 60.0 / currentRR) {
      timeSinceIntermediateRRChange = 0.0
      // With the logistic function, the current respiratory-rate is calculated.
      currentRR = (tempRR + valDiff / (1 + (exp(-k * (t - t0))))).roundToInt()
    }

    return currentRR
  }


  /** Generates randomized values around a defined interval for the etco2.
   * @param value contains the value around which the randomized values are produced.
   * @param simTime contains the current point in time. */
  private fun randomizeETCO2(value: Int, simTime: Double): Int {
    // Uses the current randomized resp-rate which might get overwitten later.
    var randVal = randomizedETCO2

    /* Random values are generated at certain points in time.
    Only applied if the transmitted value is above 5. */
    if (simTime.isZero() && value > 5) {
      randVal = ((Math.random() - 0.5) * value * 0.15 + value).roundToInt()
    } else if (value <= 5) {
      randVal = value
    }

    if (randVal == -1) return value

    return randVal
  }

  /** Generates randomized values around a defined interval for the resp-rate.
   * @param value contains the value around which the randomized values are produced.
   * @param simTime contains the current point in time. */
  private fun randomizeRR(value: Int, simTime: Double): Int {
    // Uses the current randomized resp-rate which might get overwitten later.
    var randVal = randomizedRR

    /* Random values are generated at certain points in time.
        Only applied if the transmitted value is above 5.
    */
    if (simTime.isZero() && value > 5) {
      randVal = ((Math.random() - 0.5) * value * 0.15 + value).roundToInt()
    } else if (value <= 5) {
      randVal = value
    }

    if (randVal == -1) return value

    return randVal
  }

  /** With this function, the normal ~(1:2) ratio breathing curve is generated.
   * @param simTime - Contains the current simulation time.
   * @param period - Contains the current period of the breath.
   * @param randETCO2 - Contains the randomized etco2-max-value. */
  private fun calcNormal(simTime: Double, period: Double, randETCO2: Int): Double {

    val t = simTime

    // t0 and k are parameters used in the logistic-function.
    var y = 0.0
    var t0: Double
    var k: Double

    // If the expiratory duration is to long, the plateau-phase is skipped.
    val expDuration = if (hasCOPD) 4.0 else 2.0

    /* Segment AB:
    In this Segment, a logistic function is used to reach approx. 90% of the defined
    randETCO2. It t0 is chosen, so that the 90% is not actually reached, so that the
    change from B to C looks more smooth. */
    if (t < (expDuration / 7.0) * period) {
      t0 = ((expDuration / (if (hasCOPD) 7.0 else 4.5)) * period) / 2.0
      k = (if (hasCOPD) 6.0 else 15.0) / t0
      y = (if (hasCOPD) 1.0 else 0.9) * randETCO2 / (1 + (exp(-k * (t - t0))))
    }

    /* Segment BC:
    In this Segment, a linear function is used to reach 100% of the defined randETCO2. */
    if (t >= (expDuration / 7.0) * period && t < (5.5 / 7.0) * period) {
      y = if (hasCOPD) {
        randETCO2.toDouble()
      } else {
        0.9 * randETCO2 + (0.1 * randETCO2) * (t - ((2.2 / 7.0) * period)) / ((3.5 / 7.0) * period)
      }
    }

    /* Segment CD:
    In this Segment, a logistic function is used to decrease the randETCO2 to 0%.
    This stage models the Inspiration phase. */
    if (t >= (5.5 / 7.0) * period && t < (6.5 / 7.0) * period) {
      t0 = (5.5 / 7.0) * period + ((1.0 / 7.0) * period) / 2.0
      k = 60.0 / t0
      y = randETCO2 - randETCO2 / (1 + (exp(-k * (t - t0))))
    }

    /* Segment DE:
    No Carbon-Dioxid flow in this phase. */
    if (t >= (6.5 / 7.0) * period) {
      y = 0.0
    }

    return y
  }

  /** With this function, the exerted or hyperventilation ~(1:1) ratio breathing curve
   * is generated.
   * @param simTime contains the current simulation time.
   * @param period contains the current period of the breath.
   * @param randETCO2 contains the randomized etco2-max-value. */
  private fun calcHyper(simTime: Double, period: Double, randETCO2: Int): Double {

    val t = simTime

    // t0 and k are parameters used in the logistic-function.
    var y = 0.0
    var t0: Double
    var k: Double

    // If the expiratory duration is to long, the plateau-phase is skipped.
    val expDuration = if (hasCOPD) 4.0 else 2.0

    /* Segment AB:
    In this Segment, a logistic function is used to reach approx. 90% of the defined
    randETCO2. It t0 is chosen, so that the 90% is not actually reached, so that the
    change from B to C looks more smooth. */
    if (t < (expDuration / 7.0) * period) {
      t0 = ((expDuration / (if (hasCOPD) 7.0 else 4.5)) * period) / 2.0
      k = (if (hasCOPD) 6.0 else 17.0) / t0
      y = (if (hasCOPD) 1.0 else 0.9) * randETCO2 / (1 + (exp(-k * (t - t0))))
    }

    /* Segment BC:
    In this Segment, a linear function is used to reach 100% of the defined randETCO2. */
    if (t >= (expDuration / 7.0) * period && t < (4.0 / 7.0) * period) {
      y = if (hasCOPD) {
        randETCO2.toDouble()
      } else {
        0.9 * randETCO2 + (0.1 * randETCO2) * (t - ((2.0 / 7.0) * period)) / ((2.0 / 7.0) * period)
      }
    }

    /* Segment CD:
    In this Segment, a logistic function is used to decrease the randETCO2 to 0%.
    This stage models the Inspiration phase. */
    if (t >= (4.0 / 7.0) * period && t < period) {
      t0 = (4.0 / 7.0) * period + ((1.5 / 7.0) * period) / 2.0
      k = 60.0 / t0
      y = randETCO2 - randETCO2 / (1 + (exp(-k * (t - t0))))
    }

    return y
  }

  /** With this function, the sleep or hypoventilation ~(1:4) ratio breathing curve
   * is generated.
   * @param simTime - Contains the current simulation time.
   * @param period - Contains the current period of the breath.
   * @param randETCO2 - Contains the randomized etco2-max-value. */
  private fun calcHypo(simTime: Double, period: Double, randETCO2: Int): Double {
    val t = simTime

    // t0 and k are parameters used in the logistic-function.
    var y = 0.0
    var t0: Double
    var k: Double

    // If the expiratory duration is to long, the plateau-phase is skipped.
    val expDuration = if (hasCOPD) 4.0 else 2.0

    /* Segment AB:
    In this Segment, a logistic function is used to reach approx. 90% of the defined
    randETCO2. It t0 is chosen, so that the 90% is not actually reached, so that the
    change from B to C looks more smooth. */
    if (t < (expDuration / 7.0) * period) {
      t0 = ((expDuration / (if (hasCOPD) 7.0 else 4.5)) * period) / 2.0
      k = (if (hasCOPD) 6.0 else 17.0) / t0
      y = (if (hasCOPD) 1.0 else 0.9) * randETCO2 / (1 + (exp(-k * (t - t0))))
    }

    /* Segment BC:
    In this Segment, a linear function is used to reach 100% of the defined randETCO2. */
    if (t >= (expDuration / 7.0) * period && t < (6.0 / 7.0) * period) {
      y = if (hasCOPD) {
        randETCO2.toDouble()
      } else {
        0.9 * randETCO2 + (0.1 * randETCO2) * (t - ((2 / 7) * period)) / ((4 / 7) * period)
      }
    }

    /* Segment CD:
    In this Segment, a logistic function is used to decrease the randETCO2 to 0%.
    This stage models the Inspiration phase. */
    if (t >= (6.0 / 7.0) * period && t < (7.0 / 7.0) * period) {
      t0 = (6.0 / 7.0) * period + ((1.0 / 7.0) * period) / 2.0
      k = 70.0 / t0
      y = randETCO2 - randETCO2 / (1 + (exp(-k * (t - t0))))
    }

    return y
  }

  /** Calculates the etco2-curve values based on the resp-rate. */
  fun calc(timestep: Double): Double {
    val respRate = simConfig.vitalSigns.cap.respRate
    val etco2MaxValue = simConfig.vitalSigns.cap.etco2
    this.timestep = timestep

    // Set initial values:
    if (oldRR == null) oldRR = respRate
    if (oldETCO2 == null) oldETCO2 = etco2MaxValue

    if (oldRR != respRate) {
      // Aha, the respiratory-rate was changed.
      oldRR = respRate
      timeSinceRRChange = 0.0
    }

    if (oldETCO2 != etco2MaxValue) {
      // Aha, the respiratory-rate was changed.
      oldETCO2 = etco2MaxValue
      timeSinceETCO2Change = 0.0
    }

    currentRR = calculateCurrentRR(respRate)
    currentETCO2 = calculateCurrentETCO2(etco2MaxValue)

    val randRR = randomizeRR(currentRR, simTime)
    // Saves the new randomized resp-rate.
    randomizedRR = randRR

    val randETCO2 = randomizeETCO2(currentETCO2, simTime)
    // Saves the new randomized etco2-max-value.
    randomizedETCO2 = randETCO2

    if (randETCO2 == 0 || randRR == 0) {
      simTime = 0.0
      var temp = (Math.random() - 0.5) * simConfig.vitalSigns.cap.noise

      if (simConfig.simState.hasCPR)
        temp += cpr.calc(timestep)

      temp += baselineDrift.calc(timestep)

      /* Necessary for the dynamic respiratory-rate change. */
      recentETCO2CurveValue.old = 0.0
      recentETCO2CurveValue.new = 0.0

      return temp
    }

    /* Necessary for the dynamic respiratory-rate change. */
    recentETCO2CurveValue.old = recentETCO2CurveValue.new

    val period = 60.0 / randRR

    val t = simTime
    var etco2CurveValue: Double

    etco2CurveValue = when (respRatio) {
      RespRatio.Normal -> { /* I:E = ~1:2 */
        calcNormal(t, period, randETCO2)
      }
      RespRatio.HypoVent -> {
        /* I:E = ~1:3 */
        calcHypo(t, period, randETCO2)
      }
      RespRatio.HyperVent -> { /* I:E = ~1:1 */
        calcHyper(t, period, randETCO2)
      }
    }

    simTime += timestep
    if (simTime > 60.0 / randRR) {
      // Resets the simulation time once per period:
      simTime = 0.0
      // Updates the copd flag once per period:
      hasCOPD = simConfig.simState.hasCOPD
      // Updates the ratio once per period:
      respRatio = simConfig.simState.respRatio
    }

    recentETCO2CurveValue.new = etco2CurveValue

    if (simConfig.simState.hasCPR) {
      etco2CurveValue += cpr.calc(timestep)
    }
    etco2CurveValue += (Math.random() - 0.5) * simConfig.vitalSigns.cap.noise
    etco2CurveValue += baselineDrift.calc(timestep)

    return etco2CurveValue
  }
}