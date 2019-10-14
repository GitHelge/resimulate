package de.bauerapps.resimulate.simulations

import de.bauerapps.resimulate.*
import de.bauerapps.resimulate.helper.BaselineDrift
import de.bauerapps.resimulate.helper.isZero
import de.bauerapps.resimulate.views.ESViewType
import kotlin.math.*

/** Performs all necessary calculations for the SPO2.
 *
 * Get some background-information about the waveforms from [Normal arterial line waveforms](https://derangedphysiology.com/main/cicm-primary-exam/required-reading/cardiovascular-system/Chapter%207.6.0/normal-arterial-line-waveforms)
 *
 * @param simConfig contains a reference to the global simulation configuration object.
 * @param ecgCalculation references the ecgCalculation class for synchronization reason. */
@Suppress("NAME_SHADOWING")
class OxyCalculation(
  simConfig: SimConfig,
  private var cpr: CPRCalculation,
  private var ecgCalculation: ECGCalculation
) {

  inner class NIBPTime(var sys: Double, var dia: Double)

  var simConfig = simConfig
    set(value) {

      if (forceChange) {
        oldNIBP = value.vitalSigns.nibp
        currentNIBP = value.vitalSigns.nibp
      }

      timeSinceNIBPChange = NIBPTime(0.0, 0.0)
      field = value
      forceChange = false
    }

  var forceChange = false

  /** Contains the old systolic and diastolic nibp, which is used in the dynamic changing process. */
  private var oldNIBP = NIBP(0, 0)

  /** Contains the currentTime of the nibp-simulation. */
  private var currentTime = 0.0

  /** Contains the time since last [currentNIBP] change to calculate the value dynamically. */
  private var timeSinceNIBPChange = NIBPTime(0.0, 0.0)

  /** Contains the time since the last intermediate nibp change. Is used to perform changes
   * to the [currentNIBP] only at the end of the current period. */
  private var timeSinceIntermediateNIBPChange = NIBPTime(0.0, 0.0)

  /** Contains the previous "currentNIBP" after a config change. It is used to react to changes
   * to the [currentNIBP] while an old change is still in process. */
  private var latestReachedNIBP = NIBP(-1, -1)

  /** When the ecgCalculation gets deactivated, the ecgCalculation.simTime is not counted up
   * anymore. Therefore, the old simTime value gets saved in each step to address this problem.
   */
  private var lastDeltaX: Double = 0.0

  /* Variable: hfIdleCount
      Is used to count up the time without ecg. If the time is higher then <maxHFIdleCount>,
      The spo2 curve is simulated on its own. */
  private var hfIdleCount = 0

  /* Constant: maxHFIdleCount
      Contains the max amount of steps that are tolerable for an ecg signal to be missing.
      4 steps equal to 4*0.02 seconds */
  private var maxHFIdleCount = 4

  private var timestep = 0.0

  private val baselineDrift = BaselineDrift(ESViewType.PLETH)

  /** Contains the current sys and dia-values. */
  var currentNIBP = NIBP(-1, -1)

  private fun checkPathology(type: PType): Boolean {
    return type == simConfig.vitalSigns.pathology.type
  }

  /* Function: errorFunction
      This function is used to build up the shape of the spo2-curve. It is used twice in
      the process for the first and the second peak. Even if the function is called
      errorFunction, it is solemnly used to *simulate the correct shape* of the spo2-curve.

      Parameters:
          x - Used to calculate the "error" for.

      See also:
          This implementation is based on an article:
          <Stand-alone error function errorFunction(x): https://www.johndcook.com/blog/2009/01/19/stand-alone-error-function-erf/>
          using the <Mathematical Simulation: https://www.desmos.com/calculator/t1v4bdpske>
          of the error-Function. Theory: <Wikipedia: https://en.wikipedia.org/wiki/Error_function#Approximation_with_elementary_functions>
      */
  private fun errorFunction(x: Double): Double {
    // For theoretical description, see above given links.
    val sign = sign(x)
    val x = abs(x)

    val a1 = 0.254829592
    val a2 = -0.284496736
    val a3 = 1.421413741
    val a4 = -1.453152027
    val a5 = 1.061405429
    val p = 0.3275911

    val t = 1.0 / (1.0 + p * x)
    val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-x * x)
    return sign * y
  }

  /* Function: getCurrentSysValue
      This function is used to calculated the current nibp sys-value. It was needed to
      have and dynamic change for the maximal nibp sys-value after a change.

      Parameters:
          newSys - Contains the new sys-maximum value.
          period - Contains the period of the sys-curve. This value is based on the hr.
          nibpChangeDuration - Contains an object which contains an boolean, indicating if
          the duration for an adaption is automatically chosen and an value, if a manual
          duration is chosen.

      Returns:
          The calculated current sys-max.

      See also:
          This function uses an implementation of the <Logistic Function: https://en.wikipedia.org/wiki/Logistic_function> to perform the dynamic changes.
      */
  private fun getCurrentSysValue(newSys: Int, period: Double, nibpChangeDuration: Int): Int {

    if (timeSinceNIBPChange.sys.isZero() && currentNIBP.sys != -1) {
      // Aha, change was performed:
      latestReachedNIBP.sys = currentNIBP.sys
    }

    timeSinceNIBPChange.sys += timestep

    val tempSys: Int = when {
      latestReachedNIBP.sys != -1 -> latestReachedNIBP.sys
      currentNIBP.sys != 0 -> currentNIBP.sys
      else -> newSys
    }

    val valDiff = newSys - tempSys

    /* t0 of the logistic function defines the duration to reach
    the mid point, with the steepest slope. */
    val t0 = nibpChangeDuration / 2.0

    /* k is the steepness of the curve. The adaptability through the dependency on t0 is
    chosen to make k more adaptable to different speeds and heights of changes.*/
    val k = 3.0 / t0
    val t = timeSinceNIBPChange.sys

    /* Based on the characteristic of the logistic function, the end-value (max OR min)
    is approximately reached at 2*t0. */
    if (timeSinceNIBPChange.sys > 2 * t0 || newSys == tempSys) {
      timeSinceIntermediateNIBPChange.sys = 0.0
      return newSys
    }

    timeSinceIntermediateNIBPChange.sys += timestep

    if (timeSinceIntermediateNIBPChange.sys > period) {
      timeSinceIntermediateNIBPChange.sys = 0.0
      // With the logistic function, the current hr is calculated.
      currentNIBP.sys = (tempSys + valDiff / (1 + (exp(-k * (t - t0))))).roundToInt()
    }

    return currentNIBP.sys
  }

  /* Function: getCurrentDiaValue
      This function is used to calculated the current nibp dia-value. It was needed to
      have and dynamic change for the maximal nibp dia-value after a change.

      Parameters:
          newDia - Contains the new dia-maximum value.
          period - Contains the period of the dia-curve. This value is based on the hr.
          nibpChangeDuration - Contains an object which contains an boolean, indicating if
          the duration for an adaption is automatically chosen and an value, if a manual
          duration is chosen.

      Returns:
          The calculated current dia-max.
      */
  private fun getCurrentDiaValue(newDia: Int, period: Double, nibpChangeDuration: Int): Int {

    if (timeSinceNIBPChange.dia.isZero() && currentNIBP.dia != -1) {
      // Aha, change was performed:
      latestReachedNIBP.dia = currentNIBP.dia
    }

    timeSinceNIBPChange.dia += timestep

    val tempDia: Int = when {
      latestReachedNIBP.dia != -1 -> latestReachedNIBP.dia
      currentNIBP.dia != 0 -> currentNIBP.dia
      else -> newDia
    }

    val valDiff = newDia - tempDia

    /* t0 of the logistic function defines the duration to reach
    the mid point, with the steepest slope. */
    val t0 = nibpChangeDuration / 2.0
    /* k is the steepness of the curve. The adaptability through the dependency on t0 is
    chosen to make k more adaptable to different speeds and heights of changes.*/
    val k = 3.0 / t0
    val t = timeSinceNIBPChange.dia

    /* Based on the characteristic of the logistic function, the end-value (max OR min)
    is approximately reached at 2*t0. */
    if (timeSinceNIBPChange.dia > 2 * t0 || newDia == tempDia) {
      timeSinceIntermediateNIBPChange.dia = 0.0
      return newDia
    }

    timeSinceIntermediateNIBPChange.dia += timestep

    if (timeSinceIntermediateNIBPChange.dia > period) {
      timeSinceIntermediateNIBPChange.dia = 0.0
      // With the logistic function, the current hr is calculated.
      currentNIBP.dia = (tempDia + valDiff / (1 + (exp(-k * (t - t0))))).roundToInt()
    }

    return currentNIBP.dia
  }

  /* Function: calcFullSpo2
      This function calculates the full spo2-curve-value for the given point in time.
      "full" in this case means, that it calculates the second "spo2-curve-peak"
      and adds it to the first.

      Parameters:
          firstY - Contains the first calculated spo2-curve value.
          currentNIBP - Contains the current spo2-max.
          t0 - Contains the t0-parameter from the first spo2-curve-peak.
          sigma - Contains the sigma-parameter from the first spo2-curve-peak.
          M - Contains the M-constant for normalization
          t - Contains the value for which the spo2-curve is calculated.

      Returns:
          The calculated f(t).
      */
  private fun calcFullSpo2(
    firstY: Double,
    currentNIBP: NIBP,
    t0: Double,
    sigma: Double,
    M: Double,
    t: Double
  ): Double {

    val value = (currentNIBP.sys - currentNIBP.dia) / 1.6
    val alpha = 1
    val t0 = t0 + 5.5 * t0
    val sigma = sigma / 1.5

    val y = firstY + value / M * exp(
      -(t - t0).pow(2.0) /
          (2.0 * sigma.pow(2.0))
    ) * (1.0 + errorFunction(
      alpha * (t - t0) /
          (sqrt(2.0) * sigma)
    ))

    return y
  }

  /** Calculates the current oxy-value.
   * @param timestep contains the current simulation time. */
  fun calc(timestep: Double, isPacerUsed: Boolean = false): Double {
    this.timestep = timestep
    // sigma, alpha and t0 are parameters of the skew normal distribution
    val vitalSigns = simConfig.vitalSigns
    val alpha = 5.0
    var tempHR: Int
    tempHR = if (isPacerUsed) {
      vitalSigns.ecg.hr
    } else {
      if (ecgCalculation.currentRandHR != 0) ecgCalculation.currentRandHR else vitalSigns.ecg.hr
    }

    val period = 60.0 / tempHR

    /* This is used to draw the curve of low-bpm wave in the same timeframe as a 60bpm wave.
    After the waveform, it is zero, until the next wave starts. */
    if (tempHR < 60) tempHR = 60


    val t0 = 5.0 / tempHR
    val sigma = 15.0 / tempHR

    /* The normalization factor M is dependent on the simulation of  the spo2-curve.
    If the parameters t0 or alpha are changed, the M value needs to be adapted.
    It garantees, that the configured value is reached. Therefore, M must be
    recalculated if t0 or alpha are changed! */
    val M = 1.80835783

    val t = currentTime

    if (oldNIBP.sys == -1 || oldNIBP.dia == -1)
      oldNIBP = vitalSigns.nibp.deepCopy()

    if (oldNIBP.sys != vitalSigns.nibp.sys) {
      // Aha, the sys pressure was changed.
      oldNIBP.sys = vitalSigns.nibp.sys
      timeSinceNIBPChange.sys = 0.0
    }

    if (oldNIBP.dia != vitalSigns.nibp.dia) {
      // Aha, the sys pressure was changed.
      oldNIBP.dia = vitalSigns.nibp.dia
      timeSinceNIBPChange.dia = 0.0
    }

    currentNIBP.sys =
      getCurrentSysValue(vitalSigns.nibp.sys, period, simConfig.simState.changeDuration)
    currentNIBP.dia =
      getCurrentDiaValue(vitalSigns.nibp.dia, period, simConfig.simState.changeDuration)

    val lowerBounds = 70
    val upperBounds = 120

    val normSys = lowerBounds + (upperBounds - lowerBounds) / (1 + exp(
      -(5 / 15.0) *
          (currentNIBP.sys - 85)
    ))

    val normDia = lowerBounds + (upperBounds - lowerBounds) / (1 + exp(
      -(5 / 15.0) *
          (currentNIBP.dia - 85)
    ))

    var y = normDia + (normSys - normDia) / M * exp(
      -(t - t0).pow(2.0) /
          (2 * sigma.pow(2.0))
    ) * (1 + errorFunction(
      alpha * (t - t0) /
          (sqrt(2.0) * sigma)
    ))

    y = calcFullSpo2(y, NIBP(normSys.roundToInt(), normDia.roundToInt()), t0, sigma, M, t)
    y += (Math.random() - 0.5) * simConfig.vitalSigns.oxy.noise

    currentTime += timestep

    val simTime =
      if (checkPathology(PType.AVBlock3)) ecgCalculation.avBlockCounter else ecgCalculation.simTime
    /* This check is used to find out, if the ecg is not visible anymore but the spo2 is
    still actively measured. */
    if (abs(lastDeltaX - simTime) < timestep) {
      hfIdleCount++
    } else {
      hfIdleCount = 0
    }

    /* A problem occurred, when the variable ecgCalculation.simTime was used directly.
    Often, multiple steps where skipped (i.e. going from 0.02 to 0.06 directly) and the
    curve therefore became blocky. This probably origins from different execution times
    of the methods or some prioritization when using the asynchronous callback functions.

    HOWEVER: As a synchronization of the SpO2 and ECG is mandatory and the simTime value
    couldn't be trusted for each t-value, a separat counter variable "currentTime" is used
    additionally. This variable is however resetted as soon as simTime reaches 0. */
    /*if (currentTime >= period && hfIdleCount < maxHFIdleCount) {
        currentTime -= timestep
    }*/

    /* This condition is used to check if the currentTime needs to be synchronized with
    ecgCalculation.simTime. */
    if (lastDeltaX - simTime > 0 /*&& currentTime > 0*/ && hfIdleCount < maxHFIdleCount) {

      /*if (!timeSinceNIBPChange.sys.isZero() &&
          timeSinceNIBPChange.sys > simConfig.simState.changeDuration)*/
      currentTime = simTime
    }

    if ((lastDeltaX - simTime > 0 && hfIdleCount < maxHFIdleCount)
      || (currentTime >= period && hfIdleCount >= maxHFIdleCount)
    ) {

      /*if (!timeSinceNIBPChange.sys.isZero() &&
          timeSinceNIBPChange.sys > simConfig.simState.changeDuration)*/
      //Log.i("Oxy", "CurrentRandomHR: ${ecgCalculation.currentRandHR}")
      currentTime = simTime
    }

    lastDeltaX = simTime


    if (simConfig.simState.hasCPR) {
      y += cpr.calc(timestep)
    }

    y += baselineDrift.calc(timestep)

    return y
  }

}