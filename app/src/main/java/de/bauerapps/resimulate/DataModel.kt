package de.bauerapps.resimulate

import android.util.Log
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.helper.VSConfigType
import com.google.gson.Gson


typealias DVS = DefaultVitalSigns

enum class PType(var pname: String) {
  SinusRhythm(ESApplication.getString(R.string.sinus_rhythm)),
  Asystole(ESApplication.getString(R.string.asystole)),
  JunctionalRhythm(ESApplication.getString(R.string.junc_rhythm)),
  VentricularTachycardia(ESApplication.getString(R.string.vent_tach)),
  VentricularFibrillation(ESApplication.getString(R.string.vent_fib)),
  ArtrialFibrillation(ESApplication.getString(R.string.art_fib)),
  AVBlock3(ESApplication.getString(R.string.av_block_3)),
  STElevation(ESApplication.getString(R.string.st_elev));
}

class Pathology {

  var name: String
  val type: PType
  var isUsed = true

  constructor(name: String, type: PType) {
    this.name = name
    this.type = type
  }

  constructor(type: PType) {
    this.type = type
    this.name = type.pname
  }

  constructor(name: String) {
    this.name = name

    val type = PType.values().firstOrNull { it.pname == name }
    //this.type = type ?: (ESApplication.saveMap[name]?.get("vs") as? VitalSigns)?.pathology?.type ?: PType.SinusRhythm
    this.type = type ?: (Gson().fromJson(
      ESApplication.saveMap[name]?.vs,
      VitalSigns::class.java
    ))?.pathology?.type ?: PType.SinusRhythm
  }


  fun getSpecificBounds(): Map<VSConfigType, Pair<Int, Int>> {

    val map = mutableMapOf(
      VSConfigType.HR to Pair(20, 250),
      VSConfigType.SPO2 to Pair(0, 100),
      VSConfigType.ETCO2 to Pair(0, 60),
      VSConfigType.RESP_RATE to Pair(0, 40),
      VSConfigType.SYS to Pair(0, 300),
      VSConfigType.DIA to Pair(0, 260),
      VSConfigType.PACER_THRES to Pair(10, 150),
      VSConfigType.SHOCK_THRES to Pair(5, 400)
    )

    when (type) {
      PType.SinusRhythm -> map[VSConfigType.HR] = Pair(20, 250)
      PType.Asystole -> {
        map[VSConfigType.HR] = Pair(0, 0)
        map[VSConfigType.SPO2] = Pair(0, 0)
        map[VSConfigType.ETCO2] = Pair(0, 0)
        map[VSConfigType.RESP_RATE] = Pair(0, 0)
        map[VSConfigType.SYS] = Pair(0, 0)
        map[VSConfigType.DIA] = Pair(0, 0)
      }

      else -> {}
    }

    return map
  }
}

class DownloadPathology {

  var id: String = ""
  var name: String = ""
  var vs: String = ""
  var appVersion: Int = 0
  var author: Map<String, String?> = mutableMapOf("" to "")
  var rating: Double = 0.0
  var timeStamp: Long = 0L
  var isUploaded: Boolean = false

  // NEEDED for Firebase
  constructor()

  constructor(
    id: String, name: String, vs: String,
    appVersion: Int, author: Map<String, String?>,
    timeStamp: Long, isUploaded: Boolean
  ) {
    this.id = id
    this.name = name
    this.vs = vs
    this.appVersion = appVersion
    this.author = author
    this.timeStamp = timeStamp
    this.isUploaded = isUploaded
  }
}


enum class ParseNames {
  SimConfigClass,
  PacerStateClass,
  DefiClass,
  DefiParams
}

data class SimConfig(
  var vitalSigns: VitalSigns = DVS.fromPathology(Pathology(PType.SinusRhythm)),
  var simState: SimState = SimState.getDefault()
) {
  val className = ParseNames.SimConfigClass.name

  fun deepCopy(): SimConfig = Gson().fromJson(Gson().toJson(this), SimConfig::class.java)

}

data class DefiParams(var energy: Int, var energyThreshold: Int) {
  val className = ParseNames.DefiParams.name
}

data class Defi(var vitalSigns: VitalSigns, var energy: Int, var energyThreshold: Int) {
  val className = ParseNames.DefiClass.name
  fun deepCopy(): Defi = Gson().fromJson(Gson().toJson(this), Defi::class.java)
}

enum class RespRatio {
  Normal, HyperVent, HypoVent
}

data class SimState(
  var ecgEnabled: Boolean,
  var oxyEnabled: Boolean,
  var capEnabled: Boolean,
  var nibpEnabled: Boolean,
  var defi: Defi,
  var hasCPR: Boolean,
  var hasCOPD: Boolean,
  var pacer: PacerState,
  var respRatio: RespRatio,
  var changeDuration: Int
) {

  companion object {

    fun getDefault(): SimState {
      val pacer = PacerState(false, 60, 10, 50)
      val cd = 15
      val defi = Defi(DefaultVitalSigns.fromPathology(Pathology(PType.SinusRhythm)), 150, 150)

      return SimState(
        false, false, false, false,
        defi, false, false, pacer, RespRatio.Normal, cd
      )
    }
  }

}

data class PacerState(
  var isEnabled: Boolean, var frequency: Int, var energy: Int,
  var energyThreshold: Int
) {
  val className = ParseNames.PacerStateClass
  fun deepCopy(): PacerState = Gson().fromJson(Gson().toJson(this), PacerState::class.java)
}

data class VitalSigns(
  var pathology: Pathology, var ecg: ECG, var oxy: Oxy,
  var cap: Cap, var nibp: NIBP, val parentPathology: Pathology? = null
) {
  fun deepCopy(): VitalSigns = Gson().fromJson(Gson().toJson(this), VitalSigns::class.java)
}

data class Oxy(var spo2: Int, var noise: Double) {
  fun deepCopy(): Oxy = Gson().fromJson(Gson().toJson(this), Oxy::class.java)
}

data class Cap(var respRate: Int, var etco2: Int, var noise: Double) {
  fun deepCopy(): Cap = Gson().fromJson(Gson().toJson(this), Cap::class.java)
}

data class NIBP(var sys: Int, var dia: Int) {
  fun deepCopy(): NIBP = Gson().fromJson(Gson().toJson(this), NIBP::class.java)

}

data class ECG(
  var hr: Int, var pWave: PWave, var qWave: Wave,
  var qrs: QRS, var sWave: Wave, var tWave: Wave, var stWaveSeparationDuration: Double,
  var uWave: Wave, var xValOffset: Double, var noise: Double
) {
  fun deepCopy(): ECG = Gson().fromJson(Gson().toJson(this), ECG::class.java)
}


// TODO: Remove Duration offset if not needed
data class QRS(
  var amp: Double,
  var startTime: Double, /*var amplitudeOffset: Double,*/
  var durationOffset: Double,
  var duration: Double
)

data class PWave(var amp: Double, var startTime: Double, var duration: Double)

data class Wave(var amp: Double, var startTime: Double, var duration: Double)


class DefaultVitalSigns {
  companion object {

    private const val hr = 60

    const val pWaveAmp = 0.25
    const val qWaveAmp = 0.07
    const val qrsAmp = 1.6
    const val sWaveAmp = 0.45
    const val tWaveAmp = 0.35
    const val uWaveAmp = 0.035

    const val pWaveST = -0.18
    const val qWaveST = -0.05
    const val qrsST = 0.0
    const val sWaveST = 0.03
    const val tWaveST = 0.2
    const val uWaveST = 0.433

    const val pWaveDur = 0.11
    const val qWaveDur = 0.07
    const val qrsDur = 0.07
    const val sWaveDur = 0.07
    const val tWaveDur = 0.2
    const val uWaveDur = 0.05

    const val startTimeOffset = 0.25
    const val stWaveSeparationDuration = 0.047
    const val xValOffset = 0.0

    const val ecgNoise = 0.02
    const val oxyNoise = 0.8
    const val capNoise = 0.5

    const val spo2 = 97
    const val respRate = 15
    const val etco2 = 36
    const val nibpSys = 120
    const val nibpDia = 80

    private val pWave = PWave(pWaveAmp, pWaveST, pWaveDur)
    private val qWave = Wave(qWaveAmp, qWaveST, qWaveDur)
    private val qrs = QRS(qrsAmp, qrsST, /*0.0,*/0.0, qrsDur)
    private val sWave = Wave(sWaveAmp, sWaveST, sWaveDur)
    private val tWave = Wave(tWaveAmp, tWaveST, tWaveDur)
    private val uWave = Wave(uWaveAmp, uWaveST, uWaveDur)
    private val nibp = NIBP(nibpSys, nibpDia)
    private val ecg = ECG(
      hr, pWave, qWave, qrs, sWave,
      tWave, stWaveSeparationDuration, uWave, xValOffset, ecgNoise
    )
    private val oxy = Oxy(spo2, oxyNoise)
    private val cap = Cap(respRate, etco2, capNoise)

    fun fromPathology(pathology: Pathology): VitalSigns {

      if (ESApplication.saveMap.containsKey(pathology.name))
        return customPathology(pathology.name)

      return when (pathology.type) {

        PType.SinusRhythm -> sinusRhythm()
        PType.Asystole -> asystole()
        PType.JunctionalRhythm -> junctionalRhythm()
        PType.VentricularTachycardia -> ventricularTachycardia()
        PType.VentricularFibrillation -> ventricularFibrillation()
        PType.ArtrialFibrillation -> atrialFibrillation()
        PType.AVBlock3 -> avBlock3()
        PType.STElevation -> stElevation()
      }
    }

    private fun customPathology(name: String): VitalSigns {
      if (ESApplication.saveMap.containsKey(name)) {
        Log.i(
          "ScenarioSaveDialog",
          "Name: $name found with content: ${ESApplication.saveMap[name]}"
        )
      }

      //val vs = ESApplication.saveMap[name]?.get(ESApplication.VITALSIGNS) as? VitalSigns
      val vs = Gson().fromJson(ESApplication.saveMap[name]?.vs, VitalSigns::class.java)

      return vs ?: DVS.sinusRhythm()
    }

    private fun stElevation(): VitalSigns {
      val pathology = Pathology(PType.STElevation)
      val tWave = tWave.copy(amp = 0.25) // Iterative testing
      val ecg = ecg.copy(tWave = tWave)
      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun avBlock3(): VitalSigns {
      val pathology = Pathology(PType.AVBlock3)
      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun atrialFibrillation(): VitalSigns {
      val pathology = Pathology(PType.ArtrialFibrillation)
      val qWave = qWave.copy(amp = 0.0)
      val pWave = pWave.copy(amp = 0.0)
      val tWave = tWave.copy(amp = 0.0)
      val uWave = uWave.copy(amp = 0.0)
      val ecg = ecg.copy(
        hr = 110, noise = 0.4, qWave = qWave, tWave = tWave,
        uWave = uWave, pWave = pWave
      )
      val oxy = oxy.copy(spo2 = 96)
      val cap = cap.copy(respRate = 12, etco2 = 36)
      val nibp = NIBP(100, 60)
      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun ventricularFibrillation(): VitalSigns {
      val pathology = Pathology(PType.VentricularFibrillation)
      val qWave = qWave.copy(amp = 0.0)
      val pWave = pWave.copy(amp = pWaveAmp * 2.0)
      val qrs = qrs.copy(amp = qrsAmp * 0.3)
      val sWave = sWave.copy(amp = 0.0)
      val tWave = tWave.copy(amp = tWaveAmp * 2.0)
      val uWave = uWave.copy(amp = 0.0)
      val ecg = ecg.copy(
        hr = 250, pWave = pWave, qWave = qWave, qrs = qrs,
        sWave = sWave, tWave = tWave, uWave = uWave, xValOffset = 0.1
      )
      val oxy = Oxy(0, 2.0)
      val cap = Cap(0, 0, 1.0)
      val nibp = NIBP(0, 0)

      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun ventricularTachycardia(): VitalSigns {
      val pathology = Pathology(PType.VentricularTachycardia)
      val qWave = qWave.copy(amp = 0.0)
      val pWave = pWave.copy(amp = pWaveAmp * 2.0)
      val qrs = qrs.copy(durationOffset = 0.110)
      val sWave = sWave.copy(amp = 0.0)
      val uWave = uWave.copy(amp = 0.0)
      val ecg = ecg.copy(
        hr = 180, pWave = pWave, qWave = qWave,
        qrs = qrs, sWave = sWave, uWave = uWave, xValOffset = 0.13
      )

      val cap = cap.copy(respRate = 18, etco2 = 35)

      val nibp = NIBP(70, 60)

      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun sinusRhythm(): VitalSigns {
      val pathology = Pathology(PType.SinusRhythm)
      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun asystole(): VitalSigns {
      val pathology = Pathology(PType.Asystole)
      val qWave = qWave.copy(amp = 0.0)
      val pWave = pWave.copy(amp = 0.0)
      val qrs = qrs.copy(amp = 0.0/*, amplitudeOffset = 0.0*/)//amplitudeOffset = -0.78)
      val sWave = sWave.copy(amp = 0.0)
      val tWave = tWave.copy(amp = 0.0)
      val uWave = uWave.copy(amp = 0.0)
      val ecg = ecg.copy(
        hr = 0, pWave = pWave, qWave = qWave,
        qrs = qrs, sWave = sWave, tWave = tWave, uWave = uWave, noise = 0.25
      )
      val oxy = Oxy(0, 2.0)
      val cap = Cap(0, 0, 1.0)
      val nibp = NIBP(0, 0)

      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

    private fun junctionalRhythm(): VitalSigns {
      val pathology = Pathology(PType.JunctionalRhythm)
      val qWave = qWave.copy(amp = 0.0)
      val pWave = pWave.copy(amp = -pWaveAmp, startTime = 0.05)
      val ecg = ecg.copy(hr = 40, pWave = pWave, qWave = qWave/*, noise = 0.1*/)
      val cap = cap.copy(respRate = 18, etco2 = 30)
      val nibp = NIBP(70, 30)

      return VitalSigns(pathology, ecg, oxy, cap, nibp)
    }

  }
}