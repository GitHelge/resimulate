package de.bauerapps.resimulate.views

import de.bauerapps.resimulate.R


enum class ESColor {

  HR, SPO2, ETCO2, RESP_RATE, BP, WHITE;

  companion object {
    fun fromAttributeValue(attrValue: Int): ESColor {
      return when (attrValue) {
        0 -> HR
        1 -> SPO2
        2 -> ETCO2
        3 -> RESP_RATE
        4 -> BP
        else -> WHITE
      }
    }
  }


  fun getColor(): Int {
    return when (this) {
      HR -> R.color.hrColor
      SPO2 -> R.color.spo2Color
      ETCO2 -> R.color.etco2Color
      RESP_RATE -> R.color.respRateColor
      BP -> R.color.bpColor
      else -> R.color.white
    }
  }

}