package de.bauerapps.resimulate

import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MeasurementType {
  ECG, PLETH, ETCO2, RESP_RATE
}

/** This function is used as a parent class. It is not meant to be initialized directly but
through its inherited functions. This function is used to store and calculate the
measurement data for hr, spo2, etco2 and rr.
Parameters:
avgArraySize - Defines the size of the array that stores the average values.
dataArraySize - Stores the size of the data array for each measurement circle.
maxIdleTime - defines the maximum time in seconds that is allowed before an
asystole is diagnosed and the corresponding measurement value is updated.
 */
open class Measurement(
  internal val avgArraySize: Int,
  internal val dataArraySize: Int, var maxIdleTime: Int = 3
) {

  /** This variable is used to store all data-points from the graphs. */
  internal val dataArray = mutableListOf<Double>()

  private val dataMaxArray = mutableListOf<Double>()
  private val dataMinArray = mutableListOf<Double>()

  internal var timestep = 0.0

  interface MeasurementCallback {
    fun onMeasurement(type: MeasurementType, value: Int)
    fun onPeak(type: MeasurementType)
  }

  var measurementCallback: MeasurementCallback? = null


  /** This variable is used as a counter to store the current idle time if no or low
  measurement values are acquired. */
  var currentIdleTime = 0.0

  /** This variable is used to compare ECG and SPO2 to e.g. react on deactivation of the ECG */
  var globalIdleTimeCounter = 0.0

  /** This function checks if the [currentIdleTime] is higher then [maxIdleTime]. */
  fun isOverMaxIdleTime(): Boolean {
    return (currentIdleTime >= maxIdleTime)
  }

  fun storeMax(max: Double) {
    if (dataMaxArray.size >= avgArraySize) dataMaxArray.removeAt(0)

    dataMaxArray.add(max)
  }

  fun storeMin(min: Double) {
    if (dataMinArray.size >= avgArraySize) dataMinArray.removeAt(0)

    dataMinArray.add(min)
  }

  fun getGradient(yNew: Double, yOld: Double): Double {
    return (yNew - yOld) / timestep
  }

  fun getAvgMax(): Int {
    return dataMaxArray.average().roundToInt()
  }

  fun getAvgMin(): Double {
    return dataMinArray.average()
  }

}

/** Performs measurement regarding the ecg values. */
class ECGMeasurement : Measurement(4, 200) {

  private val peakArray = mutableListOf<Int>()

  /** Stores the last known Peak Index to have some robustness against quick repeating
  above-gradient-threshold ECG values. A peak is expected to occur only after
  lastPeakIndex + 0.2s. So a maximum of 300bpm can be detected.
  See also: [checkForECGPeak] */
  private var lastPeakIndex = 0

  /** Indicates, whether a negative gradient is expected before a new peak can be detected.
  This is used for the peak detection algorithm to gain some robustness against two
  successing positive peaks over the gradient threshold.
  This is also used to draw the synchronization indicators on the "falling edge"
  of the peak. */
  private var expectsNegativeGradient = false

  /** Used for the Real Time Peak Search of the ECG. */
  private fun checkForECGPeak() {

    val currentSize = dataArray.size
    if (currentSize > 1) {

      /* If more then 0 elements are available, the gradient is calculated. The gradient
      is used for the peak detection algorithm. The algorithm is nowhere near robust
      enough to perform well for noisy environments. */
      val oldVal = dataArray[currentSize - 2]
      val newVal = dataArray[currentSize - 1]
      val gradient = getGradient(newVal, oldVal)

      if (!expectsNegativeGradient && gradient > 20 &&
        (lastPeakIndex == 0 || currentSize - 1 > lastPeakIndex + 10)
      ) {
        expectsNegativeGradient = true
      } else if (expectsNegativeGradient && gradient < -20) {
        expectsNegativeGradient = false
        lastPeakIndex = currentSize - 1
        currentIdleTime = 0.0
        measurementCallback?.onPeak(MeasurementType.ECG)
      } else {
        // If no peak is detected, the currentIdleTime gets counted up.
        currentIdleTime += timestep
      }
    } else {
      // If the size of the dataArray is 0, the lastPeakIndex is also resetted.
      lastPeakIndex = 0
    }
  }

  /** Adds a single value to the [dataArray] and performs all necessary calculations to
  acquire the measurement values. */
  fun addECGValue(ecgValue: Double, timestep: Double) {

    this.timestep = timestep

    dataArray.add(ecgValue)

    globalIdleTimeCounter += timestep

    checkForECGPeak()

    // After 0.02 * dataArraySize seconds...
    if (dataArray.size >= dataArraySize) {

      // Return a measured Asystolie if the currentIdleTime is to high.
      if (currentIdleTime >= maxIdleTime) {
        measurementCallback?.onMeasurement(MeasurementType.ECG, 0)

        peakArray.clear()
        dataArray.clear()
        return
      }

      // Find min and max or optionally a 0 if no value could be found.
      val min = dataArray.min() ?: 0.0
      val max = dataArray.max() ?: 0.0
      storeMax(max)
      storeMin(min)

      // Algorithm for finding the count of peaks in the latest measurement interval:
      var peakCount = 0

      var lastMaxIndex = 0


      val mean = dataArray.average()//(min+max)/2.0
      val d1 = abs(max - mean)
      val d2 = abs(mean - min)

      val m = if (d1 > d2) {
        // Expect upward peaks
        max - abs(max - min) * 0.25

      } else {
        // Expect downward peaks
        min + abs(max - min) * 0.25
      }

      // A peak must be higher than this calculated threshold:
      //val m = min + abs(min-max) / 1.25
      //val m = max - abs(max-min)*0.25

      // All values in dataArray are analyzed
      for (i in 0 until dataArray.size) {
        // The peaks must be at least 0.2s apart from each other. (max 300 bpm)

        val thresholdReached = if (d1 > d2) dataArray[i] > m else dataArray[i] < m

        if (thresholdReached && (lastMaxIndex == 0 || i > (lastMaxIndex + 10))) {
          peakCount++
          lastMaxIndex = i
          Log.i("Measurement", "Max: $max, Min: $min")
        }
      }

      if (peakArray.size >= avgArraySize) {
        // After a defined amount of time, the first value gets pushed out.
        peakArray.removeAt(0)
      }
      peakArray.add(peakCount)

      // Clears data array
      dataArray.clear()
      measurementCallback?.onMeasurement(MeasurementType.ECG, getAverageHeartRate())
    }
  }

  /** Calculates and returns the current average Heart-Rate. Median did not work properly. */
  private fun getAverageHeartRate(): Int {
    if (peakArray.size == 0 || dataArraySize == 0) return 0

    val avgHR = peakArray.average() * 60.0 / (dataArraySize * 0.02)
    return avgHR.roundToInt()
  }
}

/** For the SpO2Measurement, only the height of the function is of interest, the frequency
is (normally) paired with the ECG. */
class SpO2Measurement(var simConfig: SimConfig) : Measurement(4, 200) {

  private val peakArray = mutableListOf<Int>()

  private var lastPeakIndex = 0

  private var expectsNegativeGrad = false

  var maxValue: Int = 0
    private set

  /** Used for the Real Time Peak Search of the SPO2. */
  private fun checkForSpo2Peak() {

    val currentSize = dataArray.size
    if (currentSize > 1) {

      /* If more then 0 elements are available, the gradient is calculated. The gradient
          is used for the peak detection algorithm. The algorithm is nowhere near robust
          enough to perform well for noisy environments. */
      val oldVal = dataArray[currentSize - 2]
      val newVal = dataArray[currentSize - 1]
      val gradient = getGradient(newVal, oldVal)

      // The peaks must be at least 0.2s apart from each other. (max 300 bpm)
      val min = dataArray.min() ?: 0.0
      val max = dataArray.max() ?: 0.0
      val m = max - abs(max - min) * 0.25

      if (newVal > m && gradient > 50 && !expectsNegativeGrad
        && (lastPeakIndex == 0 || currentSize - 1 > lastPeakIndex + 10)
      ) {
        lastPeakIndex = currentSize - 1
        currentIdleTime = 0.0
        expectsNegativeGrad = true

        // The callback happens on the rising slope.
        measurementCallback?.onPeak(MeasurementType.PLETH)
      } else if (gradient < 0) {
        expectsNegativeGrad = false
      } else {
        // If no peak is detected, the currentIdleTime gets counted up.
        currentIdleTime += timestep
      }
    } else {
      // If the size of the dataArray is 0, the lastPeakIndex is also reset.
      lastPeakIndex = 0
    }
  }

  /** Adds a single value to the <dataArray> and performs all necessary calculations to
  acquire the measurement values.
  Parameters:
  spo2Value - Contains the latest captured value added to the <SPO2Graph>.
   */
  fun addSpO2Value(spo2Value: Double, nibp: NIBP, timestep: Double) {

    this.timestep = timestep
    dataArray.add(spo2Value)

    globalIdleTimeCounter += timestep

    checkForSpo2Peak()

    if (dataArray.size >= dataArraySize) {

      if (currentIdleTime >= maxIdleTime) {
        // Return a measured asystolie
        measurementCallback?.onMeasurement(MeasurementType.PLETH, 0)

        peakArray.clear()
        dataArray.clear()

        storeMin(0.0)
        storeMax(0.0)
        return
      }

      /* This is no measurement data, because there is no way to simulate the measurement
      of nibp here. */
      if (nibp.sys > 70 && nibp.dia <= nibp.sys) {
        storeMin(0.0)
        var newVal = simConfig.vitalSigns.oxy.spo2.toDouble() + (Math.random() - 0.5) * 5
        if (newVal < 0) newVal = 0.0
        if (newVal > 100) newVal = 100.0
        storeMax(newVal)
      } else {
        storeMin(0.0)
        storeMax(0.0)
      }

      // Algorithm for finding the count of peaks in the latest measurement interval:

      var peakCount = 0
      var lastMaxIndex = 0
      var expectsNegativeGrad = false
      for (i in 1 until dataArray.size) {
        // Finds the gradient:
        val oldVal = dataArray[i - 1]
        val newVal = dataArray[i]
        val gradient = getGradient(newVal, oldVal)

        if (gradient > 50 && !expectsNegativeGrad && (lastMaxIndex == 0 || i > lastMaxIndex + 10)) {
          peakCount++
          lastMaxIndex = i
          expectsNegativeGrad = true
        } else if (gradient < 0) {
          expectsNegativeGrad = false
        }
      }

      if (peakArray.size >= avgArraySize) {
        // After a defined amount of time, the first value gets pushed out.
        peakArray.removeAt(0)
      }
      peakArray.add(peakCount)

      dataArray.clear()

      var spo2Avg = getAvgMax()
      maxValue = spo2Avg
      // If the average is below 40, we assume that no measurement is possible atm.
      if (spo2Avg <= 40) {
        spo2Avg = 0
      }
      measurementCallback?.onMeasurement(MeasurementType.PLETH, spo2Avg)
    }
  }

  /** Calculates and returns the latest average heartrate based on the SPO2-measurement. */
  fun getAverageHeartrateFromSPO2(): Int {
    if (peakArray.size == 0 || dataArraySize == 0) return 0

    val avgFrequency = peakArray.average() * 60.0 / (dataArraySize * 0.02)
    return avgFrequency.roundToInt()
  }
}


/* Responsible for everything relating to the measurement of etco2 values. */
class ETCO2Measurement : Measurement(2, 800, 10) {

  private val rrArray = mutableListOf<Float>()
  private var expectsNegativeGradient = false

  /** This function is working like an peak finding algorithm put its mere purpose is
  to check if the SPO2 is already slow.
  See also: [checkForECGPeak] */
  private fun checkForIdle() {

    val currentSize = dataArray.size
    if (currentSize > 1) {

      // Finds the gradient:
      val oldVal = dataArray[currentSize - 2]
      val newVal = dataArray[currentSize - 1]
      val gradient = getGradient(newVal, oldVal)

      if (gradient > 5 && !expectsNegativeGradient) {
        expectsNegativeGradient = true
        currentIdleTime = 0.0
      } else if (gradient < -5) {
        expectsNegativeGradient = false
      } else {
        currentIdleTime += timestep
      }
    }
  }

  /** Adds a single value to the <dataArray> and performs all necessary calculations to
  acquire the measurement values.
   */
  fun addETCO2Value(etco2Value: Double, timestep: Double) {

    this.timestep = timestep

    dataArray.add(etco2Value)

    checkForIdle()

    if (dataArray.size >= dataArraySize) {

      // If no breathing is captured:
      if (currentIdleTime >= maxIdleTime) {
        /* Returns a function containing the parameters indicating a measured
            breathing zero-line. */
        measurementCallback?.onMeasurement(MeasurementType.ETCO2, 0)
        measurementCallback?.onMeasurement(MeasurementType.RESP_RATE, 0)

        // Clears the dataArray:
        dataArray.clear()

        return
      }

      val min = dataArray.min() ?: 0.0
      val max = dataArray.max() ?: 0.0
      storeMin(min)
      storeMax(max)

      var freqCount = 0

      // Algorithm for finding the count of peaks in the latest measurement interval:

      for (i in 0 until dataArray.size - 1) {

        // Calculates the gradient for slope detection:

        val newVal = dataArray[i + 1]
        val oldVal = dataArray[i]
        val gradient = getGradient(newVal, oldVal)

        // Some arbitrary threshold, that is required to detect a changing signal.
        val threshold = 4

        if (gradient > 5 && newVal > threshold && !expectsNegativeGradient) {
          freqCount++
          expectsNegativeGradient = true
        } else if (gradient < -5 && newVal < threshold) {
          expectsNegativeGradient = false
        }
      }

      if (rrArray.size >= avgArraySize) {
        // After 40sec, the first value gets pushed out.
        rrArray.removeAt(0)
      }

      rrArray.add(freqCount.toFloat())

      // Clear data Array
      dataArray.clear()

      measurementCallback?.onMeasurement(
        MeasurementType.ETCO2,
        if (getAvgMax() >= 5) getAvgMax() else 0
      )
      measurementCallback?.onMeasurement(MeasurementType.RESP_RATE, getAverageRespRate())
    }
  }

  /** Calculates and returns the latest average respiratory rate. */
  private fun getAverageRespRate(): Int {
    if (rrArray.size == 0 || dataArraySize == 0) return 0

    val avgRF = rrArray.average() * 60.0 / (dataArraySize * 0.02)
    return avgRF.toInt()
  }
}