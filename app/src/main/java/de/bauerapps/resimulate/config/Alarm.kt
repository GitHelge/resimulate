package de.bauerapps.resimulate.config

/** Contains the endpointState of the alarm, None, BelowLimit or above AboveLimit. */
enum class AlarmState {
  None, BelowLimit, AboveLimit
}

enum class AlarmType {
  HR, SPO2, ETCO2, RESP_RATE, SYS, DIA
}

data class AlarmLevel(var name: String, var value: Int, var min: Int, var max: Int)


class Alarm(val type: AlarmType, var lowerLimit: Int, var upperLimit: Int) {

  interface AlarmCallback {
    fun update(type: AlarmType, state: AlarmState)
  }

  var callback: AlarmCallback? = null

  var state = AlarmState.None
    private set

  private var currentVal = -1
  /** This function checks the parameter value for its limits and
   * invokes the callback with the current [AlarmState]. */
  fun testForAlarm(value: Int) {
    currentVal = value
    when {
      value > upperLimit -> setState(AlarmState.AboveLimit)
      value < lowerLimit -> setState(AlarmState.BelowLimit)
      else -> setState(AlarmState.None)
    }
  }

  fun testCurrentForAlarm() {
    if (currentVal == -1) return
    when {
      currentVal > upperLimit -> setState(AlarmState.AboveLimit)
      currentVal < lowerLimit -> setState(AlarmState.BelowLimit)
      else -> setState(AlarmState.None)
    }
  }

  private fun setState(newState: AlarmState) {
    if (newState == state) return

    state = newState
    callback?.update(type, state)
  }

  fun deactivateAlarm() {
    setState(AlarmState.None)
    currentVal = -1
  }

  companion object {

    fun initAlarm(type: AlarmType): Alarm {
      return when (type) {
        AlarmType.HR -> Alarm(type, 50, 120)
        AlarmType.SPO2 -> Alarm(type, 90, 101)
        AlarmType.ETCO2 -> Alarm(type, 30, 50)
        AlarmType.RESP_RATE -> Alarm(type, 8, 20)
        AlarmType.SYS -> Alarm(type, 80, 160)
        AlarmType.DIA -> Alarm(type, 40, 100)
      }
    }
  }
}