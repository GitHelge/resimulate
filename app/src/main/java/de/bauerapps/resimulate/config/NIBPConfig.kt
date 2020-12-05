package de.bauerapps.resimulate.config

import android.annotation.SuppressLint
import android.os.CountDownTimer
import android.os.Handler
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.SoundType
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESDialog
import kotlinx.android.synthetic.main.nibp_repeat_dialog.view.*

enum class NIBPState {
  Idle, MeasuringSingle, MeasuringRepeated, Pending
}

class NIBPConfig(private val context: AppCompatActivity) {

  enum class RepeatDuration {
    REPEAT_2MIN, REPEAT_5MIN, NONE;

    fun getInMs(): Long {
      return when (this) {
        REPEAT_2MIN -> 60 * 2 * 1000
        REPEAT_5MIN -> 60 * 5 * 1000
        NONE -> 0
      }
    }
  }

  interface NIBPConfigCallback {
    fun requestSound(type: SoundType)
    //fun updateUI(state: NIBPState)
    fun updateTimer(tick: Long)
  }

  init {

    context.findViewById<ESBootstrapButton>(R.id.b_nibp).setOnClickListener { view ->
      val esButton = (view as ESBootstrapButton)
      if (!isEnabled) {
        if (esButton.isWarning) return@setOnClickListener
        esButton.setWarningBackground(true)
        Handler().postDelayed({ esButton.setWarningBackground(false) }, 1000)
        return@setOnClickListener
      }

      if (state == NIBPState.MeasuringRepeated || state == NIBPState.Pending) {
        stopRepeatedMeasurement()
        setState(NIBPState.MeasuringSingle)
      } else {
        setState(NIBPState.MeasuringSingle)
      }
    }

    context.findViewById<ESBootstrapButton>(R.id.b_nibp_repeat).setOnClickListener { view ->
      val esButton = (view as ESBootstrapButton)
      if (!isEnabled) {
        if (esButton.isWarning) return@setOnClickListener
        esButton.setWarningBackground(true)
        Handler().postDelayed({ esButton.setWarningBackground(false) }, 1000)
        return@setOnClickListener
      }

      when (state) {
        NIBPState.MeasuringRepeated -> {
          setState(NIBPState.MeasuringSingle)
        }
        NIBPState.Pending -> {
          setState(NIBPState.Idle)
        }
        else -> openNIBPRepeatDialog()
      }
    }
  }

  var callback: NIBPConfigCallback? = null
  var repeatDuration: RepeatDuration =
    RepeatDuration.REPEAT_2MIN


  private var countDownTimer: CountDownTimer? = null
  private var nibpRepeatDialog: ESDialog? = null

  var state = NIBPState.Idle
    private set

  var isEnabled = false

  private var repetitionHandler = Handler()
  private var repetitionRunnable = Runnable {
    setState(NIBPState.MeasuringRepeated)
  }

  private fun stopRepeatedMeasurement() {
    repetitionHandler.removeCallbacks(repetitionRunnable)
    stopCountDown()
  }

  fun resetRepeatedMeasurement() {
    setState(NIBPState.Idle)
  }

  fun audioFinished() {
    if (state == NIBPState.MeasuringSingle) {
      setState(NIBPState.Idle)
    } else {
      setState(NIBPState.Pending)
    }
  }

  private fun setState(newState: NIBPState) {
    if (newState == state) return

    when (newState) {
      NIBPState.Idle -> {
        if (state == NIBPState.MeasuringRepeated || state == NIBPState.Pending) {
          stopRepeatedMeasurement()
        }
      }
      NIBPState.MeasuringSingle -> {
        if (state == NIBPState.Pending || state == NIBPState.Idle) {
          callback?.requestSound(SoundType.NIBP)
        } else {
          stopRepeatedMeasurement()
        }
      }
      NIBPState.MeasuringRepeated -> {
        callback?.requestSound(SoundType.NIBP)
        repetitionHandler.postDelayed(repetitionRunnable, repeatDuration.getInMs())
        initCountDown()
      }
      NIBPState.Pending -> {
      }
    }

    state = newState
    updateUI(newState)
  }

  private fun updateUI(state: NIBPState) {

    when (state) {
      NIBPState.Idle -> {
        context.findViewById<ESBootstrapButton>(R.id.b_nibp).setActiveBackground(false)
        context.findViewById<ESBootstrapButton>(R.id.b_nibp_repeat).setActiveBackground(false)
        context.findViewById<TextView>(R.id.tw_bp_repeat).text = ""
      }
      NIBPState.MeasuringSingle -> {
        context.findViewById<ESBootstrapButton>(R.id.b_nibp).setActiveBackground(true)
        context.findViewById<ESBootstrapButton>(R.id.b_nibp_repeat).setActiveBackground(false)
        context.findViewById<TextView>(R.id.tw_bp_repeat).text = ""
      }
      NIBPState.MeasuringRepeated -> {
        context.findViewById<ESBootstrapButton>(R.id.b_nibp).setActiveBackground(false)
        context.findViewById<ESBootstrapButton>(R.id.b_nibp_repeat).setActiveBackground(true)
      }
      NIBPState.Pending -> {
      }
    }
  }

  @SuppressLint("InflateParams")
  private fun openNIBPRepeatDialog() {
    nibpRepeatDialog = ESDialog(context, R.style.NoAnimDialog, backgroundDim = false)
    val nibpRepeatDialogView = LayoutInflater.from(context)
      .inflate(R.layout.nibp_repeat_dialog, null)

    nibpRepeatDialogView.apply {
      b_2min.setOnClickListener {
        repeatDuration = RepeatDuration.REPEAT_2MIN
        setState(NIBPState.MeasuringRepeated)
        nibpRepeatDialog?.dismiss()
      }
      b_5min.setOnClickListener {
        repeatDuration = RepeatDuration.REPEAT_5MIN
        setState(NIBPState.MeasuringRepeated)
        nibpRepeatDialog?.dismiss()
      }
    }

    nibpRepeatDialog?.apply {
      setContentView(nibpRepeatDialogView)
      show()
    }
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

    countDownTimer = object : CountDownTimer(repeatDuration.getInMs(), 1000) {
      override fun onFinish() {}

      override fun onTick(p0: Long) {
        callback?.updateTimer(p0)
      }
    }
    countDownTimer?.start()
  }

}