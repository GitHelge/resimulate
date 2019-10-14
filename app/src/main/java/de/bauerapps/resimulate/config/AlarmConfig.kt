package de.bauerapps.resimulate.config

import android.annotation.SuppressLint
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import de.bauerapps.resimulate.*
import de.bauerapps.resimulate.adapters.AlarmLevelAdapter
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESDialog
import com.beardedhen.androidbootstrap.BootstrapText
import com.beardedhen.androidbootstrap.font.FontAwesome
import kotlinx.android.synthetic.main.alarm_level_dialog.view.*


@SuppressLint("InflateParams")
class AlarmConfig(private val context: AppCompatActivity, private val sound: Sound?) :
  Alarm.AlarmCallback {

  interface AlarmConfigCallback {
    fun updateTimer(alarmConfig: AlarmConfig, ms: Long)
    fun updateAlarms()
  }

  private val alarms = mutableMapOf<AlarmType, Alarm>()
  private var alarmLevelAdapter: AlarmLevelAdapter? = null

  private var alarmLevelDialog = ESDialog(context, R.style.NoAnimDialog)
  private val alarmLevelDialogView = LayoutInflater.from(context)
    .inflate(R.layout.alarm_level_dialog, null)

  var callback: AlarmConfigCallback? = null

  private val muteDuration: Long = 120 * 1000
  private var countDownTimer: CountDownTimer? = null
  private var currentAlarmCount = -1

  init {
    for (type in AlarmType.values()) {
      alarms[type] = Alarm.initAlarm(type)
      alarms[type]?.callback = this
    }

    alarmLevelAdapter = initAlarmLevelAdapter()
    initAlarmLevelDialog()

    context.findViewById<ESBootstrapButton>(R.id.b_mute).setOnClickListener { toggleAlarmSound() }
    context.findViewById<ESBootstrapButton>(R.id.b_settings)
      .setOnClickListener { openAlarmLevelDialog() }
  }

  override fun update(type: AlarmType, state: AlarmState) {
    updateUI(type, state)
    createSound(type, state)
  }

  private var knownAlarms = mutableMapOf<AlarmType, AlarmState>()

  private fun createSound(type: AlarmType, state: AlarmState) {
    var needsAlarmValidityCheck = false
    currentAlarmCount = -1

    /* In This Condition, the currently changing alarmType is analyzed. If the Alarm was not
        active already (not part of knownAlarms), it is saved together with its alarmstate
        in knownAlarms. When the alarmType was however already saved in knownAlarms, it's endpointState
        is updated. In both cases, the sound will restart to play, as a NEW or updated alarm
        was generated. */
    if (type !in knownAlarms) {
      // For Initialization:
      knownAlarms[type] = state
      if (state != AlarmState.None) {
        sound?.playSound(SoundType.Alarm)
        //toggleAlarmSoundIcon(true)
        updateUI(type, state)
        updateCountDown()
      }
    } else if (knownAlarms[type] != state) {
      knownAlarms[type] = state
      when (state) {
        // Fallthrough is intended!
        AlarmState.AboveLimit, AlarmState.BelowLimit -> {
          sound?.playSound(SoundType.Alarm)
          //toggleAlarmSoundIcon(true)
          updateUI(type, state)
          updateCountDown()
        }
        AlarmState.None -> {
          needsAlarmValidityCheck = true
        }
      }
    }

    /* If an AlarmState changed to None, it must be checked, whether there are more alarms active.
        In case none of the alarms are active anymore, the sound is stopped. */
    if (needsAlarmValidityCheck) {
      /* The knownAlarms List must be checked if some alarms are still valid,
          so the sound keeps on playing */
      currentAlarmCount = 0
      for (alarm in knownAlarms) {
        if (knownAlarms[alarm.key] != AlarmState.None) {
          currentAlarmCount++
          break
        }
      }
    }

    if (currentAlarmCount == 0) sound?.stopAlarm()
  }

  private fun stopCountDown() {
    if (countDownTimer != null) {
      countDownTimer?.cancel()
      countDownTimer = null
    }
  }

  private fun initCountDown() {
    if (countDownTimer != null) {
      countDownTimer?.cancel()
      countDownTimer = null
    }

    countDownTimer = object : CountDownTimer(muteDuration, 1000) {
      override fun onFinish() {
        toggleAlarmSound()
      }

      override fun onTick(p0: Long) {
        callback?.updateTimer(this@AlarmConfig, p0)
      }
    }
    countDownTimer?.start()
  }

  /** Toggles the Alarm Sound. If alarms are active and sound is playing, the sound is muted.
   * If sounds are muted, button is clicked and alarms are active, the sound is unmuted.
   * */
  fun toggleAlarmSound() {
    var currentAlarmCount = 0
    if (knownAlarms.isNotEmpty()) {
      for (alarm in knownAlarms) {
        if (alarm.value != AlarmState.None) {
          currentAlarmCount++
          break
        }
      }
    }

    if (currentAlarmCount > 0)
      sound?.toggleAlarm()

    updateCountDown()
  }

  private fun updateUI(type: AlarmType, state: AlarmState) {

    val view = when (type) {
      AlarmType.HR -> context.findViewById<View>(R.id.ll_hr)
      AlarmType.SPO2 -> context.findViewById<View>(R.id.ll_spo2)
      AlarmType.ETCO2 -> context.findViewById<View>(R.id.ll_etco2)
      AlarmType.RESP_RATE -> context.findViewById<View>(R.id.ll_resp_rate)
      AlarmType.SYS -> context.findViewById<View>(R.id.ll_nibp)
      AlarmType.DIA -> context.findViewById<View>(R.id.ll_nibp)
    }

    if (state != AlarmState.None) {
      view.background = ContextCompat.getDrawable(context, R.drawable.bg_alarm)
    } else {
      view.setBackgroundResource(0)
    }

  }

  private fun updateCountDown() {
    val isAlarmLooping = sound?.isAlarmLooping ?: return

    when {
      isAlarmLooping || currentAlarmCount == 0 -> stopCountDown()
      else -> initCountDown()
    }

    updateMuteButton(isAlarmLooping || currentAlarmCount == 0)
  }

  private fun deactivateAlarm(type: AlarmType) {
    alarms[type]?.deactivateAlarm()
  }

  fun updateAlarmConfig(simState: SimState) {
    if (!simState.ecgEnabled && !simState.oxyEnabled)
      deactivateAlarm(AlarmType.HR)

    if (!simState.oxyEnabled) {
      deactivateAlarm(AlarmType.SPO2)
    }

    if (!simState.capEnabled) {
      deactivateAlarm(AlarmType.ETCO2)
      deactivateAlarm(AlarmType.RESP_RATE)
    }

    if (!simState.nibpEnabled) {
      deactivateAlarm(AlarmType.SYS)
      deactivateAlarm(AlarmType.DIA)
    }
  }

  fun deactivateAllAlarms() {
    alarms.values.forEach { it.deactivateAlarm() }
  }

  fun testCurrentForAlarm() {
    for (alarm in alarms.values) {
      alarm.testCurrentForAlarm()
    }
  }

  fun testForAlarm(type: AlarmType, value: Int) {
    alarms[type]?.testForAlarm(value)
  }

  private fun initAlarmLevelDialog() {
    alarmLevelDialogView.apply {

      rv_alarms.layoutManager = LinearLayoutManager(context)
      rv_alarms.adapter = alarmLevelAdapter
      b_check.setOnClickListener {
        applyAlarmConfigChanges()
        alarmLevelDialog.dismiss()
      }
      b_cancel.setOnClickListener { alarmLevelDialog.dismiss() }
    }

    alarmLevelDialog.apply {
      setContentView(alarmLevelDialogView)
    }
  }

  private fun openAlarmLevelDialog() {

    alarmLevelDialog.show()
  }

  private fun updateMuteButton(isAlarmLooping: Boolean) {
    context.findViewById<ESBootstrapButton>(R.id.b_mute).setWarningBackground(!isAlarmLooping)
    context.findViewById<ESBootstrapButton>(R.id.b_mute).bootstrapText = if (!isAlarmLooping) {
      BootstrapText.Builder(context, false)
        .addFontAwesomeIcon(FontAwesome.FA_BELL_SLASH)
        .addText("2:00").build()
    } else {
      BootstrapText.Builder(context, false)
        .addFontAwesomeIcon(FontAwesome.FA_BELL_SLASH)
        .addText("MUTE").build()
    }
  }

  private fun applyAlarmConfigChanges() {
    if (alarmLevelAdapter == null) return

    for (alarm in alarmLevelAdapter?.alarms!!) {
      val value = alarm.value
      when (alarm.name) {
        context.getString(R.string.hr_upper_limit) -> alarms[AlarmType.HR]?.upperLimit = value
        context.getString(R.string.hr_lower_limit) -> alarms[AlarmType.HR]?.lowerLimit = value
        context.getString(R.string.spo2_lower_limit) -> alarms[AlarmType.SPO2]?.lowerLimit = value
        context.getString(R.string.etco2_upper_limit) -> alarms[AlarmType.ETCO2]?.upperLimit = value
        context.getString(R.string.etco2_lower_limit) -> alarms[AlarmType.ETCO2]?.lowerLimit = value
        context.getString(R.string.resp_rate_upper_limit) -> alarms[AlarmType.RESP_RATE]?.upperLimit =
          value
        context.getString(R.string.resp_rate_lower_limit) -> alarms[AlarmType.RESP_RATE]?.lowerLimit =
          value
        context.getString(R.string.sys_upper_limit) -> alarms[AlarmType.SYS]?.upperLimit = value
        context.getString(R.string.sys_lower_limit) -> alarms[AlarmType.SYS]?.lowerLimit = value
        context.getString(R.string.dia_upper_limit) -> alarms[AlarmType.DIA]?.upperLimit = value
        context.getString(R.string.dia_lower_limit) -> alarms[AlarmType.DIA]?.lowerLimit = value
      }
    }
    callback?.updateAlarms()
  }

  private fun initAlarmLevelAdapter(): AlarmLevelAdapter {

    val hrUpper = AlarmLevel(
      context.getString(R.string.hr_upper_limit),
      alarms[AlarmType.HR]!!.upperLimit,
      80,
      140
    )
    val hrLower = AlarmLevel(
      context.getString(R.string.hr_lower_limit),
      alarms[AlarmType.HR]!!.lowerLimit,
      20,
      60
    )
    val spo2Lower = AlarmLevel(
      context.getString(R.string.spo2_lower_limit),
      alarms[AlarmType.SPO2]!!.lowerLimit,
      50,
      100
    )
    val etco2Upper = AlarmLevel(
      context.getString(R.string.etco2_upper_limit),
      alarms[AlarmType.ETCO2]!!.upperLimit,
      35,
      60
    )
    val etco2Lower = AlarmLevel(
      context.getString(R.string.etco2_lower_limit),
      alarms[AlarmType.ETCO2]!!.lowerLimit,
      20,
      40
    )
    val respRateUpper = AlarmLevel(
      context.getString(R.string.resp_rate_upper_limit),
      alarms[AlarmType.RESP_RATE]!!.upperLimit,
      25,
      50
    )
    val respRateLower = AlarmLevel(
      context.getString(R.string.resp_rate_lower_limit),
      alarms[AlarmType.RESP_RATE]!!.lowerLimit,
      5,
      20
    )
    val sysUpper = AlarmLevel(
      context.getString(R.string.sys_upper_limit),
      alarms[AlarmType.SYS]!!.upperLimit,
      120,
      200
    )
    val sysLower = AlarmLevel(
      context.getString(R.string.sys_lower_limit),
      alarms[AlarmType.SYS]!!.lowerLimit,
      60,
      100
    )
    val diaUpper = AlarmLevel(
      context.getString(R.string.dia_upper_limit),
      alarms[AlarmType.DIA]!!.upperLimit,
      80,
      150
    )
    val diaLower = AlarmLevel(
      context.getString(R.string.dia_lower_limit),
      alarms[AlarmType.DIA]!!.lowerLimit,
      20,
      60
    )

    val alarmLevels = arrayListOf(
      hrUpper, hrLower, spo2Lower, etco2Upper,
      etco2Lower, respRateUpper, respRateLower, sysUpper, sysLower, diaUpper, diaLower
    )

    return AlarmLevelAdapter(alarmLevels)
  }

  fun onStop() {
    stopCountDown()
  }
}