package de.bauerapps.resimulate

import android.media.*
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.helper.isZero
import de.bauerapps.resimulate.threads.PlayToneThread
import kotlin.math.roundToInt


enum class SoundType {
  Charging, Warning, Shock, ECGPeak, Spo2Peak, Alarm, NIBP
}

class Sound(private val context: AppCompatActivity) {

  interface SoundCallback {
    fun onFinish(type: SoundType)
    //fun onStart(type: SoundType)
  }

  private var charge: MediaPlayer? = null
  private var shock: MediaPlayer? = null
  private var warning: MediaPlayer? = null
  private var nibp: MediaPlayer? = null
  private var nibpShort: MediaPlayer? = null
  private var alarm: MediaPlayer? = null
  private var alarmSingle: MediaPlayer? = null

  private var spo2Value = 90

  fun updateSPO2(value: Int) {
    if (value == spo2Value) return
    spo2Value = if (value < 90) 90 else value

    PlayToneThread(400 + 10 * (spo2Value - 90), peakSoundVolume.toFloat()).start()
  }

  val chargeSoundDuration get() = charge?.duration

  var soundCallback: SoundCallback? = null

  private var peakSoundVolume = 0.0
  //var isNIBPRepeated = false

  init {
    createAllSounds()


    context.findViewById<View>(R.id.b_volume_up).setOnClickListener { peakSoundVolumeUp() }
    context.findViewById<View>(R.id.b_volume_down).setOnClickListener { peakSoundVolumeDown() }
    //context.b_volume_up.setOnClickListener { peakSoundVolumeUp() }
    //context.b_volume_down.setOnClickListener { peakSoundVolumeDown() }
  }

  fun playSound(type: SoundType) {
    when (type) {
      SoundType.Charging -> charging()
      SoundType.Warning -> warning()
      SoundType.Shock -> shock()
      SoundType.ECGPeak -> {
        if (peakSoundVolume.isZero()) return
        PlayToneThread(450, peakSoundVolume.toFloat()).start()
      }
      SoundType.Spo2Peak -> {
        if (peakSoundVolume.isZero()) return
        PlayToneThread(400 + 10 * (spo2Value - 90), peakSoundVolume.toFloat()).start()
      }
      SoundType.Alarm -> alarm()
      SoundType.NIBP -> nibp()
    }
  }

  private fun nibp() {
    if (nibp != null && nibpShort != null) {
      if (!nibp!!.isPlaying &&  !nibpShort!!.isPlaying) {
        if (Math.random() < 0.5) nibp!!.start() else nibpShort!!.start()
      }
    }
  }

  private fun alarm() {
    if (alarm != null && alarmSingle != null) {
      if (alarm!!.isPlaying && !alarmSingle!!.isPlaying) {
        alarmSingle!!.start()
      } else {
        alarm!!.isLooping = true
        alarm!!.start()
      }
    }
  }

  fun toggleAlarm() {
    if (alarm?.isPlaying == true) stopAlarm() else alarm()
  }

  val isAlarmLooping get() = alarm?.isLooping ?: false

  fun stopAlarm() {
    alarm?.isLooping = false
  }

  private fun warning() {
    warning?.start()
  }

  private fun charging() {
    charge?.start()
  }

  private fun shock() {
    shock?.start()
  }

  private fun peakSoundVolumeUp() {
    if (peakSoundVolume + 0.1 <= 1) {
      peakSoundVolume += 0.1
      updateVolumeUI(peakSoundVolume)
    }
  }

  private fun peakSoundVolumeDown() {
    if (peakSoundVolume - 0.1 >= 0) {
      peakSoundVolume -= 0.1
      updateVolumeUI(peakSoundVolume)
    }
  }

  private fun updateVolumeUI(value: Double) {
    context.findViewById<TextView>(R.id.tw_volume).text = "${(value * 100).roundToInt()}%"
  }

  fun createAllSounds() {
    charge = MediaPlayer.create(context, R.raw.defi_load)
    shock = MediaPlayer.create(context, R.raw.defi_shock)
    warning = MediaPlayer.create(context, R.raw.defi_fully_loaded)
    nibp = MediaPlayer.create(context, R.raw.nibp_sound)
    nibpShort = MediaPlayer.create(context, R.raw.nibp_sound_short)
    alarm = MediaPlayer.create(context, R.raw.ding_sound)
    alarmSingle = MediaPlayer.create(context, R.raw.ding_sound)

    charge!!.isLooping = false
    warning!!.isLooping = false
    shock!!.isLooping = false
    nibp!!.isLooping = false
    nibpShort!!.isLooping = false
    alarmSingle!!.isLooping = false

    charge!!.setOnCompletionListener { soundCallback?.onFinish(SoundType.Charging) }
    warning!!.setOnCompletionListener { soundCallback?.onFinish(SoundType.Warning) }
    shock!!.setOnCompletionListener { soundCallback?.onFinish(SoundType.Shock) }
    nibp!!.setOnCompletionListener { soundCallback?.onFinish(SoundType.NIBP) }
    nibpShort!!.setOnCompletionListener { soundCallback?.onFinish(SoundType.NIBP) }
  }

  fun clearAllSounds() {
    val list = mutableListOf(charge, shock, warning, nibp, nibpShort, alarm, alarmSingle)

    for ((index, sound) in list.withIndex()) {
      try {
        if (sound?.isLooping == true) sound.isLooping = false
        if (sound?.isPlaying == true) sound.stop()
        sound?.release()
        list[index] = null
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
}