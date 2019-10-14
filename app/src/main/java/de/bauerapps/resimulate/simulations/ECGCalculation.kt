package de.bauerapps.resimulate.simulations

import android.util.Log
import de.bauerapps.resimulate.*
import de.bauerapps.resimulate.helper.BaselineDrift
import de.bauerapps.resimulate.helper.isZero
import de.bauerapps.resimulate.views.ESViewType
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class ECGCalculation(
  simConfig: SimConfig,
  private var cpr: CPRCalculation
) {

  companion object {
    const val TAG = "ECGCalculation"
  }

  interface Callback {
    fun drawPacerPeak() {}
    fun drawZeroIndicator() {}
  }

  var callback: Callback? = null
  var pendingSimConfig: SimConfig? = null
  var currentRandHR = simConfig.vitalSigns.ecg.hr

  var simConfig = simConfig
    set(value) {
      oldSimConfig = if (forceChange) value else simConfig.deepCopy()

      if (forceChange) {
        latestReachedHR = value.vitalSigns.ecg.hr
        currentHR = latestReachedHR
        currentRandHR = latestReachedHR
        didCurrentHFChange = true
      }

      timeSinceConfigChange = 0.0
      field = value
      forceChange = false
    }

  /** Stores the current simulation time. */
  var simTime = 0.0
  var avBlockCounter = 0.0
  var currentHR = simConfig.vitalSigns.ecg.hr
  var forceChange = false
  var drawShock = false

  private var ecgOffset = 0.0
  private var didCurrentParamsChange = false
  private var didCurrentHFChange = false
  private var drawShockStartTime = -1.0

  private var timeSinceConfigChange = 0.0
  private var timeSinceIntermediateHRChange = 0.0
  private var latestReachedHR = simConfig.vitalSigns.ecg.hr
  private var startTimeOffset = DVS.startTimeOffset
  private var oldSimConfig = simConfig.deepCopy()
  private val currentParams = simConfig.vitalSigns.ecg.deepCopy()

  private var baselineDrift = BaselineDrift(ESViewType.ECG)

  /** Flags, whether a pacer peak must be drawn in the current simulation */
  private var expectsPacerPeak = false

  private var timestep = 0.0

  /** Stores the intermediate baseline value to draw low bpm pqrstu-complexes and a
   * corresponding baseline on the correct height. This is necessary, because the baseline is
   * dependent on the bpm of the ecg. The value is therefore not constant. */
  private var baselineValue = 0.65

  /** Stores the number of the FourierSeries Iterations, that are performed per frame. */
  private val fsIterations = 40


  private val isPatientPaced: Boolean
    get() {
      val pacer = simConfig.simState.pacer
      val isEnergyThresholdReached = pacer.energy >= pacer.energyThreshold
      val isHRFrequencyLower = simConfig.vitalSigns.ecg.hr < simConfig.simState.pacer.frequency

      return (pacer.isEnabled && isEnergyThresholdReached && isHRFrequencyLower)
    }


  private fun checkPathology(type: PType): Boolean {
    return type == simConfig.vitalSigns.pathology.type
  }

  /** Is used to calculate the pWaveValue for the current ECG value. */
  private fun calculatePWave(
    simTime: Double, amplitude: Double, duration: Double,
    startTime: Double, hr: Int
  ): Double {
    if (hr == 0) return 0.0

    val period = 30.0 / hr
    val x = simTime - (startTime + startTimeOffset)
    val b = (2.0 * period) / duration
    val p1 = 1.0 / period
    var p2 = 0.0

    for (i in 1..fsIterations) {
      val harm1 = (((sin((Math.PI / (2.0 * b)) * (b - (2.0 * i)))) / (b - (2.0 * i)) +
          (sin((Math.PI / (2.0 * b)) * (b + (2.0 * i)))) / (b + (2.0 * i))) * (2.0 / Math.PI)
          ) * cos((i * Math.PI * x) / period)
      p2 += harm1
    }

    return amplitude * (p1 + p2)
  }


  /** Is used to calculate and the qWaveValue for the current ECG value. */
  private fun calculateQWave(
    simTime: Double, amplitude: Double, duration: Double,
    startTime: Double, hr: Int
  ): Double {
    if (hr == 0) return 0.0

    val period = 30.0 / hr
    val x = simTime - (startTime + startTimeOffset)
    val b = (2.0 * period) / duration
    val q1 = (amplitude / (2.0 * b)) * (2.0 - b)
    var q2 = 0.0

    for (i in 1..fsIterations) {
      val harm5 =
        (((2.0 * b * amplitude) / (i * i * Math.PI * Math.PI)) * (1.0 - cos((i * Math.PI) / b))) * cos(
          (i * Math.PI * x) / period
        )
      q2 += harm5
    }

    return (-1) * (q1 + q2)
  }

  /** Is used to calculate and return the qrsComplexValue for the current ECG value. */
  private fun calculateQRSWave(
    simTime: Double, amplitude: Double, duration: Double,
    startTime: Double, hr: Int/*, amplitudeOffset: Double*/
  ): Double {
    if (hr == 0) return 0.0/*amplitudeOffset*/

    val period = 30.0 / hr
    val x = simTime - (startTime + startTimeOffset)
    val b = (2.0 * period) / duration
    val qrs1 = (amplitude / (2.0 * b)) * (2.0 - b)
    var qrs2 = 0.0

    for (i in 1..fsIterations) {
      val harm =
        (((2.0 * b * amplitude) / (i * i * Math.PI * Math.PI)) * (1.0 - cos((i * Math.PI) / b))) * cos(
          (i * Math.PI * x) / period
        )
      qrs2 += harm

    }

    return (qrs1 + qrs2)/* + amplitudeOffset*/
  }

  /** Is used to calculate the sWaveValue for the current ECG value. */
  private fun calculateSWave(
    simTime: Double, amplitude: Double, duration: Double,
    startTime: Double, hr: Int
  ): Double {
    if (hr == 0) return 0.0

    val period = 30.0 / hr
    val x = simTime - (startTime + startTimeOffset)
    val b = (2.0 * period) / duration
    val s1 = (amplitude / (2.0 * b)) * (2.0 - b)
    var s2 = 0.0

    for (i in 1..fsIterations) {
      val harm3 =
        (((2.0 * b * amplitude) / (i * i * Math.PI * Math.PI)) * (1.0 - cos((i * Math.PI) / b))) * cos(
          (i * Math.PI * x) / period
        )
      s2 += harm3
    }

    var sWaveValue = (-1) * (s1 + s2)

    if (checkPathology(PType.STElevation)) {

      if (simTime >= startTime + startTimeOffset && simTime <= startTime + startTimeOffset + duration) {
        sWaveValue = 0.5
      }
    }

    return sWaveValue
  }

  /** Is used to calculate and return the tWaveValue for the current ECG value. */
  private fun calculateTWave(
    simTime: Double, amplitude: Double, duration: Double,
    startTime: Double, tsWaveSeparationDuration: Double, hr: Int
  ): Double {
    if (hr == 0) return 0.0

    val n = 50
    val period = 30.0 / hr
    val x = simTime - (startTime + startTimeOffset) - tsWaveSeparationDuration
    val b = (2.0 * period) / duration
    val t1 = 1.0 / period
    var t2 = 0.0

    for (i in 1..n) {
      val harm2 = (((sin((Math.PI / (2.0 * b)) * (b - (2 * i)))) / (b - (2.0 * i)) +
          (sin((Math.PI / (2.0 * b)) * (b + (2.0 * i)))) / (b + (2.0 * i))
          ) * (2.0 / Math.PI)) * cos((i * Math.PI * x) / period)
      t2 += harm2
    }

    var tWaveValue = amplitude * (t1 + t2)

    if (checkPathology(PType.STElevation)) {

      if (simTime >= startTime + startTimeOffset - tsWaveSeparationDuration * 2 && simTime <= startTime + startTimeOffset + duration) {
        // Iterative value tuning:
        tWaveValue += 0.15 - (simTime - (startTime + startTimeOffset) - tsWaveSeparationDuration) /
            (startTime + startTimeOffset + tsWaveSeparationDuration * 5 + duration)
      }
    }

    return tWaveValue
  }

  /** Is used to calculate and return the uWaveValue for the current ECG value. */
  private fun calculateUWave(
    simTime: Double, amplitude: Double, duration: Double,
    startTime: Double, hr: Int
  ): Double {
    if (hr == 0) return 0.0

    val period = 30.0 / hr
    val x = simTime - (startTime + startTimeOffset)
    val b = (2.0 * period) / duration
    val u1 = 1.0 / period
    var u2 = 0.0

    for (i in 1..fsIterations) {
      val harm4 = (((sin((Math.PI / (2.0 * b)) * (b - (2.0 * i)))) / (b - (2.0 * i)) +
          (sin((Math.PI / (2.0 * b)) * (b + (2.0 * i)))) / (b + (2.0 * i))) * (2.0 / Math.PI)) *
          cos((i * Math.PI * x) / period)
      u2 += harm4
    }

    return amplitude * (u1 + u2)
  }

  /** Uses all provided parameters to calculate the current sum.
   * */
  private fun calculateWaveSum(
    p: Double, q: Double, qrs: Double, s: Double,
    t: Double, u: Double
  ): Double {

    return p + q + qrs + s + t + u
  }

  /** Randomize the provided heart-rate.
   * In order to do so correctly, a lot of conditions are checked.
   * */
  private fun randomize(hr: Int, simTime: Double): Int {
    // TODO: Is this necessary?
    var randHR = if (!checkPathology(PType.AVBlock3)) currentRandHR else hr

    if (simTime.isZero() && !isPatientPaced) {

      val maxRandFactor = when (simConfig.vitalSigns.pathology.type) {
        PType.VentricularFibrillation -> 0.7
        PType.ArtrialFibrillation -> 0.1
        else -> 0.15 - 0.14 / (1 + (exp(-5 / 60 * (hr.toDouble() - 120))))
      }

      randHR = ((Math.random() - 0.5) * hr * maxRandFactor + hr).roundToInt()

    } else if (isPatientPaced) {
      randHR = hr
    }

    // Fallback to restart again from 0 on.
    if (randHR == 0) {
      randHR = hr
    }

    return randHR
  }


  /** Calculates the current simulated heart-rate value.
   * This function contains most of the steps necessary to simulate the dynamic change
   * of the ECG. The calculation of the change is done with
   * the [Logistic Function](https://en.wikipedia.org/wiki/Logistic_function). */
  private fun calculateCurrentHFValue(): Int {
    val vitalSigns = simConfig.vitalSigns
    val pacer = simConfig.simState.pacer
    val changeDuration = if (isPatientPaced) 0 else simConfig.simState.changeDuration

    if (timeSinceConfigChange.isZero()) {
      // Change was performed:
      latestReachedHR = currentHR
      Log.i(TAG, "LatestReachedHR: $latestReachedHR")
    }

    /* timeSinceConfigChange is increased by timestep/2 because this function is called twice
    for each ECG calculation step. */
    timeSinceConfigChange += timestep

    val valDiff = vitalSigns.ecg.hr - latestReachedHR

    /* x0 of the logistic function defines the duration to reach
        the mid point, with the steepest slope. */

    val x0 = changeDuration / 2.0
    /* k is the steepness of the curve. The adaptability through the
        dependency on x0 is chosen to make k more adaptable to different
        speeds and heights of changes.*/
    val k = 3.0 / x0
    val x = timeSinceConfigChange

    /* Based on the characteristic of the logistic function,
        the end-value (max OR min) is approximately reached at 2*x0. */
    if (timeSinceConfigChange > 2 * x0 || valDiff == 0) {
      timeSinceIntermediateHRChange = 0.0
      return if (isPatientPaced) pacer.frequency else vitalSigns.ecg.hr
    }

    timeSinceIntermediateHRChange += timestep

    if (timeSinceIntermediateHRChange > 60.0 / currentHR) {
      timeSinceIntermediateHRChange = 0.0
      // With the logistic function, the current HR is calculated.
      currentHR = (latestReachedHR + valDiff / (1 + (exp(-k * (x - x0))))).roundToInt()
      didCurrentHFChange = true
    }

    return currentHR
  }

  private fun calculateCurrentParam(
    currentParam: Double, oldParam: Double,
    newParam: Double, alwaysCalculate: Boolean = false
  ): Double {

    val changeDuration = if (isPatientPaced) 0 else simConfig.simState.changeDuration

    val valDiff = newParam - oldParam


    /* x0 of the logistic function defines the duration to reach
        the mid point, with the steepest slope. */

    val x0 = changeDuration / 2.0
    /* k is the steepness of the curve. The adaptability through the
        dependency on x0 is chosen to make k more adaptable to different
        speeds and heights of changes.*/
    val k = 3.0 / x0
    val x = timeSinceConfigChange

    /* Based on the characteristic of the logistic function,
        the end-value (max OR min) is approximately reached at 2*x0. */
    if (timeSinceConfigChange > 2 * x0 || valDiff.isZero(0.001)) {
      return newParam
    }

    var returnParam = currentParam

    if (timeSinceIntermediateHRChange.isZero() || alwaysCalculate) {
      // With the logistic function, the current HR is calculated.
      returnParam = oldParam + valDiff / (1 + (exp(-k * (x - x0))))
      didCurrentParamsChange = true
    }

    return returnParam

  }


  /** Used to calculate the starting point and duration for the different ecg parts.
   * Calculate improved ECG-curves by incorporating the
   * current heart-rate. The algorithm uses a logistic function (with offset) to
   * calculate the values.
   * @param hr Contains the current heart-rate.
   * @param param Contains the parameter to be adapted depending on the hr.
   * @return Adapted Parameter */
  private fun getTimeForParameter(hr: Int, param: Double): Double {

    var hrOffset = 0
    if (checkPathology(PType.VentricularTachycardia)) hrOffset = 120
    if (checkPathology(PType.VentricularFibrillation)) hrOffset = 250

    // These parameters are superb for the whole range of hr. (Based on Tests in Excel)
    val normalHF = 60 + hrOffset
    val halfValueDif = 70.0
    val k = 6.0 / halfValueDif
    return param - (param * 0.6) / (1.0 + (exp(-k * ((hr - normalHF) - halfValueDif))))
  }

  private fun calculateECGOffset(): Double {
    val randHR = currentRandHR

    var tempHR = randHR
    val tempTime = 0.0

    /* This is used to draw the curve of low-bpm ecg in the same timeframe as a 60bpm
        ecg. After the waveform, is is zero, until the next wave starts. */
    if (tempHR in 1..34 && tempHR < 35
      && !checkPathology(PType.AVBlock3)
    ) {
      /* below 35bpm, the curve is stitched together by a 60bpm "baseline" and a
      "pqrstu"-complex. */
      tempHR = 60
    }

    val pWave = currentParams.pWave
    // Variables for the P-Wave:
    val pWaveDuration = getTimeForParameter(randHR, pWave.duration)
    val pWaveStartTime = getTimeForParameter(randHR, pWave.startTime)

    // calculate first value of the chosen frequency to eliminate offset
    val pWaveValue = calculatePWave(
      tempTime + getTimeForParameter(randHR, currentParams.xValOffset), pWave.amp,
      pWaveDuration, pWaveStartTime, tempHR
    )

    val qWave = currentParams.qWave
    val sWave = currentParams.sWave
    val tWave = currentParams.tWave
    val uWave = currentParams.uWave
    val qrs = currentParams.qrs
    // Variables for the Q-Wave:
    val qWaveDuration = getTimeForParameter(randHR, qWave.duration)
    val qWaveStartTime = getTimeForParameter(randHR, qWave.startTime)
    // Variables for the QRS-Wave:
    val qrsComplexDuration = getTimeForParameter(randHR, (qrs.duration + qrs.durationOffset))
    val qrsComplexStartTime = getTimeForParameter(randHR, (qrs.startTime))
    // Variables for the S-Wave:
    val sWaveDuration = getTimeForParameter(randHR, sWave.duration)
    val sWaveStartTime = getTimeForParameter(randHR, sWave.startTime)
    // Variables for the T-Wave:
    val tWaveDuration = getTimeForParameter(randHR, tWave.duration)
    val tWaveStartTime = getTimeForParameter(randHR, tWave.startTime)
    // Separation of S- and T-Wave
    val stWaveSeparationDuration =
      getTimeForParameter(randHR, currentParams.stWaveSeparationDuration)

    // Variables for the U-Wave:
    val uWaveDuration = getTimeForParameter(randHR, uWave.duration)
    val uWaveStartTime = getTimeForParameter(randHR, uWave.startTime)

    val qWaveValue = calculateQWave(
      tempTime, qWave.amp, qWaveDuration, qWaveStartTime, tempHR
    )

    val qrsComplexValue = calculateQRSWave(
      tempTime, qrs.amp, qrsComplexDuration, qrsComplexStartTime, tempHR/*, -qrs.amplitudeOffset*/
    )

    val sWaveValue = calculateSWave(
      tempTime, sWave.amp, sWaveDuration, sWaveStartTime, tempHR
    )

    val tWaveValue = calculateTWave(
      tempTime + currentParams.xValOffset, tWave.amp, tWaveDuration, tWaveStartTime,
      stWaveSeparationDuration, tempHR
    )

    val uWaveValue = calculateUWave(
      tempTime, uWave.amp, uWaveDuration, uWaveStartTime,
      tempHR
    )

    return calculateWaveSum(
      pWaveValue, qWaveValue, qrsComplexValue, sWaveValue,
      tWaveValue, uWaveValue
    )
  }

  /** In this function, the different compartments of the ECG signal are calculated.
   * Every signal-part is afterwards summed up to build one point of the ECG. The ECG is
   * calculated every [timestep] seconds.
   * @param simTime contains 0 or the current simulation time for calculating the ECG value.
   * @return One part of the calculated ECG value.
   */
  private fun calculateECGValue(simTime: Double): Double {

    currentHR = calculateCurrentHFValue()
    var randHR = randomize(currentHR, simTime)

    var tempHR = randHR
    var tempTime = simTime

    if (tempHR in 1..34) {
      /* below 35bpm, the curve is stitched together by a 60bpm "baseline" and a
      "pqrstu"-complex. */
      tempHR = 60
    }


    updateCurrentParams()

    val pWave = currentParams.pWave
    // Variables for the P-Wave:
    val pWaveDuration = getTimeForParameter(randHR, pWave.duration)
    val pWaveStartTime = getTimeForParameter(randHR, pWave.startTime)

    // calculate first value of the chosen frequency to eliminate offset
    // getTimeForParameter(randHR, currentParams.xValOffset)
    val pWaveValue = calculatePWave(
      tempTime + currentParams.xValOffset, pWave.amp,
      pWaveDuration, pWaveStartTime, tempHR
    )

    if (checkPathology(PType.AVBlock3)) {
      if (!isPatientPaced) {
        currentParams.hr = calculateCurrentParam(
          currentParams.hr.toDouble(),
          latestReachedHR.toDouble(), 40.0
        ).roundToInt()
        randHR = randomize(currentParams.hr, avBlockCounter)
        tempHR = randHR
        if (avBlockCounter > 60.0 / randHR) {
          Log.i(
            TAG,
            "BlockHR: ${currentParams.hr}, RandHR: $randHR, LatestReachedHR: $latestReachedHR"
          )
          avBlockCounter = 0.0
        }
        avBlockCounter += timestep
        tempTime = avBlockCounter
      }
    }

    currentRandHR = randHR

    val qWave = currentParams.qWave
    val sWave = currentParams.sWave
    val tWave = currentParams.tWave
    val uWave = currentParams.uWave
    val qrs = currentParams.qrs

    // Variables for the Q-Wave:
    val qWaveDuration = getTimeForParameter(randHR, qWave.duration)
    val qWaveStartTime = getTimeForParameter(randHR, qWave.startTime)
    // Variables for the QRS-Wave:
    val qrsComplexDuration = getTimeForParameter(randHR, (qrs.duration + qrs.durationOffset))
    val qrsComplexStartTime = getTimeForParameter(randHR, qrs.startTime)
    // Variables for the S-Wave:
    val sWaveDuration = getTimeForParameter(randHR, sWave.duration)
    val sWaveStartTime = getTimeForParameter(randHR, sWave.startTime)
    // Variables for the T-Wave:
    val tWaveDuration = getTimeForParameter(randHR, tWave.duration)
    val tWaveStartTime = getTimeForParameter(randHR, tWave.startTime)
    // Separation of S- and T-Wave
    val stWaveSeparationDuration =
      getTimeForParameter(randHR, currentParams.stWaveSeparationDuration)

    // Variables for the U-Wave:
    val uWaveDuration = getTimeForParameter(randHR, uWave.duration)
    val uWaveStartTime = getTimeForParameter(randHR, uWave.startTime)

    startTimeOffset = getTimeForParameter(randHR, DVS.startTimeOffset)

    val qWaveValue = calculateQWave(
      tempTime, qWave.amp, qWaveDuration, qWaveStartTime,
      tempHR
    )

    val qrsComplexValue = calculateQRSWave(
      tempTime, qrs.amp, qrsComplexDuration, qrsComplexStartTime, tempHR/*, qrs.amplitudeOffset*/
    )

    val sWaveValue = calculateSWave(
      tempTime, sWave.amp, sWaveDuration, sWaveStartTime, tempHR
    )

    val tWaveValue = calculateTWave(
      tempTime + currentParams.xValOffset, tWave.amp, tWaveDuration, tWaveStartTime,
      stWaveSeparationDuration, tempHR
    )

    val uWaveValue = calculateUWave(
      tempTime, uWave.amp, uWaveDuration, uWaveStartTime,
      tempHR
    )


    /* This is used to draw the curve of low-bpm ecg in the same timeframe as a 60bpm
        ecg. After the waveform, the baseline value will be drawn, until the next wave
        starts. */
    if (tempTime < 60.0 / randHR && tempTime > 0.9 && randHR < 35
      && !checkPathology(PType.AVBlock3)
      && !didCurrentHFChange
    )
      return baselineValue

    baselineValue = calculateWaveSum(
      pWaveValue, qWaveValue, qrsComplexValue, sWaveValue,
      tWaveValue, uWaveValue
    )

    return calculateWaveSum(
      pWaveValue, qWaveValue, qrsComplexValue, sWaveValue,
      tWaveValue, uWaveValue
    )
  }

  private fun updateCurrentParams() {

    val qWave = simConfig.vitalSigns.ecg.qWave
    val sWave = simConfig.vitalSigns.ecg.sWave
    val tWave = simConfig.vitalSigns.ecg.tWave
    val uWave = simConfig.vitalSigns.ecg.uWave
    val pWave = simConfig.vitalSigns.ecg.pWave
    val qrs = simConfig.vitalSigns.ecg.qrs

    val oldECG = oldSimConfig.vitalSigns.ecg

    currentParams.pWave.duration = calculateCurrentParam(
      currentParams.pWave.duration, oldECG.pWave.duration, pWave.duration
    )
    currentParams.pWave.startTime = calculateCurrentParam(
      currentParams.pWave.startTime, oldECG.pWave.startTime, pWave.startTime
    )
    currentParams.pWave.amp = calculateCurrentParam(
      currentParams.pWave.amp, oldECG.pWave.amp, pWave.amp, true
    )

    currentParams.qWave.duration = calculateCurrentParam(
      currentParams.qWave.duration,
      oldECG.qWave.duration, qWave.duration
    )
    currentParams.qWave.startTime = calculateCurrentParam(
      currentParams.qWave.startTime,
      oldECG.qWave.startTime, qWave.startTime
    )
    currentParams.qWave.amp = calculateCurrentParam(
      currentParams.qWave.amp,
      oldECG.qWave.amp, qWave.amp, true
    )

    currentParams.sWave.duration = calculateCurrentParam(
      currentParams.sWave.duration,
      oldECG.sWave.duration, sWave.duration
    )
    currentParams.sWave.startTime = calculateCurrentParam(
      currentParams.sWave.startTime,
      oldECG.sWave.startTime, sWave.startTime
    )
    currentParams.sWave.amp = calculateCurrentParam(
      currentParams.sWave.amp,
      oldECG.sWave.amp, sWave.amp, true
    )

    currentParams.tWave.duration = calculateCurrentParam(
      currentParams.tWave.duration,
      oldECG.tWave.duration, tWave.duration
    )
    currentParams.tWave.startTime = calculateCurrentParam(
      currentParams.tWave.startTime,
      oldECG.tWave.startTime, tWave.startTime
    )
    currentParams.tWave.amp = calculateCurrentParam(
      currentParams.tWave.amp,
      oldECG.tWave.amp, tWave.amp, true
    )

    currentParams.uWave.duration = calculateCurrentParam(
      currentParams.uWave.duration,
      oldECG.uWave.duration, uWave.duration
    )
    currentParams.uWave.startTime = calculateCurrentParam(
      currentParams.uWave.startTime,
      oldECG.uWave.startTime, uWave.startTime
    )
    currentParams.uWave.amp = calculateCurrentParam(
      currentParams.uWave.amp,
      oldECG.uWave.amp, uWave.amp, true
    )

    currentParams.stWaveSeparationDuration = calculateCurrentParam(
      currentParams.stWaveSeparationDuration,
      oldECG.stWaveSeparationDuration, simConfig.vitalSigns.ecg.stWaveSeparationDuration
    )

    currentParams.qrs.duration = calculateCurrentParam(
      currentParams.qrs.duration,
      oldECG.qrs.duration, qrs.duration
    )
    currentParams.qrs.startTime = calculateCurrentParam(
      currentParams.qrs.startTime,
      oldECG.qrs.startTime, qrs.startTime
    )
    currentParams.qrs.durationOffset = calculateCurrentParam(
      currentParams.qrs.durationOffset,
      oldECG.qrs.durationOffset, qrs.durationOffset
    )
    currentParams.qrs.amp = calculateCurrentParam(
      currentParams.qrs.amp,
      oldECG.qrs.amp, qrs.amp, true
    )
    /*currentParams.qrs.amplitudeOffset = calculateCurrentParam(currentParams.qrs.amplitudeOffset,
        oldECG.qrs.amplitudeOffset, qrs.amplitudeOffset, true)*/

    currentParams.xValOffset = calculateCurrentParam(
      currentParams.xValOffset,
      oldECG.xValOffset, simConfig.vitalSigns.ecg.xValOffset
    )

    currentParams.noise = calculateCurrentParam(
      currentParams.noise,
      oldECG.noise, simConfig.vitalSigns.ecg.noise, true
    )

  }

  /* Calculates and returns the current ECG II curve value. */
  fun calc(timestep: Double): Double {

    var ecgValue = calculateECGValue(simTime) + (Math.random() - 0.5) * currentParams.noise

    if (checkPathology(PType.Asystole) && pendingSimConfig != null) {
      simTime = 0.0
      currentHR = 30
      currentRandHR = 30
    }

    if (simTime.isZero() || didCurrentParamsChange || didCurrentHFChange) {

      if (pendingSimConfig != null) {
        simConfig = pendingSimConfig!!
        pendingSimConfig = null
        ecgValue = calculateECGValue(simTime) + (Math.random() - 0.5) * currentParams.noise
      }

      if (simTime.isZero())
      // This only effects the ECGDesigner
        callback?.drawZeroIndicator()

      ecgOffset = calculateECGOffset() + (Math.random() - 0.5) * currentParams.noise
      didCurrentParamsChange = false
      didCurrentHFChange = false
    }

    ecgValue -= ecgOffset

    this.simTime += timestep
    this.timestep = timestep

    // This Block is used to check conditions for resetting the simTime at the end of a period
    val tempHR = if (checkPathology(PType.AVBlock3)) currentHR else currentRandHR
    if (currentRandHR != 0 && simTime >= 60.0 / tempHR) {
      simTime = 0.0
      expectsPacerPeak = true
    }

    // Checks, if a defi shock should be drawn in the curve.
    if (drawShock) {
      // drawShockStartTime default is -1.0 as an idle indicator.
      if ((drawShockStartTime + 1.0).isZero())
        drawShockStartTime = 0.0

      ecgValue = when {
        drawShockStartTime < 0.02 -> 2 + 0.5 * exp(-drawShockStartTime / 0.004)
        drawShockStartTime < 0.04 -> -3 - 0.5 * exp(-(drawShockStartTime - 0.02) / 0.004)
        else -> {
          drawShock = false
          drawShockStartTime = -1.0
          ecgValue
        }
      }
      // This check is needed. Otherwise, the drawShockStartTime will not be reset.
      if (drawShock)
        drawShockStartTime += timestep
    }

    if (simConfig.simState.hasCPR) {
      ecgValue += cpr.calc(timestep)
    }

    // Used to adapt the position of the pacer peak in the paced ECG.
    val pacerPeakPosition = simConfig.vitalSigns.ecg.pWave.startTime +
        simConfig.vitalSigns.ecg.pWave.duration / 2.0

    if (isPatientPaced && expectsPacerPeak && simTime >= pacerPeakPosition) {
      expectsPacerPeak = false

      // TODO: It would be interesting to find out, what if the pacer hits the wrong part of the ecg.
      callback?.drawPacerPeak()
    }

    ecgValue += baselineDrift.calc(timestep)

    return ecgValue
  }
}