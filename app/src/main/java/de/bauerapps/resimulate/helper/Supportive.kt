package de.bauerapps.resimulate.helper

import android.view.View
import android.view.ViewTreeObserver
import de.bauerapps.resimulate.PType
import de.bauerapps.resimulate.Pathology
import java.text.SimpleDateFormat
import java.util.*

enum class UserType { Trainee, Trainer }

enum class VSConfigType { HR, PACER_THRES, SPO2, ETCO2, RESP_RATE, SYS, DIA, SHOCK_THRES }

data class User(var type: UserType, var name: String, var id: String?)

fun Double.isZero(threshold: Double = 0.01): Boolean {
  return this >= -threshold && this <= threshold
}

inline fun View.afterMeasured(crossinline f: View.() -> Unit) {
  viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
    override fun onGlobalLayout() {
      if (measuredWidth > 0 && measuredHeight > 0) {
        viewTreeObserver.removeOnGlobalLayoutListener(this)
        f()
      }
    }
  })
}

fun getScenarioArrayList(): ArrayList<Pathology> {
  val saveMap = ESApplication.saveMap

  val list = ArrayList<Pathology>()

  val activeMap = ESApplication.activeMap

  for (type in PType.values()) {
    list.add(Pathology(type).apply {
      isUsed = activeMap[this.name] ?: true
    })
  }

  list.addAll(saveMap.map {
    Pathology(it.key).apply {
      isUsed = activeMap[this.name] ?: true
    }
  })

  return list
}


class Const {
  companion object {
    const val UI_ANIMATION_DELAY = 300
    const val SHOCK_ID = "SHOCK"
    const val SIM_STOPPED = "SIM_STOPPED"
    /*const val TIMER_RESET = "TIMER_RESET"
    const val TIMER_STOP = "TIMER_STOP"
    const val TIMER_START = "TIMER_START"*/
  }
}

/*fun getAttributeDrawable(context: Context, attributeId: Int): Drawable? {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(attributeId, typedValue, true)
    val drawableRes = typedValue.resourceId
    return ContextCompat.getDrawable(context, drawableRes)
}*/
